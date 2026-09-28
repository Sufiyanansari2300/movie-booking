package com.sufiyan.moviebooking.repository;

import com.sufiyan.moviebooking.entity.Show;
import com.sufiyan.moviebooking.entity.ShowStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ShowRepository extends JpaRepository<Show, Long> {

    @EntityGraph(attributePaths = {"movie", "screen", "screen.theater", "screen.theater.city"})
    Optional<Show> findDetailedById(Long id);

    /** True if a scheduled show on the screen overlaps [start, end). Back-to-back shows do not overlap. */
    @Query("""
            select count(s) > 0 from Show s
            where s.screen.id = :screenId and s.status = com.sufiyan.moviebooking.entity.ShowStatus.SCHEDULED
              and s.startTime < :end and s.endTime > :start
            """)
    boolean existsOverlapping(@Param("screenId") Long screenId, @Param("start") Instant start, @Param("end") Instant end);

    boolean existsByScreenId(Long screenId);

    boolean existsByMovieId(Long movieId);

    /** Upcoming shows with optional filters; all filters null = everything upcoming. */
    @EntityGraph(attributePaths = {"movie", "screen", "screen.theater", "screen.theater.city"})
    @Query(value = """
            select s from Show s
            where s.status = :status and s.startTime > :now
              and (:cityId is null or s.screen.theater.city.id = :cityId)
              and (:theaterId is null or s.screen.theater.id = :theaterId)
              and (:movieId is null or s.movie.id = :movieId)
              and (:from is null or s.startTime >= :from)
              and (:to is null or s.startTime < :to)
            """,
            countQuery = """
            select count(s) from Show s
            where s.status = :status and s.startTime > :now
              and (:cityId is null or s.screen.theater.city.id = :cityId)
              and (:theaterId is null or s.screen.theater.id = :theaterId)
              and (:movieId is null or s.movie.id = :movieId)
              and (:from is null or s.startTime >= :from)
              and (:to is null or s.startTime < :to)
            """)
    Page<Show> search(@Param("status") ShowStatus status, @Param("now") Instant now,
                      @Param("cityId") Long cityId, @Param("theaterId") Long theaterId, @Param("movieId") Long movieId,
                      @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);
}
