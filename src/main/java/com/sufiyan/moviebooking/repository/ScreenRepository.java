package com.sufiyan.moviebooking.repository;

import com.sufiyan.moviebooking.entity.Screen;
import org.springframework.data.jpa.repository.EntityGraph;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ScreenRepository extends JpaRepository<Screen, Long> {

    @EntityGraph(attributePaths = "theater")
    Optional<Screen> findWithTheaterById(Long id);

    @EntityGraph(attributePaths = "theater")
    List<Screen> findByTheaterIdOrderByNameAsc(Long theaterId);

    /** Locks the screen row so concurrent show scheduling on the same screen is serialized. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Screen s where s.id = :id")
    Optional<Screen> findByIdForUpdate(@Param("id") Long id);

    boolean existsByTheaterId(Long theaterId);

    boolean existsByTheaterIdAndNameIgnoreCase(Long theaterId, String name);

    boolean existsByTheaterIdAndNameIgnoreCaseAndIdNot(Long theaterId, String name, Long id);
}
