# Razorpay Payment Failure Diagnostic Report

## A. Actual production payments schema
*(Determined via migration tracking and API structure verification)*
The production schema is running on migration `001_initial_schema.sql` (and potentially up to `009`, but definitely not `010` since `gateway_provider` fails).
- `order_id` UUID
- `gateway_payment_id` TEXT
- `gateway_order_id` TEXT
- `gateway_signature` TEXT
- `status` ENUM (PENDING, PAID, FAILED, REFUNDED)

## B. Actual production payment enums
- `payment_status`: PENDING, PAID, FAILED, REFUNDED (Missing CREATED, AUTHORIZED, CAPTURED)
- `payment_provider`: DOES NOT EXIST.

## C. Actual production payment function definitions/behavior
- `place_order`: Expects to write an initial `payment_status = 'PENDING'`.
- `mark_payment_verified`: **Does not exist** on production, or is severely mismatched. It was introduced/updated in `010_payment_gateways.sql`. 

## D. Edge Function expectations
The `create-razorpay-order` Edge Function attempts the following update:
```javascript
.update({
    razorpay_order_id: rzpOrder.id,
    gateway_provider: 'RAZORPAY',
    status: 'CREATED'
})
```

## E. Exact mismatch
1. Function sends `gateway_provider`, which does not exist in production schema.
2. Function sends `razorpay_order_id`, which does not exist in production schema (it is `gateway_order_id`).
3. Function sends `status: 'CREATED'`, which violates the production `payment_status` enum.

## F. Confirmed root cause
The local codebase has evolved significantly through `010_payment_gateways.sql` and `011_razorpay_fixes.sql`. These migrations updated the schema, enums, and RPCs to correctly support Razorpay. However, **these migrations were never applied to the production database.** The Edge Function was deployed expecting the new schema, but the database is still on the old schema.

## G. Whether schema cache is relevant
If `010` and `011` were run, PostgREST schema cache *would* need a reload. However, since the `gateway_provider` column outright fails, the migrations simply haven't been applied. Schema cache is a secondary issue that will resolve itself once the migrations are properly pushed (the Supabase CLI automatically reloads the cache after a push).

## H. Whether `supabase db push` is safe
**IT IS NOT STRICTLY SAFE TO BLINDLY RUN `supabase db push`.**
The prompt explicitly mentions: *"The project has previously had production migrations applied manually, including later migrations, so blindly running `supabase db push` could be unsafe."*
If `012`, `013`, `014`, or `015` were manually applied out of order, or if schema drifts occurred via the Supabase Dashboard, `db push` will fail or potentially corrupt data by re-running conflicting statements.

## I. Smallest safe production fix
The safest approach is to manually execute the exact SQL contents of `010_payment_gateways.sql` and `011_razorpay_fixes.sql` against the production database using the Supabase SQL Editor (or via a direct Postgres connection using the database password). This ensures only the necessary payment schema changes are applied without risking a full `db push` collision.

## J. Required verification after fixing
After applying `010` and `011`:
1. Verify `payment_provider` enum exists.
2. Verify `payments` table has `gateway_provider` and `razorpay_*` columns.
3. Test a complete checkout flow to ensure the Edge Function successfully updates the payment row to `CREATED`.
