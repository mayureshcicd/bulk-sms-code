package com.message.approval.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class BulkComposerClient {
    private final RestTemplate restTemplate;

    @Value("${bulk-composer.base-url}")
    private String baseUrl;

    @Value("${app.integration-key}")
    private String integrationKey;

    public BulkComposerClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void disconnectUser(long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Integration-Key", integrationKey);
        restTemplate.exchange(baseUrl + "/api/internal/users/" + userId + "/disconnect",
                HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
    }
}
