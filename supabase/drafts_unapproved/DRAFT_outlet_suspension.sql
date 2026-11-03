-- =============================================================================
-- DRAFT — NOT A MIGRATION. DO NOT APPLY WITHOUT EXPLICIT APPROVAL.
--
-- Outlet flagging / suspension / deactivation, kept separate from the existing
-- meanings of is_open (opening hours / temporary closure), is_active, deleted_at.
--
--   ordering_status   effect on NEW orders
--   ACTIVE            allowed (default)
--   FLAGGED           allowed — monitoring only
--   SUSPENDED         rejected (temporary; reversible)
--   DEACTIVATED       rejected (permanent; reactivation needs name confirmation)
--
-- A support ticket is never linked to a status change automatically; resolving a
-- complaint does nothing to the outlet. Only an active ADMIN, via the RPC below.
--
-- Enforcement is a BEFORE INSERT trigger on orders, not an edit of place_order:
--   * it covers place_order AND any other path that inserts an order (direct RPCs,
--     stale carts, replayed requests, concurrent requests);
--   * it needs no copy of the live place_order body, so it can't regress it;
--   * it takes a FOR SHARE lock on the outlet row, so an order racing a suspension
--     either commits first (a pre-existing order) or sees the suspension.
-- Existing orders, payments, tokens and history are never touched. Orders already
-- in flight (incl. paid, awaiting pickup) continue normally.
-- Only INSERT is blocked: blocking CREATED -> PLACED could strand an already-paid order.
-- =============================================================================
BEGIN;

ALTER TABLE public.outlets
    ADD COLUMN IF NOT EXISTS ordering_status TEXT NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS ordering_status_changed_at TIMESTAMPTZ;

ALTER TABLE public.outlets DROP CONSTRAINT IF EXISTS outlets_ordering_status_chk;
ALTER TABLE public.outlets ADD CONSTRAINT outlets_ordering_status_chk
    CHECK (ordering_status IN ('ACTIVE', 'FLAGGED', 'SUSPENDED', 'DEACTIVATED'));

