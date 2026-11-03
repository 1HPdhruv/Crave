#!/usr/bin/env python3
"""
Generates supabase/staging/02_staging_app_schema.sql from the repo migrations 001-019.

Purpose: CRAVE-Staging (yatbgxfjlnqpvrftjdmu) was initialised with 01_init_minimal.sql (7 deny-by-default tables)
plus migration 023. The Android app needs the full schema from migrations 001-019. This produces ONE reviewed,
single-transaction script that brings exactly that database up to date. It is NOT a migration and is deliberately
kept outside supabase/migrations so `supabase db push` can never pick it up.

Transformations (and nothing else):
  * per-file BEGIN/COMMIT removed; the whole output is one transaction (all-or-nothing)
  * CREATE TYPE ... AS ENUM          -> created only if missing (staging already has 5 of them)
  * CREATE TABLE / INDEX             -> IF NOT EXISTS (the 7 init tables already exist)
  * CREATE TRIGGER / CREATE POLICY   -> preceded by DROP ... IF EXISTS (re-runnable statements)
  * migration 003's cancel policy used NEW/OLD inside WITH CHECK, which PostgreSQL rejects; replaced by a valid one
  * a guard at the top and a least-privilege grant + RLS assertion block at the bottom
"""
import pathlib, re, hashlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
MIG = ROOT / "migrations"
OUT = ROOT / "staging" / "02_staging_app_schema.sql"
FILES = sorted(p for p in MIG.glob("0*.sql") if p.name < "020")          # 001..019 (both 019_* files)

# Tables the app reads before/without login (public catalogue). Everything else is signed-in only.
ANON_READ = ["outlets", "categories", "food_items", "food_variants", "food_variant_options", "pickup_slots", "inventory"]
# Client write access per table, derived from what the Android app actually writes (postgrest insert/update/delete calls)
# plus the vendor/admin catalogue-management policies. Anything not listed is READ-ONLY for signed-in users.
FULL_DML = ["carts", "cart_items", "cart_item_customizations", "favorites",
            "reviews", "review_replies", "outlet_reviews", "outlet_review_replies",
            "outlets", "food_items", "food_variants", "food_variant_options", "inventory", "pickup_slots",
            "categories", "coupons"]
INSERT_UPDATE = ["profiles"]                 # first-login profile row + profile edits; never deleted by clients
UPDATE_ONLY = ["orders", "notifications"]   # student cancel / mark-as-read; orders are CREATED only by place_order()
# Written ONLY by SECURITY DEFINER functions/triggers -> SELECT only, so history can't be altered or deleted by a client:
#   order_items, order_item_customizations, payments, pickup_tokens, audit_logs (and any table not listed above).
# 023 manages its own grants; never touch them here.
SKIP_GRANTS = ["support_tickets", "support_ticket_messages", "support_ticket_internal_notes", "support_ticket_events"]

FIXED_003_POLICY = '''CREATE POLICY "Users can cancel their own pending orders" ON orders FOR UPDATE
USING (auth.uid() = user_id AND status IN ('CREATED','PLACED'))
WITH CHECK (auth.uid() = user_id AND status = 'CANCELLED');
-- (staging catch-up) The repo version referenced NEW/OLD inside WITH CHECK, which PostgreSQL rejects.
-- Only the ownership + target-status rules are expressible in a policy; the order state-machine trigger
-- (enforce_order_state_machine) still blocks every other student transition.'''

