package com.message.approval.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.message.approval.domain.BulkMessageReport;

public interface BulkMessageReportRepository extends JpaRepository<BulkMessageReport, Long> {
    Page<BulkMessageReport> findBySentAtGreaterThanEqualAndSentAtLessThan(
            LocalDateTime from, LocalDateTime to, Pageable pageable);

    Page<BulkMessageReport> findBySentAtGreaterThanEqualAndSentAtLessThanAndUsernameContainingIgnoreCase(
            LocalDateTime from, LocalDateTime to, String username, Pageable pageable);

    long countByRecipientMobileAndMessageIdAndCsvSha256(
            String recipientMobile, Long messageId, String csvSha256);

    List<BulkMessageReport> findBySentAtGreaterThanEqualAndSentAtLessThanOrderBySentAtDesc(
            LocalDateTime from, LocalDateTime to);

    List<BulkMessageReport> findBySentAtGreaterThanEqualAndSentAtLessThanAndUsernameContainingIgnoreCaseOrderBySentAtDesc(
            LocalDateTime from, LocalDateTime to, String username);
}
