-- =============================================================================
-- DRAFT — NOT A MIGRATION. DO NOT APPLY WITHOUT EXPLICIT APPROVAL.
--
-- Device push tokens for FCM. The repo had no table or column for them (Android's updateFcmToken was a no-op).
--
-- Rules enforced here (not in the client):
--   * A token belongs to exactly ONE user at a time. Registering a token that another account used (shared
--     phone, account switch) MOVES it, so the previous user stops receiving that device's notifications.
--   * A user can only register / remove tokens for themselves; nobody can read other users' tokens.
--   * The table is not directly readable or writable by clients at all; only the two RPCs below
--     (SECURITY DEFINER, caller-checked) and the service role (the delivery Edge Function) touch it.
-- =============================================================================
BEGIN;

CREATE TABLE IF NOT EXISTS public.device_push_tokens (
    token       TEXT PRIMARY KEY CHECK (char_length(token) BETWEEN 20 AND 4096),
    user_id     UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    platform    TEXT NOT NULL DEFAULT 'android' CHECK (platform IN ('android')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS device_push_tokens_user_idx ON public.device_push_tokens (user_id);

ALTER TABLE public.device_push_tokens ENABLE ROW LEVEL SECURITY;      -- no policies: clients get nothing directly
REVOKE ALL ON public.device_push_tokens FROM PUBLIC, anon, authenticated;
GRANT ALL ON public.device_push_tokens TO service_role;

CREATE OR REPLACE FUNCTION public.register_push_token(p_token TEXT, p_platform TEXT DEFAULT 'android') RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp AS $$
DECLARE v_uid UUID := auth.uid();
BEGIN
    IF v_uid IS NULL THEN RAISE EXCEPTION 'Authentication required.' USING ERRCODE = '42501'; END IF;
    IF p_token IS NULL OR char_length(p_token) NOT BETWEEN 20 AND 4096 THEN
        RAISE EXCEPTION 'INVALID_TOKEN' USING ERRCODE = '22023';
    END IF;
    INSERT INTO public.device_push_tokens (token, user_id, platform)
    VALUES (p_token, v_uid, COALESCE(NULLIF(p_platform, ''), 'android'))
    ON CONFLICT (token) DO UPDATE SET user_id = EXCLUDED.user_id, last_seen = now();   -- idempotent; moves between accounts
END $$;

CREATE OR REPLACE FUNCTION public.unregister_push_token(p_token TEXT) RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp AS $$
BEGIN
    IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Authentication required.' USING ERRCODE = '42501'; END IF;
    DELETE FROM public.device_push_tokens WHERE token = p_token AND user_id = auth.uid();   -- only your own
END $$;

REVOKE ALL ON FUNCTION public.register_push_token(TEXT, TEXT), public.unregister_push_token(TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.register_push_token(TEXT, TEXT), public.unregister_push_token(TEXT) TO authenticated;

COMMIT;