def transform(name: str, sql: str) -> str:
    sql = re.sub(r'(?im)^\s*(BEGIN|COMMIT)\s*;\s*$', r'-- (\1 removed: single outer transaction)', sql)
    sql = re.sub(r'(?i)CREATE\s+TYPE\s+(\w+)\s+AS\s+ENUM\s*(\([^;]*?\))\s*;',
                 lambda m: f"DO $t$ BEGIN IF to_regtype('public.{m.group(1)}') IS NULL THEN "
                           f"CREATE TYPE public.{m.group(1)} AS ENUM {m.group(2)}; END IF; END $t$;", sql)
    sql = re.sub(r'(?i)CREATE\s+TABLE\s+(?!IF\s+NOT\s+EXISTS)', 'CREATE TABLE IF NOT EXISTS ', sql)
    sql = re.sub(r'(?i)CREATE\s+(UNIQUE\s+)?INDEX\s+(?!IF\s+NOT\s+EXISTS)', lambda m: f"CREATE {m.group(1) or ''}INDEX IF NOT EXISTS ", sql)
    sql = re.sub(r'(?is)CREATE\s+TRIGGER\s+(\w+)(\s+(?:BEFORE|AFTER|INSTEAD\s+OF)\b.*?\bON\s+([\w.]+))',
                 lambda m: f"DROP TRIGGER IF EXISTS {m.group(1)} ON {m.group(3)};\nCREATE TRIGGER {m.group(1)}{m.group(2)}", sql)
    sql = re.sub(r'(?is)CREATE\s+POLICY\s+("[^"]+"|\w+)\s+ON\s+([\w.]+)',
                 lambda m: f"DROP POLICY IF EXISTS {m.group(1)} ON {m.group(2)};\nCREATE POLICY {m.group(1)} ON {m.group(2)}", sql)
    if name.startswith("018_"):
        # 001 (earlier in THIS script) creates an old-format `reviews` table; 018 replaces it. The guard guarantees
        # `reviews` did not exist before this run, so the table being replaced was created by this transaction and is
        # empty. Keep the replacement, but make it provably non-destructive: assert it is empty and drop WITHOUT
        # CASCADE, so any unexpected dependent object makes the whole script fail instead of being removed silently.
        sql, n = re.subn(r'(?im)^\s*DROP TABLE IF EXISTS reviews CASCADE;\s*$', """DO $replace_reviews$
BEGIN
    IF to_regclass('public.reviews') IS NOT NULL AND EXISTS (SELECT 1 FROM public.reviews) THEN
        RAISE EXCEPTION 'CATCH-UP ABORTED: public.reviews contains rows; refusing to replace it. Whole script rolled back.';
    END IF;
END
$replace_reviews$;
DROP TABLE IF EXISTS public.reviews;   -- (staging catch-up) was "... CASCADE"; empty table created by 001 in this transaction""", sql)
        assert n == 1, "018 DROP TABLE not found - repo migration changed, review the generator"
    code_only = re.sub(r'--[^\n]*', '', sql)                 # ignore comments
    # The only table drop allowed anywhere is 018's guarded, non-CASCADE replacement of the empty 001 `reviews` table.
    drops = re.findall(r'(?i)\bDROP\s+(?:TABLE|SCHEMA|TYPE)\b[^;]*;', code_only)
    allowed = ["DROP TABLE IF EXISTS public.reviews;"] if name.startswith("018_") else []
    assert [d.strip() for d in drops] == allowed and not re.search(r'(?i)\bCASCADE\b', ' '.join(drops)), f"unexpected drop in {name}: {drops}"
    assert not re.search(r'(?i)\bTRUNCATE\s+(TABLE\s+)?\w', code_only), f"TRUNCATE in {name}"
    if name.startswith("003_"):
        sql, n = re.subn(r'(DROP POLICY IF EXISTS "Users can cancel their own pending orders" ON orders;\n)'
                         r'CREATE POLICY "Users can cancel their own pending orders"[\s\S]*?\n\);', r'\1' + FIXED_003_POLICY, sql, count=1)
        assert n == 1, "003 cancel policy not found - repo migration changed, review the generator"
    return sql

