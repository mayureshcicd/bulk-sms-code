package com.sms.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
public class CsvReader {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    public record ContactCsvEntry(String name, String phoneNumber, String email) {}

    // Detailed recipient parser supporting Name, Mobile/Phone, and Email
    public List<ContactCsvEntry> readRecipientsFromCSV(MultipartFile csvFile) throws Exception {
        Map<String, ContactCsvEntry> uniqueRecipients = new LinkedHashMap<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(csvFile.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            boolean isFirstLine = true;
            int nameCol = -1;
            int phoneCol = -1;
            int emailCol = -1;

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                // Handle both comma, semicolon, or tab separation
                String[] rawTokens = line.split("[,;\\t]");
                List<String> tokens = new ArrayList<>();
                for (String t : rawTokens) {
                    tokens.add(t.trim().replaceAll("^\"|\"$", ""));
                }

                // Header detection
                if (isFirstLine) {
                    boolean hasHeader = false;
                    for (int i = 0; i < tokens.size(); i++) {
                        String lower = tokens.get(i).toLowerCase();
                        if (lower.contains("name")) {
                            nameCol = i;
                            hasHeader = true;
                        } else if (lower.contains("phone") || lower.contains("mobile") ||
                                lower.contains("number") || lower.contains("contact")) {
                            phoneCol = i;
                            hasHeader = true;
                        } else if (lower.contains("email") || lower.contains("mail")) {
                            emailCol = i;
                            hasHeader = true;
                        }
                    }

                    isFirstLine = false;
                    if (hasHeader) {
                        continue;
                    }
                }

                String parsedName = null;
                String parsedPhone = null;
                String parsedEmail = null;

                if (phoneCol != -1 || nameCol != -1 || emailCol != -1) {
                    if (nameCol != -1 && nameCol < tokens.size()) parsedName = tokens.get(nameCol).trim();
                    if (phoneCol != -1 && phoneCol < tokens.size()) parsedPhone = tokens.get(phoneCol).trim();
                    if (emailCol != -1 && emailCol < tokens.size()) parsedEmail = tokens.get(emailCol).trim();
                } else if (tokens.size() == 1) {
                    String val = tokens.get(0).trim();
                    if (EMAIL_PATTERN.matcher(val).matches()) {
                        parsedEmail = val;
                    } else {
                        parsedPhone = val;
                    }
                } else {
                    // Positional heuristics:
                    // Check if column contains @ -> email
                    // Check if column contains 10-15 digits -> phone
                    // Remaining string -> name
                    for (String token : tokens) {
                        String clean = token.trim();
                        if (clean.isEmpty()) continue;
                        if (EMAIL_PATTERN.matcher(clean).matches() && parsedEmail == null) {
                            parsedEmail = clean;
                        } else {
                            String digits = clean.replaceAll("\\D", "");
                            if (digits.length() >= 10 && digits.length() <= 15 && parsedPhone == null) {
                                parsedPhone = digits;
                            } else if (parsedName == null && !clean.matches("^\\d+$")) {
                                parsedName = clean;
                            }
                        }
                    }
                }

                // Clean phone digits
                String cleanDigits = parsedPhone != null ? parsedPhone.replaceAll("\\D", "") : "";
                if (cleanDigits.length() >= 10 && cleanDigits.length() <= 15) {
                    parsedPhone = cleanDigits;
                } else {
                    parsedPhone = null;
                }

                // Clean email
                if (parsedEmail != null && !EMAIL_PATTERN.matcher(parsedEmail).matches()) {
                    parsedEmail = null;
                }

                // Skip completely invalid row
                if ((parsedPhone == null || parsedPhone.isBlank()) && (parsedEmail == null || parsedEmail.isBlank())) {
                    log.warn("Skipping invalid CSV row: {}", line);
                    continue;
                }

                // Clean name
                if (parsedName != null) {
                    parsedName = parsedName.strip();
                    if (parsedName.length() > 100) parsedName = parsedName.substring(0, 100);
                    if (parsedName.isBlank()) parsedName = null;
                }

                String dedupeKey = (parsedPhone != null ? "P:" + parsedPhone : "") + (parsedEmail != null ? "_E:" + parsedEmail.toLowerCase() : "");
                uniqueRecipients.putIfAbsent(dedupeKey, new ContactCsvEntry(parsedName, parsedPhone, parsedEmail));
            }
        }

        return new ArrayList<>(uniqueRecipients.values());
    }

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
