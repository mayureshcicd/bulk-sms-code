package com.sms.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class BulkDocumentResponse {

    private int totalRecipients;
    private int successCount;
    private int failureCount;
    private String documentName;
    private String caption;
    private List<Map<String, Object>> details;
}