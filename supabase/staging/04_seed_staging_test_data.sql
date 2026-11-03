-- =============================================================================
-- 04_seed_staging_test_data.sql — SYNTHETIC test data for CRAVE-Staging (yatbgxfjlnqpvrftjdmu) ONLY.
--
-- * Everything is fictional and labelled "TEST" / "(staging)". No production records, no personal data.
-- * Safe to re-run: fixed ids + ON CONFLICT, so a second run creates no duplicates (pickup slots are keyed by
--   outlet/date/time; a re-run on a later day only adds the new future days).
-- * One transaction, guarded: aborts unless the database already has the CRAVE app schema
--   (02_staging_app_schema.sql applied). It cannot verify the project ref: open the STAGING SQL Editor yourself.
-- * The test vendor is a placeholder identity with NO password: it cannot sign in. Student test accounts are created
--   separately through Supabase Auth (Dashboard -> Authentication -> Add user), never here.
-- * Does not change any table structure, policy, grant or function.
-- =============================================================================

DO $guard$
BEGIN
    IF to_regclass('public.food_variants') IS NULL OR to_regclass('public.inventory') IS NULL
       OR to_regclass('public.support_tickets') IS NULL OR to_regprocedure('public.place_order(uuid,uuid,text)') IS NULL THEN
        RAISE EXCEPTION 'SEED ABORTED: the CRAVE app schema is not present (run 02_staging_app_schema.sql first). Nothing was changed.';
    END IF;
END
$guard$;

BEGIN;

-- ── Placeholder vendor identity (no password -> cannot log in) ───────────────────────────────────────────────
INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
                        raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
                        confirmation_token, recovery_token, email_change_token_new, email_change)
VALUES ('00000000-0000-0000-0000-000000000000', '5eed0000-0000-4000-8000-000000000001', 'authenticated', 'authenticated',
        'crave.staging.test-vendor@example.invalid', '', now(),
        '{"provider":"email","providers":["email"]}', '{"name":"TEST Vendor (staging)"}', now(), now(), '', '', '', '')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.profiles (id, name, email, role, is_active)
VALUES ('5eed0000-0000-4000-8000-000000000001', 'TEST Vendor (staging)', 'crave.staging.test-vendor@example.invalid', 'VENDOR', true)
ON CONFLICT (id) DO NOTHING;

-- ── Categories ───────────────────────────────────────────────────────────────────────────────────────────────
INSERT INTO public.categories (id, name, emoji) VALUES
    ('5eed0000-0000-4000-8000-0000000000c1', 'TEST Snacks', '🥟'),
    ('5eed0000-0000-4000-8000-0000000000c2', 'TEST Meals', '🍛'),
    ('5eed0000-0000-4000-8000-0000000000c3', 'TEST Beverages', '🥤')
ON CONFLICT (id) DO NOTHING;

-- ── Outlets: one active (open all week, all day, so ordering is never blocked by hours) + one inactive ───────
INSERT INTO public.outlets (id, name, description, vendor_id, is_open, is_active, building, floor, location_description, operating_hours)
VALUES
    ('5eed0000-0000-4000-8000-0000000000a1', 'TEST Kitchen (staging)', 'Synthetic outlet for staging tests',
     '5eed0000-0000-4000-8000-000000000001', true, true, 'TEST Block', 'Ground', 'Synthetic location',
     '{"openTime":"00:00","closeTime":"23:59","daysOpen":["Mon","Tue","Wed","Thu","Fri","Sat","Sun"]}'),
    ('5eed0000-0000-4000-8000-0000000000a2', 'TEST Closed Outlet (inactive, must stay hidden)', 'Synthetic inactive outlet',
     '5eed0000-0000-4000-8000-000000000001', false, false, 'TEST Block', 'First', 'Synthetic location',
     '{"openTime":"08:00","closeTime":"22:00","daysOpen":["Mon","Tue","Wed","Thu","Fri"]}')
ON CONFLICT (id) DO NOTHING;

-- ── Food items (the AFTER INSERT trigger creates their inventory rows) ──────────────────────────────────────────
-- The enforce_vendor_food_ownership trigger refuses food-item writes from callers without a role (as in the SQL
-- Editor). Instead of disabling it, act AS the test vendor for the rest of this transaction (transaction-local JWT
-- settings, gone at COMMIT): the trigger then performs its normal "vendor owns this outlet" check and passes.
SELECT set_config('request.jwt.claim.sub', '5eed0000-0000-4000-8000-000000000001', true),
       set_config('request.jwt.claims', '{"sub":"5eed0000-0000-4000-8000-000000000001","role":"authenticated"}', true);

INSERT INTO public.food_items (id, outlet_id, category_id, name, description, price, is_veg, is_available,
                               prep_time_minutes, is_popular, is_recommended, tags)
