package com.sufiyan.moviebooking.notification;

import com.sufiyan.moviebooking.dto.BookingResponse;
import com.sufiyan.moviebooking.entity.NotificationType;
import com.sufiyan.moviebooking.entity.RefundReason;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Collectors;

/** Builds notification text from a booking. Pure logic. */
public final class NotificationComposer {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM yyyy, h:mm a", Locale.ENGLISH);

    private NotificationComposer() {
    }

    public record Message(String subject, String body) {
    }

    public static Message compose(NotificationType type, BookingResponse b, String customerName, String currency) {
        String movie = b.show().movieTitle();
        String when = b.show().startTime().format(WHEN);
        String seats = b.seats().stream().map(BookingResponse.SeatLine::label).collect(Collectors.joining(", "));
        String venue = b.show().theaterName() + ", " + b.show().screenName();
        return switch (type) {
            case BOOKING_CONFIRMED -> new Message(
                    "Booking #" + b.id() + " confirmed: " + movie + " on " + when,
                    """
                    Hi %s,

                    Your booking is confirmed.

                    Movie:    %s
                    When:     %s
                    Where:    %s
                    Seats:    %s
                    Paid:     %s %s%s

                    Booking reference: #%d. Enjoy the show!"""
                            .formatted(customerName, movie, when, venue, seats, currency,
                                    b.totalAmount().toPlainString(), discountNote(b, currency), b.id()));
            case SHOW_REMINDER -> new Message(
                    "Reminder: " + movie + " starts at " + b.show().startTime().format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)),
                    """
                    Hi %s,

                    A reminder that your show is coming up.

                    Movie:    %s
                    When:     %s
                    Where:    %s
                    Seats:    %s

                    Booking reference: #%d. Please arrive 15 minutes early."""
                            .formatted(customerName, movie, when, venue, seats, b.id()));
            case BOOKING_CANCELLED -> new Message(
                    "Booking #" + b.id() + " cancelled: " + movie,
                    """
                    Hi %s,

                    %s

                    Movie:    %s
                    When:     %s
                    Seats:    %s
                    %s

                    Booking reference: #%d."""
                            .formatted(customerName, cancellationLine(b), movie, when, seats,
                                    refundLine(b, currency), b.id()));
        };
    }

    private static String cancellationLine(BookingResponse b) {
        return b.refund() != null && b.refund().reason() == RefundReason.SHOW_CANCELLED
                ? "We are sorry: this show has been cancelled by the cinema, so your booking is cancelled."
                : "Your booking has been cancelled as requested.";
    }

    private static String refundLine(BookingResponse b, String currency) {
        if (b.refund() == null || b.refund().amount().signum() == 0) {
            return "Refund:   none under the refund policy for this cancellation time";
        }
        return "Refund:   " + currency + " " + b.refund().amount().toPlainString() + " ("
                + b.refund().refundPercent().stripTrailingZeros().toPlainString() + "%) to your original payment method";
    }

    private static String discountNote(BookingResponse b, String currency) {
        return b.discountCode() == null ? ""
                : " (code " + b.discountCode() + " saved " + currency + " " + b.discountAmount().toPlainString() + ")";
    }
}
