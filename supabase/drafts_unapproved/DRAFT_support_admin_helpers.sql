-- =============================================================================
-- DRAFT — NOT A MIGRATION. DO NOT APPLY WITHOUT EXPLICIT APPROVAL.
-- Depends on migration 023 (support tickets) and 018/019 (reviews). Read-only helpers
-- for the management ticket screen. No tables change; both functions are ADMIN-only
-- and re-check the caller themselves (they bypass RLS).
--
-- ASSUMPTIONS to verify on staging: profiles(id,name,role,is_active), reviews(...is_visible),
-- outlet_reviews(...), as defined in the repo migrations 018/019.
-- =============================================================================
BEGIN;

-- Eligible assignees = active ADMIN profiles (the exact rule admin_assign_support_ticket enforces).
CREATE OR REPLACE FUNCTION public.admin_list_support_assignees()
RETURNS TABLE (id UUID, name TEXT)
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp AS $$
BEGIN
    PERFORM public.crave_support_require_admin();
    RETURN QUERY
        SELECT p.id, p.name FROM public.profiles p
        WHERE p.role::text = 'ADMIN' AND p.is_active
        ORDER BY p.name;
END $$;
REVOKE ALL ON FUNCTION public.admin_list_support_assignees() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.admin_list_support_assignees() TO authenticated;

-- Reviews the ticket's student left for the linked order (food reviews + outlet review).
-- Includes hidden ones: management needs them for moderation context.
CREATE OR REPLACE FUNCTION public.admin_get_support_ticket_reviews(p_ticket_id UUID) RETURNS JSONB
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp AS $$
DECLARE
    v_ticket public.support_tickets;
BEGIN
    PERFORM public.crave_support_require_admin();
    SELECT * INTO v_ticket FROM public.support_tickets WHERE id = p_ticket_id;
    IF NOT FOUND THEN RAISE EXCEPTION 'TICKET_NOT_FOUND' USING ERRCODE = 'P0002'; END IF;
    IF v_ticket.order_id IS NULL THEN
        RETURN jsonb_build_object('food_reviews', '[]'::jsonb, 'outlet_review', NULL);
    END IF;
    RETURN jsonb_build_object(
        'food_reviews', COALESCE((
            SELECT jsonb_agg(jsonb_build_object(
                'id', r.id, 'food_item_id', r.food_item_id, 'food_name', fi.name,
                'rating', r.rating, 'review_text', r.review_text, 'is_visible', r.is_visible,
                'created_at', r.created_at) ORDER BY r.created_at)
            FROM public.reviews r JOIN public.food_items fi ON fi.id = r.food_item_id
            WHERE r.order_id = v_ticket.order_id AND r.student_id = v_ticket.student_id), '[]'::jsonb),
        'outlet_review', (
            SELECT jsonb_build_object('id', o.id, 'rating', o.rating, 'review_text', o.review_text,
                                      'is_visible', o.is_visible, 'created_at', o.created_at)
            FROM public.outlet_reviews o
            WHERE o.order_id = v_ticket.order_id AND o.student_id = v_ticket.student_id LIMIT 1)
    );
END $$;
REVOKE ALL ON FUNCTION public.admin_get_support_ticket_reviews(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.admin_get_support_ticket_reviews(UUID) TO authenticated;

COMMIT;
