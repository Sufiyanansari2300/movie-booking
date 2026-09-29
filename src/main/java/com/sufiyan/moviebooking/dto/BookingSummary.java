package com.sufiyan.moviebooking.dto;

import com.sufiyan.moviebooking.entity.BookingStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** One row of a booking history list. {@code status} is the effective status (expired holds show EXPIRED). */
public record BookingSummary(
        Long id,
        BookingStatus status,
        Long showId,
        String movieTitle,
        String theaterName,
        String cityName,
        String screenName,
        OffsetDateTime showStartTime,
        List<String> seats,
        BigDecimal totalAmount,
        String discountCode,
        BigDecimal refundAmount,
        String customerEmail,
        OffsetDateTime createdAt) {
}
