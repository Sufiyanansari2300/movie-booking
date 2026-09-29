package com.sufiyan.moviebooking.service;

import com.sufiyan.moviebooking.entity.DiscountCode;
import com.sufiyan.moviebooking.exception.BadRequestException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/** Validates a discount code against an order and computes the discount. Pure logic. */
public final class DiscountCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private DiscountCalculator() {
    }

    /**
     * @param userRedemptions how many confirmed bookings this customer already used the code on
     * @throws BadRequestException with a specific error code when the code cannot be used
     */
    public static void validate(DiscountCode code, BigDecimal subtotal, Instant now, long userRedemptions) {
        if (!code.isActive()) {
            throw invalid("DISCOUNT_INACTIVE", "This discount code is no longer active");
        }
        if (code.getValidFrom() != null && now.isBefore(code.getValidFrom())) {
            throw invalid("DISCOUNT_NOT_YET_VALID", "This discount code is not valid yet");
        }
        if (code.getValidUntil() != null && !now.isBefore(code.getValidUntil())) {
            throw invalid("DISCOUNT_EXPIRED", "This discount code has expired");
        }
        if (code.getMinOrderAmount() != null && subtotal.compareTo(code.getMinOrderAmount()) < 0) {
            throw invalid("DISCOUNT_MIN_ORDER_NOT_MET",
                    "This code needs an order of at least " + code.getMinOrderAmount().toPlainString());
        }
        if (code.getUsageLimit() != null && code.getUsedCount() >= code.getUsageLimit()) {
            throw invalid("DISCOUNT_EXHAUSTED", "This discount code has been fully used");
        }
        if (code.getPerUserLimit() != null && userRedemptions >= code.getPerUserLimit()) {
            throw invalid("DISCOUNT_USER_LIMIT_REACHED", "You have already used this discount code");
        }
    }

    /** Discount amount for the subtotal: PERCENT (capped by maxDiscountAmount) or FLAT, never above the subtotal. */
    public static BigDecimal discountFor(DiscountCode code, BigDecimal subtotal) {
        BigDecimal discount = switch (code.getDiscountType()) {
            case PERCENT -> {
                BigDecimal pct = subtotal.multiply(code.getDiscountValue()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
                yield code.getMaxDiscountAmount() == null ? pct : pct.min(code.getMaxDiscountAmount());
            }
            case FLAT -> code.getDiscountValue();
        };
        return discount.min(subtotal).setScale(2, RoundingMode.HALF_UP);
    }

    private static BadRequestException invalid(String code, String message) {
        return new BadRequestException(code, message);
    }
}
