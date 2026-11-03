// Verifies supabase/staging/02_staging_app_schema.sql on a disposable REAL PostgreSQL (embedded-postgres) that
// replays CRAVE-Staging's actual starting state: Supabase-like environment -> 01_init_minimal.sql -> migration 023.
// Touches no remote database.
//   EPG_DIR=<node_modules containing embedded-postgres + pg> INIT_SQL=<path to 01_init_minimal.sql> node run.mjs
// Optional: DUMP_POLICIES=1 prints every RLS policy after the catch-up.
import { readFileSync, mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const supa = path.resolve(here, '../..');
const EPG = process.env.EPG_DIR, INIT = process.env.INIT_SQL;
if (!EPG || !INIT) { console.error('set EPG_DIR and INIT_SQL'); process.exit(2); }
const { default: EmbeddedPostgres } = await import(pathToFileURL(path.join(EPG, 'embedded-postgres/dist/index.js')).href);
const pgmod = await import(pathToFileURL(path.join(EPG, 'pg/lib/index.js')).href);
const Client = pgmod.default?.Client ?? pgmod.Client;

const port = 55433 + Math.floor(Math.random() * 500);
const server = new EmbeddedPostgres({ databaseDir: mkdtempSync(path.join(tmpdir(), 'crave-epg-')), user: 'postgres',
  password: 'local-only', port, persistent: false, onLog: () => {}, onError: () => {} });
await server.initialise(); await server.start();
const connect = async () => { const c = new Client({ host: 'localhost', port, user: 'postgres', password: 'local-only', database: 'postgres' }); await c.connect(); return c; };

let pass = 0, fail = 0;
const gaps = [];
const ok = (n, c, x = '') => { c ? pass++ : fail++; console.log(`${c ? 'PASS' : 'FAIL'}  ${n}${c ? '' : '  ' + String(x).slice(0, 200)}`); };
const gap = (n, detail) => { gaps.push(n); console.log(`GAP   ${n}  (${detail})`); };

let db = await connect();
const Q = (s, p) => db.query(s, p);
const AS = async (role, uid, fn) => {
  await Q(`SELECT set_config('request.jwt.claims', $1, false), set_config('request.jwt.claim.sub', $2, false)`,
    [JSON.stringify({ sub: uid ?? '', role }), uid ?? '']);
  await Q(`SET ROLE ${role}`);
  try { return await fn(); } finally { await Q('RESET ROLE'); }
};
const FAILS = async (fn, re) => {
  try { await fn(); return { threw: false }; }
  catch (e) { try { await Q('ROLLBACK'); } catch {} try { await Q('RESET ROLE'); } catch {} return { threw: true, msg: e.message, match: re ? re.test(e.message) : true }; }
};
const count = async (role, uid, sql, p) => (await AS(role, uid, () => Q(sql, p))).rows[0].n;

try {
  // ── Supabase-like environment (what a Supabase project already has before 01_init_minimal) ──
  await Q(`
    CREATE ROLE anon NOLOGIN; CREATE ROLE authenticated NOLOGIN; CREATE ROLE service_role NOLOGIN BYPASSRLS;
    CREATE ROLE supabase_auth_admin NOLOGIN;                                 -- the role GoTrue writes auth.users as
    CREATE SCHEMA auth; CREATE SCHEMA extensions;
    CREATE EXTENSION pgcrypto SCHEMA extensions; CREATE EXTENSION "uuid-ossp" SCHEMA extensions;
    CREATE TABLE auth.users (instance_id uuid, id uuid PRIMARY KEY DEFAULT gen_random_uuid(), aud varchar(255), role varchar(255),
      email varchar(255), encrypted_password varchar(255), email_confirmed_at timestamptz, raw_app_meta_data jsonb,
      raw_user_meta_data jsonb, created_at timestamptz, updated_at timestamptz, confirmation_token varchar(255),
      recovery_token varchar(255), email_change_token_new varchar(255), email_change varchar(255));   -- Supabase auth.users columns used by the seed
    GRANT USAGE ON SCHEMA auth TO supabase_auth_admin; GRANT ALL ON auth.users TO supabase_auth_admin;
    ALTER ROLE supabase_auth_admin SET search_path = auth;                  -- as on Supabase: triggers must qualify names
    CREATE FUNCTION auth.uid() RETURNS uuid LANGUAGE sql STABLE AS $$
      SELECT COALESCE(nullif(current_setting('request.jwt.claim.sub', true), ''),
                      nullif(nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub', ''))::uuid $$;
    GRANT USAGE ON SCHEMA public, auth, extensions TO anon, authenticated, service_role;
    GRANT EXECUTE ON FUNCTION auth.uid() TO anon, authenticated, service_role;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO anon, authenticated, service_role;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO anon, authenticated, service_role;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON FUNCTIONS TO anon, authenticated, service_role;
    CREATE PUBLICATION supabase_realtime;
    ALTER DATABASE postgres SET search_path = "$user", public, extensions;`);
  await db.end(); db = await connect();                                     // pick up the database search_path

  // ── Staging's real starting state ──
  await Q(readFileSync(INIT, 'utf8'));
  await Q(readFileSync(path.join(supa, 'migrations/023_support_tickets.sql'), 'utf8'));
  ok('starting state built: 01_init_minimal.sql + migration 023 (as on staging)', true);
  ok('starting state already has user_role PENDING_VENDOR (no in-transaction enum change needed)',
     (await Q(`SELECT 1 FROM pg_enum WHERE enumtypid='public.user_role'::regtype AND enumlabel='PENDING_VENDOR'`)).rowCount === 1);

  const e1 = await FAILS(() => AS('anon', null, () => Q('SELECT * FROM public.outlets WHERE is_active')), /permission denied for table outlets/);
  ok('BEFORE: "permission denied for table outlets" reproduced', e1.threw && e1.match, e1.msg);
  ok('BEFORE: food_variants missing (cause of the relationship error) reproduced', (await Q(`SELECT to_regclass('public.food_variants') r`)).rows[0].r === null);

  const catchup = readFileSync(path.join(supa, 'staging/02_staging_app_schema.sql'), 'utf8');
  const untouched = async () => (await Q(`SELECT to_regclass('public.carts') IS NULL AND to_regclass('public.food_variants') IS NULL AS u`)).rows[0].u;

  // ── Guard: must fail safely on anything that is not the expected starting schema ──
  await Q(`CREATE TABLE public.zz_unexpected (id int)`);
  let g = await FAILS(() => Q(catchup), /CATCH-UP ABORTED: public tables are/);
  ok('guard aborts when an unexpected public table exists (drift / wrong database)', g.threw && g.match && await untouched(), g.msg);
  await Q(`DROP TABLE public.zz_unexpected`);
  await Q(`ALTER TABLE public.outlets RENAME COLUMN is_active TO is_active_x`);
  g = await FAILS(() => Q(catchup), /CATCH-UP ABORTED: expected columns missing: outlets\.is_active/);
  ok('guard aborts when an expected column is missing', g.threw && g.match && await untouched(), g.msg);
  await Q(`ALTER TABLE public.outlets RENAME COLUMN is_active_x TO is_active`);
  await Q(`ALTER TABLE public.support_ticket_events RENAME TO support_ticket_events_x`);
  g = await FAILS(() => Q(catchup), /CATCH-UP ABORTED: public tables are/);
  ok('guard aborts when migration 023 is not (fully) present', g.threw && g.match && await untouched(), g.msg);
  await Q(`ALTER TABLE public.support_ticket_events_x RENAME TO support_ticket_events`);
  ok('the guard does not claim to verify the Supabase project ref (header states it cannot)',
     /CANNOT verify which Supabase\s*\n?--?\s*project/.test(catchup) || catchup.includes('It CANNOT verify which Supabase'));

  // ── Apply ──
  const applied = await FAILS(() => Q(catchup));
  ok('02_staging_app_schema.sql applies cleanly in one transaction', !applied.threw, applied.msg);
  if (applied.threw) throw new Error('stop');
  const again = await FAILS(() => Q(catchup), /CATCH-UP ABORTED/);
  ok('a second run is refused by the guard', again.threw && again.match, again.msg);
  const code = catchup.replace(/--[^\n]*/g, '');
  const drops = code.match(/\bDROP\s+(TABLE|SCHEMA|TYPE)\b[^;]*;/gi) ?? [];
  ok('only drop: the guarded, non-CASCADE replacement of the empty old-format reviews table; no TRUNCATE',
     drops.length === 1 && drops[0] === 'DROP TABLE IF EXISTS public.reviews;' && !/\bTRUNCATE\s+(TABLE\s+)?\w/i.test(code), drops.join(' | '));

  if (process.env.DUMP_POLICIES) {
    for (const r of (await Q(`SELECT tablename, policyname, cmd, qual, with_check FROM pg_policies WHERE schemaname='public' ORDER BY 1,2`)).rows)
      console.log(`POLICY ${r.tablename} | ${r.policyname} | ${r.cmd} | USING ${r.qual ?? '-'} | CHECK ${r.with_check ?? '-'}`);
  }

  // ── Relationships PostgREST needs for the popular-items embed ──
  const fk = async (from, col, to) => (await Q(`
      SELECT 1 FROM pg_constraint c JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
      WHERE c.contype='f' AND c.conrelid=$1::regclass AND c.confrelid=$2::regclass AND a.attname=$3`, [from, to, col])).rowCount > 0;
  ok('FK food_variants.food_item_id -> food_items', await fk('public.food_variants', 'food_item_id', 'public.food_items'));
  ok('FK food_variant_options.variant_id -> food_variants', await fk('public.food_variant_options', 'variant_id', 'public.food_variants'));
  ok('FK food_items.outlet_id -> outlets and food_items.category_id -> categories',
     await fk('public.food_items', 'outlet_id', 'public.outlets') && await fk('public.food_items', 'category_id', 'public.categories'));

  // ── Grants: exact per-table matrix (RLS decides rows; grants decide operations) ──
  const privs = async (role, t) => (await Q(`SELECT array_to_string(ARRAY(SELECT p FROM unnest(ARRAY['SELECT','INSERT','UPDATE','DELETE','TRUNCATE','TRIGGER','REFERENCES']) p
      WHERE has_table_privilege($1, ('public.' || $2)::regclass, p)), ',') s`, [role, t])).rows[0].s;
  const expect = {
    'SELECT,INSERT,UPDATE,DELETE': ['carts','cart_items','cart_item_customizations','favorites','reviews','review_replies','outlet_reviews',
      'outlet_review_replies','outlets','food_items','food_variants','food_variant_options','inventory','pickup_slots','categories','coupons'],
    'SELECT,INSERT,UPDATE': ['profiles'],
    'SELECT,UPDATE': ['orders','notifications'],
    'SELECT': ['order_items','order_item_customizations','payments','pickup_tokens','audit_logs'],
  };
  for (const [want, tables] of Object.entries(expect)) {
    const wrong = [];
    for (const t of tables) { const got = await privs('authenticated', t); if (got !== want) wrong.push(`${t}=${got}`); }
    ok(`authenticated has exactly ${want} on ${tables.length} tables`, wrong.length === 0, wrong.join(' '));
  }
  const unlisted = (await Q(`SELECT string_agg(relname, ',') s FROM pg_class WHERE relnamespace='public'::regnamespace AND relkind='r'
      AND relname <> ALL($1::text[]) AND relname NOT LIKE 'support_ticket%'`, [Object.values(expect).flat()])).rows[0].s;
  ok('no table outside the matrix was missed (unlisted tables would default to read-only)', !unlisted, unlisted);
  const anonRead = (await Q(`SELECT string_agg(relname, ',' ORDER BY relname) s FROM pg_class WHERE relnamespace='public'::regnamespace
      AND relkind='r' AND has_table_privilege('anon', oid, 'SELECT')`)).rows[0].s;
  ok('anon reads only the public catalogue', anonRead === 'categories,food_items,food_variant_options,food_variants,inventory,outlets,pickup_slots', anonRead);
  const anonWrite = (await Q(`SELECT count(*)::int n FROM pg_class c, unnest(ARRAY['INSERT','UPDATE','DELETE','TRUNCATE']) p
      WHERE c.relnamespace='public'::regnamespace AND c.relkind='r' AND has_table_privilege('anon', c.oid, p)`)).rows[0].n;
  ok('anon has no write privilege anywhere', anonWrite === 0, anonWrite);
  const noRls = (await Q(`SELECT string_agg(relname, ',') s FROM pg_class WHERE relnamespace='public'::regnamespace AND relkind='r' AND NOT relrowsecurity`)).rows[0].s;
  ok('RLS enabled on every public table', !noRls, noRls);
  const sup = (await Q(`SELECT has_table_privilege('anon','public.support_tickets','SELECT') a,
                               has_table_privilege('authenticated','public.support_ticket_internal_notes','SELECT') n,
                               has_table_privilege('authenticated','public.support_tickets','INSERT') i`)).rows[0];
  ok("migration 023's ticket grants untouched (anon denied, notes unreadable, writes only via RPC)", !sup.a && !sup.n && !sup.i, JSON.stringify(sup));

  // ── Profile creation: sign-up trigger, run the way GoTrue runs it (role supabase_auth_admin, search_path=auth) ──
  const NEWU = 'a0000000-0000-0000-0000-00000000000a';
  const signup = await FAILS(async () => {
    await Q(`SET ROLE supabase_auth_admin`); await Q(`SET search_path = auth`);
    try { await Q(`INSERT INTO auth.users (id, email, raw_user_meta_data) VALUES ($1, 'new.student@x.test', '{"name":"New Student"}')`, [NEWU]); }
    finally { await Q(`RESET ROLE`); await Q(`SET search_path = "$user", public, extensions`); }
  });
  ok('sign-up (insert into auth.users as supabase_auth_admin) succeeds', !signup.threw, signup.msg);
  // Design (migration 007): no auth trigger; the app creates the profile on the first verified login.
  ok('sign-up does not depend on a database trigger (none on auth.users, so sign-up cannot fail on profile errors)',
     (await Q(`SELECT count(*)::int n FROM pg_trigger WHERE tgrelid='auth.users'::regclass AND NOT tgisinternal`)).rows[0].n === 0 &&
     (await Q(`SELECT count(*)::int n FROM public.profiles WHERE id=$1`, [NEWU])).rows[0].n === 0);
  const firstLogin = await FAILS(() => AS('authenticated', NEWU, () => Q(`INSERT INTO public.profiles (id, name, email, role) VALUES ($1,'New Student','new.student@x.test','STUDENT')`, [NEWU])));
  const created = (await Q(`SELECT role::text r, is_active FROM public.profiles WHERE id=$1`, [NEWU])).rows[0];
  ok("first verified login: the app's profile insert creates an active STUDENT profile", !firstLogin.threw && created?.r === 'STUDENT' && created?.is_active === true, firstLogin.msg);
  const readBack = await count('authenticated', NEWU, `SELECT count(*)::int n FROM public.profiles WHERE id=$1`, [NEWU]);
  ok('…and the login flow can read it back (step 8 of SupabaseAuthRepository.login)', readBack === 1);

  // ── Seed synthetic users and catalogue (superuser, triggers off) ──
  const ID = { stu: 'b0000000-0000-0000-0000-000000000001', stu2: 'b0000000-0000-0000-0000-000000000002',
               ven: 'b0000000-0000-0000-0000-000000000003', ven2: 'b0000000-0000-0000-0000-000000000004',
               adm: 'b0000000-0000-0000-0000-000000000005', late: 'b0000000-0000-0000-0000-000000000006' };
  const OUT = 'c0000000-0000-0000-0000-000000000001', OUT_OFF = 'c0000000-0000-0000-0000-000000000002', OUT2 = 'c0000000-0000-0000-0000-000000000003';
  const FOOD = 'd0000000-0000-0000-0000-000000000001';
  await Q(`SET session_replication_role = replica`);
  for (const [k, role] of [['stu','STUDENT'],['stu2','STUDENT'],['ven','VENDOR'],['ven2','VENDOR'],['adm','ADMIN']]) {
    await Q(`INSERT INTO auth.users (id, email) VALUES ($1, $2)`, [ID[k], `${k}@x.test`]);
    await Q(`INSERT INTO public.profiles (id, name, email, role) VALUES ($1, $2, $3, $4::user_role)`, [ID[k], k, `${k}@x.test`, role]);
  }
  await Q(`INSERT INTO auth.users (id, email) VALUES ($1, 'late@x.test')`, [ID.late]);   // auth user whose profile row is missing
  await Q(`INSERT INTO public.categories (id, name, emoji) VALUES ('e0000000-0000-0000-0000-000000000001','Snacks','x')`);
  await Q(`INSERT INTO public.outlets (id, name, description, vendor_id, is_open, is_active) VALUES
           ($1,'Open Cafe','d',$4,true,true), ($2,'Closed Down','d',$4,false,false), ($3,'Other Vendor Cafe','d',$5,true,true)`,
           [OUT, OUT_OFF, OUT2, ID.ven, ID.ven2]);
  await Q(`INSERT INTO public.food_items (id, outlet_id, category_id, name, description, price, is_popular) VALUES
           ($1,$2,'e0000000-0000-0000-0000-000000000001','Samosa','d',20,true),
           ('d0000000-0000-0000-0000-000000000002',$3,'e0000000-0000-0000-0000-000000000001','Hidden','d',30,true)`, [FOOD, OUT, OUT_OFF]);
  await Q(`INSERT INTO public.food_variants (id, food_item_id, name) VALUES ('f0000000-0000-0000-0000-000000000001',$1,'Size')`, [FOOD]);
  await Q(`INSERT INTO public.food_variant_options (variant_id, name, extra_price) VALUES ('f0000000-0000-0000-0000-000000000001','Large',10)`);
  await Q(`INSERT INTO public.inventory (food_item_id, quantity_available) VALUES ($1, 50)
           ON CONFLICT (food_item_id) DO UPDATE SET quantity_available = 50`, [FOOD]);
  await Q(`SET session_replication_role = origin`);

  // ── Login-related profile reads / app fallback profile insert ──
  ok('login: a student can read their own profile', await count('authenticated', ID.stu, `SELECT count(*)::int n FROM public.profiles WHERE id=$1`, [ID.stu]) === 1);
  ok("a student cannot read another student's profile", await count('authenticated', ID.stu, `SELECT count(*)::int n FROM public.profiles WHERE id=$1`, [ID.stu2]) === 0);
  ok('a student sees only their own profile row in total', await count('authenticated', ID.stu, `SELECT count(*)::int n FROM public.profiles`) === 1);
  ok('anon cannot read profiles at all', (await FAILS(() => AS('anon', null, () => Q(`SELECT 1 FROM public.profiles`)), /permission denied/)).match);
  ok('an admin can read all profiles', await count('authenticated', ID.adm, `SELECT count(*)::int n FROM public.profiles`) === (await Q(`SELECT count(*)::int n FROM public.profiles`)).rows[0].n);
  const lateOwn = await FAILS(() => AS('authenticated', ID.late, () => Q(`INSERT INTO public.profiles (id, name, email, role) VALUES ($1,'Late','late@x.test','STUDENT')`, [ID.late])));
  ok("app fallback: a user can create their OWN missing profile as STUDENT", !lateOwn.threw, lateOwn.msg);
  const selfVendor = await FAILS(() => AS('authenticated', NEWU, () => Q(`INSERT INTO public.profiles (id, name, email, role) VALUES (gen_random_uuid(),'x','x@x.test','VENDOR')`)));
  ok('nobody can create a profile for another id or self-assign VENDOR', selfVendor.threw, selfVendor.msg);
  const esc = await FAILS(() => AS('authenticated', ID.stu, () => Q(`UPDATE public.profiles SET role='ADMIN' WHERE id=$1`, [ID.stu])), /cannot change your own role/);
  ok('a student cannot escalate their own role', esc.threw && esc.match, esc.msg);
  const reactivate = await AS('authenticated', ID.stu, () => Q(`UPDATE public.profiles SET is_active = is_active WHERE id=$1`, [ID.stu]));
  if (reactivate.rowCount === 1) gap('profiles: a user may change their own is_active (a disabled account could re-enable itself via the API)',
     'repo policy "Users can update their own profile" has no column limits; prevent_role_escalation guards only role — same as production design');

  // ── Public outlets and food items, nested variants ──
  for (const [who, role, uid] of [['anon','anon',null], ['student','authenticated',ID.stu]]) {
    const o = await AS(role, uid, () => Q(`SELECT name FROM public.outlets WHERE is_active = true ORDER BY name`));
    ok(`${who}: outlets?is_active=eq.true returns the active outlets`, o.rows.map(r => r.name).join() === 'Open Cafe,Other Vendor Cafe', o.rows.map(r => r.name).join());
    ok(`${who}: inactive outlets hidden by RLS even without the filter`,
       await count(role, uid, `SELECT count(*)::int n FROM public.outlets WHERE id=$1`, [OUT_OFF]) === 0);
    const pop = await AS(role, uid, () => Q(`
      SELECT f.name, o.name outlet, c.name category, v.name variant, vo.name opt
      FROM public.food_items f JOIN public.outlets o ON o.id=f.outlet_id JOIN public.categories c ON c.id=f.category_id
      LEFT JOIN public.food_variants v ON v.food_item_id=f.id LEFT JOIN public.food_variant_options vo ON vo.variant_id=v.id
      WHERE f.is_popular`));
    ok(`${who}: popular items resolve outlet, category, variant and nested option; the inactive outlet's item is hidden`,
       pop.rows.length === 1 && pop.rows[0].outlet === 'Open Cafe' && pop.rows[0].category === 'Snacks' && pop.rows[0].variant === 'Size' && pop.rows[0].opt === 'Large',
       JSON.stringify(pop.rows));
  }

  // ── search_food (Search/Explore screens) ──
  const sBefore = await FAILS(() => AS('anon', null, () => Q(`SELECT public.search_food('Sam') r`)), /must appear in the GROUP BY/);
  ok('BEFORE 03: search_food fails with 42803 exactly as observed live on staging', sBefore.threw && sBefore.match, sBefore.msg);
  const sFix = await FAILS(() => Q(readFileSync(path.join(supa, 'staging/03_fix_search_food.sql'), 'utf8')));
  ok('03_fix_search_food.sql applies cleanly', !sFix.threw, sFix.msg);
  const srch = async (role, uid, args) => (await AS(role, uid, () => Q(`SELECT public.search_food(${args}) r`))).rows[0].r;
  const r1 = await srch('anon', null, `'sam'`);
  ok('search_food returns matching items with outlet and category names (anon)',
     r1.length === 1 && r1[0].name === 'Samosa' && r1[0].outlet_name === 'Open Cafe' && r1[0].category_name === 'Snacks', JSON.stringify(r1));
  ok("search_food hides items of inactive outlets from students", (await srch('authenticated', ID.stu, `'Hidden'`)).length === 0);
  ok('search_food filters still apply (category, veg, max price, outlet)',
     (await srch('anon', null, `'', NULL, 'Snacks'`)).length === 1 && (await srch('anon', null, `'', NULL, NULL, false`)).length === 0 &&
     (await srch('anon', null, `'', NULL, NULL, NULL, 10`)).length === 0 && (await srch('anon', null, `'', '${OUT2}'`)).length === 0);
  ok('search_food with no match returns an empty array, not an error', (await srch('anon', null, `'zzzz'`)).length === 0);
  const sig = (await Q(`SELECT p.prosecdef d, p.provolatile v, p.proconfig::text c FROM pg_proc p WHERE p.oid='public.search_food(text,uuid,text,boolean,numeric,boolean)'::regprocedure`)).rows[0];
  ok('search_food keeps its signature and attributes (SECURITY DEFINER, STABLE, search_path)', sig.d === true && sig.v === 's' && /search_path=public/.test(sig.c), JSON.stringify(sig));

  // ── Inventory updates (+ issue 1: audit trigger on inventory) ──
  ok('inventory has an id primary key in this schema (audit_critical_changes uses NEW.id)',
     (await Q(`SELECT 1 FROM pg_index i JOIN pg_attribute a ON a.attrelid=i.indrelid AND a.attnum=ANY(i.indkey)
               WHERE i.indrelid='public.inventory'::regclass AND i.indisprimary AND a.attname='id'`)).rowCount === 1);
  const invId = (await Q(`SELECT id FROM public.inventory WHERE food_item_id=$1`, [FOOD])).rows[0].id;
  const vUpd = await FAILS(() => AS('authenticated', ID.ven, () => Q(`UPDATE public.inventory SET quantity_available = 40 WHERE id=$1 AND quantity_available = 50`, [invId])));
  ok('the owning vendor can update stock (compare-and-set form used by the app)', !vUpd.threw && (await Q(`SELECT quantity_available q FROM public.inventory WHERE id=$1`, [invId])).rows[0].q === 40, vUpd.msg);
  ok('the inventory update wrote an audit row keyed by inventory.id (audit trigger works for inventory)',
     (await Q(`SELECT count(*)::int n FROM public.audit_logs WHERE table_name='inventory' AND record_id=$1`, [invId])).rows[0].n >= 1);
  ok("another vendor's stock update changes nothing (RLS)", (await AS('authenticated', ID.ven2, () => Q(`UPDATE public.inventory SET quantity_available = 1 WHERE id=$1`, [invId]))).rowCount === 0);
  ok("a student's stock update changes nothing (RLS)", (await AS('authenticated', ID.stu, () => Q(`UPDATE public.inventory SET quantity_available = 1 WHERE id=$1`, [invId]))).rowCount === 0);
  ok('anon cannot update stock', (await FAILS(() => AS('anon', null, () => Q(`UPDATE public.inventory SET quantity_available = 1`)), /permission denied/)).match);

  // ── Order creation (real place_order) ──
  const slot = (await Q(`INSERT INTO public.pickup_slots (outlet_id, slot_date, start_time, end_time, capacity)
      VALUES ($1, (now() AT TIME ZONE 'Asia/Kolkata')::date + 2, '12:00', '12:30', 10) RETURNING id`, [OUT])).rows[0].id;
  const cart = (await AS('authenticated', ID.stu, () => Q(`INSERT INTO public.carts (user_id, outlet_id) VALUES ($1,$2) RETURNING id`, [ID.stu, OUT]))).rows[0].id;
  await AS('authenticated', ID.stu, () => Q(`INSERT INTO public.cart_items (cart_id, food_item_id, quantity, price, is_veg) VALUES ($1,$2,2,20,true)`, [cart, FOOD]));
  ok('a student can build their own cart (direct writes the app performs)', true);
  let orderId;
  const placed = await FAILS(async () => { orderId = (await AS('authenticated', ID.stu, () => Q(`SELECT public.place_order($1,$2,'PAY_AT_COUNTER') id`, [cart, slot]))).rows[0].id; });
  ok('place_order creates an order for the signed-in student', !placed.threw && !!orderId, placed.msg);
  ok('place_order decremented inventory (40 -> 38) and booked the slot',
     (await Q(`SELECT quantity_available q FROM public.inventory WHERE id=$1`, [invId])).rows[0].q === 38 &&
     (await Q(`SELECT booked_count b FROM public.pickup_slots WHERE id=$1`, [slot])).rows[0].b === 1);
  ok('place_order created order items, a payment row and a pickup token',
     (await Q(`SELECT (SELECT count(*) FROM public.order_items WHERE order_id=$1)::int i, (SELECT count(*) FROM public.payments WHERE order_id=$1)::int p,
               (SELECT count(*) FROM public.pickup_tokens WHERE order_id=$1)::int t`, [orderId])).rows.every(r => r.i === 1 && r.p === 1 && r.t === 1));

  // ── Orders / payments isolation per role ──
  const ord = (uid) => count('authenticated', uid, `SELECT count(*)::int n FROM public.orders WHERE id=$1`, [orderId]);
  ok('orders: the student sees their order', await ord(ID.stu) === 1);
  ok("orders: another student cannot see it", await ord(ID.stu2) === 0);
  ok('orders: the outlet vendor sees it (pay-at-counter)', await ord(ID.ven) === 1);
  ok("orders: another outlet's vendor cannot see it", await ord(ID.ven2) === 0);
  ok('orders: an admin sees it', await ord(ID.adm) === 1);
  ok('anon cannot read orders', (await FAILS(() => AS('anon', null, () => Q(`SELECT 1 FROM public.orders`)), /permission denied/)).match);
  const forge = await FAILS(() => AS('authenticated', ID.stu, () => Q(`INSERT INTO public.orders (order_number,user_id,vendor_id,outlet_id,subtotal,tax,total,status)
      VALUES ('FORGED',$1,$2,$3,0,0,0,'PLACED')`, [ID.stu, ID.ven, OUT])), /permission denied/);
  ok('a client cannot insert orders directly (only place_order can)', forge.threw && forge.match, forge.msg);
  const del = await FAILS(() => AS('authenticated', ID.ven, () => Q(`DELETE FROM public.orders WHERE id=$1`, [orderId])), /permission denied/);
  ok("a vendor cannot delete their outlet's orders (history is preserved)", del.threw && del.match, del.msg);
  const delItems = await FAILS(() => AS('authenticated', ID.adm, () => Q(`DELETE FROM public.order_items WHERE order_id=$1`, [orderId])), /permission denied/);
  ok('order items are read-only for every client role', delItems.threw && delItems.match, delItems.msg);
  const pay = (uid) => count('authenticated', uid, `SELECT count(*)::int n FROM public.payments WHERE order_id=$1`, [orderId]);
  ok('payments: the student sees their payment', await pay(ID.stu) === 1);
  ok("payments: another student cannot see it", await pay(ID.stu2) === 0);
  ok("payments: another outlet's vendor cannot see it", await pay(ID.ven2) === 0);
  ok('payments: an admin can see it', await pay(ID.adm) === 1);
  const payUpd = await FAILS(() => AS('authenticated', ID.stu, () => Q(`UPDATE public.payments SET status='PAID' WHERE order_id=$1`, [orderId])), /permission denied/);
  ok('a client cannot mark a payment PAID by direct UPDATE', payUpd.threw && payUpd.match, payUpd.msg);
  const tokUpd = await FAILS(() => AS('authenticated', ID.ven, () => Q(`UPDATE public.pickup_tokens SET is_used=true WHERE order_id=$1`, [orderId])), /permission denied/);
  ok('pickup tokens are read-only for clients', tokUpd.threw && tokUpd.match, tokUpd.msg);
  const cancel = await AS('authenticated', ID.stu, () => Q(`UPDATE public.orders SET status='CANCELLED' WHERE id=$1`, [orderId]));
  ok('a student can still cancel their own pending order (the one direct order write the app does)', cancel.rowCount === 1);
  ok('cancelling restored inventory and the slot',
     (await Q(`SELECT quantity_available q FROM public.inventory WHERE id=$1`, [invId])).rows[0].q === 40 &&
     (await Q(`SELECT booked_count b FROM public.pickup_slots WHERE id=$1`, [slot])).rows[0].b === 0);
  const vendorTotal = await AS('authenticated', ID.ven, () => Q(`UPDATE public.orders SET total = 0 WHERE id=$1`, [orderId]));
  if (vendorTotal.rowCount === 1) gap("orders: a vendor can change amounts on their outlet's orders",
     'repo policy "Vendors manage their outlet\'s orders" is FOR ALL without column limits; the status state machine does not cover amounts');

  // ── Support tickets (migration 023 untouched, still isolated) ──
  await Q(`SET session_replication_role = replica`); await Q(`UPDATE public.orders SET status='PICKED_UP' WHERE id=$1`, [orderId]); await Q(`SET session_replication_role = origin`);
  const tk = await FAILS(() => AS('authenticated', ID.stu, () => Q(`SELECT public.create_support_ticket('ORDER','Cold food','The food was cold and stale',$1,NULL,NULL,gen_random_uuid())`, [orderId])));
  ok('a student can raise a support ticket on their order (023 RPC works with the new schema)', !tk.threw, tk.msg);
  ok('tickets: the student sees their ticket', await count('authenticated', ID.stu, `SELECT count(*)::int n FROM public.support_tickets`) === 1);
  ok('tickets: another student sees none', await count('authenticated', ID.stu2, `SELECT count(*)::int n FROM public.support_tickets`) === 0);
  ok('tickets: a vendor sees none', await count('authenticated', ID.ven, `SELECT count(*)::int n FROM public.support_tickets`) === 0);
  ok('tickets: anon is denied', (await FAILS(() => AS('anon', null, () => Q(`SELECT 1 FROM public.support_tickets`)), /permission denied/)).match);

  // ── 04 synthetic seed: idempotent, valid, and usable by the app's real queries ──
  const seed = readFileSync(path.join(supa, 'staging/04_seed_staging_test_data.sql'), 'utf8');
  // Mirror the Supabase SQL Editor: no signed-in user (no JWT settings in the session).
  await Q(`SELECT set_config('request.jwt.claim.sub', '', false), set_config('request.jwt.claims', '', false)`);
  const s1 = await FAILS(() => Q(seed));
  ok('04_seed_staging_test_data.sql applies cleanly', !s1.threw, s1.msg);
  const snap = async () => (await Q(`SELECT (SELECT count(*) FROM auth.users)::int u, (SELECT count(*) FROM public.outlets)::int o,
      (SELECT count(*) FROM public.categories)::int c, (SELECT count(*) FROM public.food_items)::int f, (SELECT count(*) FROM public.food_variants)::int v,
      (SELECT count(*) FROM public.food_variant_options)::int vo, (SELECT count(*) FROM public.inventory)::int i, (SELECT count(*) FROM public.pickup_slots)::int s`)).rows[0];
  const before = await snap();
  const s2 = await FAILS(() => Q(seed));
  ok('the vendor identity used by the seed does not leak past COMMIT',
     (await Q(`SELECT COALESCE(current_setting('request.jwt.claim.sub', true), '') AS s`)).rows[0].s === '');
  const after = await snap();
  ok('re-running the seed creates no duplicates', !s2.threw && JSON.stringify(before) === JSON.stringify(after), JSON.stringify([before, after]));
  ok('seeded vendor identity cannot sign in (no password) and is a VENDOR profile',
     (await Q(`SELECT u.encrypted_password = '' AS nopw, p.role::text r FROM auth.users u JOIN public.profiles p ON p.id=u.id WHERE u.id='5eed0000-0000-4000-8000-000000000001'`)).rows.every(r => r.nopw && r.r === 'VENDOR'));
  ok('seeded future pickup slots exist (none in the past)',
     (await Q(`SELECT count(*)::int n FROM public.pickup_slots WHERE outlet_id='5eed0000-0000-4000-8000-0000000000a1'`)).rows[0].n >= 20 &&
     (await Q(`SELECT count(*)::int n FROM public.pickup_slots WHERE outlet_id='5eed0000-0000-4000-8000-0000000000a1' AND slot_date < (now() AT TIME ZONE 'Asia/Kolkata')::date`)).rows[0].n === 0);
  ok('seeded stock is realistic (50; sold-out item 0), not the 1,000,000 placeholder',
     (await Q(`SELECT string_agg(quantity_available::text, ',' ORDER BY food_item_id) q FROM public.inventory WHERE food_item_id::text LIKE '5eed0000-0000-4000-8000-0000000000f%'`)).rows[0].q === '50,50,50,50,0,50');
  const pub = await AS('anon', null, () => Q(`SELECT name FROM public.outlets WHERE is_active ORDER BY name`));
  ok('anon sees the active TEST outlet and never the inactive one', pub.rows.some(r => r.name === 'TEST Kitchen (staging)') && !pub.rows.some(r => /inactive/.test(r.name)));
  const emb = await AS('anon', null, () => Q(`SELECT f.name, c.name cat, count(vo.id)::int opts FROM public.food_items f
      JOIN public.categories c ON c.id=f.category_id LEFT JOIN public.food_variants v ON v.food_item_id=f.id
      LEFT JOIN public.food_variant_options vo ON vo.variant_id=v.id WHERE f.name='TEST Masala Dosa' GROUP BY 1,2`));
  ok('anon: seeded item resolves category and its 2 variant options', emb.rows[0]?.cat === 'TEST Meals' && emb.rows[0]?.opts === 2, JSON.stringify(emb.rows));
  ok("anon: the inactive outlet's TEST Hidden Item is invisible", (await AS('anon', null, () => Q(`SELECT count(*)::int n FROM public.food_items WHERE name='TEST Hidden Item'`))).rows[0].n === 0);
  const srchSeed = (await AS('anon', null, () => Q(`SELECT public.search_food('TEST') r`))).rows[0].r.map(x => x.name).sort();
  ok('search_food("TEST") returns the visible TEST items (available only, inactive outlet hidden)',
     JSON.stringify(srchSeed) === JSON.stringify(['TEST Chicken Biryani', 'TEST Cold Coffee', 'TEST Masala Dosa', 'TEST Samosa']), JSON.stringify(srchSeed));
  const slotS = (await Q(`SELECT id FROM public.pickup_slots WHERE outlet_id='5eed0000-0000-4000-8000-0000000000a1' ORDER BY slot_date DESC, start_time LIMIT 1`)).rows[0].id;
  await Q(`DELETE FROM public.cart_items WHERE cart_id IN (SELECT id FROM public.carts WHERE user_id=$1)`, [ID.stu2]);
  await Q(`DELETE FROM public.carts WHERE user_id=$1`, [ID.stu2]);
  const cartS = (await AS('authenticated', ID.stu2, () => Q(`INSERT INTO public.carts (user_id, outlet_id) VALUES ($1,'5eed0000-0000-4000-8000-0000000000a1') RETURNING id`, [ID.stu2]))).rows[0].id;
  await AS('authenticated', ID.stu2, () => Q(`INSERT INTO public.cart_items (cart_id, food_item_id, quantity, price, is_veg) VALUES ($1,'5eed0000-0000-4000-8000-0000000000f1',3,20,true)`, [cartS]));
  const placedS = await FAILS(() => AS('authenticated', ID.stu2, () => Q(`SELECT public.place_order($1,$2,'PAY_AT_COUNTER')`, [cartS, slotS])));
  ok('a student can order seeded TEST Samosa x3 into a seeded slot; stock 50 -> 47',
     !placedS.threw && (await Q(`SELECT quantity_available q FROM public.inventory WHERE food_item_id='5eed0000-0000-4000-8000-0000000000f1'`)).rows[0].q === 47, placedS.msg);
  await Q(`SELECT set_config('request.jwt.claim.sub', '', false), set_config('request.jwt.claims', '', false)`);
  const s3 = await FAILS(() => Q(seed));
  ok('re-running the seed after orders does not reset tested stock', !s3.threw &&
     (await Q(`SELECT quantity_available q FROM public.inventory WHERE food_item_id='5eed0000-0000-4000-8000-0000000000f1'`)).rows[0].q === 47);
} catch (e) {
  if (e.message !== 'stop') { fail++; console.log('FAIL  unexpected: ' + e.message); }
} finally {
  console.log(`\n${pass} passed, ${fail} failed, ${gaps.length} known gap(s) reported (pre-existing repo policy design, not introduced by this script)`);
  await db.end().catch(() => {}); await server.stop();
  process.exit(fail ? 1 : 0);
}
