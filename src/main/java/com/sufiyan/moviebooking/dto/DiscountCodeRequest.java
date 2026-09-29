package com.sufiyan.moviebooking.dto;

import com.sufiyan.moviebooking.entity.DiscountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record DiscountCodeRequest(
        @Schema(example = "WELCOME10", description = "3-30 letters, digits, '_' or '-'; stored upper case")
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{3,30}$", message = "must be 3-30 letters, digits, '_' or '-'")
        String code,
        @Size(max = 255) String description,
        @NotNull DiscountType discountType,
        @Schema(example = "10", description = "Percent (1-100) for PERCENT, amount for FLAT")
        @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal discountValue,
        @Schema(example = "100", description = "PERCENT only: cap on the discount amount")
        @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal maxDiscountAmount,
        @Schema(example = "300", description = "Minimum subtotal for the code to apply")
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal minOrderAmount,
        OffsetDateTime validFrom,
        OffsetDateTime validUntil,
        @Schema(description = "Total confirmed uses allowed; empty = unlimited") @Positive Integer usageLimit,
        @Schema(description = "Confirmed uses per customer; empty = unlimited") @Positive Integer perUserLimit,
        @Schema(description = "Defaults to true") Boolean active) {
}
