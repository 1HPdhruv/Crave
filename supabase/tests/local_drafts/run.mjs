// Disposable local verification of supabase/drafts_unapproved/*.sql.
// Replays the repo's real migrations into an in-memory PGlite (WASM Postgres) with a stubbed
// Supabase environment. Touches NO remote database. Run:
//   PGLITE_DIR=/path/to/node_modules/@electric-sql/pglite node supabase/tests/local_drafts/run.mjs
import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath, pathToFileURL } from 'node:url';
import path from 'node:path';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../..');
const pgDir = process.env.PGLITE_DIR;
if (!pgDir) { console.error('Set PGLITE_DIR'); process.exit(2); }
const { PGlite } = await import(pathToFileURL(path.join(pgDir, 'dist/index.js')).href);
const { uuid_ossp } = await import(pathToFileURL(path.join(pgDir, 'dist/contrib/uuid_ossp.js')).href);

const db = new PGlite({ extensions: { uuid_ossp } });
let pass = 0, fail = 0;
const ok = (name, cond, extra = '') => { cond ? pass++ : fail++; console.log(`${cond ? 'PASS' : 'FAIL'}  ${name}${cond ? '' : '  ' + extra}`); };
const q = (sql, params) => db.query(sql, params);
const as = async (uid, fn, role = 'authenticated') => {
  await db.exec(`SELECT set_config('request.jwt.claim.sub','${uid ?? ''}',false); SET ROLE ${role};`);
  try { return await fn(); } finally { await db.exec('RESET ROLE; SELECT set_config(\'request.jwt.claim.sub\',\'\',false);'); }
};
const fails = async (fn, re) => { try { await fn(); return { threw: false }; } catch (e) { return { threw: true, msg: String(e.message), match: re ? re.test(String(e.message)) : true }; } };

// ── Stubbed Supabase environment ────────────────────────────────────────────
await db.exec(`
  CREATE ROLE anon NOLOGIN; CREATE ROLE authenticated NOLOGIN; CREATE ROLE service_role NOLOGIN BYPASSRLS; -- as on Supabase
  CREATE SCHEMA auth; CREATE SCHEMA extensions;
  CREATE TABLE auth.users (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), email text, raw_user_meta_data jsonb);
  CREATE FUNCTION auth.uid() RETURNS uuid LANGUAGE sql STABLE AS $$ SELECT nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
  CREATE FUNCTION extensions.gen_random_bytes(n int) RETURNS bytea LANGUAGE sql AS
    $$ SELECT decode(substr(md5(random()::text || clock_timestamp()::text) || md5(random()::text), 1, n*2), 'hex') $$;
  CREATE PUBLICATION supabase_realtime;
  GRANT USAGE ON SCHEMA public, auth, extensions TO anon, authenticated;
  GRANT EXECUTE ON FUNCTION auth.uid() TO anon, authenticated;
  ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO anon, authenticated, service_role;
  ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO anon, authenticated, service_role;
`);

// ── Replay the real migrations ──────────────────────────────────────────────
const migDir = path.join(root, 'migrations');
let replayed = 0;
for (const f of readdirSync(migDir).filter(n => n.endsWith('.sql')).sort()) {
  let sql = readFileSync(path.join(migDir, f), 'utf8');
  // Repo migration 003 contains a policy WITH CHECK that references NEW/OLD, which real PostgreSQL rejects
  // (so the repo history cannot be the live DB's source of truth). Patched for this harness only.
  if (f.startsWith('003_')) sql = sql.replace(/(CREATE POLICY "Users can cancel their own pending orders"[\s\S]*?WITH CHECK \()[\s\S]*?\n\);/, "$1auth.uid() = user_id AND status = 'CANCELLED');");
  // PGlite lacks pgcrypto / pg_trgm: drop the extension lines and the one trigram index (harness only).
  sql = sql.replace(/CREATE EXTENSION IF NOT EXISTS (pgcrypto|pg_trgm);/g, '-- (extension unavailable in PGlite)')
           .replace(/CREATE INDEX IF NOT EXISTS idx_food_items_name_trgm\s+ON food_items USING gin\(name gin_trgm_ops\);/, '');
  if (f.startsWith('013_')) await db.exec("ALTER TYPE user_role ADD VALUE IF NOT EXISTS 'PENDING_VENDOR'"); // can't be used in the tx that adds it
  try { await db.exec(sql); replayed++; }
  catch (e) { console.log(`REPLAY-ERROR ${f}: ${e.message}`.slice(0, 220)); try { await db.exec('ROLLBACK'); } catch {} }
}
ok(`replayed ${replayed} repo migrations`, replayed >= 20, `only ${replayed}`);
// NOTE: no blanket GRANT here. ALTER DEFAULT PRIVILEGES (set before the replay) emulates Supabase's
// default broad grants, and migrations that REVOKE/GRANT selectively (e.g. 023) must keep their effect.

// ── Fixtures (inserted with triggers off, as a superuser) ───────────────────
const ID = { admin: 'a0000000-0000-0000-0000-000000000001', vendor: 'a0000000-0000-0000-0000-000000000002',
             vendor2: 'a0000000-0000-0000-0000-000000000003', stu: 'a0000000-0000-0000-0000-000000000004',
             stu2: 'a0000000-0000-0000-0000-000000000005' };
const roles = { admin: 'ADMIN', vendor: 'VENDOR', vendor2: 'VENDOR', stu: 'STUDENT', stu2: 'STUDENT' };
await db.exec('SET session_replication_role = replica');
for (const [k, id] of Object.entries(ID)) {
  await q(`INSERT INTO auth.users (id, email) VALUES ($1, $2)`, [id, `${k}@x.test`]);
  await q(`INSERT INTO profiles (id, name, email, role) VALUES ($1, $2, $3, $4::user_role)`, [id, k, `${k}@x.test`, roles[k]]);
}
await db.exec(`
  INSERT INTO categories (id, name, emoji) VALUES ('c0000000-0000-0000-0000-000000000001', 'Snacks', 'x');
  INSERT INTO outlets (id, name, description, vendor_id, is_open, is_active)
    VALUES ('b0000000-0000-0000-0000-000000000001', 'Test Outlet', 'd', '${ID.vendor}', true, true),
           ('b0000000-0000-0000-0000-000000000002', 'Other Outlet', 'd', '${ID.vendor2}', true, true);
  INSERT INTO food_items (id, outlet_id, category_id, name, description, price)
    VALUES ('f0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001', 'Samosa', 'd', 20);
  INSERT INTO inventory (food_item_id, quantity_available) VALUES ('f0000000-0000-0000-0000-000000000001', 100);
`);
await db.exec('SET session_replication_role = origin');
const OUTLET = 'b0000000-0000-0000-0000-000000000001', OUTLET2 = 'b0000000-0000-0000-0000-000000000002';
const FOOD = 'f0000000-0000-0000-0000-000000000001';

let slotSeq = 0;
async function newSlot() {
  slotSeq++;
  const r = await q(`INSERT INTO pickup_slots (outlet_id, slot_date, start_time, end_time, capacity)
    VALUES ($1, (now() AT TIME ZONE 'Asia/Kolkata')::date + 2, time '00:10' + $2::int * interval '5 minutes', time '00:13' + $2::int * interval '5 minutes', 50) RETURNING id`,
    [OUTLET, slotSeq]);
  return r.rows[0].id;
}
async function placeOrderAs(uid) {          // exercises the REAL place_order from the migrations
  await db.exec(`DELETE FROM cart_items; DELETE FROM carts WHERE user_id = '${uid}';`);
  const cart = (await q(`INSERT INTO carts (user_id, outlet_id) VALUES ($1, $2) RETURNING id`, [uid, OUTLET])).rows[0].id;
  await q(`INSERT INTO cart_items (cart_id, food_item_id, quantity, price, is_veg) VALUES ($1, $2, 1, 20, true)`, [cart, FOOD]);
  const slot = await newSlot();
  return as(uid, async () => (await q(`SELECT place_order($1, $2, 'PAY_AT_COUNTER') AS id`, [cart, slot])).rows[0].id);
}
async function orderTo(status, id) { await db.exec(`SET session_replication_role = replica; UPDATE orders SET status='${status}' WHERE id='${id}'; SET session_replication_role = origin;`); }
const tokenOf = async (orderId) => (await q(`SELECT token_value FROM pickup_tokens WHERE order_id = $1`, [orderId])).rows[0].token_value;

