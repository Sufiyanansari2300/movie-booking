package com.sufiyan.moviebooking.controller;

import com.sufiyan.moviebooking.config.OpenApiConfig;
import com.sufiyan.moviebooking.dto.CreateShowRequest;
import com.sufiyan.moviebooking.dto.ShowResponse;
import com.sufiyan.moviebooking.dto.UpdateShowPricesRequest;
import com.sufiyan.moviebooking.service.ShowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Shows", description = "Schedule shows and set prices (ADMIN)")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/admin/shows")
@RequiredArgsConstructor
public class AdminShowController {

    private final ShowService showService;

    @Operation(summary = "Schedule a show (rejects overlaps on the screen; creates a seat map for the show)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShowResponse create(@Valid @RequestBody CreateShowRequest request) {
        return showService.create(request);
    }

    @Operation(summary = "Change a show's base prices per seat type (affects new bookings only)")
    @PutMapping("/{id}/prices")
    public ShowResponse updatePrices(@PathVariable Long id, @Valid @RequestBody UpdateShowPricesRequest request) {
        return showService.updatePrices(id, request);
    }

    @Operation(summary = "Delete a show that has no held or booked seats")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        showService.delete(id);
    }
}
