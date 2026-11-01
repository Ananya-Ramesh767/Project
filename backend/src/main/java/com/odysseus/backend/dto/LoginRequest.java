package com.odysseus.backend.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String username;
    private String password;
    private String role; // Expected role from client (ROLE_ADMIN or ROLE_FACULTY)
}
