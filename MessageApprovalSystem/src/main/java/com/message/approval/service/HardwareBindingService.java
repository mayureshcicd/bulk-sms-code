package com.message.approval.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class HardwareBindingService {
    @Value("${hardware-binding.enabled:false}")
    private boolean enabled;
    @Value("${hardware-binding.registration-file:license/registered-system.dat}")
    private String registrationFile;
    @Value("${hardware-binding.dmi-directory:/host-system/dmi}")
    private String dmiDirectory;

    @PostConstruct
    public void validateRegisteredSystem() throws IOException {
        if (!enabled) return;
        String registered = readRequired(Paths.get(registrationFile), "Registered motherboard file is missing");
        String current = currentMotherboardValue();
        if (!registered.equals(current)) {
            throw new IllegalStateException("License is registered for another computer motherboard.");
        }
    }

    private String currentMotherboardValue() throws IOException {
        Path dmi = Paths.get(dmiDirectory);
        String boardSerial = readOptional(dmi.resolve("board_serial"));
        if (!boardSerial.isBlank() && !isPlaceholder(boardSerial)) return boardSerial;
        String productUuid = readOptional(dmi.resolve("product_uuid"));
        if (!productUuid.isBlank() && !isPlaceholder(productUuid)) return productUuid;
        throw new IllegalStateException("Motherboard information is not available on this system.");
    }

    private String readRequired(Path path, String message) throws IOException {
        if (Files.notExists(path)) throw new IllegalStateException(message + ": " + path);
        String value = readOptional(path);
        if (value.isBlank()) throw new IllegalStateException(message + ": " + path);
        return value;
    }

    private String readOptional(Path path) throws IOException {
        return Files.exists(path) ? Files.readString(path).trim() : "";
    }

    private boolean isPlaceholder(String value) {
        String normalized = value.trim().toLowerCase();
        return normalized.equals("none") || normalized.equals("unknown")
                || normalized.equals("not specified") || normalized.equals("to be filled by o.e.m.");
    }
}
