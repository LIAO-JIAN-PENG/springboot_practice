# Spring Boot Testing 完整教材

## 📚 目錄
1. [概念理解](#概念理解)
2. [所需套件](#所需套件)
3. [測試金字塔](#測試金字塔)
4. [完整範例](#完整範例)
5. [最佳實踐](#最佳實踐)

---

## 概念理解

### Unit Test vs Integration Test

| 維度 | Unit Test | Integration Test |
|------|-----------|------------------|
| **測試範圍** | 單一類別的邏輯 | 多個元件協作 |
| **依賴處理** | Mock/Stub 所有外部依賴 | 使用真實或接近真實的依賴 |
| **資料庫** | 不連接（Mock Repository） | 連接測試資料庫（H2/Testcontainers） |
| **Spring Context** | 不啟動（或僅啟動最小化 context） | 啟動 Application Context |
| **執行速度** | 極快（毫秒級） | 較慢（秒級） |
| **測試目的** | 驗證業務邏輯正確性 | 驗證元件整合無誤 |

### 具體例子

**假設有 `UserService` 依賴 `UserRepository` 和 `EmailService`：**

```
Unit Test:
- 測試 UserService 的業務邏輯
- Mock UserRepository 和 EmailService
- 不啟動 Spring
- 不連接資料庫

Integration Test:
- 測試 UserService + UserRepository + Database 整合
- 使用真實 Repository
- 啟動部分 Spring Context
- 連接 H2 或 Testcontainers
```

---

## 所需套件

### Maven Dependencies

```xml
<dependencies>
    <!-- Spring Boot Starter Test (包含 JUnit 5, Mockito, AssertJ 等) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- Spring Boot Starter Web (for REST API testing) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- Spring Boot Starter Data JPA -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>

    <!-- H2 Database (for integration tests) -->
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- REST Assured (Optional: for API testing) -->
    <dependency>
        <groupId>io.rest-assured</groupId>
        <artifactId>rest-assured</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- Testcontainers (Optional: for real database testing) -->
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>testcontainers</artifactId>
        <version>1.19.0</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>mariadb</artifactId>
        <version>1.19.0</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### spring-boot-starter-test 包含的工具

- **JUnit 5**: 測試框架
- **Mockito**: Mock 框架
- **AssertJ**: 流暢的斷言庫
- **Hamcrest**: 匹配器庫
- **JsonPath**: JSON 斷言
- **Spring Test**: Spring 測試支援

---

## 測試金字塔

```
        /\
       /  \  E2E Tests (少量)
      /____\
     /      \
    / Integ. \ Integration Tests (適量)
   /__________\
  /            \
 /  Unit Tests  \ Unit Tests (大量)
/________________\
```

**建議比例**: 70% Unit | 20% Integration | 10% E2E

---

## 完整範例

### 1. Domain Model

```java
package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column(nullable = false)
    private UserStatus status = UserStatus.ACTIVE;
    
    public User(String name, String email) {
        this.name = name;
        this.email = email;
        this.status = UserStatus.ACTIVE;
    }
}
```

```java
package com.example.demo.model;

public enum UserStatus {
    ACTIVE, INACTIVE, SUSPENDED
}
```

### 2. Repository

```java
package com.example.demo.repository;

import com.example.demo.model.User;
import com.example.demo.model.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    List<User> findByStatus(UserStatus status);
    boolean existsByEmail(String email);
}
```

### 3. Service

```java
package com.example.demo.service;

import com.example.demo.dto.UserRequest;
import com.example.demo.dto.UserResponse;
import com.example.demo.exception.UserAlreadyExistsException;
import com.example.demo.exception.UserNotFoundException;
import com.example.demo.model.User;
import com.example.demo.model.UserStatus;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    
    private final UserRepository userRepository;
    private final EmailService emailService;
    
    @Transactional
    public UserResponse createUser(UserRequest request) {
        log.info("Creating user with email: {}", request.getEmail());
        
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("User with email " + request.getEmail() + " already exists");
        }
        
        User user = new User(request.getName(), request.getEmail());
        User saved = userRepository.save(user);
        
        // 發送歡迎信
        emailService.sendWelcomeEmail(saved.getEmail());
        
        log.info("User created successfully with id: {}", saved.getId());
        return toResponse(saved);
    }
    
    public UserResponse getUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
        return toResponse(user);
    }
    
    public List<UserResponse> getAllActiveUsers() {
        return userRepository.findByStatus(UserStatus.ACTIVE)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
    
    @Transactional
    public UserResponse updateUser(Long id, UserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
        
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        
        User updated = userRepository.save(user);
        return toResponse(updated);
    }
    
    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new UserNotFoundException("User not found with id: " + id);
        }
        userRepository.deleteById(id);
    }
    
    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getStatus());
    }
}
```

### 4. Email Service (外部依賴)

```java
package com.example.demo.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {
    
    public void sendWelcomeEmail(String email) {
        log.info("Sending welcome email to: {}", email);
        // 實際發送邏輯...
    }
}
```

### 5. Controller

```java
package com.example.demo.controller;

