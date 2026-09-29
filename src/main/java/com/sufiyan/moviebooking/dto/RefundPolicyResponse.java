package com.sufiyan.moviebooking.dto;

import java.math.BigDecimal;
import java.util.List;

/** {@code inUse}: bookings reference this policy, so it can no longer be edited or deleted. */
public record RefundPolicyResponse(Long id, String name, boolean active, boolean inUse, List<Band> rules) {

    public record Band(int minHoursBeforeShow, BigDecimal refundPercent) {
    }
}
