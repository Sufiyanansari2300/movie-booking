package com.sufiyan.moviebooking.repository;

import com.sufiyan.moviebooking.entity.Theater;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TheaterRepository extends JpaRepository<Theater, Long> {

    @EntityGraph(attributePaths = "city")
    Optional<Theater> findWithCityById(Long id);

    @EntityGraph(attributePaths = "city")
    List<Theater> findByCityIdOrderByNameAsc(Long cityId);

    boolean existsByCityId(Long cityId);

    boolean existsByCityIdAndNameIgnoreCase(Long cityId, String name);

    boolean existsByCityIdAndNameIgnoreCaseAndIdNot(Long cityId, String name, Long id);
}
