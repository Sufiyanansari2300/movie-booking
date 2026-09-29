-- A booking starts as a time-bound HOLD on seats and later becomes CONFIRMED (payment) or ends as
-- RELEASED (customer), EXPIRED (hold timed out) or CANCELLED (after confirmation).
CREATE TABLE bookings (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    user_id         BIGINT         NOT NULL,
    show_id         BIGINT         NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    hold_expires_at DATETIME(6)    NOT NULL,
    total_amount    DECIMAL(10, 2) NOT NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      DATETIME(6)    NOT NULL,
    updated_at      DATETIME(6)    NOT NULL,
    CONSTRAINT pk_bookings PRIMARY KEY (id),
    CONSTRAINT fk_bookings_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_bookings_show FOREIGN KEY (show_id) REFERENCES shows (id)
);
CREATE INDEX idx_bookings_status_expiry ON bookings (status, hold_expires_at);
CREATE INDEX idx_bookings_user ON bookings (user_id, created_at);
CREATE INDEX idx_bookings_show_user_status ON bookings (show_id, user_id, status);

-- Seats in a booking with the price charged for each. A show seat can appear in several bookings
-- over time (e.g. an expired hold, then a new one), so it is only unique within a booking.
CREATE TABLE booking_seats (
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    booking_id   BIGINT         NOT NULL,
    show_seat_id BIGINT         NOT NULL,
    price        DECIMAL(10, 2) NOT NULL,
    created_at   DATETIME(6)    NOT NULL,
    updated_at   DATETIME(6)    NOT NULL,
    CONSTRAINT pk_booking_seats PRIMARY KEY (id),
    CONSTRAINT fk_booking_seats_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_booking_seats_show_seat FOREIGN KEY (show_seat_id) REFERENCES show_seats (id),
    CONSTRAINT uk_booking_seats_booking_seat UNIQUE (booking_id, show_seat_id)
);

-- Current holder of a show seat while it is HELD or BOOKED
ALTER TABLE show_seats ADD COLUMN booking_id BIGINT NULL;
ALTER TABLE show_seats ADD COLUMN hold_expires_at DATETIME(6) NULL;
ALTER TABLE show_seats ADD CONSTRAINT fk_show_seats_booking FOREIGN KEY (booking_id) REFERENCES bookings (id);
