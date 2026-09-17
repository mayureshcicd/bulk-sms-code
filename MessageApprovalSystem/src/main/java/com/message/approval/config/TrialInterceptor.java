package com.message.approval.config;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.message.approval.service.TrialService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class TrialInterceptor implements org.springframework.web.servlet.HandlerInterceptor {
    private final TrialService trialService;

    public TrialInterceptor(TrialService trialService) {
        this.trialService = trialService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!trialService.isExpired()) {
            return true;
        }
        response.setStatus(HttpStatus.PAYMENT_REQUIRED.value());
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write("Trial period has expired.");
        return false;
    }
}
