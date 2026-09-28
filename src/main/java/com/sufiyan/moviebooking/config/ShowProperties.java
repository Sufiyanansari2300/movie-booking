package com.sufiyan.moviebooking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param cleanupBuffer gap after a movie ends before the next show on the same screen can start
 */
@ConfigurationProperties(prefix = "app.shows")
public record ShowProperties(Duration cleanupBuffer) {
}
