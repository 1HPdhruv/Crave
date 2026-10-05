-- =============================================================================
-- Migration 019: Outlet Reviews System
-- =============================================================================
-- SCOPE: Outlet reviews ONLY (not food reviews).
--
-- Depends on:
--   001 (profiles, outlets, orders)
--   018 (food reviews system)
--
-- Main features:
--   - outlet_reviews
--   - outlet_review_replies
--   - Purchase validation
--   - Vendor ownership validation
--   - Rating aggregation for outlets
--   - RLS
--   - Outlet review RPCs
-- =============================================================================

BEGIN;

-- =============================================================================
-- SECTION 1: CREATE outlet_reviews TABLE
-- =============================================================================

CREATE TABLE outlet_reviews (
    id            UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),

    student_id    UUID        NOT NULL
                  REFERENCES profiles(id)
                  ON DELETE RESTRICT,

    outlet_id     UUID        NOT NULL
                  REFERENCES outlets(id)
                  ON DELETE CASCADE,

    order_id      UUID        NOT NULL
                  REFERENCES orders(id)
                  ON DELETE RESTRICT,

    rating        INTEGER     NOT NULL,

    review_text   TEXT,

    is_visible    BOOLEAN     NOT NULL DEFAULT true,

    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_outlet_review_per_order
        UNIQUE (student_id, order_id),

    CONSTRAINT check_outlet_rating
        CHECK (rating >= 1 AND rating <= 5),

    CONSTRAINT check_outlet_review_text_length
        CHECK (
            review_text IS NULL
            OR char_length(review_text) <= 2000
        )
);

COMMENT ON TABLE outlet_reviews
IS 'Outlet reviews tied to a specific purchased order. One review per order per student.';

COMMENT ON COLUMN outlet_reviews.student_id
IS 'The student who wrote the review (auth.uid()).';

COMMENT ON COLUMN outlet_reviews.order_id
IS 'The exact completed order. Uniqueness is enforced here.';


-- =============================================================================
-- SECTION 2: CREATE outlet_review_replies TABLE
-- =============================================================================

CREATE TABLE outlet_review_replies (
    id               UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),

    outlet_review_id UUID        NOT NULL
                     REFERENCES outlet_reviews(id)
                     ON DELETE CASCADE,

    vendor_id        UUID        NOT NULL
                     REFERENCES profiles(id)
                     ON DELETE RESTRICT,

    reply_text       TEXT        NOT NULL,

    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_reply_per_outlet_review
        UNIQUE (outlet_review_id),

    CONSTRAINT check_outlet_reply_text_length
        CHECK (
            char_length(reply_text) >= 1
            AND char_length(reply_text) <= 1000
        )
);

COMMENT ON TABLE outlet_review_replies
IS 'Vendor reply to a student outlet review. One reply per review.';


-- =============================================================================
-- SECTION 3: updated_at TRIGGERS
-- =============================================================================

DROP TRIGGER IF EXISTS update_outlet_reviews_modtime
ON outlet_reviews;

CREATE TRIGGER update_outlet_reviews_modtime
    BEFORE UPDATE ON outlet_reviews
    FOR EACH ROW
    EXECUTE FUNCTION update_modified_column();


DROP TRIGGER IF EXISTS update_outlet_review_replies_modtime
ON outlet_review_replies;

CREATE TRIGGER update_outlet_review_replies_modtime
    BEFORE UPDATE ON outlet_review_replies
    FOR EACH ROW
    EXECUTE FUNCTION update_modified_column();


-- =============================================================================
-- SECTION 4: PURCHASE VALIDATION
-- =============================================================================

