-- V7: Allow nullable email so mobile-OTP-only users can register without providing email.
-- The unique constraint is replaced with a partial index that excludes NULLs.

ALTER TABLE identity.users ALTER COLUMN email DROP NOT NULL;

-- Drop old unique constraint (may be named differently depending on how it was created)
ALTER TABLE identity.users DROP CONSTRAINT IF EXISTS uq_users_email;
ALTER TABLE identity.users DROP CONSTRAINT IF EXISTS users_email_key;

-- Partial unique index: only enforces uniqueness on non-null, non-soft-deleted rows
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email
    ON identity.users (email)
    WHERE email IS NOT NULL AND deleted_at IS NULL;
