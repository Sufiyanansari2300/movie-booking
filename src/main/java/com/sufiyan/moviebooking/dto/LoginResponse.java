package com.sufiyan.moviebooking.dto;

import java.time.Instant;

public record LoginResponse(String accessToken, String tokenType, long expiresIn, Instant expiresAt, UserResponse user) {
}
