package com.sufiyan.moviebooking.controller;

import com.sufiyan.moviebooking.dto.LoginRequest;
import com.sufiyan.moviebooking.dto.LoginResponse;
import com.sufiyan.moviebooking.dto.RegisterRequest;
import com.sufiyan.moviebooking.dto.UserResponse;
import com.sufiyan.moviebooking.security.AppUserPrincipal;
import com.sufiyan.moviebooking.service.AuthService;
import com.sufiyan.moviebooking.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserResponse.from(userService.registerCustomer(request));
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AppUserPrincipal principal) {
        return UserResponse.from(userService.getById(principal.id()));
    }
}
