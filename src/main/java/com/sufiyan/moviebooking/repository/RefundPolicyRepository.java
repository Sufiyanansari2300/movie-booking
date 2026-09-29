package com.sufiyan.moviebooking.repository;

import com.sufiyan.moviebooking.entity.RefundPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefundPolicyRepository extends JpaRepository<RefundPolicy, Long> {

    Optional<RefundPolicy> findFirstByActiveTrue();

    List<RefundPolicy> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    List<RefundPolicy> findByActiveTrue();
}
