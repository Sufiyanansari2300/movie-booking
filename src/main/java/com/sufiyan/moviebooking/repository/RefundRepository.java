package com.sufiyan.moviebooking.repository;

import com.sufiyan.moviebooking.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByBookingId(Long bookingId);

    List<Refund> findByBookingIdIn(Collection<Long> bookingIds);

    @Query("select coalesce(sum(r.amount), 0) from Refund r where r.booking.show.id = :showId")
    BigDecimal sumByShow(@Param("showId") Long showId);
}