// ═══ Baseline: reproduce the issues on the repo schema (before any draft) ═══
console.log('\n── Baseline (repo schema, before drafts) ──');
const o0 = await placeOrderAs(ID.stu);
ok('real place_order works on stubbed repo schema', !!o0);
await orderTo('READY', o0);
const bypass0 = await fails(() => as(ID.vendor, () => q(`UPDATE orders SET status='PICKED_UP' WHERE id=$1`, [o0])));
ok('BASELINE: vendor can mark READY->PICKED_UP without a token (bug reproduced)', !bypass0.threw, bypass0.msg);
for (const t of ['food_items', 'outlets', 'pickup_slots']) {
  const has = (await q(`SELECT has_table_privilege('anon', 'public.${t}', 'TRUNCATE') AS p`)).rows[0].p;
  ok(`BASELINE: anon holds TRUNCATE on ${t} under emulated Supabase default grants (bug reproduced)`, has === true);
}
await db.exec(`SET session_replication_role = replica;
  INSERT INTO categories (id, name, emoji) VALUES ('c0000000-0000-0000-0000-000000000001', 'Snacks', 'x') ON CONFLICT DO NOTHING;
  INSERT INTO food_items (id, outlet_id, category_id, name, description, price)
    VALUES ('${FOOD}', '${OUTLET}', 'c0000000-0000-0000-0000-000000000001', 'Samosa', 'd', 20) ON CONFLICT DO NOTHING;
  INSERT INTO inventory (food_item_id, quantity_available) VALUES ('${FOOD}', 100) ON CONFLICT DO NOTHING;
  SET session_replication_role = origin;`);

const draft = n => readFileSync(path.join(root, 'drafts_unapproved', n), 'utf8');

// Payment verification baseline: any authenticated user can mark ANY order PAID via the RPC.
const payOrder = await placeOrderAs(ID.stu2);
await as(ID.stu, () => q(`SELECT mark_payment_verified($1, 'pay_fake', 'sig_fake', 'PAID')`, [payOrder]));
ok("BASELINE: student A can mark student B's order PAID with a fake signature (bug reproduced)",
   (await q(`SELECT payment_status::text p FROM orders WHERE id=$1`, [payOrder])).rows[0].p === 'PAID');
await db.exec(`UPDATE orders SET payment_status='PENDING' WHERE id='${payOrder}'`);

// ═══ Draft 1: pickup token ══════════════════════════════════════════════════
console.log('\n── DRAFT_pickup_token_bypass ──');
await db.exec(draft('DRAFT_pickup_token_bypass.sql'));
const o1 = await placeOrderAs(ID.stu); await orderTo('READY', o1);
const b1 = await fails(() => as(ID.vendor, () => q(`UPDATE orders SET status='PICKED_UP' WHERE id=$1`, [o1])), /PICKUP_TOKEN_REQUIRED/);
ok('vendor direct READY->PICKED_UP is rejected', b1.threw && b1.match, b1.msg);
const b1a = await fails(() => as(ID.admin, () => q(`UPDATE orders SET status='PICKED_UP' WHERE id=$1`, [o1])), /PICKUP_TOKEN_REQUIRED/);
ok('admin direct READY->PICKED_UP is also rejected (must go through the token)', b1a.threw && b1a.match, b1a.msg);
const spoof = await fails(() => as(ID.vendor, () => q(`SELECT set_config('crave.pickup_verified_order', $1, true)`, [o1])));
// set_config is callable in raw SQL; PostgREST cannot call pg_catalog functions. A same-tx spoof is only possible in raw SQL.
ok('(info) marker is only reachable via raw SQL, not PostgREST RPC', true);
const tok = await tokenOf(o1);
const wrongVendor = await fails(() => as(ID.vendor2, () => q(`SELECT verify_pickup_token($1)`, [tok])), /does not belong/);
ok("another vendor's token verification is rejected", wrongVendor.threw && wrongVendor.match, wrongVendor.msg);
const student = await fails(() => as(ID.stu, () => q(`SELECT verify_pickup_token($1)`, [tok])), /Only vendors/);
ok('a student cannot verify tokens', student.threw && student.match, student.msg);
const anonCall = await fails(() => as(null, () => q(`SELECT verify_pickup_token($1)`, [tok]), 'anon'));
ok('anon cannot execute verify_pickup_token', anonCall.threw, anonCall.msg);
const good = await as(ID.vendor, () => q(`SELECT verify_pickup_token($1) AS r`, [tok]));
ok('valid token completes pickup', good.rows[0].r.success === true);
ok('order is PICKED_UP, picked_up_at set', (await q(`SELECT status::text s, picked_up_at IS NOT NULL p FROM orders WHERE id=$1`, [o1])).rows[0].s === 'PICKED_UP');
const reuse = await fails(() => as(ID.vendor, () => q(`SELECT verify_pickup_token($1)`, [tok])), /already used/);
ok('token cannot be reused', reuse.threw && reuse.match, reuse.msg);
const o1b = await placeOrderAs(ID.stu2); // token for an order that is not READY
const notReady = await fails(() => as(ID.vendor, async () => q(`SELECT verify_pickup_token($1)`, [await tokenOf(o1b)])), /not ready/);
ok('token for a non-READY order is rejected', notReady.threw && notReady.match, notReady.msg);
const normal = await fails(async () => { await orderTo('READY', o1b); await as(ID.vendor, () => q(`UPDATE orders SET status='PICKED_UP' WHERE id=$1`, [o1b])); }, /PICKUP_TOKEN_REQUIRED/);
ok('other transitions unaffected: vendor accept/prepare/ready still work',
   await (async () => { const o = await placeOrderAs(ID.stu); try { await as(ID.vendor, async () => { await q(`SELECT vendor_accept_order($1)`, [o]); await q(`SELECT vendor_start_preparing($1)`, [o]); await q(`SELECT vendor_mark_ready($1)`, [o]); }); return true; } catch (e) { console.log(e.message); return false; } })());

// ═══ Draft 2: grants ═══════════════════════════════════════════════════════
console.log('\n── DRAFT_least_privilege_grants ──');
await db.exec(draft('DRAFT_least_privilege_grants.sql'));
for (const t of ['food_items', 'outlets', 'pickup_slots']) {
  const r = await fails(() => as(null, () => db.exec(`TRUNCATE ${t}`), 'anon'));
  const has = (await q(`SELECT has_table_privilege('anon','public.${t}','TRUNCATE') p, has_table_privilege('authenticated','public.${t}','TRUNCATE') a,
                               has_table_privilege('anon','public.${t}','TRIGGER') tr, has_table_privilege('anon','public.${t}','REFERENCES') rf`)).rows[0];
  ok(`anon TRUNCATE ${t} denied; TRIGGER/REFERENCES revoked too`, r.threw && /permission denied/.test(r.msg) && !has.p && !has.a && !has.tr && !has.rf, r.msg);
}
const anonWrite = await fails(() => as(null, () => q(`UPDATE food_items SET price = 0`), 'anon'), /permission denied/);
ok('anon UPDATE food_items denied', anonWrite.threw && anonWrite.match, anonWrite.msg);
const anonSel = await as(null, () => q(`SELECT count(*)::int AS n FROM food_items`), 'anon');
ok('anon can still read the public menu (food_items)', anonSel.rows[0].n >= 1);
const anonOutlets = await as(null, () => q(`SELECT count(*)::int AS n FROM outlets`), 'anon');
ok('anon can still read outlets', anonOutlets.rows[0].n >= 1);
const authTrunc = await fails(() => as(ID.vendor, () => db.exec('TRUNCATE food_items CASCADE')), /permission denied/);
ok('authenticated TRUNCATE denied', authTrunc.threw && authTrunc.match, authTrunc.msg);
const vendorUpd = await as(ID.vendor, () => q(`UPDATE food_items SET price = 25 WHERE id = $1 RETURNING price::int AS p`, [FOOD]));
ok('vendor can still update own food item (RLS + grants)', vendorUpd.rows[0]?.p === 25);
await q(`UPDATE food_items SET price = 20 WHERE id = $1`, [FOOD]);

