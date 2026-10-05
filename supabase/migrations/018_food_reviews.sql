-- =============================================================================
-- Migration 018: Food Item Reviews System
-- =============================================================================
-- SCOPE: Food item reviews ONLY (not outlet reviews).
--
-- Depends on:
--   001 (reviews table, order_items, food_items, orders, profiles, outlets)
--
-- IMPORTANT:
--   This migration intentionally replaces the old under-engineered reviews
--   table. The old production table was confirmed to contain no app data.
--
-- Main features:
--   - One review per purchased order item
--   - Purchase validation
--   - Student-owned reviews
--   - Vendor replies
--   - Vendor ownership validation
--   - Rating aggregation
--   - RLS
--   - Food review RPCs
--   - Vendor review RPC
--   - Management review RPC
-- =============================================================================

BEGIN;

-- =============================================================================
-- SECTION 1: DROP OLD reviews TABLE
-- =============================================================================

DROP TRIGGER IF EXISTS update_reviews_modtime ON reviews;

DROP POLICY IF EXISTS "Anyone can view reviews"                     ON reviews;
DROP POLICY IF EXISTS "Users can create and edit their own reviews" ON reviews;
DROP POLICY IF EXISTS "Admins manage reviews"                       ON reviews;

DROP TABLE IF EXISTS reviews CASCADE;


-- =============================================================================
-- SECTION 2: CREATE NEW reviews TABLE
-- =============================================================================

CREATE TABLE reviews (
    id            UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),

    student_id    UUID        NOT NULL
                  REFERENCES profiles(id)
                  ON DELETE RESTRICT,

    food_item_id  UUID        NOT NULL
                  REFERENCES food_items(id)
                  ON DELETE CASCADE,

    order_id      UUID        NOT NULL
                  REFERENCES orders(id)
                  ON DELETE RESTRICT,

    order_item_id UUID        NOT NULL
                  REFERENCES order_items(id)
                  ON DELETE RESTRICT,

    rating        INTEGER     NOT NULL,

    review_text   TEXT,

    is_visible    BOOLEAN     NOT NULL DEFAULT true,

    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_review_per_order_item
        UNIQUE (student_id, order_item_id),

    CONSTRAINT check_rating
        CHECK (rating >= 1 AND rating <= 5),

    CONSTRAINT check_review_text_length
        CHECK (
            review_text IS NULL
            OR char_length(review_text) <= 2000
        )
);

COMMENT ON TABLE reviews
IS 'Food item reviews tied to a specific purchased order item. One review per order item per student.';

COMMENT ON COLUMN reviews.student_id
IS 'The student who wrote the review (auth.uid()).';

COMMENT ON COLUMN reviews.order_item_id
IS 'The exact purchased order item. Uniqueness is enforced here.';

COMMENT ON COLUMN reviews.is_visible
IS 'Soft visibility flag for moderation.';


-- =============================================================================
-- SECTION 3: CREATE review_replies TABLE
-- =============================================================================

CREATE TABLE review_replies (
    id          UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),

    review_id   UUID        NOT NULL
                REFERENCES reviews(id)
                ON DELETE CASCADE,

    vendor_id   UUID        NOT NULL
                REFERENCES profiles(id)
                ON DELETE RESTRICT,

    reply_text  TEXT        NOT NULL,

    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_reply_per_review
        UNIQUE (review_id),

    CONSTRAINT check_reply_text_length
        CHECK (
            char_length(reply_text) >= 1
            AND char_length(reply_text) <= 1000
        )
);

COMMENT ON TABLE review_replies
IS 'Vendor reply to a student food review. One reply per review.';

COMMENT ON COLUMN review_replies.vendor_id
IS 'The vendor who replied. Must own the outlet selling the reviewed food item.';


-- =============================================================================
-- SECTION 4: updated_at TRIGGERS
-- =============================================================================

DROP TRIGGER IF EXISTS update_reviews_modtime
ON reviews;

CREATE TRIGGER update_reviews_modtime
    BEFORE UPDATE ON reviews
    FOR EACH ROW
    EXECUTE FUNCTION update_modified_column();


DROP TRIGGER IF EXISTS update_review_replies_modtime
ON review_replies;

CREATE TRIGGER update_review_replies_modtime
    BEFORE UPDATE ON review_replies
    FOR EACH ROW
    EXECUTE FUNCTION update_modified_column();


