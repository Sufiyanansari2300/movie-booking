package com.sufiyan.moviebooking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** "Cancelling at least {@code minHoursBeforeShow} hours before the show refunds {@code refundPercent}%." */
@Getter
@Entity
@Table(name = "refund_policy_rules")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundPolicyRule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private RefundPolicy policy;

    @Column(name = "min_hours_before_show", nullable = false)
    private int minHoursBeforeShow;

    @Column(name = "refund_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal refundPercent;

    public RefundPolicyRule(RefundPolicy policy, int minHoursBeforeShow, BigDecimal refundPercent) {
        this.policy = policy;
        this.minHoursBeforeShow = minHoursBeforeShow;
        this.refundPercent = refundPercent;
    }
}
