package com.message.approval.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import com.message.approval.domain.IncomingWhatsAppMessage;

public interface IncomingWhatsAppMessageRepository extends JpaRepository<IncomingWhatsAppMessage, Long> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update IncomingWhatsAppMessage message set message.messageBody = '' where message.messageBody is null")
    int replaceNullMessageBodies();

    boolean existsByIdempotencyKey(String idempotencyKey);
    Optional<IncomingWhatsAppMessage> findFirstBySessionIdAndWhatsappMessageId(String sessionId, String whatsappMessageId);
    Page<IncomingWhatsAppMessage> findBySessionId(String sessionId, Pageable pageable);
    Page<IncomingWhatsAppMessage> findBySessionIdAndChatId(String sessionId, String chatId, Pageable pageable);
    List<IncomingWhatsAppMessage> findTop500BySessionIdAndChatIdOrderByReceivedAtAsc(String sessionId, String chatId);
}
