package com.sms.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sms.config.OpenWAConfig;
import com.sms.dto.BulkDocumentResponse;
import com.sms.service.BulkDocumentService;
import com.sms.service.ApprovedMessageClient;
import com.sms.service.TrialService;
import com.sms.util.CsvReader;
import com.sms.util.OpenWAHandler;
import com.sms.util.VideoCompressor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/whatsapp")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class WhatsAppController {

    private final OpenWAConfig openwaConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final VideoCompressor videoCompressor;
    private final CsvReader csvReader;
    private final OpenWAHandler openWAHandler;
    private final BulkDocumentService bulkDocumentService;
    private final TrialService trialService;
    private final com.sms.service.ContactService contactService;
    private final ApprovedMessageClient approvedMessageClient;
    private final com.sms.service.EmailService emailService;

    @Value("${whatsapp.bulk.min-delay-ms:3000}")
    private long minBulkDelayMs;
    @Value("${whatsapp.bulk.max-recipients:100}")
    private int maxBulkRecipients;
    @Value("${whatsapp.bulk.cooldown-after:50}")
    private int bulkCooldownAfter;
    @Value("${whatsapp.bulk.cooldown-ms:60000}")
    private long bulkCooldownMs;

    @GetMapping("/account")
    public Map<String, String> account() {
        var user = openWAHandler.currentUser();
        return Map.of(
                "username", user.getUsername(),
                "mobileNumber", user.getMobileNumber() == null ? "" : user.getMobileNumber(),
                "role", user.getRole());
    }

    @GetMapping("/approved-messages")
    public ResponseEntity<?> approvedMessages() {
        try {
            return ResponseEntity.ok(approvedMessageClient.list());
        } catch (Exception e) {
            log.error("Unable to load approved messages", e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "Unable to load approved messages. Ensure MessageApprovalSystem is running."));
        }
    }

    @GetMapping("/approved-messages/{id}/attachment")
    public ResponseEntity<byte[]> approvedMessageAttachment(@PathVariable long id) {
        return approvedMessageAttachment(id, 0);
    }

    @GetMapping("/approved-messages/{id}/attachments/{fileIndex}")
    public ResponseEntity<byte[]> approvedMessageAttachment(@PathVariable long id, @PathVariable int fileIndex) {
        try {
            ApprovedMessageClient.ApprovedMessage approved = approvedMessageClient.get(id);
            if (approved == null || approved.files() == null || fileIndex < 0 || fileIndex >= approved.files().size()) {
                return ResponseEntity.notFound().build();
            }
            ApprovedMessageClient.Attachment attachment = approvedMessageClient.getAttachment(id, fileIndex, approved.files().get(fileIndex));
            MediaType contentType = MediaType.APPLICATION_OCTET_STREAM;
            if (attachment.contentType() != null) {
                try { contentType = MediaType.parseMediaType(attachment.contentType()); }
                catch (IllegalArgumentException ignored) { }
            }
            return ResponseEntity.ok()
                    .contentType(contentType)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + attachment.fileName().replace("\"", "'").replace("\r", "").replace("\n", "") + "\"")
                    .body(attachment.bytes());
        } catch (Exception e) {
            log.error("Unable to load approved-message attachment {}", id, e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    @GetMapping(value = "/approved-messages/{id}/attachments/{fileIndex}/preview", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> approvedMessageAttachmentPreview(@PathVariable long id, @PathVariable int fileIndex) {
        try {
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(approvedMessageClient.getPreview(id, fileIndex));
        } catch (Exception e) {
            log.error("Unable to preview approved-message attachment {} index {}", id, fileIndex, e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("<p>Unable to generate document preview.</p>");
        }
    }

    @GetMapping("/batch-status/{batchId}")
    public ResponseEntity<String> batchStatus(@PathVariable String batchId) throws IOException {
        String periodMessage = getStringResponseEntity();
        if (periodMessage != null) return ResponseEntity.badRequest().body(
                objectMapper.createObjectNode().put("error", periodMessage).toString());
        if (openWAHandler.getSessionId() == null) {
            return ResponseEntity.badRequest().body(
                    objectMapper.createObjectNode().put("error", "Session not connected. Click Connect first!").toString());
        }
        return ResponseEntity.ok(
                bulkDocumentService.batchStatus(batchId));

    }



    @DeleteMapping("/disconnect")
    public ResponseEntity<String> disconnect() throws IOException {
        String periodMessage = getStringResponseEntity();
        if (periodMessage != null) return ResponseEntity.badRequest().body(
                objectMapper.createObjectNode().put("error", periodMessage).toString());
        if (openWAHandler.getSessionId() == null) {
            return ResponseEntity.badRequest().body(
                    objectMapper.createObjectNode().put("error", "Session not connected. Click Connect first!").toString());
        }
        return ResponseEntity.ok(
                objectMapper.createObjectNode()
                        .put("message", bulkDocumentService.disconnect())
                        .toString());

    }
    @PostMapping("/send-document-bulk")
    public ResponseEntity<String> sendDocumentBulk(
            @RequestParam(value = "file", required = false) MultipartFile documentFile,
            @RequestParam(value = "csv", required = false) MultipartFile csvFile,
            @RequestParam(value = "contactIds", required = false) List<Long> contactIds,
            @RequestParam(value = "caption", required = false) String caption,
            @RequestParam(value = "approvedMessageId", required = false) Long approvedMessageId,
            @RequestParam(value = "phoneLimit", required = false) Integer phoneLimit,
            @RequestParam(value = "delayMs", required = false, defaultValue = "3000") long delayMs) throws IOException {
        String periodMessage = getStringResponseEntity();
        if (periodMessage != null) return ResponseEntity.badRequest().body(
                objectMapper.createObjectNode().put("error", periodMessage).toString());
        if (openWAHandler.getSessionId() == null) {
            return ResponseEntity.badRequest().body(
                    objectMapper.createObjectNode().put("error", "Session not connected. Click Connect first!").toString());
        }
        if (!openWAHandler.isSessionReady()) {
            return ResponseEntity.badRequest().body(
                    objectMapper.createObjectNode().put("error", "The registered WhatsApp number is not connected.").toString());
        }

        String validationError = validateBulkOptions(phoneLimit, delayMs);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(objectMapper.createObjectNode().put("error", validationError).toString());
        }

        String activeSessionId = openWAHandler.getSessionId();
        if (!bulkDocumentService.tryStartBulk(activeSessionId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(objectMapper.createObjectNode()
                    .put("error", "A bulk send is already running for this WhatsApp connection.").toString());
        }

        try {
            byte[] fileBytes;
            String mimeType;
            String filename;
            if (approvedMessageId != null) {
                ApprovedMessageClient.ApprovedMessage approved = approvedMessageClient.get(approvedMessageId);
                if (approved == null || approved.files() == null || approved.files().isEmpty()) {
                    return ResponseEntity.badRequest().body(objectMapper.createObjectNode()
                            .put("error", "The selected approved message has no attachment").toString());
                }
                ApprovedMessageClient.Attachment attachment = approvedMessageClient.getAttachment(approvedMessageId, approved.files().get(0));
                fileBytes = attachment.bytes();
                mimeType = attachment.contentType();
                filename = attachment.fileName();
                caption = approved.content();
            } else {
                if (documentFile == null || documentFile.isEmpty()) {
                    return ResponseEntity.badRequest().body(objectMapper.createObjectNode().put("error", "Document file is required").toString());
                }
                fileBytes = documentFile.getBytes();
                mimeType = documentFile.getContentType();
                filename = documentFile.getOriginalFilename();
            }
            if (caption == null || caption.isBlank()) caption = "Dear User, please find attached document.";
            // Step 1: Read phone numbers from CSV file
            List<String> phoneNumbers = applyPhoneLimit(contactService.recipients(csvFile, contactIds), phoneLimit);

            if (phoneNumbers.isEmpty()) {
                return ResponseEntity.badRequest().body(
                        objectMapper.createObjectNode().put("error", "No valid phone numbers found in CSV file").toString());
            }

            log.info("Found {} phone numbers in CSV file", phoneNumbers.size());

            // Step 2: Convert document to Base64
            if (mimeType != null && mimeType.contains("video")) {
                fileBytes = videoCompressor.compressVideo(fileBytes);
            }
            String base64Content = Base64.getEncoder().encodeToString(fileBytes);

            BulkDocumentResponse bulkResponse =
                    bulkDocumentService.sendDocuments(
                            phoneNumbers,
                            base64Content,
                            mimeType,
                            filename,
                            caption,
                            delayMs,
                            bulkCooldownAfter,
                            bulkCooldownMs
                    );

            if (approvedMessageId != null) {
                List<String> successfulRecipients = bulkResponse.getDetails().stream()
                        .filter(detail -> "success".equals(detail.get("status")))
                        .map(detail -> String.valueOf(detail.get("phoneNumber")))
                        .toList();
                recordBulkReportSafely(approvedMessageId, csvFile, successfulRecipients);
            }

            ObjectNode response =
                    objectMapper.createObjectNode();

            response.put("success", true);
            response.put(
                    "totalRecipients",
                    bulkResponse.getTotalRecipients()
            );

            response.put(
                    "successCount",
                    bulkResponse.getSuccessCount()
            );

            response.put(
                    "failureCount",
                    bulkResponse.getFailureCount()
            );

            response.put(
                    "documentName",
                    bulkResponse.getDocumentName()
            );

            response.put(
                    "caption",
                    bulkResponse.getCaption()
            );

            response.set(
                    "details",
                    objectMapper.valueToTree(
                            bulkResponse.getDetails()
                    )
            );


            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(response));

        } catch (org.springframework.web.server.ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to send bulk documents: ", e);
            return ResponseEntity.status(500).body(
                    objectMapper.createObjectNode().put("error", "Failed to send bulk documents: " + e.getMessage()).toString());
        } finally {
            bulkDocumentService.finishBulk(activeSessionId);
        }
    }


    // Connect endpoint - creates and starts session automatically
    @PostMapping("/connect")
    public ResponseEntity<Map<String, String>> connect() throws IOException {
        String periodMessage = getStringResponseEntity();
        if (periodMessage != null)
        {
            Map<String, String> error = new HashMap<>();
            error.put("error", periodMessage);
            return ResponseEntity.status(500).body(error);
        }

        try {
            if (openWAHandler.registeredMobile() == null || openWAHandler.registeredMobile().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "No mobile number is registered for this account. Contact the administrator."));
            }
            // OpenWA is the source of truth: reuse its persisted session.
            String sessionId = openWAHandler.getSessionId();
            if (sessionId != null && openWAHandler.isSessionReady()) {
                Map<String, String> response = new HashMap<>();
                response.put("status", "already_connected");
                response.put("message", "Session already connected!");
                return ResponseEntity.ok(response);
            }
            HttpHeaders headers = openWAHandler.getHttpHeaders();
            if (sessionId == null) {
                // No persisted session exists for this user, so create one.
                String createSessionUrl = openwaConfig.getUrl() + "/sessions";
                Map<String, Object> sessionConfig = new HashMap<>();
                sessionConfig.put("name", openWAHandler.getSessionName());

                Map<String, Object> config = new HashMap<>();
                config.put("autoReconnect", true);
                config.put("autoRefresh", true);
                config.put("qrRefreshS", 120);
                config.put("qrTimeout", 600);
                config.put("authTimeout", 600);
                sessionConfig.put("config", config);

                HttpEntity<Map<String, Object>> createEntity = new HttpEntity<>(sessionConfig, headers);
                try {
                    ResponseEntity<String> createResponseRaw = restTemplate.exchange(
                            createSessionUrl, HttpMethod.POST, createEntity, String.class);
                    JsonNode createResponse = objectMapper.readTree(createResponseRaw.getBody());
                    sessionId = createResponse.path("id").asText(null);
                } catch (HttpClientErrorException.Conflict conflict) {
                    // A concurrent request may have created it after our lookup.
                    sessionId = openWAHandler.getSessionId();
                    if (sessionId == null) {
                        throw conflict;
                    }
                    log.info("Reusing existing OpenWA session '{}' ({}) after create conflict",
                            openWAHandler.getSessionName(), sessionId);
                }
            }
            if (sessionId == null || sessionId.isBlank()) {
                throw new IllegalStateException("OpenWA did not return a session ID");
            }
            Thread.sleep(3000);
            // Step 2: Start the session
            String startSessionUrl = openwaConfig.getUrl() + "/sessions/" + sessionId + "/start";
            HttpEntity<String> startEntity = new HttpEntity<>(headers);
            ResponseEntity<String> startResponseRaw = restTemplate.exchange(
                    startSessionUrl, HttpMethod.POST, startEntity, String.class);
            JsonNode startResponse = objectMapper.readTree(startResponseRaw.getBody());
            Thread.sleep(3000);
            // Step 3: Get QR code
            String qrUrl = openwaConfig.getUrl() + "/sessions/" + sessionId + "/qr";
            ResponseEntity<String> qrResponseRaw = restTemplate.exchange(
                    qrUrl, HttpMethod.GET, startEntity, String.class);
            JsonNode qrResponse = objectMapper.readTree(qrResponseRaw.getBody());
            Map<String, String> response = new HashMap<>();
            response.put("qrCode", qrResponse.get("qrCode").asText());
            response.put("sessionId", sessionId);
            response.put("status", startResponse.get("status").asText());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to connect: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    @PostMapping("/pairing-code")
    public ResponseEntity<Map<String, String>> pairingCode() throws IOException {
        String periodMessage = getStringResponseEntity();
        if (periodMessage != null) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of("error", periodMessage));
        }
        String registeredMobile = openWAHandler.registeredMobile();
        if (registeredMobile == null || registeredMobile.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "No mobile number is registered for this account. Contact the administrator."));
        }
        try {
            ResponseEntity<Map<String, String>> prepared = connect();
            if (!prepared.getStatusCode().is2xxSuccessful()) return prepared;
            Map<String, String> preparedBody = prepared.getBody();
            if (preparedBody != null && "already_connected".equals(preparedBody.get("status"))) {
                return ResponseEntity.ok(preparedBody);
            }
            String sessionId = openWAHandler.getSessionId();
            if (sessionId == null || sessionId.isBlank()) {
                throw new IllegalStateException("OpenWA session is not available");
            }
            String digits = registeredMobile.replaceAll("\\D", "");
            if (digits.length() == 10) digits = "91" + digits;
            if (digits.length() < 11) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Registered mobile number must include a valid country code."));
            }
            Map<String, String> request = Map.of("phoneNumber", digits);
            ResponseEntity<String> openwaResponse = restTemplate.exchange(
                    openwaConfig.getUrl() + "/sessions/" + sessionId + "/pairing-code",
                    HttpMethod.POST, new HttpEntity<>(request, openWAHandler.getHttpHeaders()), String.class);
            JsonNode json = objectMapper.readTree(openwaResponse.getBody());
            String code = json.path("pairingCode").asText("");
            if (code.isBlank()) throw new IllegalStateException("OpenWA did not return a pairing code");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "pairingCode", code,
                    "status", json.path("status").asText("qr_ready"),
                    "mobileNumber", digits,
                    "sessionId", sessionId));
        } catch (Exception e) {
            log.error("Unable to generate WhatsApp pairing code", e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "Unable to generate pairing code: " + e.getMessage()));
        }
    }

    // Check session status
    @GetMapping("/status")
    public ResponseEntity<Map<String, String>> getStatus(@RequestParam(required = false) String action) throws IOException {
        String periodMessage = getStringResponseEntity();
        if (periodMessage != null)
        {
            Map<String, String> error = new HashMap<>();
            error.put("error",  periodMessage);
            return ResponseEntity.status(500).body(error);
        }
        if (openWAHandler.getSessionId() == null) {
            Map<String, String> response = new HashMap<>();
            response.put("status", "not_connected");
            response.put("message", "Please click Connect button first");
            return ResponseEntity.ok(response);
        }

        try {
            Thread.sleep(3000);
            String url = openwaConfig.getUrl() + "/sessions/" + openWAHandler.getSessionId();
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-API-Key", openwaConfig.getApiKey());
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> responseRaw = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);
            JsonNode response = objectMapper.readTree(responseRaw.getBody());

            if (response.isEmpty())
            {
                Map<String, String> result = new HashMap<>();
                result.put("status", "not_connected");
                return ResponseEntity.ok(result);
            }
            if ("check".equalsIgnoreCase(action)) {
                if ("null".equals(response.get("phone").asText())) {
                    bulkDocumentService.disconnect();
                    Map<String, String> result = new HashMap<>();
                    result.put("status", "not_connected");
                    return ResponseEntity.ok(result);

                }
            }
            String linkedPhone = response.path("phone").asText("");
            if ("ready".equals(response.path("status").asText())
                    && !openWAHandler.isLinkedPhoneAllowed(linkedPhone)) {
                String registeredMobile = openWAHandler.registeredMobile();
                bulkDocumentService.disconnect();
                Map<String, String> result = new HashMap<>();
                result.put("status", "number_mismatch");
                result.put("message", "This account may only link WhatsApp number " + registeredMobile
                        + ". The attempted number " + linkedPhone + " was disconnected.");
                result.put("registeredMobile", registeredMobile);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
            }
            Map<String, String> result = new HashMap<>();

            result.put("status", response.get("status").asText());
            if (response.has("phone")) {
                result.put("phone", response.get("phone").asText());
            }
            if (response.has("pushName")) {
                result.put("pushName", response.get("pushName").asText());
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    // 1. Send text message
    @PostMapping("/send-text")
    public ResponseEntity<String> sendText(@RequestBody Map<String, String> request) throws IOException {
        String periodMessage = getStringResponseEntity();
        if (periodMessage != null) return ResponseEntity.badRequest().body(
                objectMapper.createObjectNode().put("error", periodMessage).toString());
        if (openWAHandler.getSessionId() == null) {
            return ResponseEntity.badRequest().body(
                    objectMapper.createObjectNode().put("error", "Session not connected. Click Connect first!").toString());
        }
        if (!openWAHandler.isSessionReady()) {
            return ResponseEntity.badRequest().body(
                    objectMapper.createObjectNode().put("error", "The registered WhatsApp number is not connected.").toString());
        }
        try {
        String url = openwaConfig.getUrl() + "/sessions/" + openWAHandler.getSessionId() + "/messages/send-text";
            HttpHeaders headers = openWAHandler.getHttpHeaders();

            Map<String, String> body = new HashMap<>();
        body.put("chatId", csvReader.validateAndFormatNumber(request.get("chatId")) );
        body.put("text", request.get("text"));

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> responseRaw = restTemplate.exchange(
                url, HttpMethod.POST, entity, String.class);
        return ResponseEntity.ok(responseRaw.getBody());
        } catch (Exception e) {

            ObjectNode error = objectMapper.createObjectNode();
            error.put("error", "Failed to send message: " + e.getMessage());
            return ResponseEntity.status(500).body(error.toString());
        }
    }

    // 2. Send bulk messages
    @PostMapping("/send-bulk-text")
    public ResponseEntity<String> sendBulkText(
            @RequestParam(value = "csv", required = false) MultipartFile csvFile,
            @RequestParam(value = "contactIds", required = false) List<Long> contactIds,
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "approvedMessageId", required = false) Long approvedMessageId,
            @RequestParam(value = "phoneLimit", required = false) Integer phoneLimit,
            @RequestParam(value = "delayMs", required = false, defaultValue = "3000") int delayMs,
            @RequestParam(value = "randomizeDelay", required = false, defaultValue = "false") boolean randomizeDelay) throws IOException {
        String periodMessage = getStringResponseEntity();
        if (periodMessage != null) return ResponseEntity.badRequest().body(
                objectMapper.createObjectNode().put("error", periodMessage).toString());
        if (openWAHandler.getSessionId() == null) {
            return ResponseEntity.badRequest().body(
                    objectMapper.createObjectNode().put("error", "Session not connected. Click Connect first!").toString());
        }

        if (!openWAHandler.isSessionReady()) {
            return ResponseEntity.badRequest().body(
                    objectMapper.createObjectNode().put("error", "Session is not ready. Please check connection.").toString());
        }

        String validationError = validateBulkOptions(phoneLimit, delayMs);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(objectMapper.createObjectNode().put("error", validationError).toString());
        }

        try {
            if (approvedMessageId != null) {
                ApprovedMessageClient.ApprovedMessage approved = approvedMessageClient.get(approvedMessageId);
                message = approved == null ? null : approved.content();
            }
            if (message == null || message.isBlank()) {
                return ResponseEntity.badRequest().body(objectMapper.createObjectNode().put("error", "Approved message is required").toString());
            }
            // Read recipients from CSV file or selected contacts
            List<com.sms.service.ContactService.RecipientDetail> recipientDetails = contactService.recipientsDetailed(csvFile, contactIds);
            if (phoneLimit != null && phoneLimit > 0 && recipientDetails.size() > phoneLimit) {
                recipientDetails = recipientDetails.subList(0, phoneLimit);
            }

            // Filter for valid WhatsApp phone numbers
            List<com.sms.service.ContactService.RecipientDetail> validPhoneRecipients = new ArrayList<>();
            List<String> phoneNumbers = new ArrayList<>();
            for (com.sms.service.ContactService.RecipientDetail r : recipientDetails) {
                if (r.formattedChatId() != null && !r.formattedChatId().isBlank()) {
                    validPhoneRecipients.add(r);
                    phoneNumbers.add(r.formattedChatId());
                }
            }

            if (validPhoneRecipients.isEmpty()) {
                return ResponseEntity.badRequest().body(
                        objectMapper.createObjectNode().put("error", "No valid phone numbers found in CSV file or selected contacts").toString());
            }

            log.info("Found {} phone numbers for WhatsApp bulk send", validPhoneRecipients.size());

            // ✅ Build the bulk payload for ALL messages at once with personalized greeting
            List<Map<String, Object>> messages = new ArrayList<>();

            for (com.sms.service.ContactService.RecipientDetail recipient : validPhoneRecipients) {
                Map<String, Object> messageBody = new HashMap<>();
                messageBody.put("chatId", recipient.formattedChatId());
                messageBody.put("type", "text");

                String greetingName = (recipient.name() != null && !recipient.name().isBlank())
                        ? recipient.name().strip()
                        : "User";
                String personalizedMessage = "Dear " + greetingName + ",\n\n" + message;

                Map<String, String> content = new HashMap<>();
                content.put("text", personalizedMessage);
                messageBody.put("content", content);

                messages.add(messageBody);
            }

            // ✅ Create the complete payload for OpenWA
            Map<String, Object> payload = new HashMap<>();
            payload.put("messages", messages);

            Map<String, Object> options = new HashMap<>();
            options.put("delayBetweenMessages", delayMs);
            options.put("randomizeDelay", randomizeDelay);
            options.put("stopOnError", false);
            payload.put("options", options);

            // ✅ Send ONE request to OpenWA with ALL messages
            HttpHeaders headers = openWAHandler.getHttpHeaders();

            String url = openwaConfig.getUrl() + "/sessions/" + openWAHandler.getSessionId() + "/messages/send-bulk";
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            log.info("Sending bulk request with {} messages", messages.size());
            log.debug("Payload: {}", objectMapper.writeValueAsString(payload));

            ResponseEntity<String> responseRaw = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);

            JsonNode response = objectMapper.readTree(responseRaw.getBody());

            if (approvedMessageId != null) {
                recordBulkReportSafely(approvedMessageId, csvFile, phoneNumbers);
            }

            // Build response summary
            ObjectNode responseNode = objectMapper.createObjectNode();
            responseNode.put("success", true);
            responseNode.put("totalRecipients", phoneNumbers.size());
            responseNode.put("batchId", response.has("batchId") ? response.get("batchId").asText() : "N/A");
            responseNode.put("status", response.has("status") ? response.get("status").asText() : "processing");
            responseNode.put("message", message);
            responseNode.put("delayMs", delayMs);
            responseNode.put("randomizeDelay", randomizeDelay);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(responseNode));

        } catch (org.springframework.web.server.ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to send bulk messages: ", e);
            return ResponseEntity.status(500).body(
                    objectMapper.createObjectNode().put("error", "Failed to send bulk messages: " + e.getMessage()).toString());
        }
    }

    // 2b. Send bulk plain text messages (unformatted, direct plain text via OpenWA free engine)
    @PostMapping("/send-bulk-plain-text")
    public ResponseEntity<String> sendBulkPlainText(
            @RequestParam(value = "csv", required = false) MultipartFile csvFile,
            @RequestParam(value = "contactIds", required = false) List<Long> contactIds,
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "approvedMessageId", required = false) Long approvedMessageId,
            @RequestParam(value = "phoneLimit", required = false) Integer phoneLimit,
            @RequestParam(value = "delayMs", required = false, defaultValue = "3000") int delayMs,
            @RequestParam(value = "randomizeDelay", required = false, defaultValue = "false") boolean randomizeDelay) throws IOException {
        String periodMessage = getStringResponseEntity();
        if (periodMessage != null) return ResponseEntity.badRequest().body(
                objectMapper.createObjectNode().put("error", periodMessage).toString());
        if (openWAHandler.getSessionId() == null) {
            return ResponseEntity.badRequest().body(
                    objectMapper.createObjectNode().put("error", "Session not connected. Click Connect first!").toString());
        }

        if (!openWAHandler.isSessionReady()) {
            return ResponseEntity.badRequest().body(
                    objectMapper.createObjectNode().put("error", "Session is not ready. Please check connection.").toString());
        }

        String validationError = validateBulkOptions(phoneLimit, delayMs);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(objectMapper.createObjectNode().put("error", validationError).toString());
        }

        try {
            if (approvedMessageId != null) {
                ApprovedMessageClient.ApprovedMessage approved = approvedMessageClient.get(approvedMessageId);
                message = approved == null ? null : approved.content();
            }
            if (message == null || message.isBlank()) {
                return ResponseEntity.badRequest().body(objectMapper.createObjectNode().put("error", "Approved message is required").toString());
            }

            // Strip formatting if any to guarantee plain text delivery
            String plainTextMessage = message.trim();

            List<com.sms.service.ContactService.RecipientDetail> recipientDetails = contactService.recipientsDetailed(csvFile, contactIds);
            if (phoneLimit != null && phoneLimit > 0 && recipientDetails.size() > phoneLimit) {
                recipientDetails = recipientDetails.subList(0, phoneLimit);
            }

            List<com.sms.service.ContactService.RecipientDetail> validPhoneRecipients = new ArrayList<>();
            List<String> phoneNumbers = new ArrayList<>();
            for (com.sms.service.ContactService.RecipientDetail r : recipientDetails) {
                if (r.formattedChatId() != null && !r.formattedChatId().isBlank()) {
                    validPhoneRecipients.add(r);
                    phoneNumbers.add(r.formattedChatId());
                }
            }

            if (validPhoneRecipients.isEmpty()) {
                return ResponseEntity.badRequest().body(
                        objectMapper.createObjectNode().put("error", "No valid phone numbers found in CSV file or selected contacts").toString());
            }

            log.info("Found {} phone numbers for plain text bulk send", validPhoneRecipients.size());

            List<Map<String, Object>> messages = new ArrayList<>();
            for (com.sms.service.ContactService.RecipientDetail recipient : validPhoneRecipients) {
                Map<String, Object> messageBody = new HashMap<>();
                messageBody.put("chatId", recipient.formattedChatId());
                messageBody.put("type", "text");

                Map<String, String> content = new HashMap<>();
                content.put("text", plainTextMessage);
                messageBody.put("content", content);

                messages.add(messageBody);
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("messages", messages);

            Map<String, Object> options = new HashMap<>();
            options.put("delayBetweenMessages", delayMs);
            options.put("randomizeDelay", randomizeDelay);
            options.put("stopOnError", false);
            payload.put("options", options);

            HttpHeaders headers = openWAHandler.getHttpHeaders();
            String url = openwaConfig.getUrl() + "/sessions/" + openWAHandler.getSessionId() + "/messages/send-bulk";
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            ResponseEntity<String> responseRaw = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            JsonNode response = objectMapper.readTree(responseRaw.getBody());

            if (approvedMessageId != null) {
                recordBulkReportSafely(approvedMessageId, csvFile, phoneNumbers);
            }

            ObjectNode responseNode = objectMapper.createObjectNode();
            responseNode.put("success", true);
            responseNode.put("totalRecipients", phoneNumbers.size());
            responseNode.put("batchId", response.has("batchId") ? response.get("batchId").asText() : "N/A");
            responseNode.put("status", response.has("status") ? response.get("status").asText() : "processing");
            responseNode.put("message", plainTextMessage);
            responseNode.put("delayMs", delayMs);
            responseNode.put("randomizeDelay", randomizeDelay);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(responseNode));

        } catch (org.springframework.web.server.ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to send bulk plain text messages: ", e);
            return ResponseEntity.status(500).body(
                    objectMapper.createObjectNode().put("error", "Failed to send bulk plain text: " + e.getMessage()).toString());
        }
    }

    // 2c. Send bulk MMS (Media / Image / Video / Document attachment with caption via free OpenWA)
    @PostMapping("/send-bulk-mms")
    public ResponseEntity<String> sendBulkMms(
            @RequestParam(value = "csv", required = false) MultipartFile csvFile,
            @RequestParam(value = "contactIds", required = false) List<Long> contactIds,
            @RequestParam(value = "approvedMessageId", required = false) Long approvedMessageId,
            @RequestParam(value = "phoneLimit", required = false) Integer phoneLimit,
            @RequestParam(value = "delayMs", required = false, defaultValue = "3000") long delayMs) throws IOException {
        return sendDocumentBulk(csvFile, null, contactIds, null, approvedMessageId, phoneLimit, delayMs);
    }

    @PostMapping("/send-bulk-email")
    public ResponseEntity<String> sendBulkEmail(
            @RequestParam(value = "csv", required = false) MultipartFile csvFile,
            @RequestParam(value = "contactIds", required = false) List<Long> contactIds,
            @RequestParam(value = "approvedMessageId", required = false) Long approvedMessageId,
            @RequestParam(value = "emailLimit", required = false) Integer emailLimit,
            @RequestParam(value = "delayMs", required = false, defaultValue = "1000") long delayMs) throws IOException {

        String periodMessage = getStringResponseEntity();
        if (periodMessage != null) {
            return ResponseEntity.badRequest().body(
                    objectMapper.createObjectNode().put("error", periodMessage).toString());
        }

        if (delayMs < 0) delayMs = 1000;
        if (emailLimit != null && (emailLimit <= 0 || emailLimit > maxBulkRecipients)) {
            return ResponseEntity.badRequest().body(
                    objectMapper.createObjectNode().put("error", "Email limit must be between 1 and " + maxBulkRecipients).toString());
        }

        try {
            String message = null;
            String subject = "Important Notice";
            byte[] attachmentBytes = null;
            String attachmentName = null;
            String attachmentType = null;

            if (approvedMessageId != null) {
                ApprovedMessageClient.ApprovedMessage approved = approvedMessageClient.get(approvedMessageId);
                if (approved != null) {
                    message = approved.content();
                    if (approved.title() != null && !approved.title().isBlank()) {
                        subject = approved.title();
                    }
                    if (approved.files() != null && !approved.files().isEmpty()) {
                        try {
                            ApprovedMessageClient.Attachment att = approvedMessageClient.getAttachment(approvedMessageId, 0, approved.files().get(0));
                            if (att != null) {
                                attachmentBytes = att.bytes();
                                attachmentName = att.fileName();
                                attachmentType = att.contentType();
                            }
                        } catch (Exception ex) {
                            log.warn("Could not load attachment for email: {}", ex.getMessage());
                        }
                    }
                }
            }

            if (message == null || message.isBlank()) {
                return ResponseEntity.badRequest().body(
                        objectMapper.createObjectNode().put("error", "Approved message is required").toString());
            }

            List<com.sms.service.ContactService.RecipientDetail> detailedRecipients = contactService.recipientsDetailed(csvFile, contactIds);
            List<com.sms.service.EmailService.EmailRecipient> emailRecipients = new ArrayList<>();

            for (com.sms.service.ContactService.RecipientDetail r : detailedRecipients) {
                if (r.email() != null && !r.email().isBlank() && r.email().contains("@")) {
                    emailRecipients.add(new com.sms.service.EmailService.EmailRecipient(r.name(), r.email()));
                }
            }

            if (emailLimit != null && emailLimit > 0 && emailRecipients.size() > emailLimit) {
                emailRecipients = emailRecipients.subList(0, emailLimit);
            }

            if (emailRecipients.isEmpty()) {
                return ResponseEntity.badRequest().body(
                        objectMapper.createObjectNode().put("error", "No recipients with valid email addresses found in the selected contacts or CSV file.").toString());
            }

            com.sms.service.EmailService.EmailBatchResult result = emailService.sendBulkEmails(
                    emailRecipients,
                    subject,
                    message,
                    attachmentBytes,
                    attachmentName,
                    attachmentType,
                    delayMs
            );

            ObjectNode res = objectMapper.createObjectNode();
            res.put("success", !"failed".equalsIgnoreCase(result.status()));
            res.put("batchId", result.batchId());
            res.put("status", result.status());
            res.put("totalRecipients", result.totalRecipients());
            res.put("sentMessages", result.sentCount());
            res.put("failedMessages", result.failedCount());
            res.put("message", result.message());
            res.set("failedDetails", objectMapper.valueToTree(result.failedDetails()));

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(res));

        } catch (org.springframework.web.server.ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to send bulk emails: ", e);
            return ResponseEntity.status(500).body(
                    objectMapper.createObjectNode().put("error", "Failed to send bulk emails: " + e.getMessage()).toString());
        }
    }

    private List<String> applyPhoneLimit(List<String> phoneNumbers, Integer phoneLimit) {
        if (phoneNumbers.size() > maxBulkRecipients && phoneLimit == null) {
            throw new IllegalArgumentException("Recipient count exceeds the configured maximum of " + maxBulkRecipients);
        }
        if (phoneLimit == null) return phoneNumbers;
        if (phoneLimit <= 0) throw new IllegalArgumentException("Phone limit must be greater than zero");
        if (phoneLimit > maxBulkRecipients) {
            throw new IllegalArgumentException("Phone limit cannot exceed " + maxBulkRecipients);
        }
        return phoneNumbers.stream().limit(phoneLimit).toList();
    }

    private String validateBulkOptions(Integer phoneLimit, long delayMs) {
        if (phoneLimit != null && (phoneLimit <= 0 || phoneLimit > maxBulkRecipients)) {
            return "Phone limit must be between 1 and " + maxBulkRecipients + ".";
        }
        if (delayMs < minBulkDelayMs) {
            return "Delay between messages must be at least " + minBulkDelayMs + " ms.";
        }
        return null;
    }

    private void recordBulkReportSafely(Long approvedMessageId, MultipartFile csvFile,
            List<String> recipients) {
        if (recipients == null || recipients.isEmpty()) return;
        try {
            String csvSha256 = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(csvFile == null
                            ? String.join("\n", recipients).getBytes(java.nio.charset.StandardCharsets.UTF_8) : csvFile.getBytes()));
            int recorded = approvedMessageClient.recordBulkReport(approvedMessageId,
                    csvFile == null ? "selected-contacts.csv" : csvFile.getOriginalFilename(), csvSha256, recipients);
            log.info("Recorded {} bulk-message report rows for approved message {}", recorded, approvedMessageId);
        } catch (Exception e) {
            log.error("WhatsApp send succeeded, but bulk-message reporting failed for approved message {}",
                    approvedMessageId, e);
        }
    }

    // 3. Send document/file
    @PostMapping("/send-document")
    public ResponseEntity<String> sendDocument(
            @RequestParam String chatId,
            @RequestParam String caption,
            @RequestParam MultipartFile file) throws IOException {
        String periodMessage = getStringResponseEntity();
        if (periodMessage != null) return ResponseEntity.badRequest().body(
                objectMapper.createObjectNode().put("error", periodMessage).toString());
        if (openWAHandler.getSessionId() == null) {
            return ResponseEntity.badRequest().body(
                    objectMapper.createObjectNode().put("error", "Session not connected. Click Connect first!").toString());
        }
        if (!openWAHandler.isSessionReady()) {
            return ResponseEntity.badRequest().body(
                    objectMapper.createObjectNode().put("error", "The registered WhatsApp number is not connected.").toString());
        }

        try {
            String endpoint = isImage(file.getContentType(), file.getOriginalFilename())
                    ? "/messages/send-image"
                    : "/messages/send-document";
            String url = openwaConfig.getUrl() + "/sessions/" + openWAHandler.getSessionId() + endpoint;
            HttpHeaders headers = openWAHandler.getHttpHeaders();

            // Convert file to Base64
            byte[] fileBytes = file.getBytes();
            if (file.getContentType() != null && file.getContentType().contains("video")) {
                fileBytes = videoCompressor.compressVideo(file.getBytes());
            }

            String base64Content = Base64.getEncoder().encodeToString(fileBytes);

            Map<String, String> body = new HashMap<>();
            body.put("chatId", csvReader.validateAndFormatNumber(chatId));
            body.put("base64", base64Content);
            body.put("mimetype", file.getContentType());
            body.put("filename", file.getOriginalFilename());
            body.put("caption", caption);

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> responseRaw = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);
            return ResponseEntity.ok(responseRaw.getBody());

        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                    objectMapper.createObjectNode().put("error", "Failed to send document: " + e.getMessage()).toString());
        }
    }

    private boolean isImage(String mimeType, String filename) {
        if (mimeType != null && mimeType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return true;
        }
        if (filename == null) {
            return false;
        }
        String lowerFilename = filename.toLowerCase(Locale.ROOT);
        return lowerFilename.endsWith(".jpg")
                || lowerFilename.endsWith(".jpeg")
                || lowerFilename.endsWith(".png")
                || lowerFilename.endsWith(".gif")
                || lowerFilename.endsWith(".webp");
    }

    private @Nullable String getStringResponseEntity() throws IOException {
        if (trialService.isExpired()) {
            return "Trial period has expired.";
        }
        return null;
    }

}
