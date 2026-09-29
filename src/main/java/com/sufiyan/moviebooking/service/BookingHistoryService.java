package com.sufiyan.moviebooking.service;

import com.sufiyan.moviebooking.dto.BookingSummary;
import com.sufiyan.moviebooking.dto.PageResponse;
import com.sufiyan.moviebooking.dto.ShowSalesSummary;
import com.sufiyan.moviebooking.entity.Booking;
import com.sufiyan.moviebooking.entity.BookingStatus;
import com.sufiyan.moviebooking.entity.Refund;
import com.sufiyan.moviebooking.entity.Show;
import com.sufiyan.moviebooking.entity.ShowSeatStatus;
import com.sufiyan.moviebooking.exception.ResourceNotFoundException;
import com.sufiyan.moviebooking.repository.BookingRepository;
import com.sufiyan.moviebooking.repository.BookingSeatRepository;
import com.sufiyan.moviebooking.repository.BookingSpecifications;
import com.sufiyan.moviebooking.repository.BookingSpecifications.When;
import com.sufiyan.moviebooking.repository.PaymentRepository;
import com.sufiyan.moviebooking.repository.RefundRepository;
import com.sufiyan.moviebooking.repository.ShowRepository;
import com.sufiyan.moviebooking.repository.ShowSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Booking history for customers and booking search / show sales for admins. */
@Service
@RequiredArgsConstructor
public class BookingHistoryService {

    private static final Set<String> SORTABLE = Set.of("createdAt", "confirmedAt", "show.startTime", "totalAmount");

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final RefundRepository refundRepository;
    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final PaymentRepository paymentRepository;
    private final Clock clock;
    private final ZoneId businessZone;

    public record Filter(BookingStatus status, When when, Long showId, Long userId, String email) {
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingSummary> mine(Long userId, BookingStatus status, When when, Pageable pageable) {
        return search(new Filter(status, when, null, userId, null), pageable);
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingSummary> search(Filter filter, Pageable pageable) {
        SortValidator.requireAllowed(pageable, SORTABLE);
        Instant now = clock.instant();
        Specification<Booking> spec = Specification.allOf(
                BookingSpecifications.user(filter.userId()),
                BookingSpecifications.userEmail(filter.email()),
                BookingSpecifications.show(filter.showId()),
                BookingSpecifications.effectiveStatus(filter.status(), now),
                BookingSpecifications.when(filter.when(), now));
        Page<Booking> page = bookingRepository.findAll(spec, pageable);
        List<Long> ids = page.getContent().stream().map(Booking::getId).toList();

        // Two batched queries for the whole page instead of two per booking
        Map<Long, List<String>> seats = ids.isEmpty() ? Map.of() : bookingSeatRepository.findByBookingIdIn(ids).stream()
                .collect(Collectors.groupingBy(bs -> bs.getBooking().getId(),
                        Collectors.mapping(bs -> bs.getShowSeat().getSeat().getLabel(), Collectors.toList())));
        Map<Long, BigDecimal> refunds = ids.isEmpty() ? Map.of() : refundRepository.findByBookingIdIn(ids).stream()
                .collect(Collectors.toMap(r -> r.getBooking().getId(), Refund::getAmount));

        return PageResponse.from(page, b -> toSummary(b, seats.getOrDefault(b.getId(), List.of()),
                refunds.get(b.getId()), now));
    }

    @Transactional(readOnly = true)
    public ShowSalesSummary showSales(Long showId) {
        Show show = showRepository.findById(showId).orElseThrow(() -> new ResourceNotFoundException("Show", showId));
        Instant now = clock.instant();
        long total = showSeatRepository.countByShowId(showId);
        long booked = showSeatRepository.countByShowIdAndStatus(showId, ShowSeatStatus.BOOKED);
        long available = showSeatRepository.countHoldableByShow(List.of(showId), now).stream()
                .mapToLong(r -> (Long) r[1]).sum();
        long held = total - booked - available;
        BigDecimal gross = paymentRepository.sumSucceededByShow(showId);
        BigDecimal refunded = refundRepository.sumByShow(showId);
        double occupancy = total == 0 ? 0 : BigDecimal.valueOf(booked * 100.0 / total)
                .setScale(1, RoundingMode.HALF_UP).doubleValue();
        return new ShowSalesSummary(showId, show.getStatus(), total, booked, held, available, occupancy,
                bookingRepository.countByShowIdAndStatus(showId, BookingStatus.CONFIRMED),
                bookingRepository.countByShowIdAndStatus(showId, BookingStatus.CANCELLED),
                gross.setScale(2, RoundingMode.HALF_UP), refunded.setScale(2, RoundingMode.HALF_UP),
                gross.subtract(refunded).setScale(2, RoundingMode.HALF_UP));
    }

    private BookingSummary toSummary(Booking b, List<String> seats, BigDecimal refund, Instant now) {
        Show show = b.getShow();
        return new BookingSummary(b.getId(), b.isHoldExpired(now) ? BookingStatus.EXPIRED : b.getStatus(),
                show.getId(), show.getMovie().getTitle(), show.getScreen().getTheater().getName(),
                show.getScreen().getTheater().getCity().getName(), show.getScreen().getName(),
                show.getStartTime().atZone(businessZone).toOffsetDateTime(), seats, b.getTotalAmount(),
                b.getDiscountCode() == null ? null : b.getDiscountCode().getCode(), refund, b.getUser().getEmail(),
                b.getCreatedAt().atZone(businessZone).toOffsetDateTime());
    }
}
