-- 1. Get the exact currently deployed production definition
SELECT pg_get_functiondef('public.place_order(uuid,uuid,text)'::regprocedure);

-- 4. Also query the production clock
SELECT
    NOW() AS db_now,
    NOW() AT TIME ZONE 'Asia/Kolkata' AS ist_now,
    (NOW() AT TIME ZONE 'Asia/Kolkata')::date AS ist_date,
    (NOW() AT TIME ZONE 'Asia/Kolkata')::time AS ist_time;

-- 8. Check for ALL functions containing the exact error message
SELECT
    n.nspname AS schema_name,
    p.proname AS function_name,
    pg_get_function_identity_arguments(p.oid) AS arguments
FROM pg_proc p
JOIN pg_namespace n ON n.oid = p.pronamespace
WHERE pg_get_functiondef(p.oid) ILIKE '%Outlet is temporarily closed right now%';

-- 9. Check the exact function count/signature
SELECT
    p.oid::regprocedure
FROM pg_proc p
JOIN pg_namespace n ON n.oid = p.pronamespace
WHERE n.nspname = 'public'
  AND p.proname = 'place_order';
