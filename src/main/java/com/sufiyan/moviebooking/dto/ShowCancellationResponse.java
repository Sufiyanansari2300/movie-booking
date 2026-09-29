package com.sufiyan.moviebooking.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Result of (re)running a show cancellation. {@code failedBookingIds} were left untouched and are processed
 * when the cancellation is run again.
 */
public record ShowCancellationResponse(Long showId, int refundedBookings, int releasedHolds, BigDecimal totalRefunded,
                                       List<Long> failedBookingIds) {
}
