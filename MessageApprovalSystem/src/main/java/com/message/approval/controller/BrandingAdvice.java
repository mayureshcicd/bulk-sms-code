package com.message.approval.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class BrandingAdvice {
    @Value("${app.company-name}")
    private String companyName;

    @ModelAttribute("companyName")
    public String companyName() {
        return companyName;
    }
}
