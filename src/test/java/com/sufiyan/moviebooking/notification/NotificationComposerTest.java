package com.sufiyan.moviebooking.notification;

import com.sufiyan.moviebooking.dto.BookingResponse;
import com.sufiyan.moviebooking.entity.BookingStatus;
import com.sufiyan.moviebooking.entity.NotificationType;
import com.sufiyan.moviebooking.entity.RefundReason;
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
            OffsetDateTime.parse("2026-10-01T10:05:00+05:30"), null, null,
            OffsetDateTime.parse("2026-10-01T10:00:00+05:30"));

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
    void customerCancellation_mentionsTheRefund() {
        BookingResponse cancelled = withRefund(new BookingResponse.RefundInfo(new BigDecimal("217.80"),
                new BigDecimal("50.00"), RefundReason.CUSTOMER_CANCELLATION, "Standard"));

        var message = NotificationComposer.compose(NotificationType.BOOKING_CANCELLED, cancelled, "Alice", "INR");

        assertThat(message.subject()).isEqualTo("Booking #42 cancelled: Oppenheimer");
        assertThat(message.body()).contains("cancelled as requested")
                .contains("Refund:   INR 217.80 (50%) to your original payment method");
    }

    @Test
    void showCancellation_apologises_andZeroRefundIsExplained() {
        var showCancelled = NotificationComposer.compose(NotificationType.BOOKING_CANCELLED,
                withRefund(new BookingResponse.RefundInfo(new BigDecimal("435.60"), new BigDecimal("100"),
                        RefundReason.SHOW_CANCELLED, null)), "Alice", "INR");
        var noRefund = NotificationComposer.compose(NotificationType.BOOKING_CANCELLED,
                withRefund(new BookingResponse.RefundInfo(BigDecimal.ZERO.setScale(2), BigDecimal.ZERO,
                        RefundReason.CUSTOMER_CANCELLATION, "Standard")), "Alice", "INR");

        assertThat(showCancelled.body()).contains("cancelled by the cinema").contains("INR 435.60 (100%)");
        assertThat(noRefund.body()).contains("Refund:   none under the refund policy");
    }

    private BookingResponse withRefund(BookingResponse.RefundInfo refund) {
        return new BookingResponse(booking.id(), BookingStatus.CANCELLED, booking.show(), booking.seats(),
                booking.appliedPricingRules(), booking.subtotalAmount(), booking.discountCode(),
                booking.discountAmount(), booking.totalAmount(), booking.holdExpiresAt(), booking.confirmedAt(),
                OffsetDateTime.parse("2026-10-02T10:00:00+05:30"), refund, booking.createdAt());
    }

    @Test
    void confirmationWithoutDiscount_hasNoDiscountNote() {
        BookingResponse plain = new BookingResponse(1L, BookingStatus.CONFIRMED, booking.show(), booking.seats(),
                List.of(), new BigDecimal("440.00"), null, BigDecimal.ZERO, new BigDecimal("440.00"),
                booking.holdExpiresAt(), booking.confirmedAt(), null, null, booking.createdAt());

        assertThat(NotificationComposer.compose(NotificationType.BOOKING_CONFIRMED, plain, "Bob", "INR").body())
                .contains("Paid:     INR 440.00\n").doesNotContain("code");
    }
}
