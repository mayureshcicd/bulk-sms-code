package com.sms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sms.config.OpenWAConfig;
import com.sms.dto.BulkDocumentResponse;
import com.sms.util.CsvReader;
import com.sms.util.OpenWAHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulkDocumentService {

    private final Set<String> activeBulkSessions = ConcurrentHashMap.newKeySet();

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final OpenWAConfig openwaConfig;
    private final OpenWAHandler openWAHandler;
    private final CsvReader csvReader;

    public String batchStatus(String batchId)   {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("status", "pending");
        result.put("sentMessages", "0");
        result.put("totalMessages", "0");
        result.put("failedMessages", "0");
        String url = openwaConfig.getUrl()
                + "/sessions/"
                + openWAHandler.getSessionId()
                + "/messages/batch/"
                + batchId;

        HttpHeaders headers = openWAHandler.getHttpHeaders();

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        try {
            JsonNode node= objectMapper.readTree(response.getBody());
            JsonNode progress = node.path("progress");

            result.put("status", node.path("status").asText());
            result.put("sentMessages", progress.path("sent").asInt());
            result.put("totalMessages", progress.path("total").asInt());
            result.put("failedMessages", progress.path("failed").asInt());

        } catch (JsonProcessingException e) {
            log.error("Error fetching batch status", e);
        }
        return result.toString();
    }
    public String disconnect()
    {
        String url =
                openwaConfig.getUrl()
                        + "/sessions/"
                        + openWAHandler.getSessionId();
        HttpHeaders headers = openWAHandler.getHttpHeaders();

        ResponseEntity<Void> response = restTemplate.exchange(
                url,
                HttpMethod.DELETE,
                new HttpEntity<>(headers),
                Void.class
        );

        HttpStatus status = (HttpStatus) response.getStatusCode();

        if (status.is2xxSuccessful())
        {
            return "disconnected";
        }
        return "Failed to disconnect";

    }
    public BulkDocumentResponse sendDocuments(
            List<String> phoneNumbers,
            String base64Content,
            String mimeType,
            String filename,
            String caption,
            long delayMs,
            int cooldownAfter,
            long cooldownMs) {

        List<Map<String, Object>> results = new ArrayList<>();

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        String endpoint = resolveMediaEndpoint(mimeType, filename);
        String url =
                openwaConfig.getUrl()
                        + "/sessions/"
                        + openWAHandler.getSessionId()
                        + endpoint;

        HttpHeaders headers = openWAHandler.getHttpHeaders();

        for (int i = 0; i < phoneNumbers.size(); i++) {

            String phoneNumber = phoneNumbers.get(i);

            try {

                Map<String, String> body = new HashMap<>();
                body.put(
                        "chatId",
                        csvReader.validateAndFormatNumber(phoneNumber)
                );
                body.put("base64", base64Content);
                body.put("mimetype", mimeType);
                body.put("filename", filename);
                body.put("caption", caption);

                HttpEntity<Map<String, String>> entity =
                        new HttpEntity<>(body, headers);

                ResponseEntity<String> responseRaw =
                        restTemplate.exchange(
                                url,
                                HttpMethod.POST,
                                entity,
                                String.class);

                String responseBody = responseRaw.getBody();

                if (responseBody == null || responseBody.isBlank()) {
                    throw new RuntimeException("Empty response from OpenWA");
                }

                JsonNode response =
                        objectMapper.readTree(responseBody);

                Map<String, Object> result = new HashMap<>();
                result.put("phoneNumber", phoneNumber);
                result.put("status", "success");
                result.put(
                        "messageId",
                        response.has("messageId")
                                ? response.get("messageId").asText()
                                : "Sent"
                );

                results.add(result);

                successCount.incrementAndGet();

                log.info(
                        "Sent media using {} to {} ({}/{})",
                        endpoint,
                        phoneNumber,
                        i + 1,
                        phoneNumbers.size()
                );

            } catch (Exception ex) {

                log.error(
                        "Failed to send to {}",
                        phoneNumber,
                        ex
                );

                Map<String, Object> result =
                        new HashMap<>();

                result.put("phoneNumber", phoneNumber);
                result.put("status", "failed");
                result.put("error", ex.getMessage());

                results.add(result);

                failureCount.incrementAndGet();
            }

            if (i < phoneNumbers.size() - 1) {
                sleep(delayMs, "Delay between media messages");
                if (cooldownAfter > 0 && (i + 1) % cooldownAfter == 0) {
                    log.info("Cooling down after {} media messages", i + 1);
                    sleep(cooldownMs, "Media batch cooldown");
                }
            }
        }

        BulkDocumentResponse response =
                new BulkDocumentResponse();

        response.setTotalRecipients(phoneNumbers.size());
        response.setSuccessCount(successCount.get());
        response.setFailureCount(failureCount.get());
        response.setDocumentName(filename);
        response.setCaption(caption);
        response.setDetails(results);

        return response;
    }

    public boolean tryStartBulk(String sessionId) {
        return sessionId != null && activeBulkSessions.add(sessionId);
    }

    public void finishBulk(String sessionId) {
        if (sessionId != null) activeBulkSessions.remove(sessionId);
    }

    private void sleep(long delayMs, String reason) {
        if (delayMs <= 0) return;
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(reason + " was interrupted", ex);
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

    String resolveMediaEndpoint(String mimeType, String filename) {
        if (isImage(mimeType, filename)) {
            return "/messages/send-image";
        }
        if (isVideo(mimeType, filename)) {
            return "/messages/send-video";
        }
        return "/messages/send-document";
    }

    private boolean isVideo(String mimeType, String filename) {
        if (mimeType != null && mimeType.toLowerCase(Locale.ROOT).startsWith("video/")) {
            return true;
        }
        if (filename == null) {
            return false;
        }
        String lowerFilename = filename.toLowerCase(Locale.ROOT);
        return lowerFilename.endsWith(".mp4")
                || lowerFilename.endsWith(".mov")
                || lowerFilename.endsWith(".m4v")
                || lowerFilename.endsWith(".webm")
                || lowerFilename.endsWith(".3gp")
                || lowerFilename.endsWith(".mkv")
                || lowerFilename.endsWith(".avi");
    }
}
