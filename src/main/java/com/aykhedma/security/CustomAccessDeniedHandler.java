package com.aykhedma.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Custom handler for 403 Forbidden responses from Spring Security.
 * Returns a JSON body with the reason for the denial.
 */
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        String reason = determineReason(request, accessDeniedException);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.FORBIDDEN.value());
        body.put("error", "Forbidden");
        body.put("message", reason);
        body.put("path", request.getRequestURI());

        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private String determineReason(HttpServletRequest request,
                                   AccessDeniedException ex) {
        String uri = request.getRequestURI();

        if (uri.startsWith("/admin")) {
            return "Access denied: this endpoint requires ADMIN role";
        } else if (uri.startsWith("/provider")) {
            return "Access denied: this endpoint requires PROVIDER role";
        } else if (uri.startsWith("/consumer")) {
            return "Access denied: this endpoint requires CONSUMER role";
        }

        // Fall back to the exception's own message
        String msg = ex.getMessage();
        if (msg != null && !msg.isBlank()) {
            return "Access denied: " + msg;
        }

        return "Access denied: you do not have permission to access this resource";
    }
}
