package com.sufiyan.moviebooking.repository;

import com.sufiyan.moviebooking.entity.DiscountRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscountRedemptionRepository extends JpaRepository<DiscountRedemption, Long> {

    long countByDiscountCodeIdAndUserId(Long discountCodeId, Long userId);

    boolean existsByDiscountCodeId(Long discountCodeId);
}
