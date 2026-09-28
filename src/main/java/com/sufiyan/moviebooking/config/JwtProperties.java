package com.sufiyan.moviebooking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT settings. {@code secret} is the HS256 signing key (at least 32 bytes) and must come from
 * JWT_SECRET or local.properties, never from committed config.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration expiration, String issuer) {
}
