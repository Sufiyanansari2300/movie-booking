package com.sufiyan.moviebooking.repository;

import com.sufiyan.moviebooking.entity.ShowSeat;
import com.sufiyan.moviebooking.entity.ShowSeatStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {

    @EntityGraph(attributePaths = "seat")
    @Query("select ss from ShowSeat ss where ss.show.id = :showId order by ss.seat.rowLabel, ss.seat.seatNumber")
    List<ShowSeat> findSeatMap(@Param("showId") Long showId);

    boolean existsByShowIdAndStatusNot(Long showId, ShowSeatStatus status);

    /**
     * Row-locks the requested seats of a show ({@code SELECT ... FOR UPDATE}). Locks are taken in id order so
     * two overlapping requests always lock in the same sequence and cannot deadlock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select ss from ShowSeat ss join fetch ss.seat where ss.show.id = :showId and ss.id in :ids order by ss.id")
    List<ShowSeat> lockForHold(@Param("showId") Long showId, @Param("ids") Collection<Long> ids);

    /** [showId, count] of seats a customer can hold right now (AVAILABLE or an expired hold). */
    @Query("""
            select ss.show.id, count(ss) from ShowSeat ss
            where ss.show.id in :showIds
              and (ss.status = com.sufiyan.moviebooking.entity.ShowSeatStatus.AVAILABLE
                   or (ss.status = com.sufiyan.moviebooking.entity.ShowSeatStatus.HELD and ss.holdExpiresAt <= :now))
            group by ss.show.id
            """)
    List<Object[]> countHoldableByShow(@Param("showIds") Collection<Long> showIds, @Param("now") Instant now);

    /** Seats currently tied to a booking in the given status (at most max-seats-per-booking rows). */
    List<ShowSeat> findByBookingIdAndStatus(Long bookingId, ShowSeatStatus status);

    /**
     * Frees seats still held by the given booking. Scoped to that booking so it never touches a seat that
     * someone else has held since (after this booking's hold expired). Bumps version for optimistic locking.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ShowSeat ss set ss.status = com.sufiyan.moviebooking.entity.ShowSeatStatus.AVAILABLE,
                                   ss.booking = null, ss.holdExpiresAt = null,
                                   ss.version = ss.version + 1, ss.updatedAt = :now
            where ss.booking.id = :bookingId and ss.status = com.sufiyan.moviebooking.entity.ShowSeatStatus.HELD
            """)
    int releaseHeldByBooking(@Param("bookingId") Long bookingId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ShowSeat ss where ss.show.id = :showId")
    int deleteByShowId(@Param("showId") Long showId);
}
