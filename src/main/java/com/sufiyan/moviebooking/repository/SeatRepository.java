package com.sufiyan.moviebooking.repository;

import com.sufiyan.moviebooking.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByScreenIdOrderByRowLabelAscSeatNumberAsc(Long screenId);

    long countByScreenId(Long screenId);

    /** Seat counts per screen of a theater, as [screenId, count] pairs (avoids N+1 counts). */
    @Query("select s.screen.id, count(s) from Seat s where s.screen.theater.id = :theaterId group by s.screen.id")
    List<Object[]> countByScreenForTheater(@Param("theaterId") Long theaterId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Seat s where s.screen.id = :screenId")
    int deleteByScreenId(@Param("screenId") Long screenId);
}
