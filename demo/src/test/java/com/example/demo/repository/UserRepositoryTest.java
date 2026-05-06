package com.example.demo.repository;

import com.example.demo.model.User;
import com.example.demo.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DisplayName("UserRepository Integration Tests")
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User("John Doe", "john@example.com");
    }

    @Test
    @DisplayName("should save and find user by id")
    void shouldSaveAndFindUserById() {
        User saved = entityManager.persistAndFlush(testUser);

        Optional<User> found = userRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("John Doe");
        assertThat(found.get().getEmail()).isEqualTo("john@example.com");
    }

    @Test
    @DisplayName("should find user by email")
    void shouldFindUserByEmail() {
        entityManager.persistAndFlush(testUser);

        Optional<User> found = userRepository.findByEmail("john@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("John Doe");
    }

    @Test
    @DisplayName("should return empty when email not found")
    void shouldReturnEmptyWhenEmailNotFound() {
        Optional<User> found = userRepository.findByEmail("notfound@example.com");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("should find users by status")
    void shouldFindUsersByStatus() {
        User activeUser = new User("Active User", "active@example.com");
        User inactiveUser = new User("Inactive User", "inactive@example.com");
        inactiveUser.setStatus(UserStatus.INACTIVE);

        entityManager.persist(activeUser);
        entityManager.persist(inactiveUser);
        entityManager.flush();

        List<User> activeUsers = userRepository.findByStatus(UserStatus.ACTIVE);

        assertThat(activeUsers).hasSize(1);
        assertThat(activeUsers.get(0).getEmail()).isEqualTo("active@example.com");
    }

    @Test
    @DisplayName("should check if email exists")
    void shouldCheckIfEmailExists() {
        entityManager.persistAndFlush(testUser);

        boolean exists = userRepository.existsByEmail("john@example.com");
        boolean notExists = userRepository.existsByEmail("notfound@example.com");

        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    @DisplayName("should delete user")
    void shouldDeleteUser() {
        User saved = entityManager.persistAndFlush(testUser);
        Long userId = saved.getId();

        userRepository.deleteById(userId);
        entityManager.flush();

        Optional<User> found = userRepository.findById(userId);
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("should enforce unique email constraint")
    void shouldEnforceUniqueEmailConstraint() {
        entityManager.persistAndFlush(testUser);

        User duplicateUser = new User("Another User", "john@example.com");

        try {
            entityManager.persistAndFlush(duplicateUser);
            assertThat(false).isTrue();
        } catch (Exception e) {
            assertThat(e).isNotNull();
        }
    }
}
