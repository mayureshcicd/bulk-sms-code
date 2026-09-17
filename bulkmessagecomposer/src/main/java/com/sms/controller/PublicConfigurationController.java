package com.sms.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PublicConfigurationController {
    @Value("${app.company-name}")
    private String companyName;

    @GetMapping("/api/public/config")
    public Map<String, String> configuration() {
        return Map.of("companyName", companyName);
    }
}
