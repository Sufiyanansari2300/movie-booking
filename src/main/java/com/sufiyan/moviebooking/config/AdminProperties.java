package com.sufiyan.moviebooking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bootstrap admin account. The password must be supplied via ADMIN_PASSWORD or local.properties,
 * never committed.
 */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(String name, String email, String password) {
}
