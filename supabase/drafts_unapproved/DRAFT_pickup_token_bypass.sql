-- =============================================================================
-- DRAFT — NOT A MIGRATION. DO NOT APPLY TO ANY DATABASE WITHOUT EXPLICIT APPROVAL.
-- Lives outside supabase/migrations on purpose so `supabase db push` can't pick it up.
-- Target migration number: undecided until both repos + staging are checked.
--
-- Problem: orders.status READY -> PICKED_UP is allowed to a vendor by the order
-- state machine (migration 003, `enforce_order_state_machine`; live name
-- `crave_guard_order_update`), so a vendor can mark an order collected with a
-- plain UPDATE and skip verify_pickup_token().
--
-- Fix: a dedicated trigger that only lets READY -> PICKED_UP through when the
-- same transaction has been marked by verify_pickup_token() for THAT order.
-- It sits beside the existing guard (whatever its live definition) instead of
-- editing it, so it cannot loosen anything else.
--
-- BEFORE APPLYING: diff the verify_pickup_token() below against the live
--   SELECT pg_get_functiondef('public.verify_pickup_token(text)'::regprocedure);
-- It is the 003 definition + (a) FOR UPDATE on the order row, (b) the marker,
-- (c) hardened search_path. If live differs, merge by hand.
-- =============================================================================
BEGIN;

CREATE OR REPLACE FUNCTION public.crave_enforce_pickup_via_token() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp AS $$
BEGIN
    IF OLD.status::text = 'READY' AND NEW.status::text = 'PICKED_UP'
       AND COALESCE(pg_catalog.current_setting('crave.pickup_verified_order', true), '') <> NEW.id::text THEN
        RAISE EXCEPTION 'PICKUP_TOKEN_REQUIRED: orders can only be marked picked up by verifying the pickup token.'
            USING ERRCODE = '42501';
    END IF;
    RETURN NEW;
END $$;

REVOKE ALL ON FUNCTION public.crave_enforce_pickup_via_token() FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS crave_orders_pickup_via_token ON public.orders;
CREATE TRIGGER crave_orders_pickup_via_token
    BEFORE UPDATE OF status ON public.orders
    FOR EACH ROW
    WHEN (OLD.status IS DISTINCT FROM NEW.status AND NEW.status::text = 'PICKED_UP')
    EXECUTE FUNCTION public.crave_enforce_pickup_via_token();

CREATE OR REPLACE FUNCTION public.verify_pickup_token(p_token TEXT) RETURNS JSONB
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp AS $$
DECLARE
    v_vendor UUID := auth.uid();
    v_token  RECORD; v_order RECORD; v_outlet RECORD;
BEGIN
    IF public.get_user_role() != 'VENDOR' THEN RAISE EXCEPTION 'Only vendors can verify pickup tokens.'; END IF;

    SELECT * INTO v_token FROM public.pickup_tokens WHERE token_value = p_token FOR UPDATE;
    IF NOT FOUND          THEN RAISE EXCEPTION 'Invalid pickup token.'; END IF;
    IF v_token.is_used    THEN RAISE EXCEPTION 'Token already used.'; END IF;
    IF v_token.expires_at < NOW() THEN RAISE EXCEPTION 'Token expired.'; END IF;

    SELECT * INTO v_order FROM public.orders WHERE id = v_token.order_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Order not found.'; END IF;

    SELECT * INTO v_outlet FROM public.outlets WHERE id = v_order.outlet_id AND vendor_id = v_vendor;
    IF NOT FOUND THEN RAISE EXCEPTION 'Order does not belong to your outlet.'; END IF;

    IF v_order.status::text != 'READY' THEN
        RAISE EXCEPTION 'Order not ready for pickup. Status: %', v_order.status;
    END IF;

    UPDATE public.pickup_tokens SET is_used = true, used_at = NOW() WHERE id = v_token.id;

    -- Transaction-local marker, scoped to this one order. Not settable through PostgREST.
    PERFORM pg_catalog.set_config('crave.pickup_verified_order', v_order.id::text, true);
    UPDATE public.orders SET status = 'PICKED_UP', picked_up_at = NOW(), updated_at = NOW() WHERE id = v_order.id;
    PERFORM pg_catalog.set_config('crave.pickup_verified_order', '', true);

    INSERT INTO public.audit_logs (user_id, action, table_name, record_id, new_data)
    VALUES (v_vendor, 'PICKUP_COMPLETED', 'orders', v_order.id,
            jsonb_build_object('order_number', v_order.order_number));

    RETURN jsonb_build_object('success', true, 'order_id', v_order.id,
                              'order_number', v_order.order_number, 'picked_up_at', NOW());
END $$;

REVOKE ALL ON FUNCTION public.verify_pickup_token(TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.verify_pickup_token(TEXT) TO authenticated;

COMMIT;
