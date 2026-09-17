package com.message.approval.domain;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "bulk_message_reports", indexes = {
        @Index(name = "idx_bulk_report_sent_at", columnList = "sent_at"),
        @Index(name = "idx_bulk_report_duplicate", columnList = "recipient_mobile,message_id,csv_sha256")
})
public class BulkMessageReport {
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("hh:mm a");
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_id", nullable = false, length = 36)
    private String campaignId;

    @Column(name = "recipient_mobile", nullable = false, length = 20)
    private String recipientMobile;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "message_title", nullable = false, length = 500)
    private String messageTitle;

    @Column(name = "message_content", nullable = false, length = 4000)
    private String messageContent;

    @Column(name = "csv_file_name", nullable = false, length = 500)
    private String csvFileName;

    @Column(name = "csv_sha256", nullable = false, length = 64)
    private String csvSha256;

    @Column(name = "duplicate_message_count", nullable = false)
    private long duplicateMessageCount;

    @Column(name = "username", nullable = false, length = 100)
    private String username;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCampaignId() { return campaignId; }
    public void setCampaignId(String campaignId) { this.campaignId = campaignId; }
    public String getRecipientMobile() { return recipientMobile; }
    public void setRecipientMobile(String recipientMobile) { this.recipientMobile = recipientMobile; }
    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }
    public String getMessageTitle() { return messageTitle; }
    public void setMessageTitle(String messageTitle) { this.messageTitle = messageTitle; }
    public String getMessageContent() { return messageContent; }
    public void setMessageContent(String messageContent) { this.messageContent = messageContent; }
    public String getCsvFileName() { return csvFileName; }
    public void setCsvFileName(String csvFileName) { this.csvFileName = csvFileName; }
    public String getCsvSha256() { return csvSha256; }
    public void setCsvSha256(String csvSha256) { this.csvSha256 = csvSha256; }
    public long getDuplicateMessageCount() { return duplicateMessageCount; }
    public void setDuplicateMessageCount(long duplicateMessageCount) { this.duplicateMessageCount = duplicateMessageCount; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
    public String getDisplayDate() { return sentAt == null ? "" : DISPLAY_DATE.format(sentAt); }
    public String getDisplayTime() { return sentAt == null ? "" : DISPLAY_TIME.format(sentAt); }
}
