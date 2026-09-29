package com.sufiyan.moviebooking.dto;

import com.sufiyan.moviebooking.entity.PricingRuleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalTime;

public record PricingRuleRequest(
        @Schema(example = "Weekend surcharge") @NotBlank @Size(max = 100) String name,
        @NotNull PricingRuleType ruleType,
        @Schema(example = "20.00", description = "Percent added to base prices; negative for a reduction")
        @NotNull @DecimalMin("-90") @DecimalMax("300") @Digits(integer = 4, fraction = 2) BigDecimal adjustmentPercent,
        @Schema(example = "18:00", description = "PRIME_TIME only: window start (inclusive), show local time")
        LocalTime windowStart,
        @Schema(example = "22:00", description = "PRIME_TIME only: window end (exclusive)")
        LocalTime windowEnd,
        @Schema(description = "Defaults to true") Boolean active) {
}
