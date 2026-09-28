package com.sufiyan.moviebooking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ScreenRequest(@NotBlank @Size(max = 50) String name) {
}
