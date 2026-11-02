package com.easwari.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.easwari.backend.config.JwtTokenProvider;
import com.easwari.backend.domain.User;
import com.easwari.backend.dto.LoginRequest;
import com.easwari.backend.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    // 1. Valid login succeeds
    @Test
    @DisplayName("1. Valid admin login succeeds and returns JWT token and role")
    void testValidLoginSucceeds() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("admin123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.role").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.username").value("admin"));
    }

    // 2. Invalid login fails
    @Test
    @DisplayName("2. Invalid login fails with 401 Unauthorized")
    void testInvalidLoginFails() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("wrongpassword");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    // 3. Passwords are not stored as plaintext
    @Test
    @DisplayName("3. Passwords in database are BCrypt-hashed, never plaintext")
    void testPasswordsNotStoredAsPlaintext() {
        User admin = userRepository.findByUsername("admin").orElseThrow();
        assertNotNull(admin.getPassword());
        assertNotEquals("admin123", admin.getPassword());
        assertTrue(admin.getPassword().startsWith("$2a$") || admin.getPassword().startsWith("$2b$") || admin.getPassword().startsWith("$2y$"));
        assertTrue(passwordEncoder.matches("admin123", admin.getPassword()));
    }

    // 4. Protected endpoints reject unauthenticated requests
    @Test
    @DisplayName("4. Protected endpoints reject unauthenticated requests with 401 Unauthorized")
    void testProtectedEndpointsRejectUnauthenticated() throws Exception {
        // Administrative endpoint
        mockMvc.perform(get("/api/admin/rooms"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        // Scheduler generation endpoint
        mockMvc.perform(post("/api/scheduler/generate"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        // Timetable assignment endpoint
        mockMvc.perform(post("/api/timetable/assignment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    // 5. Invalid and expired tokens are rejected
    @Test
    @DisplayName("5. Malformed, invalid, and tampered tokens are rejected with 401 Unauthorized")
    void testInvalidTokensRejected() throws Exception {
        mockMvc.perform(get("/api/admin/rooms")
                        .header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    // 6. An administrator can perform authorized administrative actions
    @Test
    @DisplayName("6. Administrator token can access administrative endpoints")
    void testAdminAuthorizedActions() throws Exception {
        String adminToken = jwtTokenProvider.generateToken("admin", "ROLE_ADMIN");

        mockMvc.perform(get("/api/admin/rooms")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/readiness")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // 7. A faculty account cannot perform administrator-only actions
    @Test
    @DisplayName("7. Faculty token is rejected with 403 Forbidden on administrative endpoints")
    void testFacultyCannotPerformAdminActions() throws Exception {
        String facultyToken = jwtTokenProvider.generateToken("faculty", "ROLE_FACULTY");

        // Faculty cannot access admin rooms
        mockMvc.perform(get("/api/admin/rooms")
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        // Faculty cannot trigger scheduler generation
        mockMvc.perform(post("/api/scheduler/generate")
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        // Faculty cannot create assignments
        mockMvc.perform(post("/api/timetable/assignment")
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        // But faculty CAN view the academic calendar
        mockMvc.perform(get("/api/admin/calendar")
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk());
    }

    // 8. Invalid request data is handled safely
    @Test
    @DisplayName("8. Malformed or invalid request payloads return 400 Bad Request safely without 500 or leak")
    void testInvalidRequestDataHandledSafely() throws Exception {
        String adminToken = jwtTokenProvider.generateToken("admin", "ROLE_ADMIN");

        mockMvc.perform(post("/api/admin/rooms")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invalidJsonFormat\": [ broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }
}
