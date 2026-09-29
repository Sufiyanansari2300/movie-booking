package com.sufiyan.moviebooking.service;

import com.sufiyan.moviebooking.entity.BookingStatus;
import com.sufiyan.moviebooking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

/**
 * Periodically expires overdue seat holds and frees their seats. Each booking is expired in its own
 * transaction so one failure does not block the rest. Holds are also treated as expired on read, so
 * correctness never depends on how quickly this job runs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HoldExpiryJob {

    private static final int BATCH_SIZE = 200;

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.booking.expiry-sweep-interval}")
    public int sweep() {
        List<Long> due = bookingRepository.findIdsByStatusAndExpiredBefore(BookingStatus.HELD, clock.instant(),
                Limit.of(BATCH_SIZE));
        int expired = 0;
        for (Long id : due) {
            try {
                if (bookingService.expireHold(id)) {
                    expired++;
                }
            } catch (RuntimeException e) {
                log.warn("Could not expire booking {}: {}", id, e.getMessage());
            }
        }
        if (expired > 0) {
            log.info("Hold sweep expired {} booking(s)", expired);
        }
        return expired;
    }
}
