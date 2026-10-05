-- =============================================================================
-- Migration 019: Add Analytics Trends for Management Analytics
-- =============================================================================

BEGIN;

CREATE OR REPLACE FUNCTION get_management_analytics() RETURNS JSONB
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    v_user_id UUID := auth.uid();
    v_role TEXT;
    v_result JSONB;
BEGIN
    IF v_user_id IS NULL THEN RAISE EXCEPTION 'Authentication required.'; END IF;

    -- Verify admin role from profiles table
    SELECT role INTO v_role FROM profiles WHERE id = v_user_id;
    IF v_role != 'ADMIN' THEN RAISE EXCEPTION 'Unauthorized: Requires ADMIN role.'; END IF;

    -- Compute detailed analytics including trend time series in a single JSON block
    SELECT jsonb_build_object(
        -- TIME-BASED METRICS (Asia/Kolkata)
        'ordersToday', (SELECT count(*) FROM orders WHERE (placed_at AT TIME ZONE 'Asia/Kolkata')::date = (now() AT TIME ZONE 'Asia/Kolkata')::date),
        'revenueToday', COALESCE((
            SELECT sum(amount) FROM payments
            WHERE status IN ('PAID', 'CAPTURED')
            AND (created_at AT TIME ZONE 'Asia/Kolkata')::date = (now() AT TIME ZONE 'Asia/Kolkata')::date
        ), 0),
        'ordersThisWeek', (SELECT count(*) FROM orders WHERE date_trunc('week', placed_at AT TIME ZONE 'Asia/Kolkata') = date_trunc('week', now() AT TIME ZONE 'Asia/Kolkata')),
        'revenueThisWeek', COALESCE((
            SELECT sum(amount) FROM payments
            WHERE status IN ('PAID', 'CAPTURED')
            AND date_trunc('week', created_at AT TIME ZONE 'Asia/Kolkata') = date_trunc('week', now() AT TIME ZONE 'Asia/Kolkata')
        ), 0),
        'ordersThisMonth', (SELECT count(*) FROM orders WHERE date_trunc('month', placed_at AT TIME ZONE 'Asia/Kolkata') = date_trunc('month', now() AT TIME ZONE 'Asia/Kolkata')),
        'revenueThisMonth', COALESCE((
            SELECT sum(amount) FROM payments
            WHERE status IN ('PAID', 'CAPTURED')
            AND date_trunc('month', created_at AT TIME ZONE 'Asia/Kolkata') = date_trunc('month', now() AT TIME ZONE 'Asia/Kolkata')
        ), 0),

        -- ORDER METRICS
        'totalOrders', (SELECT count(*) FROM orders),
        'completedOrders', (SELECT count(*) FROM orders WHERE status = 'PICKED_UP'),
        'cancelledOrders', (SELECT count(*) FROM orders WHERE status = 'CANCELLED'),
        'rejectedOrders', (SELECT count(*) FROM orders WHERE status = 'REJECTED'),
        'activeOrders', (SELECT count(*) FROM orders WHERE status IN ('PLACED', 'ACCEPTED', 'PREPARING', 'READY')),

        -- PAYMENT METRICS
        'paidRevenue', COALESCE((SELECT sum(amount) FROM payments WHERE status IN ('PAID', 'CAPTURED')), 0),
        'refundedAmount', COALESCE((SELECT sum(amount) FROM payments WHERE status = 'REFUNDED'), 0),
        'cashRevenue', COALESCE((SELECT sum(amount) FROM payments WHERE status IN ('PAID', 'CAPTURED') AND gateway_provider = 'CASH'), 0),
        'razorpayRevenue', COALESCE((SELECT sum(amount) FROM payments WHERE status IN ('PAID', 'CAPTURED') AND gateway_provider = 'RAZORPAY'), 0),

        -- PLATFORM METRICS
        'totalStudents', (SELECT count(*) FROM profiles WHERE role = 'STUDENT'),
        'totalVendors', (SELECT count(*) FROM profiles WHERE role = 'VENDOR'),
        'totalOutlets', (SELECT count(*) FROM outlets),
        'activeOutlets', (SELECT count(*) FROM outlets WHERE is_open = true AND is_active = true),

        -- FOOD METRICS
        'totalFoodItems', (SELECT count(*) FROM food_items),
        'availableFoodItems', (SELECT count(*) FROM food_items WHERE is_available = true),

        -- TREND DATA (TODAY - Hourly)
        'trendToday', COALESCE((
            SELECT jsonb_agg(
                jsonb_build_object(
                    'label', l.hr || ':00',
                    'orders', COALESCE(o.cnt, 0),
                    'revenue', COALESCE(r.amt, 0.0)
                ) ORDER BY l.hr
            )
            FROM generate_series(0, 23) l(hr)
            LEFT JOIN (
                SELECT extract(hour from placed_at AT TIME ZONE 'Asia/Kolkata') as hr, count(*) as cnt
                FROM orders
                WHERE (placed_at AT TIME ZONE 'Asia/Kolkata')::date = (now() AT TIME ZONE 'Asia/Kolkata')::date
                GROUP BY hr
            ) o ON l.hr = o.hr
            LEFT JOIN (
                SELECT extract(hour from created_at AT TIME ZONE 'Asia/Kolkata') as hr, sum(amount) as amt
                FROM payments
                WHERE status IN ('PAID', 'CAPTURED')
                AND (created_at AT TIME ZONE 'Asia/Kolkata')::date = (now() AT TIME ZONE 'Asia/Kolkata')::date
                GROUP BY hr
            ) r ON l.hr = r.hr
        ), '[]'::jsonb),

        -- TREND DATA (THIS WEEK - Daily)
        'trendWeek', COALESCE((
            SELECT jsonb_agg(
                jsonb_build_object(
                    'label', to_char(d.dt, 'Dy'),
                    'orders', COALESCE(o.cnt, 0),
                    'revenue', COALESCE(r.amt, 0.0)
                ) ORDER BY d.dt
            )
            FROM (
                SELECT generate_series(
                    date_trunc('week', now() AT TIME ZONE 'Asia/Kolkata'),
                    date_trunc('week', now() AT TIME ZONE 'Asia/Kolkata') + interval '6 days',
                    interval '1 day'
                )::date as dt
            ) d
            LEFT JOIN (
                SELECT (placed_at AT TIME ZONE 'Asia/Kolkata')::date as dt, count(*) as cnt
                FROM orders
                WHERE date_trunc('week', placed_at AT TIME ZONE 'Asia/Kolkata') = date_trunc('week', now() AT TIME ZONE 'Asia/Kolkata')
                GROUP BY dt
            ) o ON d.dt = o.dt
            LEFT JOIN (
                SELECT (created_at AT TIME ZONE 'Asia/Kolkata')::date as dt, sum(amount) as amt
                FROM payments
                WHERE status IN ('PAID', 'CAPTURED')
                AND date_trunc('week', created_at AT TIME ZONE 'Asia/Kolkata') = date_trunc('week', now() AT TIME ZONE 'Asia/Kolkata')
                GROUP BY dt
            ) r ON d.dt = r.dt
        ), '[]'::jsonb),

        -- TREND DATA (THIS MONTH - Daily)
        'trendMonth', COALESCE((
            SELECT jsonb_agg(
                jsonb_build_object(
                    'label', to_char(d.dt, 'DD Mon'),
                    'orders', COALESCE(o.cnt, 0),
                    'revenue', COALESCE(r.amt, 0.0)
                ) ORDER BY d.dt
            )
            FROM (
                SELECT generate_series(
                    date_trunc('month', now() AT TIME ZONE 'Asia/Kolkata'),
                    least(now() AT TIME ZONE 'Asia/Kolkata', date_trunc('month', now() AT TIME ZONE 'Asia/Kolkata') + interval '1 month' - interval '1 day')::date,
                    interval '1 day'
                )::date as dt
            ) d
            LEFT JOIN (
                SELECT (placed_at AT TIME ZONE 'Asia/Kolkata')::date as dt, count(*) as cnt
                FROM orders
                WHERE date_trunc('month', placed_at AT TIME ZONE 'Asia/Kolkata') = date_trunc('month', now() AT TIME ZONE 'Asia/Kolkata')
                GROUP BY dt
            ) o ON d.dt = o.dt
            LEFT JOIN (
                SELECT (created_at AT TIME ZONE 'Asia/Kolkata')::date as dt, sum(amount) as amt
                FROM payments
                WHERE status IN ('PAID', 'CAPTURED')
                AND date_trunc('month', created_at AT TIME ZONE 'Asia/Kolkata') = date_trunc('month', now() AT TIME ZONE 'Asia/Kolkata')
                GROUP BY dt
            ) r ON d.dt = r.dt
        ), '[]'::jsonb)
    ) INTO v_result;

    RETURN v_result;
END;
$$;

COMMIT;
