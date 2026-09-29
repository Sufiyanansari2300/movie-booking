package com.sufiyan.moviebooking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record HoldSeatsRequest(
        @Schema(description = "showSeatId values from GET /api/shows/{id}/seats", example = "[1, 2]")
        @NotEmpty List<@NotNull @Positive Long> showSeatIds) {
}
