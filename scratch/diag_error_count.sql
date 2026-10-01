SELECT COUNT(*)
FROM pg_proc p
WHERE pg_get_functiondef(p.oid) ILIKE '%Outlet is temporarily closed right now%';
