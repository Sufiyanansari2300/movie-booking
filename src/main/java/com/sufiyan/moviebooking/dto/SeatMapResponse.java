package com.sufiyan.moviebooking.dto;

import com.sufiyan.moviebooking.entity.SeatType;
import com.sufiyan.moviebooking.entity.ShowSeatStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** Seat availability for one show. Book seats using {@code showSeatId}. */
public record SeatMapResponse(
        Long showId,
        OffsetDateTime startTime,
        String screenName,
        Map<ShowSeatStatus, Long> summary,
        List<String> appliedPricingRules,
        List<Row> rows) {

    /** {@code price} is what a seat in this row costs now (after pricing rules). */
    public record Row(String row, SeatType seatType, BigDecimal basePrice, BigDecimal price, List<SeatStatus> seats) {
    }

    public record SeatStatus(Long showSeatId, String label, int number, ShowSeatStatus status) {
    }
}
