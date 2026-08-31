-- One subscriber so a fresh checkout actually delivers something.
--
-- Without this the pipeline looks broken on first run: events arrive and are
-- stored, but the recipient query returns nothing and no email is ever sent.
-- Previously nothing seeded the table at all.
INSERT INTO subscriber (full_name, email, notification_enabled, created_at)
VALUES ('Asteroid Watch (dev)', 'dev@asteroid.local', 1, UTC_TIMESTAMP(6));
