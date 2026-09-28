package com.sufiyan.moviebooking.dto;

import com.sufiyan.moviebooking.entity.Role;
import com.sufiyan.moviebooking.entity.User;

import java.time.Instant;

public record UserResponse(Long id, String name, String email, Role role, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }
}
