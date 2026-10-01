SELECT
    id,
    slot_date,
    start_time,
    end_time,
    status,
    capacity,
    booked_count
FROM public.pickup_slots
WHERE outlet_id = 'c5409e94-4ba2-5c38-8958-7a5121f78f04'
  AND slot_date IN (
      DATE '2026-09-21',
      DATE '2026-09-22'
  )
ORDER BY slot_date, start_time;
