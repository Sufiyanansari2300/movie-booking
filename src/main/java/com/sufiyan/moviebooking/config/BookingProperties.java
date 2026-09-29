package com.sufiyan.moviebooking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param holdDuration        how long held seats stay reserved while the customer pays
 * @param maxSeatsPerBooking  upper bound of seats in one booking
 */
@ConfigurationProperties(prefix = "app.booking")
public record BookingProperties(Duration holdDuration, int maxSeatsPerBooking) {
}
