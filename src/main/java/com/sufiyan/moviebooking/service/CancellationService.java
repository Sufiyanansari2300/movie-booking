package com.sufiyan.moviebooking.service;

import com.sufiyan.moviebooking.config.PaymentProperties;
import com.sufiyan.moviebooking.dto.BookingResponse;
import com.sufiyan.moviebooking.dto.RefundQuoteResponse;
import com.sufiyan.moviebooking.dto.ShowCancellationResponse;
import com.sufiyan.moviebooking.entity.Booking;
import com.sufiyan.moviebooking.entity.BookingStatus;
import com.sufiyan.moviebooking.entity.Payment;
import com.sufiyan.moviebooking.entity.PaymentStatus;
import com.sufiyan.moviebooking.entity.Refund;
import com.sufiyan.moviebooking.entity.RefundReason;
import com.sufiyan.moviebooking.entity.Show;
import com.sufiyan.moviebooking.entity.ShowSeat;
import com.sufiyan.moviebooking.entity.ShowSeatStatus;
import com.sufiyan.moviebooking.entity.ShowStatus;
import com.sufiyan.moviebooking.event.BookingCancelledEvent;
import com.sufiyan.moviebooking.exception.BusinessException;
import com.sufiyan.moviebooking.exception.ConflictException;
import com.sufiyan.moviebooking.exception.ResourceNotFoundException;
import com.sufiyan.moviebooking.payment.PaymentGateway;
import com.sufiyan.moviebooking.repository.BookingRepository;
import com.sufiyan.moviebooking.repository.PaymentRepository;
import com.sufiyan.moviebooking.repository.RefundPolicyRuleRepository;
import com.sufiyan.moviebooking.repository.RefundRepository;
import com.sufiyan.moviebooking.repository.ShowRepository;
import com.sufiyan.moviebooking.repository.ShowSeatRepository;
import com.sufiyan.moviebooking.security.AppUserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Cancellations and refunds.
 *
 * <p>Customer cancellation: only a CONFIRMED booking, only before the show starts. The refund percent comes from
 * the policy frozen on the booking at payment time and is applied to what was actually paid. The booking row is
 * locked, so a cancellation can happen (and refund) only once.
 *
 * <p>Show cancellation (admin): the show is marked CANCELLED first (no new holds or payments), then every open
 * booking is handled in its own short transaction: confirmed ones get a 100% refund, held ones are released.
 * Re-running it resumes any bookings that failed or were not reached.
 */
@Slf4j
@Service
public class CancellationService {

    private static final BigDecimal FULL = BigDecimal.valueOf(100);

    private final BookingRepository bookingRepository;
    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final RefundPolicyRuleRepository ruleRepository;
    private final BookingService bookingService;
    private final PaymentGateway gateway;
    private final ApplicationEventPublisher events;
    private final PaymentProperties paymentProperties;
    private final Clock clock;
    private final TransactionTemplate tx;

