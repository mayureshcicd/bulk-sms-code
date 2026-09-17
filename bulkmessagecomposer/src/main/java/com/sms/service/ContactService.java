package com.sms.service;

import com.sms.domain.Contact;
import com.sms.repository.ContactRepository;
import com.sms.repository.UserRepository;
import com.sms.util.CsvReader;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@Service
public class ContactService {
    private final ContactRepository contacts;
    private final UserRepository users;
    private final CsvReader csvReader;
    public ContactService(ContactRepository contacts, UserRepository users, CsvReader csvReader) {
        this.contacts = contacts; this.users = users; this.csvReader = csvReader;
    }
    private Long ownerId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return users.findByUsername(auth.getName()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)).getId();
    }
    public List<Contact> list() { return contacts.findByOwnerIdOrderByNameAsc(ownerId()); }
    @Transactional
    public Contact save(Long id, String name, String phoneNumber) {
        Long owner = ownerId();
        Contact contact = id == null ? new Contact() : contacts.findByIdAndOwnerId(id, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contact not found"));
        if (name == null || name.isBlank() || name.strip().length() > 100 || phoneNumber == null
                || !phoneNumber.matches("[0-9]{10,15}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a name and a phone number with 10–15 digits");
        }
        contact.setOwnerId(owner); contact.setName(name.strip()); contact.setPhoneNumber(phoneNumber);
        return contacts.save(contact);
    }
    @Transactional
    public void delete(Long id) {
        Contact contact = contacts.findByIdAndOwnerId(id, ownerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contact not found"));
        contacts.delete(contact);
    }
    public List<String> recipients(MultipartFile csv, List<Long> ids) throws Exception {
        boolean selected = ids != null && !ids.isEmpty();
        if (selected && csv != null && !csv.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose either a CSV/TXT file or contacts");
        if (!selected) {
            if (csv == null || csv.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a CSV/TXT file or contacts");
            return csvReader.readPhoneNumbersFromCSV(csv);
        }
        var uniqueIds = new LinkedHashSet<>(ids);
        List<Contact> found = contacts.findByOwnerIdAndIdIn(ownerId(), uniqueIds);
        if (found.size() != uniqueIds.size()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more selected contacts are unavailable");
        Map<Long, Contact> byId = new HashMap<>();
        found.forEach(contact -> byId.put(contact.getId(), contact));
        return uniqueIds.stream().map(id -> csvReader.validateAndFormatNumber(byId.get(id).getPhoneNumber())).distinct().toList();
    }
}
