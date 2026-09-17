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
}
