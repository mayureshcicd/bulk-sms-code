package com.sms.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sms.util.OpenWAHandler;

@RestController
@RequestMapping("/api/internal/users")
public class InternalUserController {
    private final OpenWAHandler openWAHandler;

    @Value("${message-approval.integration-key}")
    private String integrationKey;

    public InternalUserController(OpenWAHandler openWAHandler) {
        this.openWAHandler = openWAHandler;
    }

    @DeleteMapping("/{userId}/disconnect")
    public ResponseEntity<?> disconnect(@PathVariable long userId,
            @RequestHeader("X-Integration-Key") String key) {
        if (!integrationKey.equals(key)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid integration key"));
        }
        openWAHandler.disconnectUser(userId);
        return ResponseEntity.ok(Map.of("status", "disconnected"));
    }
}
