package com.example.demo.service;

import com.example.demo.dto.UserRequest;
import com.example.demo.dto.UserResponse;
import com.example.demo.exception.UserAlreadyExistsException;
import com.example.demo.exception.UserNotFoundException;
import com.example.demo.model.User;
import com.example.demo.model.UserStatus;
import com.example.demo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DisplayName("UserService Integration Tests")
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        doNothing().when(emailService).sendWelcomeEmail(anyString());
    }

    @Test
    @DisplayName("should create user and persist to database")
    void shouldCreateUserAndPersistToDatabase() {
        UserRequest request = new UserRequest("John Doe", "john@example.com");

        UserResponse response = userService.createUser(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();
        assertThat(response.getName()).isEqualTo("John Doe");

        User savedUser = userRepository.findById(response.getId()).orElseThrow();
        assertThat(savedUser.getName()).isEqualTo("John Doe");
        assertThat(savedUser.getEmail()).isEqualTo("john@example.com");

        verify(emailService, times(1)).sendWelcomeEmail("john@example.com");
    }

    @Test
    @DisplayName("should throw exception when creating duplicate user")
    void shouldThrowExceptionWhenCreatingDuplicateUser() {
        UserRequest request = new UserRequest("John Doe", "john@example.com");
        userService.createUser(request);

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("should retrieve all active users from database")
    void shouldRetrieveAllActiveUsersFromDatabase() {
        User user1 = new User("User 1", "user1@example.com");
        User user2 = new User("User 2", "user2@example.com");
        User inactiveUser = new User("Inactive", "inactive@example.com");
        inactiveUser.setStatus(UserStatus.INACTIVE);

        userRepository.save(user1);
        userRepository.save(user2);
        userRepository.save(inactiveUser);

        List<UserResponse> activeUsers = userService.getAllActiveUsers();

        assertThat(activeUsers).hasSize(2);
        assertThat(activeUsers)
                .extracting(UserResponse::getEmail)
                .containsExactlyInAnyOrder("user1@example.com", "user2@example.com");
    }

    @Test
    @DisplayName("should update user and persist changes")
    void shouldUpdateUserAndPersistChanges() {
        UserRequest createRequest = new UserRequest("John Doe", "john@example.com");
        UserResponse created = userService.createUser(createRequest);

        UserRequest updateRequest = new UserRequest("John Updated", "johnupdated@example.com");

        UserResponse updated = userService.updateUser(created.getId(), updateRequest);

        assertThat(updated.getName()).isEqualTo("John Updated");
        assertThat(updated.getEmail()).isEqualTo("johnupdated@example.com");

        User savedUser = userRepository.findById(created.getId()).orElseThrow();
        assertThat(savedUser.getName()).isEqualTo("John Updated");
    }

    @Test
    @DisplayName("should delete user from database")
    void shouldDeleteUserFromDatabase() {
        UserRequest request = new UserRequest("John Doe", "john@example.com");
        UserResponse created = userService.createUser(request);
        Long userId = created.getId();

        userService.deleteUser(userId);

        assertThat(userRepository.findById(userId)).isEmpty();
    }

    @Test
    @DisplayName("should throw exception when getting non-existent user")
    void shouldThrowExceptionWhenGettingNonExistentUser() {
        assertThatThrownBy(() -> userService.getUser(999L))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("not found");
    }
}