VALUES
    ('5eed0000-0000-4000-8000-0000000000f1', '5eed0000-0000-4000-8000-0000000000a1', '5eed0000-0000-4000-8000-0000000000c1',
     'TEST Samosa', 'Synthetic veg snack', 20.00, true,  true, 5,  true,  false, '{snack,test}'),
    ('5eed0000-0000-4000-8000-0000000000f2', '5eed0000-0000-4000-8000-0000000000a1', '5eed0000-0000-4000-8000-0000000000c2',
     'TEST Masala Dosa', 'Synthetic veg meal', 60.00, true,  true, 10, true,  true,  '{meal,test}'),
    ('5eed0000-0000-4000-8000-0000000000f3', '5eed0000-0000-4000-8000-0000000000a1', '5eed0000-0000-4000-8000-0000000000c2',
     'TEST Chicken Biryani', 'Synthetic non-veg meal', 120.00, false, true, 15, true,  true,  '{meal,test}'),
    ('5eed0000-0000-4000-8000-0000000000f4', '5eed0000-0000-4000-8000-0000000000a1', '5eed0000-0000-4000-8000-0000000000c3',
     'TEST Cold Coffee', 'Synthetic beverage', 50.00, true,  true, 3,  false, true,  '{drink,test}'),
    ('5eed0000-0000-4000-8000-0000000000f5', '5eed0000-0000-4000-8000-0000000000a1', '5eed0000-0000-4000-8000-0000000000c1',
     'TEST Sold-out Puff', 'Synthetic unavailable item', 25.00, true,  false, 5, false, false, '{snack,test}'),
    ('5eed0000-0000-4000-8000-0000000000f6', '5eed0000-0000-4000-8000-0000000000a2', '5eed0000-0000-4000-8000-0000000000c1',
     'TEST Hidden Item', 'Belongs to the inactive outlet; must never be visible to students', 30.00, true, true, 5, true, false, '{test}')
ON CONFLICT (id) DO NOTHING;

-- Realistic stock (the insert trigger seeds 1,000,000 = "not tracked"; only reset the synthetic rows we own)
INSERT INTO public.inventory (food_item_id, quantity_available, low_stock_threshold)
SELECT f.id, CASE WHEN f.id = '5eed0000-0000-4000-8000-0000000000f5' THEN 0 ELSE 50 END, 5
FROM public.food_items f WHERE f.id::text LIKE '5eed0000-0000-4000-8000-0000000000f%'
ON CONFLICT (food_item_id) DO UPDATE
    SET quantity_available = EXCLUDED.quantity_available, low_stock_threshold = EXCLUDED.low_stock_threshold
    WHERE public.inventory.quantity_available >= 100000;        -- never overwrite stock that tests have since changed

-- ── Variants and options ─────────────────────────────────────────────────────────────────────────────────────
INSERT INTO public.food_variants (id, food_item_id, name, is_required, max_selections) VALUES
    ('5eed0000-0000-4000-8000-0000000000b1', '5eed0000-0000-4000-8000-0000000000f2', 'TEST Size', true, 1),
    ('5eed0000-0000-4000-8000-0000000000b2', '5eed0000-0000-4000-8000-0000000000f3', 'TEST Spice level', false, 1),
    ('5eed0000-0000-4000-8000-0000000000b3', '5eed0000-0000-4000-8000-0000000000f4', 'TEST Add-ons', false, 2)
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.food_variant_options (id, variant_id, name, extra_price) VALUES
    ('5eed0000-0000-4000-8000-0000000000d1', '5eed0000-0000-4000-8000-0000000000b1', 'Regular', 0),
    ('5eed0000-0000-4000-8000-0000000000d2', '5eed0000-0000-4000-8000-0000000000b1', 'Large', 20),
    ('5eed0000-0000-4000-8000-0000000000d3', '5eed0000-0000-4000-8000-0000000000b2', 'Mild', 0),
    ('5eed0000-0000-4000-8000-0000000000d4', '5eed0000-0000-4000-8000-0000000000b2', 'Hot', 0),
    ('5eed0000-0000-4000-8000-0000000000d5', '5eed0000-0000-4000-8000-0000000000b3', 'Extra ice cream', 30),
    ('5eed0000-0000-4000-8000-0000000000d6', '5eed0000-0000-4000-8000-0000000000b3', 'Whipped cream', 15)
ON CONFLICT (id) DO NOTHING;

-- ── Future pickup slots: next 7 days (Asia/Kolkata), 4 per day, for the active test outlet ────────────────────
INSERT INTO public.pickup_slots (outlet_id, slot_date, start_time, end_time, capacity)
SELECT '5eed0000-0000-4000-8000-0000000000a1', d::date, t.s, t.e, 20
FROM generate_series((now() AT TIME ZONE 'Asia/Kolkata')::date, (now() AT TIME ZONE 'Asia/Kolkata')::date + 6, interval '1 day') d
CROSS JOIN (VALUES ('12:00'::time, '12:30'::time), ('13:00', '13:30'), ('18:00', '18:30'), ('20:00', '20:30')) t(s, e)
WHERE d::date > (now() AT TIME ZONE 'Asia/Kolkata')::date OR t.s > (now() AT TIME ZONE 'Asia/Kolkata')::time
ON CONFLICT (outlet_id, slot_date, start_time) DO NOTHING;

COMMIT;
