package com.sufiyan.moviebooking.controller;

import com.sufiyan.moviebooking.dto.MovieResponse;
import com.sufiyan.moviebooking.dto.PageResponse;
import com.sufiyan.moviebooking.service.MovieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public movie browsing. */
@Tag(name = "Browse - Movies", description = "Public, no token needed")
@RestController
@RequestMapping("/api/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieService movieService;

    /** e.g. GET /api/movies?title=incep&language=English&genre=Sci-Fi&page=0&size=20&sort=releaseDate,desc */
    @Operation(summary = "Search movies by title, language and genre (paginated, sortable by title, releaseDate, durationMinutes, language, genre)")
    @GetMapping
    public PageResponse<MovieResponse> search(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String genre,
            @ParameterObject @PageableDefault(size = 20, sort = "title", direction = Sort.Direction.ASC) Pageable pageable) {
        return movieService.search(title, language, genre, pageable);
    }

    @Operation(summary = "Get a movie")
    @GetMapping("/{id}")
    public MovieResponse get(@PathVariable Long id) {
        return movieService.get(id);
    }
}
