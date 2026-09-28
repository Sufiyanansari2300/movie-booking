package com.sufiyan.moviebooking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record MovieRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        @NotBlank @Size(max = 50) String language,
        @NotBlank @Size(max = 50) String genre,
        @Min(1) @Max(600) int durationMinutes,
        @Size(max = 10) String certificate,
        LocalDate releaseDate) {
}
