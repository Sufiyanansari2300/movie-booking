package com.sufiyan.moviebooking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "seats")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seat extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "screen_id", nullable = false)
    private Screen screen;

    @Column(name = "row_label", nullable = false, length = 3)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false)
    private int seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_type", nullable = false, length = 20)
    private SeatType seatType;

    public Seat(Screen screen, String rowLabel, int seatNumber, SeatType seatType) {
        this.screen = screen;
        this.rowLabel = rowLabel;
        this.seatNumber = seatNumber;
        this.seatType = seatType;
    }

    /** Human-readable seat label, e.g. "A12". */
    public String getLabel() {
        return rowLabel + seatNumber;
    }
}
