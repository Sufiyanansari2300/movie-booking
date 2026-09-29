package com.sufiyan.moviebooking.controller;

import com.sufiyan.moviebooking.config.OpenApiConfig;
import com.sufiyan.moviebooking.dto.ApplyDiscountRequest;
import com.sufiyan.moviebooking.dto.BookingResponse;
import com.sufiyan.moviebooking.dto.HoldSeatsRequest;
import com.sufiyan.moviebooking.security.AppUserPrincipal;
import com.sufiyan.moviebooking.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Bookings", description = "Hold seats and manage your bookings (logged-in customer)")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @Operation(summary = "Hold seats of a show for 10 minutes (all or nothing); pay before the hold expires")
    @PostMapping("/shows/{showId}/holds")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse hold(@AuthenticationPrincipal AppUserPrincipal caller, @PathVariable Long showId,
                                @Valid @RequestBody HoldSeatsRequest request) {
        return bookingService.hold(caller.id(), showId, request.showSeatIds());
    }

    @Operation(summary = "Release a hold before paying; the seats become available again")
    @DeleteMapping("/bookings/{id}/hold")
    public BookingResponse release(@AuthenticationPrincipal AppUserPrincipal caller, @PathVariable Long id) {
        return bookingService.release(caller, id);
    }

    @Operation(summary = "Apply a discount code to a held booking (replaces any previous code)")
    @PostMapping("/bookings/{id}/discount")
    public BookingResponse applyDiscount(@AuthenticationPrincipal AppUserPrincipal caller, @PathVariable Long id,
                                         @Valid @RequestBody ApplyDiscountRequest request) {
        return bookingService.applyDiscount(caller, id, request.code());
    }

    @Operation(summary = "Remove the discount code from a held booking")
    @DeleteMapping("/bookings/{id}/discount")
    public BookingResponse removeDiscount(@AuthenticationPrincipal AppUserPrincipal caller, @PathVariable Long id) {
        return bookingService.removeDiscount(caller, id);
    }

    @Operation(summary = "Get one of your bookings (admins can see any)")
    @GetMapping("/bookings/{id}")
    public BookingResponse get(@AuthenticationPrincipal AppUserPrincipal caller, @PathVariable Long id) {
        return bookingService.getForCaller(caller, id);
    }
}