import com.example.demo.dto.UserRequest;
import com.example.demo.dto.UserResponse;
import com.example.demo.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    
    private final UserService userService;
    
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserRequest request) {
        UserResponse response = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
        UserResponse response = userService.getUser(id);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllActiveUsers() {
        List<UserResponse> responses = userService.getAllActiveUsers();
        return ResponseEntity.ok(responses);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UserRequest request) {
        UserResponse response = userService.updateUser(id, request);
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
```

### 6. DTOs

```java
package com.example.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRequest {
    
    @NotBlank(message = "Name is required")
    private String name;
    
    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    private String email;
}
```

```java
package com.example.demo.dto;

import com.example.demo.model.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String name;
    private String email;
    private UserStatus status;
}
```

### 7. Exceptions

```java
package com.example.demo.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String message) {
        super(message);
    }
}
```

```java
package com.example.demo.exception;

public class UserAlreadyExistsException extends RuntimeException {
    public UserAlreadyExistsException(String message) {
        super(message);
    }
}
```

---

## Unit Tests

### 1. Model Unit Test

```java
package com.example.demo.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("User Model Unit Tests")
class UserTest {
    
    @Test
    @DisplayName("should create user with constructor")
    void shouldCreateUserWithConstructor() {
        // Given
        String name = "John Doe";
        String email = "john@example.com";
        
        // When
        User user = new User(name, email);
        
        // Then
        assertThat(user.getName()).isEqualTo(name);
        assertThat(user.getEmail()).isEqualTo(email);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getId()).isNull();
    }
    
    @Test
    @DisplayName("should set and get all properties")
    void shouldSetAndGetAllProperties() {
        // Given
        User user = new User();
        
        // When
        user.setId(1L);
        user.setName("Jane Doe");
        user.setEmail("jane@example.com");
        user.setStatus(UserStatus.INACTIVE);
        
        // Then
        assertThat(user.getId()).isEqualTo(1L);
        assertThat(user.getName()).isEqualTo("Jane Doe");
        assertThat(user.getEmail()).isEqualTo("jane@example.com");
        assertThat(user.getStatus()).isEqualTo(UserStatus.INACTIVE);
    }
    
    @Test
    @DisplayName("should have default ACTIVE status")
    void shouldHaveDefaultActiveStatus() {
        // Given & When
        User user = new User("Test", "test@example.com");
        
        // Then
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }
}
```

### 2. Service Unit Test

```java
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Unit Tests")
class UserServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private EmailService emailService;
    
    @InjectMocks
    private UserService userService;
    
    private UserRequest validRequest;
    private User validUser;
    
    @BeforeEach
    void setUp() {
        validRequest = new UserRequest("John Doe", "john@example.com");
        validUser = new User(1L, "John Doe", "john@example.com", UserStatus.ACTIVE);
    }
    
    @Test
    @DisplayName("should create user successfully")
    void shouldCreateUserSuccessfully() {
        // Given
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(validUser);
        doNothing().when(emailService).sendWelcomeEmail(anyString());
        
        // When
        UserResponse response = userService.createUser(validRequest);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("John Doe");
        assertThat(response.getEmail()).isEqualTo("john@example.com");
        assertThat(response.getStatus()).isEqualTo(UserStatus.ACTIVE);
        
        // Verify interactions
        verify(userRepository, times(1)).existsByEmail("john@example.com");
        verify(userRepository, times(1)).save(any(User.class));
        verify(emailService, times(1)).sendWelcomeEmail("john@example.com");
    }
    
    @Test
    @DisplayName("should throw exception when user already exists")
    void shouldThrowExceptionWhenUserAlreadyExists() {
        // Given
        when(userRepository.existsByEmail(anyString())).thenReturn(true);
        
        // When & Then
        assertThatThrownBy(() -> userService.createUser(validRequest))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("already exists");
        
        // Verify
        verify(userRepository, times(1)).existsByEmail("john@example.com");
        verify(userRepository, never()).save(any(User.class));
        verify(emailService, never()).sendWelcomeEmail(anyString());
    }
    
    @Test
    @DisplayName("should get user by id successfully")
    void shouldGetUserByIdSuccessfully() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(validUser));
        
        // When
        UserResponse response = userService.getUser(1L);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("John Doe");
        
        verify(userRepository, times(1)).findById(1L);
    }
    
    @Test
    @DisplayName("should throw exception when user not found")
    void shouldThrowExceptionWhenUserNotFound() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        
        // When & Then
        assertThatThrownBy(() -> userService.getUser(1L))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("not found");
        
        verify(userRepository, times(1)).findById(1L);
    }
    
    @Test
    @DisplayName("should get all active users")
    void shouldGetAllActiveUsers() {
        // Given
        User user2 = new User(2L, "Jane Doe", "jane@example.com", UserStatus.ACTIVE);
        when(userRepository.findByStatus(UserStatus.ACTIVE))
                .thenReturn(Arrays.asList(validUser, user2));
        
        // When
        List<UserResponse> responses = userService.getAllActiveUsers();
        
        // Then
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getName()).isEqualTo("John Doe");
        assertThat(responses.get(1).getName()).isEqualTo("Jane Doe");
        
        verify(userRepository, times(1)).findByStatus(UserStatus.ACTIVE);
    }
    
    @Test
    @DisplayName("should update user successfully")
    void shouldUpdateUserSuccessfully() {
        // Given
        UserRequest updateRequest = new UserRequest("John Updated", "johnupdated@example.com");
        User updatedUser = new User(1L, "John Updated", "johnupdated@example.com", UserStatus.ACTIVE);
        
        when(userRepository.findById(1L)).thenReturn(Optional.of(validUser));
        when(userRepository.save(any(User.class))).thenReturn(updatedUser);
        
        // When
        UserResponse response = userService.updateUser(1L, updateRequest);
        
        // Then
        assertThat(response.getName()).isEqualTo("John Updated");
        assertThat(response.getEmail()).isEqualTo("johnupdated@example.com");
        
        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, times(1)).save(any(User.class));
    }
    
    @Test
    @DisplayName("should delete user successfully")
    void shouldDeleteUserSuccessfully() {
        // Given
        when(userRepository.existsById(1L)).thenReturn(true);
        doNothing().when(userRepository).deleteById(1L);
        
        // When
        userService.deleteUser(1L);
        
        // Then
        verify(userRepository, times(1)).existsById(1L);
        verify(userRepository, times(1)).deleteById(1L);
    }
    
    @Test
    @DisplayName("should throw exception when deleting non-existent user")
    void shouldThrowExceptionWhenDeletingNonExistentUser() {
        // Given
        when(userRepository.existsById(1L)).thenReturn(false);
        
        // When & Then
        assertThatThrownBy(() -> userService.deleteUser(1L))
                .isInstanceOf(UserNotFoundException.class);
        
        verify(userRepository, times(1)).existsById(1L);
        verify(userRepository, never()).deleteById(anyLong());
    }
}
```

### 3. Controller Unit Test

```java
package com.example.demo.controller;

