package com.sufiyan.moviebooking.repository;

import com.sufiyan.moviebooking.entity.City;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CityRepository extends JpaRepository<City, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    List<City> findAllByOrderByNameAsc();
}
