SELECT json_build_object(
    'specific_outlet', (
        SELECT json_build_object(
            'id', id, 'name', name, 'vendor_id', vendor_id, 'is_active', is_active, 'is_open', is_open
        )
        FROM public.outlets
        WHERE id = 'c5409e94-4ba2-5c38-8958-7a5121f78f04'
    ),
    'outlet_vendor_null_counts', (
        SELECT json_agg(json_build_object('is_null', is_null, 'count', count))
        FROM (SELECT vendor_id IS NULL as is_null, COUNT(*) FROM public.outlets GROUP BY vendor_id IS NULL) as t
    ),
    'outlet_vendor_roles', (
        SELECT json_agg(json_build_object('role', role, 'count', count))
        FROM (
            SELECT p.role, COUNT(*) 
            FROM public.outlets o
            JOIN public.profiles p ON o.vendor_id = p.id
            GROUP BY p.role
        ) as t
    ),
    'orders_summary', (
        SELECT json_agg(json_build_object('outlet_id', outlet_id, 'vendor_id', vendor_id, 'count', count))
        FROM (
            SELECT outlet_id, vendor_id, COUNT(*)
            FROM public.orders
            GROUP BY outlet_id, vendor_id
            ORDER BY COUNT(*) DESC
            LIMIT 20
        ) as t
    )
) as diag_result;
