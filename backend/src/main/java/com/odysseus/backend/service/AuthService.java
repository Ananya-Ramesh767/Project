package com.odysseus.backend.service;

import com.odysseus.backend.config.JwtTokenProvider;
import com.odysseus.backend.domain.User;
import com.odysseus.backend.dto.LoginRequest;
import com.odysseus.backend.dto.LoginResponse;
import com.odysseus.backend.dto.SignupRequest;
import com.odysseus.backend.repository.DepartmentRepository;
import com.odysseus.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final DepartmentRepository departmentRepository;

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .or(() -> userRepository.findByEmail(request.getUsername()))
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        if (!user.isActive()) {
            throw new BadCredentialsException("Account is inactive");
        }

        // Role mismatch check: if client sent an expected role, verify it matches
        if (request.getRole() != null && !request.getRole().isBlank()
                && !user.getRole().equals(request.getRole())) {
            throw new BadCredentialsException(
                "Role mismatch: your account is registered as " +
                user.getRole().replace("ROLE_", "") +
                ", not " + request.getRole().replace("ROLE_", ""));
        }

        // Faculty must have a department
        if ("ROLE_FACULTY".equals(user.getRole()) && 
                (user.getDepartment() == null || user.getDepartment().isBlank())) {
            throw new BadCredentialsException(
                "Faculty account is missing a registered department. Contact the administrator.");
        }

        String token = tokenProvider.generateToken(user.getUsername(), user.getRole());

        return LoginResponse.builder()
                .token(token)
                .username(user.getUsername())
                .role(user.getRole())
                .fullName(user.getFullName())
                .department(user.getDepartment())
                .email(user.getEmail())
                .build();
    }

    public LoginResponse signup(SignupRequest request) {
        // Validate required fields
        if (request.getFullName() == null || request.getFullName().trim().isEmpty()) {
            throw new IllegalArgumentException("Full name is required");
        }
        if (request.getEmail() == null || !request.getEmail().matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
            throw new IllegalArgumentException("Valid email address is required");
        }
        if (request.getPhone() == null || !request.getPhone().matches("^\\d{10}$")) {
            throw new IllegalArgumentException("Phone number must be exactly 10 digits");
        }
        if (request.getUsername() == null || request.getUsername().trim().length() < 3) {
            throw new IllegalArgumentException("Username must be at least 3 characters");
        }
        if (request.getPassword() == null || request.getPassword().length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }
        String assignedRole = "ROLE_ADMIN".equalsIgnoreCase(request.getRole()) || "ADMIN".equalsIgnoreCase(request.getRole()) 
                ? "ROLE_ADMIN" 
                : "ROLE_FACULTY";

        if ("ROLE_FACULTY".equals(assignedRole) && (request.getDepartment() == null || request.getDepartment().trim().isEmpty())) {
            throw new IllegalArgumentException("Department is required for faculty registration");
        }

        // Check uniqueness
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username '" + request.getUsername() + "' is already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email '" + request.getEmail() + "' is already registered");
        }

        User user = User.builder()
                .fullName(request.getFullName().trim())
                .email(request.getEmail().trim().toLowerCase())
                .phone(request.getPhone().trim())
                .username(request.getUsername().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(assignedRole)
                .department("ROLE_ADMIN".equals(assignedRole) && (request.getDepartment() == null || request.getDepartment().trim().isEmpty()) ? null : request.getDepartment().trim())
                .active(true)
                .build();

        userRepository.save(user);

        String token = tokenProvider.generateToken(user.getUsername(), user.getRole());

        return LoginResponse.builder()
                .token(token)
                .username(user.getUsername())
                .role(user.getRole())
                .fullName(user.getFullName())
                .department(user.getDepartment())
                .email(user.getEmail())
                .build();
    }
}
