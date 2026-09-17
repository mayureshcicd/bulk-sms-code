package com.message.approval.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Principal;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.message.approval.domain.AppUser;
import com.message.approval.domain.Message;
import com.message.approval.domain.MessageStatus;
import com.message.approval.repository.UserRepository;
import com.message.approval.service.MessageService;
import com.message.approval.service.DocumentPreviewService;

@Controller
public class MainController {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    private final MessageService messageService;
    private final UserRepository userRepository;
    private final DocumentPreviewService documentPreviewService;

    public MainController(MessageService messageService, UserRepository userRepository, DocumentPreviewService documentPreviewService) {
        this.messageService = messageService;
        this.userRepository = userRepository;
        this.documentPreviewService = documentPreviewService;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    @PreAuthorize("hasRole('ADMIN')")
    public String register(Model model, Principal principal) {
        AppUser currentUser = userRepository.findByUsername(principal.getName()).orElseThrow();
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("users", messageService.getUsers());
        return "register";
    }

    @PostMapping("/register")
    @PreAuthorize("hasRole('ADMIN')")
    public String registerUser(@RequestParam String username,
                               @RequestParam String password,
                               @RequestParam String role,
                               @RequestParam String mobileNumber,
                               RedirectAttributes redirectAttributes) {
        try {
            messageService.createUser(username, password, role, mobileNumber);
            redirectAttributes.addFlashAttribute("successMessage", "Account created successfully.");
        } catch (IllegalArgumentException e) {
            preserveAccountForm(redirectAttributes, username, role, mobileNumber);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (DataIntegrityViolationException e) {
            log.warn("Account creation failed because a unique value already exists", e);
            preserveAccountForm(redirectAttributes, username, role, mobileNumber);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Username or mobile number is already registered.");
        } catch (Exception e) {
            log.error("Unexpected error while creating account", e);
            preserveAccountForm(redirectAttributes, username, role, mobileNumber);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Account could not be created. Please try again or contact the administrator.");
        }
        return "redirect:/register";
    }

    private void preserveAccountForm(RedirectAttributes attributes, String username, String role,
            String mobileNumber) {
        attributes.addFlashAttribute("usernameValue", username == null ? "" : username.trim());
        attributes.addFlashAttribute("roleValue", role);
        attributes.addFlashAttribute("mobileNumberValue", mobileNumber);
    }

    @PostMapping("/users/delete-selected")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteSelectedUsers(@RequestParam("userIds") List<Long> userIds,
                                      Principal principal) {
        if (userIds == null || userIds.isEmpty()) {
            return "redirect:/register?error=selection";
        }
        AppUser currentAdmin = userRepository.findByUsername(principal.getName()).orElseThrow();
        int deleted = messageService.deleteUsers(userIds, currentAdmin);
        return "redirect:/register?deleted=" + deleted;
    }

    @GetMapping("/change-password")
    public String showChangePassword() {
        return "change-password";
    }

    @PostMapping("/change-password")
    public String changePassword(@RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 Principal principal) {
        if (newPassword.length() < 8) {
            return "redirect:/change-password?error=length";
        }
        if (!newPassword.equals(confirmPassword)) {
            return "redirect:/change-password?error=mismatch";
        }
        if (!messageService.changePassword(principal.getName(), currentPassword, newPassword)) {
            return "redirect:/change-password?error=current";
        }
        return "redirect:/change-password?changed=true";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model,
                            Principal principal,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "10") int size,
                            @RequestParam(required = false) String search) {
        AppUser currentUser = userRepository.findByUsername(principal.getName()).orElseThrow();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("search", search);
        if ("ROLE_ADMIN".equals(currentUser.getRole())) {
            Page<Message> messagePage = messageService.getMessagesForAdmin(search, pageable);
            model.addAttribute("messages", messagePage.getContent());
            model.addAttribute("messagePage", messagePage);
            return "admin-dashboard";
        }
        Page<Message> messagePage = messageService.getMessagesForUser(currentUser, search, pageable);
        model.addAttribute("messages", messagePage.getContent());
        model.addAttribute("messagePage", messagePage);
        return "user-dashboard";
    }

    @GetMapping("/messages/new")
    public String showCreateForm(Model model) {
        model.addAttribute("message", new Message());
        return "message-form";
    }

    @PostMapping("/messages")
    public String createMessage(@RequestParam String title,
                                @RequestParam String content,
                                @RequestParam(required = false) MultipartFile[] files,
                                Principal principal) throws IOException {
        validateMessageForm(title, content, files);
        AppUser currentUser = userRepository.findByUsername(principal.getName()).orElseThrow();
        messageService.createMessage(title, content, files, currentUser);
        return "redirect:/dashboard";
    }

    @GetMapping("/messages/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model, Principal principal) {
        AppUser currentUser = userRepository.findByUsername(principal.getName()).orElseThrow();
        Message message = messageService.getMessagesForUser(currentUser).stream()
                .filter(m -> m.getId().equals(id))
                .findFirst()
                .orElseThrow();
        model.addAttribute("message", message);
        return "message-form";
    }

    @PostMapping("/messages/{id}/edit")
    public String updateMessage(@PathVariable Long id,
                                @RequestParam String title,
                                @RequestParam String content,
                                @RequestParam(required = false) MultipartFile[] files,
                                Principal principal) throws IOException {
        validateMessageForm(title, content, files);
        AppUser currentUser = userRepository.findByUsername(principal.getName()).orElseThrow();
        messageService.updateMessage(id, title, content, files, currentUser);
        return "redirect:/dashboard";
    }

    @PostMapping("/messages/{id}/delete")
    public String deleteMessage(@PathVariable Long id, Principal principal) {
        AppUser currentUser = userRepository.findByUsername(principal.getName()).orElseThrow();
        messageService.deleteMessage(id, currentUser);
        return "redirect:/dashboard";
    }

    @PostMapping("/messages/{id}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public String reviewMessage(@PathVariable Long id,
                                @RequestParam MessageStatus status,
                                @RequestParam String reason,
                                Principal principal) {
        AppUser currentUser = userRepository.findByUsername(principal.getName()).orElseThrow();
        messageService.reviewMessage(id, status, reason, currentUser);
        return "redirect:/dashboard";
    }

    @PostMapping("/messages/review-selected")
    @PreAuthorize("hasRole('ADMIN')")
    public String reviewSelectedMessages(@RequestParam("messageIds") List<Long> messageIds,
                                         @RequestParam MessageStatus status,
                                         @RequestParam String reason,
                                         Principal principal) {
        if (messageIds == null || messageIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one message");
        }
        AppUser currentUser = userRepository.findByUsername(principal.getName()).orElseThrow();
        messageService.reviewMessages(messageIds, status, reason, currentUser);
        return "redirect:/dashboard";
    }

    @GetMapping("/files/{storedName}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String storedName) throws IOException {
        return serveFile(storedName, false);
    }

    @GetMapping("/files/{storedName}/view")
    public ResponseEntity<Resource> viewFile(@PathVariable String storedName) throws IOException {
        return serveFile(storedName, true);
    }

    @GetMapping("/files/{storedName}/preview")
    public ResponseEntity<String> previewDocument(@PathVariable String storedName) throws IOException {
        Path uploadPath = messageService.getUploadPath();
        Path filePath = uploadPath.resolve(storedName).normalize();
        if (!filePath.startsWith(uploadPath) || !Files.isReadable(filePath)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                .body(documentPreviewService.render(filePath, safeHeaderFilename(storedName)));
    }

    private ResponseEntity<Resource> serveFile(String storedName, boolean inline) throws IOException {
        Path uploadPath = messageService.getUploadPath();
        Path filePath = uploadPath.resolve(storedName).normalize();
        if (!filePath.startsWith(uploadPath)) {
            return ResponseEntity.badRequest().build();
        }

        Resource resource = new UrlResource(filePath.toUri());
        if (!resource.exists() || !resource.isReadable()) {
            return ResponseEntity.notFound().build();
        }

        String contentType = Files.probeContentType(filePath);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (contentType != null) {
            try {
                mediaType = MediaType.parseMediaType(contentType);
            } catch (IllegalArgumentException ignored) {
                // Keep the safe binary fallback for an invalid detected MIME type.
            }
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        (inline ? "inline" : "attachment") + "; filename=\"" + safeHeaderFilename(storedName) + "\"")
                .body(resource);
    }

    private String safeHeaderFilename(String storedName) {
        int separator = storedName.indexOf('_');
        String originalName = separator >= 0 ? storedName.substring(separator + 1) : storedName;
        return originalName.replace("\"", "'").replace("\r", "").replace("\n", "");
    }

    private void validateMessageForm(String title, String content, MultipartFile[] files) {
        if (title == null || title.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title is required");
        }
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content is required");
        }
        if (files != null) {
            long maxFileSize = 20L * 1024 * 1024;
            for (MultipartFile file : files) {
                if (file != null && file.getSize() > maxFileSize) {
                    throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                            "Each attachment must be 20 MB or smaller");
                }
            }
        }
    }
}
