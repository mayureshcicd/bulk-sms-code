package com.message.approval.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.message.approval.domain.IncomingWhatsAppMessage;
import com.message.approval.domain.MonitoredWhatsAppSession;
import com.message.approval.repository.IncomingWhatsAppMessageRepository;
import com.message.approval.repository.MonitoredWhatsAppSessionRepository;

@Service
public class OpenWAWebhookReceiverService {
    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Kolkata");
    private final ObjectMapper objectMapper;
    private final IncomingWhatsAppMessageRepository messageRepository;
    private final MonitoredWhatsAppSessionRepository monitoredRepository;
    private final OpenWAIncomingContentService incomingContentService;

    @Value("${openwa.webhook.secret}")
    private String webhookSecret;

    public OpenWAWebhookReceiverService(ObjectMapper objectMapper,
            IncomingWhatsAppMessageRepository messageRepository,
            MonitoredWhatsAppSessionRepository monitoredRepository,
            OpenWAIncomingContentService incomingContentService) {
        this.objectMapper = objectMapper;
        this.messageRepository = messageRepository;
        this.monitoredRepository = monitoredRepository;
        this.incomingContentService = incomingContentService;
    }

    public boolean validSignature(byte[] rawBody, String signature) {
        if (signature == null || webhookSecret == null || webhookSecret.isBlank()) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(rawBody));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                    signature.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to verify webhook signature", e);
        }
    }

    @Transactional
    public ReceiveResult receive(byte[] rawBody, String headerIdempotencyKey, String headerDeliveryId) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String sessionId = requiredText(root, "sessionId");
            Optional<MonitoredWhatsAppSession> monitored = monitoredRepository.findBySessionIdAndEnabledTrue(sessionId);
            if (monitored.isEmpty()) return ReceiveResult.IGNORED;
            String event = root.path("event").asText("");
            JsonNode data = root.path("data");
            String whatsappMessageId = firstText(data, "id", "messageId", "quotedMessageId");
            if (whatsappMessageId == null) return ReceiveResult.IGNORED;
            String idempotencyKey = firstNonBlank(headerIdempotencyKey,
                    root.path("idempotencyKey").asText(null), sha256(rawBody));
            if (messageRepository.existsByIdempotencyKey(idempotencyKey)) return ReceiveResult.DUPLICATE;

            if ("message.edited".equals(event) || "message.revoked".equals(event)) {
                Optional<IncomingWhatsAppMessage> existing = messageRepository
                        .findFirstBySessionIdAndWhatsappMessageId(sessionId, whatsappMessageId);
                if (existing.isPresent()) {
                    IncomingWhatsAppMessage message = existing.get();
                    if ("message.edited".equals(event)) {
                        message.setEdited(true);
                        String editedBody = firstText(data, "body", "text", "caption");
                        if (editedBody != null) message.setMessageBody(editedBody);
                    } else {
                        message.setRevoked(true);
                    }
                    messageRepository.save(message);
                }
                return ReceiveResult.UPDATED;
            }
            if (!"message.received".equals(event)) return ReceiveResult.IGNORED;

            IncomingWhatsAppMessage message = new IncomingWhatsAppMessage();
            message.setIdempotencyKey(idempotencyKey);
            message.setDeliveryId(firstNonBlank(headerDeliveryId, root.path("deliveryId").asText(null)));
            message.setSessionId(sessionId);
            message.setLinkedMobile(monitored.get().getMobileNumber());
            message.setWhatsappMessageId(whatsappMessageId);
            message.setChatId(firstNonBlank(firstText(data, "chatId"), firstText(data, "from"), "unknown"));
            message.setSenderId(firstText(data, "from", "sender"));
            message.setRecipientId(firstText(data, "to", "recipient"));
            message.setAuthorId(firstText(data, "author"));
            message.setMessageType(firstNonBlank(firstText(data, "type"), "unknown"));
            String messageBody = firstText(data, "body", "text", "caption");
            message.setMessageBody(messageBody == null ? "" : messageBody);
            message.setGroupMessage(data.path("isGroup").asBoolean(false));
            message.setFromMe(data.path("fromMe").asBoolean(false));
            JsonNode media = data.path("media");
            message.setMediaMimetype(firstNonBlank(firstText(media, "mimetype", "mimeType", "contentType"),
                    firstText(data, "mimetype", "mimeType", "contentType")));
            message.setMediaFilename(firstNonBlank(firstText(media, "filename", "fileName"),
                    firstText(data, "filename", "fileName")));
            message.setHasMedia(data.path("hasMedia").asBoolean(false)
                    || incomingContentService.isMedia(message.getMessageType(), message.getMediaMimetype()));
            if (message.getMessageBody().isBlank() && !message.isHasMedia()) {
                return ReceiveResult.IGNORED;
            }
            String senderContact = firstNonBlank(message.getAuthorId(), message.getSenderId());
            message.setSenderMobile(incomingContentService.resolvePhone(sessionId, senderContact));
            message.setMessageTimestamp(toLocalDateTime(data.path("timestamp")));
            message.setReceivedAt(LocalDateTime.now(REPORT_ZONE));
            messageRepository.save(message);
            return ReceiveResult.CREATED;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid OpenWA webhook payload", e);
        }
    }

    private LocalDateTime toLocalDateTime(JsonNode timestamp) {
        if (timestamp == null || timestamp.isMissingNode() || timestamp.isNull()) return null;
        try {
            if (timestamp.isNumber()) {
                long value = timestamp.asLong();
                Instant instant = Math.abs(value) >= 100_000_000_000L
                        ? Instant.ofEpochMilli(value)
                        : Instant.ofEpochSecond(value);
                return LocalDateTime.ofInstant(instant, REPORT_ZONE);
            }
            return LocalDateTime.ofInstant(Instant.parse(timestamp.asText()), REPORT_ZONE);
        } catch (Exception ignored) { return null; }
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    private String firstText(JsonNode node, String... fields) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isTextual() || value.isNumber()) {
                String text = value.asText();
                if (!text.isBlank() && !"null".equals(text)) return text;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }

    private String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    public enum ReceiveResult { CREATED, UPDATED, DUPLICATE, IGNORED }
}
