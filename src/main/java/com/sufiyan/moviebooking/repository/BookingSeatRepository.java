package com.sufiyan.moviebooking.repository;

import com.sufiyan.moviebooking.entity.BookingSeat;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookingSeatRepository extends JpaRepository<BookingSeat, Long> {

    @EntityGraph(attributePaths = {"showSeat", "showSeat.seat"})
    @Query("select bs from BookingSeat bs where bs.booking.id = :bookingId order by bs.showSeat.seat.rowLabel, bs.showSeat.seat.seatNumber")
    List<BookingSeat> findByBookingId(@Param("bookingId") Long bookingId);

    long countByBookingId(Long bookingId);
}
