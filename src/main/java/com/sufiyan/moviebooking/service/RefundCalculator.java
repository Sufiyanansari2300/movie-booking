package com.sufiyan.moviebooking.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;

/** Refund percent from a policy's time bands, and the refund amount. Pure logic. */
public final class RefundCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private RefundCalculator() {
    }

    /** "Cancelling at least {@code minHoursBeforeShow} hours before the show refunds {@code percent}%." */
    public record Band(int minHoursBeforeShow, BigDecimal percent) {
    }

    /**
     * The band with the largest threshold that {@code timeBeforeShow} still meets wins, so cancelling exactly
     * 48h before a "48h -> 100%" band gets 100%. Below every band (or with no policy) the refund is 0%.
     */
    public static BigDecimal percentFor(List<Band> bands, Duration timeBeforeShow) {
        return bands.stream()
                .sorted(Comparator.comparingInt(Band::minHoursBeforeShow).reversed())
                .filter(b -> timeBeforeShow.compareTo(Duration.ofHours(b.minHoursBeforeShow())) >= 0)
                .map(Band::percent)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    public static BigDecimal amount(BigDecimal paid, BigDecimal percent) {
        return paid.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }
}
