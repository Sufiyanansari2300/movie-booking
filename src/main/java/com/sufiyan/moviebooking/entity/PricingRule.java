package com.sufiyan.moviebooking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalTime;

@Getter
@Setter
@Entity
@Table(name = "pricing_rules")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PricingRule extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 20)
    private PricingRuleType ruleType;

    /** e.g. 20.00 = +20%, -10.00 = -10%. */
    @Column(name = "adjustment_percent", nullable = false, precision = 6, scale = 2)
    private BigDecimal adjustmentPercent;

    @Column(name = "window_start")
    private LocalTime windowStart;

    @Column(name = "window_end")
    private LocalTime windowEnd;

    @Column(nullable = false)
    private boolean active;

    public PricingRule(String name, PricingRuleType ruleType, BigDecimal adjustmentPercent,
                       LocalTime windowStart, LocalTime windowEnd, boolean active) {
        this.name = name;
        this.ruleType = ruleType;
        this.adjustmentPercent = adjustmentPercent;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.active = active;
    }
}
