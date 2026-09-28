package com.sufiyan.moviebooking.service;

import com.sufiyan.moviebooking.dto.LoginRequest;
import com.sufiyan.moviebooking.dto.LoginResponse;
import com.sufiyan.moviebooking.dto.UserResponse;
import com.sufiyan.moviebooking.exception.BusinessException;
import com.sufiyan.moviebooking.security.AppUserPrincipal;
import com.sufiyan.moviebooking.security.JwtTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService tokenService;
    private final UserService userService;

    public LoginResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                    UserService.normalizeEmail(request.email()), request.password()));
        } catch (AuthenticationException e) {
            // Same message for unknown email and wrong password, so accounts cannot be enumerated.
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
        }
        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        JwtTokenService.IssuedToken token = tokenService.issue(principal);
        long expiresIn = Duration.between(token.issuedAt(), token.expiresAt()).toSeconds();
        return new LoginResponse(token.value(), "Bearer", expiresIn, token.expiresAt(),
                UserResponse.from(userService.getById(principal.id())));
    }
}
