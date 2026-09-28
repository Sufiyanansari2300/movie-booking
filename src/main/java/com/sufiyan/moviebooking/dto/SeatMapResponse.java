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
        List<Row> rows) {

    public record Row(String row, SeatType seatType, BigDecimal price, List<SeatStatus> seats) {
    }

    public record SeatStatus(Long showSeatId, String label, int number, ShowSeatStatus status) {
    }
}
