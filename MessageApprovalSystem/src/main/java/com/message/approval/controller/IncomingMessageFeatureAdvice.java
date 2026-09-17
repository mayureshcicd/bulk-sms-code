package com.message.approval.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class IncomingMessageFeatureAdvice {
    private final boolean allowIncomingMessage;

    public IncomingMessageFeatureAdvice(
            @Value("${allow-incoming-message:true}") boolean allowIncomingMessage) {
        this.allowIncomingMessage = allowIncomingMessage;
    }

    @ModelAttribute("allowIncomingMessage")
    public boolean allowIncomingMessage() {
        return allowIncomingMessage;
    }
}
