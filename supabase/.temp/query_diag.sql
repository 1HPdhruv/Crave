-- Overloads
SELECT oid::regprocedure as sig
FROM pg_proc 
WHERE proname = 'place_order' AND pronamespace = 'public'::regnamespace;

-- Migration tables
SELECT table_schema, table_name FROM information_schema.tables WHERE table_name LIKE '%migration%';

-- Future slots
SELECT 
    ps.id as slot_id, ps.outlet_id, ps.slot_date, ps.start_time, ps.end_time, 
    ps.status as slot_status, ps.booked_count, ps.capacity,
    o.is_active, o.is_open, o.operating_hours
FROM public.pickup_slots ps
JOIN public.outlets o ON ps.outlet_id = o.id
WHERE ps.slot_date >= (NOW() AT TIME ZONE 'UTC' AT TIME ZONE 'Asia/Kolkata')::date
ORDER BY ps.slot_date, ps.start_time
LIMIT 5;

-- IST time
SELECT NOW() AT TIME ZONE 'UTC' AT TIME ZONE 'Asia/Kolkata' as ist_now;
