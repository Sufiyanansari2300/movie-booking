package com.sufiyan.moviebooking.repository;

import com.sufiyan.moviebooking.entity.Screen;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScreenRepository extends JpaRepository<Screen, Long> {

    @EntityGraph(attributePaths = "theater")
    Optional<Screen> findWithTheaterById(Long id);

    @EntityGraph(attributePaths = "theater")
    List<Screen> findByTheaterIdOrderByNameAsc(Long theaterId);

    boolean existsByTheaterId(Long theaterId);

    boolean existsByTheaterIdAndNameIgnoreCase(Long theaterId, String name);

    boolean existsByTheaterIdAndNameIgnoreCaseAndIdNot(Long theaterId, String name, Long id);
}
