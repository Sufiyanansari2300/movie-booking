package com.sufiyan.moviebooking.repository;

import com.sufiyan.moviebooking.entity.DiscountCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DiscountCodeRepository extends JpaRepository<DiscountCode, Long> {

    Optional<DiscountCode> findByCode(String code);

    boolean existsByCode(String code);

    List<DiscountCode> findAllByOrderByCodeAsc();

    /** Locks the code row so usage-limit checks and increments at confirmation are serialized. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DiscountCode d where d.id = :id")
    Optional<DiscountCode> findByIdForUpdate(@Param("id") Long id);
}
