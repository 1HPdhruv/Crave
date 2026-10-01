SELECT json_build_object(
    'schema_columns', (
        SELECT json_agg(json_build_object(
            'table', table_name, 'column', column_name, 'type', data_type, 'nullable', is_nullable
        ))
        FROM information_schema.columns
        WHERE table_name IN ('outlets', 'orders', 'profiles')
    )
) as diag_result;
