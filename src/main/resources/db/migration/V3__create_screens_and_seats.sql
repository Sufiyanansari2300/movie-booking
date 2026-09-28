CREATE TABLE screens (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    theater_id BIGINT      NOT NULL,
    name       VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_screens PRIMARY KEY (id),
    CONSTRAINT fk_screens_theater FOREIGN KEY (theater_id) REFERENCES theaters (id),
    CONSTRAINT uk_screens_theater_name UNIQUE (theater_id, name)
);

-- Physical seat layout of a screen. Per-show availability is tracked separately (show_seats, phase 3).
CREATE TABLE seats (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    screen_id   BIGINT      NOT NULL,
    row_label   VARCHAR(3)  NOT NULL,
    seat_number INT         NOT NULL,
    seat_type   VARCHAR(20) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    CONSTRAINT pk_seats PRIMARY KEY (id),
    CONSTRAINT fk_seats_screen FOREIGN KEY (screen_id) REFERENCES screens (id),
    CONSTRAINT uk_seats_screen_row_number UNIQUE (screen_id, row_label, seat_number)
);
