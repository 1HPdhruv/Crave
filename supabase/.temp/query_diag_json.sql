SELECT json_build_object(
    'overloads', (
        SELECT json_agg(oid::regprocedure)
        FROM pg_proc 
        WHERE proname = 'place_order' AND pronamespace = 'public'::regnamespace
    ),
    'migration_tables', (
        SELECT json_agg(table_schema || '.' || table_name) 
        FROM information_schema.tables 
        WHERE table_name LIKE '%migration%'
    ),
    'future_slots', (
        SELECT json_agg(t) FROM (
            SELECT 
                ps.id as slot_id, ps.outlet_id, ps.slot_date, ps.start_time, ps.end_time, 
                ps.status as slot_status, ps.booked_count, ps.capacity,
                o.is_active, o.is_open, o.operating_hours
            FROM public.pickup_slots ps
            JOIN public.outlets o ON ps.outlet_id = o.id
            WHERE ps.slot_date >= (NOW() AT TIME ZONE 'UTC' AT TIME ZONE 'Asia/Kolkata')::date
            ORDER BY ps.slot_date, ps.start_time
            LIMIT 5
        ) t
    ),
    'ist_now', (SELECT NOW() AT TIME ZONE 'UTC' AT TIME ZONE 'Asia/Kolkata')
) as diag_result;