-- =============================================================================
-- SECTION 5: PURCHASE VALIDATION
-- =============================================================================
--
-- Before a review is inserted:
--
-- 1. User must be authenticated
-- 2. auth.uid() must equal NEW.student_id
-- 3. order must exist
-- 4. order must belong to student
-- 5. order must be PICKED_UP
-- 6. order_item must exist
-- 7. order_item must belong to order
-- 8. order_item food must equal reviewed food
--
-- =============================================================================

CREATE OR REPLACE FUNCTION validate_review_purchase()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_order RECORD;
    v_item  RECORD;
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
        status
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
            'You can only review items from completed (picked-up) orders. Status: %',
            v_order.status;
    END IF;


    SELECT
        id,
        order_id,
        food_item_id
    INTO v_item
    FROM order_items
    WHERE id = NEW.order_item_id;


    IF NOT FOUND THEN
        RAISE EXCEPTION
            'Order item not found.';
    END IF;


    IF v_item.order_id != NEW.order_id THEN
        RAISE EXCEPTION
            'The order item does not belong to the specified order.';
    END IF;


    IF v_item.food_item_id != NEW.food_item_id THEN
        RAISE EXCEPTION
            'The food item being reviewed does not match the purchased order item.';
    END IF;


    RETURN NEW;

END;
$$;


DROP TRIGGER IF EXISTS enforce_review_purchase_validation
ON reviews;

CREATE TRIGGER enforce_review_purchase_validation
    BEFORE INSERT ON reviews
    FOR EACH ROW
    EXECUTE FUNCTION validate_review_purchase();


-- =============================================================================
-- SECTION 6: PROTECT REVIEW IMMUTABLE FIELDS
-- =============================================================================
--
-- NEW/OLD are trigger records and therefore belong here, NOT inside RLS.
--
-- Students may edit:
--   - rating
--   - review_text
--
-- Students may NOT edit:
--   - student_id
--   - food_item_id
--   - order_id
--   - order_item_id
--   - is_visible
--
-- Admins retain moderation access to is_visible.
-- =============================================================================

CREATE OR REPLACE FUNCTION protect_review_immutable_fields()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN

    /*
     * These identity/purchase anchors must never change.
     * This applies to everyone, including management.
     */
    IF NEW.student_id IS DISTINCT FROM OLD.student_id THEN
        RAISE EXCEPTION
            'Cannot change the student associated with a review.';
    END IF;


    IF NEW.food_item_id IS DISTINCT FROM OLD.food_item_id THEN
        RAISE EXCEPTION
            'Cannot change the food item associated with a review.';
    END IF;


    IF NEW.order_id IS DISTINCT FROM OLD.order_id THEN
        RAISE EXCEPTION
            'Cannot change the order associated with a review.';
    END IF;


    IF NEW.order_item_id IS DISTINCT FROM OLD.order_item_id THEN
        RAISE EXCEPTION
            'Cannot change the order item associated with a review.';
    END IF;


    /*
     * Only admins may change visibility.
     */
    IF NEW.is_visible IS DISTINCT FROM OLD.is_visible THEN

        IF NOT is_admin() THEN
            RAISE EXCEPTION
                'Only management can change review visibility.';
        END IF;

    END IF;


    RETURN NEW;

END;
$$;


DROP TRIGGER IF EXISTS protect_review_immutable_fields
ON reviews;

CREATE TRIGGER protect_review_immutable_fields
    BEFORE UPDATE ON reviews
    FOR EACH ROW
    EXECUTE FUNCTION protect_review_immutable_fields();


-- =============================================================================
-- SECTION 7: VENDOR REPLY INSERT OWNERSHIP VALIDATION
-- =============================================================================

CREATE OR REPLACE FUNCTION validate_vendor_reply_ownership()
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


    /*
     * Management may perform administrative operations.
     */
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
    FROM reviews r
    JOIN food_items fi
        ON fi.id = r.food_item_id
    JOIN outlets o
        ON o.id = fi.outlet_id
    WHERE r.id = NEW.review_id;


    IF NOT FOUND THEN
        RAISE EXCEPTION
            'Review not found.';
    END IF;


    IF v_vendor_id != auth.uid() THEN
        RAISE EXCEPTION
            'You can only reply to reviews for your own food items.';
    END IF;


    RETURN NEW;

END;
$$;


DROP TRIGGER IF EXISTS enforce_vendor_reply_ownership
ON review_replies;

CREATE TRIGGER enforce_vendor_reply_ownership
    BEFORE INSERT ON review_replies
    FOR EACH ROW
    EXECUTE FUNCTION validate_vendor_reply_ownership();


