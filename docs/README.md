# Repository documentation

This folder contains documentation that supports the Crave application.

## Diagnostics archive

`diagnostics/archive/` contains historical investigation notes and ad-hoc SQL diagnostics retained for context. These files are not maintained scripts, deployment instructions, or the source of truth for the current database.

For the current database definition, use the ordered migrations in `supabase/migrations/`. Review every migration and the target environment before applying changes. Do not run archived SQL diagnostics against a live environment.
