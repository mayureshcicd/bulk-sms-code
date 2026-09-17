package com.sms.controller;

import com.sms.domain.Contact;
import com.sms.service.ContactService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/contacts")
public class ContactController {
    private final ContactService contacts;
    public ContactController(ContactService contacts) { this.contacts = contacts; }
    public record ContactInput(String name, String phoneNumber, String email) {}
    public record ContactView(Long id, String name, String phoneNumber, String email) {
        static ContactView from(Contact c) { return new ContactView(c.getId(), c.getName(), c.getPhoneNumber(), c.getEmail()); }
    }
    @GetMapping public List<ContactView> list() { return contacts.list().stream().map(ContactView::from).toList(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ContactView create(@RequestBody ContactInput input) {
        return ContactView.from(contacts.save(null, input.name(), input.phoneNumber(), input.email()));
    }
    @PutMapping("/{id}") public ContactView update(@PathVariable Long id, @RequestBody ContactInput input) {
        return ContactView.from(contacts.save(id, input.name(), input.phoneNumber(), input.email()));
    }
    @PostMapping("/import-csv")
    public Map<String, Object> importCsv(@RequestParam("csvFile") MultipartFile csvFile) throws Exception {
        return contacts.importFromCsv(csvFile);
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { contacts.delete(id); }
}
