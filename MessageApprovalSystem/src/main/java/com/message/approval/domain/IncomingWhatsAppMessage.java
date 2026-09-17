package com.message.approval.domain;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "incoming_whatsapp_messages", indexes = {
        @Index(name = "idx_incoming_session_time", columnList = "session_id,received_at"),
        @Index(name = "idx_incoming_thread", columnList = "session_id,chat_id,received_at")
})
public class IncomingWhatsAppMessage {
    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm a");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;
    @Column(name = "delivery_id", length = 100)
    private String deliveryId;
    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;
    @Column(name = "linked_mobile", nullable = false, length = 20)
    private String linkedMobile;
    @Column(name = "whatsapp_message_id", nullable = false, length = 255)
    private String whatsappMessageId;
    @Column(name = "chat_id", nullable = false, length = 255)
    private String chatId;
    @Column(name = "sender_id", length = 255)
    private String senderId;
    @Column(name = "sender_mobile", length = 20)
    private String senderMobile;
    @Column(name = "recipient_id", length = 255)
    private String recipientId;
    @Column(name = "author_id", length = 255)
    private String authorId;
    @Column(name = "message_type", nullable = false, length = 50)
    private String messageType;
    @Column(name = "message_body", columnDefinition = "TEXT")
    private String messageBody;
    @Column(name = "group_message", nullable = false)
    private boolean groupMessage;
    @Column(name = "from_me", nullable = false)
    private boolean fromMe;
    @Column(name = "has_media", nullable = false)
    private boolean hasMedia;
    @Column(name = "media_mimetype", length = 150)
    private String mediaMimetype;
    @Column(name = "media_filename", length = 500)
    private String mediaFilename;
    @Column(nullable = false)
    private boolean edited;
    @Column(nullable = false)
    private boolean revoked;
    @Column(name = "message_timestamp")
    private LocalDateTime messageTimestamp;
    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    public Long getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String value) { this.idempotencyKey = value; }
    public String getDeliveryId() { return deliveryId; }
    public void setDeliveryId(String value) { this.deliveryId = value; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String value) { this.sessionId = value; }
    public String getLinkedMobile() { return linkedMobile; }
    public void setLinkedMobile(String value) { this.linkedMobile = value; }
    public String getWhatsappMessageId() { return whatsappMessageId; }
    public void setWhatsappMessageId(String value) { this.whatsappMessageId = value; }
    public String getChatId() { return chatId; }
    public void setChatId(String value) { this.chatId = value; }
    public String getSenderId() { return senderId; }
    public void setSenderId(String value) { this.senderId = value; }
    public String getSenderMobile() { return senderMobile; }
    public void setSenderMobile(String value) { this.senderMobile = value; }
    public String getRecipientId() { return recipientId; }
    public void setRecipientId(String value) { this.recipientId = value; }
    public String getAuthorId() { return authorId; }
    public void setAuthorId(String value) { this.authorId = value; }
    public String getMessageType() { return messageType; }
    public void setMessageType(String value) { this.messageType = value; }
    public String getMessageBody() { return messageBody; }
    public void setMessageBody(String value) { this.messageBody = value == null ? "" : value; }
    public boolean isGroupMessage() { return groupMessage; }
    public void setGroupMessage(boolean value) { this.groupMessage = value; }
    public boolean isFromMe() { return fromMe; }
    public void setFromMe(boolean value) { this.fromMe = value; }
    public boolean isHasMedia() { return hasMedia; }
    public void setHasMedia(boolean value) { this.hasMedia = value; }
    public String getMediaMimetype() { return mediaMimetype; }
    public void setMediaMimetype(String value) { this.mediaMimetype = value; }
    public String getMediaFilename() { return mediaFilename; }
    public void setMediaFilename(String value) { this.mediaFilename = value; }
    public boolean isEdited() { return edited; }
    public void setEdited(boolean value) { this.edited = value; }
    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean value) { this.revoked = value; }
    public LocalDateTime getMessageTimestamp() { return messageTimestamp; }
    public void setMessageTimestamp(LocalDateTime value) { this.messageTimestamp = value; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime value) { this.receivedAt = value; }
    public String getDisplayTime() { return receivedAt == null ? "" : DISPLAY_DATE_TIME.format(receivedAt); }
    public String getSenderDisplay() {
        if (senderMobile != null && !senderMobile.isBlank()) return senderMobile;
        return authorId != null && !authorId.isBlank() ? authorId : senderId;
    }
    public boolean isImageMedia() {
        return mediaMatches("image/") || "image".equalsIgnoreCase(messageType) || "sticker".equalsIgnoreCase(messageType);
    }
    public boolean isVideoMedia() {
        return mediaMatches("video/") || "video".equalsIgnoreCase(messageType);
    }
    public boolean isPdfMedia() {
        return "application/pdf".equalsIgnoreCase(mediaMimetype)
                || (mediaFilename != null && mediaFilename.toLowerCase().endsWith(".pdf"));
    }
    public boolean isAudioMedia() {
        return mediaMatches("audio/") || "audio".equalsIgnoreCase(messageType)
                || "voice".equalsIgnoreCase(messageType) || "ptt".equalsIgnoreCase(messageType);
    }
    public boolean isOtherMedia() {
        return hasMedia && !isImageMedia() && !isVideoMedia() && !isPdfMedia() && !isAudioMedia();
    }
    private boolean mediaMatches(String prefix) {
        return mediaMimetype != null && mediaMimetype.toLowerCase().startsWith(prefix);
    }
}