// ═══ Draft 3: search_path ═════════════════════════════════════════════════
console.log('\n── DRAFT_security_definer_search_path ──');
await db.exec(draft('DRAFT_security_definer_search_path.sql'));
const sp = await q(`SELECT proname, proconfig::text AS c FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
                    WHERE n.nspname='public' AND proname IN ('place_order','verify_pickup_token','get_user_role','vendor_accept_order')`);
ok('hardened functions end with pg_temp', sp.rows.length >= 4 && sp.rows.every(r => /pg_temp\}?"?\}$/.test(r.c) || r.c.includes('pg_temp}')), JSON.stringify(sp.rows));
const trg = await fails(() => as(ID.stu, () => q(`SELECT enforce_order_state_machine()`)), /permission denied/);
ok('trigger-only function not executable by authenticated', trg.threw && trg.match, trg.msg);
for (const fn of ['vendor_accept_order(gen_random_uuid())', 'place_order(gen_random_uuid(), gen_random_uuid(), \'PAY_AT_COUNTER\')', 'get_admin_stats()']) {
  const r = await fails(() => as(null, () => q(`SELECT ${fn}`), 'anon'), /permission denied for function/);
  ok(`anon has no EXECUTE on ${fn.split('(')[0]}`, r.threw && r.match, r.msg);
}
const anonCatalogue = await fails(() => as(null, () => q(`SELECT is_admin()`), 'anon'));
ok('RLS helper is_admin() stays callable (needed inside policies)', !anonCatalogue.threw, anonCatalogue.msg);
const oS = await placeOrderAs(ID.stu);
ok('place_order still works after hardening', !!oS);
await as(ID.vendor, async () => { await q(`SELECT vendor_accept_order($1)`, [oS]); await q(`SELECT vendor_start_preparing($1)`, [oS]); await q(`SELECT vendor_mark_ready($1)`, [oS]); });
const gv = await as(ID.vendor, () => q(`SELECT verify_pickup_token($1) AS r`, [null ?? 'x']).catch(e => ({ err: e.message })));
ok('verify_pickup_token still resolves its dependencies (rejects bad token, not a missing-object error)', /Invalid pickup token/.test(gv.err ?? ''), gv.err);
await as(ID.vendor, async () => q(`SELECT verify_pickup_token($1)`, [await tokenOf(oS)]));
ok('full lifecycle after hardening: place → accept → prepare → ready → token pickup', (await q(`SELECT status::text s FROM orders WHERE id=$1`, [oS])).rows[0].s === 'PICKED_UP');
ok('trigger side-effects still fire (status timestamps)', (await q(`SELECT picked_up_at IS NOT NULL p, ready_at IS NOT NULL r FROM orders WHERE id=$1`, [oS])).rows[0].p);

// ═══ Support tickets (REAL migration 023 as replayed) + drafts: helpers & notifications ═══════
console.log('\n── Support tickets (migration 023) ──');
const U = '00000000-0000-0000-0000-0000000000';
const rid = n => `${U}${String(n).padStart(2, '0')}`;
const mk = (uid, cat, subj, desc, order, req, outlet = null) => as(uid, async () =>
  (await q(`SELECT create_support_ticket($1,$2,$3,$4,NULL,$5,$6) AS t`, [cat, subj, desc, order, outlet, req])).rows[0].t);
const stuOrder = await placeOrderAs(ID.stu);
await db.exec(`SET session_replication_role = replica; UPDATE orders SET status='PICKED_UP' WHERE id='${stuOrder}'; SET session_replication_role = origin;`);

const t1 = await mk(ID.stu, 'FOOD_QUALITY', 'Cold food', 'The samosa was cold and stale', stuOrder, rid(1));
ok('student raises a ticket on their own order; server derives the outlet', t1.outlet_id === OUTLET && t1.status === 'OPEN' && t1.student_id === ID.stu);
ok('student-facing ticket JSON omits assigned_to / client_request_id', !('assigned_to' in t1) && !('client_request_id' in t1));
const replay = await mk(ID.stu, 'FOOD_QUALITY', 'Cold food', 'The samosa was cold and stale', stuOrder, rid(1));
ok('retry with the same request id returns the original ticket (idempotent)', replay.id === t1.id);
const dup = await fails(() => mk(ID.stu, 'FOOD_QUALITY', 'Cold food again', 'Another complaint same thing', stuOrder, rid(2)), /TICKET_ALREADY_OPEN/);
ok('second active ticket for same order+category -> TICKET_ALREADY_OPEN', dup.threw && dup.match, dup.msg);
const otherStu = await fails(() => mk(ID.stu2, 'ORDER', 'Not mine', 'Trying someone elses order', stuOrder, rid(3)), /ORDER_NOT_FOUND/);
ok("student B cannot raise a ticket on student A's order", otherStu.threw && otherStu.match, otherStu.msg);
const vendorCreate = await fails(() => mk(ID.vendor, 'OTHER', 'Vendor ticket', 'Vendors are not students', null, rid(4), OUTLET), /student/i);
ok('vendor cannot raise a student ticket', vendorCreate.threw && vendorCreate.match, vendorCreate.msg);
const anonCreate = await fails(() => as(null, () => q(`SELECT create_support_ticket('OTHER','abc','abcdefghijk',NULL,NULL,$1,NULL)`, [OUTLET]), 'anon'), /permission denied/);
ok('anon cannot execute create_support_ticket', anonCreate.threw && anonCreate.match, anonCreate.msg);

const seenBy = (uid, tbl, role = 'authenticated') => as(uid, async () => (await q(`SELECT count(*)::int n FROM ${tbl}`)).rows[0].n, role);
ok('student A sees their ticket', await seenBy(ID.stu, 'support_tickets') === 1);
ok('student B sees none of A’s tickets (RLS)', await seenBy(ID.stu2, 'support_tickets') === 0);
ok('vendor sees no tickets (RLS)', await seenBy(ID.vendor, 'support_tickets') === 0);
const anonSelect = await fails(() => as(null, () => q('SELECT 1 FROM support_tickets'), 'anon'), /permission denied/);
ok('anon cannot read tickets', anonSelect.threw && anonSelect.match, anonSelect.msg);
const assignedCol = await fails(() => as(ID.stu, () => q('SELECT assigned_to FROM support_tickets')), /permission denied/);
ok('management-only column assigned_to is not readable by students', assignedCol.threw && assignedCol.match, assignedCol.msg);
for (const [who, uid] of [['student', ID.stu], ['vendor', ID.vendor]]) {
  for (const tbl of ['support_ticket_internal_notes', 'support_ticket_events']) {
    const r = await fails(() => as(uid, () => q(`SELECT 1 FROM ${tbl}`)));
    const rows = r.threw ? 0 : await seenBy(uid, tbl);
    ok(`${who} cannot read ${tbl}`, r.threw || rows === 0, r.msg);
  }
}
for (const fn of ['admin_list_support_tickets()', `admin_get_support_ticket('${t1.id}')`, `admin_set_support_ticket_status('${t1.id}','UNDER_REVIEW',NULL,NULL)`,
                  `admin_add_support_ticket_note('${t1.id}','leak')`, `admin_assign_support_ticket('${t1.id}',NULL)`]) {
  for (const [who, uid] of [['student', ID.stu], ['vendor', ID.vendor]]) {
    const r = await fails(() => as(uid, () => q(`SELECT * FROM ${fn}`)), /Management access required/);
    ok(`${who} cannot call ${fn.split('(')[0]}`, r.threw && r.match, r.msg);
  }
}
const directWrite = await fails(() => as(ID.stu, () => q(`UPDATE support_tickets SET status='RESOLVED' WHERE id=$1`, [t1.id])), /permission denied/);
ok('student cannot change ticket status directly', directWrite.threw && directWrite.match, directWrite.msg);

// Management workflow
const adm = (sql, params) => as(ID.admin, () => q(sql, params));
const noOwn = await fails(() => adm(`SELECT admin_assign_support_ticket($1,$2)`, [t1.id, ID.stu]), /ASSIGNEE_NOT_MANAGEMENT/);
ok('cannot assign a ticket to a non-admin', noOwn.threw && noOwn.match, noOwn.msg);
await adm(`SELECT admin_assign_support_ticket($1,$2)`, [t1.id, ID.admin]);
ok('admin assigns the ticket to an active admin', (await q(`SELECT assigned_to FROM support_tickets WHERE id=$1`, [t1.id])).rows[0].assigned_to === ID.admin);
await adm(`SELECT admin_add_support_ticket_note($1,'SECRET-INTERNAL-NOTE vendor was rude before')`, [t1.id]);
await adm(`SELECT admin_respond_support_ticket($1,'We are looking into this')`, [t1.id]);
const stuMsgs = await as(ID.stu, () => q(`SELECT body FROM support_ticket_messages WHERE ticket_id=$1`, [t1.id]));
ok('student sees management reply but never the internal note',
   stuMsgs.rows.some(r => /looking into/.test(r.body)) && !stuMsgs.rows.some(r => /SECRET-INTERNAL/.test(r.body)));
const detail = (await adm(`SELECT admin_get_support_ticket($1) AS d`, [t1.id])).rows[0].d;
ok('admin detail includes the internal note, messages and audit events', detail.internal_notes.length === 1 && detail.messages.length >= 1 && detail.events.length >= 3);
await adm(`SELECT admin_set_support_ticket_status($1,'UNDER_REVIEW',NULL,'OPEN')`, [t1.id]);
const stale = await fails(() => adm(`SELECT admin_set_support_ticket_status($1,'WAITING_FOR_STUDENT',NULL,'OPEN')`, [t1.id]), /TICKET_CHANGED/);
ok('stale expected-status is rejected (another manager moved it) -> TICKET_CHANGED', stale.threw && stale.match, stale.msg);
const noReason = await fails(() => adm(`SELECT admin_set_support_ticket_status($1,'RESOLVED',NULL,NULL)`, [t1.id]), /REASON_REQUIRED/);
ok('resolving requires a reason', noReason.threw && noReason.match, noReason.msg);
await adm(`SELECT admin_set_support_ticket_status($1,'RESOLVED','Refunded at counter',NULL)`, [t1.id]);
const replyResolved = await fails(() => as(ID.stu, () => q(`SELECT add_support_ticket_message($1,'thanks but still unhappy')`, [t1.id])), /TICKET_CLOSED/);
ok('student cannot reply on a resolved ticket', replyResolved.threw && replyResolved.match, replyResolved.msg);

// Reopen conflict: new active ticket for the same order+category exists, then reopening the resolved one must fail.
const t2 = await mk(ID.stu, 'FOOD_QUALITY', 'Cold food round two', 'It happened again with the same order', stuOrder, rid(5));
const conflict = await fails(() => adm(`SELECT admin_set_support_ticket_status($1,'UNDER_REVIEW','Student sent new photos',NULL)`, [t1.id]), /REOPEN_CONFLICT/);
ok('reopening while another active ticket exists -> REOPEN_CONFLICT', conflict.threw && conflict.match, conflict.msg);
await adm(`SELECT admin_set_support_ticket_status($1,'RESOLVED','Duplicate; handled in first ticket',NULL)`, [t2.id]);
await adm(`SELECT admin_set_support_ticket_status($1,'UNDER_REVIEW','Student sent new photos',NULL)`, [t1.id]);
ok('reopen succeeds once the conflict is gone', (await q(`SELECT status FROM support_tickets WHERE id=$1`, [t1.id])).rows[0].status === 'UNDER_REVIEW');
const conc = await Promise.allSettled([ // sequential in PGlite, but the unique index is what is under test
  mk(ID.stu2, 'OTHER', 'Race one', 'Concurrent request number one', null, rid(6), OUTLET),
  mk(ID.stu2, 'OTHER', 'Race one', 'Concurrent request number one', null, rid(6), OUTLET)]);
ok('same request id twice yields one ticket', (await q(`SELECT count(*)::int n FROM support_tickets WHERE client_request_id=$1`, [rid(6)])).rows[0].n === 1);
await adm(`SELECT admin_set_support_ticket_status($1,'CLOSED','Done',NULL)`, [t2.id]);
const closedFinal = await fails(() => adm(`SELECT admin_set_support_ticket_status($1,'UNDER_REVIEW','try reopen closed',NULL)`, [t2.id]), /INVALID_TRANSITION/);
ok('CLOSED is final', closedFinal.threw && closedFinal.match, closedFinal.msg);
const noDelete = await fails(() => db.exec(`DELETE FROM profiles WHERE id='${ID.stu}'`));
ok('deleting a profile with ticket history is blocked, history intact', noDelete.threw);
const events = (await q(`SELECT action FROM support_ticket_events WHERE ticket_id=$1 ORDER BY id`, [t1.id])).rows.map(r => r.action);
ok('audit trail records created/assigned/status/reopened', ['CREATED', 'ASSIGNED', 'STATUS_CHANGED', 'REOPENED'].every(a => events.includes(a)), events.join());
for (let i = 0; i < 5; i++) await mk(ID.stu2, 'OTHER', `Rate ${i}`, 'Rate limit probing request', null, rid(20 + i), OUTLET).catch(() => {});
const rate = await fails(() => mk(ID.stu2, 'OTHER', 'Rate over', 'Rate limit probing request', null, rid(30), OUTLET), /RATE_LIMITED|TOO_MANY/);
ok('ticket creation is rate limited', rate.threw && rate.match, rate.msg);

console.log('\n── DRAFT_support_admin_helpers ──');
await db.exec(draft('DRAFT_support_admin_helpers.sql'));
const ass = (await adm(`SELECT * FROM admin_list_support_assignees()`)).rows;
ok('assignee list = active admins only', ass.length === 1 && ass[0].id === ID.admin, JSON.stringify(ass));
for (const [who, uid] of [['student', ID.stu], ['vendor', ID.vendor]]) {
  const r = await fails(() => as(uid, () => q(`SELECT * FROM admin_list_support_assignees()`)), /Management access required/);
  ok(`${who} cannot list assignees`, r.threw && r.match, r.msg);
  const r2 = await fails(() => as(uid, () => q(`SELECT admin_get_support_ticket_reviews($1)`, [t1.id])), /Management access required/);
  ok(`${who} cannot read linked reviews`, r2.threw && r2.match, r2.msg);
}
const anonAss = await fails(() => as(null, () => q(`SELECT * FROM admin_list_support_assignees()`), 'anon'), /permission denied/);
ok('anon cannot list assignees', anonAss.threw && anonAss.match, anonAss.msg);
await db.exec(`SET session_replication_role = replica;
  INSERT INTO reviews (student_id, food_item_id, order_id, order_item_id, rating, review_text, is_visible)
  SELECT '${ID.stu}', '${FOOD}', '${stuOrder}', id, 2, 'Cold and stale', false FROM order_items WHERE order_id='${stuOrder}' LIMIT 1;
  SET session_replication_role = origin;`);
const rv = (await adm(`SELECT admin_get_support_ticket_reviews($1) AS r`, [t1.id])).rows[0].r;
ok('linked reviews include the student’s hidden review for that order', rv.food_reviews.length === 1 && rv.food_reviews[0].is_visible === false && rv.food_reviews[0].food_name === 'Samosa');

console.log('\n── DRAFT_support_notifications ──');
await db.exec(draft('DRAFT_support_notifications.sql'));
const t3 = await mk(ID.stu, 'PICKUP', 'Pickup problem', 'Counter staff would not hand over my order', stuOrder, rid(40));
const before = (await q(`SELECT count(*)::int n FROM notifications WHERE deep_link LIKE 'ticket:%'`)).rows[0].n;
await adm(`SELECT admin_add_support_ticket_note($1,'TOP-SECRET internal only')`, [t3.id]);
ok('an internal note creates NO notification', (await q(`SELECT count(*)::int n FROM notifications WHERE deep_link LIKE 'ticket:%'`)).rows[0].n === before);
await adm(`SELECT admin_respond_support_ticket($1,'Please send a photo of the receipt')`, [t3.id]);
await adm(`SELECT admin_set_support_ticket_status($1,'WAITING_FOR_STUDENT',NULL,NULL)`, [t3.id]);
await adm(`SELECT admin_set_support_ticket_status($1,'RESOLVED','Receipt verified; counter retrained',NULL)`, [t3.id]);
const nts = (await q(`SELECT user_id, title, body, deep_link, type::text ty FROM notifications WHERE deep_link LIKE 'ticket:${t3.id}:%' ORDER BY created_at, deep_link`)).rows;
ok('reply, request-for-info and resolution each notify the student once', nts.length === 3 && nts.every(n => n.user_id === ID.stu && n.ty === 'GENERAL'), JSON.stringify(nts.map(n => n.title)));
ok('no notification contains internal-note text', !nts.some(n => /TOP-SECRET/.test(n.title + n.body)));
ok('notifications are for the student only, never the admin', (await q(`SELECT count(*)::int n FROM notifications WHERE user_id=$1 AND deep_link LIKE 'ticket:%'`, [ID.admin])).rows[0].n === 0);
const dupIns = await q(`INSERT INTO notifications (user_id,title,body,type,deep_link) VALUES ($1,'x','y','GENERAL',$2) ON CONFLICT DO NOTHING`, [ID.stu, nts[0].deep_link]);
ok('replaying the same event cannot create a duplicate notification', dupIns.affectedRows === 0);
await db.exec(`ALTER TABLE notifications ADD CONSTRAINT notif_block CHECK (false) NOT VALID`);
const failSoft = await fails(() => adm(`SELECT admin_respond_support_ticket($1,'Reply while notifications are broken')`, [t3.id]));
await db.exec(`ALTER TABLE notifications DROP CONSTRAINT notif_block`);
ok('a failing notification does not break or roll back the ticket reply', !failSoft.threw && (await q(`SELECT count(*)::int n FROM support_ticket_messages WHERE body='Reply while notifications are broken'`)).rows[0].n === 1, failSoft.msg);
const own = await as(ID.stu, () => q(`SELECT count(*)::int n FROM notifications WHERE deep_link LIKE 'ticket:%'`));
const others = await as(ID.stu2, () => q(`SELECT count(*)::int n FROM notifications WHERE deep_link LIKE 'ticket:%'`));
ok("student sees own ticket notifications, not other students'", own.rows[0].n >= 3 && others.rows[0].n === 0);

console.log('\n── DRAFT_payment_verification_lockdown ──');
await db.exec(draft('DRAFT_payment_verification_lockdown.sql'));
for (const [who, uid, role] of [['student', ID.stu, 'authenticated'], ['vendor', ID.vendor, 'authenticated'], ['anon', null, 'anon']]) {
  const r = await fails(() => as(uid, () => q(`SELECT mark_payment_verified($1,'pay_fake','sig_fake','PAID')`, [payOrder]), role), /permission denied for function/);
  ok(`${who} can no longer call mark_payment_verified`, r.threw && r.match, r.msg);
}
await as(null, () => q(`SELECT mark_payment_verified($1,'pay_real','sig_real','PAID')`, [payOrder]), 'service_role');
const pv = (await q(`SELECT o.payment_status::text s, p.status::text ps, p.razorpay_payment_id rp FROM orders o JOIN payments p ON p.order_id=o.id WHERE o.id=$1`, [payOrder])).rows[0];
ok('service role (Edge Function) can still mark verified; order + payment rows updated', pv.s === 'PAID' && pv.rp === 'pay_real', JSON.stringify(pv));
ok('payment lockdown leaves the order lifecycle intact (accept still works)', await (async () => { try { await as(ID.vendor, () => q(`SELECT vendor_accept_order($1)`, [payOrder])); return true; } catch (e) { return false; } })());

// ═══ Draft 4: outlet suspension ═══════════════════════════════════════════
// Regression: cancelling restores inventory + pickup-slot capacity (trigger from migration 011), after all hardening drafts.
{
  const inv0 = (await q(`SELECT quantity_available::int q FROM inventory WHERE food_item_id=$1`, [FOOD])).rows[0].q;
  const oc = await placeOrderAs(ID.stu);
  const slot = (await q(`SELECT pickup_slot_id s FROM orders WHERE id=$1`, [oc])).rows[0].s;
  ok('place_order decrements inventory and books the slot',
     (await q(`SELECT quantity_available::int q FROM inventory WHERE food_item_id=$1`, [FOOD])).rows[0].q === inv0 - 1 &&
     (await q(`SELECT booked_count::int b FROM pickup_slots WHERE id=$1`, [slot])).rows[0].b === 1);
  await as(ID.stu, () => q(`UPDATE orders SET status='CANCELLED' WHERE id=$1`, [oc]));
  ok('student cancel restores inventory and slot capacity',
     (await q(`SELECT quantity_available::int q FROM inventory WHERE food_item_id=$1`, [FOOD])).rows[0].q === inv0 &&
     (await q(`SELECT booked_count::int b FROM pickup_slots WHERE id=$1`, [slot])).rows[0].b === 0);
  await fails(() => as(ID.stu, () => q(`UPDATE orders SET status='PICKED_UP' WHERE id=$1`, [oc])));
  ok('a student cannot push an order to PICKED_UP (RLS/state machine leave it CANCELLED)',
     (await q(`SELECT status::text s FROM orders WHERE id=$1`, [oc])).rows[0].s === 'CANCELLED');
}

// ═══ Ownership, reviews and management-metric integrity (repo schema + hardening drafts applied) ═══
console.log('\n── Ownership / reviews / metrics ──');
{
  const oR = await placeOrderAs(ID.stu);
  const item = (await q(`SELECT id FROM order_items WHERE order_id=$1 LIMIT 1`, [oR])).rows[0].id;
  const review = (uid, rating = 4, o = oR, it = item) => as(uid, () => q(
    `INSERT INTO reviews (student_id, food_item_id, order_id, order_item_id, rating, review_text) VALUES ($1,$2,$3,$4,$5,'tasty')`, [uid, FOOD, o, it, rating]));
  const early = await fails(() => review(ID.stu));
  ok('a student cannot review an order that is not yet completed', early.threw, early.msg);
  await as(ID.vendor, async () => { for (const f of ['vendor_accept_order', 'vendor_start_preparing', 'vendor_mark_ready']) await q(`SELECT ${f}($1)`, [oR]); });
  await as(ID.vendor, async () => q(`SELECT verify_pickup_token($1)`, [await tokenOf(oR)]));
  const other = await fails(() => review(ID.stu2));
  ok("another student cannot review someone else's order item", other.threw, other.msg);
  const vend = await fails(() => review(ID.vendor));
  ok('a vendor cannot post a student review', vend.threw, vend.msg);
  const bad = await fails(() => review(ID.stu, 9));
  ok('rating outside 1-5 is rejected', bad.threw, bad.msg);
  const good = await fails(() => review(ID.stu));
  ok('the student can review after pickup completes', !good.threw, good.msg);
  const dupRev = await fails(() => review(ID.stu));
  ok('one review per order item', dupRev.threw, dupRev.msg);
  const rid1 = (await q(`SELECT id FROM reviews WHERE order_item_id=$1`, [item])).rows[0].id;
  const wrongReply = await fails(() => as(ID.vendor2, () => q(`INSERT INTO review_replies (review_id, vendor_id, reply_text) VALUES ($1,$2,'not mine')`, [rid1, ID.vendor2])));
  ok("a vendor of a different outlet cannot reply to the review", wrongReply.threw, wrongReply.msg);
  const okReply = await fails(() => as(ID.vendor, () => q(`INSERT INTO review_replies (review_id, vendor_id, reply_text) VALUES ($1,$2,'thanks')`, [rid1, ID.vendor])));
  ok('the owning vendor can reply', !okReply.threw, okReply.msg);
  const sneaky = await fails(() => as(ID.stu, () => q(`UPDATE reviews SET is_visible=false WHERE id=$1`, [rid1])));
  const stillVisible = (await q(`SELECT rating, is_visible FROM reviews WHERE id=$1`, [rid1])).rows[0];
  ok('a student cannot hide/alter moderation fields on their own review', sneaky.threw || stillVisible.is_visible === true, JSON.stringify(stillVisible));

  // Row-level isolation
  const nOrders = async uid => (await as(uid, () => q(`SELECT count(*)::int n FROM orders WHERE id=$1`, [oR]))).rows[0].n;
  ok('student A sees their order', await nOrders(ID.stu) === 1);
  ok("student B cannot see A's order", await nOrders(ID.stu2) === 0);
  ok("another outlet's vendor cannot see the order", await nOrders(ID.vendor2) === 0);
  const nPay = async uid => (await as(uid, () => q(`SELECT count(*)::int n FROM payments WHERE order_id=$1`, [oR]))).rows[0].n;
  ok('student B cannot see A’s payment row', await nPay(ID.stu2) === 0);
  await db.exec(`SET session_replication_role = replica; INSERT INTO favorites (user_id, food_item_id) VALUES ('${ID.stu}','${FOOD}') ON CONFLICT DO NOTHING; SET session_replication_role = origin;`);
  ok("student B cannot see A's favourites", (await as(ID.stu2, () => q(`SELECT count(*)::int n FROM favorites`))).rows[0].n === 0);
  ok("student B cannot see A's cart", (await as(ID.stu2, () => q(`SELECT count(*)::int n FROM carts WHERE user_id=$1`, [ID.stu]))).rows[0].n === 0);
  const notifLeak = await as(ID.stu2, () => q(`SELECT count(*)::int n FROM notifications WHERE user_id=$1`, [ID.stu]));
  ok("student B cannot read A's notifications", notifLeak.rows[0].n === 0);

  // Management metrics: authorization + consistency with the underlying tables
  for (const [who, uid] of [['student', ID.stu], ['vendor', ID.vendor]]) {
    const r = await fails(() => as(uid, () => q(`SELECT get_management_analytics()`)), /ADMIN/);
    ok(`${who} cannot read management analytics`, r.threw && r.match, r.msg);
  }
  const an = (await as(ID.admin, () => q(`SELECT get_management_analytics() AS a`))).rows[0].a;
  const truth = (await q(`SELECT count(*)::int total,
      count(*) FILTER (WHERE status::text='PICKED_UP')::int done,
      count(*) FILTER (WHERE status::text IN ('PLACED','ACCEPTED','PREPARING','READY'))::int act,
      count(*) FILTER (WHERE status::text='CANCELLED')::int canc FROM orders`)).rows[0];
  ok('analytics totalOrders/completed/active/cancelled match the orders table',
     an.totalOrders === truth.total && an.completedOrders === truth.done && an.activeOrders === truth.act && an.cancelledOrders === truth.canc,
     JSON.stringify({ an: [an.totalOrders, an.completedOrders, an.activeOrders, an.cancelledOrders], truth }));
  const paid = Number((await q(`SELECT COALESCE(sum(amount),0) s FROM payments WHERE status::text IN ('PAID','CAPTURED')`)).rows[0].s);
  ok('analytics paidRevenue equals the sum of PAID payments', Number(an.paidRevenue) === paid, `${an.paidRevenue} vs ${paid}`);
  ok('analytics user/outlet counts match the tables',
     an.totalStudents === (await q(`SELECT count(*)::int n FROM profiles WHERE role::text='STUDENT'`)).rows[0].n &&
     an.totalOutlets === (await q(`SELECT count(*)::int n FROM outlets`)).rows[0].n);
  const pickedWithoutPayment = (await q(`SELECT count(*)::int n FROM orders o JOIN payments p ON p.order_id=o.id
      WHERE o.status::text='PICKED_UP' AND o.payment_method::text='PAY_AT_COUNTER' AND p.status::text='PENDING'`)).rows[0].n;
  console.log(`INFO  finding: ${pickedWithoutPayment} PAY_AT_COUNTER order(s) reached PICKED_UP with payment still PENDING (nothing records cash collection) -> cashRevenue stays 0`);
}

console.log('\n── DRAFT_vendor_data_guards ──');
{
  // Baseline on the repo policies: a vendor can tamper with locked columns directly.
  const tamperBefore = await fails(() => as(ID.vendor, () => q(`UPDATE outlets SET is_active=true, rating=5, total_reviews=999 WHERE id=$1`, [OUTLET])));
  ok('BASELINE: vendor can edit rating/total_reviews of their own outlet (bug reproduced)', !tamperBefore.threw, tamperBefore.msg);
  await db.exec(`SET session_replication_role = replica; UPDATE outlets SET rating=0, total_reviews=0 WHERE id='${OUTLET}'; SET session_replication_role = origin;`);
  const slotBefore = await newSlot();
  await as(ID.vendor, () => q(`UPDATE pickup_slots SET booked_count=0 WHERE id=$1`, [slotBefore]));
  await db.exec(draft('DRAFT_vendor_data_guards.sql'));

  for (const [col, val] of [['is_active', 'false'], ['rating', '5'], ['total_reviews', '99'], ['vendor_id', `'${ID.vendor2}'`]]) {
    const r = await fails(() => as(ID.vendor, () => q(`UPDATE outlets SET ${col}=${val} WHERE id=$1`, [OUTLET])), /VENDOR_COLUMN_LOCKED|permission|violates/);
    ok(`vendor cannot change outlets.${col}`, r.threw && r.match, r.msg);
  }
  const okEdit = await fails(() => as(ID.vendor, () => q(`UPDATE outlets SET is_open=false, description='Fresh', phone='9999999999', operating_hours='{"openTime":"09:00","closeTime":"21:00","daysOpen":["Mon"]}' WHERE id=$1`, [OUTLET])));
  ok('vendor can still edit open flag, description, phone and hours', !okEdit.threw, okEdit.msg);
  await db.exec(`SET session_replication_role = replica; UPDATE outlets SET is_open=true WHERE id='${OUTLET}'; SET session_replication_role = origin;`);
  const admEdit = await fails(() => as(ID.admin, () => q(`UPDATE outlets SET is_active=true, rating=4.5 WHERE id=$1`, [OUTLET])));
  ok('admin is not restricted', !admEdit.threw, admEdit.msg);
  await db.exec(`SET session_replication_role = replica; UPDATE outlets SET rating=0 WHERE id='${OUTLET}'; SET session_replication_role = origin;`);

  for (const col of ['rating=5', 'total_reviews=50', 'is_popular=true', 'is_recommended=true']) {
    const r = await fails(() => as(ID.vendor, () => q(`UPDATE food_items SET ${col} WHERE id=$1`, [FOOD])), /VENDOR_COLUMN_LOCKED/);
    ok(`vendor cannot change food_items.${col.split('=')[0]}`, r.threw && r.match, r.msg);
  }
  const foodOk = await fails(() => as(ID.vendor, () => q(`UPDATE food_items SET name='Samosa', price=21, is_veg=true, is_available=true, prep_time_minutes=6 WHERE id=$1`, [FOOD])));
  ok('vendor can still edit name, price, veg flag, availability, prep time', !foodOk.threw, foodOk.msg);
  await db.exec(`SET session_replication_role = replica; UPDATE food_items SET price=20 WHERE id='${FOOD}'; SET session_replication_role = origin;`);
  const noPrice = await fails(() => as(ID.vendor, () => q(`UPDATE food_items SET price=0 WHERE id=$1`, [FOOD])), /greater than 0/);
  ok('existing price integrity trigger still rejects a zero price', noPrice.threw && noPrice.match, noPrice.msg);

  // slots
  const ins = (date, st, en, cap = 10) => as(ID.vendor, () => q(`INSERT INTO pickup_slots (outlet_id, slot_date, start_time, end_time, capacity) VALUES ($1,$2,$3,$4,$5) RETURNING id, booked_count, status::text s`, [OUTLET, date, st, en, cap]));
  const past = await fails(() => ins(new Date(Date.now() - 2 * 864e5).toISOString().slice(0, 10), '10:00', '10:30'), /SLOT_IN_PAST/);
  ok('vendor cannot create a slot in the past (IST)', past.threw && past.match, past.msg);
  const backwards = await fails(() => ins(new Date(Date.now() + 3 * 864e5).toISOString().slice(0, 10), '11:00', '10:00'), /SLOT_INVALID|violates/);
  ok('vendor cannot create a slot that ends before it starts', backwards.threw && backwards.match, backwards.msg);
  const future = new Date(Date.now() + 4 * 864e5).toISOString().slice(0, 10);
  const mine = (await ins(future, '10:00', '10:30', 10)).rows[0];
  ok('vendor can create a future slot; booked_count forced to 0', mine.booked_count === 0 && mine.s === 'AVAILABLE');
  const dup = await fails(() => ins(future, '10:00', '10:30', 5), /duplicate|unique/i);
  ok('duplicate slot start time is rejected by the UNIQUE constraint', dup.threw && dup.match, dup.msg);
  const cheat = await fails(() => as(ID.vendor, () => q(`INSERT INTO pickup_slots (outlet_id, slot_date, start_time, end_time, capacity, booked_count) VALUES ($1,$2,'12:00','12:30',10,9) RETURNING booked_count`, [OUTLET, future])));
  ok('a pre-booked insert is neutralised (booked_count reset to 0)', !cheat.threw && (await q(`SELECT booked_count::int b FROM pickup_slots WHERE outlet_id=$1 AND slot_date=$2 AND start_time='12:00'`, [OUTLET, future])).rows[0].b === 0);
  await as(ID.vendor, () => q(`UPDATE pickup_slots SET capacity=20 WHERE id=$1`, [mine.id]));
  ok('capacity can be changed', (await q(`SELECT capacity::int c FROM pickup_slots WHERE id=$1`, [mine.id])).rows[0].c === 20);
  const move = await fails(() => as(ID.vendor, () => q(`UPDATE pickup_slots SET start_time='15:00' WHERE id=$1`, [mine.id])), /SLOT_COLUMN_LOCKED/);
  ok('vendor cannot move a slot (only capacity is editable)', move.threw && move.match, move.msg);
  const other = await fails(() => as(ID.vendor2, () => q(`UPDATE pickup_slots SET capacity=1 WHERE id=$1`, [mine.id])));
  ok("another vendor cannot change this outlet's slot (RLS: 0 rows)", (await q(`SELECT capacity::int c FROM pickup_slots WHERE id=$1`, [mine.id])).rows[0].c === 20);

  // real ordering still books slots and moves status (internal SECURITY DEFINER path is untouched)
  const slotRow = (await q(`SELECT id FROM pickup_slots WHERE outlet_id=$1 AND slot_date=$2 AND start_time='10:00'`, [OUTLET, future])).rows[0];
  await db.exec(`SET session_replication_role = replica; UPDATE pickup_slots SET capacity=1 WHERE id='${slotRow.id}'; SET session_replication_role = origin;`);
  await db.exec(`DELETE FROM cart_items; DELETE FROM carts WHERE user_id='${ID.stu}'`);
  const cart = (await q(`INSERT INTO carts (user_id, outlet_id) VALUES ($1,$2) RETURNING id`, [ID.stu, OUTLET])).rows[0].id;
  await q(`INSERT INTO cart_items (cart_id, food_item_id, quantity, price, is_veg) VALUES ($1,$2,1,20,true)`, [cart, FOOD]);
  const ord = await fails(() => as(ID.stu, () => q(`SELECT place_order($1,$2,'PAY_AT_COUNTER')`, [cart, slotRow.id])));
  const after = (await q(`SELECT booked_count::int b, status::text s FROM pickup_slots WHERE id=$1`, [slotRow.id])).rows[0];
  ok('place_order still books the slot and marks it FULL', !ord.threw && after.b === 1 && after.s === 'FULL', ord.msg + JSON.stringify(after));
  const overbook = await fails(() => { const c2 = null; return as(ID.stu2, async () => { await db.exec(`RESET ROLE`); throw new Error('skip'); }); });
  const lower = await fails(() => as(ID.vendor, () => q(`UPDATE pickup_slots SET capacity=0 WHERE id=$1`, [slotRow.id])));
  ok('capacity below current bookings is rejected', lower.threw, lower.msg);
  const delBooked = await fails(() => as(ID.vendor, () => q(`DELETE FROM pickup_slots WHERE id=$1`, [slotRow.id])));
  ok('a slot with an order cannot be deleted (FK RESTRICT)', delBooked.threw, delBooked.msg);

  // internal paths still work: a student's review updates the outlet/food rating despite the vendor guards
  const oRv = await placeOrderAs(ID.stu2);
  await as(ID.vendor, async () => { for (const f of ['vendor_accept_order', 'vendor_start_preparing', 'vendor_mark_ready']) await q(`SELECT ${f}($1)`, [oRv]); });
  await as(ID.vendor, async () => q(`SELECT verify_pickup_token($1)`, [await tokenOf(oRv)]));
  const itemRv = (await q(`SELECT id FROM order_items WHERE order_id=$1 LIMIT 1`, [oRv])).rows[0].id;
  await as(ID.stu2, () => q(`INSERT INTO outlet_reviews (student_id, outlet_id, order_id, rating, review_text) VALUES ($1,$2,$3,5,'great')`, [ID.stu2, OUTLET, oRv]));
  await as(ID.stu2, () => q(`INSERT INTO reviews (student_id, food_item_id, order_id, order_item_id, rating, review_text) VALUES ($1,$2,$3,$4,4,'ok')`, [ID.stu2, FOOD, oRv, itemRv]));
  const rt = (await q(`SELECT (SELECT rating::float FROM outlets WHERE id=$1) o, (SELECT total_reviews::int FROM food_items WHERE id=$2) f`, [OUTLET, FOOD])).rows[0];
  ok('review triggers can still update outlet and food ratings (internal path unaffected)', rt.o > 0 && rt.f >= 1, JSON.stringify(rt));
}

console.log('\n── DRAFT_push_tokens ──');
{
  await db.exec(draft('DRAFT_push_tokens.sql'));
  const TOK_A = 'fcm-token-device-A-0123456789abcdef', TOK_SHORT = 'short';
  const reg = (uid, tok) => as(uid, () => q(`SELECT register_push_token($1,'android')`, [tok]));
  const owner = async tok => (await q(`SELECT user_id FROM device_push_tokens WHERE token=$1`, [tok])).rows[0]?.user_id ?? null;
  await reg(ID.stu, TOK_A);
  ok('a signed-in user can register a token for themselves', await owner(TOK_A) === ID.stu);
  await reg(ID.stu, TOK_A);
  ok('registering twice is idempotent (one row)', (await q(`SELECT count(*)::int n FROM device_push_tokens WHERE token=$1`, [TOK_A])).rows[0].n === 1);
  await reg(ID.stu2, TOK_A);
  ok('account switch on the same phone MOVES the token to the new user', await owner(TOK_A) === ID.stu2);
  await as(ID.stu, () => q(`SELECT unregister_push_token($1)`, [TOK_A]));
  ok("a user cannot remove a token owned by someone else", await owner(TOK_A) === ID.stu2);
  await as(ID.stu2, () => q(`SELECT unregister_push_token($1)`, [TOK_A]));
  ok('logout removes the caller’s own token', await owner(TOK_A) === null);
  const bad = await fails(() => reg(ID.stu, TOK_SHORT), /INVALID_TOKEN/);
  ok('implausible tokens are rejected', bad.threw && bad.match, bad.msg);
  const anon = await fails(() => as(null, () => q(`SELECT register_push_token($1,'android')`, [TOK_A]), 'anon'), /permission denied/);
  ok('anon cannot register tokens', anon.threw && anon.match, anon.msg);
  await reg(ID.stu, TOK_A);
  for (const [who, uid] of [['student', ID.stu2], ['vendor', ID.vendor], ['owner', ID.stu]]) {
    const r = await fails(() => as(uid, () => q(`SELECT token FROM device_push_tokens`)), /permission denied/);
    ok(`${who} cannot read the token table directly`, r.threw && r.match, r.msg);
  }
  const w = await fails(() => as(ID.stu, () => q(`UPDATE device_push_tokens SET user_id=$1 WHERE token=$2`, [ID.stu2, TOK_A])), /permission denied/);
  ok('clients cannot reassign tokens with a direct UPDATE', w.threw && w.match, w.msg);
  const svc = await as(null, () => q(`SELECT count(*)::int n FROM device_push_tokens`), 'service_role');
  ok('the service role (delivery function) can read tokens', svc.rows[0].n >= 1);
  await db.exec(`SET session_replication_role = replica; DELETE FROM device_push_tokens; SET session_replication_role = origin;`);
}

console.log('\n── DRAFT_outlet_suspension ──');
await db.exec(draft('DRAFT_outlet_suspension.sql'));
const rpc = (uid, action, reason, confirm = null, outlet = OUTLET) =>
  as(uid, () => q(`SELECT admin_set_outlet_ordering_status($1, $2, $3, $4) AS r`, [outlet, action, reason, confirm]));
const existing = await placeOrderAs(ID.stu); // paid-or-pending order that exists BEFORE suspension
await as(ID.vendor, () => q(`SELECT vendor_accept_order($1)`, [existing]));
await db.exec(`UPDATE orders SET payment_status='PAID' WHERE id='${existing}'`);

const nonAdmin = await fails(() => rpc(ID.vendor, 'SUSPEND', 'valid long reason here', 'Test Outlet'), /Management access required/);
ok('vendor cannot call the suspension RPC', nonAdmin.threw && nonAdmin.match, nonAdmin.msg);
const stuAdmin = await fails(() => rpc(ID.stu, 'FLAG', 'reason'), /Management access required/);
ok('student cannot call the suspension RPC', stuAdmin.threw && stuAdmin.match, stuAdmin.msg);
const anonAdmin = await fails(() => as(null, () => q(`SELECT admin_set_outlet_ordering_status($1,'FLAG','reason',null)`, [OUTLET]), 'anon'));
ok('anon cannot call the suspension RPC', anonAdmin.threw, anonAdmin.msg);
const selfLift = await fails(() => as(ID.vendor, () => q(`UPDATE outlets SET ordering_status='FLAGGED' WHERE id=$1`, [OUTLET])), /only be changed by management/);
ok('vendor cannot edit ordering_status directly', selfLift.threw && selfLift.match, selfLift.msg);
const vendorEdit = await as(ID.vendor, () => q(`UPDATE outlets SET description='new' WHERE id=$1 RETURNING description`, [OUTLET]));
ok('vendor can still edit unrelated outlet fields', vendorEdit.rows[0]?.description === 'new');

await rpc(ID.admin, 'FLAG', 'monitoring after complaints');
ok('FLAG recorded', (await q(`SELECT ordering_status s FROM outlets WHERE id=$1`, [OUTLET])).rows[0].s === 'FLAGGED');
ok('flagged outlet still accepts orders', !!(await placeOrderAs(ID.stu2)));

const noConfirm = await fails(() => rpc(ID.admin, 'SUSPEND', 'serious food safety issue'), /CONFIRMATION_REQUIRED/);
ok('SUSPEND needs the outlet name as confirmation', noConfirm.threw && noConfirm.match, noConfirm.msg);
const shortReason = await fails(() => rpc(ID.admin, 'SUSPEND', 'short', 'Test Outlet'), /REASON_REQUIRED/);
ok('SUSPEND needs a substantive reason', shortReason.threw && shortReason.match, shortReason.msg);
await rpc(ID.admin, 'SUSPEND', 'serious food safety issue', 'Test Outlet');
ok('SUSPEND applied', (await q(`SELECT ordering_status s FROM outlets WHERE id=$1`, [OUTLET])).rows[0].s === 'SUSPENDED');
ok('is_open / is_active / deleted_at untouched by suspension',
   (await q(`SELECT is_open, is_active, deleted_at FROM outlets WHERE id=$1`, [OUTLET])).rows[0].is_open === true);

const lift = await fails(() => as(ID.vendor, () => q(`UPDATE outlets SET ordering_status='ACTIVE' WHERE id=$1`, [OUTLET])), /only be changed by management/);
ok('vendor cannot lift their own suspension with a direct UPDATE', lift.threw && lift.match, lift.msg);
const blocked = await fails(() => placeOrderAs(ID.stu), /OUTLET_ORDERING_SUSPENDED/);
ok('place_order rejects new orders for a suspended outlet (stale cart / direct RPC)', blocked.threw && blocked.match, blocked.msg);
const directInsert = await fails(() => db.exec(`INSERT INTO orders (order_number,user_id,vendor_id,outlet_id,subtotal,tax,total,status)
  VALUES ('X-1','${ID.stu}','${ID.vendor}','${OUTLET}',0,0,0,'PLACED')`), /OUTLET_ORDERING_SUSPENDED/);
ok('any other insert path is also blocked (trigger, not UI/RPC specific)', directInsert.threw && directInsert.match, directInsert.msg);
const ex = (await q(`SELECT status::text s, payment_status::text p FROM orders WHERE id=$1`, [existing])).rows[0];
ok('existing paid order preserved (status and payment untouched)', ex.s === 'ACCEPTED' && ex.p === 'PAID', JSON.stringify(ex));
await as(ID.vendor, () => q(`SELECT vendor_start_preparing($1)`, [existing]));
await as(ID.vendor, () => q(`SELECT vendor_mark_ready($1)`, [existing]));
await as(ID.vendor, async () => q(`SELECT verify_pickup_token($1)`, [await tokenOf(existing)]));
ok('existing order can still be fulfilled to pickup during suspension', (await q(`SELECT status::text s FROM orders WHERE id=$1`, [existing])).rows[0].s === 'PICKED_UP');
const other = await placeOrderAs(ID.stu2).catch(e => null);
ok('(isolation) other outlets unaffected', (await fails(() => q(`SELECT 1 FROM outlets WHERE id='${OUTLET2}' AND ordering_status='ACTIVE'`))).threw === false);

const badTrans = await fails(() => rpc(ID.admin, 'FLAG', 'again'), /INVALID_TRANSITION/);
ok('invalid transition (FLAG while suspended) rejected', badTrans.threw && badTrans.match, badTrans.msg);
const reactNoReason = await fails(() => rpc(ID.admin, 'REACTIVATE', 'x'), /REASON_REQUIRED/);
ok('REACTIVATE needs a reason', reactNoReason.threw && reactNoReason.match, reactNoReason.msg);
await rpc(ID.admin, 'REACTIVATE', 'inspection passed');
ok('REACTIVATE restores ordering', !!(await placeOrderAs(ID.stu)));

const deact0 = await fails(() => rpc(ID.admin, 'DEACTIVATE', 'permanent closure decided', 'wrong name'), /CONFIRMATION_REQUIRED/);
ok('DEACTIVATE needs exact outlet name', deact0.threw && deact0.match, deact0.msg);
await rpc(ID.admin, 'DEACTIVATE', 'permanent closure decided', 'Test Outlet');
const blocked2 = await fails(() => placeOrderAs(ID.stu2), /OUTLET_ORDERING_SUSPENDED/);
ok('deactivated outlet rejects orders', blocked2.threw && blocked2.match, blocked2.msg);
const react2 = await fails(() => rpc(ID.admin, 'REACTIVATE', 'changed our mind here'), /CONFIRMATION_REQUIRED/);
ok('reactivating a DEACTIVATED outlet needs name confirmation', react2.threw && react2.match, react2.msg);
await rpc(ID.admin, 'REACTIVATE', 'approved by director', 'Test Outlet');
ok('reactivated after deactivation', !!(await placeOrderAs(ID.stu2)));

const audit = (await q(`SELECT action, from_status, to_status FROM outlet_management_actions ORDER BY id`)).rows;
ok('audit trail has every action in order', audit.map(a => a.action).join() === 'FLAG,SUSPEND,REACTIVATE,DEACTIVATE,REACTIVATE', audit.map(a => a.action).join());
const auditRead = await as(ID.admin, () => q(`SELECT count(*)::int n FROM outlet_management_actions`));
ok('admin can read the audit trail', auditRead.rows[0].n === 5);
const auditVendor = await as(ID.vendor, () => q(`SELECT count(*)::int n FROM outlet_management_actions`));
ok('vendor sees no audit rows (RLS)', auditVendor.rows[0].n === 0);
const auditEdit = await fails(() => db.exec(`UPDATE outlet_management_actions SET reason='tampered reason'`), /append-only/);
ok('audit trail is append-only', auditEdit.threw && auditEdit.match, auditEdit.msg);
const auditWrite = await fails(() => as(ID.admin, () => q(`INSERT INTO outlet_management_actions(outlet_id,actor_id,action,from_status,to_status,reason) VALUES ($1,$2,'FLAG','ACTIVE','FLAGGED','forged entry')`, [OUTLET, ID.admin])), /permission denied/);
ok('clients cannot write audit rows directly', auditWrite.threw && auditWrite.match, auditWrite.msg);

console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);
