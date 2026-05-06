package com.example.demo.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("User Model Unit Tests")
class UserTest {

    @Test
    @DisplayName("should create user with constructor")
    void shouldCreateUserWithConstructor() {
        String name = "John Doe";
        String email = "john@example.com";

        User user = new User(name, email);

        assertThat(user.getName()).isEqualTo(name);
        assertThat(user.getEmail()).isEqualTo(email);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getId()).isNull();
    }

    @Test
    @DisplayName("should set and get all properties")
    void shouldSetAndGetAllProperties() {
        User user = new User();

        user.setId(1L);
        user.setName("Jane Doe");
        user.setEmail("jane@example.com");
        user.setStatus(UserStatus.INACTIVE);

        assertThat(user.getId()).isEqualTo(1L);
        assertThat(user.getName()).isEqualTo("Jane Doe");
        assertThat(user.getEmail()).isEqualTo("jane@example.com");
        assertThat(user.getStatus()).isEqualTo(UserStatus.INACTIVE);
    }

    @Test
    @DisplayName("should have default ACTIVE status")
    void shouldHaveDefaultActiveStatus() {
        User user = new User("Test", "test@example.com");

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }
}
