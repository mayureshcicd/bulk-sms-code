package com.sms.service;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class ApprovedMessageClient {
    private final RestTemplate restTemplate;

    @Value("${message-approval.base-url}")
    private String baseUrl;
    @Value("${message-approval.integration-key}")
    private String integrationKey;

    public ApprovedMessageClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public List<ApprovedMessage> list() {
        ResponseEntity<ApprovedMessage[]> response = restTemplate.exchange(url(""), HttpMethod.GET,
                new HttpEntity<>(headers()), ApprovedMessage[].class);
        return response.getBody() == null ? List.of() : Arrays.asList(response.getBody());
    }

    public ApprovedMessage get(long id) {
        return restTemplate.exchange(url("/" + id), HttpMethod.GET,
                new HttpEntity<>(headers()), ApprovedMessage.class).getBody();
    }

    public Attachment getAttachment(long id, ApprovedFile metadata) {
        return getAttachment(id, 0, metadata);
    }

    public Attachment getAttachment(long id, int fileIndex, ApprovedFile metadata) {
        ResponseEntity<byte[]> response = restTemplate.exchange(url("/" + id + "/attachments/" + fileIndex), HttpMethod.GET,
                new HttpEntity<>(headers()), byte[].class);
        return new Attachment(response.getBody() == null ? new byte[0] : response.getBody(),
                metadata.fileName(), metadata.contentType());
    }

    public String getPreview(long id, int fileIndex) {
        return restTemplate.exchange(url("/" + id + "/attachments/" + fileIndex + "/preview"), HttpMethod.GET,
                new HttpEntity<>(headers()), String.class).getBody();
    }

    public int recordBulkReport(long messageId, String csvFileName, String csvSha256,
            List<String> recipients) {
        BulkReportRequest request = new BulkReportRequest(messageId, csvFileName, csvSha256, recipients);
        BulkReportResponse response = restTemplate.exchange(url("/bulk-reports"), HttpMethod.POST,
                new HttpEntity<>(request, headers()), BulkReportResponse.class).getBody();
        return response == null ? 0 : response.recorded();
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Integration-Key", integrationKey);
        headers.set("X-Username", SecurityContextHolder.getContext().getAuthentication().getName());
        return headers;
    }

    private String url(String suffix) {
        return baseUrl + "/api/integration/approved-messages" + suffix;
    }

    public record ApprovedMessage(Long id, String title, String content, String createdBy,
            String createdAt, List<ApprovedFile> files) { }
    public record ApprovedFile(String fileName, String contentType, long size) { }
    public record Attachment(byte[] bytes, String fileName, String contentType) { }
    public record BulkReportRequest(Long messageId, String csvFileName, String csvSha256,
            List<String> recipients) { }
    public record BulkReportResponse(int recorded) { }
}
