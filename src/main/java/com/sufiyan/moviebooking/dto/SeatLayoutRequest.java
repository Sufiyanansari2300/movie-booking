package com.sufiyan.moviebooking.dto;

import com.sufiyan.moviebooking.entity.SeatType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Whole-screen seat layout described as sections, e.g. rows "A-H" with 12 REGULAR seats each.
 * Submitting a layout replaces the existing one.
 */
public record SeatLayoutRequest(@NotEmpty @Size(max = 26) List<@Valid @NotNull Section> sections) {

    public record Section(
            @NotBlank String rows,
            @Min(1) @Max(50) int seatsPerRow,
            @NotNull SeatType seatType) {
    }
}
