-- =============================================================================
-- DRAFT — NOT A MIGRATION. DO NOT APPLY WITHOUT EXPLICIT APPROVAL.
--
-- Problem (repo policies 002): vendors have FOR ALL policies (no WITH CHECK, no column limits) on
-- outlets, food_items, pickup_slots and inventory. Via the API a vendor could therefore:
--   * re-activate / re-assign their outlet (is_active, vendor_id) or forge rating / total_reviews,
--   * forge a food item's rating, total_reviews, is_popular, is_recommended,
--   * reset a slot's booked_count to 0 (overbooking), or create / move slots into the past.
-- The vendor screens only ever send the allowed columns; these triggers make the DATABASE enforce it.
--
-- Scope: applies ONLY to direct client writes by a VENDOR. Detection = current_user is a client role
-- ('authenticated'/'anon') AND the caller's profile role is VENDOR. Internal SECURITY DEFINER paths
-- (place_order, rating sync triggers, restore-on-cancel) run as the function owner and are untouched;
-- admins are untouched.
--
-- ASSUMPTIONS to verify against the LIVE schema before applying: the columns named below exist
-- (rating, total_reviews, is_popular, is_recommended on food_items; rating, total_reviews, vendor_id,
-- is_active, deleted_at on outlets; booked_count, status, slot_date, start_time, end_time, capacity on
-- pickup_slots) and public.get_user_role() exists.
-- =============================================================================
BEGIN;

-- (The check is inlined in each trigger: these run as the invoking client role, which must not need EXECUTE on a
-- private helper. get_user_role() is already executable by clients because RLS policies call it.)

-- outlets ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.crave_guard_outlet_vendor_columns() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, public, pg_temp AS $$
BEGIN
    IF (current_user IN ('authenticated', 'anon') AND public.get_user_role() = 'VENDOR') AND (
           NEW.vendor_id    IS DISTINCT FROM OLD.vendor_id
        OR NEW.is_active    IS DISTINCT FROM OLD.is_active
        OR NEW.rating       IS DISTINCT FROM OLD.rating
        OR NEW.total_reviews IS DISTINCT FROM OLD.total_reviews
        OR NEW.deleted_at   IS DISTINCT FROM OLD.deleted_at) THEN
        RAISE EXCEPTION 'VENDOR_COLUMN_LOCKED: owner, active flag, ratings and deletion are managed by management.' USING ERRCODE = '42501';
    END IF;
    RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION public.crave_guard_outlet_vendor_columns() FROM PUBLIC, anon, authenticated;
DROP TRIGGER IF EXISTS crave_outlets_vendor_columns ON public.outlets;
CREATE TRIGGER crave_outlets_vendor_columns BEFORE UPDATE ON public.outlets
    FOR EACH ROW EXECUTE FUNCTION public.crave_guard_outlet_vendor_columns();

-- food_items ------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.crave_guard_food_vendor_columns() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, public, pg_temp AS $$
BEGIN
    IF (current_user IN ('authenticated', 'anon') AND public.get_user_role() = 'VENDOR') AND (
           NEW.rating         IS DISTINCT FROM OLD.rating
        OR NEW.total_reviews  IS DISTINCT FROM OLD.total_reviews
        OR NEW.is_popular     IS DISTINCT FROM OLD.is_popular
        OR NEW.is_recommended IS DISTINCT FROM OLD.is_recommended) THEN
        RAISE EXCEPTION 'VENDOR_COLUMN_LOCKED: ratings and featured flags are managed by management.' USING ERRCODE = '42501';
    END IF;
    RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION public.crave_guard_food_vendor_columns() FROM PUBLIC, anon, authenticated;
DROP TRIGGER IF EXISTS crave_food_vendor_columns ON public.food_items;
CREATE TRIGGER crave_food_vendor_columns BEFORE UPDATE ON public.food_items
    FOR EACH ROW EXECUTE FUNCTION public.crave_guard_food_vendor_columns();

-- pickup_slots ----------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.crave_guard_slot_vendor_writes() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, public, pg_temp AS $$
DECLARE v_today DATE := (now() AT TIME ZONE 'Asia/Kolkata')::date;
BEGIN
    IF NOT (current_user IN ('authenticated', 'anon') AND public.get_user_role() = 'VENDOR') THEN RETURN NEW; END IF;

    IF TG_OP = 'INSERT' THEN
        IF NEW.end_time <= NEW.start_time THEN
            RAISE EXCEPTION 'SLOT_INVALID: end time must be after start time.' USING ERRCODE = '22023';
        END IF;
        IF NEW.slot_date < v_today
           OR (NEW.slot_date = v_today AND NEW.start_time <= (now() AT TIME ZONE 'Asia/Kolkata')::time) THEN
            RAISE EXCEPTION 'SLOT_IN_PAST: slots cannot be created in the past (IST).' USING ERRCODE = '22023';
        END IF;
        NEW.booked_count := 0;                                   -- a vendor cannot pre-book capacity
    ELSE
        IF NEW.booked_count IS DISTINCT FROM OLD.booked_count THEN
            RAISE EXCEPTION 'SLOT_COLUMN_LOCKED: bookings are managed by the ordering system.' USING ERRCODE = '42501';
        END IF;
        IF NEW.outlet_id IS DISTINCT FROM OLD.outlet_id OR NEW.slot_date IS DISTINCT FROM OLD.slot_date
           OR NEW.start_time IS DISTINCT FROM OLD.start_time OR NEW.end_time IS DISTINCT FROM OLD.end_time THEN
            RAISE EXCEPTION 'SLOT_COLUMN_LOCKED: only capacity can be changed; create a new slot instead.' USING ERRCODE = '42501';
        END IF;
        IF OLD.slot_date < v_today OR (OLD.slot_date = v_today AND OLD.end_time <= (now() AT TIME ZONE 'Asia/Kolkata')::time) THEN
            RAISE EXCEPTION 'SLOT_IN_PAST: past slots cannot be changed.' USING ERRCODE = '22023';
        END IF;
    END IF;

    -- same thresholds as place_order
    NEW.status := CASE WHEN NEW.booked_count >= NEW.capacity       THEN 'FULL'::pickup_slot_status
                       WHEN NEW.booked_count >= NEW.capacity * 0.8 THEN 'LIMITED'::pickup_slot_status
                       ELSE 'AVAILABLE'::pickup_slot_status END;
    RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION public.crave_guard_slot_vendor_writes() FROM PUBLIC, anon, authenticated;
DROP TRIGGER IF EXISTS crave_slots_vendor_writes ON public.pickup_slots;
CREATE TRIGGER crave_slots_vendor_writes BEFORE INSERT OR UPDATE ON public.pickup_slots
    FOR EACH ROW EXECUTE FUNCTION public.crave_guard_slot_vendor_writes();

COMMIT;