import com.example.demo.dto.UserRequest;
import com.example.demo.dto.UserResponse;
import com.example.demo.exception.UserAlreadyExistsException;
import com.example.demo.exception.UserNotFoundException;
import com.example.demo.model.UserStatus;
import com.example.demo.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@DisplayName("UserController Unit Tests")
class UserControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private UserService userService;
    
    private UserRequest validRequest;
    private UserResponse validResponse;
    
    @BeforeEach
    void setUp() {
        validRequest = new UserRequest("John Doe", "john@example.com");
        validResponse = new UserResponse(1L, "John Doe", "john@example.com", UserStatus.ACTIVE);
    }
    
    @Test
    @DisplayName("should create user successfully")
    void shouldCreateUserSuccessfully() throws Exception {
        // Given
        when(userService.createUser(any(UserRequest.class))).thenReturn(validResponse);
        
        // When & Then
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("John Doe"))
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        
        verify(userService, times(1)).createUser(any(UserRequest.class));
    }
    
    @Test
    @DisplayName("should return 400 when request validation fails")
    void shouldReturn400WhenValidationFails() throws Exception {
        // Given
        UserRequest invalidRequest = new UserRequest("", "invalid-email");
        
        // When & Then
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
        
        verify(userService, never()).createUser(any(UserRequest.class));
    }
    
    @Test
    @DisplayName("should get user by id successfully")
    void shouldGetUserByIdSuccessfully() throws Exception {
        // Given
        when(userService.getUser(1L)).thenReturn(validResponse);
        
        // When & Then
        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("John Doe"));
        
        verify(userService, times(1)).getUser(1L);
    }
    
    @Test
    @DisplayName("should return 404 when user not found")
    void shouldReturn404WhenUserNotFound() throws Exception {
        // Given
        when(userService.getUser(1L)).thenThrow(new UserNotFoundException("User not found"));
        
        // When & Then
        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isNotFound());
        
        verify(userService, times(1)).getUser(1L);
    }
    
    @Test
    @DisplayName("should get all active users")
    void shouldGetAllActiveUsers() throws Exception {
        // Given
        UserResponse user2 = new UserResponse(2L, "Jane Doe", "jane@example.com", UserStatus.ACTIVE);
        List<UserResponse> users = Arrays.asList(validResponse, user2);
        when(userService.getAllActiveUsers()).thenReturn(users);
        
        // When & Then
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("John Doe"))
                .andExpect(jsonPath("$[1].name").value("Jane Doe"));
        
        verify(userService, times(1)).getAllActiveUsers();
    }
    
    @Test
    @DisplayName("should update user successfully")
    void shouldUpdateUserSuccessfully() throws Exception {
        // Given
        UserResponse updatedResponse = new UserResponse(1L, "John Updated", "john@example.com", UserStatus.ACTIVE);
        when(userService.updateUser(eq(1L), any(UserRequest.class))).thenReturn(updatedResponse);
        
        // When & Then
        mockMvc.perform(put("/api/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("John Updated"));
        
        verify(userService, times(1)).updateUser(eq(1L), any(UserRequest.class));
    }
    
    @Test
    @DisplayName("should delete user successfully")
    void shouldDeleteUserSuccessfully() throws Exception {
        // Given
        doNothing().when(userService).deleteUser(1L);
        
        // When & Then
        mockMvc.perform(delete("/api/users/1"))
                .andExpect(status().isNoContent());
        
        verify(userService, times(1)).deleteUser(1L);
    }
}
```

---

## Integration Tests

### 1. Repository Integration Test

```java
package com.example.demo.repository;

import com.example.demo.model.User;
import com.example.demo.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

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
        // Given
        User saved = entityManager.persistAndFlush(testUser);
        
        // When
        Optional<User> found = userRepository.findById(saved.getId());
        
        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("John Doe");
        assertThat(found.get().getEmail()).isEqualTo("john@example.com");
    }
    
    @Test
    @DisplayName("should find user by email")
    void shouldFindUserByEmail() {
        // Given
        entityManager.persistAndFlush(testUser);
        
        // When
        Optional<User> found = userRepository.findByEmail("john@example.com");
        
        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("John Doe");
    }
    
    @Test
    @DisplayName("should return empty when email not found")
    void shouldReturnEmptyWhenEmailNotFound() {
        // When
        Optional<User> found = userRepository.findByEmail("notfound@example.com");
        
        // Then
        assertThat(found).isEmpty();
    }
    
    @Test
    @DisplayName("should find users by status")
    void shouldFindUsersByStatus() {
        // Given
        User activeUser = new User("Active User", "active@example.com");
        User inactiveUser = new User("Inactive User", "inactive@example.com");
        inactiveUser.setStatus(UserStatus.INACTIVE);
        
        entityManager.persist(activeUser);
        entityManager.persist(inactiveUser);
        entityManager.flush();
        
        // When
        List<User> activeUsers = userRepository.findByStatus(UserStatus.ACTIVE);
        
        // Then
        assertThat(activeUsers).hasSize(1);
        assertThat(activeUsers.get(0).getEmail()).isEqualTo("active@example.com");
    }
    
    @Test
    @DisplayName("should check if email exists")
    void shouldCheckIfEmailExists() {
        // Given
        entityManager.persistAndFlush(testUser);
        
        // When
        boolean exists = userRepository.existsByEmail("john@example.com");
        boolean notExists = userRepository.existsByEmail("notfound@example.com");
        
        // Then
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }
    
    @Test
    @DisplayName("should delete user")
    void shouldDeleteUser() {
        // Given
        User saved = entityManager.persistAndFlush(testUser);
        Long userId = saved.getId();
        
        // When
        userRepository.deleteById(userId);
        entityManager.flush();
        
        // Then
        Optional<User> found = userRepository.findById(userId);
        assertThat(found).isEmpty();
    }
    
    @Test
    @DisplayName("should enforce unique email constraint")
    void shouldEnforceUniqueEmailConstraint() {
        // Given
        entityManager.persistAndFlush(testUser);
        
        // When
        User duplicateUser = new User("Another User", "john@example.com");
        
        // Then
        try {
            entityManager.persistAndFlush(duplicateUser);
            assertThat(false).isTrue(); // Should not reach here
        } catch (Exception e) {
            assertThat(e).isNotNull();
        }
    }
}
```

### 2. Service Integration Test

```java
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
@DisplayName("UserService Integration Tests")
class UserServiceIntegrationTest {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private UserRepository userRepository;
    
