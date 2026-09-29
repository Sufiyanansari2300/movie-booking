package com.sufiyan.moviebooking.payment;

import com.sufiyan.moviebooking.entity.PaymentMethod;
import com.sufiyan.moviebooking.payment.PaymentGateway.ChargeRequest;
import com.sufiyan.moviebooking.payment.PaymentGateway.Outcome;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MockPaymentGatewayTest {

    private final MockPaymentGateway gateway = new MockPaymentGateway();

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "tok_success, SUCCEEDED, null",
            "null, SUCCEEDED, null",
            "tok_declined, DECLINED, CARD_DECLINED",
            "tok_insufficient_funds, DECLINED, INSUFFICIENT_FUNDS",
            "tok_error, ERROR, GATEWAY_ERROR"})
    void tokenDecidesTheOutcome(String token, Outcome outcome, String failureCode) {
        var result = gateway.charge(new ChargeRequest(1L, new BigDecimal("100.00"), "INR", PaymentMethod.CARD,
                token, "key-12345678"));

        assertThat(result.outcome()).isEqualTo(outcome);
        assertThat(result.failureCode()).isEqualTo(failureCode);
        assertThat(result.gatewayReference() != null).isEqualTo(outcome == Outcome.SUCCEEDED);
    }
}
