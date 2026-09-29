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

    @Setter
    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Version
    @Column(nullable = false)
    private long version;

    public Booking(User user, Show show, Instant holdExpiresAt, BigDecimal totalAmount) {
        this.user = user;
        this.show = show;
        this.status = BookingStatus.HELD;
        this.holdExpiresAt = holdExpiresAt;
        this.totalAmount = totalAmount;
    }

    public boolean isHoldExpired(Instant now) {
        return status == BookingStatus.HELD && !holdExpiresAt.isAfter(now);
    }
}
