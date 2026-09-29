package com.sufiyan.moviebooking.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Simulated payment provider. The payment token decides the outcome so every path can be demoed and tested:
 * {@code tok_declined} / {@code tok_insufficient_funds} are declined, {@code tok_error} is a provider failure,
 * anything else (e.g. {@code tok_success}) succeeds. Refunds always succeed.
 */
@Slf4j
@Component
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public ChargeResult charge(ChargeRequest request) {
        String token = request.paymentToken() == null ? "tok_success" : request.paymentToken();
        ChargeResult result = switch (token) {
            case "tok_declined" -> ChargeResult.declined("CARD_DECLINED", "The payment was declined");
            case "tok_insufficient_funds" -> ChargeResult.declined("INSUFFICIENT_FUNDS", "Insufficient funds");
            case "tok_error" -> ChargeResult.error("Payment provider unavailable, please retry");
            default -> ChargeResult.succeeded("mock_" + UUID.randomUUID());
        };
        log.info("Mock charge of {} {} for booking {} via {}: {}", request.amount(), request.currency(),
                request.bookingId(), request.method(), result.outcome());
        return result;
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        log.info("Mock refund of {} {} for booking {} (charge {})", request.amount(), request.currency(),
                request.bookingId(), request.chargeReference());
        return new RefundResult(true, "mock_refund_" + UUID.randomUUID(), null);
    }
}
