package com.sufiyan.moviebooking.dto;

import com.sufiyan.moviebooking.entity.PricingRule;
import com.sufiyan.moviebooking.entity.PricingRuleType;

import java.math.BigDecimal;
import java.time.LocalTime;

public record PricingRuleResponse(Long id, String name, PricingRuleType ruleType, BigDecimal adjustmentPercent,
                                  LocalTime windowStart, LocalTime windowEnd, boolean active) {

    public static PricingRuleResponse from(PricingRule r) {
        return new PricingRuleResponse(r.getId(), r.getName(), r.getRuleType(), r.getAdjustmentPercent(),
                r.getWindowStart(), r.getWindowEnd(), r.isActive());
    }
}
