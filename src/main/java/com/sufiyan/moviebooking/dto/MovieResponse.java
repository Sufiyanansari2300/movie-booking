package com.sufiyan.moviebooking.dto;

import com.sufiyan.moviebooking.entity.Movie;

import java.time.LocalDate;

public record MovieResponse(Long id, String title, String description, String language, String genre,
                            int durationMinutes, String certificate, LocalDate releaseDate) {

    public static MovieResponse from(Movie m) {
        return new MovieResponse(m.getId(), m.getTitle(), m.getDescription(), m.getLanguage(), m.getGenre(),
                m.getDurationMinutes(), m.getCertificate(), m.getReleaseDate());
    }
}
