package com.sufiyan.moviebooking.dto;

import com.sufiyan.moviebooking.entity.DiscountType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record DiscountCodeResponse(Long id, String code, String description, DiscountType discountType,
                                   BigDecimal discountValue, BigDecimal maxDiscountAmount, BigDecimal minOrderAmount,
                                   OffsetDateTime validFrom, OffsetDateTime validUntil, Integer usageLimit,
                                   Integer perUserLimit, int usedCount, boolean active) {
}
