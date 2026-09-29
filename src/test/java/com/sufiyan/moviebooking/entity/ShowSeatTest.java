package com.sufiyan.moviebooking.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ShowSeatTest {

    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");

    private final ShowSeat seat = new ShowSeat(null, null);
    private final Booking booking = new Booking(null, null, NOW.plusSeconds(600), BigDecimal.TEN, null);

    @Test
    void availableSeat_isHoldable() {
        assertThat(seat.isHoldable(NOW)).isTrue();
        assertThat(seat.effectiveStatus(NOW)).isEqualTo(ShowSeatStatus.AVAILABLE);
    }

    @Test
    void activeHold_blocksSeat_untilItExpires() {
        seat.hold(booking, NOW.plusSeconds(600));

        assertThat(seat.isHoldable(NOW)).isFalse();
        assertThat(seat.effectiveStatus(NOW)).isEqualTo(ShowSeatStatus.HELD);
        // exactly at expiry the hold is over
        assertThat(seat.isHoldable(NOW.plusSeconds(600))).isTrue();
        assertThat(seat.effectiveStatus(NOW.plusSeconds(601))).isEqualTo(ShowSeatStatus.AVAILABLE);
    }

    @Test
    void bookedSeat_isNeverHoldable() {
        seat.setStatus(ShowSeatStatus.BOOKED);

        assertThat(seat.isHoldable(NOW.plusSeconds(100_000))).isFalse();
    }

    @Test
    void booking_reportsExpiredHold() {
        assertThat(booking.isHoldExpired(NOW)).isFalse();
        assertThat(booking.isHoldExpired(NOW.plusSeconds(600))).isTrue();
        booking.setStatus(BookingStatus.CONFIRMED);
        assertThat(booking.isHoldExpired(NOW.plusSeconds(600))).isFalse();
    }
}
