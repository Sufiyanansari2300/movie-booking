package com.sufiyan.moviebooking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param reminderLeadTime how long before a show starts its reminder goes out
 * @param maxAttempts      delivery attempts per notification before it stays FAILED
 */
@ConfigurationProperties(prefix = "app.notifications")
public record NotificationProperties(Duration reminderLeadTime, int maxAttempts) {
}
