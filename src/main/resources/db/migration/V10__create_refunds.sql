-- Refund policy = time bands before the show start. The band with the largest min_hours_before_show that the
-- cancellation still meets decides the refund percent; below every band the refund is 0%.
CREATE TABLE refund_policies (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(100) NOT NULL,
    active     BOOLEAN      NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    CONSTRAINT pk_refund_policies PRIMARY KEY (id),
    CONSTRAINT uk_refund_policies_name UNIQUE (name)
);

CREATE TABLE refund_policy_rules (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    policy_id             BIGINT        NOT NULL,
    min_hours_before_show INT           NOT NULL,
    refund_percent        DECIMAL(5, 2) NOT NULL,
    created_at            DATETIME(6)   NOT NULL,
    updated_at            DATETIME(6)   NOT NULL,
    CONSTRAINT pk_refund_policy_rules PRIMARY KEY (id),
    CONSTRAINT fk_refund_rules_policy FOREIGN KEY (policy_id) REFERENCES refund_policies (id),
    CONSTRAINT uk_refund_rules_policy_hours UNIQUE (policy_id, min_hours_before_show)
);

-- At most one refund per booking (the cancellation)
CREATE TABLE refunds (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    booking_id        BIGINT         NOT NULL,
    payment_id        BIGINT,
    amount            DECIMAL(10, 2) NOT NULL,
    refund_percent    DECIMAL(5, 2)  NOT NULL,
    reason            VARCHAR(30)    NOT NULL,
    policy_name       VARCHAR(100),
    gateway_reference VARCHAR(100),
    created_at        DATETIME(6)    NOT NULL,
    updated_at        DATETIME(6)    NOT NULL,
    CONSTRAINT pk_refunds PRIMARY KEY (id),
    CONSTRAINT fk_refunds_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT uk_refunds_booking UNIQUE (booking_id)
);

-- The policy in force when the booking was paid (so later policy changes never apply retroactively)
ALTER TABLE bookings ADD COLUMN refund_policy_id BIGINT NULL;
ALTER TABLE bookings ADD COLUMN cancelled_at DATETIME(6) NULL;
ALTER TABLE bookings ADD CONSTRAINT fk_bookings_refund_policy FOREIGN KEY (refund_policy_id) REFERENCES refund_policies (id);

ALTER TABLE shows ADD COLUMN cancellation_reason VARCHAR(255) NULL;
