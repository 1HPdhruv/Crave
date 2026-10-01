-- 1. Inspect definition of public.place_order
SELECT pg_get_functiondef(oid) as def, oid::regprocedure as sig
FROM pg_proc 
WHERE proname = 'place_order' AND pronamespace = 'public'::regnamespace;

-- 4. Check overloads
SELECT oid::regprocedure as sig
FROM pg_proc 
WHERE proname = 'place_order' AND pronamespace = 'public'::regnamespace;

-- 5. Check migration history
SELECT * FROM supabase_migrations.schema_migrations ORDER BY version DESC LIMIT 5;

-- 7 & 8. Verify future pickup slots and outlets
SELECT 
    ps.id as slot_id, ps.outlet_id, ps.slot_date, ps.start_time, ps.end_time, 
    ps.status as slot_status, ps.booked_count, ps.capacity,
    o.is_active, o.is_open, o.operating_hours
FROM public.pickup_slots ps
JOIN public.outlets o ON ps.outlet_id = o.id
WHERE ps.slot_date >= current_date
ORDER BY ps.slot_date, ps.start_time;
