package com.careerpilot.repository;

import com.careerpilot.entity.Role;
import com.careerpilot.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Should persist User and populate audit timestamps")
    void shouldPersistUserAndAuditTimestamps() {
        User user = User.builder()
                .email("test.fresher@careerpilot.com")
                .fullName("Fresher Candidate")
                .passwordHash("$2a$10$hashedpasswordforvalidation")
                .role(Role.USER)
                .isActive(true)
                .build();

        User savedUser = userRepository.saveAndFlush(user);

        assertThat(savedUser.getId()).isNotNull();
        assertThat(savedUser.getCreatedAt()).isNotNull();
        assertThat(savedUser.getUpdatedAt()).isNotNull();

        Optional<User> found = userRepository.findByEmail("test.fresher@careerpilot.com");
        assertThat(found).isPresent();
        assertThat(found.get().getFullName()).isEqualTo("Fresher Candidate");
    }
}
