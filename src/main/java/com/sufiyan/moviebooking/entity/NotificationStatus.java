package com.sufiyan.moviebooking.entity;

public enum NotificationStatus {
    /** Recorded, not delivered yet. */
    PENDING,
    SENT,
    /** Last attempt failed; retried until the attempt limit. */
    FAILED
}
