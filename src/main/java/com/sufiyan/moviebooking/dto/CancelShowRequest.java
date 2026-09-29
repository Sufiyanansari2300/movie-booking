package com.sufiyan.moviebooking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelShowRequest(@Schema(example = "Projector failure") @NotBlank @Size(max = 255) String reason) {
}
