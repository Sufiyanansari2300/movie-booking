package com.sufiyan.moviebooking.notification;

import com.sufiyan.moviebooking.config.NotificationProperties;
import com.sufiyan.moviebooking.config.PaymentProperties;
import com.sufiyan.moviebooking.dto.BookingResponse;
import com.sufiyan.moviebooking.dto.NotificationResponse;
import com.sufiyan.moviebooking.dto.PageResponse;
import com.sufiyan.moviebooking.entity.Booking;
import com.sufiyan.moviebooking.entity.Notification;
import com.sufiyan.moviebooking.entity.NotificationStatus;
import com.sufiyan.moviebooking.entity.NotificationType;
import com.sufiyan.moviebooking.repository.BookingRepository;
import com.sufiyan.moviebooking.repository.NotificationRepository;
import com.sufiyan.moviebooking.security.AppUserPrincipal;
import com.sufiyan.moviebooking.service.BookingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Records and delivers notifications.
 *
 * <p>Two short transactions per message: (1) record it as PENDING, (2) after the provider call, mark it SENT or
 * FAILED. The provider is never called inside a transaction, so a slow provider holds no DB connection or lock.
 * The unique (booking, type) constraint makes {@link #notify} idempotent even under concurrent duplicate calls.
 */
@Slf4j
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final NotificationSender sender;
    private final NotificationProperties properties;
    private final PaymentProperties paymentProperties;
    private final Clock clock;
    private final ZoneId businessZone;
    private final TransactionTemplate tx;

    public NotificationService(NotificationRepository notificationRepository, BookingRepository bookingRepository,
                               BookingService bookingService, NotificationSender sender,
                               NotificationProperties properties, PaymentProperties paymentProperties, Clock clock,
                               ZoneId businessZone, PlatformTransactionManager transactionManager) {
        this.notificationRepository = notificationRepository;
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.sender = sender;
        this.properties = properties;
        this.paymentProperties = paymentProperties;
        this.clock = clock;
        this.businessZone = businessZone;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** Records and sends a notification of this type for the booking, unless one already exists. */
    public void notify(Long bookingId, NotificationType type) {
        Optional<Long> created;
        try {
            created = Optional.ofNullable(tx.execute(status -> record(bookingId, type)));
        } catch (DataIntegrityViolationException duplicate) {
            log.debug("{} for booking {} was recorded concurrently", type, bookingId);
            return;
        }
        created.ifPresent(this::deliver);
    }

    /** One delivery attempt; returns true if sent. */
    public boolean deliver(Long notificationId) {
        Notification n = tx.execute(status -> notificationRepository.findById(notificationId).orElse(null));
        if (n == null || n.getStatus() == NotificationStatus.SENT) {
            return false;
        }
        String error = null;
        try {
            sender.send(new NotificationSender.OutgoingMessage(n.getChannel(), n.getRecipient(), n.getSubject(), n.getBody()));
        } catch (RuntimeException e) {
            error = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.warn("Sending notification {} ({}) failed: {}", notificationId, n.getType(), error);
        }
        String failure = error;
        tx.executeWithoutResult(status -> notificationRepository.findById(notificationId).ifPresent(saved -> {
            if (failure == null) {
                saved.markSent(clock.instant());
            } else {
                saved.markFailed(failure);
            }
        }));
        return failure == null;
    }

    /** Ids of notifications to retry now: FAILED below the attempt limit, or PENDING for more than 5 minutes. */
    @Transactional(readOnly = true)
    public List<Long> retryable(int limit) {
        Instant stuckBefore = clock.instant().minusSeconds(300);
        return notificationRepository.findRetryable(properties.maxAttempts(), stuckBefore,
                Limit.of(limit));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> forBooking(AppUserPrincipal caller, Long bookingId) {
        bookingService.getForCaller(caller, bookingId); // ownership check
        return notificationRepository.findByBookingIdOrderByCreatedAtAsc(bookingId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> search(NotificationStatus status, Pageable pageable) {
        var page = status == null ? notificationRepository.findAll(pageable)
                : notificationRepository.findByStatus(status, pageable);
        return PageResponse.from(page, this::toResponse);
    }

    private Long record(Long bookingId, NotificationType type) {
        if (notificationRepository.existsByBookingIdAndType(bookingId, type)) {
            return null;
        }
        Booking booking = bookingRepository.findDetailedById(bookingId).orElse(null);
        if (booking == null) {
            return null;
        }
        BookingResponse details = bookingService.get(bookingId);
        NotificationComposer.Message message = NotificationComposer.compose(type, details,
                booking.getUser().getName(), paymentProperties.currency());
        Notification saved = notificationRepository.saveAndFlush(new Notification(booking, booking.getUser(), type,
                booking.getUser().getEmail(), message.subject(), message.body()));
        return saved.getId();
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getBooking().getId(), n.getType(), n.getChannel(), n.getRecipient(),
                n.getSubject(), n.getBody(), n.getStatus(), n.getAttempts(), n.getLastError(),
                n.getSentAt() == null ? null : n.getSentAt().atZone(businessZone).toOffsetDateTime(),
                n.getCreatedAt().atZone(businessZone).toOffsetDateTime());
    }
}
