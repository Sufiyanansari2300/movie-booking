package com.sufiyan.moviebooking.service;

import com.sufiyan.moviebooking.config.BookingProperties;
import com.sufiyan.moviebooking.dto.BookingResponse;
import com.sufiyan.moviebooking.entity.Booking;
import com.sufiyan.moviebooking.entity.BookingSeat;
import com.sufiyan.moviebooking.entity.BookingStatus;
import com.sufiyan.moviebooking.entity.DiscountCode;
import com.sufiyan.moviebooking.entity.SeatType;
import com.sufiyan.moviebooking.entity.Show;
import com.sufiyan.moviebooking.entity.ShowSeat;
import com.sufiyan.moviebooking.entity.ShowStatus;
import com.sufiyan.moviebooking.entity.User;
import com.sufiyan.moviebooking.exception.BadRequestException;
import com.sufiyan.moviebooking.exception.ConflictException;
import com.sufiyan.moviebooking.exception.ResourceNotFoundException;
import com.sufiyan.moviebooking.repository.BookingRepository;
import com.sufiyan.moviebooking.repository.BookingSeatRepository;
import com.sufiyan.moviebooking.repository.DiscountCodeRepository;
import com.sufiyan.moviebooking.repository.DiscountRedemptionRepository;
import com.sufiyan.moviebooking.repository.RefundRepository;
import com.sufiyan.moviebooking.repository.ShowPriceRepository;
import com.sufiyan.moviebooking.repository.ShowRepository;
import com.sufiyan.moviebooking.repository.ShowSeatRepository;
import com.sufiyan.moviebooking.repository.UserRepository;
import com.sufiyan.moviebooking.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Seat holds and the booking lifecycle.
 *
 * <p>Concurrency: {@link #hold} row-locks the requested show seats ({@code SELECT ... FOR UPDATE}, in id order)
 * before checking them, so concurrent requests for the same seat are serialized and exactly one succeeds.
 * {@code @Version} on show seats and the unique (show, seat) constraint back this up.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final ShowSeatRepository showSeatRepository;
    private final ShowRepository showRepository;
    private final ShowPriceRepository showPriceRepository;
    private final UserRepository userRepository;
    private final DiscountCodeRepository discountCodeRepository;
    private final DiscountRedemptionRepository redemptionRepository;
    private final PricingService pricingService;
    private final RefundRepository refundRepository;
    private final BookingProperties properties;
    private final Clock clock;
    private final ZoneId businessZone;

    /** Holds the given seats for the caller for {@code app.booking.hold-duration}. All or nothing. */
    @Transactional
    public BookingResponse hold(Long userId, Long showId, List<Long> requestedSeatIds) {
        Set<Long> seatIds = new LinkedHashSet<>(requestedSeatIds);
        if (seatIds.size() != requestedSeatIds.size()) {
            throw new BadRequestException("DUPLICATE_SEATS", "The same seat was requested more than once");
        }
        if (seatIds.size() > properties.maxSeatsPerBooking()) {
            throw new BadRequestException("TOO_MANY_SEATS",
                    "At most " + properties.maxSeatsPerBooking() + " seats can be booked at once");
        }
        Instant now = clock.instant();
        Show show = showRepository.findById(showId).orElseThrow(() -> new ResourceNotFoundException("Show", showId));
        if (show.getStatus() != ShowStatus.SCHEDULED || !show.getStartTime().isAfter(now)) {
            throw new ConflictException("SHOW_NOT_BOOKABLE", "This show is no longer open for booking");
        }
        if (bookingRepository.existsActiveHold(userId, showId, now)) {
            throw new ConflictException("ACTIVE_HOLD_EXISTS",
                    "You already hold seats for this show; pay for or release that hold first");
        }

        // Serialization point: concurrent holds on any of these seats wait here.
        List<ShowSeat> seats = showSeatRepository.lockForHold(showId, seatIds);
        if (seats.size() != seatIds.size()) {
            throw new BadRequestException("SEAT_NOT_IN_SHOW", "One or more seats do not belong to this show");
        }
        List<String> taken = seats.stream().filter(s -> !s.isHoldable(now)).map(s -> s.getSeat().getLabel()).toList();
        if (!taken.isEmpty()) {
            throw new ConflictException("SEATS_UNAVAILABLE", "Seats no longer available: " + String.join(", ", taken));
        }

        // Seat prices are fixed at hold time: base price per seat type, then the active pricing rules.
        Map<SeatType, BigDecimal> basePrices = new EnumMap<>(SeatType.class);
        showPriceRepository.findByShowId(showId).forEach(p -> basePrices.put(p.getSeatType(), p.getPrice()));
        PricingService.Quote quote = pricingService.quote(show.getStartTime(), basePrices);
        BigDecimal subtotal = seats.stream().map(s -> quote.effectivePrices().get(s.getSeat().getSeatType()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        User user = userRepository.getReferenceById(userId);
        Instant expiresAt = now.plus(properties.holdDuration());
        String rules = quote.appliedRules().isEmpty() ? null : String.join(", ", quote.appliedRules());
        Booking booking = bookingRepository.save(new Booking(user, show, expiresAt, subtotal, rules));
        for (ShowSeat seat : seats) {
            // A seat whose previous hold expired but was not swept yet is simply taken over; the sweeper only
            // releases seats still pointing at the expired booking, so it cannot undo this hold.
            seat.hold(booking, expiresAt);
            SeatType type = seat.getSeat().getSeatType();
            bookingSeatRepository.save(new BookingSeat(booking, seat, basePrices.get(type),
                    quote.effectivePrices().get(type)));
        }
        log.info("Booking {} held {} seat(s) of show {} for user {} until {}", booking.getId(), seats.size(), showId,
                userId, expiresAt);
        return get(booking.getId());
    }

    /** Lets the customer give up a hold before paying. */
    @Transactional
    public BookingResponse release(AppUserPrincipal caller, Long bookingId) {
        Booking booking = getOwned(caller, bookingId);
        Instant now = clock.instant();
        if (booking.getStatus() != BookingStatus.HELD) {
            throw new ConflictException("BOOKING_NOT_HELD", "Only a held booking can be released (status: "
                    + booking.getStatus() + ")");
        }
        if (bookingRepository.endHold(bookingId, BookingStatus.RELEASED, now) == 1) {
            showSeatRepository.releaseHeldByBooking(bookingId, now);
        }
        return get(bookingId);
    }

    /**
     * Applies a discount code to a held booking (replacing any previous one). The code is validated now; its
     * usage count is consumed only when the booking is confirmed, under a row lock on the code.
     */
    @Transactional
    public BookingResponse applyDiscount(AppUserPrincipal caller, Long bookingId, String rawCode) {
        Booking booking = requireActiveHold(getOwned(caller, bookingId));
        String code = DiscountCodeService.normalize(rawCode);
        DiscountCode discount = discountCodeRepository.findByCode(code)
                .orElseThrow(() -> new BadRequestException("DISCOUNT_NOT_FOUND", "Unknown discount code: " + code));
        long userUses = redemptionRepository.countByDiscountCodeIdAndUserId(discount.getId(), booking.getUser().getId());
        DiscountCalculator.validate(discount, booking.getSubtotalAmount(), clock.instant(), userUses);
        booking.applyDiscount(discount, DiscountCalculator.discountFor(discount, booking.getSubtotalAmount()));
        bookingRepository.flush();
        return get(bookingId);
    }

    @Transactional
    public BookingResponse removeDiscount(AppUserPrincipal caller, Long bookingId) {
        Booking booking = requireActiveHold(getOwned(caller, bookingId));
        booking.removeDiscount();
        bookingRepository.flush();
        return get(bookingId);
    }

    @Transactional(readOnly = true)
    public BookingResponse getForCaller(AppUserPrincipal caller, Long bookingId) {
        getOwned(caller, bookingId);
        return get(bookingId);
    }

    /**
     * Expires one overdue hold and frees its seats. Returns false if the booking was no longer HELD
     * (e.g. confirmed or released concurrently) — the conditional update decides the winner.
     */
    @Transactional
    public boolean expireHold(Long bookingId) {
        Instant now = clock.instant();
        if (bookingRepository.expireIfDue(bookingId, now) == 0) {
            return false;
        }
        int seats = showSeatRepository.releaseHeldByBooking(bookingId, now);
        log.info("Booking {} expired, released {} seat(s)", bookingId, seats);
        return true;
    }

    @Transactional(readOnly = true)
    public BookingResponse get(Long bookingId) {
        Booking b = bookingRepository.findDetailedById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        Show show = b.getShow();
        List<BookingResponse.SeatLine> seats = bookingSeatRepository.findByBookingId(bookingId).stream()
                .map(bs -> new BookingResponse.SeatLine(bs.getShowSeat().getId(), bs.getShowSeat().getSeat().getLabel(),
                        bs.getShowSeat().getSeat().getSeatType(), bs.getBasePrice(), bs.getPrice()))
                .toList();
        // A hold past its expiry is reported as EXPIRED even before the sweeper has run.
        BookingStatus status = b.isHoldExpired(clock.instant()) ? BookingStatus.EXPIRED : b.getStatus();
        return new BookingResponse(b.getId(), status,
                new BookingResponse.ShowInfo(show.getId(), show.getMovie().getTitle(),
                        show.getScreen().getTheater().getName(), show.getScreen().getName(), zoned(show.getStartTime())),
                seats,
                b.getAppliedPricingRules() == null ? List.of() : List.of(b.getAppliedPricingRules().split(", ")),
                b.getSubtotalAmount(), b.getDiscountCode() == null ? null : b.getDiscountCode().getCode(),
                b.getDiscountAmount(), b.getTotalAmount(), zoned(b.getHoldExpiresAt()),
                b.getConfirmedAt() == null ? null : zoned(b.getConfirmedAt()),
                b.getCancelledAt() == null ? null : zoned(b.getCancelledAt()),
                refundRepository.findByBookingId(bookingId)
                        .map(r -> new BookingResponse.RefundInfo(r.getAmount(), r.getRefundPercent(), r.getReason(),
                                r.getPolicyName()))
                        .orElse(null),
                zoned(b.getCreatedAt()));
    }

    private Booking requireActiveHold(Booking booking) {
        if (booking.getStatus() != BookingStatus.HELD) {
            throw new ConflictException("BOOKING_NOT_HELD", "Only a held booking can be changed (status: "
                    + booking.getStatus() + ")");
        }
        if (booking.isHoldExpired(clock.instant())) {
            throw new ConflictException("HOLD_EXPIRED", "The seat hold has expired; hold the seats again");
        }
        return booking;
    }

    /** Customers see only their own bookings; admins see all. Others get 404 so ids cannot be probed. */
    private Booking getOwned(AppUserPrincipal caller, Long bookingId) {
        Booking booking = bookingRepository.findDetailedById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        if (!caller.isAdmin() && !booking.getUser().getId().equals(caller.id())) {
            throw new ResourceNotFoundException("Booking", bookingId);
        }
        return booking;
    }

    private OffsetDateTime zoned(Instant instant) {
        return instant.atZone(businessZone).toOffsetDateTime();
    }
}
