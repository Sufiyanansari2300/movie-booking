package com.sufiyan.moviebooking.entity;

public enum RefundReason {
    /** Customer cancelled; refund per the booking's refund policy. */
    CUSTOMER_CANCELLATION,
    /** The show was cancelled by an admin; always a full refund. */
    SHOW_CANCELLED
}
