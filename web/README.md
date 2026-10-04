# Crave Web

The student-first web companion for the SRMIST Crave (GaG) campus food-ordering app.

## Run locally

```bash
pnpm install
pnpm dev
```

The managed project supplies these browser-safe environment values through Webdev secret input:

- `VITE_SUPABASE_URL` — the Supabase project URL; the adapter also normalizes a connector-provided `/rest/v1` URL.
- `VITE_SUPABASE_ANON_KEY` — the public anon key. Do not use a service-role key in browser code.

For a plain local run, set those values in your local environment before `pnpm dev`. If they are unavailable or the live catalog request fails, the site keeps the seeded campus dataset as a graceful fallback.

## Product shape

- **Students:** discover open outlets, search/filter live food, customize items from Supabase variants, add to bag, read real pickup slots, sign in with Supabase Auth, place server-authoritative pickup orders through `place_order`, track status with realtime updates, save bites, and reorder.
- **Vendors:** see incoming-order concepts, quick menu availability, and daily metrics in the secondary role workspace.
- **Admins:** see campus pulse, outlet health, and high-level system metrics in the secondary role workspace.

The browser adapter lives in `src/lib/backend.ts`. It uses the existing schema and RLS policies for public catalog reads, authenticated favorites, carts, pickup slots, and orders. The Android app remains under `/app`; this website lives under `/web` in the original repository mirror.
