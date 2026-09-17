package com.sms.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "openwa.api")
public class OpenWAConfig {
    private String url;
    private String apiKey;
}
