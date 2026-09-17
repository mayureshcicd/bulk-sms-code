package com.message.approval.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.message.approval.domain.AppUser;
import com.message.approval.domain.Message;
import com.message.approval.domain.MessageFile;
import com.message.approval.domain.MessageStatus;
import com.message.approval.repository.MessageRepository;
import com.message.approval.repository.UserRepository;

@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BulkComposerClient bulkComposerClient;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    public String getUploadDir() {
        return uploadDir;
    }

    public Path getUploadPath() {
        Path configured = Paths.get(uploadDir);
        if (configured.isAbsolute()) {
            return configured.normalize();
        }
        Path workingDirectory = Paths.get("").toAbsolutePath().normalize();
        Path direct = workingDirectory.resolve(configured).normalize();
        if (Files.exists(direct)) {
            return direct;
        }
        Path projectDirectory = workingDirectory.resolve("MessageApprovalSystem").resolve(configured).normalize();
        if (Files.exists(projectDirectory)) {
            return projectDirectory;
        }
        try {
            Path codeLocation = Paths.get(MessageService.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path applicationRoot = Files.isDirectory(codeLocation)
                    ? codeLocation.getParent().getParent()
                    : codeLocation.getParent().getParent();
            Path besideApplication = applicationRoot.resolve(configured).normalize();
            if (Files.exists(besideApplication)) {
                return besideApplication;
            }
        } catch (Exception ignored) {
            // Fall back to the configured path under the working directory.
        }
        return direct;
    }

    public MessageService(MessageRepository messageRepository, UserRepository userRepository,
            PasswordEncoder passwordEncoder, BulkComposerClient bulkComposerClient) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.bulkComposerClient = bulkComposerClient;
    }

    @Transactional
    public Message createMessage(String title, String content, MultipartFile[] files, AppUser createdBy) throws IOException {
        Message message = new Message();
        message.setTitle(title);
        message.setContent(content);
        message.setCreatedBy(createdBy);
        message.setStatus(MessageStatus.PENDING);

        addUploadedFiles(message, files);

        return messageRepository.save(message);
    }

    @Transactional(readOnly = true)
    public List<Message> getMessagesForUser(AppUser user) {
        List<Message> messages = messageRepository.findByCreatedByOrderByCreatedAtDesc(user);
        messages.forEach(this::initializeMessage);
        return messages;
    }

    @Transactional(readOnly = true)
    public List<Message> getMessagesForAdmin() {
        List<Message> messages = messageRepository.findAllByOrderByCreatedAtDesc();
        messages.forEach(this::initializeMessage);
        return messages;
    }

    @Transactional(readOnly = true)
    public Page<Message> getMessagesForUser(AppUser user, String search, Pageable pageable) {
        List<Message> messages = this.messageRepository.findByCreatedByOrderByCreatedAtDesc(user);
        messages.forEach(this::initializeMessage);
        List<Message> filtered = messages.stream()
                .filter(message -> matchesSearch(message, search))
                .toList();
        return paginate(filtered, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Message> getMessagesForAdmin(String search, Pageable pageable) {
        List<Message> messages = this.messageRepository.findAllByOrderByCreatedAtDesc();
        messages.forEach(this::initializeMessage);
        List<Message> filtered = messages.stream()
                .filter(message -> matchesSearch(message, search))
                .toList();
        return paginate(filtered, pageable);
    }

    private void initializeMessage(Message message) {
        if (message == null) {
            return;
        }
        Hibernate.initialize(message.getFiles());
        Hibernate.initialize(message.getCreatedBy());
    }

    private boolean matchesSearch(Message message, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String term = search.toLowerCase();
        return (message.getTitle() != null && message.getTitle().toLowerCase().contains(term))
                || (message.getContent() != null && message.getContent().toLowerCase().contains(term));
    }

    private Page<Message> paginate(List<Message> messages, Pageable pageable) {
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), messages.size());
        if (start > end) {
            start = end;
        }
        List<Message> pageItems = start >= messages.size() ? List.of() : messages.subList(start, end);
        return new PageImpl<>(pageItems, pageable, messages.size());
    }

    public Message reviewMessage(Long id, MessageStatus status, String reason, AppUser reviewer) {
        Message message = messageRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));
        if (!"ROLE_ADMIN".equals(reviewer.getRole())) {
            throw new AccessDeniedException("Only admins can review messages");
        }
        message.setStatus(status);
        message.setReviewReason(reason);
        message.setReviewedBy(reviewer);
        return messageRepository.save(message);
    }

    @Transactional
    public void reviewMessages(List<Long> ids, MessageStatus status, String reason, AppUser reviewer) {
        if (!"ROLE_ADMIN".equals(reviewer.getRole())) {
            throw new AccessDeniedException("Only admins can review messages");
        }
        List<Long> distinctIds = ids.stream().distinct().toList();
        List<Message> messages = messageRepository.findAllById(distinctIds);
        if (messages.size() != distinctIds.size()) {
            throw new IllegalArgumentException("One or more messages were not found");
        }
        messages.forEach(message -> {
            message.setStatus(status);
            message.setReviewReason(reason);
            message.setReviewedBy(reviewer);
        });
        messageRepository.saveAll(messages);
    }

    @Transactional
    public Message updateMessage(Long id, String title, String content, MultipartFile[] files, AppUser user) throws IOException {
        Message message = messageRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));
        if (!message.getCreatedBy().getId().equals(user.getId())) {
            throw new AccessDeniedException("You cannot edit this message");
        }
        message.setTitle(title);
        message.setContent(content);
        message.setStatus(MessageStatus.PENDING);
        message.setReviewReason(null);
        message.setReviewedBy(null);

        message.getFiles().clear();
        addUploadedFiles(message, files);
        return messageRepository.save(message);
    }

    private void addUploadedFiles(Message message, MultipartFile[] files) throws IOException {
        if (files == null) {
            return;
        }

        Path targetDir = getUploadPath();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }

            String submittedName = file.getOriginalFilename();
            String fileName = submittedName == null || submittedName.isBlank()
                    ? "file"
                    : Paths.get(submittedName).getFileName().toString();
            String storedName = UUID.randomUUID() + "_" + fileName;
            Files.createDirectories(targetDir);
            Files.copy(file.getInputStream(), targetDir.resolve(storedName), StandardCopyOption.REPLACE_EXISTING);

            String contentType = file.getContentType();
            MessageFile messageFile = new MessageFile();
            messageFile.setFileName(fileName);
            messageFile.setStoredName(storedName);
            messageFile.setContentType(contentType == null || contentType.isBlank()
                    ? "application/octet-stream"
                    : contentType);
            messageFile.setSize(file.getSize());
            messageFile.setMessage(message);
            message.getFiles().add(messageFile);
        }
    }

    @Transactional
    public void deleteMessage(Long id, AppUser user) {
        Message message = messageRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));
        if (!message.getCreatedBy().getId().equals(user.getId())) {
            throw new AccessDeniedException("You cannot delete this message");
        }
        deleteMessageEntity(message);
    }

    private void deleteMessageEntity(Message message) {
        Path uploadPath = getUploadPath();
        List<Path> attachmentPaths = message.getFiles().stream()
                .map(MessageFile::getStoredName)
                .map(uploadPath::resolve)
                .map(Path::normalize)
                .filter(path -> path.startsWith(uploadPath))
                .toList();
        messageRepository.delete(message);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                attachmentPaths.forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        log.error("Message was deleted, but attachment file could not be removed: {}", path, e);
                    }
                });
            }
        });
    }

    @Transactional(readOnly = true)
    public List<AppUser> getUsers() {
        return userRepository.findAllByOrderByUsernameAsc();
    }

    @Transactional
    public AppUser createUser(String username, String password, String role, String mobileNumber) {
        String normalizedUsername = username == null ? "" : username.trim();
        if (normalizedUsername.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (normalizedUsername.length() > 100) {
            throw new IllegalArgumentException("Username cannot be longer than 100 characters");
        }
        if (userRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new IllegalArgumentException("Username is already registered");
        }
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must contain at least 8 characters");
        }
        if (!List.of("ROLE_USER", "ROLE_ADMIN").contains(role)) {
            throw new IllegalArgumentException("Select a valid account role");
        }
        String normalizedMobile = normalizeMobileNumber(mobileNumber);
        if (userRepository.existsByMobileNumber(normalizedMobile)) {
            throw new IllegalArgumentException("Mobile number is already registered");
        }
        AppUser user = new AppUser();
        user.setUsername(normalizedUsername);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        user.setMobileNumber(normalizedMobile);
        return userRepository.saveAndFlush(user);
    }

    private String normalizeMobileNumber(String mobileNumber) {
        String digits = mobileNumber == null ? "" : mobileNumber.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        }
        if (!digits.matches("[6-9]\\d{9}")) {
            throw new IllegalArgumentException("Enter a valid 10-digit Indian mobile number");
        }
        return digits;
    }

    @Transactional
    public int deleteUsers(List<Long> ids, AppUser currentAdmin) {
        List<Long> distinctIds = ids.stream().distinct().toList();
        List<AppUser> users = userRepository.findAllById(distinctIds);
        if (users.size() != distinctIds.size()) {
            throw new IllegalArgumentException("One or more users were not found");
        }
        if (users.stream().anyMatch(user -> user.getId().equals(currentAdmin.getId()))) {
            throw new AccessDeniedException("You cannot delete your own account");
        }

        long adminsBeingDeleted = users.stream()
                .filter(user -> "ROLE_ADMIN".equals(user.getRole()))
                .count();
        long totalAdmins = userRepository.findAll().stream()
                .filter(user -> "ROLE_ADMIN".equals(user.getRole()))
                .count();
        if (totalAdmins - adminsBeingDeleted < 1) {
            throw new AccessDeniedException("At least one admin account must remain");
        }

        for (AppUser user : users) {
            bulkComposerClient.disconnectUser(user.getId());
            List<Message> reviewedMessages = messageRepository.findByReviewedBy(user);
            reviewedMessages.forEach(message -> message.setReviewedBy(null));
            messageRepository.saveAll(reviewedMessages);

            List<Message> authoredMessages = messageRepository.findByCreatedByOrderByCreatedAtDesc(user);
            authoredMessages.forEach(this::deleteMessageEntity);
            userRepository.delete(user);
        }
        return users.size();
    }

    @Transactional
    public boolean changePassword(String username, String currentPassword, String newPassword) {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            return false;
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        return true;
    }
}
