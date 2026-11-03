-- =============================================================================
-- DRAFT — NOT A MIGRATION. DO NOT APPLY WITHOUT EXPLICIT APPROVAL.
--
-- Problem: SECURITY DEFINER functions with `search_path = public` (or none) can be
-- hijacked by objects a lower-privileged role creates earlier in the path.
--
-- This is NOT a mechanical rewrite. Only functions NAMED below are touched, each
-- was traced in the repo migrations (all use only public tables/types, pg_catalog
-- built-ins and — for pgcrypto — schema-qualified `extensions.` calls). The loop
-- resolves signatures from pg_proc so overloads are handled and nothing is guessed.
-- Function BODIES are not changed; only the per-function search_path setting.
--
-- Target: pg_catalog, public, extensions, pg_temp   (pg_temp LAST)
--   place_order / mark_payment_verified: confirm on staging that the payment and
--   order smoke tests still pass; they are included because they are the highest value.
--
-- Part 2 revokes client EXECUTE on trigger-only functions. Triggers are unaffected:
-- EXECUTE is checked when a trigger is created, not when it fires.
--
-- PREFLIGHT (read-only): list what is still unhardened
--   SELECT p.oid::regprocedure, p.proconfig FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
--   WHERE n.nspname='public' AND p.prosecdef
--     AND NOT COALESCE(p.proconfig::text,'') LIKE '%pg_temp%';
-- =============================================================================
BEGIN;

DO $$
DECLARE
    v_names TEXT[] := ARRAY[
        -- roles / auth helpers
        'get_user_role','is_admin','is_student','is_vendor','prevent_role_escalation','handle_new_user',
        -- ordering / payment / pickup
        'place_order','mark_payment_verified','verify_pickup_token',
        'vendor_accept_order','vendor_start_preparing','vendor_mark_ready','vendor_reject_order',
        'enforce_order_state_machine','notify_order_status_change','release_pickup_slot_capacity',
        'restore_inventory_and_slots_on_cancel','initialize_food_inventory','enforce_cart_single_outlet',
        'enforce_food_price_integrity','enforce_vendor_food_ownership','audit_critical_changes',
        -- catalogue / admin
        'get_catalogue_for_outlet','search_food','data_quality_report','get_admin_stats',
        'get_management_analytics','admin_toggle_outlet_status',
        -- reviews
        'get_food_reviews','get_management_reviews','get_review_for_order_item','get_vendor_reviews',
        'get_outlet_reviews','get_management_outlet_reviews','get_outlet_review_for_order','get_vendor_outlet_reviews',
        'protect_review_immutable_fields','protect_outlet_review_immutable_fields','sync_food_item_rating',
        'sync_outlet_rating','validate_review_purchase','validate_outlet_review_purchase',
        'validate_vendor_reply_ownership','validate_vendor_reply_update',
        'validate_outlet_vendor_reply_ownership','validate_outlet_vendor_reply_update'
    ];
    v_path TEXT := CASE WHEN EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'extensions')
                        THEN 'pg_catalog, public, extensions, pg_temp' ELSE 'pg_catalog, public, pg_temp' END;
    r RECORD;
BEGIN
    FOR r IN SELECT p.oid::regprocedure AS sig FROM pg_proc p
             JOIN pg_namespace n ON n.oid = p.pronamespace
             WHERE n.nspname = 'public' AND p.prosecdef AND p.proname = ANY (v_names)
    LOOP
        EXECUTE format('ALTER FUNCTION %s SET search_path = %s', r.sig, v_path);
    END LOOP;
END $$;

-- Part 2: trigger-only functions need no client EXECUTE.
DO $$
DECLARE
    v_trg TEXT[] := ARRAY[
        'prevent_role_escalation','handle_new_user','enforce_order_state_machine','notify_order_status_change',
        'release_pickup_slot_capacity','restore_inventory_and_slots_on_cancel','initialize_food_inventory',
        'enforce_cart_single_outlet','enforce_food_price_integrity','enforce_vendor_food_ownership',
        'audit_critical_changes','protect_review_immutable_fields','protect_outlet_review_immutable_fields',
        'sync_food_item_rating','sync_outlet_rating','validate_review_purchase','validate_outlet_review_purchase',
        'validate_vendor_reply_ownership','validate_vendor_reply_update',
        'validate_outlet_vendor_reply_ownership','validate_outlet_vendor_reply_update'
    ];
    r RECORD;
BEGIN
    FOR r IN SELECT p.oid::regprocedure AS sig FROM pg_proc p
             JOIN pg_namespace n ON n.oid = p.pronamespace
             WHERE n.nspname = 'public' AND p.proname = ANY (v_trg) AND p.prorettype = 'pg_catalog.trigger'::regtype
    LOOP
        EXECUTE format('REVOKE ALL ON FUNCTION %s FROM PUBLIC, anon, authenticated', r.sig);
    END LOOP;
END $$;

-- Part 3: privileged RPCs must not be executable by `anon` (default PUBLIC execute).
-- They already refuse unauthenticated callers internally; this removes the attack surface itself.
-- NOT touched on purpose: get_user_role / is_admin / is_student / is_vendor (evaluated inside RLS
-- policies for anon queries), get_catalogue_for_outlet and search_food (public menu discovery).
DO $$
DECLARE
    v_priv TEXT[] := ARRAY[
        'place_order','verify_pickup_token',  -- mark_payment_verified: see DRAFT_payment_verification_lockdown.sql
        'vendor_accept_order','vendor_start_preparing','vendor_mark_ready','vendor_reject_order',
        'get_admin_stats','get_management_analytics','data_quality_report','admin_toggle_outlet_status',
        'get_management_reviews','get_management_outlet_reviews','get_vendor_reviews','get_vendor_outlet_reviews'
    ];
    r RECORD;
BEGIN
    FOR r IN SELECT p.oid::regprocedure AS sig FROM pg_proc p
             JOIN pg_namespace n ON n.oid = p.pronamespace
             WHERE n.nspname = 'public' AND p.proname = ANY (v_priv) AND p.prorettype <> 'pg_catalog.trigger'::regtype
    LOOP
        EXECUTE format('REVOKE ALL ON FUNCTION %s FROM PUBLIC, anon', r.sig);
        EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated', r.sig);
    END LOOP;
END $$;

COMMIT;
