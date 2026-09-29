package com.sufiyan.moviebooking.notification;

import com.sufiyan.moviebooking.dto.BookingResponse;
import com.sufiyan.moviebooking.entity.BookingStatus;
import com.sufiyan.moviebooking.entity.NotificationType;
import com.sufiyan.moviebooking.entity.SeatType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationComposerTest {

    private final BookingResponse booking = new BookingResponse(42L, BookingStatus.CONFIRMED,
            new BookingResponse.ShowInfo(7L, "Oppenheimer", "Riverside Cinemas", "Audi 1",
                    OffsetDateTime.parse("2026-10-05T18:30:00+05:30")),
            List.of(new BookingResponse.SeatLine(1L, "A1", SeatType.REGULAR, new BigDecimal("220.00"), new BigDecimal("242.00")),
                    new BookingResponse.SeatLine(2L, "A2", SeatType.REGULAR, new BigDecimal("220.00"), new BigDecimal("242.00"))),
            List.of("Prime time"), new BigDecimal("484.00"), "WELCOME10", new BigDecimal("48.40"),
            new BigDecimal("435.60"), OffsetDateTime.parse("2026-10-01T10:10:00+05:30"),
            OffsetDateTime.parse("2026-10-01T10:05:00+05:30"), OffsetDateTime.parse("2026-10-01T10:00:00+05:30"));

    @Test
    void confirmation_hasAllBookingDetails() {
        var message = NotificationComposer.compose(NotificationType.BOOKING_CONFIRMED, booking, "Alice", "INR");

        assertThat(message.subject()).isEqualTo("Booking #42 confirmed: Oppenheimer on Mon 5 Oct 2026, 6:30 PM");
        assertThat(message.body())
                .contains("Hi Alice,")
                .contains("Riverside Cinemas, Audi 1")
                .contains("Seats:    A1, A2")
                .contains("Paid:     INR 435.60 (code WELCOME10 saved INR 48.40)")
                .contains("#42");
    }

    @Test
    void reminder_mentionsStartTimeAndSeats() {
        var message = NotificationComposer.compose(NotificationType.SHOW_REMINDER, booking, "Alice", "INR");

        assertThat(message.subject()).isEqualTo("Reminder: Oppenheimer starts at 6:30 PM");
        assertThat(message.body()).contains("A1, A2").contains("arrive 15 minutes early");
    }

    @Test
    void confirmationWithoutDiscount_hasNoDiscountNote() {
        BookingResponse plain = new BookingResponse(1L, BookingStatus.CONFIRMED, booking.show(), booking.seats(),
                List.of(), new BigDecimal("440.00"), null, BigDecimal.ZERO, new BigDecimal("440.00"),
                booking.holdExpiresAt(), booking.confirmedAt(), booking.createdAt());

        assertThat(NotificationComposer.compose(NotificationType.BOOKING_CONFIRMED, plain, "Bob", "INR").body())
                .contains("Paid:     INR 440.00\n").doesNotContain("code");
    }
}
