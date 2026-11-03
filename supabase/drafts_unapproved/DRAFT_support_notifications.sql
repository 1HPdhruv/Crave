-- =============================================================================
-- DRAFT — NOT A MIGRATION. DO NOT APPLY WITHOUT EXPLICIT APPROVAL.
-- Student notifications for support tickets, reusing the EXISTING `notifications` table
-- (no second notification system) and the EXISTING enum value GENERAL (no ALTER TYPE:
-- adding enum values can't be used in the same transaction, so this avoids that trap).
-- The ticket is identified by notifications.deep_link = 'ticket:<ticket id>:<event key>';
-- the Android app routes that link to the ticket screen.
--
-- Notified (student only): a management reply, a request for more information
-- (WAITING_FOR_STUDENT), resolution, closure.
-- NEVER used: internal notes. They live in support_ticket_internal_notes, which neither
-- trigger reads.
--
-- Duplicates: each trigger fires once per real change, and a partial unique index on
-- (user_id, deep_link) + ON CONFLICT DO NOTHING makes a replay a no-op.
-- A notification failure can't roll back the ticket change: each insert is wrapped in an
-- exception block that downgrades the error to a WARNING.
--
-- ASSUMPTIONS to verify on staging: notifications(user_id,title,body,type,order_id,is_read,deep_link)
-- as in migration 001, and that enum notification_type contains GENERAL.
-- =============================================================================
BEGIN;

CREATE UNIQUE INDEX IF NOT EXISTS notifications_ticket_event_uq
    ON public.notifications (user_id, deep_link) WHERE deep_link LIKE 'ticket:%';

CREATE OR REPLACE FUNCTION public.crave_support_notify_message() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp AS $$
DECLARE v_ticket public.support_tickets;
BEGIN
    BEGIN
        SELECT * INTO v_ticket FROM public.support_tickets WHERE id = NEW.ticket_id;
        IF FOUND THEN
            INSERT INTO public.notifications (user_id, title, body, type, deep_link)
            VALUES (v_ticket.student_id,
                    'Reply on ticket ' || v_ticket.ticket_number,
                    pg_catalog.left(NEW.body, 140),
                    'GENERAL',
                    'ticket:' || v_ticket.id || ':msg:' || NEW.id)
            ON CONFLICT DO NOTHING;
        END IF;
    EXCEPTION WHEN OTHERS THEN
        RAISE WARNING 'support notification (message) failed: %', SQLERRM;
    END;
    RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION public.crave_support_notify_message() FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS support_ticket_message_notify ON public.support_ticket_messages;
CREATE TRIGGER support_ticket_message_notify AFTER INSERT ON public.support_ticket_messages
    FOR EACH ROW WHEN (NEW.author_role = 'MANAGEMENT')
    EXECUTE FUNCTION public.crave_support_notify_message();

CREATE OR REPLACE FUNCTION public.crave_support_notify_status() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp AS $$
DECLARE v_title TEXT; v_body TEXT;
BEGIN
    BEGIN
        IF NEW.status = 'WAITING_FOR_STUDENT' THEN
            v_title := 'We need more information'; v_body := 'Ticket ' || NEW.ticket_number || ': please reply so we can continue.';
        ELSIF NEW.status = 'RESOLVED' THEN
            v_title := 'Ticket resolved'; v_body := 'Ticket ' || NEW.ticket_number || ' was resolved. ' || COALESCE(pg_catalog.left(NEW.resolution_reason, 120), '');
        ELSIF NEW.status = 'CLOSED' THEN
            v_title := 'Ticket closed'; v_body := 'Ticket ' || NEW.ticket_number || ' was closed. ' || COALESCE(pg_catalog.left(NEW.resolution_reason, 120), '');
        ELSE
            RETURN NEW;
        END IF;
        INSERT INTO public.notifications (user_id, title, body, type, deep_link)
        VALUES (NEW.student_id, v_title, pg_catalog.btrim(v_body), 'GENERAL',
                'ticket:' || NEW.id || ':status:' || NEW.status || ':' ||
                (extract(epoch FROM NEW.updated_at) * 1000000)::bigint)
        ON CONFLICT DO NOTHING;
    EXCEPTION WHEN OTHERS THEN
        RAISE WARNING 'support notification (status) failed: %', SQLERRM;
    END;
    RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION public.crave_support_notify_status() FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS support_ticket_status_notify ON public.support_tickets;
CREATE TRIGGER support_ticket_status_notify AFTER UPDATE OF status ON public.support_tickets
    FOR EACH ROW WHEN (OLD.status IS DISTINCT FROM NEW.status)
    EXECUTE FUNCTION public.crave_support_notify_status();

COMMIT;
