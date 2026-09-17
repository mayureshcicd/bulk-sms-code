package com.message.approval.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.message.approval.domain.MonitoredWhatsAppSession;

public interface MonitoredWhatsAppSessionRepository extends JpaRepository<MonitoredWhatsAppSession, Long> {
    Optional<MonitoredWhatsAppSession> findBySessionId(String sessionId);
    Optional<MonitoredWhatsAppSession> findBySessionIdAndEnabledTrue(String sessionId);
    List<MonitoredWhatsAppSession> findAllByOrderByMobileNumberAsc();
}
