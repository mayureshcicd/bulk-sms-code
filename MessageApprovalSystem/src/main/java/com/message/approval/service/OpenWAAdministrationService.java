package com.message.approval.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.message.approval.domain.MonitoredWhatsAppSession;
import com.message.approval.repository.MonitoredWhatsAppSessionRepository;

@Service
public class OpenWAAdministrationService {
    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Kolkata");
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final MonitoredWhatsAppSessionRepository monitoredRepository;

    @Value("${openwa.api.base-url:http://localhost:2785/api}")
    private String baseUrl;
    @Value("${openwa.api.key:}")
    private String apiKey;
    @Value("${openwa.webhook.url:http://message-approve-api:8081/api/openwa/webhook}")
    private String webhookUrl;
    @Value("${openwa.webhook.secret}")
    private String webhookSecret;

    public OpenWAAdministrationService(RestTemplate restTemplate, ObjectMapper objectMapper,
            MonitoredWhatsAppSessionRepository monitoredRepository) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.monitoredRepository = monitoredRepository;
    }

    public List<OpenWASession> listSessions() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/sessions?limit=100&offset=0", HttpMethod.GET,
                new HttpEntity<>(headers()), String.class);
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode rows = root != null && root.isArray() ? root
                    : firstArray(root, "items", "data", "sessions");
            List<OpenWASession> sessions = new ArrayList<>();
            if (rows != null) {
                rows.forEach(node -> {
                    String id = text(node, "id", "sessionId");
                    if (id == null) return;
                    String phone = normalizeMobile(text(node, "phone", "phoneNumber", "wid"));
                    String name = text(node, "name", "sessionName");
                    String status = text(node, "status", "state");
                    boolean monitored = monitoredRepository.findBySessionIdAndEnabledTrue(id).isPresent();
                    sessions.add(new OpenWASession(id, name == null ? id : name,
                            phone == null ? "" : phone, status == null ? "unknown" : status, monitored));
                });
            }
            return sessions;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read OpenWA sessions", e);
        }
    }

    @Transactional
    public MonitoredWhatsAppSession enable(String sessionId) {
        OpenWASession session = listSessions().stream()
                .filter(item -> item.id().equals(sessionId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("OpenWA session not found"));
        if (session.mobileNumber().isBlank()) {
            throw new IllegalStateException("This OpenWA session has no linked mobile number");
        }
        MonitoredWhatsAppSession monitored = monitoredRepository.findBySessionId(sessionId)
                .orElseGet(MonitoredWhatsAppSession::new);
        if (monitored.isEnabled() && monitored.getWebhookId() != null) return monitored;

        removeExistingReceiverWebhooks(sessionId);
        Map<String, Object> request = new HashMap<>();
        request.put("url", webhookUrl);
        request.put("events", List.of("message.received", "message.edited", "message.revoked", "session.status"));
        request.put("secret", webhookSecret);
        request.put("headers", Map.of("X-Webhook-Source", "openwa"));
        request.put("retryCount", 3);
        ResponseEntity<JsonNode> response = restTemplate.exchange(baseUrl + "/sessions/" + sessionId + "/webhooks",
                HttpMethod.POST, new HttpEntity<>(request, headers()), JsonNode.class);
        JsonNode created = response.getBody();
        String webhookId = created == null ? null : text(created, "id", "webhookId");
        if (webhookId == null || webhookId.isBlank()) {
            throw new IllegalStateException("OpenWA created the webhook without returning its ID");
        }
        monitored.setSessionId(session.id());
        monitored.setSessionName(session.name());
        monitored.setMobileNumber(session.mobileNumber());
        monitored.setWebhookId(webhookId);
        monitored.setEnabled(true);
        monitored.setUpdatedAt(LocalDateTime.now(REPORT_ZONE));
        return monitoredRepository.save(monitored);
    }

    @Transactional
    public void disable(String sessionId) {
        MonitoredWhatsAppSession monitored = monitoredRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Monitored session not found"));
        if (monitored.getWebhookId() != null) {
            try {
                restTemplate.exchange(baseUrl + "/sessions/" + sessionId + "/webhooks/" + monitored.getWebhookId(),
                        HttpMethod.DELETE, new HttpEntity<>(headers()), Void.class);
            } catch (HttpClientErrorException.NotFound ignored) { }
        }
        monitored.setEnabled(false);
        monitored.setWebhookId(null);
        monitored.setUpdatedAt(LocalDateTime.now(REPORT_ZONE));
        monitoredRepository.save(monitored);
    }

    public List<MonitoredWhatsAppSession> monitoredSessions() {
        return monitoredRepository.findAllByOrderByMobileNumberAsc();
    }

    private void removeExistingReceiverWebhooks(String sessionId) {
        ResponseEntity<JsonNode> response = restTemplate.exchange(baseUrl + "/sessions/" + sessionId + "/webhooks",
                HttpMethod.GET, new HttpEntity<>(headers()), JsonNode.class);
        JsonNode rows = response.getBody();
        if (rows == null || !rows.isArray()) return;
        rows.forEach(node -> {
            if (webhookUrl.equals(node.path("url").asText())) {
                String id = node.path("id").asText();
                if (!id.isBlank()) {
                    restTemplate.exchange(baseUrl + "/sessions/" + sessionId + "/webhooks/" + id,
                            HttpMethod.DELETE, new HttpEntity<>(headers()), Void.class);
                }
            }
        });
    }

    private HttpHeaders headers() {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("OPENWA API key is not configured");
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", apiKey);
        return headers;
    }

    private JsonNode firstArray(JsonNode root, String... fields) {
        if (root == null) return null;
        for (String field : fields) if (root.path(field).isArray()) return root.path(field);
        return null;
    }

    private String text(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = node.path(field).asText(null);
            if (value != null && !value.isBlank() && !"null".equals(value)) return value;
        }
        return null;
    }

    private String normalizeMobile(String value) {
        if (value == null) return null;
        String beforeAt = value.contains("@") ? value.substring(0, value.indexOf('@')) : value;
        String digits = beforeAt.replaceAll("\\D", "");
        return digits.isBlank() ? null : digits;
    }

    public record OpenWASession(String id, String name, String mobileNumber, String status, boolean monitored) { }
}
