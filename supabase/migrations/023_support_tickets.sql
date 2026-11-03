-- =============================================================================
-- Migration 023: Support tickets (student complaints + management queue)
-- =============================================================================
-- Numbered 023 because the Android repo (018_food_reviews, 019_analytics_trend,
-- 019_outlet_reviews) and the website repo (018..022) have diverged migration
-- histories against the same Supabase project. 023 is unused in both.
--
-- Self-contained on purpose: it only depends on objects from 001 (profiles,
-- orders, order_items, outlets, food_items) and defines its own role helpers,
-- because the live database was provisioned with a subset of the repository
-- migrations. It does NOT touch existing tables, policies or functions.
--
-- Security model
--   * Clients get NO direct INSERT/UPDATE/DELETE on any table created here.
--     Every write goes through a SECURITY DEFINER RPC that validates
--     auth.uid(), role, ownership and state transitions.
--   * Students can SELECT their own tickets (column-level grant excludes the
--     management-only `assigned_to`) and the messages on their own tickets.
--   * Internal notes and the audit trail are readable only through ADMIN RPCs.
--   * Vendors have no access to tickets at all.
--   * Messages, internal notes and the audit trail are append-only (triggers),
--     so no API role (anon, authenticated, service_role) can rewrite history.
--     Their author/actor foreign keys are ON DELETE RESTRICT, so deleting a
--     profile that appears in that history is rejected instead of rewriting it.
--     The table owner / superuser can still bypass triggers; this is not a
--     defence against the database owner.
--   * At most one active ticket per student + order + category is enforced by
--     a partial unique index, not only by RPC checks.
--
-- Idempotent: safe to re-run. Apply via the SQL editor after review.
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- 0. Role helpers (scoped to this feature; read profiles directly)
-- -----------------------------------------------------------------------------

-- Active ADMIN = management. Disabled accounts lose access immediately.
CREATE OR REPLACE FUNCTION public.crave_support_is_admin() RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public, pg_temp AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.profiles p
        WHERE p.id = auth.uid() AND p.role::text = 'ADMIN' AND p.is_active
    )
$$;

CREATE OR REPLACE FUNCTION public.crave_support_is_student() RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public, pg_temp AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.profiles p
        WHERE p.id = auth.uid() AND p.role::text = 'STUDENT' AND p.is_active
    )
$$;

-- -----------------------------------------------------------------------------
-- 1. Tables
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS public.support_tickets (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_number      TEXT NOT NULL UNIQUE,
    student_id         UUID NOT NULL REFERENCES public.profiles(id) ON DELETE RESTRICT,
    -- Linked records are derived/validated server-side, never trusted from the client.
    order_id           UUID REFERENCES public.orders(id) ON DELETE RESTRICT,
    outlet_id          UUID REFERENCES public.outlets(id) ON DELETE RESTRICT,
    food_item_id       UUID REFERENCES public.food_items(id) ON DELETE RESTRICT,
    category           TEXT NOT NULL,
    subject            TEXT NOT NULL,
    description        TEXT NOT NULL,
    priority           TEXT NOT NULL DEFAULT 'NORMAL',
    status             TEXT NOT NULL DEFAULT 'OPEN',
    assigned_to        UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    resolution_reason  TEXT,
    -- Client-generated key so a retried/double-tapped submit returns the same ticket.
    client_request_id  UUID,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at        TIMESTAMPTZ,
    closed_at          TIMESTAMPTZ,
    CONSTRAINT support_tickets_category_chk CHECK (category IN
        ('ORDER', 'FOOD_QUALITY', 'MISSING_ITEMS', 'PAYMENT', 'PICKUP', 'VENDOR_CONDUCT', 'OTHER')),
    CONSTRAINT support_tickets_status_chk CHECK (status IN
        ('OPEN', 'UNDER_REVIEW', 'WAITING_FOR_STUDENT', 'RESOLVED', 'CLOSED')),
    CONSTRAINT support_tickets_priority_chk CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT support_tickets_subject_len_chk CHECK (char_length(subject) BETWEEN 3 AND 120),
    CONSTRAINT support_tickets_description_len_chk CHECK (char_length(description) BETWEEN 10 AND 4000),
    CONSTRAINT support_tickets_resolution_len_chk CHECK (resolution_reason IS NULL OR char_length(resolution_reason) BETWEEN 3 AND 2000),
    -- A resolved/closed ticket always carries when and why.
    CONSTRAINT support_tickets_resolved_chk CHECK (
        status NOT IN ('RESOLVED', 'CLOSED') OR (resolved_at IS NOT NULL AND resolution_reason IS NOT NULL)),
    CONSTRAINT support_tickets_closed_chk CHECK (status <> 'CLOSED' OR closed_at IS NOT NULL),
    -- A food item is only meaningful together with the order it came from.
    CONSTRAINT support_tickets_food_needs_order_chk CHECK (food_item_id IS NULL OR order_id IS NOT NULL)
);

CREATE UNIQUE INDEX IF NOT EXISTS support_tickets_client_request_uq
    ON public.support_tickets (student_id, client_request_id) WHERE client_request_id IS NOT NULL;
-- Authoritative rule (independent of isolation level and of the RPC pre-checks):
-- at most one active ticket per student + order + category. Resolved/closed
-- history is unrestricted. Keep this status list in sync with the RPC checks below.
CREATE UNIQUE INDEX IF NOT EXISTS support_tickets_one_active_per_order_uq
    ON public.support_tickets (student_id, order_id, category)
    WHERE order_id IS NOT NULL AND status IN ('OPEN', 'UNDER_REVIEW', 'WAITING_FOR_STUDENT');
CREATE INDEX IF NOT EXISTS support_tickets_student_idx  ON public.support_tickets (student_id, created_at DESC);
CREATE INDEX IF NOT EXISTS support_tickets_queue_idx    ON public.support_tickets (status, priority, created_at DESC);
CREATE INDEX IF NOT EXISTS support_tickets_outlet_idx   ON public.support_tickets (outlet_id, created_at DESC) WHERE outlet_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS support_tickets_order_idx    ON public.support_tickets (order_id) WHERE order_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS support_tickets_assignee_idx ON public.support_tickets (assigned_to) WHERE assigned_to IS NOT NULL;

