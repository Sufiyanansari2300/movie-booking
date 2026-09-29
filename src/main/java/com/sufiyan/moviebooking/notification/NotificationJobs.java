package com.sufiyan.moviebooking.notification;

import com.sufiyan.moviebooking.config.NotificationProperties;
import com.sufiyan.moviebooking.entity.NotificationType;
import com.sufiyan.moviebooking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Background notification work: show reminders and retries of failed deliveries. */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationJobs {

    private static final int BATCH_SIZE = 200;

    private final NotificationService notificationService;
    private final BookingRepository bookingRepository;
    private final NotificationProperties properties;
    private final Clock clock;

    /** Reminds every confirmed booking whose show starts within the lead time. Safe to re-run (one per booking). */
    @Scheduled(fixedDelayString = "${app.notifications.reminder-interval}")
    public int sendReminders() {
        Instant now = clock.instant();
        List<Long> due = bookingRepository.findDueForReminder(now, now.plus(properties.reminderLeadTime()),
                Limit.of(BATCH_SIZE));
        for (Long bookingId : due) {
            try {
                notificationService.notify(bookingId, NotificationType.SHOW_REMINDER);
            } catch (RuntimeException e) {
                log.warn("Reminder for booking {} failed: {}", bookingId, e.getMessage());
            }
        }
        if (!due.isEmpty()) {
            log.info("Reminder job processed {} booking(s)", due.size());
        }
        return due.size();
    }

    @Scheduled(fixedDelayString = "${app.notifications.retry-interval}")
    public int retryFailed() {
        int sent = 0;
        for (Long id : notificationService.retryable(BATCH_SIZE)) {
            if (notificationService.deliver(id)) {
                sent++;
            }
        }
        return sent;
    }
}
