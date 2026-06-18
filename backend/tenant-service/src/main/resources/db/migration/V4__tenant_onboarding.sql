-- Add onboarding_step column to track seller wizard progress for resume-later.
-- Cannot be stored in tenant_settings (key-value) because it is a first-class
-- lifecycle field queried by admin views and the seller status page.
ALTER TABLE tenant.tenants
    ADD COLUMN IF NOT EXISTS onboarding_step VARCHAR(50) DEFAULT 'MOBILE';

-- Existing tenants that are already ACTIVE have completed onboarding
UPDATE tenant.tenants SET onboarding_step = 'COMPLETE' WHERE status = 'ACTIVE';
