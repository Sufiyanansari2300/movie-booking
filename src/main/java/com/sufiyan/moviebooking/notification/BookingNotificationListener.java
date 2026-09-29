package com.sufiyan.moviebooking.notification;

import com.sufiyan.moviebooking.config.AsyncConfig;
import com.sufiyan.moviebooking.entity.NotificationType;
import com.sufiyan.moviebooking.event.BookingCancelledEvent;
import com.sufiyan.moviebooking.event.BookingConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends confirmations/cancellations only after the transaction has committed (a rolled-back confirmation
 * never notifies) and on the notification thread pool (the payment request never waits for it).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingNotificationListener {

    private final NotificationService notificationService;

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        try {
            notificationService.notify(event.bookingId(), NotificationType.BOOKING_CONFIRMED);
        } catch (RuntimeException e) {
            // Never propagate: the booking is already confirmed. The retry job picks up recorded failures.
            log.error("Could not notify confirmation of booking {}", event.bookingId(), e);
        }
    }

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingCancelled(BookingCancelledEvent event) {
        try {
            notificationService.notify(event.bookingId(), NotificationType.BOOKING_CANCELLED);
        } catch (RuntimeException e) {
            log.error("Could not notify cancellation of booking {}", event.bookingId(), e);
        }
    }
}
