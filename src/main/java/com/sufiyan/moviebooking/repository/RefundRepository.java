package com.sufiyan.moviebooking.repository;

import com.sufiyan.moviebooking.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByBookingId(Long bookingId);
}
