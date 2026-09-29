package com.sufiyan.moviebooking.repository;

import com.sufiyan.moviebooking.entity.Payment;
import com.sufiyan.moviebooking.entity.PaymentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @EntityGraph(attributePaths = "booking")
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findByBookingIdOrderByCreatedAtAsc(Long bookingId);

    long countByBookingId(Long bookingId);

    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
            where p.booking.show.id = :showId and p.status = com.sufiyan.moviebooking.entity.PaymentStatus.SUCCEEDED
            """)
    BigDecimal sumSucceededByShow(@Param("showId") Long showId);

    Optional<Payment> findFirstByBookingIdAndStatus(Long bookingId, PaymentStatus status);
}