CREATE OR REPLACE FUNCTION validate_outlet_review_purchase()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_order RECORD;
BEGIN

    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION
            'Authentication required to submit a review.';
    END IF;

    IF auth.uid() != NEW.student_id THEN
        RAISE EXCEPTION
            'You can only submit reviews as yourself.';
    END IF;

    SELECT
        id,
        user_id,
        status,
        outlet_id
    INTO v_order
    FROM orders
    WHERE id = NEW.order_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION
            'Order not found.';
    END IF;

    IF v_order.user_id != NEW.student_id THEN
        RAISE EXCEPTION
            'The order does not belong to you.';
    END IF;

    IF v_order.status != 'PICKED_UP' THEN
        RAISE EXCEPTION
            'You can only review outlets from completed (picked-up) orders. Status: %',
            v_order.status;
    END IF;

    IF v_order.outlet_id != NEW.outlet_id THEN
        RAISE EXCEPTION
            'The outlet being reviewed does not match the outlet on the order.';
    END IF;

    RETURN NEW;

END;
$$;


DROP TRIGGER IF EXISTS enforce_outlet_review_purchase_validation
ON outlet_reviews;

CREATE TRIGGER enforce_outlet_review_purchase_validation
    BEFORE INSERT ON outlet_reviews
    FOR EACH ROW
    EXECUTE FUNCTION validate_outlet_review_purchase();


-- =============================================================================
-- SECTION 5: PROTECT REVIEW IMMUTABLE FIELDS
-- =============================================================================

CREATE OR REPLACE FUNCTION protect_outlet_review_immutable_fields()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN

    IF NEW.student_id IS DISTINCT FROM OLD.student_id THEN
        RAISE EXCEPTION
            'Cannot change the student associated with a review.';
    END IF;

    IF NEW.outlet_id IS DISTINCT FROM OLD.outlet_id THEN
        RAISE EXCEPTION
            'Cannot change the outlet associated with a review.';
    END IF;

    IF NEW.order_id IS DISTINCT FROM OLD.order_id THEN
        RAISE EXCEPTION
            'Cannot change the order associated with a review.';
    END IF;

    IF NEW.is_visible IS DISTINCT FROM OLD.is_visible THEN
        IF NOT is_admin() THEN
            RAISE EXCEPTION
                'Only management can change review visibility.';
        END IF;
    END IF;

    RETURN NEW;

END;
$$;


DROP TRIGGER IF EXISTS protect_outlet_review_immutable_fields
ON outlet_reviews;

CREATE TRIGGER protect_outlet_review_immutable_fields
    BEFORE UPDATE ON outlet_reviews
    FOR EACH ROW
    EXECUTE FUNCTION protect_outlet_review_immutable_fields();


-- =============================================================================
-- SECTION 6: VENDOR REPLY INSERT OWNERSHIP VALIDATION
-- =============================================================================

CREATE OR REPLACE FUNCTION validate_outlet_vendor_reply_ownership()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_caller_role TEXT;
    v_vendor_id   UUID;
BEGIN

    v_caller_role := get_user_role();

    IF v_caller_role = 'ADMIN' THEN
        RETURN NEW;
    END IF;

    IF v_caller_role != 'VENDOR' THEN
        RAISE EXCEPTION
            'Only vendors can reply to reviews. Your role: %',
            COALESCE(v_caller_role, 'unauthenticated');
    END IF;

    IF auth.uid() != NEW.vendor_id THEN
        RAISE EXCEPTION
            'You can only reply as yourself.';
    END IF;

    SELECT o.vendor_id
    INTO v_vendor_id
    FROM outlet_reviews r
    JOIN outlets o
        ON o.id = r.outlet_id
    WHERE r.id = NEW.outlet_review_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION
            'Outlet review not found.';
    END IF;

    IF v_vendor_id != auth.uid() THEN
        RAISE EXCEPTION
            'You can only reply to reviews for your own outlets.';
    END IF;

    RETURN NEW;

END;
$$;


DROP TRIGGER IF EXISTS enforce_outlet_vendor_reply_ownership
ON outlet_review_replies;

CREATE TRIGGER enforce_outlet_vendor_reply_ownership
    BEFORE INSERT ON outlet_review_replies
    FOR EACH ROW
    EXECUTE FUNCTION validate_outlet_vendor_reply_ownership();


