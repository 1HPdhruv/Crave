SELECT pg_get_functiondef(oid) as def
FROM pg_proc 
WHERE proname = 'place_order' AND pronamespace = 'public'::regnamespace;
