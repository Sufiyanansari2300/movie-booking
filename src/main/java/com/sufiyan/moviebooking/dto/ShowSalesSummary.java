package com.sufiyan.moviebooking.dto;

import com.sufiyan.moviebooking.entity.ShowStatus;

import java.math.BigDecimal;

/** Occupancy and money for one show. {@code heldSeats} counts only holds that are still valid. */
public record ShowSalesSummary(
        Long showId,
        ShowStatus status,
        long totalSeats,
        long bookedSeats,
        long heldSeats,
        long availableSeats,
        double occupancyPercent,
        long confirmedBookings,
        long cancelledBookings,
        BigDecimal grossPaid,
        BigDecimal refunded,
        BigDecimal netRevenue) {
}
