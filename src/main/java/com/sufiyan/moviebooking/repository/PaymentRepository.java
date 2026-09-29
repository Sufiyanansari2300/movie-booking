package com.sufiyan.moviebooking.repository;

import com.sufiyan.moviebooking.entity.Payment;
import com.sufiyan.moviebooking.entity.PaymentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @EntityGraph(attributePaths = "booking")
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findByBookingIdOrderByCreatedAtAsc(Long bookingId);

    long countByBookingId(Long bookingId);

    Optional<Payment> findFirstByBookingIdAndStatus(Long bookingId, PaymentStatus status);
}
