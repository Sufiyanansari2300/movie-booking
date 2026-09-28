package com.sufiyan.moviebooking.repository;

import com.sufiyan.moviebooking.entity.ShowSeat;
import com.sufiyan.moviebooking.entity.ShowSeatStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {

    @EntityGraph(attributePaths = "seat")
    @Query("select ss from ShowSeat ss where ss.show.id = :showId order by ss.seat.rowLabel, ss.seat.seatNumber")
    List<ShowSeat> findSeatMap(@Param("showId") Long showId);

    boolean existsByShowIdAndStatusNot(Long showId, ShowSeatStatus status);

    /** [showId, count] of seats in the given status for each show. */
    @Query("select ss.show.id, count(ss) from ShowSeat ss where ss.show.id in :showIds and ss.status = :status group by ss.show.id")
    List<Object[]> countByShowAndStatus(@Param("showIds") Collection<Long> showIds, @Param("status") ShowSeatStatus status);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ShowSeat ss where ss.show.id = :showId")
    int deleteByShowId(@Param("showId") Long showId);
}