-- Conversation visible to the student: the student's follow-ups and the
-- student-facing management responses.
CREATE TABLE IF NOT EXISTS public.support_ticket_messages (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id    UUID NOT NULL REFERENCES public.support_tickets(id) ON DELETE RESTRICT,
    -- RESTRICT, not SET NULL: history rows are append-only, so a profile that
    -- authored history cannot be deleted (it would have to rewrite the row).
    author_id    UUID CONSTRAINT support_ticket_messages_author_id_fkey
                 REFERENCES public.profiles(id) ON DELETE RESTRICT,
    author_role  TEXT NOT NULL,
    body         TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT support_ticket_messages_role_chk CHECK (author_role IN ('STUDENT', 'MANAGEMENT')),
    CONSTRAINT support_ticket_messages_len_chk CHECK (char_length(body) BETWEEN 1 AND 4000)
);
CREATE INDEX IF NOT EXISTS support_ticket_messages_ticket_idx ON public.support_ticket_messages (ticket_id, created_at);

-- Management-only notes. Kept in a separate table so no student/vendor query
-- path can ever select them alongside the ticket.
CREATE TABLE IF NOT EXISTS public.support_ticket_internal_notes (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id    UUID NOT NULL REFERENCES public.support_tickets(id) ON DELETE RESTRICT,
    author_id    UUID CONSTRAINT support_ticket_internal_notes_author_id_fkey
                 REFERENCES public.profiles(id) ON DELETE RESTRICT,
    note         TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT support_ticket_notes_len_chk CHECK (char_length(note) BETWEEN 1 AND 4000)
);
CREATE INDEX IF NOT EXISTS support_ticket_notes_ticket_idx ON public.support_ticket_internal_notes (ticket_id, created_at);

-- Append-only audit trail of every state change and management decision.
CREATE TABLE IF NOT EXISTS public.support_ticket_events (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id    UUID NOT NULL REFERENCES public.support_tickets(id) ON DELETE RESTRICT,
    actor_id     UUID CONSTRAINT support_ticket_events_actor_id_fkey
                 REFERENCES public.profiles(id) ON DELETE RESTRICT,
    actor_role   TEXT NOT NULL,
    action       TEXT NOT NULL,
    from_value   TEXT,
    to_value     TEXT,
    metadata     JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT support_ticket_events_action_chk CHECK (action IN
        ('CREATED', 'STATUS_CHANGED', 'PRIORITY_CHANGED', 'ASSIGNED', 'UNASSIGNED',
         'STUDENT_MESSAGE', 'MANAGEMENT_RESPONSE', 'INTERNAL_NOTE_ADDED', 'REOPENED'))
);
CREATE INDEX IF NOT EXISTS support_ticket_events_ticket_idx ON public.support_ticket_events (ticket_id, id);

-- -----------------------------------------------------------------------------
-- 2. Integrity triggers
-- -----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.crave_support_touch_updated_at() RETURNS trigger
LANGUAGE plpgsql SET search_path = public, pg_temp AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS support_tickets_touch ON public.support_tickets;
CREATE TRIGGER support_tickets_touch BEFORE UPDATE ON public.support_tickets
FOR EACH ROW EXECUTE FUNCTION public.crave_support_touch_updated_at();

-- Ownership, links and creation time never change after creation, whoever writes.
CREATE OR REPLACE FUNCTION public.crave_support_guard_ticket_update() RETURNS trigger
LANGUAGE plpgsql SET search_path = public, pg_temp AS $$
BEGIN
    IF NEW.id IS DISTINCT FROM OLD.id
       OR NEW.ticket_number IS DISTINCT FROM OLD.ticket_number
       OR NEW.student_id IS DISTINCT FROM OLD.student_id
       OR NEW.order_id IS DISTINCT FROM OLD.order_id
       OR NEW.outlet_id IS DISTINCT FROM OLD.outlet_id
       OR NEW.food_item_id IS DISTINCT FROM OLD.food_item_id
       OR NEW.client_request_id IS DISTINCT FROM OLD.client_request_id
       OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'Ticket ownership and links are immutable.' USING ERRCODE = '42501';
    END IF;
    RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS support_tickets_guard ON public.support_tickets;
CREATE TRIGGER support_tickets_guard BEFORE UPDATE ON public.support_tickets
FOR EACH ROW EXECUTE FUNCTION public.crave_support_guard_ticket_update();

-- History rows are never edited or removed.
CREATE OR REPLACE FUNCTION public.crave_support_append_only() RETURNS trigger
LANGUAGE plpgsql SET search_path = public, pg_temp AS $$
BEGIN
    RAISE EXCEPTION '% is append-only.', TG_TABLE_NAME USING ERRCODE = '42501';
END $$;

DROP TRIGGER IF EXISTS support_ticket_events_append_only ON public.support_ticket_events;
CREATE TRIGGER support_ticket_events_append_only BEFORE UPDATE OR DELETE ON public.support_ticket_events
FOR EACH ROW EXECUTE FUNCTION public.crave_support_append_only();

DROP TRIGGER IF EXISTS support_ticket_messages_append_only ON public.support_ticket_messages;
CREATE TRIGGER support_ticket_messages_append_only BEFORE UPDATE OR DELETE ON public.support_ticket_messages
FOR EACH ROW EXECUTE FUNCTION public.crave_support_append_only();

DROP TRIGGER IF EXISTS support_ticket_notes_append_only ON public.support_ticket_internal_notes;
CREATE TRIGGER support_ticket_notes_append_only BEFORE UPDATE OR DELETE ON public.support_ticket_internal_notes
FOR EACH ROW EXECUTE FUNCTION public.crave_support_append_only();

-- Tickets themselves are never deleted (closed instead).
DROP TRIGGER IF EXISTS support_tickets_no_delete ON public.support_tickets;
CREATE TRIGGER support_tickets_no_delete BEFORE DELETE ON public.support_tickets
FOR EACH ROW EXECUTE FUNCTION public.crave_support_append_only();

