SELECT
    NOW() AS db_now,
    NOW() AT TIME ZONE 'Asia/Kolkata' AS ist_now,
    (NOW() AT TIME ZONE 'Asia/Kolkata')::date AS ist_date,
    (NOW() AT TIME ZONE 'Asia/Kolkata')::time AS ist_time;
