package com.sufiyan.moviebooking.controller;

import com.sufiyan.moviebooking.config.OpenApiConfig;
import com.sufiyan.moviebooking.dto.BookingResponse;
import com.sufiyan.moviebooking.dto.CancelShowRequest;
import com.sufiyan.moviebooking.dto.RefundQuoteResponse;
import com.sufiyan.moviebooking.dto.ShowCancellationResponse;
import com.sufiyan.moviebooking.security.AppUserPrincipal;
import com.sufiyan.moviebooking.service.CancellationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Cancellations & Refunds", description = "Cancel bookings (customer) or whole shows (ADMIN) with refunds")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequiredArgsConstructor
public class CancellationController {

    private final CancellationService cancellationService;

    @Operation(summary = "What cancelling this booking right now would refund (per its refund policy)")
    @GetMapping("/api/bookings/{id}/refund-quote")
    public RefundQuoteResponse quote(@AuthenticationPrincipal AppUserPrincipal caller, @PathVariable Long id) {
        return cancellationService.quote(caller, id);
    }

    @Operation(summary = "Cancel a paid booking before the show starts; refunds per the policy in force when it was paid")
    @PostMapping("/api/bookings/{id}/cancel")
    public BookingResponse cancel(@AuthenticationPrincipal AppUserPrincipal caller, @PathVariable Long id) {
        return cancellationService.cancel(caller, id);
    }

    @Operation(summary = "Cancel a show (ADMIN): full refund for every paid booking, open holds released; re-run to resume")
    @PostMapping("/api/admin/shows/{id}/cancel")
    public ShowCancellationResponse cancelShow(@PathVariable Long id, @Valid @RequestBody CancelShowRequest request) {
        return cancellationService.cancelShow(id, request.reason().trim());
    }
}
