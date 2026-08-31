-- Baseline schema.
--
-- Previously the schema came entirely from hibernate ddl-auto=update, which
-- cannot be reviewed, cannot be rolled back, and never drops or safely alters
-- anything. The old docker init.sql created the database and no tables at all.

CREATE TABLE notification
(
    id                            BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id                      VARCHAR(64)    NOT NULL,
    asteroid_id                   VARCHAR(64)    NOT NULL,
    asteroid_name                 VARCHAR(255)   NOT NULL,
    close_approach_date           DATE           NOT NULL,
    miss_distance_kilometers      DECIMAL(20, 4) NOT NULL,
    estimated_diameter_avg_meters DOUBLE         NOT NULL,
    occurred_at                   DATETIME(6)    NOT NULL,
    created_at                    DATETIME(6)    NOT NULL,

    -- the idempotency key: makes Kafka re-delivery and overlapping producer
    -- scan windows a no-op rather than a duplicate row and a duplicate email
    CONSTRAINT uk_notification_event_id UNIQUE (event_id)
) ENGINE = InnoDB;

CREATE INDEX ix_notification_close_approach_date ON notification (close_approach_date);

-- "user" is reserved in MySQL 8, so this table is named for what it holds
CREATE TABLE subscriber
(
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name            VARCHAR(255) NOT NULL,
    email                VARCHAR(320) NOT NULL,
    notification_enabled TINYINT(1)   NOT NULL DEFAULT 1,
    created_at           DATETIME(6)  NOT NULL,

    CONSTRAINT uk_subscriber_email UNIQUE (email)
) ENGINE = InnoDB;

CREATE TABLE notification_delivery
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    notification_id BIGINT        NOT NULL,
    subscriber_id   BIGINT        NOT NULL,
    status          VARCHAR(16)   NOT NULL,
    attempts        INT           NOT NULL DEFAULT 0,
    sent_at         DATETIME(6)   NULL,
    last_error      VARCHAR(1000) NULL,
    created_at      DATETIME(6)   NOT NULL,

    CONSTRAINT fk_delivery_notification FOREIGN KEY (notification_id) REFERENCES notification (id),
    CONSTRAINT fk_delivery_subscriber FOREIGN KEY (subscriber_id) REFERENCES subscriber (id),
    -- one delivery per notification per subscriber, so a re-run cannot fan out twice
    CONSTRAINT uk_delivery_notification_subscriber UNIQUE (notification_id, subscriber_id)
) ENGINE = InnoDB;

-- the dispatch worker polls on status; without this it is a full table scan
CREATE INDEX ix_delivery_status ON notification_delivery (status, id);
