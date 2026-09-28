package com.sufiyan.moviebooking.security;

import com.sufiyan.moviebooking.exception.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Writes 401/403 responses raised by the security filter chain in the same {@link ApiError} shape
 * as the rest of the API.
 */
@Component
@RequiredArgsConstructor
public class JsonSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        boolean tokenRejected = authException instanceof InvalidBearerTokenException;
        String code = tokenRejected ? "INVALID_TOKEN" : "UNAUTHORIZED";
        String message = tokenRejected ? "Access token is invalid or expired" : "Authentication is required";
        response.setHeader("WWW-Authenticate", "Bearer");
        write(response, ApiError.of(401, code, message, request.getRequestURI()));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, ApiError.of(403, "FORBIDDEN", "You do not have permission to perform this action",
                request.getRequestURI()));
    }

    private void write(HttpServletResponse response, ApiError body) throws IOException {
        response.setStatus(body.status());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
