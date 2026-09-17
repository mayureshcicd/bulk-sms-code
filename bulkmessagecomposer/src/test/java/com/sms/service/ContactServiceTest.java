package com.sms.service;

import com.sms.domain.AppUser;
import com.sms.domain.Contact;
import com.sms.repository.ContactRepository;
import com.sms.repository.UserRepository;
import com.sms.util.CsvReader;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContactServiceTest {
    ContactRepository contacts = mock(ContactRepository.class);
    UserRepository users = mock(UserRepository.class);
    ContactService service = new ContactService(contacts, users, new CsvReader());
    @BeforeEach void login() {
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", 7L);
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", "", List.of()));
    }
    @AfterEach void logout() { SecurityContextHolder.clearContext(); }
    @Test void listIsScopedToLoggedInUser() {
        service.list(); verify(contacts).findByOwnerIdOrderByNameAsc(7L);
    }
    @Test void cannotEditOrDeleteAnotherUsersContact() {
        when(contacts.findByIdAndOwnerId(12L, 7L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.save(12L, "Bob", "9876543210"));
        assertThrows(ResponseStatusException.class, () -> service.delete(12L));
        verify(contacts, never()).save(any()); verify(contacts, never()).delete(any(Contact.class));
    }
    @Test void createUsesAuthenticatedOwnerAndValidatesInput() {
        when(contacts.save(any())).thenAnswer(call -> call.getArgument(0));
        Contact saved = service.save(null, "  Mahesh  ", "9876543210");
        assertEquals(7L, saved.getOwnerId()); assertEquals("Mahesh", saved.getName());
        assertThrows(ResponseStatusException.class, () -> service.save(null, " ", "9876543210"));
        assertThrows(ResponseStatusException.class, () -> service.save(null, "Name", "bad-number"));
    }
    @Test void rejectsForeignOrDeletedRecipientsWithoutPartialSending() {
        when(contacts.findByOwnerIdAndIdIn(eq(7L), any())).thenReturn(List.of());
        assertThrows(ResponseStatusException.class, () -> service.recipients(null, List.of(12L)));
    }
    @Test void selectedContactsPreserveOrderAndDeduplicateNumbers() throws Exception {
        Contact a = contact(1L, "9876543210"), b = contact(2L, "9876543211"), c = contact(3L, "9876543210");
        when(contacts.findByOwnerIdAndIdIn(eq(7L), any())).thenReturn(List.of(a,b,c));
        assertEquals(List.of("919876543211@c.us", "919876543210@c.us"), service.recipients(null, List.of(2L,1L,3L,2L)));
    }
    @Test void csvStillUsesExistingParserAndCannotBeMixedWithContacts() throws Exception {
        var csv = new MockMultipartFile("csv", "phones.csv", "text/csv", "phone\n9876543210\n9876543210\n".getBytes());
        assertEquals(List.of("919876543210@c.us"), service.recipients(csv, null));
        assertThrows(ResponseStatusException.class, () -> service.recipients(csv, List.of(1L)));
        assertThrows(ResponseStatusException.class, () -> service.recipients(null, List.of()));
        verifyNoInteractions(contacts);
    }
    private Contact contact(Long id, String phone) {
        Contact c = new Contact(); ReflectionTestUtils.setField(c, "id", id); c.setPhoneNumber(phone); return c;
    }
}
