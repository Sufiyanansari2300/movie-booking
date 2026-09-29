package com.sufiyan.moviebooking.entity;

public enum PaymentMethod {
    CARD,
    UPI,
    WALLET,
    /** Nothing to charge (total was 0 after discount). */
    NONE
}
