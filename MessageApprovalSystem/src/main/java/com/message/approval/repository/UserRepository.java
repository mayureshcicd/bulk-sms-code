package com.message.approval.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.message.approval.domain.AppUser;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
    Optional<AppUser> findByMobileNumber(String mobileNumber);
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByMobileNumber(String mobileNumber);
    List<AppUser> findAllByOrderByUsernameAsc();
}
