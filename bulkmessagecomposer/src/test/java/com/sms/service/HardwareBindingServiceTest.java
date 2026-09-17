package com.sms.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class HardwareBindingServiceTest {
    @TempDir Path directory;

    @Test
    void acceptsRegisteredMotherboardAndRejectsDifferentOne() throws Exception {
        Path dmi = Files.createDirectories(directory.resolve("dmi"));
        Path registration = directory.resolve("registered-system.dat");
        Files.writeString(dmi.resolve("board_serial"), "BOARD-123\n");
        Files.writeString(registration, "BOARD-123\n");
        HardwareBindingService service = service(registration, dmi);
        assertDoesNotThrow(service::validateRegisteredSystem);

        Files.writeString(registration, "OTHER-BOARD\n");
        assertThrows(IllegalStateException.class, service::validateRegisteredSystem);
    }

    private HardwareBindingService service(Path registration, Path dmi) {
        HardwareBindingService service = new HardwareBindingService();
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "registrationFile", registration.toString());
        ReflectionTestUtils.setField(service, "dmiDirectory", dmi.toString());
        return service;
    }
}