-- -----------------------------------------------------------------------------
-- 3. Privileges + RLS
-- -----------------------------------------------------------------------------
-- Supabase grants ALL on new public tables to anon/authenticated by default;
-- take that back and grant only what the read policies need.

ALTER TABLE public.support_tickets               ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.support_ticket_messages       ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.support_ticket_internal_notes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.support_ticket_events         ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON public.support_tickets, public.support_ticket_messages,
              public.support_ticket_internal_notes, public.support_ticket_events
FROM PUBLIC, anon, authenticated;

-- Students read their own tickets; `assigned_to` and `client_request_id` stay hidden.
GRANT SELECT (id, ticket_number, student_id, order_id, outlet_id, food_item_id, category, subject,
              description, priority, status, resolution_reason, created_at, updated_at,
              resolved_at, closed_at)
    ON public.support_tickets TO authenticated;
GRANT SELECT ON public.support_ticket_messages TO authenticated;
-- No grants at all on internal notes / events: management reads them via RPC.

DROP POLICY IF EXISTS support_tickets_select_own   ON public.support_tickets;
DROP POLICY IF EXISTS support_tickets_select_admin ON public.support_tickets;
CREATE POLICY support_tickets_select_own ON public.support_tickets
    FOR SELECT TO authenticated USING (student_id = auth.uid());
CREATE POLICY support_tickets_select_admin ON public.support_tickets
    FOR SELECT TO authenticated USING (public.crave_support_is_admin());

DROP POLICY IF EXISTS support_ticket_messages_select ON public.support_ticket_messages;
CREATE POLICY support_ticket_messages_select ON public.support_ticket_messages
    FOR SELECT TO authenticated USING (
        public.crave_support_is_admin()
        OR EXISTS (SELECT 1 FROM public.support_tickets t
                   WHERE t.id = support_ticket_messages.ticket_id AND t.student_id = auth.uid())
    );

-- Defence in depth: even if a grant is added later, notes/events stay admin-only.
DROP POLICY IF EXISTS support_ticket_notes_select_admin ON public.support_ticket_internal_notes;
CREATE POLICY support_ticket_notes_select_admin ON public.support_ticket_internal_notes
    FOR SELECT TO authenticated USING (public.crave_support_is_admin());
DROP POLICY IF EXISTS support_ticket_events_select_admin ON public.support_ticket_events;
CREATE POLICY support_ticket_events_select_admin ON public.support_ticket_events
    FOR SELECT TO authenticated USING (public.crave_support_is_admin());

-- -----------------------------------------------------------------------------
-- 4. Internal helpers
-- -----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.crave_support_log(
    p_ticket_id UUID, p_actor_role TEXT, p_action TEXT,
    p_from TEXT DEFAULT NULL, p_to TEXT DEFAULT NULL, p_metadata JSONB DEFAULT '{}'::jsonb
) RETURNS void
LANGUAGE sql SECURITY DEFINER SET search_path = public, pg_temp AS $$
    INSERT INTO public.support_ticket_events (ticket_id, actor_id, actor_role, action, from_value, to_value, metadata)
    VALUES (p_ticket_id, auth.uid(), p_actor_role, p_action, p_from, p_to, COALESCE(p_metadata, '{}'::jsonb))
$$;

CREATE OR REPLACE FUNCTION public.crave_support_clean(p_text TEXT) RETURNS TEXT
LANGUAGE sql IMMUTABLE SET search_path = public, pg_temp AS $$
    SELECT NULLIF(btrim(regexp_replace(COALESCE(p_text, ''), '[\x01-\x08\x0B\x0C\x0E-\x1F\x7F]', '', 'g')), '')
$$;

-- -----------------------------------------------------------------------------
-- 5. Student RPCs
-- -----------------------------------------------------------------------------

-- Creates a ticket for the caller. Outlet is derived from the order; a food item
-- must be part of that order. Retrying with the same p_client_request_id returns
-- the original ticket instead of creating a duplicate. At most one active ticket
-- per order + category (unique index). Returns only the
-- student-visible columns (never assigned_to), even on a retry after assignment.
-- Dropped and recreated so the JSONB return type applies cleanly on re-run;
-- its grants are re-applied in section 7.
DROP FUNCTION IF EXISTS public.create_support_ticket(TEXT, TEXT, TEXT, UUID, UUID, UUID, UUID);
CREATE OR REPLACE FUNCTION public.create_support_ticket(
    p_category          TEXT,
    p_subject           TEXT,
    p_description       TEXT,
    p_order_id          UUID DEFAULT NULL,
    p_food_item_id      UUID DEFAULT NULL,
    p_outlet_id         UUID DEFAULT NULL,
    p_client_request_id UUID DEFAULT NULL
) RETURNS JSONB
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE
    v_uid       UUID := auth.uid();
    v_ticket    public.support_tickets;
    v_order     RECORD;
    v_outlet_id UUID;
    v_subject   TEXT := public.crave_support_clean(p_subject);
    v_desc      TEXT := public.crave_support_clean(p_description);
    v_category  TEXT := upper(btrim(COALESCE(p_category, '')));
    v_recent    INTEGER;
    v_active    INTEGER;
    v_dup       UUID;
    v_number    TEXT;
    v_conflict  TEXT;
    v_attempt   INTEGER := 0;
