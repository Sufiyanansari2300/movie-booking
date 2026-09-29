package com.sufiyan.moviebooking.dto;

import com.sufiyan.moviebooking.entity.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PayRequest(
        @Schema(example = "CARD", allowableValues = {"CARD", "UPI", "WALLET"}) @NotNull PaymentMethod method,
        @Schema(example = "tok_success",
                description = "Mock gateway token: tok_success (default), tok_declined, tok_insufficient_funds, tok_error")
        @Size(max = 100) String paymentToken) {
}
