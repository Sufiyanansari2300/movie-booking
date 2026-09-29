package com.sufiyan.moviebooking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record RefundPolicyRequest(
        @Schema(example = "Standard") @NotBlank @Size(max = 100) String name,
        @Schema(description = "Time bands; cancelling at least minHoursBeforeShow hours before the show refunds "
                + "refundPercent. Below every band the refund is 0%.")
        @NotEmpty @Size(max = 10) List<@Valid @NotNull Band> rules,
        @Schema(description = "Make this the active policy (deactivates the current one)") Boolean active) {

    public record Band(
            @Schema(example = "48") @Min(0) @Max(720) int minHoursBeforeShow,
            @Schema(example = "100") @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
            BigDecimal refundPercent) {
    }
}
