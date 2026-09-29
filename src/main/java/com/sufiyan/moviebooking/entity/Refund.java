package com.sufiyan.moviebooking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Entity
@Table(name = "refunds")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "refund_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal refundPercent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RefundReason reason;

    /** Snapshot of the policy name used (null for show cancellations and bookings without a policy). */
    @Column(name = "policy_name", length = 100)
    private String policyName;

    @Column(name = "gateway_reference", length = 100)
    private String gatewayReference;

    public Refund(Booking booking, Payment payment, BigDecimal amount, BigDecimal refundPercent, RefundReason reason,
                  String policyName, String gatewayReference) {
        this.booking = booking;
        this.payment = payment;
        this.amount = amount;
        this.refundPercent = refundPercent;
        this.reason = reason;
        this.policyName = policyName;
        this.gatewayReference = gatewayReference;
    }
}
