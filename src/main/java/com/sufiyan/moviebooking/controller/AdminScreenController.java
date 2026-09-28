package com.sufiyan.moviebooking.controller;

import com.sufiyan.moviebooking.config.OpenApiConfig;
import com.sufiyan.moviebooking.dto.ScreenRequest;
import com.sufiyan.moviebooking.dto.ScreenResponse;
import com.sufiyan.moviebooking.dto.SeatLayoutRequest;
import com.sufiyan.moviebooking.dto.SeatLayoutResponse;
import com.sufiyan.moviebooking.service.ScreenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Screens & Seat Layouts", description = "Manage screens and their seat layouts (ADMIN)")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminScreenController {

    private final ScreenService screenService;

    @Operation(summary = "Add a screen to a theater")
    @PostMapping("/theaters/{theaterId}/screens")
    @ResponseStatus(HttpStatus.CREATED)
    public ScreenResponse create(@PathVariable Long theaterId, @Valid @RequestBody ScreenRequest request) {
        return screenService.create(theaterId, request);
    }

    @Operation(summary = "Rename a screen")
    @PutMapping("/screens/{id}")
    public ScreenResponse rename(@PathVariable Long id, @Valid @RequestBody ScreenRequest request) {
        return screenService.rename(id, request);
    }

    @Operation(summary = "Delete a screen and its seats (blocked once it has shows)")
    @DeleteMapping("/screens/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        screenService.delete(id);
    }

    @Operation(summary = "Replace the whole seat layout from row sections, e.g. rows A-H x 12 REGULAR (blocked once it has shows)")
    @PutMapping("/screens/{id}/layout")
    public SeatLayoutResponse replaceLayout(@PathVariable Long id, @Valid @RequestBody SeatLayoutRequest request) {
        return screenService.replaceLayout(id, request);
    }

    @Operation(summary = "Get the seat layout row by row")
    @GetMapping("/screens/{id}/layout")
    public SeatLayoutResponse getLayout(@PathVariable Long id) {
        return screenService.getLayout(id);
    }
}
