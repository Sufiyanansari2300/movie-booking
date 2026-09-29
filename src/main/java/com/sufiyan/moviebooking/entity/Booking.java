package com.sufiyan.moviebooking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Entity
@Table(name = "bookings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(name = "hold_expires_at", nullable = false)
    private Instant holdExpiresAt;

    /** Sum of seat prices after pricing rules. */
    @Column(name = "subtotal_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotalAmount;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount;

    /** subtotal - discount; what the customer pays. */
    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discount_code_id")
    private DiscountCode discountCode;

    /** Names of the pricing rules applied when the seats were priced, comma separated. */
    @Column(name = "applied_pricing_rules")
    private String appliedPricingRules;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public Booking(User user, Show show, Instant holdExpiresAt, BigDecimal subtotalAmount, String appliedPricingRules) {
        this.user = user;
        this.show = show;
        this.status = BookingStatus.HELD;
        this.holdExpiresAt = holdExpiresAt;
        this.subtotalAmount = subtotalAmount;
        this.discountAmount = BigDecimal.ZERO;
        this.totalAmount = subtotalAmount;
        this.appliedPricingRules = appliedPricingRules;
    }

    public void applyDiscount(DiscountCode code, BigDecimal amount) {
        this.discountCode = code;
        this.discountAmount = amount;
        this.totalAmount = subtotalAmount.subtract(amount);
    }

    public void confirm(Instant now) {
        this.status = BookingStatus.CONFIRMED;
        this.confirmedAt = now;
    }

    public void removeDiscount() {
        applyDiscount(null, BigDecimal.ZERO);
    }

    public boolean isHoldExpired(Instant now) {
        return status == BookingStatus.HELD && !holdExpiresAt.isAfter(now);
    }
}
