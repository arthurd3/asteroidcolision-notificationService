-- Supports the history page, which orders by created_at desc and pages through it.
-- Without this index that ordering is a filesort over the whole table on every page
-- view, and the table only grows: one row per hazardous approach per scan, forever.
CREATE INDEX ix_notification_created_at ON notification (created_at);

-- Deliberately NOT adding an index on notification_delivery (notification_id) for
-- the grouped count query. uk_delivery_notification_subscriber is already
-- (notification_id, subscriber_id), and MySQL uses its leftmost prefix for a
-- `where notification_id in (...)` lookup. A second index would be another copy of
-- the same B-tree to keep up to date on every insert.
