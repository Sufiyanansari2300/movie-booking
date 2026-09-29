package com.sufiyan.moviebooking.dto;

import com.sufiyan.moviebooking.entity.SeatType;
import com.sufiyan.moviebooking.entity.ShowStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Times are rendered in the business time zone. {@code endTime} includes the cleanup buffer.
 * {@code prices} are the base prices; {@code effectivePrices} are what customers pay after pricing rules.
 */
public record ShowResponse(
        Long id,
        MovieSummary movie,
        TheaterSummary theater,
        ScreenSummary screen,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        ShowStatus status,
        Map<SeatType, BigDecimal> prices,
        Map<SeatType, BigDecimal> effectivePrices,
        List<String> appliedPricingRules,
        long availableSeats) {

    public record MovieSummary(Long id, String title, String language, int durationMinutes, String certificate) {
    }

    public record TheaterSummary(Long id, String name, String address, Long cityId, String cityName) {
    }

    public record ScreenSummary(Long id, String name) {
    }
}
