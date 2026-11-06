package com.careerpilot.integration;

import com.careerpilot.repository.TargetRoleRepository;
import com.careerpilot.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class PostgreSqlTestcontainersIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("careerpilot_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        try {
            if (postgres != null && postgres.isRunning()) {
                registry.add("spring.datasource.url", postgres::getJdbcUrl);
                registry.add("spring.datasource.username", postgres::getUsername);
                registry.add("spring.datasource.password", postgres::getPassword);
                registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
                registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
            }
        } catch (Throwable ignored) {
            // Docker disabled or unavailable
        }
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TargetRoleRepository targetRoleRepository;

    @Test
    @DisplayName("Testcontainers: Verify Flyway migrations and PostgreSQL connection")
    void testPostgreSqlConnectionAndFlywaySeeding() {
        assertThat(userRepository).isNotNull();
        assertThat(targetRoleRepository.findAll()).isNotEmpty();
    }
}
