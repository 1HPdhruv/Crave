SELECT json_agg(json_build_object(
    'schema_name', n.nspname,
    'function_name', p.proname,
    'arguments', pg_get_function_identity_arguments(p.oid)
)) AS functions
FROM pg_proc p
JOIN pg_namespace n ON n.oid = p.pronamespace
WHERE pg_get_functiondef(p.oid) ILIKE '%Outlet is temporarily closed right now%';
