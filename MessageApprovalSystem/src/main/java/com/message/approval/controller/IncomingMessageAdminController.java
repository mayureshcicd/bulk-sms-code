package com.message.approval.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.message.approval.domain.IncomingWhatsAppMessage;
import com.message.approval.repository.IncomingWhatsAppMessageRepository;
import com.message.approval.service.OpenWAAdministrationService;
import com.message.approval.service.OpenWAIncomingContentService;

@Controller
@PreAuthorize("hasRole('ADMIN')")
@ConditionalOnProperty(name = "allow-incoming-message", havingValue = "true", matchIfMissing = true)
public class IncomingMessageAdminController {
    private final OpenWAAdministrationService openWAService;
    private final IncomingWhatsAppMessageRepository messageRepository;
    private final OpenWAIncomingContentService incomingContentService;

    public IncomingMessageAdminController(OpenWAAdministrationService openWAService,
            IncomingWhatsAppMessageRepository messageRepository,
            OpenWAIncomingContentService incomingContentService) {
        this.openWAService = openWAService;
        this.messageRepository = messageRepository;
        this.incomingContentService = incomingContentService;
    }

    @GetMapping("/incoming-messages")
    public String messages(@RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String chatId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {
        var monitored = openWAService.monitoredSessions();
        String selectedSession = sessionId;
        if ((selectedSession == null || selectedSession.isBlank()) && !monitored.isEmpty()) {
            selectedSession = monitored.stream().filter(item -> item.isEnabled()).findFirst()
                    .orElse(monitored.get(0)).getSessionId();
        }
        int safeSize = List.of(10, 20, 50, 100).contains(size) ? size : 20;
        Page<IncomingWhatsAppMessage> messagePage = Page.empty(PageRequest.of(0, safeSize));
        if (selectedSession != null && !selectedSession.isBlank()) {
            Sort.Direction direction = chatId == null || chatId.isBlank()
                    ? Sort.Direction.DESC
                    : Sort.Direction.ASC;
            PageRequest pageable = PageRequest.of(Math.max(0, page), safeSize,
                    Sort.by(direction, "receivedAt"));
            messagePage = chatId == null || chatId.isBlank()
                    ? messageRepository.findBySessionId(selectedSession, pageable)
                    : messageRepository.findBySessionIdAndChatId(selectedSession, chatId, pageable);
        }
        incomingContentService.enrichStoredMessages(messagePage.getContent());
        try {
            model.addAttribute("openwaSessions", openWAService.listSessions());
        } catch (Exception e) {
            model.addAttribute("openwaSessions", List.of());
            model.addAttribute("openwaError", e.getMessage());
        }
        model.addAttribute("monitoredSessions", monitored);
        model.addAttribute("selectedSessionId", selectedSession);
        model.addAttribute("selectedChatId", chatId);
        model.addAttribute("messages", messagePage.getContent());
        model.addAttribute("messagePage", messagePage);
        return "incoming-messages";
    }

    @GetMapping("/incoming-messages/{messageId}/media")
    public void media(@PathVariable long messageId,
            @RequestParam(defaultValue = "false") boolean download,
            @RequestHeader(value = "Range", required = false) String range,
            HttpServletResponse response) throws java.io.IOException {
        try {
            incomingContentService.streamMedia(messageId, download, range, response);
        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping("/incoming-messages/delete")
    public String deleteSelected(@RequestParam(required = false) List<Long> selectedIds,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String chatId,
            @RequestParam(defaultValue = "20") int size,
            RedirectAttributes redirectAttributes) {
        List<Long> safeIds = selectedIds == null ? List.of() : selectedIds.stream()
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (safeIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Select at least one incoming message to delete.");
        } else {
            List<IncomingWhatsAppMessage> messages = messageRepository.findAllById(safeIds);
            messageRepository.deleteAllInBatch(messages);
            redirectAttributes.addFlashAttribute("success",
                    messages.size() + " incoming message(s) permanently deleted.");
        }
        if (sessionId != null && !sessionId.isBlank()) redirectAttributes.addAttribute("sessionId", sessionId);
        if (chatId != null && !chatId.isBlank()) redirectAttributes.addAttribute("chatId", chatId);
        redirectAttributes.addAttribute("size", List.of(10, 20, 50, 100).contains(size) ? size : 20);
        return "redirect:/incoming-messages";
    }

    @PostMapping("/incoming-messages/monitor")
    public String monitor(@RequestParam String sessionId, RedirectAttributes redirectAttributes) {
        try {
            var monitored = openWAService.enable(sessionId);
            redirectAttributes.addFlashAttribute("success",
                    "Message collection enabled for " + monitored.getMobileNumber());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/incoming-messages?sessionId=" + sessionId;
    }

    @PostMapping("/incoming-messages/stop")
    public String stop(@RequestParam String sessionId, RedirectAttributes redirectAttributes) {
        try {
            openWAService.disable(sessionId);
            redirectAttributes.addFlashAttribute("success", "Message collection stopped");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/incoming-messages?sessionId=" + sessionId;
    }
}