    public CancellationService(BookingRepository bookingRepository, ShowRepository showRepository,
                               ShowSeatRepository showSeatRepository, PaymentRepository paymentRepository,
                               RefundRepository refundRepository, RefundPolicyRuleRepository ruleRepository,
                               BookingService bookingService, PaymentGateway gateway, ApplicationEventPublisher events,
                               PaymentProperties paymentProperties, Clock clock,
                               PlatformTransactionManager transactionManager) {
        this.bookingRepository = bookingRepository;
        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.ruleRepository = ruleRepository;
        this.bookingService = bookingService;
        this.gateway = gateway;
        this.events = events;
        this.paymentProperties = paymentProperties;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public RefundQuoteResponse quote(AppUserPrincipal caller, Long bookingId) {
        Booking booking = owned(caller, bookingRepository.findDetailedById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId)));
        Instant now = clock.instant();
        String blocker = cancellationBlocker(booking, now);
        Duration before = Duration.between(now, booking.getShow().getStartTime());
        BigDecimal paid = paidAmount(booking.getId());
        BigDecimal percent = blocker == null ? policyPercent(booking, before) : BigDecimal.ZERO;
        return new RefundQuoteResponse(bookingId, blocker == null, blocker, policyName(booking),
                Math.max(0, before.toHours()), paid, percent, RefundCalculator.amount(paid, percent));
    }

    @Transactional
    public BookingResponse cancel(AppUserPrincipal caller, Long bookingId) {
        Booking booking = owned(caller, bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId)));
        Instant now = clock.instant();
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ConflictException("BOOKING_ALREADY_CANCELLED", "This booking is already cancelled");
        }
        String blocker = cancellationBlocker(booking, now);
        if (blocker != null) {
            throw new ConflictException(booking.getStatus() == BookingStatus.CONFIRMED
                    ? "CANCELLATION_CLOSED" : "BOOKING_NOT_CONFIRMED", blocker);
        }
        BigDecimal percent = policyPercent(booking, Duration.between(now, booking.getShow().getStartTime()));
        refundAndCancel(booking, percent, RefundReason.CUSTOMER_CANCELLATION, policyName(booking), now);
        return bookingService.get(bookingId);
    }

    /** Cancels a show and settles all its bookings. Safe to call again on an already cancelled show (resumes). */
    public ShowCancellationResponse cancelShow(Long showId, String reason) {
        tx.executeWithoutResult(status -> {
            Show show = showRepository.findByIdForUpdate(showId)
                    .orElseThrow(() -> new ResourceNotFoundException("Show", showId));
            if (show.getStatus() == ShowStatus.SCHEDULED) {
                if (!show.getEndTime().isAfter(clock.instant())) {
                    throw new ConflictException("SHOW_ALREADY_ENDED", "A show that has ended cannot be cancelled");
                }
                show.cancel(reason);
                log.info("Show {} cancelled: {}", showId, reason);
            }
        });

        List<Long> open = tx.execute(status -> bookingRepository.findOpenIdsByShow(showId));
        int refunded = 0;
        int released = 0;
        BigDecimal total = BigDecimal.ZERO;
        List<Long> failed = new ArrayList<>();
        for (Long bookingId : open) {
            try {
                BigDecimal amount = tx.execute(status -> settleForCancelledShow(bookingId));
                if (amount == null) {
                    released++;
                } else {
                    refunded++;
                    total = total.add(amount);
                }
            } catch (RuntimeException e) {
                log.warn("Could not settle booking {} of cancelled show {}: {}", bookingId, showId, e.getMessage());
                failed.add(bookingId);
            }
        }
        return new ShowCancellationResponse(showId, refunded, released, total, failed);
    }

    /** Returns the refunded amount for a confirmed booking, or null when a hold was released. */
    private BigDecimal settleForCancelledShow(Long bookingId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElseThrow();
        Instant now = clock.instant();
        return switch (booking.getStatus()) {
            case CONFIRMED -> refundAndCancel(booking, FULL, RefundReason.SHOW_CANCELLED, null, now);
            case HELD -> {
                booking.setStatus(BookingStatus.RELEASED);
                showSeatRepository.findByBookingIdAndStatus(bookingId, ShowSeatStatus.HELD)
                        .forEach(ShowSeat::release);
                yield null;
            }
            default -> null; // settled concurrently (e.g. released or cancelled by the customer)
        };
    }

    private BigDecimal refundAndCancel(Booking booking, BigDecimal percent, RefundReason reason, String policyName,
                                       Instant now) {
        Payment payment = paymentRepository.findFirstByBookingIdAndStatus(booking.getId(), PaymentStatus.SUCCEEDED)
                .orElse(null);
        BigDecimal paid = payment == null ? BigDecimal.ZERO : payment.getAmount();
        BigDecimal amount = RefundCalculator.amount(paid, percent);
        String reference = null;
        if (amount.signum() > 0) {
            PaymentGateway.RefundResult result = gateway.refund(new PaymentGateway.RefundRequest(booking.getId(),
                    payment.getGatewayReference(), amount, paymentProperties.currency(), "refund-" + booking.getId()));
            if (!result.succeeded()) {
                // Nothing is changed; the customer (or the show cancellation re-run) can try again.
                throw new BusinessException(HttpStatus.BAD_GATEWAY, "REFUND_FAILED",
                        "The refund could not be processed, please retry: " + result.failureReason());
            }
            reference = result.refundReference();
        }
        refundRepository.save(new Refund(booking, payment, amount, percent, reason, policyName, reference));
        booking.cancel(now);
        showSeatRepository.findByBookingIdAndStatus(booking.getId(), ShowSeatStatus.BOOKED)
                .forEach(ShowSeat::release);
        events.publishEvent(new BookingCancelledEvent(booking.getId()));
        log.info("Booking {} cancelled ({}), refunded {} ({}%)", booking.getId(), reason, amount, percent);
        return amount;
    }

    /** Null if the booking can be cancelled now, otherwise the reason it cannot. */
    private static String cancellationBlocker(Booking booking, Instant now) {
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            return booking.getStatus() == BookingStatus.HELD
                    ? "Only paid bookings can be cancelled; release the hold instead"
                    : "The booking is " + booking.getStatus();
        }
        if (!booking.getShow().getStartTime().isAfter(now)) {
            return "The show has already started";
        }
        return null;
    }

    private BigDecimal policyPercent(Booking booking, Duration beforeShow) {
        if (booking.getRefundPolicy() == null) {
            return BigDecimal.ZERO;
        }
        List<RefundCalculator.Band> bands = ruleRepository
                .findByPolicyIdOrderByMinHoursBeforeShowDesc(booking.getRefundPolicy().getId()).stream()
                .map(r -> new RefundCalculator.Band(r.getMinHoursBeforeShow(), r.getRefundPercent())).toList();
        return RefundCalculator.percentFor(bands, beforeShow);
    }

    private static String policyName(Booking booking) {
        return booking.getRefundPolicy() == null ? null : booking.getRefundPolicy().getName();
    }

    private BigDecimal paidAmount(Long bookingId) {
        return paymentRepository.findFirstByBookingIdAndStatus(bookingId, PaymentStatus.SUCCEEDED)
                .map(Payment::getAmount).orElse(BigDecimal.ZERO);
    }

    private static Booking owned(AppUserPrincipal caller, Booking booking) {
        if (!caller.isAdmin() && !booking.getUser().getId().equals(caller.id())) {
            throw new ResourceNotFoundException("Booking", booking.getId());
        }
        return booking;
    }
}
