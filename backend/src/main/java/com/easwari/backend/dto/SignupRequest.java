package com.easwari.backend.dto;

import lombok.Data;

@Data
public class SignupRequest {
    private String fullName;
    private String email;
    private String phone;
    private String username;
    private String password;
    private String confirmPassword;
    private String department;
    private String role; // "ROLE_ADMIN" or "ROLE_FACULTY" (defaults to ROLE_FACULTY if null)
}
