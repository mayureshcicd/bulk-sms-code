package com.message.approval.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.message.approval.domain.Message;
import com.message.approval.domain.MessageFile;
import com.message.approval.domain.MessageStatus;
import com.message.approval.repository.MessageRepository;
import com.message.approval.repository.UserRepository;
import com.message.approval.domain.AppUser;
import com.message.approval.service.MessageService;
import com.message.approval.service.DocumentPreviewService;
import com.message.approval.service.BulkMessageReportService;
import com.message.approval.service.BulkMessageReportService.RecordBulkSendRequest;

@RestController
@RequestMapping("/api/integration/approved-messages")
public class ApprovedMessageApiController {
    private final MessageRepository messageRepository;
    private final MessageService messageService;
    private final DocumentPreviewService documentPreviewService;
    private final UserRepository userRepository;
    private final BulkMessageReportService bulkMessageReportService;

    @Value("${app.integration-key}")
    private String integrationKey;

    public ApprovedMessageApiController(MessageRepository messageRepository, MessageService messageService,
            DocumentPreviewService documentPreviewService, UserRepository userRepository,
            BulkMessageReportService bulkMessageReportService) {
        this.messageRepository = messageRepository;
        this.messageService = messageService;
        this.documentPreviewService = documentPreviewService;
        this.userRepository = userRepository;
        this.bulkMessageReportService = bulkMessageReportService;
    }

    @PostMapping("/bulk-reports")
    public BulkReportRecordedDto recordBulkReport(@RequestBody RecordBulkSendRequest request,
            @RequestHeader("X-Integration-Key") String key,
            @RequestHeader("X-Username") String username) {
        authorize(key);
        return new BulkReportRecordedDto(bulkMessageReportService.record(request, username));
    }

    @GetMapping
    public List<ApprovedMessageDto> list(@RequestHeader("X-Integration-Key") String key,
            @RequestHeader("X-Username") String username) {
        authorize(key);
        AppUser user = findUser(username);
        List<Message> messages = "ROLE_ADMIN".equals(user.getRole())
                ? messageRepository.findByStatusOrderByCreatedAtDesc(MessageStatus.APPROVED)
                : messageRepository.findByStatusAndCreatedByOrderByCreatedAtDesc(MessageStatus.APPROVED, user);
        return messages.stream()
                .map(this::toDto).toList();
    }

    @GetMapping("/{id}")
    public ApprovedMessageDto get(@PathVariable Long id, @RequestHeader("X-Integration-Key") String key,
            @RequestHeader("X-Username") String username) {
        authorize(key);
        return toDto(findApproved(id, username));
    }

    @GetMapping("/{id}/attachment")
    public ResponseEntity<Resource> attachment(@PathVariable Long id,
            @RequestHeader("X-Integration-Key") String key,
            @RequestHeader("X-Username") String username) throws Exception {
        return attachment(id, 0, key, username);
    }

    @GetMapping("/{id}/attachments/{fileIndex}")
    public ResponseEntity<Resource> attachment(@PathVariable Long id, @PathVariable int fileIndex,
            @RequestHeader("X-Integration-Key") String key,
            @RequestHeader("X-Username") String username) throws Exception {
        authorize(key);
        Message message = findApproved(id, username);
        if (fileIndex < 0 || fileIndex >= message.getFiles().size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Approved message has no attachment");
        }
        MessageFile file = message.getFiles().get(fileIndex);
        Path uploadPath = messageService.getUploadPath();
        Path filePath = uploadPath.resolve(file.getStoredName()).normalize();
        if (!filePath.startsWith(uploadPath) || !Files.isReadable(filePath)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found");
        }
        String detectedType = Files.probeContentType(filePath);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        try { mediaType = MediaType.parseMediaType(detectedType == null ? file.getContentType() : detectedType); }
        catch (IllegalArgumentException ignored) { }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFileName().replace("\"", "'") + "\"")
                .body(new UrlResource(filePath.toUri()));
    }

    @GetMapping("/{id}/attachments/{fileIndex}/preview")
    public ResponseEntity<String> attachmentPreview(@PathVariable Long id, @PathVariable int fileIndex,
            @RequestHeader("X-Integration-Key") String key,
            @RequestHeader("X-Username") String username) throws IOException {
        authorize(key);
        Message message = findApproved(id, username);
        if (fileIndex < 0 || fileIndex >= message.getFiles().size()) return ResponseEntity.notFound().build();
        MessageFile file = message.getFiles().get(fileIndex);
        Path uploadPath = messageService.getUploadPath();
        Path filePath = uploadPath.resolve(file.getStoredName()).normalize();
        if (!filePath.startsWith(uploadPath) || !Files.isReadable(filePath)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                .body(documentPreviewService.render(filePath, file.getFileName()));
    }

    private Message findApproved(Long id, String username) {
        Message message = messageRepository.findOneById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
        if (message.getStatus() != MessageStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Message is not approved");
        }
        AppUser user = findUser(username);
        if (!"ROLE_ADMIN".equals(user.getRole())
                && !message.getCreatedBy().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found");
        }
        return message;
    }

    private AppUser findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User no longer exists"));
    }

    private ApprovedMessageDto toDto(Message message) {
        return new ApprovedMessageDto(message.getId(), message.getTitle(), message.getContent(),
                message.getCreatedBy().getUsername(), message.getCreatedAt(),
                message.getFiles().stream().map(this::toFileDto).toList());
    }

    private ApprovedFileDto toFileDto(MessageFile file) {
        String contentType = file.getContentType();
        try {
            Path path = messageService.getUploadPath().resolve(file.getStoredName()).normalize();
            String detected = Files.probeContentType(path);
            if (detected != null && !detected.isBlank()) contentType = detected;
        } catch (IOException ignored) { }
        return new ApprovedFileDto(file.getFileName(), contentType, file.getSize());
    }

    private void authorize(String key) {
        if (!integrationKey.equals(key)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid integration key");
        }
    }

    public record ApprovedMessageDto(Long id, String title, String content, String createdBy,
            java.time.LocalDateTime createdAt, List<ApprovedFileDto> files) { }
    public record ApprovedFileDto(String fileName, String contentType, long size) { }
    public record BulkReportRecordedDto(int recorded) { }
}
