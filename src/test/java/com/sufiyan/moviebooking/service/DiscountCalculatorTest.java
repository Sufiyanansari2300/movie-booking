package com.sufiyan.moviebooking.service;

import com.sufiyan.moviebooking.entity.DiscountCode;
import com.sufiyan.moviebooking.entity.DiscountType;
import com.sufiyan.moviebooking.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscountCalculatorTest {

    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");
    private static final BigDecimal SUBTOTAL = new BigDecimal("600.00");

    @Test
    void percent_isAppliedToSubtotal() {
        DiscountCode code = new DiscountCode("TEN", DiscountType.PERCENT, new BigDecimal("10"));

        assertThat(DiscountCalculator.discountFor(code, SUBTOTAL)).isEqualByComparingTo("60.00");
    }

    @Test
    void percent_isCappedByMaxDiscount() {
        DiscountCode code = new DiscountCode("TWENTY", DiscountType.PERCENT, new BigDecimal("20"));
        code.setMaxDiscountAmount(new BigDecimal("100"));

        assertThat(DiscountCalculator.discountFor(code, SUBTOTAL)).isEqualByComparingTo("100.00");
    }

    @Test
    void percent_roundsHalfUp() {
        DiscountCode code = new DiscountCode("SEVEN", DiscountType.PERCENT, new BigDecimal("7.5"));

        // 7.5% of 199.99 = 14.99925
        assertThat(DiscountCalculator.discountFor(code, new BigDecimal("199.99"))).isEqualByComparingTo("15.00");
    }

    @Test
    void flat_neverExceedsSubtotal() {
        DiscountCode code = new DiscountCode("FLAT500", DiscountType.FLAT, new BigDecimal("500"));

        assertThat(DiscountCalculator.discountFor(code, SUBTOTAL)).isEqualByComparingTo("500.00");
        assertThat(DiscountCalculator.discountFor(code, new BigDecimal("300.00"))).isEqualByComparingTo("300.00");
    }

    @Test
    void validCode_passes() {
        DiscountCode code = fullyConfigured();

        assertThatCode(() -> DiscountCalculator.validate(code, SUBTOTAL, NOW, 0)).doesNotThrowAnyException();
    }

    @Test
    void eachRule_hasItsOwnErrorCode() {
        DiscountCode inactive = fullyConfigured();
        inactive.setActive(false);
        expectError(inactive, SUBTOTAL, 0, "DISCOUNT_INACTIVE");

        DiscountCode early = fullyConfigured();
        early.setValidFrom(NOW.plusSeconds(60));
        expectError(early, SUBTOTAL, 0, "DISCOUNT_NOT_YET_VALID");

        DiscountCode expired = fullyConfigured();
        expired.setValidUntil(NOW); // valid until is exclusive
        expectError(expired, SUBTOTAL, 0, "DISCOUNT_EXPIRED");

        expectError(fullyConfigured(), new BigDecimal("299.99"), 0, "DISCOUNT_MIN_ORDER_NOT_MET");

        DiscountCode exhausted = fullyConfigured();
        exhausted.setUsedCount(100);
        expectError(exhausted, SUBTOTAL, 0, "DISCOUNT_EXHAUSTED");

        expectError(fullyConfigured(), SUBTOTAL, 1, "DISCOUNT_USER_LIMIT_REACHED");
    }

    private static DiscountCode fullyConfigured() {
        DiscountCode code = new DiscountCode("WELCOME", DiscountType.PERCENT, new BigDecimal("10"));
        code.setValidFrom(NOW.minusSeconds(3600));
        code.setValidUntil(NOW.plusSeconds(3600));
        code.setMinOrderAmount(new BigDecimal("300"));
        code.setUsageLimit(100);
        code.setPerUserLimit(1);
        return code;
    }

    private static void expectError(DiscountCode code, BigDecimal subtotal, long userUses, String errorCode) {
        assertThatThrownBy(() -> DiscountCalculator.validate(code, subtotal, NOW, userUses))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode").isEqualTo(errorCode);
    }
}
