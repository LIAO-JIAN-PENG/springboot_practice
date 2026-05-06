package com.example.demo.controller;

import com.example.demo.dto.UserRequest;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.EmailService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
@DisplayName("UserController Integration Tests")
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    @DisplayName("should create user via API and persist to database")
    void shouldCreateUserViaApiAndPersistToDatabase() throws Exception {
        UserRequest request = new UserRequest("John Doe", "john@example.com");

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("John Doe"))
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        User savedUser = userRepository.findByEmail("john@example.com").orElseThrow();
        assertThat(savedUser.getName()).isEqualTo("John Doe");
    }

    @Test
    @DisplayName("should return 400 when creating user with invalid email")
    void shouldReturn400WhenCreatingUserWithInvalidEmail() throws Exception {
        UserRequest request = new UserRequest("John Doe", "invalid-email");

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should get user by id via API")
    void shouldGetUserByIdViaApi() throws Exception {
        User user = new User("John Doe", "john@example.com");
        User saved = userRepository.save(user);

        mockMvc.perform(get("/api/users/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.name").value("John Doe"))
                .andExpect(jsonPath("$.email").value("john@example.com"));
    }

    @Test
    @DisplayName("should get all active users via API")
    void shouldGetAllActiveUsersViaApi() throws Exception {
        userRepository.save(new User("User 1", "user1@example.com"));
        userRepository.save(new User("User 2", "user2@example.com"));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").exists())
                .andExpect(jsonPath("$[1].name").exists());
    }

    @Test
    @DisplayName("should update user via API")
    void shouldUpdateUserViaApi() throws Exception {
        User user = new User("John Doe", "john@example.com");
        User saved = userRepository.save(user);

        UserRequest updateRequest = new UserRequest("John Updated", "johnupdated@example.com");

        mockMvc.perform(put("/api/users/" + saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("John Updated"))
                .andExpect(jsonPath("$.email").value("johnupdated@example.com"));

        User updated = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("John Updated");
    }

    @Test
    @DisplayName("should delete user via API")
    void shouldDeleteUserViaApi() throws Exception {
        User user = new User("John Doe", "john@example.com");
        User saved = userRepository.save(user);

        mockMvc.perform(delete("/api/users/" + saved.getId()))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    @DisplayName("should return 404 when getting non-existent user")
    void shouldReturn404WhenGettingNonExistentUser() throws Exception {
        mockMvc.perform(get("/api/users/999"))
                .andExpect(status().isNotFound());
    }
}
