package com.message.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.message.approval.domain.AppUser;
import com.message.approval.domain.BulkMessageReport;
import com.message.approval.domain.Message;
import com.message.approval.domain.MessageStatus;
import com.message.approval.repository.BulkMessageReportRepository;
import com.message.approval.repository.MessageRepository;
import com.message.approval.repository.UserRepository;
import com.message.approval.service.BulkMessageReportService;
import com.message.approval.service.BulkMessageReportService.RecordBulkSendRequest;

@ExtendWith(MockitoExtension.class)
class BulkMessageReportServiceTest {
    @Mock BulkMessageReportRepository reportRepository;
    @Mock MessageRepository messageRepository;
    @Mock UserRepository userRepository;

    @Test
    void recordsRecipientWithExistingDuplicateCountAndMessageSnapshot() {
        AppUser user = new AppUser();
        user.setId(7L);
        user.setUsername("prakash");
        user.setRole("ROLE_USER");
        Message message = new Message();
        message.setId(42L);
        message.setTitle("Offer");
        message.setContent("Selected content");
        message.setStatus(MessageStatus.APPROVED);
        message.setCreatedBy(user);
        String hash = "a".repeat(64);

        when(userRepository.findByUsername("prakash")).thenReturn(Optional.of(user));
        when(messageRepository.findOneById(42L)).thenReturn(Optional.of(message));
        when(reportRepository.countByRecipientMobileAndMessageIdAndCsvSha256("9822004153", 42L, hash))
                .thenReturn(4L);
        when(reportRepository.save(any(BulkMessageReport.class))).thenAnswer(call -> call.getArgument(0));

        BulkMessageReportService service = new BulkMessageReportService(
                reportRepository, messageRepository, userRepository);
        int recorded = service.record(new RecordBulkSendRequest(
                42L, "customers.csv", hash, List.of("919822004153@c.us")), "prakash");

        ArgumentCaptor<BulkMessageReport> captor = ArgumentCaptor.forClass(BulkMessageReport.class);
        verify(reportRepository).save(captor.capture());
        assertEquals(1, recorded);
        assertEquals("9822004153", captor.getValue().getRecipientMobile());
        assertEquals(4, captor.getValue().getDuplicateMessageCount());
        assertEquals("Selected content", captor.getValue().getMessageContent());
        assertEquals("prakash", captor.getValue().getUsername());
    }
}
