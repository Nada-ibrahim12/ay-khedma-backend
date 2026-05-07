package com.aykhedma.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Custom entry point for 401 Unauthorized responses from Spring Security.
 * Returns a JSON body with the reason for the rejection.
 */
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        String reason = determineReason(request, authException);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("error", "Unauthorized");
        body.put("message", reason);
        body.put("path", request.getRequestURI());

        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private String determineReason(HttpServletRequest request,
                                   AuthenticationException ex) {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || authHeader.isBlank()) {
            return "Authentication required: no token provided. Please include a valid Bearer token in the Authorization header";
        }

        if (!authHeader.startsWith("Bearer ")) {
            return "Authentication required: invalid Authorization header format. Expected 'Bearer <token>'";
        }

        // Token was present but invalid/expired
        String msg = ex.getMessage();
        if (msg != null && !msg.isBlank()) {
            return "Authentication failed: " + msg;
        }

        return "Authentication failed: the provided token is invalid or expired";
    }
}
