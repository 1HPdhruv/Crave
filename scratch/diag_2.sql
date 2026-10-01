SELECT
    NOW() AS db_now,
    NOW() AT TIME ZONE 'Asia/Kolkata' AS ist_now,
    (NOW() AT TIME ZONE 'Asia/Kolkata')::date AS ist_date,
    (NOW() AT TIME ZONE 'Asia/Kolkata')::time AS ist_time;

SELECT
    n.nspname AS schema_name,
    p.proname AS function_name,
    pg_get_function_identity_arguments(p.oid) AS arguments
FROM pg_proc p
JOIN pg_namespace n ON n.oid = p.pronamespace
WHERE pg_get_functiondef(p.oid) ILIKE '%Outlet is temporarily closed right now%';

SELECT
    p.oid::regprocedure
FROM pg_proc p
JOIN pg_namespace n ON n.oid = p.pronamespace
WHERE n.nspname = 'public'
  AND p.proname = 'place_order';

SELECT
    c.id AS cart_id,
    c.user_id,
    c.outlet_id,
    o.name AS outlet_name,
    o.is_active,
    o.is_open,
    o.opening_time,
    o.closing_time
FROM public.carts c
JOIN public.outlets o ON o.id = c.outlet_id
WHERE EXISTS (
      SELECT 1
      FROM public.cart_items ci
      WHERE ci.cart_id = c.id
  )
ORDER BY c.updated_at DESC
LIMIT 1;
