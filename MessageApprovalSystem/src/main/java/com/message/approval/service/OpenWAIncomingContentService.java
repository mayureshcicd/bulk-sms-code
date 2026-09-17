package com.message.approval.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.message.approval.domain.IncomingWhatsAppMessage;
import com.message.approval.repository.IncomingWhatsAppMessageRepository;

import jakarta.servlet.http.HttpServletResponse;

@Service
public class OpenWAIncomingContentService {
    private static final Set<String> MEDIA_TYPES = Set.of(
            "image", "video", "document", "audio", "voice", "ptt", "sticker", "gif");

    private final RestTemplate restTemplate;
    private final IncomingWhatsAppMessageRepository messageRepository;

    @Value("${openwa.api.base-url:http://localhost:2785/api}")
    private String openwaBaseUrl;
    @Value("${openwa.api.key:}")
    private String openwaApiKey;

    public OpenWAIncomingContentService(RestTemplate restTemplate,
            IncomingWhatsAppMessageRepository messageRepository) {
        this.restTemplate = restTemplate;
        this.messageRepository = messageRepository;
    }

    public String resolvePhone(String sessionId, String contactId) {
        if (contactId == null || contactId.isBlank()) return null;
        if (contactId.endsWith("@c.us")) return digitsBeforeAt(contactId);
        if (!contactId.endsWith("@lid")) return digitsBeforeAt(contactId);
        try {
            String url = path("sessions", sessionId, "contacts", contactId, "phone");
            JsonNode response = restTemplate.exchange(url, HttpMethod.GET,
                    new org.springframework.http.HttpEntity<>(apiHeaders()), JsonNode.class).getBody();
            return response == null ? null : cleanPhone(response.path("phone").asText(null));
        } catch (Exception ignored) {
            return null;
        }
    }

    @Transactional
    public void enrichStoredMessages(List<IncomingWhatsAppMessage> messages) {
        boolean changed = false;
        java.util.Map<String, String> resolved = new java.util.HashMap<>();
        for (IncomingWhatsAppMessage message : messages) {
            String senderContact = message.getAuthorId() != null && !message.getAuthorId().isBlank()
                    ? message.getAuthorId() : message.getSenderId();
            if ((message.getSenderMobile() == null || message.getSenderMobile().isBlank())
                    && senderContact != null) {
                String cacheKey = message.getSessionId() + "|" + senderContact;
                String phone = resolved.computeIfAbsent(cacheKey,
                        ignored -> resolvePhone(message.getSessionId(), senderContact));
                if (phone != null && !phone.isBlank()) {
                    message.setSenderMobile(phone);
                    changed = true;
                }
            }
            if (!message.isHasMedia() && isMedia(message.getMessageType(), message.getMediaMimetype())) {
                message.setHasMedia(true);
                changed = true;
            }
        }
        if (changed) messageRepository.saveAll(messages);
    }

