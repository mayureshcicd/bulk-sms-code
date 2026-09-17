package com.message.approval.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.message.approval.domain.AppUser;
import com.message.approval.domain.BulkMessageReport;
import com.message.approval.domain.Message;
import com.message.approval.domain.MessageStatus;
import com.message.approval.repository.BulkMessageReportRepository;
import com.message.approval.repository.MessageRepository;
import com.message.approval.repository.UserRepository;

@Service
public class BulkMessageReportService {
    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Kolkata");
    private final BulkMessageReportRepository reportRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    public BulkMessageReportService(BulkMessageReportRepository reportRepository,
            MessageRepository messageRepository, UserRepository userRepository) {
        this.reportRepository = reportRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public int record(RecordBulkSendRequest request, String username) {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User no longer exists"));
        Message message = messageRepository.findOneById(request.messageId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
        if (message.getStatus() != MessageStatus.APPROVED
                || (!"ROLE_ADMIN".equals(user.getRole()) && !message.getCreatedBy().getId().equals(user.getId()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Approved message not found");
        }
        if (request.csvSha256() == null || !request.csvSha256().matches("[a-fA-F0-9]{64}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid CSV fingerprint");
        }
        if (request.recipients() == null || request.recipients().isEmpty()) {
            return 0;
        }

        String campaignId = UUID.randomUUID().toString();
        LocalDateTime sentAt = LocalDateTime.now(REPORT_ZONE);
        int recorded = 0;
        for (String rawRecipient : request.recipients()) {
            String mobile = normalizeMobile(rawRecipient);
            if (mobile.isBlank()) continue;
            long duplicates = reportRepository.countByRecipientMobileAndMessageIdAndCsvSha256(
                    mobile, message.getId(), request.csvSha256().toLowerCase());
            BulkMessageReport report = new BulkMessageReport();
            report.setCampaignId(campaignId);
            report.setRecipientMobile(mobile);
            report.setMessageId(message.getId());
            report.setMessageTitle(message.getTitle());
            report.setMessageContent(message.getContent());
            report.setCsvFileName(safeCsvName(request.csvFileName()));
            report.setCsvSha256(request.csvSha256().toLowerCase());
            report.setDuplicateMessageCount(duplicates);
            report.setUsername(user.getUsername());
            report.setSentAt(sentAt);
            reportRepository.save(report);
            recorded++;
        }
        return recorded;
    }

    @Transactional(readOnly = true)
    public Page<BulkMessageReport> find(LocalDateTime from, LocalDateTime to, Pageable pageable) {
        return reportRepository.findBySentAtGreaterThanEqualAndSentAtLessThan(from, to, pageable);
    }

    @Transactional(readOnly = true)
    public Page<BulkMessageReport> find(LocalDateTime from, LocalDateTime to, String username,
            Pageable pageable) {
        String filter = normalizeUsernameFilter(username);
        return filter.isBlank()
                ? find(from, to, pageable)
                : reportRepository.findBySentAtGreaterThanEqualAndSentAtLessThanAndUsernameContainingIgnoreCase(
                        from, to, filter, pageable);
    }

    @Transactional(readOnly = true)
    public List<BulkMessageReport> findAll(LocalDateTime from, LocalDateTime to) {
        return reportRepository.findBySentAtGreaterThanEqualAndSentAtLessThanOrderBySentAtDesc(from, to);
    }

    @Transactional(readOnly = true)
    public List<BulkMessageReport> findAll(LocalDateTime from, LocalDateTime to, String username) {
        String filter = normalizeUsernameFilter(username);
        return filter.isBlank()
                ? findAll(from, to)
                : reportRepository.findBySentAtGreaterThanEqualAndSentAtLessThanAndUsernameContainingIgnoreCaseOrderBySentAtDesc(
                        from, to, filter);
    }

    @Transactional
    public int deleteSelected(List<Long> ids) {
        List<Long> safeIds = ids == null ? List.of() : ids.stream()
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (safeIds.isEmpty()) return 0;
        List<BulkMessageReport> reports = reportRepository.findAllById(safeIds);
        reportRepository.deleteAllInBatch(reports);
        return reports.size();
    }

    public DateRange daily(LocalDate date) {
        return new DateRange(date.atStartOfDay(), date.plusDays(1).atStartOfDay());
    }

    public DateRange monthly(YearMonth month) {
        return new DateRange(month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay());
    }

    private String normalizeMobile(String value) {
        String digits = value == null ? "" : value.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) return digits.substring(2);
        return digits;
    }

    private String safeCsvName(String value) {
        if (value == null || value.isBlank()) return "contacts.csv";
        String name = value.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replace("\r", "").replace("\n", "");
        return name.length() > 500 ? name.substring(0, 500) : name;
    }

    private String normalizeUsernameFilter(String value) {
        if (value == null) return "";
        String filter = value.trim();
        return filter.length() > 100 ? filter.substring(0, 100) : filter;
    }

    public record RecordBulkSendRequest(Long messageId, String csvFileName, String csvSha256,
            List<String> recipients) { }
    public record DateRange(LocalDateTime from, LocalDateTime to) { }
}