-- =============================================================================
-- SECTION 7: VENDOR REPLY UPDATE VALIDATION
-- =============================================================================

CREATE OR REPLACE FUNCTION validate_outlet_vendor_reply_update()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN

    IF is_admin() THEN
        RETURN NEW;
    END IF;

    IF NEW.vendor_id IS DISTINCT FROM OLD.vendor_id THEN
        RAISE EXCEPTION
            'Cannot change the vendor of an existing reply.';
    END IF;

    IF NEW.outlet_review_id IS DISTINCT FROM OLD.outlet_review_id THEN
        RAISE EXCEPTION
            'Cannot reassign a reply to a different review.';
    END IF;

    IF auth.uid() != OLD.vendor_id THEN
        RAISE EXCEPTION
            'You can only edit your own replies.';
    END IF;

    RETURN NEW;

END;
$$;


DROP TRIGGER IF EXISTS enforce_outlet_vendor_reply_update
ON outlet_review_replies;

CREATE TRIGGER enforce_outlet_vendor_reply_update
    BEFORE UPDATE ON outlet_review_replies
    FOR EACH ROW
    EXECUTE FUNCTION validate_outlet_vendor_reply_update();


-- =============================================================================
-- SECTION 8: RATING AGGREGATION
-- =============================================================================

CREATE OR REPLACE FUNCTION sync_outlet_rating()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_outlet_id UUID;
    v_new_rating   DECIMAL(3,2);
    v_new_count    INTEGER;
BEGIN

    IF TG_OP = 'DELETE' THEN
        v_outlet_id := OLD.outlet_id;
    ELSE
        v_outlet_id := NEW.outlet_id;

        IF TG_OP = 'UPDATE'
           AND OLD.outlet_id IS DISTINCT FROM NEW.outlet_id
        THEN
            SELECT
                COALESCE(
                    ROUND(AVG(rating)::DECIMAL, 2),
                    0.00
                ),
                COUNT(*)
            INTO
                v_new_rating,
                v_new_count
            FROM outlet_reviews
            WHERE outlet_id = OLD.outlet_id
              AND is_visible = true;

            UPDATE outlets
            SET
                rating = v_new_rating,
                total_reviews = v_new_count,
                updated_at = NOW()
            WHERE id = OLD.outlet_id;
        END IF;
    END IF;

    SELECT
        COALESCE(
            ROUND(AVG(rating)::DECIMAL, 2),
            0.00
        ),
        COUNT(*)
    INTO
        v_new_rating,
        v_new_count
    FROM outlet_reviews
    WHERE outlet_id = v_outlet_id
      AND is_visible = true;

    UPDATE outlets
    SET
        rating = v_new_rating,
        total_reviews = v_new_count,
        updated_at = NOW()
    WHERE id = v_outlet_id;

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;

    RETURN NEW;

END;
$$;


DROP TRIGGER IF EXISTS sync_rating_on_outlet_review_change
ON outlet_reviews;

CREATE TRIGGER sync_rating_on_outlet_review_change
    AFTER INSERT OR UPDATE OR DELETE ON outlet_reviews
    FOR EACH ROW
    EXECUTE FUNCTION sync_outlet_rating();


-- =============================================================================
-- SECTION 9: ENABLE RLS
-- =============================================================================

ALTER TABLE outlet_reviews
    ENABLE ROW LEVEL SECURITY;

ALTER TABLE outlet_review_replies
    ENABLE ROW LEVEL SECURITY;


-- =============================================================================
-- SECTION 10: RLS POLICIES -- outlet_reviews
-- =============================================================================

CREATE POLICY "Students and vendors see visible outlet reviews"
ON outlet_reviews
FOR SELECT
USING (
    is_visible = true
    OR is_admin()
);

CREATE POLICY "Students insert their own outlet reviews"
ON outlet_reviews
FOR INSERT
WITH CHECK (
    auth.uid() = student_id
    AND is_student()
);

