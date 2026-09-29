package com.sufiyan.moviebooking.repository;

import com.sufiyan.moviebooking.entity.Booking;
import com.sufiyan.moviebooking.entity.BookingStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @EntityGraph(attributePaths = {"user", "show", "show.movie", "show.screen", "show.screen.theater", "discountCode"})
    Optional<Booking> findDetailedById(Long id);

    boolean existsByShowId(Long showId);

    boolean existsByDiscountCodeId(Long discountCodeId);

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
