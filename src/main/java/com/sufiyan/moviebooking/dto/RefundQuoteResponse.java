package com.sufiyan.moviebooking.dto;

import java.math.BigDecimal;

/** What cancelling right now would refund. {@code cancellable=false} explains why in {@code reason}. */
public record RefundQuoteResponse(Long bookingId, boolean cancellable, String reason, String policyName,
                                  long hoursBeforeShow, BigDecimal paidAmount, BigDecimal refundPercent,
                                  BigDecimal refundAmount) {
}
