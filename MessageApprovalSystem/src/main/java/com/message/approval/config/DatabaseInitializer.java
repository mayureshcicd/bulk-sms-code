package com.message.approval.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.message.approval.domain.AppUser;
import com.message.approval.repository.IncomingWhatsAppMessageRepository;
import com.message.approval.repository.UserRepository;

@Configuration
public class DatabaseInitializer {

    @Bean
    public CommandLineRunner normalizeIncomingMessageBodies(
            IncomingWhatsAppMessageRepository incomingMessageRepository) {
        return args -> incomingMessageRepository.replaceNullMessageBodies();
    }

    @Bean
    public CommandLineRunner seedUsers(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.findByUsername("admin").isEmpty()) {
                AppUser admin = new AppUser();
                admin.setUsername("admin");
                admin.setPassword(passwordEncoder.encode("admin#Login"));
                admin.setRole("ROLE_ADMIN");
                admin.setMobileNumber("0000000000");
                userRepository.save(admin);
            }
        };
    }
}