BEGIN
    IF v_uid IS NULL THEN RAISE EXCEPTION 'Authentication required.' USING ERRCODE = '42501'; END IF;
    IF NOT public.crave_support_is_student() THEN
        RAISE EXCEPTION 'Only active student accounts can raise support tickets.' USING ERRCODE = '42501';
    END IF;

    -- Serialise submissions per student so the idempotency and rate checks are race-free.
    PERFORM pg_advisory_xact_lock(hashtextextended('support_ticket:' || v_uid::text, 0));

    IF p_client_request_id IS NOT NULL THEN
        SELECT * INTO v_ticket FROM public.support_tickets
        WHERE student_id = v_uid AND client_request_id = p_client_request_id;
        IF FOUND THEN RETURN to_jsonb(v_ticket) - 'assigned_to' - 'client_request_id'; END IF;
    END IF;

    IF v_category NOT IN ('ORDER', 'FOOD_QUALITY', 'MISSING_ITEMS', 'PAYMENT', 'PICKUP', 'VENDOR_CONDUCT', 'OTHER') THEN
        RAISE EXCEPTION 'INVALID_CATEGORY' USING ERRCODE = '22023';
    END IF;
    IF v_subject IS NULL OR char_length(v_subject) NOT BETWEEN 3 AND 120 THEN
        RAISE EXCEPTION 'INVALID_SUBJECT: subject must be 3-120 characters.' USING ERRCODE = '22023';
    END IF;
    IF v_desc IS NULL OR char_length(v_desc) NOT BETWEEN 10 AND 4000 THEN
        RAISE EXCEPTION 'INVALID_DESCRIPTION: description must be 10-4000 characters.' USING ERRCODE = '22023';
    END IF;

    -- Linked records: never trust client-supplied relationships.
    IF p_order_id IS NOT NULL THEN
        SELECT o.id, o.user_id, o.outlet_id INTO v_order FROM public.orders o WHERE o.id = p_order_id;
        IF NOT FOUND OR v_order.user_id IS DISTINCT FROM v_uid THEN
            -- Same message whether the order is missing or someone else's.
            RAISE EXCEPTION 'ORDER_NOT_FOUND' USING ERRCODE = '42501';
        END IF;
        IF p_outlet_id IS NOT NULL AND p_outlet_id IS DISTINCT FROM v_order.outlet_id THEN
            RAISE EXCEPTION 'OUTLET_MISMATCH' USING ERRCODE = '22023';
        END IF;
        v_outlet_id := v_order.outlet_id;
        IF p_food_item_id IS NOT NULL AND NOT EXISTS (
            SELECT 1 FROM public.order_items oi WHERE oi.order_id = p_order_id AND oi.food_item_id = p_food_item_id
        ) THEN
            RAISE EXCEPTION 'FOOD_ITEM_NOT_IN_ORDER' USING ERRCODE = '22023';
        END IF;
    ELSE
        IF p_food_item_id IS NOT NULL THEN
            RAISE EXCEPTION 'FOOD_ITEM_REQUIRES_ORDER' USING ERRCODE = '22023';
        END IF;
        IF v_category IN ('ORDER', 'FOOD_QUALITY', 'MISSING_ITEMS', 'PICKUP') THEN
            RAISE EXCEPTION 'ORDER_REQUIRED: this category needs the related order.' USING ERRCODE = '22023';
        END IF;
        IF p_outlet_id IS NOT NULL THEN
            IF NOT EXISTS (SELECT 1 FROM public.outlets WHERE id = p_outlet_id) THEN
                RAISE EXCEPTION 'OUTLET_NOT_FOUND' USING ERRCODE = '22023';
            END IF;
            v_outlet_id := p_outlet_id;
        END IF;
        IF v_category = 'VENDOR_CONDUCT' AND v_outlet_id IS NULL THEN
            RAISE EXCEPTION 'OUTLET_REQUIRED: choose the order or outlet involved.' USING ERRCODE = '22023';
        END IF;
    END IF;

    -- One active ticket per order + category; point the student at the existing one.
    IF p_order_id IS NOT NULL THEN
        SELECT id INTO v_dup FROM public.support_tickets
        WHERE student_id = v_uid AND order_id = p_order_id AND category = v_category
          AND status NOT IN ('RESOLVED', 'CLOSED')
        LIMIT 1;
        IF v_dup IS NOT NULL THEN
            RAISE EXCEPTION 'TICKET_ALREADY_OPEN:%', v_dup USING ERRCODE = '23505';
        END IF;
    END IF;

    -- Abuse limits.
    SELECT count(*) INTO v_recent FROM public.support_tickets
    WHERE student_id = v_uid AND created_at > now() - interval '1 hour';
    IF v_recent >= 5 THEN
        RAISE EXCEPTION 'RATE_LIMITED: too many tickets in the last hour.' USING ERRCODE = '54000';
    END IF;
    SELECT count(*) INTO v_active FROM public.support_tickets
    WHERE student_id = v_uid AND status NOT IN ('RESOLVED', 'CLOSED');
    IF v_active >= 10 THEN
        RAISE EXCEPTION 'TOO_MANY_OPEN_TICKETS' USING ERRCODE = '54000';
    END IF;

    -- The checks above give friendly errors; the unique indexes are the real
    -- guarantee. A conflict that slips past them (e.g. a race under a stricter
    -- isolation level) is mapped to the same errors instead of a raw 23505.
    LOOP
        v_attempt := v_attempt + 1;
        v_number := 'TKT-' || to_char(now() AT TIME ZONE 'Asia/Kolkata', 'YYMMDD') || '-'
                    || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 6));
        BEGIN
            INSERT INTO public.support_tickets (ticket_number, student_id, order_id, outlet_id, food_item_id,
                                                category, subject, description, client_request_id)
            VALUES (v_number, v_uid, p_order_id, v_outlet_id, p_food_item_id,
                    v_category, v_subject, v_desc, p_client_request_id)
            RETURNING * INTO v_ticket;
            EXIT;
        EXCEPTION WHEN unique_violation THEN
            GET STACKED DIAGNOSTICS v_conflict = CONSTRAINT_NAME;
            IF v_conflict = 'support_tickets_one_active_per_order_uq' THEN
                SELECT id INTO v_dup FROM public.support_tickets
                WHERE student_id = v_uid AND order_id = p_order_id AND category = v_category
                  AND status IN ('OPEN', 'UNDER_REVIEW', 'WAITING_FOR_STUDENT')
                LIMIT 1;
                RAISE EXCEPTION 'TICKET_ALREADY_OPEN:%', COALESCE(v_dup::text, '') USING ERRCODE = '23505';
            ELSIF v_conflict = 'support_tickets_client_request_uq' THEN
                SELECT * INTO v_ticket FROM public.support_tickets
                WHERE student_id = v_uid AND client_request_id = p_client_request_id;
                IF FOUND THEN RETURN to_jsonb(v_ticket) - 'assigned_to' - 'client_request_id'; END IF;
                -- Committed by a concurrent request but not visible in this snapshot: ask to retry.
                RAISE EXCEPTION 'REQUEST_IN_PROGRESS: retry the same request.' USING ERRCODE = '40001';
            ELSIF v_conflict = 'support_tickets_ticket_number_key' AND v_attempt < 5 THEN
                CONTINUE;  -- ticket number collision: draw a new number
            ELSE
                RAISE;
            END IF;
        END;
    END LOOP;

    PERFORM public.crave_support_log(v_ticket.id, 'STUDENT', 'CREATED', NULL, 'OPEN',
        jsonb_build_object('category', v_category, 'order_id', p_order_id, 'outlet_id', v_outlet_id));
    RETURN to_jsonb(v_ticket) - 'assigned_to' - 'client_request_id';
