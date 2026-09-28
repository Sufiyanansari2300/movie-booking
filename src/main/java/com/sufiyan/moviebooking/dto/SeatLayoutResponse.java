package com.sufiyan.moviebooking.dto;

import com.sufiyan.moviebooking.entity.SeatType;

import java.util.List;
import java.util.Map;

public record SeatLayoutResponse(
        Long screenId,
        String screenName,
        int totalSeats,
        Map<SeatType, Long> seatsByType,
        List<Row> rows) {

    public record Row(String row, SeatType seatType, List<SeatInfo> seats) {
    }

    public record SeatInfo(Long id, String label, int number) {
    }
}
