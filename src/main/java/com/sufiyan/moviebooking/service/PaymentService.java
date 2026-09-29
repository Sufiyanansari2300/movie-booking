package com.sufiyan.moviebooking.service;

import com.sufiyan.moviebooking.config.PaymentProperties;
import com.sufiyan.moviebooking.dto.BookingResponse;
import com.sufiyan.moviebooking.dto.PayRequest;
import com.sufiyan.moviebooking.dto.PaymentResponse;
import com.sufiyan.moviebooking.entity.Booking;
import com.sufiyan.moviebooking.entity.BookingStatus;
import com.sufiyan.moviebooking.entity.DiscountCode;
import com.sufiyan.moviebooking.entity.DiscountRedemption;
import com.sufiyan.moviebooking.entity.Payment;
import com.sufiyan.moviebooking.entity.PaymentMethod;
import com.sufiyan.moviebooking.entity.PaymentStatus;
import com.sufiyan.moviebooking.entity.ShowSeat;
import com.sufiyan.moviebooking.entity.ShowSeatStatus;
import com.sufiyan.moviebooking.entity.ShowStatus;
import com.sufiyan.moviebooking.event.BookingConfirmedEvent;
import com.sufiyan.moviebooking.exception.BadRequestException;
import com.sufiyan.moviebooking.exception.ConflictException;
import com.sufiyan.moviebooking.exception.ResourceNotFoundException;
import com.sufiyan.moviebooking.payment.PaymentGateway;
import com.sufiyan.moviebooking.payment.PaymentGateway.ChargeRequest;
import com.sufiyan.moviebooking.payment.PaymentGateway.ChargeResult;
import com.sufiyan.moviebooking.repository.BookingRepository;
import com.sufiyan.moviebooking.repository.BookingSeatRepository;
import com.sufiyan.moviebooking.repository.DiscountCodeRepository;
import com.sufiyan.moviebooking.repository.DiscountRedemptionRepository;
import com.sufiyan.moviebooking.repository.PaymentRepository;
import com.sufiyan.moviebooking.repository.RefundPolicyRepository;
import com.sufiyan.moviebooking.repository.ShowSeatRepository;
import com.sufiyan.moviebooking.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Pays for a held booking and confirms it.
 *
 * <p>Everything for one booking is serialized by a row lock on the booking ({@code SELECT ... FOR UPDATE}): two
 * payment attempts, a retry with the same idempotency key, and the hold-expiry sweeper all queue on that lock,
 * so a booking is charged successfully at most once and never confirmed after it expired.
 *
 * <p>Idempotency: a request with an {@code Idempotency-Key} that was already used for this booking returns the
 * stored attempt (success or failure) without calling the gateway again. Keys are single use: to retry a failed
 * payment, send a new key.
 *
 * <p>Trade-off: the (mock) gateway is called while the booking row is locked. With a real provider this would
 * become "record pending payment, commit, charge, then confirm" or a provider webhook.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final Pattern KEY_FORMAT = Pattern.compile("^[A-Za-z0-9_\\-:.]{8,100}$");

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final ShowSeatRepository showSeatRepository;
    private final PaymentRepository paymentRepository;
    private final DiscountCodeRepository discountCodeRepository;
    private final DiscountRedemptionRepository redemptionRepository;
    private final RefundPolicyRepository refundPolicyRepository;
    private final BookingService bookingService;
    private final PaymentGateway gateway;
    private final ApplicationEventPublisher events;
    private final PaymentProperties properties;
    private final Clock clock;
    private final ZoneId businessZone;

    public record PaymentOutcome(PaymentResponse payment, boolean replayed) {
    }

    @Transactional
    public PaymentOutcome pay(AppUserPrincipal caller, Long bookingId, String idempotencyKey, PayRequest request) {
        if (idempotencyKey == null || !KEY_FORMAT.matcher(idempotencyKey).matches()) {
            throw new BadRequestException("INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key header must be 8-100 characters of letters, digits, '-', '_', ':' or '.'");
        }
        // Lock first: every later read for this booking sees the latest committed state.
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        if (!caller.isAdmin() && !booking.getUser().getId().equals(caller.id())) {
            throw new ResourceNotFoundException("Booking", bookingId);
        }

        Optional<Payment> previous = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (previous.isPresent()) {
            Payment p = previous.get();
            if (!p.getBooking().getId().equals(bookingId)) {
                throw new ConflictException("IDEMPOTENCY_KEY_REUSED",
                        "This Idempotency-Key was already used for another payment");
            }
            return new PaymentOutcome(toResponse(p), true);
        }

        Instant now = clock.instant();
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            throw new ConflictException("BOOKING_ALREADY_CONFIRMED", "This booking is already paid");
        }
        if (booking.getStatus() != BookingStatus.HELD) {
            throw new ConflictException("BOOKING_NOT_HELD", "Only a held booking can be paid (status: "
                    + booking.getStatus() + ")");
        }
        if (booking.getShow().getStatus() != ShowStatus.SCHEDULED) {
            throw new ConflictException("SHOW_CANCELLED", "This show has been cancelled");
        }
        if (booking.isHoldExpired(now)) {
            throw new ConflictException("HOLD_EXPIRED", "The seat hold has expired; hold the seats again");
        }

        DiscountCode code = lockAndRecheckDiscount(booking, now);

        PaymentMethod method = request.method();
        ChargeResult result;
        if (booking.getTotalAmount().signum() == 0) {
            method = PaymentMethod.NONE; // fully discounted: nothing to charge
            result = ChargeResult.succeeded(null);
        } else {
            result = gateway.charge(new ChargeRequest(bookingId, booking.getTotalAmount(), properties.currency(),
                    method, request.paymentToken(), idempotencyKey));
        }

        boolean succeeded = result.outcome() == PaymentGateway.Outcome.SUCCEEDED;
        Payment payment = paymentRepository.save(new Payment(booking, booking.getUser(), booking.getTotalAmount(),
                properties.currency(), method, idempotencyKey,
                succeeded ? PaymentStatus.SUCCEEDED : PaymentStatus.FAILED,
                result.gatewayReference(), result.failureCode(), result.failureReason()));

        if (succeeded) {
            confirm(booking, code, now);
        } else {
            log.info("Payment {} for booking {} failed: {}", payment.getId(), bookingId, result.failureCode());
        }
        return new PaymentOutcome(toResponse(payment), false);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> history(AppUserPrincipal caller, Long bookingId) {
        bookingService.getForCaller(caller, bookingId); // ownership check
        return paymentRepository.findByBookingIdOrderByCreatedAtAsc(bookingId).stream()
                .map(p -> toResponse(p, null)).toList();
    }

    /**
     * Re-validates the booking's discount code at payment time with the code row locked, so the usage limit
     * cannot be exceeded by concurrent confirmations (two customers racing for the last use).
     */
    private DiscountCode lockAndRecheckDiscount(Booking booking, Instant now) {
        if (booking.getDiscountCode() == null) {
            return null;
        }
        DiscountCode code = discountCodeRepository.findByIdForUpdate(booking.getDiscountCode().getId()).orElseThrow();
        long userUses = redemptionRepository.countByDiscountCodeIdAndUserId(code.getId(), booking.getUser().getId());
        try {
            DiscountCalculator.validate(code, booking.getSubtotalAmount(), now, userUses);
        } catch (BadRequestException e) {
            throw new ConflictException("DISCOUNT_NO_LONGER_VALID",
                    e.getMessage() + " (" + e.getErrorCode() + "); remove the code or apply another one");
        }
        return code;
    }

    private void confirm(Booking booking, DiscountCode code, Instant now) {
        // Freeze the refund policy in force now: later policy changes never apply to this booking.
        booking.confirm(now, refundPolicyRepository.findFirstByActiveTrue().orElse(null));
        if (code != null) {
            code.setUsedCount(code.getUsedCount() + 1);
            redemptionRepository.save(new DiscountRedemption(code, booking, booking.getUser(), booking.getDiscountAmount()));
        }
        // Entity updates (not a bulk query) so @Version is checked and the persistence context stays consistent.
        List<ShowSeat> seats = showSeatRepository.findByBookingIdAndStatus(booking.getId(), ShowSeatStatus.HELD);
        long expected = bookingSeatRepository.countByBookingId(booking.getId());
        if (seats.size() != expected) {
            // Cannot happen while the hold is valid (only expired seats can be taken over); roll everything back.
            throw new ConflictException("HOLD_EXPIRED", "Some seats of this booking are no longer held");
        }
        seats.forEach(ShowSeat::markBooked);
        events.publishEvent(new BookingConfirmedEvent(booking.getId()));
        log.info("Booking {} confirmed ({} seats, total {})", booking.getId(), seats.size(), booking.getTotalAmount());
    }

    private PaymentResponse toResponse(Payment p) {
        return toResponse(p, bookingService.get(p.getBooking().getId()));
    }

    private PaymentResponse toResponse(Payment p, BookingResponse booking) {
        return new PaymentResponse(p.getId(), p.getStatus(), p.getAmount(), p.getCurrency(), p.getMethod(),
                p.getGatewayReference(), p.getFailureCode(), p.getFailureReason(),
                p.getCreatedAt() == null ? null : p.getCreatedAt().atZone(businessZone).toOffsetDateTime(), booking);
    }
}