CREATE POLICY "Students update their own outlet reviews"
ON outlet_reviews
FOR UPDATE
USING (
    auth.uid() = student_id
    AND is_student()
)
WITH CHECK (
    auth.uid() = student_id
    AND is_student()
);

CREATE POLICY "Students delete their own outlet reviews"
ON outlet_reviews
FOR DELETE
USING (
    auth.uid() = student_id
    AND is_student()
);

CREATE POLICY "Admins have full access to outlet reviews"
ON outlet_reviews
FOR ALL
USING (
    is_admin()
)
WITH CHECK (
    is_admin()
);


-- =============================================================================
-- SECTION 11: RLS POLICIES -- outlet_review_replies
-- =============================================================================

CREATE POLICY "Anyone can see outlet review replies"
ON outlet_review_replies
FOR SELECT
USING (
    EXISTS (
        SELECT 1
        FROM outlet_reviews r
        WHERE r.id = outlet_review_replies.outlet_review_id
          AND (
              r.is_visible = true
              OR is_admin()
          )
    )
);

CREATE POLICY "Vendors insert their own outlet reply"
ON outlet_review_replies
FOR INSERT
WITH CHECK (
    auth.uid() = vendor_id
    AND is_vendor()
);

CREATE POLICY "Vendors update their own outlet reply"
ON outlet_review_replies
FOR UPDATE
USING (
    auth.uid() = vendor_id
    AND is_vendor()
)
WITH CHECK (
    auth.uid() = vendor_id
    AND is_vendor()
);

CREATE POLICY "Vendors delete their own outlet reply"
ON outlet_review_replies
FOR DELETE
USING (
    (
        auth.uid() = vendor_id
        AND is_vendor()
    )
    OR is_admin()
);

CREATE POLICY "Admins have full access to outlet review replies"
ON outlet_review_replies
FOR ALL
USING (
    is_admin()
)
WITH CHECK (
    is_admin()
);


-- =============================================================================
-- SECTION 12: PERFORMANCE INDEXES
-- =============================================================================

CREATE INDEX IF NOT EXISTS idx_outlet_reviews_outlet_id
    ON outlet_reviews(outlet_id)
    WHERE is_visible = true;

CREATE INDEX IF NOT EXISTS idx_outlet_reviews_student_id
    ON outlet_reviews(student_id);

CREATE INDEX IF NOT EXISTS idx_outlet_reviews_order_id
    ON outlet_reviews(order_id);

CREATE INDEX IF NOT EXISTS idx_outlet_review_replies_review
    ON outlet_review_replies(outlet_review_id);

CREATE INDEX IF NOT EXISTS idx_outlet_review_replies_vendor
    ON outlet_review_replies(vendor_id);


-- =============================================================================
-- SECTION 13: RPC -- get_outlet_reviews
-- =============================================================================

CREATE OR REPLACE FUNCTION get_outlet_reviews(
    p_outlet_id UUID,
    p_limit INTEGER DEFAULT 20,
    p_offset INTEGER DEFAULT 0
)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_results JSONB;
    v_total   INTEGER;
