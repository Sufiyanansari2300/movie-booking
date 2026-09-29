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

import java.time.Instant;

/**
 * A physical seat in the context of one show; this is what customers hold and book.
 * {@code @Version} adds optimistic locking on top of the row locks taken when holding seats.
 */
@Getter
@Entity
@Table(name = "show_seats")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShowSeat extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShowSeatStatus status;

    /** Booking currently holding or owning this seat; null while AVAILABLE. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    @Version
    @Column(nullable = false)
    private long version;

    public ShowSeat(Show show, Seat seat) {
        this.show = show;
        this.seat = seat;
        this.status = ShowSeatStatus.AVAILABLE;
    }

    /** Free to hold: AVAILABLE, or HELD by a hold that has already expired (not yet swept). */
    public boolean isHoldable(Instant now) {
        return status == ShowSeatStatus.AVAILABLE
                || (status == ShowSeatStatus.HELD && holdExpiresAt != null && !holdExpiresAt.isAfter(now));
    }

    /** Status as customers should see it: an expired hold shows as AVAILABLE. */
    public ShowSeatStatus effectiveStatus(Instant now) {
        return isHoldable(now) ? ShowSeatStatus.AVAILABLE : status;
    }

    /** Paid: the seat stays with its booking for good. */
    public void markBooked() {
        this.status = ShowSeatStatus.BOOKED;
        this.holdExpiresAt = null;
    }

    /** Back to sale after a cancellation. */
    public void release() {
        this.status = ShowSeatStatus.AVAILABLE;
        this.booking = null;
        this.holdExpiresAt = null;
    }

    public void hold(Booking booking, Instant expiresAt) {
        this.status = ShowSeatStatus.HELD;
        this.booking = booking;
        this.holdExpiresAt = expiresAt;
    }
}
