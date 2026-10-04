# Crave Web

The student-first web companion for the SRMIST Crave (GaG) campus food-ordering app.

## Run locally

```bash
pnpm install
pnpm dev
```

The app uses seeded campus data and client-side state so the core discovery, menu, customization, bag, pickup, checkout, tracking/QR, favorites, history, vendor, and admin demo flows work without browser secrets or a live backend.

## Product shape

- **Students:** discover open outlets, search/filter food, customize items, add to bag, choose pickup slots, place demo orders, track live status, show QR pickup tokens, save bites, and reorder.
- **Vendors:** see incoming orders, quick menu availability, and daily metrics.
- **Admins:** see campus pulse, outlet health, and high-level system metrics.

The Android app remains under `/app`; this website lives under `/web` in the original repository mirror.
