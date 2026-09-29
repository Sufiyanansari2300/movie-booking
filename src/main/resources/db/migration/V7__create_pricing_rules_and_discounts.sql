-- Surcharges/reductions on top of a show's base prices. Applicable rules are summed.
CREATE TABLE pricing_rules (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    name               VARCHAR(100)  NOT NULL,
    rule_type          VARCHAR(20)   NOT NULL,
    adjustment_percent DECIMAL(6, 2) NOT NULL,
    -- PRIME_TIME window on the show's local start time: [window_start, window_end)
    window_start       TIME,
    window_end         TIME,
    active             BOOLEAN       NOT NULL,
    created_at         DATETIME(6)   NOT NULL,
    updated_at         DATETIME(6)   NOT NULL,
    CONSTRAINT pk_pricing_rules PRIMARY KEY (id),
    CONSTRAINT uk_pricing_rules_name UNIQUE (name)
);

CREATE TABLE discount_codes (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    code                VARCHAR(30)    NOT NULL,
    description         VARCHAR(255),
    discount_type       VARCHAR(20)    NOT NULL,
    discount_value      DECIMAL(10, 2) NOT NULL,
    max_discount_amount DECIMAL(10, 2),
    min_order_amount    DECIMAL(10, 2),
    valid_from          DATETIME(6),
    valid_until         DATETIME(6),
    usage_limit         INT,
    per_user_limit      INT,
    used_count          INT            NOT NULL DEFAULT 0,
    active              BOOLEAN        NOT NULL,
    version             BIGINT         NOT NULL DEFAULT 0,
    created_at          DATETIME(6)    NOT NULL,
    updated_at          DATETIME(6)    NOT NULL,
    CONSTRAINT pk_discount_codes PRIMARY KEY (id),
    CONSTRAINT uk_discount_codes_code UNIQUE (code)
);

-- One row per confirmed booking that used a code; the source of truth for per-user limits.
CREATE TABLE discount_redemptions (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    discount_code_id BIGINT         NOT NULL,
    booking_id       BIGINT         NOT NULL,
    user_id          BIGINT         NOT NULL,
    amount           DECIMAL(10, 2) NOT NULL,
    created_at       DATETIME(6)    NOT NULL,
    updated_at       DATETIME(6)    NOT NULL,
    CONSTRAINT pk_discount_redemptions PRIMARY KEY (id),
    CONSTRAINT fk_redemptions_code FOREIGN KEY (discount_code_id) REFERENCES discount_codes (id),
    CONSTRAINT fk_redemptions_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_redemptions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_redemptions_booking UNIQUE (booking_id)
);
CREATE INDEX idx_redemptions_code_user ON discount_redemptions (discount_code_id, user_id);

-- Price breakdown on bookings: subtotal (after pricing rules) - discount = total
ALTER TABLE bookings ADD COLUMN subtotal_amount DECIMAL(10, 2) NOT NULL DEFAULT 0;
ALTER TABLE bookings ADD COLUMN discount_amount DECIMAL(10, 2) NOT NULL DEFAULT 0;
ALTER TABLE bookings ADD COLUMN discount_code_id BIGINT NULL;
ALTER TABLE bookings ADD COLUMN applied_pricing_rules VARCHAR(255) NULL;
ALTER TABLE bookings ADD CONSTRAINT fk_bookings_discount_code FOREIGN KEY (discount_code_id) REFERENCES discount_codes (id);
UPDATE bookings SET subtotal_amount = total_amount;

-- Base price per seat next to the charged price
ALTER TABLE booking_seats ADD COLUMN base_price DECIMAL(10, 2) NOT NULL DEFAULT 0;
UPDATE booking_seats SET base_price = price;
