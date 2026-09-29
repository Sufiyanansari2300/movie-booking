package com.sufiyan.moviebooking.controller;

import com.sufiyan.moviebooking.config.OpenApiConfig;
import com.sufiyan.moviebooking.dto.BookingSummary;
import com.sufiyan.moviebooking.dto.PageResponse;
import com.sufiyan.moviebooking.dto.ShowSalesSummary;
import com.sufiyan.moviebooking.entity.BookingStatus;
import com.sufiyan.moviebooking.repository.BookingSpecifications.When;
import com.sufiyan.moviebooking.security.AppUserPrincipal;
import com.sufiyan.moviebooking.service.BookingHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Booking History", description = "Your bookings, and admin booking search / show sales")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequiredArgsConstructor
public class BookingHistoryController {

    private final BookingHistoryService historyService;

    @Operation(summary = "Your booking history, newest first (filter by status and upcoming/past; sortable by "
            + "createdAt, confirmedAt, show.startTime, totalAmount)")
    @GetMapping("/api/bookings/me")
    public PageResponse<BookingSummary> mine(
            @AuthenticationPrincipal AppUserPrincipal caller,
            @Parameter(description = "Effective status: an overdue hold counts as EXPIRED")
            @RequestParam(required = false) BookingStatus status,
            @Parameter(description = "UPCOMING = show not started yet, PAST = started")
            @RequestParam(required = false) When when,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return historyService.mine(caller.id(), status, when, pageable);
    }

    @Operation(summary = "Search all bookings (ADMIN) by status, upcoming/past, show, customer id or email")
    @GetMapping("/api/admin/bookings")
    public PageResponse<BookingSummary> search(
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) When when,
            @RequestParam(required = false) Long showId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String email,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return historyService.search(new BookingHistoryService.Filter(status, when, showId, userId, email), pageable);
    }

    @Operation(summary = "Occupancy and revenue of a show (ADMIN): booked/held/free seats, paid, refunded, net")
    @GetMapping("/api/admin/shows/{id}/sales")
    public ShowSalesSummary sales(@PathVariable Long id) {
        return historyService.showSales(id);
    }
}