    @MockBean
    private EmailService emailService;
    
    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        doNothing().when(emailService).sendWelcomeEmail(anyString());
    }
    
    @Test
    @DisplayName("should create user and persist to database")
    void shouldCreateUserAndPersistToDatabase() {
        // Given
        UserRequest request = new UserRequest("John Doe", "john@example.com");
        
        // When
        UserResponse response = userService.createUser(request);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();
        assertThat(response.getName()).isEqualTo("John Doe");
        
        // Verify in database
        User savedUser = userRepository.findById(response.getId()).orElseThrow();
        assertThat(savedUser.getName()).isEqualTo("John Doe");
        assertThat(savedUser.getEmail()).isEqualTo("john@example.com");
        
        verify(emailService, times(1)).sendWelcomeEmail("john@example.com");
    }
    
    @Test
    @DisplayName("should throw exception when creating duplicate user")
    void shouldThrowExceptionWhenCreatingDuplicateUser() {
        // Given
        UserRequest request = new UserRequest("John Doe", "john@example.com");
        userService.createUser(request);
        
        // When & Then
        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("already exists");
    }
    
    @Test
    @DisplayName("should retrieve all active users from database")
    void shouldRetrieveAllActiveUsersFromDatabase() {
        // Given
        User user1 = new User("User 1", "user1@example.com");
        User user2 = new User("User 2", "user2@example.com");
        User inactiveUser = new User("Inactive", "inactive@example.com");
        inactiveUser.setStatus(UserStatus.INACTIVE);
        
        userRepository.save(user1);
        userRepository.save(user2);
        userRepository.save(inactiveUser);
        
        // When
        List<UserResponse> activeUsers = userService.getAllActiveUsers();
        
        // Then
        assertThat(activeUsers).hasSize(2);
        assertThat(activeUsers)
                .extracting(UserResponse::getEmail)
                .containsExactlyInAnyOrder("user1@example.com", "user2@example.com");
    }
    
    @Test
    @DisplayName("should update user and persist changes")
    void shouldUpdateUserAndPersistChanges() {
        // Given
        UserRequest createRequest = new UserRequest("John Doe", "john@example.com");
        UserResponse created = userService.createUser(createRequest);
        
        UserRequest updateRequest = new UserRequest("John Updated", "johnupdated@example.com");
        
        // When
        UserResponse updated = userService.updateUser(created.getId(), updateRequest);
        
        // Then
        assertThat(updated.getName()).isEqualTo("John Updated");
        assertThat(updated.getEmail()).isEqualTo("johnupdated@example.com");
        
        // Verify in database
        User savedUser = userRepository.findById(created.getId()).orElseThrow();
        assertThat(savedUser.getName()).isEqualTo("John Updated");
    }
    
    @Test
    @DisplayName("should delete user from database")
    void shouldDeleteUserFromDatabase() {
        // Given
        UserRequest request = new UserRequest("John Doe", "john@example.com");
        UserResponse created = userService.createUser(request);
        Long userId = created.getId();
        
        // When
        userService.deleteUser(userId);
        
        // Then
        assertThat(userRepository.findById(userId)).isEmpty();
    }
    
    @Test
    @DisplayName("should throw exception when getting non-existent user")
    void shouldThrowExceptionWhenGettingNonExistentUser() {
        // When & Then
        assertThatThrownBy(() -> userService.getUser(999L))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("not found");
    }
}
```

### 3. Controller Integration Test (with MockMvc)

```java
package com.example.demo.controller;