BEGIN

    p_limit := LEAST(GREATEST(COALESCE(p_limit, 20), 1), 100);
    p_offset := GREATEST(COALESCE(p_offset, 0), 0);

    SELECT COUNT(*)
    INTO v_total
    FROM outlet_reviews
    WHERE outlet_id = p_outlet_id
      AND is_visible = true;

    SELECT COALESCE(
        jsonb_agg(
            jsonb_build_object(
                'id',            r.id,
                'student_id',    r.student_id,
                'student_name',  p.name,
                'outlet_id',     r.outlet_id,
                'order_id',      r.order_id,
                'rating',        r.rating,
                'review_text',   r.review_text,
                'is_visible',    r.is_visible,
                'created_at',    r.created_at,
                'updated_at',    r.updated_at,

                'vendor_reply',
                CASE
                    WHEN rr.id IS NOT NULL THEN
                        jsonb_build_object(
                            'id',         rr.id,
                            'vendor_id',  rr.vendor_id,
                            'reply_text', rr.reply_text,
                            'created_at', rr.created_at,
                            'updated_at', rr.updated_at
                        )
                    ELSE NULL
                END
            )
            ORDER BY r.created_at DESC
        ),
        '[]'::jsonb
    )
    INTO v_results
    FROM (
        SELECT *
        FROM outlet_reviews
        WHERE outlet_id = p_outlet_id
          AND is_visible = true
        ORDER BY created_at DESC
        LIMIT p_limit
        OFFSET p_offset
    ) r
    JOIN profiles p
        ON p.id = r.student_id
    LEFT JOIN outlet_review_replies rr
        ON rr.outlet_review_id = r.id;

    RETURN jsonb_build_object(
        'reviews', v_results,
        'total', v_total
    );

END;
$$;

REVOKE ALL
ON FUNCTION get_outlet_reviews(UUID, INTEGER, INTEGER)
FROM PUBLIC;

GRANT EXECUTE
ON FUNCTION get_outlet_reviews(UUID, INTEGER, INTEGER)
TO authenticated, anon;


-- =============================================================================
-- SECTION 14: RPC -- get_outlet_review_for_order
-- =============================================================================

CREATE OR REPLACE FUNCTION get_outlet_review_for_order(
    p_order_id UUID
)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid    UUID := auth.uid();
    v_review JSONB;
BEGIN

    IF v_uid IS NULL THEN
        RAISE EXCEPTION
            'Authentication required.';
    END IF;

    SELECT jsonb_build_object(
        'id',            r.id,
        'student_id',    r.student_id,
        'outlet_id',     r.outlet_id,
        'order_id',      r.order_id,
        'rating',        r.rating,
        'review_text',   r.review_text,
        'is_visible',    r.is_visible,
        'created_at',    r.created_at,
        'updated_at',    r.updated_at
    )
    INTO v_review
    FROM outlet_reviews r
    WHERE r.order_id = p_order_id
      AND r.student_id = v_uid;

    RETURN v_review;

END;
$$;

REVOKE ALL
ON FUNCTION get_outlet_review_for_order(UUID)
FROM PUBLIC;

GRANT EXECUTE
ON FUNCTION get_outlet_review_for_order(UUID)
TO authenticated;


-- =============================================================================
-- SECTION 15: RPC -- get_vendor_outlet_reviews
-- =============================================================================

CREATE OR REPLACE FUNCTION get_vendor_outlet_reviews(
    p_limit INTEGER DEFAULT 50,
    p_offset INTEGER DEFAULT 0
)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid     UUID := auth.uid();
    v_role    TEXT := get_user_role();
    v_results JSONB;
    v_total   INTEGER;
BEGIN

    IF v_uid IS NULL OR v_role != 'VENDOR' THEN
        RAISE EXCEPTION
            'Only vendors can call this function.';
    END IF;

    p_limit := LEAST(GREATEST(COALESCE(p_limit, 50), 1), 100);
    p_offset := GREATEST(COALESCE(p_offset, 0), 0);

    SELECT COUNT(*)
    INTO v_total
    FROM outlet_reviews r
    JOIN outlets o
        ON o.id = r.outlet_id
    WHERE o.vendor_id = v_uid;

    SELECT COALESCE(
        jsonb_agg(
            jsonb_build_object(
                'id',            r.id,
                'student_id',    r.student_id,
                'student_name',  p.name,
                'outlet_id',     r.outlet_id,
                'outlet_name',   o.name,
                'order_id',      r.order_id,
                'rating',        r.rating,
                'review_text',   r.review_text,
                'is_visible',    r.is_visible,
                'created_at',    r.created_at,
                'updated_at',    r.updated_at,

                'vendor_reply',
                CASE
                    WHEN rr.id IS NOT NULL THEN
                        jsonb_build_object(
                            'id',         rr.id,
                            'reply_text', rr.reply_text,
                            'created_at', rr.created_at,
                            'updated_at', rr.updated_at
                        )
                    ELSE NULL
                END
            )
            ORDER BY r.created_at DESC
        ),
        '[]'::jsonb
    )
    INTO v_results
    FROM (
        SELECT r.*
        FROM outlet_reviews r
        JOIN outlets o
            ON o.id = r.outlet_id
        WHERE o.vendor_id = v_uid
        ORDER BY r.created_at DESC
        LIMIT p_limit
        OFFSET p_offset
    ) r
    JOIN profiles p
        ON p.id = r.student_id
    JOIN outlets o
        ON o.id = r.outlet_id
    LEFT JOIN outlet_review_replies rr
        ON rr.outlet_review_id = r.id;

    RETURN jsonb_build_object(
        'reviews', v_results,
        'total', v_total
    );

