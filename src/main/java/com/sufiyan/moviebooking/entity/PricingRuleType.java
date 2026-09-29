package com.sufiyan.moviebooking.entity;

public enum PricingRuleType {
    /** Show starts on a Saturday or Sunday (business time zone). */
    WEEKEND,
    /** Show's local start time falls in [windowStart, windowEnd). */
    PRIME_TIME
}