import com.example.demo.dto.UserRequest;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("UserController Integration Tests")
class UserControllerIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private UserRepository userRepository;
    
    @MockBean
    private EmailService emailService;
    
    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        doNothing().when(emailService).sendWelcomeEmail(anyString());
    }
    
    @Test
    @DisplayName("should create user via API and persist to database")
    void shouldCreateUserViaApiAndPersistToDatabase() throws Exception {
        // Given
        UserRequest request = new UserRequest("John Doe", "john@example.com");
        
        // When & Then
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("John Doe"))
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        
        // Verify in database
        User savedUser = userRepository.findByEmail("john@example.com").orElseThrow();
        assertThat(savedUser.getName()).isEqualTo("John Doe");
    }
    
    @Test
    @DisplayName("should return 400 when creating user with invalid email")
    void shouldReturn400WhenCreatingUserWithInvalidEmail() throws Exception {
        // Given
        UserRequest request = new UserRequest("John Doe", "invalid-email");
        
        // When & Then
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    @DisplayName("should get user by id via API")
    void shouldGetUserByIdViaApi() throws Exception {
        // Given
        User user = new User("John Doe", "john@example.com");
        User saved = userRepository.save(user);
        
        // When & Then
        mockMvc.perform(get("/api/users/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.name").value("John Doe"))
                .andExpect(jsonPath("$.email").value("john@example.com"));
    }
    
    @Test
    @DisplayName("should get all active users via API")
    void shouldGetAllActiveUsersViaApi() throws Exception {
        // Given
        userRepository.save(new User("User 1", "user1@example.com"));
        userRepository.save(new User("User 2", "user2@example.com"));
        
        // When & Then
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").exists())
                .andExpect(jsonPath("$[1].name").exists());
    }
    
    @Test
    @DisplayName("should update user via API")
    void shouldUpdateUserViaApi() throws Exception {
        // Given
        User user = new User("John Doe", "john@example.com");
        User saved = userRepository.save(user);
        
        UserRequest updateRequest = new UserRequest("John Updated", "johnupdated@example.com");
        
        // When & Then
        mockMvc.perform(put("/api/users/" + saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("John Updated"))
                .andExpect(jsonPath("$.email").value("johnupdated@example.com"));
        
        // Verify in database
        User updated = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("John Updated");
    }
    
    @Test
    @DisplayName("should delete user via API")
    void shouldDeleteUserViaApi() throws Exception {
        // Given
        User user = new User("John Doe", "john@example.com");
        User saved = userRepository.save(user);
        
        // When & Then
        mockMvc.perform(delete("/api/users/" + saved.getId()))
                .andExpect(status().isNoContent());
        
        // Verify in database
        assertThat(userRepository.findById(saved.getId())).isEmpty();
    }
    
    @Test
    @DisplayName("should return 404 when getting non-existent user")
    void shouldReturn404WhenGettingNonExistentUser() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/users/999"))
                .andExpect(status().isNotFound());
    }
}
```

---

## 最佳實踐

### 1. 測試命名

```java
// Good: 清楚描述測試內容
@Test
void shouldCreateUserSuccessfully() { }

