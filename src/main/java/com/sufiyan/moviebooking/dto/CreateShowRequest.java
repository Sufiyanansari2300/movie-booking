package com.sufiyan.moviebooking.dto;

import com.sufiyan.moviebooking.entity.SeatType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

public record CreateShowRequest(
        @NotNull @Positive Long movieId,
        @NotNull @Positive Long screenId,
        @Schema(example = "2026-10-02T18:30:00+05:30", description = "Start time with offset; must be in the future")
        @NotNull OffsetDateTime startTime,
        @Schema(example = "{\"REGULAR\": 200.00, \"PREMIUM\": 350.00}",
                description = "Base price per seat type; required for every seat type on the screen")
        @NotEmpty Map<SeatType, @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal> prices) {
}
