package com.message.approval.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.message.approval.service.OpenWAWebhookReceiverService;

@RestController
@RequestMapping("/api/openwa")
@ConditionalOnProperty(name = "allow-incoming-message", havingValue = "true", matchIfMissing = true)
public class OpenWAWebhookController {
    private final OpenWAWebhookReceiverService receiverService;

    public OpenWAWebhookController(OpenWAWebhookReceiverService receiverService) {
        this.receiverService = receiverService;
    }

    @PostMapping(value = "/webhook", consumes = "application/json")
    public ResponseEntity<?> receive(@RequestBody byte[] rawBody,
            @RequestHeader(value = "X-OpenWA-Signature", required = false) String signature,
            @RequestHeader(value = "X-OpenWA-Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-OpenWA-Delivery-Id", required = false) String deliveryId) {
        if (!receiverService.validSignature(rawBody, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid signature"));
        }
        try {
            var result = receiverService.receive(rawBody, idempotencyKey, deliveryId);
            return ResponseEntity.ok(Map.of("status", result.name().toLowerCase()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
