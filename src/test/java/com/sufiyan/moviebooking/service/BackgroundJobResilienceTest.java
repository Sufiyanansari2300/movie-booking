package com.sufiyan.moviebooking.service;

import com.sufiyan.moviebooking.config.NotificationProperties;
import com.sufiyan.moviebooking.entity.BookingStatus;
import com.sufiyan.moviebooking.entity.NotificationType;
import com.sufiyan.moviebooking.event.BookingCancelledEvent;
import com.sufiyan.moviebooking.event.BookingConfirmedEvent;
import com.sufiyan.moviebooking.notification.BookingNotificationListener;
import com.sufiyan.moviebooking.notification.NotificationJobs;
import com.sufiyan.moviebooking.notification.NotificationService;
import com.sufiyan.moviebooking.repository.BookingRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** One bad item must never stop a background job or leak an exception into a committed transaction. */
class BackgroundJobResilienceTest {

    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);

    @Test
    void holdSweeper_keepsGoingWhenOneBookingFails() {
        BookingService bookingService = mock(BookingService.class);
        when(bookingRepository.findIdsByStatusAndExpiredBefore(eq(BookingStatus.HELD), any(), any()))
                .thenReturn(List.of(1L, 2L, 3L));
        when(bookingService.expireHold(1L)).thenReturn(true);
        when(bookingService.expireHold(2L)).thenThrow(new IllegalStateException("db hiccup"));
        when(bookingService.expireHold(3L)).thenReturn(true);

        int expired = new HoldExpiryJob(bookingRepository, bookingService, Clock.systemUTC()).sweep();

        assertThat(expired).isEqualTo(2);
        verify(bookingService).expireHold(3L);
    }

    @Test
    void reminderJob_keepsGoingWhenOneReminderFails() {
        when(bookingRepository.findDueForReminder(any(), any(), any())).thenReturn(List.of(10L, 11L));
        doThrow(new IllegalStateException("boom")).when(notificationService).notify(10L, NotificationType.SHOW_REMINDER);

        NotificationJobs jobs = new NotificationJobs(notificationService, bookingRepository,
                new NotificationProperties(Duration.ofHours(2), 3), Clock.systemUTC());

        assertThat(jobs.sendReminders()).isEqualTo(2);
        verify(notificationService).notify(11L, NotificationType.SHOW_REMINDER);
    }

    @Test
    void eventListeners_neverPropagateFailures() {
        doThrow(new IllegalStateException("provider exploded")).when(notificationService).notify(any(), any());
        BookingNotificationListener listener = new BookingNotificationListener(notificationService);

        assertThatCode(() -> listener.onBookingConfirmed(new BookingConfirmedEvent(1L))).doesNotThrowAnyException();
        assertThatCode(() -> listener.onBookingCancelled(new BookingCancelledEvent(1L))).doesNotThrowAnyException();
    }
}
