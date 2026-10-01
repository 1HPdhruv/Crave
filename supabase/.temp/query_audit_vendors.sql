SELECT json_build_object(
    'profiles_summary', (
        SELECT json_build_object(
            'total', COUNT(*),
            'vendor_count', COUNT(*) FILTER (WHERE role = 'VENDOR'),
            'student_count', COUNT(*) FILTER (WHERE role = 'STUDENT'),
            'admin_count', COUNT(*) FILTER (WHERE role = 'ADMIN')
        ) FROM public.profiles
    ),
    'vendor_profiles', (
        SELECT COALESCE(json_agg(json_build_object(
            'id', p.id,
            'name', p.name,
            'email', p.email,
            'role', p.role,
            'has_auth_user', EXISTS(SELECT 1 FROM auth.users au WHERE au.id = p.id)
        )), '[]'::json)
        FROM public.profiles p
        WHERE p.role = 'VENDOR'
    ),
    'outlets', (
        SELECT COALESCE(json_agg(json_build_object(
            'id', id,
            'name', name,
            'vendor_id', vendor_id,
            'is_active', is_active,
            'is_open', is_open
        )), '[]'::json)
        FROM public.outlets
    )
) as audit_result;
