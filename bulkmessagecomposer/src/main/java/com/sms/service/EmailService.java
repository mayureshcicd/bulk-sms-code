package com.sms.service;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class EmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${app.mail.from:}")
    private String mailFrom;

    @Value("${app.mail.from-name:}")
    private String mailFromName;

    public record EmailRecipient(String name, String email) {}

    public record EmailBatchResult(
            String batchId,
            int totalRecipients,
            int sentCount,
            int failedCount,
            String status,
            String message,
            List<String> failedDetails
    ) {}

    public boolean isConfigured() {
        return mailSender != null && mailHost != null && !mailHost.isBlank()
                && mailUsername != null && !mailUsername.isBlank();
    }

    public EmailBatchResult sendBulkEmails(
            List<EmailRecipient> recipients,
            String subject,
            String messageContent,
            byte[] attachmentData,
            String attachmentName,
            String attachmentMimeType,
            long delayMs
    ) {
        String batchId = "email-" + UUID.randomUUID().toString().substring(0, 8);

        if (!isConfigured()) {
            String errorMsg = "SMTP is not configured yet. Please configure SPRING_MAIL_HOST, SPRING_MAIL_USERNAME, and SPRING_MAIL_PASSWORD (e.g. for Gmail SMTP or your corporate SMTP) in application.yml or environment variables.";
            log.error(errorMsg);
            return new EmailBatchResult(batchId, recipients.size(), 0, recipients.size(), "failed", errorMsg, List.of(errorMsg));
        }

        if (recipients == null || recipients.isEmpty()) {
            return new EmailBatchResult(batchId, 0, 0, 0, "completed", "No email recipients provided", List.of());
        }

        int sent = 0;
        int failed = 0;
        List<String> failures = new ArrayList<>();

        String effectiveFrom = (mailFrom != null && !mailFrom.isBlank()) ? mailFrom : mailUsername;
        String effectiveFromName = (mailFromName != null && !mailFromName.isBlank()) ? mailFromName : "Notification";

        for (EmailRecipient recipient : recipients) {
            String email = recipient.email();
            if (email == null || !email.contains("@")) {
                failed++;
                failures.add("Skipped invalid email for: " + recipient.name());
                continue;
            }

            String greetingName = (recipient.name() != null && !recipient.name().isBlank())
                    ? recipient.name().strip()
                    : "User";

            try {
                MimeMessage mimeMessage = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());

                helper.setFrom(new InternetAddress(effectiveFrom, effectiveFromName, StandardCharsets.UTF_8.name()));
                helper.setTo(email.trim());
                helper.setSubject(subject != null && !subject.isBlank() ? subject : "Message Notification");

                String htmlBody = buildHtmlBody(greetingName, messageContent);
                helper.setText(htmlBody, true);

                if (attachmentData != null && attachmentData.length > 0 && attachmentName != null) {
                    helper.addAttachment(attachmentName, new ByteArrayResource(attachmentData), attachmentMimeType);
                }

                mailSender.send(mimeMessage);
                sent++;
                log.info("Sent email to {} ({}) in batch {}", email, greetingName, batchId);

                if (delayMs > 0) {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (Exception ex) {
                log.error("Failed to send email to {}: {}", email, ex.getMessage(), ex);
                failed++;
                failures.add(email + ": " + ex.getMessage());
            }
        }

        String finalStatus = failed == 0 ? "completed" : (sent > 0 ? "completed_with_failures" : "failed");
        String finalMsg = String.format("Email dispatch finished: %d sent successfully, %d failed out of %d total.", sent, failed, recipients.size());

        return new EmailBatchResult(batchId, recipients.size(), sent, failed, finalStatus, finalMsg, failures);
    }

    private String buildHtmlBody(String greetingName, String messageContent) {
        String escapedContent = messageContent != null ? messageContent
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br/>") : "";

        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: 'Segoe UI', Arial, sans-serif; background-color: #f8fafc; margin: 0; padding: 20px; color: #1e293b; }
                        .email-container { max-width: 600px; margin: 0 auto; background: #ffffff; border: 1px solid #e2e8f0; border-radius: 8px; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05); }
                        .email-header { background: #1e3a8a; padding: 20px 24px; color: #ffffff; font-size: 20px; font-weight: bold; }
                        .email-body { padding: 24px; font-size: 15px; line-height: 1.6; }
                        .greeting { font-size: 16px; font-weight: 600; color: #0f172a; margin-bottom: 16px; }
                        .content { margin-top: 12px; margin-bottom: 24px; color: #334155; }
                        .email-footer { background: #f1f5f9; padding: 16px 24px; font-size: 12px; color: #64748b; border-top: 1px solid #e2e8f0; }
                    </style>
                </head>
                <body>
                    <div class="email-container">
                        <div class="email-header">
                            Notification
                        </div>
                        <div class="email-body">
                            <p class="greeting">Dear %s,</p>
                            <div class="content">
                                %s
                            </div>
                        </div>
                        <div class="email-footer">
                            Sent via Bulk Messenger System
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(greetingName, escapedContent);
    }
}
