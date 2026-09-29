package com.sufiyan.moviebooking.service;

import com.sufiyan.moviebooking.entity.PricingRule;
import com.sufiyan.moviebooking.entity.PricingRuleType;
import com.sufiyan.moviebooking.entity.SeatType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PricingServiceTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final Map<SeatType, BigDecimal> BASE = Map.of(
            SeatType.REGULAR, new BigDecimal("200.00"), SeatType.PREMIUM, new BigDecimal("350.00"));

    private final PricingService service = new PricingService(null, IST);
    private final PricingRule weekend = rule("Weekend", PricingRuleType.WEEKEND, "20", null, null, true);
    private final PricingRule primeTime = rule("Prime time", PricingRuleType.PRIME_TIME, "10",
            LocalTime.of(18, 0), LocalTime.of(22, 0), true);

    // 2026-10-02 is a Friday, 2026-10-03 a Saturday
    private static Instant ist(String localDateTime) {
        return ZonedDateTime.of(java.time.LocalDateTime.parse(localDateTime), IST).toInstant();
    }

    @Test
    void weekdayMatinee_paysBasePrice() {
        var quote = service.quote(ist("2026-10-02T13:00"), BASE, List.of(weekend, primeTime));

        assertThat(quote.effectivePrices()).containsEntry(SeatType.REGULAR, new BigDecimal("200.00"))
                .containsEntry(SeatType.PREMIUM, new BigDecimal("350.00"));
        assertThat(quote.appliedRules()).isEmpty();
    }

    @Test
    void weekendSurcharge() {
        var quote = service.quote(ist("2026-10-03T13:00"), BASE, List.of(weekend, primeTime));

        assertThat(quote.effectivePrices()).containsEntry(SeatType.REGULAR, new BigDecimal("240.00"))
                .containsEntry(SeatType.PREMIUM, new BigDecimal("420.00"));
        assertThat(quote.appliedRules()).containsExactly("Weekend");
    }

    @Test
    void weekendPrimeTime_rulesAreSummed() {
        var quote = service.quote(ist("2026-10-03T19:00"), BASE, List.of(weekend, primeTime));

        assertThat(quote.adjustmentPercent()).isEqualByComparingTo("30");
        assertThat(quote.effectivePrices()).containsEntry(SeatType.REGULAR, new BigDecimal("260.00"))
                .containsEntry(SeatType.PREMIUM, new BigDecimal("455.00"));
        assertThat(quote.appliedRules()).containsExactly("Weekend", "Prime time");
    }

    @Test
    void weekendIsDecidedInTheBusinessTimeZone() {
        // Friday 20:00 UTC is already Saturday 01:30 in India
        var quote = service.quote(Instant.parse("2026-10-02T20:00:00Z"), BASE, List.of(weekend));

        assertThat(quote.appliedRules()).containsExactly("Weekend");
    }

    @ParameterizedTest
    @CsvSource({"17:59, false", "18:00, true", "21:59, true", "22:00, false"})
    void primeTimeWindow_isStartInclusiveEndExclusive(String time, boolean applies) {
        var quote = service.quote(ist("2026-10-02T" + time), BASE, List.of(primeTime));

        assertThat(quote.appliedRules().contains("Prime time")).isEqualTo(applies);
    }

    @Test
    void inactiveRulesAreIgnored() {
        PricingRule off = rule("Off", PricingRuleType.WEEKEND, "50", null, null, false);

        assertThat(service.quote(ist("2026-10-03T13:00"), BASE, List.of(off)).appliedRules()).isEmpty();
    }

    @Test
    void negativeAdjustments_andRoundingHalfUp_neverBelowZero() {
        assertThat(PricingService.adjust(new BigDecimal("199.99"), new BigDecimal("12.5"))).isEqualByComparingTo("224.99");
        assertThat(PricingService.adjust(new BigDecimal("100.05"), new BigDecimal("10"))).isEqualByComparingTo("110.06");
        assertThat(PricingService.adjust(new BigDecimal("200.00"), new BigDecimal("-10"))).isEqualByComparingTo("180.00");
        assertThat(PricingService.adjust(new BigDecimal("200.00"), new BigDecimal("-150"))).isEqualByComparingTo("0.00");
    }

    private static PricingRule rule(String name, PricingRuleType type, String pct, LocalTime from, LocalTime to,
                                    boolean active) {
        return new PricingRule(name, type, new BigDecimal(pct), from, to, active);
    }
}
