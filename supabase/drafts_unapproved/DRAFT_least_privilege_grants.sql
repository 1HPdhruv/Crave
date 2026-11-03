-- =============================================================================
-- DRAFT — NOT A MIGRATION. DO NOT APPLY WITHOUT EXPLICIT APPROVAL.
--
-- Problem (production audit): `anon` holds broad privileges (incl. TRUNCATE) on
-- food_items / outlets / pickup_slots. RLS does not protect TRUNCATE.
--
-- Scope kept deliberately small and non-breaking:
--   * anon: lose every privilege except SELECT (public menu discovery keeps working).
--   * anon + authenticated: lose TRUNCATE, TRIGGER, REFERENCES (never needed by the app).
--   * authenticated keeps INSERT/UPDATE/DELETE, still gated by RLS + guard triggers.
--   * service_role / postgres untouched.
-- Follow-up (separate review): narrow anon SELECT to the tables the app really reads
-- before login (outlets, food_items, pickup_slots + catalogue RPCs).
--
-- PREFLIGHT (read-only, run first, expect zero rows — anon must have no write policy):
--   SELECT schemaname, tablename, policyname, cmd FROM pg_policies
--   WHERE schemaname = 'public' AND cmd <> 'SELECT' AND ('anon' = ANY(roles) OR 'public' = ANY(roles));
-- (a `public` role policy with a write cmd would be reachable by anon; review any hit.)
-- =============================================================================
BEGIN;

DO $$
DECLARE t RECORD;
BEGIN
    FOR t IN SELECT c.relname FROM pg_class c
             JOIN pg_namespace n ON n.oid = c.relnamespace
             WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')
    LOOP
        EXECUTE format('REVOKE INSERT, UPDATE, DELETE, TRUNCATE, TRIGGER, REFERENCES ON public.%I FROM anon', t.relname);
        EXECUTE format('REVOKE TRUNCATE, TRIGGER, REFERENCES ON public.%I FROM authenticated', t.relname);
    END LOOP;
END $$;

-- Tables created later must not re-grant the dangerous set.
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE INSERT, UPDATE, DELETE, TRUNCATE, TRIGGER, REFERENCES ON TABLES FROM anon;
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE TRUNCATE, TRIGGER, REFERENCES ON TABLES FROM authenticated;

COMMIT;
