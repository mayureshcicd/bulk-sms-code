package com.sms.util;

import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sms.config.OpenWAConfig;
import com.sms.domain.AppUser;
import com.sms.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenWAHandler {
    private static final String SESSION_PREFIX = "whatsappBulk-u";

    private final OpenWAConfig openwaConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    public String getSessionId() {
        return findSessionId(currentUser());
    }

    public String getSessionName() {
        AppUser user = currentUser();
        return sessionName(user.getId(), user.getMobileNumber());
    }

    public AppUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new IllegalStateException("Authenticated user is required");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User account no longer exists"));
    }

    public void disconnectUser(long userId) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    openwaConfig.getUrl() + "/sessions", HttpMethod.GET,
                    new HttpEntity<>(getHttpHeaders()), String.class);
            JsonNode openwaSessions = objectMapper.readTree(response.getBody());
            if (openwaSessions.isArray()) {
                String prefix = SESSION_PREFIX + userId + "-";
                for (JsonNode session : openwaSessions) {
                    if (session.path("name").asText().startsWith(prefix)) {
                        deleteSession(session.path("id").asText());
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to disconnect the user's OpenWA session", e);
        }
    }

    public boolean isLinkedPhoneAllowed(String linkedPhone) {
        String registered = digits(currentUser().getMobileNumber());
        String linked = digits(linkedPhone);
        if (registered.length() != 10 || linked.length() < 10) {
            return false;
        }
        return linked.substring(linked.length() - 10).equals(registered);
    }

    public String registeredMobile() {
        return currentUser().getMobileNumber();
    }

    public boolean isSessionReady() {
        String sessionId = getSessionId();
        if (sessionId == null) return false;
        try {
            ResponseEntity<String> responseRaw = restTemplate.exchange(
                    openwaConfig.getUrl() + "/sessions/" + sessionId,
                    HttpMethod.GET, new HttpEntity<>(getHttpHeaders()), String.class);
            JsonNode response = objectMapper.readTree(responseRaw.getBody());
            if (!"ready".equals(response.path("status").asText())) return false;
            if (!isLinkedPhoneAllowed(response.path("phone").asText())) {
                deleteSession(sessionId);
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public @NonNull HttpHeaders getHttpHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", openwaConfig.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private void deleteSession(String sessionId) {
        restTemplate.exchange(openwaConfig.getUrl() + "/sessions/" + sessionId,
                HttpMethod.DELETE, new HttpEntity<>(getHttpHeaders()), Void.class);
    }

    private String findSessionId(AppUser user) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    openwaConfig.getUrl() + "/sessions", HttpMethod.GET,
                    new HttpEntity<>(getHttpHeaders()), String.class);
            JsonNode openwaSessions = objectMapper.readTree(response.getBody());
            String expectedName = sessionName(user.getId(), user.getMobileNumber());
            if (openwaSessions.isArray()) {
                for (JsonNode session : openwaSessions) {
                    if (expectedName.equals(session.path("name").asText())) {
                        return session.path("id").asText();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not query OpenWA session for user {}: {}", user.getId(), e.getMessage());
        }
        return null;
    }

    private String sessionName(long userId, String mobile) {
        return SESSION_PREFIX + userId + "-m" + digits(mobile);
    }

    private String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }
}
