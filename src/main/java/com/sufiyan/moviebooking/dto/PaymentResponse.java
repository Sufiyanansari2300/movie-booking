package com.sufiyan.moviebooking.dto;

import com.sufiyan.moviebooking.entity.PaymentMethod;
import com.sufiyan.moviebooking.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentResponse(
        Long paymentId,
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        PaymentMethod method,
        String gatewayReference,
        String failureCode,
        String failureReason,
        OffsetDateTime createdAt,
        BookingResponse booking) {
}
