package com.sufiyan.moviebooking.dto;

import com.sufiyan.moviebooking.entity.SeatType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Map;

public record UpdateShowPricesRequest(
        @Schema(example = "{\"REGULAR\": 220.00, \"PREMIUM\": 380.00}")
        @NotEmpty Map<SeatType, @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal> prices) {
}