-- =============================================================================
-- SECTION 8: VENDOR REPLY UPDATE VALIDATION
-- =============================================================================

CREATE OR REPLACE FUNCTION validate_vendor_reply_update()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN

    /*
     * Management can perform administrative updates.
     */
    IF is_admin() THEN
        RETURN NEW;
    END IF;


    IF NEW.vendor_id IS DISTINCT FROM OLD.vendor_id THEN
        RAISE EXCEPTION
            'Cannot change the vendor of an existing reply.';
    END IF;


    IF NEW.review_id IS DISTINCT FROM OLD.review_id THEN
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


DROP TRIGGER IF EXISTS enforce_vendor_reply_update
ON review_replies;

CREATE TRIGGER enforce_vendor_reply_update
    BEFORE UPDATE ON review_replies
    FOR EACH ROW
    EXECUTE FUNCTION validate_vendor_reply_update();


-- =============================================================================
-- SECTION 9: RATING AGGREGATION
-- =============================================================================
--
-- Keeps:
--   food_items.rating
--   food_items.total_reviews
--
-- synchronized with visible reviews.
--
-- Handles:
--   INSERT
--   UPDATE
--   DELETE
--
-- Also handles moving a review between food items, although normal
-- application logic should never do this.
-- =============================================================================

CREATE OR REPLACE FUNCTION sync_food_item_rating()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_food_item_id UUID;
    v_new_rating   DECIMAL(3,2);
    v_new_count    INTEGER;
BEGIN

    /*
     * DELETE:
     * Recalculate the old food item.
     */
    IF TG_OP = 'DELETE' THEN

        v_food_item_id := OLD.food_item_id;


    /*
     * INSERT / UPDATE:
     * Recalculate the new food item.
     */
    ELSE

        v_food_item_id := NEW.food_item_id;


        /*
         * If food_item_id somehow changes,
         * recalculate the old food item as well.
         */
        IF TG_OP = 'UPDATE'
           AND OLD.food_item_id IS DISTINCT FROM NEW.food_item_id
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
            FROM reviews
            WHERE food_item_id = OLD.food_item_id
              AND is_visible = true;


            UPDATE food_items
            SET
                rating = v_new_rating,
                total_reviews = v_new_count,
                updated_at = NOW()
            WHERE id = OLD.food_item_id;

        END IF;

    END IF;


    /*
     * Recalculate the current/new food item.
     */
    SELECT
        COALESCE(
            ROUND(AVG(rating)::DECIMAL, 2),
            0.00
        ),
        COUNT(*)
    INTO
        v_new_rating,
        v_new_count
    FROM reviews
    WHERE food_item_id = v_food_item_id
      AND is_visible = true;


    UPDATE food_items
    SET
        rating = v_new_rating,
        total_reviews = v_new_count,
        updated_at = NOW()
    WHERE id = v_food_item_id;


    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;


    RETURN NEW;

END;
$$;


DROP TRIGGER IF EXISTS sync_rating_on_review_change
ON reviews;

CREATE TRIGGER sync_rating_on_review_change
    AFTER INSERT OR UPDATE OR DELETE ON reviews
    FOR EACH ROW
    EXECUTE FUNCTION sync_food_item_rating();


-- =============================================================================
-- SECTION 10: ENABLE RLS
-- =============================================================================

ALTER TABLE reviews
    ENABLE ROW LEVEL SECURITY;

ALTER TABLE review_replies
    ENABLE ROW LEVEL SECURITY;


-- =============================================================================
-- SECTION 11: CLEAN OLD POLICIES IF PRESENT
-- =============================================================================

DROP POLICY IF EXISTS "Students and vendors see visible reviews"
    ON reviews;

DROP POLICY IF EXISTS "Students insert their own reviews"
    ON reviews;

DROP POLICY IF EXISTS "Students update their own reviews"
    ON reviews;

DROP POLICY IF EXISTS "Students delete their own reviews"
    ON reviews;

DROP POLICY IF EXISTS "Admins have full access to reviews"
    ON reviews;


DROP POLICY IF EXISTS "Anyone can see review replies"
    ON review_replies;

DROP POLICY IF EXISTS "Vendors insert their own reply"
    ON review_replies;

DROP POLICY IF EXISTS "Vendors update their own reply"
    ON review_replies;

DROP POLICY IF EXISTS "Vendors delete their own reply"
    ON review_replies;

DROP POLICY IF EXISTS "Admins have full access to review replies"
    ON review_replies;


