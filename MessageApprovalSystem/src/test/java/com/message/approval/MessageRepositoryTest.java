package com.message.approval;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.message.approval.domain.AppUser;
import com.message.approval.domain.Message;
import com.message.approval.domain.MessageFile;
import com.message.approval.repository.MessageRepository;
import com.message.approval.repository.UserRepository;

@DataJpaTest
class MessageRepositoryTest {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldLoadMessageWithFilesAndCreator() {
        AppUser user = new AppUser();
        user.setUsername("repo-user");
        user.setPassword("secret");
        user.setRole("ROLE_USER");
        user = userRepository.save(user);

        Message message = new Message();
        message.setTitle("Test");
        message.setContent("Body");
        message.setCreatedBy(user);

        MessageFile file = new MessageFile();
        file.setFileName("note.txt");
        file.setStoredName("stored.txt");
        file.setContentType("text/plain");
        file.setSize(10L);
        file.setMessage(message);
        message.getFiles().add(file);

        messageRepository.save(message);

        List<Message> messages = messageRepository.findByCreatedByOrderByCreatedAtDesc(user);
        assertNotNull(messages.get(0).getCreatedBy());
        assertNotNull(messages.get(0).getFiles());
    }
}
