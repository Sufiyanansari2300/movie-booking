package com.sufiyan.moviebooking.payment;

import com.sufiyan.moviebooking.entity.PaymentMethod;

import java.math.BigDecimal;

/**
 * Port to a payment provider. The app only ships {@link MockPaymentGateway}; a real provider would be another
 * implementation of this interface.
 */
public interface PaymentGateway {

    ChargeResult charge(ChargeRequest request);

    /** Refunds (part of) a previous successful charge. */
    RefundResult refund(RefundRequest request);

    /**
     * @param idempotencyKey forwarded so a real provider can also de-duplicate retries
     * @param paymentToken   opaque token for the payment instrument (never raw card data)
     */
    record ChargeRequest(Long bookingId, BigDecimal amount, String currency, PaymentMethod method,
                         String paymentToken, String idempotencyKey) {
    }

    record ChargeResult(Outcome outcome, String gatewayReference, String failureCode, String failureReason) {

        public static ChargeResult succeeded(String reference) {
            return new ChargeResult(Outcome.SUCCEEDED, reference, null, null);
        }

        public static ChargeResult declined(String code, String reason) {
            return new ChargeResult(Outcome.DECLINED, null, code, reason);
        }

        public static ChargeResult error(String reason) {
            return new ChargeResult(Outcome.ERROR, null, "GATEWAY_ERROR", reason);
        }
    }

    record RefundRequest(Long bookingId, String chargeReference, BigDecimal amount, String currency,
                         String idempotencyKey) {
    }

    record RefundResult(boolean succeeded, String refundReference, String failureReason) {
    }

    enum Outcome {
        SUCCEEDED,
        /** The provider refused the payment (e.g. insufficient funds); the customer may retry. */
        DECLINED,
        /** The provider could not be reached or failed; the customer may retry. */
        ERROR
    }
}