@Test
void shouldThrowExceptionWhenUserAlreadyExists() { }

// Bad: 模糊不清
@Test
void test1() { }

@Test
void testCreateUser() { }
```

### 2. AAA Pattern (Arrange-Act-Assert)

```java
@Test
void shouldCalculateTotal() {
    // Arrange (Given) - 準備測試資料
    Order order = new Order();
    order.addItem(new Item("Book", 100));
    
    // Act (When) - 執行要測試的行為
    double total = order.calculateTotal();
    
    // Assert (Then) - 驗證結果
    assertThat(total).isEqualTo(100.0);
}
```

### 3. 使用 @DisplayName

```java
@DisplayName("User Service Unit Tests")
class UserServiceTest {
    
    @Test
    @DisplayName("should create user successfully when email is unique")
    void shouldCreateUserSuccessfully() { }
}
```

### 4. 測試獨立性

```java
// Good: 每個測試獨立
@BeforeEach
void setUp() {
    userRepository.deleteAll();
}

@Test
void test1() { }

@Test
void test2() { } // 不依賴 test1 的結果
```

### 5. 測試覆蓋率目標

- **Unit Tests**: 80%+ (業務邏輯)
- **Integration Tests**: 主要流程覆蓋
- **E2E Tests**: 關鍵用戶旅程

### 6. Mock vs Real

```java
// Unit Test: Mock 外部依賴
@Mock
private EmailService emailService;

