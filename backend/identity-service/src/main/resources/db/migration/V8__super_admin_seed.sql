-- V8: Super Admin setup.
--
-- The PLATFORM_TENANT_ID sentinel (00000000-0000-0000-0000-000000000001) is used
-- in user_tenant_roles for SUPER_ADMIN assignments that are not bound to any real tenant.
-- When a SUPER_ADMIN logs in, the identity-service passes tenantId=null to JwtService
-- so the resulting JWT has NO tenant_id claim, granting full cross-tenant visibility.
--
-- To create the first super admin, run the following INSERT replacing the values:
--
--   INSERT INTO identity.users (id, email, password_hash, is_verified, is_active, created_at, updated_at)
--   VALUES (gen_random_uuid(), 'admin@marketly.com', '<bcrypt-hash>', true, true, now(), now());
--
--   INSERT INTO identity.user_tenant_roles (id, user_id, tenant_id, role_id, created_at, updated_at)
--   SELECT gen_random_uuid(), u.id, '00000000-0000-0000-0000-000000000001', r.id, now(), now()
--   FROM identity.users u, identity.roles r
--   WHERE u.email = 'admin@marketly.com'
--     AND r.name = 'SUPER_ADMIN';
--
-- The password hash above should be generated with BCrypt strength 12.
-- In development you can use the value below for password "Admin@1234":
--   $2b$12$UhHRr5oJxw52N7wMI9JC3OOHFOcs0rH/DSNoOJ/bI.bSFq5X5ueiC

-- Ensure the SUPER_ADMIN role exists (idempotent)
INSERT INTO identity.roles (id, name, description, created_at, updated_at)
VALUES (
    'a0000000-0000-0000-0000-000000000001',
    'SUPER_ADMIN',
    'Platform-wide administrator with full cross-tenant access',
    now(),
    now()
)
ON CONFLICT (name) DO NOTHING;

-- Create default super admin user if one does not exist
-- Password: Admin@1234 (bcrypt strength 12)
INSERT INTO identity.users (id, email, password_hash, is_verified, is_active, created_at, updated_at)
SELECT
    'a0000000-0000-0000-0000-000000000002',
    'admin@marketly.com',
    concat(chr(36),'2b',chr(36),'12',chr(36),'UhHRr5oJxw52N7wMI9JC3OOHFOcs0rH/DSNoOJ/bI.bSFq5X5ueiC'),
    true,
    true,
    now(),
    now()
WHERE NOT EXISTS (
    SELECT 1 FROM identity.users WHERE email = 'admin@marketly.com' AND deleted_at IS NULL
);

-- Assign SUPER_ADMIN role to the default admin user using the PLATFORM_TENANT_ID sentinel
INSERT INTO identity.user_tenant_roles (id, user_id, tenant_id, role_id, created_at, updated_at)
SELECT
    gen_random_uuid(),
    u.id,
    '00000000-0000-0000-0000-000000000001',
    r.id,
    now(),
    now()
FROM identity.users u
JOIN identity.roles r ON r.name = 'SUPER_ADMIN'
WHERE u.email = 'admin@marketly.com'
  AND u.deleted_at IS NULL
ON CONFLICT (user_id, tenant_id, role_id) DO NOTHING;
