package com.sufiyan.moviebooking.repository;

import com.sufiyan.moviebooking.entity.Booking;
import com.sufiyan.moviebooking.entity.BookingStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Locale;

/** Composable filters for booking history and admin search. A null argument means "no filter". */
public final class BookingSpecifications {

    public enum When { UPCOMING, PAST }

    private BookingSpecifications() {
    }

    public static Specification<Booking> user(Long userId) {
        return (root, q, cb) -> userId == null ? null : cb.equal(root.get("user").get("id"), userId);
    }

    public static Specification<Booking> userEmail(String email) {
        return (root, q, cb) -> email == null ? null
                : cb.equal(root.get("user").get("email"), email.trim().toLowerCase(Locale.ROOT));
    }

    public static Specification<Booking> show(Long showId) {
        return (root, q, cb) -> showId == null ? null : cb.equal(root.get("show").get("id"), showId);
    }

    /**
     * Filters by the status customers see: a HELD booking whose hold has run out counts as EXPIRED even before
     * the sweeper has updated it.
     */
    public static Specification<Booking> effectiveStatus(BookingStatus status, Instant now) {
        return (root, q, cb) -> {
            if (status == null) {
                return null;
            }
            var stored = root.<BookingStatus>get("status");
            var expiresAt = root.<Instant>get("holdExpiresAt");
            return switch (status) {
                case HELD -> cb.and(cb.equal(stored, BookingStatus.HELD), cb.greaterThan(expiresAt, now));
                case EXPIRED -> cb.or(cb.equal(stored, BookingStatus.EXPIRED),
                        cb.and(cb.equal(stored, BookingStatus.HELD), cb.lessThanOrEqualTo(expiresAt, now)));
                default -> cb.equal(stored, status);
            };
        };
    }

    public static Specification<Booking> when(When when, Instant now) {
        return (root, q, cb) -> {
            if (when == null) {
                return null;
            }
            var start = root.get("show").<Instant>get("startTime");
            return when == When.UPCOMING ? cb.greaterThan(start, now) : cb.lessThanOrEqualTo(start, now);
        };
    }
}
