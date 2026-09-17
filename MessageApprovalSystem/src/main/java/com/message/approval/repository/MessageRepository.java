package com.message.approval.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.message.approval.domain.AppUser;
import com.message.approval.domain.Message;
import com.message.approval.domain.MessageStatus;

public interface MessageRepository extends JpaRepository<Message, Long> {
    @EntityGraph(attributePaths = {"createdBy", "files"})
    List<Message> findByCreatedByOrderByCreatedAtDesc(AppUser createdBy);

    @EntityGraph(attributePaths = {"createdBy", "files"})
    List<Message> findByStatusOrderByCreatedAtDesc(MessageStatus status);

    @EntityGraph(attributePaths = {"createdBy", "files"})
    List<Message> findByStatusAndCreatedByOrderByCreatedAtDesc(MessageStatus status, AppUser createdBy);

    @EntityGraph(attributePaths = {"createdBy", "files"})
    List<Message> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"createdBy", "files"})
    List<Message> findByReviewedBy(AppUser reviewedBy);

    @EntityGraph(attributePaths = {"createdBy", "files"})
    Optional<Message> findOneById(Long id);
}
