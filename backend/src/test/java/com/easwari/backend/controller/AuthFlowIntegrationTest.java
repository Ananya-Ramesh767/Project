package com.easwari.backend.controller;

import com.easwari.backend.dto.LoginRequest;
import com.easwari.backend.dto.LoginResponse;
import com.easwari.backend.dto.SignupRequest;
import com.easwari.backend.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Admin login succeeds with ROLE_ADMIN")
    void testAdminLoginSuccess() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("admin123");
        req.setRole("ROLE_ADMIN");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("ROLE_ADMIN"));
    }

    @Test
    @DisplayName("Login fails with role mismatch")
    void testLoginRoleMismatch() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("admin123");
        req.setRole("ROLE_FACULTY"); // Mismatch!

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Role mismatch")));
    }

    @Test
    @DisplayName("Faculty signup succeeds and can authenticate")
    void testFacultySignupAndLogin() throws Exception {
        String testUser = "fac_test_" + System.currentTimeMillis();
        String testEmail = testUser + "@easwari.edu.in";

        SignupRequest signupReq = new SignupRequest();
        signupReq.setFullName("Dr. Test Faculty");
        signupReq.setEmail(testEmail);
        signupReq.setPhone("9876543210");
        signupReq.setUsername(testUser);
        signupReq.setPassword("Password123!");
        signupReq.setConfirmPassword("Password123!");
        signupReq.setDepartment("Computer Science and Engineering");

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("ROLE_FACULTY"))
                .andExpect(jsonPath("$.department").value("Computer Science and Engineering"));

        // Now login
        LoginRequest loginReq = new LoginRequest();
        loginReq.setUsername(testUser);
        loginReq.setPassword("Password123!");
        loginReq.setRole("ROLE_FACULTY");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("ROLE_FACULTY"))
                .andExpect(jsonPath("$.department").value("Computer Science and Engineering"));
    }

    @Test
    @DisplayName("Signup fails if required department is missing")
    void testFacultySignupMissingDepartment() throws Exception {
        SignupRequest signupReq = new SignupRequest();
        signupReq.setFullName("Dr. Test Missing Dept");
        signupReq.setEmail("nodept@easwari.edu.in");
        signupReq.setPhone("9876543210");
        signupReq.setUsername("nodept_user");
        signupReq.setPassword("Password123!");
        signupReq.setConfirmPassword("Password123!");
        signupReq.setDepartment(""); // Empty!

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Department is required")));
    }
}

