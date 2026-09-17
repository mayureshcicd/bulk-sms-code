package com.sms.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // 30 sec connection timeout
        factory.setConnectTimeout(30_000);
        // 60 sec response timeout
        factory.setReadTimeout(60_000);
        return new RestTemplate(factory);
    }
}