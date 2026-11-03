-- =============================================================================
-- DRAFT — NOT A MIGRATION. DO NOT APPLY WITHOUT EXPLICIT APPROVAL.
--
-- Problem (repo migrations 010/011): mark_payment_verified() has NO caller or ownership check
-- and is executable by `authenticated` (and, by default PUBLIC grant, `anon`). Any logged-in user
-- can call it directly with a fake signature and mark ANY order PAID, bypassing the HMAC check
-- the verify-razorpay-payment Edge Function performs. (To confirm on staging whether the live
-- function matches: SELECT pg_get_functiondef('public.mark_payment_verified(uuid,text,text,payment_status)'::regprocedure);
-- and has_function_privilege('authenticated', ..., 'EXECUTE').)
--
-- Fix: only the service role may call it. The Edge Function must call it with a service-role client
-- AFTER it has authenticated the user, checked payment ownership (RLS-scoped select) and verified
-- the HMAC. The repo copy of supabase/functions/verify-razorpay-payment/index.ts is updated that way.
--
-- DEPLOY ORDER MATTERS: (1) deploy the updated Edge Function, (2) THEN apply this SQL. Applying the
-- SQL first would make every payment verification fail. razorpay-webhook already uses the service role.
-- The Android app never calls this RPC directly (verified by grep).
-- =============================================================================
BEGIN;

REVOKE ALL ON FUNCTION public.mark_payment_verified(UUID, TEXT, TEXT, payment_status) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.mark_payment_verified(UUID, TEXT, TEXT, payment_status) TO service_role;

ALTER FUNCTION public.mark_payment_verified(UUID, TEXT, TEXT, payment_status)
    SET search_path = pg_catalog, public, extensions, pg_temp;

COMMIT;