END $$;

-- Student follow-up. Answering a WAITING_FOR_STUDENT ticket puts it back in review.
CREATE OR REPLACE FUNCTION public.add_support_ticket_message(p_ticket_id UUID, p_body TEXT)
RETURNS public.support_ticket_messages
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE
    v_uid    UUID := auth.uid();
    v_ticket public.support_tickets;
    v_msg    public.support_ticket_messages;
    v_body   TEXT := public.crave_support_clean(p_body);
BEGIN
    IF v_uid IS NULL OR NOT public.crave_support_is_student() THEN
        RAISE EXCEPTION 'Only the student who raised the ticket can reply.' USING ERRCODE = '42501';
    END IF;
    SELECT * INTO v_ticket FROM public.support_tickets WHERE id = p_ticket_id FOR UPDATE;
    IF NOT FOUND OR v_ticket.student_id IS DISTINCT FROM v_uid THEN
        RAISE EXCEPTION 'TICKET_NOT_FOUND' USING ERRCODE = '42501';
    END IF;
    IF v_ticket.status IN ('RESOLVED', 'CLOSED') THEN
        RAISE EXCEPTION 'TICKET_CLOSED: raise a new ticket if the problem continues.' USING ERRCODE = '22023';
    END IF;
    IF v_body IS NULL OR char_length(v_body) > 4000 THEN
        RAISE EXCEPTION 'INVALID_MESSAGE' USING ERRCODE = '22023';
    END IF;
    IF (SELECT count(*) FROM public.support_ticket_messages
        WHERE ticket_id = p_ticket_id AND author_id = v_uid AND created_at > now() - interval '10 minutes') >= 5 THEN
        RAISE EXCEPTION 'RATE_LIMITED' USING ERRCODE = '54000';
    END IF;

    INSERT INTO public.support_ticket_messages (ticket_id, author_id, author_role, body)
    VALUES (p_ticket_id, v_uid, 'STUDENT', v_body) RETURNING * INTO v_msg;
    PERFORM public.crave_support_log(p_ticket_id, 'STUDENT', 'STUDENT_MESSAGE', NULL, NULL,
        jsonb_build_object('message_id', v_msg.id));

    IF v_ticket.status = 'WAITING_FOR_STUDENT' THEN
        UPDATE public.support_tickets SET status = 'UNDER_REVIEW' WHERE id = p_ticket_id;
        PERFORM public.crave_support_log(p_ticket_id, 'STUDENT', 'STATUS_CHANGED', 'WAITING_FOR_STUDENT', 'UNDER_REVIEW',
            jsonb_build_object('reason', 'Student replied'));
    ELSE
        UPDATE public.support_tickets SET updated_at = now() WHERE id = p_ticket_id;
    END IF;
    RETURN v_msg;
END $$;

-- -----------------------------------------------------------------------------
-- 6. Management RPCs (ADMIN only)
-- -----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.crave_support_require_admin() RETURNS void
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public, pg_temp AS $$
BEGIN
    IF auth.uid() IS NULL OR NOT public.crave_support_is_admin() THEN
        RAISE EXCEPTION 'Management access required.' USING ERRCODE = '42501';
    END IF;
END $$;

-- Status workflow. p_expected_status guards against two managers acting on a
-- stale view of the same ticket. RESOLVED/CLOSED require a reason; RESOLVED ->
-- UNDER_REVIEW is the only (management-only) reopen path; CLOSED is final.
CREATE OR REPLACE FUNCTION public.admin_set_support_ticket_status(
    p_ticket_id UUID, p_status TEXT, p_reason TEXT DEFAULT NULL, p_expected_status TEXT DEFAULT NULL
) RETURNS public.support_tickets
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE
    v_ticket public.support_tickets;
    v_new    TEXT := upper(btrim(COALESCE(p_status, '')));
    v_reason TEXT := public.crave_support_clean(p_reason);
    v_ok     BOOLEAN;
    v_old    TEXT;
    v_other  UUID;
    v_conflict TEXT;