// Integration Test: 使用真實 Repository
@Autowired
private UserRepository userRepository;

// 但 EmailService 還是 Mock (因為不想真的發信)
@MockBean
private EmailService emailService;
```

### 7. 測試資料建立

```java
// Good: 使用 Builder Pattern 或工廠方法
public class UserTestFactory {
    public static User createTestUser() {
        return new User("Test User", "test@example.com");
    }
    
    public static User createTestUser(String email) {
        return new User("Test User", email);
    }
}

// 在測試中使用
User user = UserTestFactory.createTestUser("custom@example.com");
```

### 8. AssertJ 流暢斷言

```java
// Good: 可讀性高
assertThat(user)
    .isNotNull()
    .extracting(User::getName, User::getEmail)
    .containsExactly("John", "john@example.com");

// 或
assertThat(users)
    .hasSize(2)
    .extracting(User::getEmail)
    .containsExactlyInAnyOrder("john@example.com", "jane@example.com");
```

---

## application-test.yml

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
    username: sa
    password:
  
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
    properties:
      hibernate:
        format_sql: true
  
  h2:
    console:
      enabled: true

logging:
  level:
    com.example.demo: DEBUG
    org.hibernate.SQL: DEBUG
```

---

## 執行測試

```bash
# 執行所有測試
mvn test

# 只執行 Unit Tests
mvn test -Dtest=*Test

# 只執行 Integration Tests
mvn test -Dtest=*IntegrationTest

# 產生測試報告
mvn test jacoco:report
```

---

## 總結

### Unit Test 特徵
✅ 不啟動 Spring Context  
✅ Mock 所有依賴  
✅ 快速執行  
✅ 測試單一類別邏輯  

### Integration Test 特徵
✅ 啟動 Spring Context  
✅ 使用真實 Repository + 測試資料庫  
✅ 測試元件協作  
✅ 驗證端到端流程  

### 測試金字塔
```
🔺 E2E Tests (少) - 慢但全面
🔺🔺 Integration Tests (中) - 驗證整合
🔺🔺🔺 Unit Tests (多) - 快速反饋
```
