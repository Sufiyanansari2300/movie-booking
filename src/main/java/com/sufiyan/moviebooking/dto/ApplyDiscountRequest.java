package com.sufiyan.moviebooking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApplyDiscountRequest(@Schema(example = "WELCOME10") @NotBlank @Size(max = 30) String code) {
}