BEGIN
    PERFORM public.crave_support_require_admin();
    SELECT * INTO v_ticket FROM public.support_tickets WHERE id = p_ticket_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'TICKET_NOT_FOUND' USING ERRCODE = 'P0002'; END IF;
    v_old := v_ticket.status;
    IF p_expected_status IS NOT NULL AND v_ticket.status <> upper(p_expected_status) THEN
        RAISE EXCEPTION 'TICKET_CHANGED:%', v_ticket.status USING ERRCODE = '40001';
    END IF;
    IF v_new = v_ticket.status THEN RETURN v_ticket; END IF;

    v_ok := CASE v_ticket.status
        WHEN 'OPEN'                THEN v_new IN ('UNDER_REVIEW', 'WAITING_FOR_STUDENT', 'RESOLVED', 'CLOSED')
        WHEN 'UNDER_REVIEW'        THEN v_new IN ('WAITING_FOR_STUDENT', 'RESOLVED', 'CLOSED')
        WHEN 'WAITING_FOR_STUDENT' THEN v_new IN ('UNDER_REVIEW', 'RESOLVED', 'CLOSED')
        WHEN 'RESOLVED'            THEN v_new IN ('CLOSED', 'UNDER_REVIEW')
        ELSE false
    END;
    IF NOT v_ok THEN
        RAISE EXCEPTION 'INVALID_TRANSITION: % -> %', v_ticket.status, v_new USING ERRCODE = '22023';
    END IF;
    IF v_new IN ('RESOLVED', 'CLOSED') AND v_reason IS NULL AND v_ticket.resolution_reason IS NULL THEN
        RAISE EXCEPTION 'REASON_REQUIRED' USING ERRCODE = '22023';
    END IF;
    IF v_ticket.status = 'RESOLVED' AND v_new = 'UNDER_REVIEW' AND v_reason IS NULL THEN
        RAISE EXCEPTION 'REASON_REQUIRED: explain why the ticket is reopened.' USING ERRCODE = '22023';
    END IF;
    IF v_reason IS NOT NULL AND char_length(v_reason) NOT BETWEEN 3 AND 2000 THEN
        RAISE EXCEPTION 'INVALID_REASON' USING ERRCODE = '22023';
    END IF;

    -- Reopening must not create a second active ticket for the same order + category.
    -- Friendly pre-check; support_tickets_one_active_per_order_uq is authoritative.
    IF v_old = 'RESOLVED' AND v_new = 'UNDER_REVIEW' AND v_ticket.order_id IS NOT NULL THEN
        SELECT id INTO v_other FROM public.support_tickets
        WHERE student_id = v_ticket.student_id AND order_id = v_ticket.order_id
          AND category = v_ticket.category AND id <> v_ticket.id
          AND status IN ('OPEN', 'UNDER_REVIEW', 'WAITING_FOR_STUDENT')
        LIMIT 1;
        IF v_other IS NOT NULL THEN
            RAISE EXCEPTION 'REOPEN_CONFLICT:%', v_other USING ERRCODE = '23505',
                HINT = 'Another active ticket exists for this order and category; continue there instead.';
        END IF;
    END IF;

    BEGIN
    UPDATE public.support_tickets SET
        status            = v_new,
        resolution_reason = CASE WHEN v_new IN ('RESOLVED', 'CLOSED') THEN COALESCE(v_reason, resolution_reason)
                                 WHEN v_new = 'UNDER_REVIEW' AND v_ticket.status = 'RESOLVED' THEN NULL
                                 ELSE resolution_reason END,
        resolved_at       = CASE WHEN v_new IN ('RESOLVED', 'CLOSED') THEN COALESCE(resolved_at, now())
                                 WHEN v_new = 'UNDER_REVIEW' THEN NULL ELSE resolved_at END,
        closed_at         = CASE WHEN v_new = 'CLOSED' THEN now() ELSE closed_at END
    WHERE id = p_ticket_id
    RETURNING * INTO v_ticket;
    EXCEPTION WHEN unique_violation THEN
        GET STACKED DIAGNOSTICS v_conflict = CONSTRAINT_NAME;
        IF v_conflict = 'support_tickets_one_active_per_order_uq' THEN
            -- Lost a race with a concurrent create/reopen.
            RAISE EXCEPTION 'REOPEN_CONFLICT:' USING ERRCODE = '23505',
                HINT = 'Another active ticket exists for this order and category; continue there instead.';
        END IF;
        RAISE;
    END;

    PERFORM public.crave_support_log(p_ticket_id, 'MANAGEMENT',
        CASE WHEN v_old = 'RESOLVED' AND v_new = 'UNDER_REVIEW' THEN 'REOPENED' ELSE 'STATUS_CHANGED' END,
        v_old, v_new, jsonb_build_object('reason', v_reason));
    RETURN v_ticket;
END $$;

CREATE OR REPLACE FUNCTION public.admin_set_support_ticket_priority(p_ticket_id UUID, p_priority TEXT)
RETURNS public.support_tickets
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE
    v_ticket public.support_tickets;
    v_old    TEXT;
    v_new    TEXT := upper(btrim(COALESCE(p_priority, '')));
BEGIN
    PERFORM public.crave_support_require_admin();
    IF v_new NOT IN ('LOW', 'NORMAL', 'HIGH', 'URGENT') THEN
        RAISE EXCEPTION 'INVALID_PRIORITY' USING ERRCODE = '22023';
    END IF;
    SELECT * INTO v_ticket FROM public.support_tickets WHERE id = p_ticket_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'TICKET_NOT_FOUND' USING ERRCODE = 'P0002'; END IF;
    IF v_ticket.status = 'CLOSED' THEN RAISE EXCEPTION 'TICKET_CLOSED' USING ERRCODE = '22023'; END IF;
    v_old := v_ticket.priority;
    IF v_old = v_new THEN RETURN v_ticket; END IF;
    UPDATE public.support_tickets SET priority = v_new WHERE id = p_ticket_id RETURNING * INTO v_ticket;
    PERFORM public.crave_support_log(p_ticket_id, 'MANAGEMENT', 'PRIORITY_CHANGED', v_old, v_new);
    RETURN v_ticket;
END $$;

-- Assign to an active management user, or pass NULL to unassign.
CREATE OR REPLACE FUNCTION public.admin_assign_support_ticket(p_ticket_id UUID, p_assignee UUID)
RETURNS public.support_tickets
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE
    v_ticket public.support_tickets;
    v_old    UUID;
BEGIN
    PERFORM public.crave_support_require_admin();
    IF p_assignee IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM public.profiles p WHERE p.id = p_assignee AND p.role::text = 'ADMIN' AND p.is_active
    ) THEN
        RAISE EXCEPTION 'ASSIGNEE_NOT_MANAGEMENT' USING ERRCODE = '22023';
    END IF;
    SELECT * INTO v_ticket FROM public.support_tickets WHERE id = p_ticket_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'TICKET_NOT_FOUND' USING ERRCODE = 'P0002'; END IF;
    IF v_ticket.status = 'CLOSED' THEN RAISE EXCEPTION 'TICKET_CLOSED' USING ERRCODE = '22023'; END IF;
    v_old := v_ticket.assigned_to;
    IF v_old IS NOT DISTINCT FROM p_assignee THEN RETURN v_ticket; END IF;
    UPDATE public.support_tickets SET assigned_to = p_assignee WHERE id = p_ticket_id RETURNING * INTO v_ticket;
    PERFORM public.crave_support_log(p_ticket_id, 'MANAGEMENT',
        CASE WHEN p_assignee IS NULL THEN 'UNASSIGNED' ELSE 'ASSIGNED' END,
        v_old::text, p_assignee::text);
    RETURN v_ticket;
