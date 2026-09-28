CREATE TABLE shows (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    movie_id   BIGINT      NOT NULL,
    screen_id  BIGINT      NOT NULL,
    start_time DATETIME(6) NOT NULL,
    -- start + movie duration + cleanup buffer; used for overlap checks
    end_time   DATETIME(6) NOT NULL,
    status     VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_shows PRIMARY KEY (id),
    CONSTRAINT fk_shows_movie FOREIGN KEY (movie_id) REFERENCES movies (id),
    CONSTRAINT fk_shows_screen FOREIGN KEY (screen_id) REFERENCES screens (id)
);
CREATE INDEX idx_shows_screen_start ON shows (screen_id, start_time);
CREATE INDEX idx_shows_start ON shows (start_time);

-- Base ticket price per seat type for a show
CREATE TABLE show_prices (
    id         BIGINT         NOT NULL AUTO_INCREMENT,
    show_id    BIGINT         NOT NULL,
    seat_type  VARCHAR(20)    NOT NULL,
    price      DECIMAL(10, 2) NOT NULL,
    created_at DATETIME(6)    NOT NULL,
    updated_at DATETIME(6)    NOT NULL,
    CONSTRAINT pk_show_prices PRIMARY KEY (id),
    CONSTRAINT fk_show_prices_show FOREIGN KEY (show_id) REFERENCES shows (id),
    CONSTRAINT uk_show_prices_show_type UNIQUE (show_id, seat_type)
);

-- One row per physical seat per show: the unit that gets held and booked.
-- The unique constraint makes double allocation of a seat within a show impossible at the DB level.
CREATE TABLE show_seats (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    show_id    BIGINT      NOT NULL,
    seat_id    BIGINT      NOT NULL,
    status     VARCHAR(20) NOT NULL,
    version    BIGINT      NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_show_seats PRIMARY KEY (id),
    CONSTRAINT fk_show_seats_show FOREIGN KEY (show_id) REFERENCES shows (id),
    CONSTRAINT fk_show_seats_seat FOREIGN KEY (seat_id) REFERENCES seats (id),
    CONSTRAINT uk_show_seats_show_seat UNIQUE (show_id, seat_id)
);
CREATE INDEX idx_show_seats_show_status ON show_seats (show_id, status);
