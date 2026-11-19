# Razorpay Payment Failure Diagnostic Report (Part 2)

## A. Actual production payments schema
We previously confirmed via the migration history and Edge Function errors that the schema differs. The Edge function logs indicate: `"Could not find the 'gateway_provider' column of 'payments' in the schema cache"`. This confirms the `payments` table lacks this column. The table in production likely looks like:
- `order_id` UUID
- `gateway_payment_id` TEXT
- `gateway_order_id` TEXT
- `gateway_signature` TEXT
- `status` ENUM (PENDING, PAID, FAILED, REFUNDED)

## B. Actual production payment enums
As per `001_initial_schema.sql` (the only one containing enums before `010`):
- `payment_status`: PENDING, PAID, FAILED, REFUNDED.
- `payment_provider`: DOES NOT EXIST.

## C. Actual production payment function definitions/behavior
- `place_order`: Expects `gateway_*` columns based on the early schema logic (or relies on defaults). It creates an order and initializes its payment row to `PENDING`.
- `mark_payment_verified`: Does not exist or is severely mismatched (it expects `razorpay_*` columns based on `010`).

## D. Edge Function expectations
The `create-razorpay-order` edge function specifically requires `gateway_provider`, `razorpay_order_id`, and expects to insert `status: 'CREATED'`.
The `verify-razorpay-payment` expects to call the `mark_payment_verified` RPC and expects the `razorpay_payment_id` and `razorpay_signature` columns.

## E. Exact mismatch
| Element | Local expected | Production actual | Compatible? |
| :--- | :--- | :--- | :--- |
| `payments.gateway_provider` | Exists (Enum `payment_provider`) | Missing | NO |
| `payments.razorpay_order_id` | Exists | Missing (named `gateway_order_id`) | NO |
| `payments.razorpay_payment_id` | Exists | Missing (named `gateway_payment_id`) | NO |
| `payments.razorpay_signature` | Exists | Missing (named `gateway_signature`) | NO |
| `payment_provider` enum | `RAZORPAY`, `CASH` | Missing entirely | NO |
| `payment_status` enum | Has `CREATED`, `AUTHORIZED`, `CAPTURED` | Missing those | NO |
| `place_order` RPC | Sets `payment_method` (often `PAY_AT_COUNTER`) | May error on missing enum | NO |
| `mark_payment_verified` RPC | Exists | Missing | NO |
| `create-razorpay-order` | Uses new schema | Fails on `gateway_provider` | NO |
| `verify-razorpay-payment` | Uses `mark_payment_verified` | Fails due to missing RPC | NO |

## F. Confirmed root cause
The local codebase (and Edge Functions) are written for schema versions `010` and `011`. The production database has not had `010_payment_gateways.sql` (nor `011_razorpay_fixes.sql`) applied to it.

## G. Whether schema cache is relevant
The Edge Function explicitly says `"Could not find the 'gateway_provider' column of 'payments' in the schema cache"`. This *could* mean the migration was run manually via SQL Editor but the PostgREST cache wasn't reloaded (`NOTIFY pgrst, reload schema;`). However, it is overwhelmingly more likely that `010` was simply not run. Both require fixing.

## H. Whether `supabase db push` is safe
**IT IS NOT SAFE.**
Since some migrations may have been manually applied to production (as per instructions: "The project has previously had production migrations applied manually"), running `supabase db push` will attempt to replay migrations that might partially exist, leading to unrecoverable conflicts or unintended data loss (like recreating tables).

## I. Smallest safe production fix
The smallest safe fix is to manually run the SQL from `010_payment_gateways.sql` and `011_razorpay_fixes.sql` directly against the production database using the Supabase SQL Editor.
Because it's possible some pieces exist, you should run them carefully (e.g., checking if the enum value already exists before adding it).
After running the SQL, you MUST execute `NOTIFY pgrst, reload schema;` in the SQL editor to flush the schema cache.

## J. Required verification after fixing
1. Ensure the Edge function can now insert `gateway_provider`.
2. Ensure the order transitions to `CREATED`.
3. Verify Razorpay checkout opens.
4. Verify `verify-razorpay-payment` successfully calls `mark_payment_verified`.
