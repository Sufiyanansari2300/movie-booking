package com.sufiyan.moviebooking.entity;

public enum BookingStatus {
    /** Seats are reserved until {@code holdExpiresAt}; awaiting payment. */
    HELD,
    /** Paid; seats are BOOKED. */
    CONFIRMED,
    /** The customer let go of the hold before paying. */
    RELEASED,
    /** The hold timed out before payment. */
    EXPIRED,
    /** Cancelled after confirmation (refund per policy). */
    CANCELLED
}
