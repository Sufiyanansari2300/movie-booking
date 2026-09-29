package com.sufiyan.moviebooking.dto;

import com.sufiyan.moviebooking.entity.BookingStatus;
import com.sufiyan.moviebooking.entity.SeatType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record BookingResponse(
        Long id,
        BookingStatus status,
        ShowInfo show,
        List<SeatLine> seats,
        BigDecimal totalAmount,
        OffsetDateTime holdExpiresAt,
        OffsetDateTime createdAt) {

    public record ShowInfo(Long id, String movieTitle, String theaterName, String screenName, OffsetDateTime startTime) {
    }

    public record SeatLine(Long showSeatId, String label, SeatType seatType, BigDecimal price) {
    }
}
