package com.sufiyan.moviebooking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record TheaterRequest(
        @NotNull @Positive Long cityId,
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 255) String address) {
}