-- =============================================================================
-- SECTION 12: RLS POLICIES -- reviews
-- =============================================================================

CREATE POLICY "Students and vendors see visible reviews"
ON reviews
FOR SELECT
USING (
    is_visible = true
    OR is_admin()
);


CREATE POLICY "Students insert their own reviews"
ON reviews
FOR INSERT
WITH CHECK (
    auth.uid() = student_id
    AND is_student()
);


CREATE POLICY "Students update their own reviews"
ON reviews
FOR UPDATE
USING (
    auth.uid() = student_id
    AND is_student()
)
WITH CHECK (
    auth.uid() = student_id
    AND is_student()
);


CREATE POLICY "Students delete their own reviews"
ON reviews
FOR DELETE
USING (
    auth.uid() = student_id
    AND is_student()
);


CREATE POLICY "Admins have full access to reviews"
ON reviews
FOR ALL
USING (
    is_admin()
)
WITH CHECK (
    is_admin()
);


-- =============================================================================
-- SECTION 13: RLS POLICIES -- review_replies
-- =============================================================================

CREATE POLICY "Anyone can see review replies"
ON review_replies
FOR SELECT
USING (
    EXISTS (
        SELECT 1
        FROM reviews r
        WHERE r.id = review_replies.review_id
          AND (
              r.is_visible = true
              OR is_admin()
          )
    )
);


CREATE POLICY "Vendors insert their own reply"
ON review_replies
FOR INSERT
WITH CHECK (
    auth.uid() = vendor_id
    AND is_vendor()
);


CREATE POLICY "Vendors update their own reply"
ON review_replies
FOR UPDATE
USING (
    auth.uid() = vendor_id
    AND is_vendor()
)
WITH CHECK (
    auth.uid() = vendor_id
    AND is_vendor()
);


CREATE POLICY "Vendors delete their own reply"
ON review_replies
FOR DELETE
USING (
    (
        auth.uid() = vendor_id
        AND is_vendor()
    )
    OR is_admin()
);


CREATE POLICY "Admins have full access to review replies"
ON review_replies
FOR ALL
USING (
    is_admin()
)
WITH CHECK (
    is_admin()
);


-- =============================================================================
-- SECTION 14: PERFORMANCE INDEXES
-- =============================================================================

CREATE INDEX IF NOT EXISTS idx_reviews_food_item_id
    ON reviews(food_item_id)
    WHERE is_visible = true;


CREATE INDEX IF NOT EXISTS idx_reviews_student_id
    ON reviews(student_id);


CREATE INDEX IF NOT EXISTS idx_reviews_order_item_id
    ON reviews(order_item_id);


CREATE INDEX IF NOT EXISTS idx_reviews_order_id
    ON reviews(order_id);


CREATE INDEX IF NOT EXISTS idx_review_replies_review
    ON review_replies(review_id);


CREATE INDEX IF NOT EXISTS idx_review_replies_vendor
    ON review_replies(vendor_id);


-- =============================================================================
-- SECTION 15: RPC -- get_food_reviews
-- =============================================================================
--
-- Returns:
--   reviews
--   total
--
-- Correct pagination happens BEFORE json aggregation.
-- =============================================================================

