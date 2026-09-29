package com.sufiyan.moviebooking.service;

import com.sufiyan.moviebooking.entity.PricingRule;
import com.sufiyan.moviebooking.entity.SeatType;
import com.sufiyan.moviebooking.repository.PricingRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a show's base prices (per seat type) into the prices customers pay, by applying the active pricing rules.
 * Applicable rules are summed (weekend +20% and prime time +10% = +30%); each seat price is rounded half-up to
 * 2 decimals and never goes below zero.
 */
@Service
@RequiredArgsConstructor
public class PricingService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final PricingRuleRepository pricingRuleRepository;
    private final ZoneId businessZone;

    public record Quote(Map<SeatType, BigDecimal> effectivePrices, List<String> appliedRules, BigDecimal adjustmentPercent) {
    }

    @Transactional(readOnly = true)
    public List<PricingRule> activeRules() {
        return pricingRuleRepository.findByActiveTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Quote quote(Instant showStart, Map<SeatType, BigDecimal> basePrices) {
        return quote(showStart, basePrices, activeRules());
    }

    /** Pure calculation; {@code rules} are the rules to consider (normally the active ones). */
    public Quote quote(Instant showStart, Map<SeatType, BigDecimal> basePrices, List<PricingRule> rules) {
        ZonedDateTime local = showStart.atZone(businessZone);
        List<PricingRule> applied = rules.stream().filter(r -> r.isActive() && applies(r, local)).toList();
        BigDecimal percent = applied.stream().map(PricingRule::getAdjustmentPercent).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<SeatType, BigDecimal> effective = new EnumMap<>(SeatType.class);
        basePrices.forEach((type, base) -> effective.put(type, adjust(base, percent)));
        return new Quote(effective, applied.stream().map(PricingRule::getName).toList(), percent);
    }

    static boolean applies(PricingRule rule, ZonedDateTime showStartLocal) {
        return switch (rule.getRuleType()) {
            case WEEKEND -> {
                DayOfWeek day = showStartLocal.getDayOfWeek();
                yield day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
            }
            case PRIME_TIME -> {
                LocalTime t = showStartLocal.toLocalTime();
                yield !t.isBefore(rule.getWindowStart()) && t.isBefore(rule.getWindowEnd());
            }
        };
    }

    static BigDecimal adjust(BigDecimal base, BigDecimal percent) {
        BigDecimal price = base.multiply(HUNDRED.add(percent)).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        return price.max(BigDecimal.ZERO.setScale(2));
    }
}
