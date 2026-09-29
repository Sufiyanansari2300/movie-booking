package com.sufiyan.moviebooking.controller;

import com.sufiyan.moviebooking.config.OpenApiConfig;
import com.sufiyan.moviebooking.dto.PayRequest;
import com.sufiyan.moviebooking.dto.PaymentResponse;
import com.sufiyan.moviebooking.security.AppUserPrincipal;
import com.sufiyan.moviebooking.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Payments", description = "Pay for a held booking (mock gateway) and see payment attempts")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/bookings/{id}")
@RequiredArgsConstructor
public class PaymentController {

    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private final PaymentService paymentService;

    @Operation(summary = "Pay for a held booking and confirm it. Retrying with the same Idempotency-Key never charges twice")
    @ApiResponse(responseCode = "200", description = "Paid; booking CONFIRMED")
    @ApiResponse(responseCode = "402", description = "Payment declined; booking stays HELD, retry with a new key",
            content = @Content(schema = @Schema(implementation = PaymentResponse.class)))
    @ApiResponse(responseCode = "502", description = "Payment provider error; booking stays HELD, retry with a new key",
            content = @Content(schema = @Schema(implementation = PaymentResponse.class)))
    @PostMapping("/pay")
    public ResponseEntity<PaymentResponse> pay(
            @AuthenticationPrincipal AppUserPrincipal caller,
            @PathVariable Long id,
            @Parameter(description = "Unique per payment attempt, e.g. a UUID; 8-100 chars", required = true)
            @RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
            @Valid @RequestBody PayRequest request) {
        PaymentService.PaymentOutcome outcome = paymentService.pay(caller, id, idempotencyKey, request);
        HttpStatus status = switch (outcome.payment().status()) {
            case SUCCEEDED -> HttpStatus.OK;
            case FAILED -> "GATEWAY_ERROR".equals(outcome.payment().failureCode())
                    ? HttpStatus.BAD_GATEWAY : HttpStatus.PAYMENT_REQUIRED;
        };
        return ResponseEntity.status(status)
                .header(REPLAYED_HEADER, String.valueOf(outcome.replayed()))
                .body(outcome.payment());
    }

    @Operation(summary = "List payment attempts of one of your bookings")
    @GetMapping("/payments")
    public List<PaymentResponse> payments(@AuthenticationPrincipal AppUserPrincipal caller, @PathVariable Long id) {
        return paymentService.history(caller, id);
    }
}
