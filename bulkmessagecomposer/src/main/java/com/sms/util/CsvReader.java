package com.sms.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
@Slf4j
@Service
public class CsvReader {

    // Helper method to read phone numbers from CSV
    public List<String> readPhoneNumbersFromCSV(MultipartFile csvFile) throws Exception {
        List<String> phoneNumbers = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(csvFile.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            boolean isFirstLine = true;

            while ((line = reader.readLine()) != null) {
                // Skip empty lines
                if (line.trim().isEmpty()) {
                    continue;
                }

                // Skip header if present (look for common header names)
                if (isFirstLine) {
                    String lowerLine = line.toLowerCase();
                    if (lowerLine.contains("phone") || lowerLine.contains("mobile") ||
                            lowerLine.contains("number") || lowerLine.contains("contact")) {
                        isFirstLine = false;
                        continue;
                    }
                    isFirstLine = false;
                }

                // Clean the phone number (remove quotes, spaces, etc.)
                String phoneNumber = line.trim()
                        .replaceAll("^\"|\"$", "") // Remove quotes
                        .replaceAll("\\s+", "");   // Remove whitespace

                // Extract first column if CSV has multiple columns
                if (phoneNumber.contains(",")) {
                    phoneNumber = phoneNumber.split(",")[0].trim();
                }

                // Validate phone number (only digits, 10-15 characters)
                if (phoneNumber.matches("\\d{10,15}")) {
                    phoneNumbers.add(validateAndFormatNumber(phoneNumber));
                } else {
                    log.warn("Skipping invalid phone number: {}", phoneNumber);
                }
            }
        }

        // Preserve the CSV order, but never send the same recipient twice in one batch.
        return new ArrayList<>(new LinkedHashSet<>(phoneNumbers));
    }
    public String validateAndFormatNumber(String number) {
        // Remove any non-digit characters
        String cleanNumber = number.replaceAll("\\D", "");

        // Ensure country code (default to 91 for India)

        if (!cleanNumber.startsWith("966")) {  //966563093382
            if (!cleanNumber.startsWith("91")) {
                cleanNumber = "91" + cleanNumber;
            }
        }

        // Add @c.us suffix
        return cleanNumber + "@c.us";
    }
}
