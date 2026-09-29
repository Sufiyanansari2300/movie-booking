-- Every payment attempt (successful or not). A booking may have several failed attempts and at most one success,
-- which is guaranteed by the booking's HELD -> CONFIRMED transition happening under a row lock.
CREATE TABLE payments (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    booking_id        BIGINT         NOT NULL,
    user_id           BIGINT         NOT NULL,
    amount            DECIMAL(10, 2) NOT NULL,
    currency          VARCHAR(3)     NOT NULL,
    method            VARCHAR(20)    NOT NULL,
    status            VARCHAR(20)    NOT NULL,
    gateway_reference VARCHAR(100),
    failure_code      VARCHAR(50),
    failure_reason    VARCHAR(255),
    -- Client-supplied key; retrying with the same key returns this attempt instead of charging again
    idempotency_key   VARCHAR(100)   NOT NULL,
    created_at        DATETIME(6)    NOT NULL,
    updated_at        DATETIME(6)    NOT NULL,
    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT fk_payments_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_payments_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_payments_idempotency_key UNIQUE (idempotency_key)
);
CREATE INDEX idx_payments_booking ON payments (booking_id, created_at);

ALTER TABLE bookings ADD COLUMN confirmed_at DATETIME(6) NULL;
