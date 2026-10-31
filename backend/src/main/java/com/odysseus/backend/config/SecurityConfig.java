package com.odysseus.backend.config;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // Use sameOrigin frameOptions to allow H2 console iframe while preventing cross-origin clickjacking
            .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setContentType("application/json");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Authentication required or token invalid/expired\"}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setContentType("application/json");
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"Access denied: insufficient permissions for this resource\"}");
                })
            )
            .authorizeHttpRequests(auth -> auth
                // 1. Public Authentication Endpoints
                .requestMatchers("/api/auth/**").permitAll()

                // 2. Local Development H2 Web Console (sameOrigin frame-protected)
                .requestMatchers("/h2-console/**").permitAll()

                // 3. Academic Calendar: Faculty and Admin can view calendars and holidays
                .requestMatchers(HttpMethod.GET, "/api/admin/calendar/**").hasAnyRole("ADMIN", "FACULTY")

                // 4. Timetable Viewing: Faculty and Admin can inspect batch/faculty timetables and change history
                .requestMatchers(HttpMethod.GET, "/api/timetable/batch/**", "/api/timetable/faculty/**", "/api/timetable/room-history/**").hasAnyRole("ADMIN", "FACULTY")
                .requestMatchers(HttpMethod.GET, "/api/scheduler/jobs/**", "/api/scheduler/timetables/**").hasAnyRole("ADMIN", "FACULTY")
                .requestMatchers(HttpMethod.POST, "/api/timetable/validate").hasAnyRole("ADMIN", "FACULTY")

                // 5. Timetable Modifications: Only Administrators can create, assign, delete, or change rooms
                .requestMatchers("/api/timetable/**").hasRole("ADMIN")

                // 6. Timetable Scheduler Generation & Input: Administrator only
                .requestMatchers("/api/scheduler/**").hasRole("ADMIN")

                // 7. Core Administrative Modules: Faculty, Rooms, Subjects, Batches, Offerings, Readiness, Timeslots, NEP rules
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                // 8. Faculty Portal: Authenticated Faculty & Admin
                .requestMatchers("/api/faculty/**").hasAnyRole("ADMIN", "FACULTY")

                // 9. Lesson Plan Overview: Admin only; Personal tracking & retrieval: Authenticated Faculty / Admin
                .requestMatchers("/api/lesson-plan/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/lesson-plan/**").authenticated()

                // 10. All other requests require authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        String allowedOriginsEnv = System.getenv("CORS_ALLOWED_ORIGINS");
        if (allowedOriginsEnv != null && !allowedOriginsEnv.isBlank()) {
            configuration.setAllowedOrigins(Arrays.stream(allowedOriginsEnv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList());
        } else {
            configuration.setAllowedOrigins(List.of(
                    "http://localhost:5173",
                    "http://127.0.0.1:5173",
                    "http://localhost:3000",
                    "http://localhost:8080"
            ));
        }
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