END;
$$;

REVOKE ALL
ON FUNCTION get_vendor_outlet_reviews(INTEGER, INTEGER)
FROM PUBLIC;

GRANT EXECUTE
ON FUNCTION get_vendor_outlet_reviews(INTEGER, INTEGER)
TO authenticated;


-- =============================================================================
-- SECTION 16: RPC -- get_management_outlet_reviews
-- =============================================================================

CREATE OR REPLACE FUNCTION get_management_outlet_reviews(
    p_limit INTEGER DEFAULT 50,
    p_offset INTEGER DEFAULT 0
)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid     UUID := auth.uid();
    v_role    TEXT := get_user_role();
    v_results JSONB;
    v_total   INTEGER;
BEGIN

    IF v_uid IS NULL OR v_role != 'ADMIN' THEN
        RAISE EXCEPTION
            'Only admins can call this function.';
    END IF;

    p_limit := LEAST(GREATEST(COALESCE(p_limit, 50), 1), 100);
    p_offset := GREATEST(COALESCE(p_offset, 0), 0);

    SELECT COUNT(*)
    INTO v_total
    FROM outlet_reviews;

    SELECT COALESCE(
        jsonb_agg(
            jsonb_build_object(
                'id',            r.id,
                'student_id',    r.student_id,
                'student_name',  p.name,
                'outlet_id',     r.outlet_id,
                'outlet_name',   o.name,
                'order_id',      r.order_id,
                'rating',        r.rating,
                'review_text',   r.review_text,
                'is_visible',    r.is_visible,
                'created_at',    r.created_at,
                'updated_at',    r.updated_at,

                'vendor_reply',
                CASE
                    WHEN rr.id IS NOT NULL THEN
                        jsonb_build_object(
                            'id',         rr.id,
                            'vendor_id',  rr.vendor_id,
                            'reply_text', rr.reply_text,
                            'created_at', rr.created_at,
                            'updated_at', rr.updated_at
                        )
                    ELSE NULL
                END
            )
            ORDER BY r.created_at DESC
        ),
        '[]'::jsonb
    )
    INTO v_results
    FROM (
        SELECT r.*
        FROM outlet_reviews r
        ORDER BY r.created_at DESC
        LIMIT p_limit
        OFFSET p_offset
    ) r
    JOIN profiles p
        ON p.id = r.student_id
    JOIN outlets o
        ON o.id = r.outlet_id
    LEFT JOIN outlet_review_replies rr
        ON rr.outlet_review_id = r.id;

    RETURN jsonb_build_object(
        'reviews', v_results,
        'total', v_total
    );

END;
$$;

REVOKE ALL
ON FUNCTION get_management_outlet_reviews(INTEGER, INTEGER)
FROM PUBLIC;

GRANT EXECUTE
ON FUNCTION get_management_outlet_reviews(INTEGER, INTEGER)
TO authenticated;


-- =============================================================================
-- SECTION 17: POSTGREST SCHEMA CACHE
-- =============================================================================

NOTIFY pgrst, 'reload schema';

COMMIT;