CREATE OR REPLACE FUNCTION get_food_reviews(
    p_food_item_id UUID,
    p_limit        INTEGER DEFAULT 20,
    p_offset       INTEGER DEFAULT 0
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
    FROM reviews
    WHERE food_item_id = p_food_item_id
      AND is_visible = true;


    SELECT COALESCE(
        jsonb_agg(
            jsonb_build_object(
                'id',            r.id,
                'student_id',    r.student_id,
                'student_name',  p.name,
                'food_item_id',  r.food_item_id,
                'order_id',      r.order_id,
                'order_item_id', r.order_item_id,
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
        FROM reviews
        WHERE food_item_id = p_food_item_id
          AND is_visible = true
        ORDER BY created_at DESC
        LIMIT p_limit
        OFFSET p_offset
    ) r
    JOIN profiles p
        ON p.id = r.student_id
    LEFT JOIN review_replies rr
        ON rr.review_id = r.id;


    RETURN jsonb_build_object(
        'reviews', v_results,
        'total', v_total
    );

END;
$$;


REVOKE ALL
ON FUNCTION get_food_reviews(UUID, INTEGER, INTEGER)
FROM PUBLIC;

GRANT EXECUTE
ON FUNCTION get_food_reviews(UUID, INTEGER, INTEGER)
TO authenticated, anon;


-- =============================================================================
-- SECTION 16: RPC -- get_review_for_order_item
-- =============================================================================

CREATE OR REPLACE FUNCTION get_review_for_order_item(
    p_order_item_id UUID
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
        'food_item_id',  r.food_item_id,
        'order_id',      r.order_id,
        'order_item_id', r.order_item_id,
        'rating',        r.rating,
        'review_text',   r.review_text,
        'is_visible',    r.is_visible,
        'created_at',    r.created_at,
        'updated_at',    r.updated_at
    )
    INTO v_review
    FROM reviews r
    WHERE r.order_item_id = p_order_item_id
      AND r.student_id = v_uid;


    RETURN v_review;

END;
$$;


REVOKE ALL
ON FUNCTION get_review_for_order_item(UUID)
FROM PUBLIC;

GRANT EXECUTE
ON FUNCTION get_review_for_order_item(UUID)
TO authenticated;


-- =============================================================================
-- SECTION 17: RPC -- get_vendor_reviews
-- =============================================================================
--
-- Returns reviews belonging to food items sold by the authenticated vendor.
-- Supports multiple outlets owned by the same vendor.
-- =============================================================================

CREATE OR REPLACE FUNCTION get_vendor_reviews(
    p_limit  INTEGER DEFAULT 50,
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
    FROM reviews r
    JOIN food_items fi
        ON fi.id = r.food_item_id
    JOIN outlets o
        ON o.id = fi.outlet_id
    WHERE o.vendor_id = v_uid;


    SELECT COALESCE(
        jsonb_agg(
            jsonb_build_object(
                'id',            r.id,
                'student_id',    r.student_id,
                'student_name',  p.name,
                'food_item_id',  r.food_item_id,
                'food_name',     fi.name,
                'outlet_id',     o.id,
                'outlet_name',   o.name,
                'order_id',      r.order_id,
                'order_item_id', r.order_item_id,
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
        FROM reviews r
        JOIN food_items fi
            ON fi.id = r.food_item_id
        JOIN outlets o
            ON o.id = fi.outlet_id
        WHERE o.vendor_id = v_uid
        ORDER BY r.created_at DESC
        LIMIT p_limit
        OFFSET p_offset
    ) r
    JOIN profiles p
        ON p.id = r.student_id
    JOIN food_items fi
        ON fi.id = r.food_item_id
    JOIN outlets o
        ON o.id = fi.outlet_id
    LEFT JOIN review_replies rr
        ON rr.review_id = r.id;


    RETURN jsonb_build_object(
        'reviews', v_results,
        'total', v_total
    );

END;
$$;


REVOKE ALL
ON FUNCTION get_vendor_reviews(INTEGER, INTEGER)
FROM PUBLIC;

GRANT EXECUTE
ON FUNCTION get_vendor_reviews(INTEGER, INTEGER)
TO authenticated;


-- =============================================================================
-- SECTION 18: RPC -- get_management_reviews
-- =============================================================================
--
-- Management can view all reviews.
-- =============================================================================

CREATE OR REPLACE FUNCTION get_management_reviews(
    p_limit  INTEGER DEFAULT 50,
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
    FROM reviews;


    SELECT COALESCE(
        jsonb_agg(
            jsonb_build_object(
                'id',            r.id,
                'student_id',    r.student_id,
                'student_name',  p.name,
                'food_item_id',  r.food_item_id,
                'food_name',     fi.name,
                'outlet_id',     o.id,
                'outlet_name',   o.name,
                'vendor_id',     o.vendor_id,
                'order_id',      r.order_id,
                'order_item_id', r.order_item_id,
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
        FROM reviews r
        ORDER BY r.created_at DESC
        LIMIT p_limit
        OFFSET p_offset
    ) r
    JOIN profiles p
        ON p.id = r.student_id
    JOIN food_items fi
        ON fi.id = r.food_item_id
    JOIN outlets o
        ON o.id = fi.outlet_id
    LEFT JOIN review_replies rr
        ON rr.review_id = r.id;


    RETURN jsonb_build_object(
        'reviews', v_results,
        'total', v_total
    );

END;
$$;


REVOKE ALL
ON FUNCTION get_management_reviews(INTEGER, INTEGER)
FROM PUBLIC;

GRANT EXECUTE
ON FUNCTION get_management_reviews(INTEGER, INTEGER)
TO authenticated;


-- =============================================================================
-- SECTION 19: POSTGREST SCHEMA CACHE
-- =============================================================================

NOTIFY pgrst, 'reload schema';


COMMIT;