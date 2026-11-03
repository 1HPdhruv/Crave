-- =============================================================================
-- 03_fix_search_food.sql — CRAVE-Staging. NOT APPLIED. Requires authorization.
-- Fixes public.search_food(TEXT, UUID, TEXT, BOOLEAN, DECIMAL, BOOLEAN) from repo migration 004, which fails on every
-- call with SQLSTATE 42803 (observed live on staging: "column fi.name must appear in the GROUP BY clause ...").
-- Same signature, attributes (STABLE, SECURITY DEFINER, search_path), filters, ordering, LIMIT and JSON fields;
-- only the query shape changes (sort + limit in a subquery, then aggregate). No tables, data or grants are touched.
-- Run in the SQL Editor of the STAGING project.
-- =============================================================================
BEGIN;
CREATE OR REPLACE FUNCTION search_food(
    p_query      TEXT    DEFAULT '',
    p_outlet_id  UUID    DEFAULT NULL,
    p_category   TEXT    DEFAULT NULL,   -- category name filter
    p_is_veg     BOOLEAN DEFAULT NULL,
    p_max_price  DECIMAL DEFAULT NULL,
    p_available_only BOOLEAN DEFAULT true
)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_results JSONB;
    v_is_privileged BOOLEAN := is_admin() OR is_vendor();
BEGIN
        -- Order and LIMIT inside a subquery, aggregate outside: the original put ORDER BY fi.* / LIMIT on the aggregating
    -- query itself, which PostgreSQL rejects (42803 "column fi.name must appear in the GROUP BY clause").
    SELECT COALESCE(jsonb_agg(r.obj ORDER BY r.rn), '[]'::jsonb) INTO v_results
    FROM (
        SELECT jsonb_build_object(
                'id',               fi.id,
                'name',             fi.name,
                'description',      fi.description,
                'image_url',        fi.image_url,
                'price',            fi.price,
                'is_veg',           fi.is_veg,
                'is_available',     fi.is_available,
                'prep_time_minutes',fi.prep_time_minutes,
                'is_popular',       fi.is_popular,
                'is_recommended',   fi.is_recommended,
                'rating',           fi.rating,
                'outlet_id',        fi.outlet_id,
                'outlet_name',      out.name,
                'category_id',      fi.category_id,
                'category_name',    cat.name
            ) AS obj,
               row_number() OVER (ORDER BY
        -- Boost exact name matches, then popular, then rating
        CASE WHEN fi.name ILIKE p_query THEN 0 ELSE 1 END,
        fi.is_popular DESC,
        fi.rating DESC,
        fi.name ASC) AS rn
    FROM food_items fi
    JOIN outlets    out ON out.id  = fi.outlet_id
    JOIN categories cat ON cat.id  = fi.category_id
    WHERE
        fi.deleted_at  IS NULL
        AND out.deleted_at IS NULL
        -- Availability filter (always on for students)
        AND (
            v_is_privileged
            OR (
                (NOT p_available_only OR fi.is_available = true)
                AND out.is_active = true
            )
        )
        -- Text search across name, description, tags
        AND (
            p_query = ''
            OR fi.name        ILIKE '%' || p_query || '%'
            OR fi.description ILIKE '%' || p_query || '%'
            OR EXISTS (SELECT 1 FROM unnest(fi.tags) t WHERE t ILIKE '%' || p_query || '%')
        )
        -- Optional filters
        AND (p_outlet_id IS NULL OR fi.outlet_id   = p_outlet_id)
        AND (p_category  IS NULL OR cat.name ILIKE p_category)
        AND (p_is_veg    IS NULL OR fi.is_veg       = p_is_veg)
        AND (p_max_price IS NULL OR fi.price        <= p_max_price)
    ORDER BY
        -- Boost exact name matches, then popular, then rating
        CASE WHEN fi.name ILIKE p_query THEN 0 ELSE 1 END,
        fi.is_popular DESC,
        fi.rating DESC,
        fi.name ASC
        LIMIT 100
    ) r;
    RETURN v_results;
END;
$$;
COMMIT;
NOTIFY pgrst, 'reload schema';
