package com.sufiyan.moviebooking.service;

import com.sufiyan.moviebooking.service.RefundCalculator.Band;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RefundCalculatorTest {

    // Deliberately unsorted: order must not matter
    private static final List<Band> STANDARD = List.of(
            new Band(24, new BigDecimal("50")), new Band(48, new BigDecimal("100")), new Band(2, new BigDecimal("25")));

    @ParameterizedTest(name = "{0} minutes before -> {1}%")
    @CsvSource({
            "4320, 100",   // 3 days
            "2880, 100",   // exactly 48h: band threshold is inclusive
            "2879, 50",    // 47h59m
            "1440, 50",    // exactly 24h
            "1439, 25",
            "120, 25",     // exactly 2h
            "119, 0",      // below every band
            "0, 0"})
    void bandWithTheLargestThresholdMet_wins(long minutesBefore, String expectedPercent) {
        assertThat(RefundCalculator.percentFor(STANDARD, Duration.ofMinutes(minutesBefore)))
                .isEqualByComparingTo(expectedPercent);
    }

    @Test
    void noPolicy_meansNoRefund() {
        assertThat(RefundCalculator.percentFor(List.of(), Duration.ofDays(30))).isEqualByComparingTo("0");
    }

    @Test
    void amount_isAPercentOfWhatWasPaid_roundedHalfUp() {
        assertThat(RefundCalculator.amount(new BigDecimal("435.60"), new BigDecimal("50"))).isEqualByComparingTo("217.80");
        assertThat(RefundCalculator.amount(new BigDecimal("199.99"), new BigDecimal("25"))).isEqualByComparingTo("50.00");
        assertThat(RefundCalculator.amount(new BigDecimal("0.00"), new BigDecimal("100"))).isEqualByComparingTo("0.00");
    }
}
