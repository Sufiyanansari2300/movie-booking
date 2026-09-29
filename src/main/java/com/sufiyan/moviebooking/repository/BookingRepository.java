package com.sufiyan.moviebooking.repository;

import com.sufiyan.moviebooking.entity.Booking;
import com.sufiyan.moviebooking.entity.BookingStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking> {

    /** Specification search that also fetches what history rows display (avoids N+1). */
    @Override
    @EntityGraph(attributePaths = {"user", "show", "show.movie", "show.screen", "show.screen.theater",
            "show.screen.theater.city", "discountCode"})
    Page<Booking> findAll(Specification<Booking> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "show", "show.movie", "show.screen", "show.screen.theater", "discountCode"})
    Optional<Booking> findDetailedById(Long id);

    /** Locks the booking row; serializes payment attempts, confirmation and expiry of one booking. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);

    boolean existsByShowId(Long showId);

    /** Confirmed bookings whose show starts in (from, to] and that have not been reminded yet. */
    @Query("""
            select b.id from Booking b
            where b.status = com.sufiyan.moviebooking.entity.BookingStatus.CONFIRMED
              and b.show.status = com.sufiyan.moviebooking.entity.ShowStatus.SCHEDULED
              and b.show.startTime > :from and b.show.startTime <= :to
              and not exists (select n.id from Notification n where n.booking = b
                              and n.type = com.sufiyan.moviebooking.entity.NotificationType.SHOW_REMINDER)
            order by b.show.startTime
            """)
    List<Long> findDueForReminder(@Param("from") Instant from, @Param("to") Instant to, Limit limit);

    boolean existsByDiscountCodeId(Long discountCodeId);

    boolean existsByRefundPolicyId(Long refundPolicyId);

    long countByShowIdAndStatus(Long showId, BookingStatus status);

    /** Bookings of a show still to be processed by a show cancellation (confirmed ones and open holds). */
    @Query("""
            select b.id from Booking b where b.show.id = :showId
              and b.status in (com.sufiyan.moviebooking.entity.BookingStatus.CONFIRMED,
                               com.sufiyan.moviebooking.entity.BookingStatus.HELD)
            order by b.id
            """)
    List<Long> findOpenIdsByShow(@Param("showId") Long showId);

    /** An unexpired hold of this user on this show (one active hold per user per show). */
    @Query("""
            select count(b) > 0 from Booking b
            where b.user.id = :userId and b.show.id = :showId
              and b.status = com.sufiyan.moviebooking.entity.BookingStatus.HELD and b.holdExpiresAt > :now
            """)
    boolean existsActiveHold(@Param("userId") Long userId, @Param("showId") Long showId, @Param("now") Instant now);

    @Query("select b.id from Booking b where b.status = :status and b.holdExpiresAt <= :now order by b.holdExpiresAt")
    List<Long> findIdsByStatusAndExpiredBefore(@Param("status") BookingStatus status, @Param("now") Instant now, Limit limit);

    /**
     * Moves a booking from HELD to a final status only if it is still HELD. The conditional update makes this
     * safe against a concurrent transition (e.g. confirmation): exactly one of them wins.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Booking b set b.status = :to, b.version = b.version + 1, b.updatedAt = :now
            where b.id = :id and b.status = com.sufiyan.moviebooking.entity.BookingStatus.HELD
            """)
    int endHold(@Param("id") Long id, @Param("to") BookingStatus to, @Param("now") Instant now);

    /** Same as {@link #endHold} but only for holds that have actually expired. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Booking b set b.status = com.sufiyan.moviebooking.entity.BookingStatus.EXPIRED,
                                 b.version = b.version + 1, b.updatedAt = :now
            where b.id = :id and b.status = com.sufiyan.moviebooking.entity.BookingStatus.HELD
              and b.holdExpiresAt <= :now
            """)
    int expireIfDue(@Param("id") Long id, @Param("now") Instant now);
}
