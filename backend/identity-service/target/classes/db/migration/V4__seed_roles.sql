-- Seed the five platform roles
INSERT INTO identity.roles (id, name, description, created_at, updated_at)
VALUES
    (gen_random_uuid(), 'SUPER_ADMIN',    'Platform-wide administrator with full access',                     now(), now()),
    (gen_random_uuid(), 'TENANT_OWNER',   'Owner of a tenant store — full access to own store',               now(), now()),
    (gen_random_uuid(), 'STORE_MANAGER',  'Can manage products, orders, customers for own store',             now(), now()),
    (gen_random_uuid(), 'STORE_STAFF',    'Can view and process orders, adjust inventory',                    now(), now()),
    (gen_random_uuid(), 'CUSTOMER',       'End customer — can shop, manage profile, view own orders',          now(), now());
