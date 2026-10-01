SELECT column_name, data_type, udt_name, is_nullable, column_default
FROM information_schema.columns
WHERE table_name = 'payments' AND table_schema = 'public';

SELECT typname, enumlabel
FROM pg_enum
JOIN pg_type ON pg_enum.enumtypid = pg_type.oid
WHERE typname IN ('payment_status', 'payment_provider')
ORDER BY typname, enumsortorder;

SELECT pg_get_functiondef(oid) as def
FROM pg_proc
WHERE proname IN ('place_order', 'mark_payment_verified') AND pronamespace = 'public'::regnamespace;

SELECT * FROM supabase_migrations.schema_migrations ORDER BY version DESC LIMIT 10;