GUARD = r'''-- =============================================================================
-- 02_staging_app_schema.sql — intended for CRAVE-Staging (yatbgxfjlnqpvrftjdmu) ONLY.
-- GENERATED by supabase/staging/build_staging_catchup.py from supabase/migrations/001..019 — do not hand-edit.
--
-- What the guard below CAN verify: that this database has exactly the starting schema created by
-- 01_init_minimal.sql + migration 023 (table set, key columns, enum values). It CANNOT verify which Supabase
-- project it is running in — plain SQL has no reliable access to the project ref. Open the SQL Editor of the
-- staging project yourself before running this.
--
-- One transaction: it either fully applies or changes nothing. No TRUNCATE, no rows deleted, no CASCADE drops. The only
-- table drop replaces the EMPTY old-format `reviews` table that migration 001 creates earlier in this same transaction
-- (asserted empty; dropped without CASCADE).
-- Data-touching statements (only effective if rows already exist): 014 seeds an inventory row per food item;
-- 015's is_veg backfill only touches NULLs (none possible: the column is NOT NULL in the staging schema).
-- =============================================================================

DO $guard$
DECLARE
    v_expected text[] := ARRAY['profiles','outlets','categories','food_items','pickup_slots','orders','order_items',
                               'support_tickets','support_ticket_messages','support_ticket_internal_notes','support_ticket_events'];
    v_actual   text[];
    v_problem  text := '';
BEGIN
    IF to_regclass('auth.users') IS NULL OR to_regprocedure('auth.uid()') IS NULL OR to_regnamespace('extensions') IS NULL THEN
        RAISE EXCEPTION 'CATCH-UP ABORTED: auth.users / auth.uid() / schema "extensions" missing - not a Supabase database. Nothing was changed.';
    END IF;
    IF (SELECT count(*) FROM pg_roles WHERE rolname IN ('anon','authenticated','service_role')) <> 3 THEN
        RAISE EXCEPTION 'CATCH-UP ABORTED: Supabase API roles missing. Nothing was changed.';
    END IF;

    -- 1. The public table set must be EXACTLY the minimal staging set (catches production, a partial run, or drift).
    SELECT array_agg(relname::text ORDER BY relname) INTO v_actual
    FROM pg_class WHERE relnamespace = 'public'::regnamespace AND relkind IN ('r','p');
    IF v_actual IS DISTINCT FROM (SELECT array_agg(x ORDER BY x) FROM unnest(v_expected) x) THEN
        RAISE EXCEPTION 'CATCH-UP ABORTED: public tables are % but the minimal staging schema has exactly %. Nothing was changed.',
            COALESCE(array_to_string(v_actual, ', '), '(none)'), array_to_string(v_expected, ', ');
    END IF;

    -- 2. Key columns the catch-up relies on (fingerprint of 01_init_minimal.sql).
    SELECT string_agg(t || '.' || c, ', ') INTO v_problem
    FROM (VALUES ('outlets','is_active'),('outlets','vendor_id'),('outlets','operating_hours'),
                 ('food_items','category_id'),('food_items','outlet_id'),('food_items','is_popular'),('food_items','is_veg'),
                 ('orders','payment_status'),('orders','pickup_slot_id'),('profiles','role'),('profiles','is_active'),
                 ('pickup_slots','booked_count')) k(t, c)
    WHERE NOT EXISTS (SELECT 1 FROM information_schema.columns
                      WHERE table_schema = 'public' AND table_name = k.t AND column_name = k.c);
    IF v_problem IS NOT NULL THEN
        RAISE EXCEPTION 'CATCH-UP ABORTED: expected columns missing: %. Nothing was changed.', v_problem;
    END IF;

    -- 3. Enum values used by the policies created below must ALREADY exist: a value added inside this transaction
    --    could not be used by those policies ("unsafe use of new value"), so it is required up front, not added here.
    SELECT string_agg(v, ', ') INTO v_problem
    FROM (VALUES ('user_role','STUDENT'),('user_role','VENDOR'),('user_role','ADMIN'),('user_role','PENDING_VENDOR'),
                 ('payment_status','PAID'),('payment_status','CAPTURED'),('payment_status','CREATED'),('payment_status','AUTHORIZED'),
                 ('order_status','PICKED_UP'),('order_status','EXPIRED'),('payment_method','ONLINE')) e(typ, v)
    WHERE NOT EXISTS (SELECT 1 FROM pg_enum WHERE enumtypid = to_regtype('public.' || e.typ) AND enumlabel = e.v);
    IF v_problem IS NOT NULL THEN
        RAISE EXCEPTION 'CATCH-UP ABORTED: enum values missing (%): add them in a separate, committed step first. Nothing was changed.', v_problem;
    END IF;
END
$guard$;

BEGIN;
-- Supabase keeps pgcrypto/uuid-ossp in the "extensions" schema; the repo migrations call them unqualified.
SET LOCAL search_path = public, extensions;
'''

