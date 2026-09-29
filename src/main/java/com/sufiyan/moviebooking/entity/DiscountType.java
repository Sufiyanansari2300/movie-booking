package com.sufiyan.moviebooking.entity;

public enum DiscountType {
    /** {@code value}% off the subtotal, optionally capped by {@code maxDiscountAmount}. */
    PERCENT,
    /** {@code value} off the subtotal (never below zero). */
    FLAT
}