-- Reason / actor live in the audit table (management-only), not on the publicly readable outlets row.
CREATE TABLE IF NOT EXISTS public.outlet_management_actions (
    id          BIGSERIAL PRIMARY KEY,
    outlet_id   UUID NOT NULL REFERENCES public.outlets(id)  ON DELETE RESTRICT,
    actor_id    UUID NOT NULL REFERENCES public.profiles(id) ON DELETE RESTRICT,
    action      TEXT NOT NULL CHECK (action IN ('FLAG', 'SUSPEND', 'DEACTIVATE', 'REACTIVATE')),
    from_status TEXT NOT NULL,
    to_status   TEXT NOT NULL,
    reason      TEXT NOT NULL CHECK (char_length(reason) BETWEEN 3 AND 2000),
    support_ticket_id UUID,                       -- optional context only; no automatic link
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS outlet_mgmt_actions_outlet_idx ON public.outlet_management_actions (outlet_id, created_at DESC);

ALTER TABLE public.outlet_management_actions ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.outlet_management_actions FROM PUBLIC, anon, authenticated;
GRANT SELECT ON public.outlet_management_actions TO authenticated;
DROP POLICY IF EXISTS outlet_mgmt_actions_admin_read ON public.outlet_management_actions;
CREATE POLICY outlet_mgmt_actions_admin_read ON public.outlet_management_actions FOR SELECT TO authenticated
    USING (EXISTS (SELECT 1 FROM public.profiles p WHERE p.id = auth.uid() AND p.role::text = 'ADMIN' AND p.is_active));

CREATE OR REPLACE FUNCTION public.crave_outlet_actions_append_only() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, public, pg_temp AS $$
BEGIN RAISE EXCEPTION 'outlet_management_actions is append-only.' USING ERRCODE = '42501'; END $$;
REVOKE ALL ON FUNCTION public.crave_outlet_actions_append_only() FROM PUBLIC, anon, authenticated;
DROP TRIGGER IF EXISTS outlet_actions_append_only ON public.outlet_management_actions;
CREATE TRIGGER outlet_actions_append_only BEFORE UPDATE OR DELETE ON public.outlet_management_actions
    FOR EACH ROW EXECUTE FUNCTION public.crave_outlet_actions_append_only();

-- Vendors may UPDATE their own outlet row (policy "Vendors can manage their own outlets"):
-- they must not be able to lift a suspension. Only the RPC sets the transaction marker.
CREATE OR REPLACE FUNCTION public.crave_guard_outlet_ordering_status() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, public, pg_temp AS $$
BEGIN
    IF (NEW.ordering_status IS DISTINCT FROM OLD.ordering_status
        OR NEW.ordering_status_changed_at IS DISTINCT FROM OLD.ordering_status_changed_at)
       AND COALESCE(pg_catalog.current_setting('crave.outlet_status_rpc', true), '') <> 'on' THEN
        RAISE EXCEPTION 'ordering_status can only be changed by management.' USING ERRCODE = '42501';
    END IF;
    RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION public.crave_guard_outlet_ordering_status() FROM PUBLIC, anon, authenticated;
DROP TRIGGER IF EXISTS outlets_guard_ordering_status ON public.outlets;
CREATE TRIGGER outlets_guard_ordering_status BEFORE UPDATE ON public.outlets
    FOR EACH ROW EXECUTE FUNCTION public.crave_guard_outlet_ordering_status();

CREATE OR REPLACE FUNCTION public.admin_set_outlet_ordering_status(
    p_outlet_id         UUID,
    p_action            TEXT,
    p_reason            TEXT,
    p_confirm_name      TEXT DEFAULT NULL,
    p_support_ticket_id UUID DEFAULT NULL
) RETURNS JSONB
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp AS $$
DECLARE
    v_uid    UUID := auth.uid();
    v_outlet public.outlets;
    v_action TEXT := upper(btrim(COALESCE(p_action, '')));
    v_reason TEXT := btrim(COALESCE(p_reason, ''));
    v_from   TEXT;
    v_to     TEXT;
    v_min    INTEGER;
BEGIN
    -- Authorization is re-checked here: this function bypasses RLS.
    IF v_uid IS NULL OR NOT EXISTS (
        SELECT 1 FROM public.profiles p WHERE p.id = v_uid AND p.role::text = 'ADMIN' AND p.is_active
    ) THEN
        RAISE EXCEPTION 'Management access required.' USING ERRCODE = '42501';
    END IF;

    IF v_action NOT IN ('FLAG', 'SUSPEND', 'DEACTIVATE', 'REACTIVATE') THEN
        RAISE EXCEPTION 'INVALID_ACTION' USING ERRCODE = '22023';
    END IF;
    v_min := CASE WHEN v_action IN ('SUSPEND', 'DEACTIVATE') THEN 10 ELSE 3 END;  -- (CASE..THEN can't sit inside IF..THEN)
    IF char_length(v_reason) < v_min OR char_length(v_reason) > 2000 THEN
        RAISE EXCEPTION 'REASON_REQUIRED' USING ERRCODE = '22023';
    END IF;

    SELECT * INTO v_outlet FROM public.outlets WHERE id = p_outlet_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'OUTLET_NOT_FOUND' USING ERRCODE = 'P0002'; END IF;
    v_from := v_outlet.ordering_status;

    v_to := CASE v_action
        WHEN 'FLAG'       THEN CASE WHEN v_from = 'ACTIVE' THEN 'FLAGGED' END
        WHEN 'SUSPEND'    THEN CASE WHEN v_from IN ('ACTIVE', 'FLAGGED') THEN 'SUSPENDED' END
        WHEN 'DEACTIVATE' THEN CASE WHEN v_from IN ('ACTIVE', 'FLAGGED', 'SUSPENDED') THEN 'DEACTIVATED' END
        WHEN 'REACTIVATE' THEN CASE WHEN v_from IN ('FLAGGED', 'SUSPENDED', 'DEACTIVATED') THEN 'ACTIVE' END
    END;
    IF v_to IS NULL THEN
        RAISE EXCEPTION 'INVALID_TRANSITION: % from %', v_action, v_from USING ERRCODE = '22023';
    END IF;

    -- Destructive / hard-to-reverse changes need the outlet name typed back.
    IF (v_action = 'DEACTIVATE' OR (v_action = 'REACTIVATE' AND v_from = 'DEACTIVATED') OR v_action = 'SUSPEND')
       AND btrim(COALESCE(p_confirm_name, '')) IS DISTINCT FROM v_outlet.name THEN
        RAISE EXCEPTION 'CONFIRMATION_REQUIRED' USING ERRCODE = '22023';
    END IF;

    PERFORM pg_catalog.set_config('crave.outlet_status_rpc', 'on', true);
    UPDATE public.outlets
       SET ordering_status = v_to, ordering_status_changed_at = now()
     WHERE id = p_outlet_id;
    PERFORM pg_catalog.set_config('crave.outlet_status_rpc', '', true);

    INSERT INTO public.outlet_management_actions (outlet_id, actor_id, action, from_status, to_status, reason, support_ticket_id)
    VALUES (p_outlet_id, v_uid, v_action, v_from, v_to, v_reason, p_support_ticket_id);

    RETURN jsonb_build_object('outlet_id', p_outlet_id, 'from', v_from, 'to', v_to);
END $$;
REVOKE ALL ON FUNCTION public.admin_set_outlet_ordering_status(UUID, TEXT, TEXT, TEXT, UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.admin_set_outlet_ordering_status(UUID, TEXT, TEXT, TEXT, UUID) TO authenticated;

-- Authoritative server-side block for NEW orders.
CREATE OR REPLACE FUNCTION public.crave_block_orders_for_unavailable_outlet() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp AS $$
DECLARE v_status TEXT;
BEGIN
    SELECT ordering_status INTO v_status FROM public.outlets WHERE id = NEW.outlet_id FOR SHARE;
    IF v_status IN ('SUSPENDED', 'DEACTIVATED') THEN
        RAISE EXCEPTION 'OUTLET_ORDERING_SUSPENDED: this outlet is not accepting orders.' USING ERRCODE = '55000';
    END IF;
    RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION public.crave_block_orders_for_unavailable_outlet() FROM PUBLIC, anon, authenticated;
DROP TRIGGER IF EXISTS crave_orders_block_unavailable_outlet ON public.orders;
CREATE TRIGGER crave_orders_block_unavailable_outlet BEFORE INSERT ON public.orders
    FOR EACH ROW EXECUTE FUNCTION public.crave_block_orders_for_unavailable_outlet();

COMMIT;