def footer() -> str:
    anon = ", ".join(f"public.{t}" for t in ANON_READ)
    q = lambda xs: ", ".join(f"'{x}'" for x in xs)
    full, iu, uo = q(FULL_DML), q(INSERT_UPDATE), q(UPDATE_ONLY)
    skip = ", ".join(f"'{t}'" for t in SKIP_GRANTS)
    return f'''
-- =============================================================================
-- Least-privilege grants (replaces Supabase's broad defaults, which include TRUNCATE for anon) — per-table matrix.
-- Row-level security decides WHICH rows; these grants decide which operations are possible at all.
-- =============================================================================
DO $grants$
DECLARE t record;
BEGIN
    FOR t IN SELECT c.relname FROM pg_class c
             WHERE c.relnamespace = 'public'::regnamespace AND c.relkind IN ('r','p')
               AND c.relname NOT IN ({skip})
    LOOP
        EXECUTE format('REVOKE ALL ON public.%I FROM PUBLIC, anon, authenticated', t.relname);
        EXECUTE format('GRANT SELECT ON public.%I TO authenticated', t.relname);   -- RLS still decides which rows
        EXECUTE format('GRANT ALL ON public.%I TO service_role', t.relname);
        IF t.relname = ANY (ARRAY[{full}]) THEN
            EXECUTE format('GRANT INSERT, UPDATE, DELETE ON public.%I TO authenticated', t.relname);
        ELSIF t.relname = ANY (ARRAY[{iu}]) THEN
            EXECUTE format('GRANT INSERT, UPDATE ON public.%I TO authenticated', t.relname);
        ELSIF t.relname = ANY (ARRAY[{uo}]) THEN
            EXECUTE format('GRANT UPDATE ON public.%I TO authenticated', t.relname);
        END IF;
    END LOOP;
END
$grants$;
GRANT SELECT ON {anon} TO anon;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO authenticated;

-- Tables created later must not silently receive the broad defaults either.
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE TRUNCATE, TRIGGER, REFERENCES ON TABLES FROM anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE INSERT, UPDATE, DELETE ON TABLES FROM anon;

-- Safety net: every table a client role can touch MUST have RLS enabled, or the whole script rolls back.
DO $rls$
DECLARE v_bad text;
BEGIN
    SELECT string_agg(c.relname, ', ') INTO v_bad
    FROM pg_class c
    WHERE c.relnamespace = 'public'::regnamespace AND c.relkind IN ('r','p') AND NOT c.relrowsecurity
      AND (has_table_privilege('anon', c.oid, 'SELECT') OR has_table_privilege('authenticated', c.oid, 'SELECT'));
    IF v_bad IS NOT NULL THEN
        RAISE EXCEPTION 'CATCH-UP ABORTED: client-visible tables without RLS: % (whole script rolled back)', v_bad;
    END IF;
END
$rls$;

COMMIT;

-- Make PostgREST pick up the new tables and foreign keys (food_items -> food_variants -> food_variant_options).
NOTIFY pgrst, 'reload schema';
'''

def main() -> None:
    parts = [GUARD]
    for f in FILES:
        src = f.read_text()
        digest = hashlib.sha256(src.encode()).hexdigest()[:16]
        parts.append(f"\n-- ===== {f.name} (sha256 {digest}…) =====\n" + transform(f.name, src))
    parts.append(footer())
    OUT.write_text("".join(parts))
    print(f"wrote {OUT.relative_to(ROOT.parent)} from {len(FILES)} migrations; sha256={hashlib.sha256(OUT.read_bytes()).hexdigest()}")

if __name__ == "__main__":
    main()
