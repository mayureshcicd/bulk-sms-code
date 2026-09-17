package com.sms.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class CsvReaderTest {

    private final CsvReader csvReader = new CsvReader();

    @Test
    void removesDuplicateRecipientsAndPreservesCsvOrder() throws Exception {
        var csv = new MockMultipartFile(
                "csv",
                "recipients.csv",
                "text/csv",
                "mobile\n9822004153\n9822004152\n9822004153\n".getBytes(StandardCharsets.UTF_8));

        assertEquals(
                List.of("919822004153@c.us", "919822004152@c.us"),
                csvReader.readPhoneNumbersFromCSV(csv));
    }

    @Test
    void readsRecipientsWithNameMobileAndEmail() throws Exception {
        String content = "name,mobile,email\nMayuresh,9823510523,mayuresh@example.com\nAlice,7276022904,alice@domain.org\n";
        var csv = new MockMultipartFile("csv", "contacts.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));

        List<CsvReader.ContactCsvEntry> entries = csvReader.readRecipientsFromCSV(csv);
        assertEquals(2, entries.size());
        assertEquals("Mayuresh", entries.get(0).name());
        assertEquals("9823510523", entries.get(0).phone());
        assertEquals("mayuresh@example.com", entries.get(0).email());

        assertEquals("Alice", entries.get(1).name());
        assertEquals("7276022904", entries.get(1).phone());
        assertEquals("alice@domain.org", entries.get(1).email());
    }

    @Test
    void readsSingleColumnPhoneNumbersGracefully() throws Exception {
        String content = "9823510523\n7276022904\n9049737589\n";
        var csv = new MockMultipartFile("csv", "phones.txt", "text/plain", content.getBytes(StandardCharsets.UTF_8));

        List<CsvReader.ContactCsvEntry> entries = csvReader.readRecipientsFromCSV(csv);
        assertEquals(3, entries.size());
        assertEquals("9823510523", entries.get(0).phone());
        assertEquals("7276022904", entries.get(1).phone());
        assertEquals("9049737589", entries.get(2).phone());
    }
}
