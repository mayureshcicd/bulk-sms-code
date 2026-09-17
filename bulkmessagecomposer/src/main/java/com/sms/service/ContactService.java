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
        return save(id, name, phoneNumber, null);
    }
    @Transactional
    public Contact save(Long id, String name, String phoneNumber, String email) {
        Long owner = ownerId();
        Contact contact = id == null ? new Contact() : contacts.findByIdAndOwnerId(id, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contact not found"));
        if (name == null || name.isBlank() || name.strip().length() > 100 || phoneNumber == null
                || !phoneNumber.matches("[0-9]{10,15}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a name and a phone number with 10–15 digits");
        }
        String cleanEmail = null;
        if (email != null && !email.isBlank()) {
            cleanEmail = email.strip();
            if (cleanEmail.length() > 150 || !cleanEmail.contains("@")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid email address");
            }
        }
        contact.setOwnerId(owner); contact.setName(name.strip()); contact.setPhoneNumber(phoneNumber);
        contact.setEmail(cleanEmail);
        return contacts.save(contact);
    }
    @Transactional
    public Map<String, Object> importFromCsv(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please upload a valid CSV file");
        }
        Long owner = ownerId();
        List<CsvReader.ContactCsvEntry> entries = csvReader.readRecipientsFromCSV(file);
        if (entries.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No valid contacts found in CSV");
        }
        int imported = 0;
        int updated = 0;
        for (CsvReader.ContactCsvEntry entry : entries) {
            String phone = entry.phoneNumber();
            if (phone == null || !phone.matches("[0-9]{10,15}")) {
                continue;
            }
            String name = entry.name();
            if (name == null || name.isBlank()) {
                name = "User " + (phone.length() > 4 ? phone.substring(phone.length() - 4) : phone);
            }
            if (name.length() > 100) name = name.substring(0, 100);

            String email = entry.email();
            if (email != null && (email.length() > 150 || !email.contains("@"))) {
                email = null;
            }

            Optional<Contact> existing = contacts.findByOwnerIdAndPhoneNumber(owner, phone);
            if (existing.isPresent()) {
                Contact c = existing.get();
                c.setName(name);
                if (email != null) c.setEmail(email);
                contacts.save(c);
                updated++;
            } else {
                Contact c = new Contact();
                c.setOwnerId(owner);
                c.setName(name);
                c.setPhoneNumber(phone);
                c.setEmail(email);
                contacts.save(c);
                imported++;
            }
        }
        return Map.of(
            "imported", imported,
            "updated", updated,
            "total", imported + updated,
            "message", String.format("Processed CSV: %d added, %d updated", imported, updated)
        );
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
    public record RecipientDetail(String name, String phoneNumber, String email, String formattedChatId) {}
    public List<RecipientDetail> recipientsDetailed(MultipartFile csv, List<Long> ids) throws Exception {
        boolean selected = ids != null && !ids.isEmpty();
        if (selected && csv != null && !csv.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose either a CSV/TXT file or contacts");
        if (!selected) {
            if (csv == null || csv.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a CSV/TXT file or contacts");
            List<CsvReader.ContactCsvEntry> entries = csvReader.readRecipientsFromCSV(csv);
            List<RecipientDetail> list = new ArrayList<>();
            for (CsvReader.ContactCsvEntry e : entries) {
                String formatted = (e.phoneNumber() != null && !e.phoneNumber().isBlank())
                        ? csvReader.validateAndFormatNumber(e.phoneNumber())
                        : null;
                list.add(new RecipientDetail(e.name(), e.phoneNumber(), e.email(), formatted));
            }
            return list;
        }
        var uniqueIds = new LinkedHashSet<>(ids);
        List<Contact> found = contacts.findByOwnerIdAndIdIn(ownerId(), uniqueIds);
        if (found.size() != uniqueIds.size()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more selected contacts are unavailable");
        Map<Long, Contact> byId = new HashMap<>();
        found.forEach(contact -> byId.put(contact.getId(), contact));
        List<RecipientDetail> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Long id : uniqueIds) {
            Contact c = byId.get(id);
            if (c != null) {
                String phone = c.getPhoneNumber();
                String key = (phone != null ? phone : "") + "_" + (c.getEmail() != null ? c.getEmail() : "");
                if (seen.add(key)) {
                    String formatted = (phone != null && !phone.isBlank()) ? csvReader.validateAndFormatNumber(phone) : null;
                    result.add(new RecipientDetail(c.getName(), c.getPhoneNumber(), c.getEmail(), formatted));
                }
            }
        }
        return result;
    }
}
