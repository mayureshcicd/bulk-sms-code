package com.message.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.message.approval.domain.AppUser;
import com.message.approval.domain.Message;
import com.message.approval.domain.MessageStatus;
import com.message.approval.repository.MessageRepository;
import com.message.approval.repository.UserRepository;
import com.message.approval.service.MessageService;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private MessageService messageService;

    @Test
    void shouldApproveMessageWithReason() {
        AppUser admin = new AppUser();
        admin.setUsername("admin");
        admin.setRole("ROLE_ADMIN");

        Message message = new Message();
        message.setStatus(MessageStatus.PENDING);

        when(messageRepository.findById(1L)).thenReturn(Optional.of(message));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Message updated = messageService.reviewMessage(1L, MessageStatus.APPROVED, "Looks good", admin);

        assertEquals(MessageStatus.APPROVED, updated.getStatus());
        assertEquals("Looks good", updated.getReviewReason());
    }

    @Test
    void shouldChangePasswordWhenCurrentPasswordMatches() {
        AppUser user = new AppUser();
        user.setUsername("user");
        user.setPassword("old-hash");
        when(userRepository.findByUsername("user")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old-password", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        assertTrue(messageService.changePassword("user", "old-password", "new-password"));
        assertEquals("new-hash", user.getPassword());
        verify(userRepository).save(user);
    }

    @Test
    void shouldRejectPasswordChangeWhenCurrentPasswordDoesNotMatch() {
        AppUser user = new AppUser();
        user.setPassword("old-hash");
        when(userRepository.findByUsername("user")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "old-hash")).thenReturn(false);

        assertFalse(messageService.changePassword("user", "wrong-password", "new-password"));
        verify(userRepository, never()).save(any(AppUser.class));
    }

    @Test
    void shouldRejectDuplicateUsernameBeforeSaving() {
        when(userRepository.existsByUsernameIgnoreCase("prakash")).thenReturn(true);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> messageService.createUser(" prakash ", "password123", "ROLE_USER", "9822004153"));

        assertEquals("Username is already registered", error.getMessage());
        verify(userRepository, never()).saveAndFlush(any(AppUser.class));
    }

    @Test
    void shouldRejectShortPasswordBeforeSaving() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> messageService.createUser("prakash", "short", "ROLE_USER", "9822004153"));

        assertEquals("Password must contain at least 8 characters", error.getMessage());
        verify(userRepository, never()).saveAndFlush(any(AppUser.class));
    }
}
