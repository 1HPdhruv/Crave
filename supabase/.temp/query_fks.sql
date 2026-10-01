SELECT json_agg(json_build_object(
    'table', tc.table_name, 'column', kcu.column_name, 'ref_table', ccu.table_name, 'ref_column', ccu.column_name
))
FROM information_schema.table_constraints AS tc
JOIN information_schema.key_column_usage AS kcu ON tc.constraint_name = kcu.constraint_name
JOIN information_schema.constraint_column_usage AS ccu ON ccu.constraint_name = tc.constraint_name
WHERE tc.constraint_type = 'FOREIGN KEY' AND kcu.column_name = 'vendor_id';
