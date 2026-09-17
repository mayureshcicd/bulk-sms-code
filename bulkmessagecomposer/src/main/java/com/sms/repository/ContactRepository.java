package com.sms.repository;

import com.sms.domain.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface ContactRepository extends JpaRepository<Contact, Long> {
    List<Contact> findByOwnerIdOrderByNameAsc(Long ownerId);
    Optional<Contact> findByIdAndOwnerId(Long id, Long ownerId);
    List<Contact> findByOwnerIdAndIdIn(Long ownerId, Collection<Long> ids);
}
