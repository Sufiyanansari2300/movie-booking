-- Outgoing customer notifications. Written before sending, so every message is traceable and retryable.
-- Unique (booking, type): a duplicate event or a re-run job can never send the same message twice.
CREATE TABLE notifications (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    booking_id  BIGINT        NOT NULL,
    user_id     BIGINT        NOT NULL,
    type        VARCHAR(30)   NOT NULL,
    channel     VARCHAR(20)   NOT NULL,
    recipient   VARCHAR(255)  NOT NULL,
    subject     VARCHAR(255)  NOT NULL,
    body        VARCHAR(2000) NOT NULL,
    status      VARCHAR(20)   NOT NULL,
    attempts    INT           NOT NULL DEFAULT 0,
    last_error  VARCHAR(255),
    sent_at     DATETIME(6),
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_notifications_booking_type UNIQUE (booking_id, type)
);
CREATE INDEX idx_notifications_status ON notifications (status, updated_at);
