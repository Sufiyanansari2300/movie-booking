package com.sufiyan.moviebooking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Entity
@Table(name = "booking_seats")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookingSeat extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "show_seat_id", nullable = false)
    private ShowSeat showSeat;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    public BookingSeat(Booking booking, ShowSeat showSeat, BigDecimal price) {
        this.booking = booking;
        this.showSeat = showSeat;
        this.price = price;
    }
}