    public void streamMedia(long messageId, boolean download, String range, HttpServletResponse browserResponse)
            throws IOException {
        IncomingWhatsAppMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Incoming message not found"));
        if (!message.isHasMedia() && !isMedia(message.getMessageType(), message.getMediaMimetype())) {
            throw new IllegalArgumentException("This message has no downloadable media");
        }
        String url = path("sessions", message.getSessionId(), "messages", message.getChatId(),
                message.getWhatsappMessageId(), "media");
        try {
            restTemplate.execute(url, HttpMethod.GET, request -> {
                request.getHeaders().putAll(apiHeaders());
                if (range != null && !range.isBlank()) request.getHeaders().set(HttpHeaders.RANGE, range);
            }, response -> {
                browserResponse.setStatus(response.getStatusCode().value());
                String responseContentType = response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
                if (responseContentType != null && !responseContentType.isBlank()) {
                    browserResponse.setHeader(HttpHeaders.CONTENT_TYPE, responseContentType);
                } else if (message.getMediaMimetype() != null && !message.getMediaMimetype().isBlank()) {
                    browserResponse.setHeader(HttpHeaders.CONTENT_TYPE, message.getMediaMimetype());
                }
                copyHeader(response.getHeaders(), browserResponse, HttpHeaders.CONTENT_LENGTH);
                copyHeader(response.getHeaders(), browserResponse, HttpHeaders.CONTENT_RANGE);
                copyHeader(response.getHeaders(), browserResponse, HttpHeaders.ACCEPT_RANGES);
                browserResponse.setHeader(HttpHeaders.CACHE_CONTROL, "private, max-age=300");
                String filename = safeFilename(message);
                ContentDisposition disposition = download
                        ? ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build()
                        : ContentDisposition.inline().filename(filename, StandardCharsets.UTF_8).build();
                browserResponse.setHeader(HttpHeaders.CONTENT_DISPOSITION, disposition.toString());
                response.getBody().transferTo(browserResponse.getOutputStream());
                browserResponse.flushBuffer();
                return null;
            });
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND
                    && streamMediaFromLiveHistory(message, download, browserResponse)) {
                return;
            }
            writeMediaError(browserResponse, e.getStatusCode().value(),
                    "Media is no longer available from OpenWA");
        } catch (RestClientException e) {
            writeMediaError(browserResponse, HttpServletResponse.SC_BAD_GATEWAY,
                    "Unable to retrieve media from OpenWA right now");
        }
    }

    private boolean streamMediaFromLiveHistory(IncomingWhatsAppMessage message, boolean download,
            HttpServletResponse browserResponse) {
        try {
            String historyUrl = UriComponentsBuilder.fromHttpUrl(path("sessions", message.getSessionId(),
                            "messages", message.getChatId(), "history"))
                    .queryParam("limit", 100)
                    .queryParam("includeMedia", true)
                    .build().encode().toUriString();
            JsonNode root = restTemplate.exchange(historyUrl, HttpMethod.GET,
                    new org.springframework.http.HttpEntity<>(apiHeaders()), JsonNode.class).getBody();
            JsonNode rows = firstArray(root, "messages", "items", "data");
            if (rows == null) return false;
            for (JsonNode row : rows) {
                String id = firstText(row, "id", "messageId", "waMessageId");
                if (!message.getWhatsappMessageId().equals(id)) continue;
                JsonNode media = row.path("media");
                String encoded = firstText(media, "data", "base64");
                if (encoded == null) return false;
                int comma = encoded.indexOf(',');
                if (encoded.startsWith("data:") && comma >= 0) encoded = encoded.substring(comma + 1);
                byte[] bytes = Base64.getDecoder().decode(encoded);
                String mimetype = firstNonBlank(firstText(media, "mimetype", "mimeType", "contentType"),
                        message.getMediaMimetype(), "application/octet-stream");
                browserResponse.setStatus(HttpServletResponse.SC_OK);
                browserResponse.setContentType(mimetype);
                browserResponse.setContentLengthLong(bytes.length);
                ContentDisposition disposition = download
                        ? ContentDisposition.attachment().filename(safeFilename(message), StandardCharsets.UTF_8).build()
                        : ContentDisposition.inline().filename(safeFilename(message), StandardCharsets.UTF_8).build();
                browserResponse.setHeader(HttpHeaders.CONTENT_DISPOSITION, disposition.toString());
                browserResponse.setHeader(HttpHeaders.CACHE_CONTROL, "private, max-age=300");
                browserResponse.getOutputStream().write(bytes);
                browserResponse.flushBuffer();
                return true;
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }

    private JsonNode firstArray(JsonNode root, String... fields) {
        if (root == null) return null;
        if (root.isArray()) return root;
        for (String field : fields) if (root.path(field).isArray()) return root.path(field);
        return null;
    }

    private String firstText(JsonNode node, String... fields) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        for (String field : fields) {
            String value = node.path(field).asText(null);
            if (value != null && !value.isBlank() && !"null".equals(value)) return value;
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }

    private void writeMediaError(HttpServletResponse response, int status, String message) throws IOException {
        if (response.isCommitted()) return;
        response.resetBuffer();
        response.setStatus(status);
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write(message);
        response.flushBuffer();
    }

    public boolean isMedia(String type, String mimetype) {
        return (mimetype != null && !mimetype.isBlank())
                || (type != null && MEDIA_TYPES.contains(type.toLowerCase(Locale.ROOT)));
    }

    private HttpHeaders apiHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (openwaApiKey != null && !openwaApiKey.isBlank()) headers.set("X-API-Key", openwaApiKey);
        return headers;
    }

    private String path(String... segments) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(openwaBaseUrl);
        for (String segment : segments) builder.pathSegment(segment);
        return builder.build().encode().toUriString();
    }

    private void copyHeader(HttpHeaders source, HttpServletResponse target, String name) {
        String value = source.getFirst(name);
        if (value != null) target.setHeader(name, value);
    }

    private String safeFilename(IncomingWhatsAppMessage message) {
        if (message.getMediaFilename() != null && !message.getMediaFilename().isBlank()) {
            return message.getMediaFilename().replace('"', '_').replace('\r', '_').replace('\n', '_');
        }
        String extension = switch (message.getMediaMimetype() == null ? "" : message.getMediaMimetype()) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "video/mp4" -> ".mp4";
            case "application/pdf" -> ".pdf";
            default -> "";
        };
        return "whatsapp-media-" + message.getId() + extension;
    }

    private String digitsBeforeAt(String value) {
        int at = value.indexOf('@');
        return cleanPhone(at < 0 ? value : value.substring(0, at));
    }

    private String cleanPhone(String value) {
        if (value == null) return null;
        String digits = value.replaceAll("\\D", "");
        return digits.isBlank() ? null : digits;
    }
}