END $$;

-- Student-facing response (shown in the student's ticket conversation).
CREATE OR REPLACE FUNCTION public.admin_respond_support_ticket(p_ticket_id UUID, p_body TEXT)
RETURNS public.support_ticket_messages
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE
    v_ticket public.support_tickets;
    v_msg    public.support_ticket_messages;
    v_body   TEXT := public.crave_support_clean(p_body);
BEGIN
    PERFORM public.crave_support_require_admin();
    IF v_body IS NULL OR char_length(v_body) > 4000 THEN
        RAISE EXCEPTION 'INVALID_MESSAGE' USING ERRCODE = '22023';
    END IF;
    SELECT * INTO v_ticket FROM public.support_tickets WHERE id = p_ticket_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'TICKET_NOT_FOUND' USING ERRCODE = 'P0002'; END IF;
    IF v_ticket.status = 'CLOSED' THEN RAISE EXCEPTION 'TICKET_CLOSED' USING ERRCODE = '22023'; END IF;
    INSERT INTO public.support_ticket_messages (ticket_id, author_id, author_role, body)
    VALUES (p_ticket_id, auth.uid(), 'MANAGEMENT', v_body) RETURNING * INTO v_msg;
    UPDATE public.support_tickets SET updated_at = now() WHERE id = p_ticket_id;
    PERFORM public.crave_support_log(p_ticket_id, 'MANAGEMENT', 'MANAGEMENT_RESPONSE', NULL, NULL,
        jsonb_build_object('message_id', v_msg.id));
    RETURN v_msg;
END $$;

-- Internal note: never visible to students or vendors. The audit event only
-- records the note id, not its text.
CREATE OR REPLACE FUNCTION public.admin_add_support_ticket_note(p_ticket_id UUID, p_note TEXT)
RETURNS public.support_ticket_internal_notes
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE
    v_note public.support_ticket_internal_notes;
    v_text TEXT := public.crave_support_clean(p_note);
BEGIN
    PERFORM public.crave_support_require_admin();
    IF v_text IS NULL OR char_length(v_text) > 4000 THEN
        RAISE EXCEPTION 'INVALID_NOTE' USING ERRCODE = '22023';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM public.support_tickets WHERE id = p_ticket_id) THEN
        RAISE EXCEPTION 'TICKET_NOT_FOUND' USING ERRCODE = 'P0002';
    END IF;
    INSERT INTO public.support_ticket_internal_notes (ticket_id, author_id, note)
    VALUES (p_ticket_id, auth.uid(), v_text) RETURNING * INTO v_note;
    PERFORM public.crave_support_log(p_ticket_id, 'MANAGEMENT', 'INTERNAL_NOTE_ADDED', NULL, NULL,
        jsonb_build_object('note_id', v_note.id));
    RETURN v_note;
END $$;

-- Management queue with filters and search. total_count supports paging.
CREATE OR REPLACE FUNCTION public.admin_list_support_tickets(
    p_statuses    TEXT[]      DEFAULT NULL,
    p_categories  TEXT[]      DEFAULT NULL,
    p_priorities  TEXT[]      DEFAULT NULL,
    p_outlet_id   UUID        DEFAULT NULL,
    p_assigned_to UUID        DEFAULT NULL,
    p_from        TIMESTAMPTZ DEFAULT NULL,
    p_to          TIMESTAMPTZ DEFAULT NULL,
    p_search      TEXT        DEFAULT NULL,
    p_limit       INTEGER     DEFAULT 50,
    p_offset      INTEGER     DEFAULT 0
) RETURNS TABLE (
    id UUID, ticket_number TEXT, status TEXT, priority TEXT, category TEXT, subject TEXT,
    student_id UUID, student_name TEXT, student_email TEXT,
    order_id UUID, order_number TEXT, outlet_id UUID, outlet_name TEXT,
    assigned_to UUID, assignee_name TEXT,
    created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ, resolved_at TIMESTAMPTZ,
    total_count BIGINT
)
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE
    v_search TEXT := public.crave_support_clean(p_search);
BEGIN
    PERFORM public.crave_support_require_admin();
    RETURN QUERY
    SELECT t.id, t.ticket_number, t.status, t.priority, t.category, t.subject,
           t.student_id, sp.name, sp.email,
           t.order_id, o.order_number, t.outlet_id, ot.name,
           t.assigned_to, ap.name,
           t.created_at, t.updated_at, t.resolved_at,
           count(*) OVER ()
    FROM public.support_tickets t
    JOIN public.profiles sp      ON sp.id = t.student_id
    LEFT JOIN public.orders o    ON o.id = t.order_id
    LEFT JOIN public.outlets ot  ON ot.id = t.outlet_id
    LEFT JOIN public.profiles ap ON ap.id = t.assigned_to
    WHERE (p_statuses   IS NULL OR t.status   = ANY (p_statuses))
      AND (p_categories IS NULL OR t.category = ANY (p_categories))
      AND (p_priorities IS NULL OR t.priority = ANY (p_priorities))
      AND (p_outlet_id   IS NULL OR t.outlet_id   = p_outlet_id)
      AND (p_assigned_to IS NULL OR t.assigned_to = p_assigned_to)
      AND (p_from IS NULL OR t.created_at >= p_from)
      AND (p_to   IS NULL OR t.created_at <  p_to)
      AND (v_search IS NULL
           OR t.ticket_number ILIKE '%' || v_search || '%'
           OR t.subject       ILIKE '%' || v_search || '%'
           OR sp.name         ILIKE '%' || v_search || '%'
           OR sp.email        ILIKE '%' || v_search || '%'
           OR o.order_number  ILIKE '%' || v_search || '%')
    ORDER BY CASE t.priority WHEN 'URGENT' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'NORMAL' THEN 2 ELSE 3 END,
             t.created_at DESC
    LIMIT LEAST(GREATEST(COALESCE(p_limit, 50), 1), 100)
    OFFSET GREATEST(COALESCE(p_offset, 0), 0);
