package com.sufiyan.moviebooking.dto;

import com.sufiyan.moviebooking.entity.Screen;

public record ScreenResponse(Long id, String name, Long theaterId, String theaterName, long totalSeats) {

    public static ScreenResponse from(Screen screen, long totalSeats) {
        return new ScreenResponse(screen.getId(), screen.getName(), screen.getTheater().getId(),
                screen.getTheater().getName(), totalSeats);
    }
}
