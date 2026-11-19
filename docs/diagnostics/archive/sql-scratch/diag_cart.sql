SELECT
    c.id AS cart_id,
    c.user_id,
    c.outlet_id,
    o.name AS outlet_name,
    o.is_active,
    o.is_open,
    o.operating_hours,
    c.updated_at
FROM public.carts c
JOIN public.outlets o ON o.id = c.outlet_id
WHERE EXISTS (
      SELECT 1
      FROM public.cart_items ci
      WHERE ci.cart_id = c.id
  )
ORDER BY c.updated_at DESC
LIMIT 1;