END $$;

-- Full ticket for the management detail view: ticket, conversation, internal
-- notes and audit timeline in one call.
CREATE OR REPLACE FUNCTION public.admin_get_support_ticket(p_ticket_id UUID) RETURNS JSONB
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE
    v_result JSONB;
BEGIN
    PERFORM public.crave_support_require_admin();
    SELECT jsonb_build_object(
        'ticket', to_jsonb(t) - 'client_request_id',
        'student', jsonb_build_object('id', sp.id, 'name', sp.name, 'email', sp.email,
                                      'registration_number', sp.registration_number),
        'order', CASE WHEN o.id IS NULL THEN NULL ELSE jsonb_build_object(
                     'id', o.id, 'order_number', o.order_number, 'status', o.status,
                     'payment_status', o.payment_status, 'total', o.total, 'created_at', o.created_at) END,
        'outlet', CASE WHEN ot.id IS NULL THEN NULL ELSE jsonb_build_object(
                     'id', ot.id, 'name', ot.name, 'is_active', ot.is_active, 'is_open', ot.is_open) END,
        'assignee', CASE WHEN ap.id IS NULL THEN NULL ELSE jsonb_build_object('id', ap.id, 'name', ap.name) END,
        'messages', COALESCE((SELECT jsonb_agg(jsonb_build_object(
                         'id', m.id, 'author_id', m.author_id, 'author_role', m.author_role,
                         'body', m.body, 'created_at', m.created_at) ORDER BY m.created_at)
                     FROM public.support_ticket_messages m WHERE m.ticket_id = t.id), '[]'::jsonb),
        'internal_notes', COALESCE((SELECT jsonb_agg(jsonb_build_object(
                         'id', n.id, 'author_id', n.author_id, 'author_name', np.name,
                         'note', n.note, 'created_at', n.created_at) ORDER BY n.created_at)
                     FROM public.support_ticket_internal_notes n
                     LEFT JOIN public.profiles np ON np.id = n.author_id
                     WHERE n.ticket_id = t.id), '[]'::jsonb),
        'events', COALESCE((SELECT jsonb_agg(jsonb_build_object(
                         'id', e.id, 'actor_id', e.actor_id, 'actor_name', ep.name, 'actor_role', e.actor_role,
                         'action', e.action, 'from_value', e.from_value, 'to_value', e.to_value,
                         'metadata', e.metadata, 'created_at', e.created_at) ORDER BY e.id)
                     FROM public.support_ticket_events e
                     LEFT JOIN public.profiles ep ON ep.id = e.actor_id
                     WHERE e.ticket_id = t.id), '[]'::jsonb)
    ) INTO v_result
    FROM public.support_tickets t
    JOIN public.profiles sp      ON sp.id = t.student_id
    LEFT JOIN public.orders o    ON o.id = t.order_id
    LEFT JOIN public.outlets ot  ON ot.id = t.outlet_id
    LEFT JOIN public.profiles ap ON ap.id = t.assigned_to
    WHERE t.id = p_ticket_id;

    IF v_result IS NULL THEN RAISE EXCEPTION 'TICKET_NOT_FOUND' USING ERRCODE = 'P0002'; END IF;
    RETURN v_result;
END $$;

-- -----------------------------------------------------------------------------
-- 7. Function privileges
-- -----------------------------------------------------------------------------
-- Supabase grants EXECUTE on new public functions to anon by default, and
-- REVOKE ... FROM PUBLIC alone does not remove it. Lock every function down
-- explicitly and expose only the RPCs to `authenticated`.

REVOKE ALL ON FUNCTION public.crave_support_is_admin()                        FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.crave_support_is_student()                      FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.crave_support_touch_updated_at()                FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.crave_support_guard_ticket_update()             FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.crave_support_append_only()                     FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.crave_support_log(UUID, TEXT, TEXT, TEXT, TEXT, JSONB) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.crave_support_clean(TEXT)                       FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.crave_support_require_admin()                   FROM PUBLIC, anon, authenticated;

-- RLS policies call crave_support_is_admin() as the querying user.
GRANT EXECUTE ON FUNCTION public.crave_support_is_admin() TO authenticated;

REVOKE ALL ON FUNCTION public.create_support_ticket(TEXT, TEXT, TEXT, UUID, UUID, UUID, UUID) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.add_support_ticket_message(UUID, TEXT)                         FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.admin_set_support_ticket_status(UUID, TEXT, TEXT, TEXT)        FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.admin_set_support_ticket_priority(UUID, TEXT)                  FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.admin_assign_support_ticket(UUID, UUID)                        FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.admin_respond_support_ticket(UUID, TEXT)                       FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.admin_add_support_ticket_note(UUID, TEXT)                      FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.admin_list_support_tickets(TEXT[], TEXT[], TEXT[], UUID, UUID, TIMESTAMPTZ, TIMESTAMPTZ, TEXT, INTEGER, INTEGER) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.admin_get_support_ticket(UUID)                                 FROM PUBLIC, anon;

GRANT EXECUTE ON FUNCTION public.create_support_ticket(TEXT, TEXT, TEXT, UUID, UUID, UUID, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.add_support_ticket_message(UUID, TEXT)                         TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_set_support_ticket_status(UUID, TEXT, TEXT, TEXT)        TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_set_support_ticket_priority(UUID, TEXT)                  TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_assign_support_ticket(UUID, UUID)                        TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_respond_support_ticket(UUID, TEXT)                       TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_add_support_ticket_note(UUID, TEXT)                      TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_list_support_tickets(TEXT[], TEXT[], TEXT[], UUID, UUID, TIMESTAMPTZ, TIMESTAMPTZ, TEXT, INTEGER, INTEGER) TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_get_support_ticket(UUID)                                 TO authenticated;

COMMIT;
