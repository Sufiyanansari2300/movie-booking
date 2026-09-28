package com.sufiyan.moviebooking.controller;

import com.sufiyan.moviebooking.dto.PageResponse;
import com.sufiyan.moviebooking.dto.SeatMapResponse;
import com.sufiyan.moviebooking.dto.ShowResponse;
import com.sufiyan.moviebooking.service.ShowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Browse - Shows", description = "Public, no token needed")
@RestController
@RequestMapping("/api/shows")
@RequiredArgsConstructor
public class ShowController {

    private final ShowService showService;

    @Operation(summary = "Find upcoming shows by city, theater, movie and date (paginated, sorted by startTime)")
    @GetMapping
    public PageResponse<ShowResponse> search(
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) Long theaterId,
            @RequestParam(required = false) Long movieId,
            @Parameter(description = "Calendar day in the business time zone, e.g. 2026-10-02")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @ParameterObject @PageableDefault(size = 20, sort = "startTime", direction = Sort.Direction.ASC) Pageable pageable) {
        return showService.search(cityId, theaterId, movieId, date, pageable);
    }

    @Operation(summary = "Get a show with prices and available seat count")
    @GetMapping("/{id}")
    public ShowResponse get(@PathVariable Long id) {
        return showService.get(id);
    }

    @Operation(summary = "Seat map of a show: every seat with its status and price")
    @GetMapping("/{id}/seats")
    public SeatMapResponse seatMap(@PathVariable Long id) {
        return showService.seatMap(id);
    }
}
