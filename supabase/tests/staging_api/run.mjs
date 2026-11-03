// CRAVE-Staging API verification (real PostgREST/Auth, separate sessions). NOT YET RUN: needs the replacement staging keys.
//
//   export CRAVE_STAGING_URL=https://yatbgxfjlnqpvrftjdmu.supabase.co
//   export CRAVE_STAGING_PUBLISHABLE_KEY=sb_publishable_...   # (CRAVE_STAGING_ANON_KEY is accepted as an alias, shared with the Gradle build)
//   export CRAVE_STAGING_SECRET_KEY=sb_secret_...             # used ONLY for synthetic setup + cleanup; never logged or stored
//   node supabase/tests/staging_api/run.mjs [--cleanup]
//
// Fail closed, before ANY write:
//   * URL must be exactly the CRAVE-Staging project; legacy JWT keys (eyJ…) are refused outright.
//   * sb_ keys are opaque (no `ref` claim), so ownership is proven by read-only GET preflight requests that only
//     succeed for this project (see preflight.mjs, unit-tested offline in preflight.test.mjs). Any failure aborts.
// The secret key is used only for synthetic setup/cleanup; secrets are never printed or persisted; synthetic users get
// random in-memory passwords. Checks that depend on not-yet-deployed drafts report SKIP (not PASS).
import { randomBytes, randomUUID } from 'node:crypto';
import { preflight, PreflightError } from './preflight.mjs';

const die = m => { console.error('REFUSING: ' + m); process.exit(2); };
const URL_ = process.env.CRAVE_STAGING_URL?.replace(/\/$/, '');
const ANON = process.env.CRAVE_STAGING_PUBLISHABLE_KEY ?? process.env.CRAVE_STAGING_ANON_KEY; // publishable key
const SVC = process.env.CRAVE_STAGING_SECRET_KEY;                                              // secret key
try {
  await preflight({ url: URL_, publishableKey: ANON, secretKey: SVC });
} catch (e) {
  die(e instanceof PreflightError ? e.message : 'preflight failed unexpectedly');
}
console.log('preflight OK: project pinned, publishable key valid and unprivileged, secret key valid (read-only checks only)');
const CLEANUP = process.argv.includes('--cleanup');

let pass = 0, fail = 0, skip = 0;
const ok = (n, c, x = '') => { c ? pass++ : fail++; console.log(`${c ? 'PASS' : 'FAIL'}  ${n}${c ? '' : '  ' + x}`); };
const skipT = (n, why) => { skip++; console.log(`SKIP  ${n}  (${why})`); };

async function http(path, { method = 'GET', key = ANON, token, body, headers = {} } = {}) {
  // sb_ keys are not JWTs: send them only as `apikey`. A Bearer token is sent only for real user sessions.
  const auth = token && token !== key ? { Authorization: `Bearer ${token}` } : {};
  const res = await fetch(URL_ + path, { method, redirect: 'error', headers: { apikey: key, ...auth, 'Content-Type': 'application/json', ...headers }, body: body ? JSON.stringify(body) : undefined });
  const text = await res.text(); let json; try { json = JSON.parse(text); } catch { json = text; }
  return { status: res.status, json, text };
}
const rpc = (fn, args, token) => http(`/rest/v1/rpc/${fn}`, { method: 'POST', token, body: args ?? {} });
const sel = (tbl, qs, token) => http(`/rest/v1/${tbl}?${qs}`, { token });
const svc = (path, o = {}) => http(path, { ...o, key: SVC });
const has = (r, re) => re.test(r.text);
const missing = r => /PGRST202|Could not find the function|does not exist/.test(r.text);

// ── Setup: synthetic identities (service role used only here) ───────────────
const tag = randomUUID().slice(0, 8);
const people = {};
for (const [k, role] of [['stuA', 'STUDENT'], ['stuB', 'STUDENT'], ['vendor', 'VENDOR'], ['admin', 'ADMIN']]) {
  const email = `zz-synth-${tag}-${k}@crave-test.invalid`, password = randomBytes(18).toString('base64url');
  const u = await svc('/auth/v1/admin/users', { method: 'POST', body: { email, password, email_confirm: true, user_metadata: { name: `ZZ-SYNTH ${k}` } } });
  if (u.status >= 300) die(`could not create ${k}: ${u.status}`);
  const id = u.json.id;
  await svc('/rest/v1/profiles?on_conflict=id', { method: 'POST', headers: { Prefer: 'resolution=merge-duplicates' }, body: { id, name: `ZZ-SYNTH ${k}`, email, role, is_active: true } });
  const s = await http('/auth/v1/token?grant_type=password', { method: 'POST', body: { email, password } });
  if (!s.json.access_token) die(`could not sign in ${k}`);
  people[k] = { id, token: s.json.access_token };
}
// Seed minimum records through the real schema (service role).
const outlet = (await svc('/rest/v1/outlets', { method: 'POST', headers: { Prefer: 'return=representation' }, body: { name: `ZZ-SYNTH Outlet ${tag}`, description: 'synthetic', vendor_id: people.vendor.id, is_open: true, is_active: true } })).json[0];
const cat = (await svc('/rest/v1/categories?on_conflict=name', { method: 'POST', headers: { Prefer: 'resolution=merge-duplicates,return=representation' }, body: { name: `ZZ-SYNTH ${tag}`, emoji: 'x' } })).json[0];
const food = (await svc('/rest/v1/food_items', { method: 'POST', headers: { Prefer: 'return=representation' }, body: { outlet_id: outlet.id, category_id: cat.id, name: 'ZZ Samosa', description: 'synthetic', price: 20 } })).json[0];
await svc('/rest/v1/inventory', { method: 'POST', body: { food_item_id: food.id, quantity_available: 50 } });
const d = new Date(Date.now() + 2 * 864e5).toISOString().slice(0, 10);
const slot = (await svc('/rest/v1/pickup_slots', { method: 'POST', headers: { Prefer: 'return=representation' }, body: { outlet_id: outlet.id, slot_date: d, start_time: '11:00', end_time: '11:30', capacity: 20 } })).json[0];
async function order(p) { // via the real place_order path
  const c = await svc('/rest/v1/carts?on_conflict=user_id', { method: 'POST', headers: { Prefer: 'resolution=merge-duplicates,return=representation' }, body: { user_id: p.id, outlet_id: outlet.id } });
  await svc(`/rest/v1/cart_items?cart_id=eq.${c.json[0].id}`, { method: 'DELETE' });
  await svc('/rest/v1/cart_items', { method: 'POST', body: { cart_id: c.json[0].id, food_item_id: food.id, quantity: 1, price: 20, is_veg: true } });
  return rpc('place_order', { p_cart_id: c.json[0].id, p_pickup_slot_id: slot.id, p_payment_method: 'PAY_AT_COUNTER' }, p.token);
}
const oA = await order(people.stuA); ok('student places an order through the real place_order RPC', oA.status < 300, oA.text);
const orderId = oA.json;

console.log('\n── Anonymous ──');
for (const fn of ['create_support_ticket', 'admin_list_support_tickets', 'add_support_ticket_message', 'place_order', 'verify_pickup_token']) {
  const r = await rpc(fn, {}, ANON); ok(`anon cannot execute ${fn}`, r.status === 401 || r.status === 403 || has(r, /permission denied|JWT|42501/i) || missing(r), r.text.slice(0, 120));
}
for (const t of ['support_tickets', 'support_ticket_messages', 'support_ticket_internal_notes', 'support_ticket_events']) {
  const r = await sel(t, 'select=*&limit=1', ANON); ok(`anon cannot read ${t}`, r.status >= 400 || (Array.isArray(r.json) && r.json.length === 0), r.text.slice(0, 100));
}
const anonTrunc = await http('/rest/v1/food_items?id=not.is.null', { method: 'DELETE' });
ok('anon cannot delete from food_items', anonTrunc.status >= 400 || (Array.isArray(anonTrunc.json) && anonTrunc.json.length === 0), anonTrunc.text.slice(0, 100));

console.log('\n── Student tickets ──');
const rq = randomUUID();
const mk = (p, cat, subj, desc, ord, req = randomUUID(), out = null) => rpc('create_support_ticket', { p_category: cat, p_subject: subj, p_description: desc, p_order_id: ord, p_outlet_id: out, p_client_request_id: req }, p.token);
const t1 = await mk(people.stuA, 'FOOD_QUALITY', 'Cold food', 'The samosa was cold and stale', orderId, rq);
ok('create ticket on own order; outlet derived by server', t1.status < 300 && t1.json.outlet_id === outlet.id, t1.text);
ok('response omits assigned_to / client_request_id', t1.status < 300 && !('assigned_to' in t1.json) && !('client_request_id' in t1.json));
const t1b = await mk(people.stuA, 'FOOD_QUALITY', 'Cold food', 'The samosa was cold and stale', orderId, rq);
ok('retry with same request id returns the same ticket', t1b.json?.id === t1.json?.id, t1b.text);
const dup = await mk(people.stuA, 'FOOD_QUALITY', 'Cold again', 'Second complaint same order', orderId);
ok('duplicate active ticket -> TICKET_ALREADY_OPEN', has(dup, /TICKET_ALREADY_OPEN/), dup.text);
const foreign = await mk(people.stuB, 'ORDER', 'Not mine', 'Trying A’s order id here', orderId);
ok("student B cannot raise a ticket on A's order", has(foreign, /ORDER_NOT_FOUND/), foreign.text);
const unrelated = await mk(people.stuA, 'ORDER', 'Bad ids', 'Random order id used here', randomUUID());
ok('unrelated order id rejected', has(unrelated, /ORDER_NOT_FOUND/), unrelated.text);
ok('student A reads own ticket', (await sel('support_tickets', `id=eq.${t1.json.id}&select=id,status,subject`, people.stuA.token)).json.length === 1);
ok("student B cannot read A's ticket", (await sel('support_tickets', `id=eq.${t1.json.id}&select=id`, people.stuB.token)).json.length === 0);
ok('assigned_to not selectable by student', (await sel('support_tickets', 'select=assigned_to', people.stuA.token)).status >= 400);
for (const t of ['support_ticket_internal_notes', 'support_ticket_events']) {
  const r = await sel(t, 'select=*', people.stuA.token); ok(`student cannot read ${t}`, r.status >= 400 || r.json.length === 0, r.text.slice(0, 100));
}
const direct = await http(`/rest/v1/support_tickets?id=eq.${t1.json.id}`, { method: 'PATCH', token: people.stuA.token, body: { status: 'RESOLVED' } });
ok('student cannot PATCH ticket status', direct.status >= 400 || (Array.isArray(direct.json) && direct.json.length === 0), direct.text.slice(0, 100));
const fu = await rpc('add_support_ticket_message', { p_ticket_id: t1.json.id, p_body: 'It was also missing a chutney' }, people.stuA.token);
ok('student follow-up message', fu.status < 300, fu.text);
const burst = []; for (let i = 0; i < 6; i++) burst.push(await mk(people.stuB, 'OTHER', `Rate ${i}`, 'Rate limit probing request', null, randomUUID(), outlet.id));
ok('rate limit triggers within 6 rapid tickets', burst.some(r => has(r, /RATE_LIMITED|TOO_MANY/)), 'no limit hit');
const par = await Promise.all([0, 1, 2, 3].map(() => mk(people.stuA, 'PICKUP', 'Parallel', 'Four parallel identical requests', orderId, randomUUID())));
ok('concurrent identical creates yield at most one new active ticket (DB uniqueness)', (await sel('support_tickets', `order_id=eq.${orderId}&category=eq.PICKUP&select=id`, people.stuA.token)).json.length <= 1, JSON.stringify(par.map(r => r.status)));

console.log('\n── Vendor ──');
ok('vendor sees no tickets', (await sel('support_tickets', 'select=id', people.vendor.token)).json.length === 0);
for (const fn of ['admin_list_support_tickets', 'admin_get_support_ticket']) {
  const r = await rpc(fn, { p_ticket_id: t1.json.id }, people.vendor.token); ok(`vendor cannot call ${fn}`, has(r, /Management access required|permission/i) || r.status >= 400, r.text.slice(0, 100));
}
const vn = await rpc('admin_add_support_ticket_note', { p_ticket_id: t1.json.id, p_note: 'x' }, people.vendor.token);
ok('vendor cannot add internal notes', vn.status >= 400, vn.text.slice(0, 100));

console.log('\n── Management ──');
const A = people.admin.token, tid = t1.json.id;
const list = await rpc('admin_list_support_tickets', { p_search: 'Cold' }, A); ok('admin lists/searches tickets', list.status < 300 && list.json.length >= 1, list.text.slice(0, 120));
ok('admin filters by status', (await rpc('admin_list_support_tickets', { p_statuses: ['RESOLVED'] }, A)).json.every(x => x.status === 'RESOLVED'));
ok('admin assigns to an active admin', (await rpc('admin_assign_support_ticket', { p_ticket_id: tid, p_assignee: people.admin.id }, A)).status < 300);
ok('assign to a student rejected', has(await rpc('admin_assign_support_ticket', { p_ticket_id: tid, p_assignee: people.stuA.id }, A), /ASSIGNEE_NOT_MANAGEMENT/));
ok('admin adds internal note', (await rpc('admin_add_support_ticket_note', { p_ticket_id: tid, p_note: 'SECRET-NOTE internal' }, A)).status < 300);
ok('admin sends student-facing reply', (await rpc('admin_respond_support_ticket', { p_ticket_id: tid, p_body: 'We are reviewing this' }, A)).status < 300);
const msgs = await sel('support_ticket_messages', `ticket_id=eq.${tid}&select=body`, people.stuA.token);
ok('student sees the reply but never the internal note', msgs.json.some(m => /reviewing/.test(m.body)) && !JSON.stringify(msgs.json).includes('SECRET-NOTE'));
ok('stale expected status -> TICKET_CHANGED', has(await rpc('admin_set_support_ticket_status', { p_ticket_id: tid, p_status: 'UNDER_REVIEW', p_expected_status: 'RESOLVED' }, A), /TICKET_CHANGED/));
ok('OPEN -> UNDER_REVIEW', (await rpc('admin_set_support_ticket_status', { p_ticket_id: tid, p_status: 'UNDER_REVIEW', p_expected_status: 'OPEN' }, A)).status < 300);
ok('resolve without reason rejected', has(await rpc('admin_set_support_ticket_status', { p_ticket_id: tid, p_status: 'RESOLVED' }, A), /REASON_REQUIRED/));
ok('resolve with reason', (await rpc('admin_set_support_ticket_status', { p_ticket_id: tid, p_status: 'RESOLVED', p_reason: 'Refunded at counter' }, A)).status < 300);
ok('student cannot reply on resolved ticket', has(await rpc('add_support_ticket_message', { p_ticket_id: tid, p_body: 'hi' }, people.stuA.token), /TICKET_CLOSED/));
const t2 = await mk(people.stuA, 'FOOD_QUALITY', 'Again cold', 'Cold again after resolution', orderId);
ok('new active ticket for same order+category allowed after resolution', t2.status < 300, t2.text);
ok('reopen while another active ticket exists -> REOPEN_CONFLICT', has(await rpc('admin_set_support_ticket_status', { p_ticket_id: tid, p_status: 'UNDER_REVIEW', p_reason: 'new photos' }, A), /REOPEN_CONFLICT/));
const detail = await rpc('admin_get_support_ticket', { p_ticket_id: tid }, A);
ok('audit trail present and consistent', detail.json.events.map(e => e.action).includes('CREATED') && detail.json.events.some(e => e.action === 'STATUS_CHANGED'), detail.text.slice(0, 100));
const assg = await rpc('admin_list_support_assignees', {}, A);
missing(assg) ? skipT('assignee list', 'DRAFT_support_admin_helpers.sql not deployed') : ok('assignee list returns active admins', assg.json.some(a => a.id === people.admin.id));
const nt = await sel('notifications', `user_id=eq.${people.stuA.id}&deep_link=like.ticket:*&select=title,body,deep_link`, people.stuA.token);
nt.json?.length ? ok('student received ticket notifications, none contain the internal note', !JSON.stringify(nt.json).includes('SECRET-NOTE')) : skipT('ticket notifications', 'DRAFT_support_notifications.sql not deployed');

console.log('\n── Pickup token ──');
const vend = people.vendor.token;
for (const fn of ['vendor_accept_order', 'vendor_start_preparing', 'vendor_mark_ready']) ok(`${fn}`, (await rpc(fn, { p_order_id: orderId }, vend)).status < 300);
const bypass = await http(`/rest/v1/orders?id=eq.${orderId}`, { method: 'PATCH', token: vend, body: { status: 'PICKED_UP' }, headers: { Prefer: 'return=representation' } });
const after = (await svc(`/rest/v1/orders?id=eq.${orderId}&select=status`)).json[0].status;
after === 'PICKED_UP' ? ok('vendor direct READY->PICKED_UP is blocked', false, 'BYPASS STILL OPEN (expected until DRAFT_pickup_token_bypass.sql is deployed)') : ok('vendor direct READY->PICKED_UP is blocked', true);
const tok = (await svc(`/rest/v1/pickup_tokens?order_id=eq.${orderId}&select=token_value`)).json?.[0]?.token_value;
if (after !== 'PICKED_UP' && tok) {
  ok('other vendor/student cannot verify the token', (await rpc('verify_pickup_token', { p_token: tok }, people.stuA.token)).status >= 400);
  ok('owning vendor verifies the token', (await rpc('verify_pickup_token', { p_token: tok }, vend)).status < 300);
  ok('token cannot be reused', has(await rpc('verify_pickup_token', { p_token: tok }, vend), /already used/));
}

console.log('\n── Payment verification ──');
const o2 = await order(people.stuB);
const pay = await rpc('mark_payment_verified', { p_order_id: o2.json, p_razorpay_payment_id: 'pay_fake', p_razorpay_signature: 'sig_fake', p_status: 'PAID' }, people.stuA.token);
const paid = (await svc(`/rest/v1/orders?id=eq.${o2.json}&select=payment_status`)).json[0].payment_status;
ok('a student cannot mark ANOTHER student’s order PAID via the RPC', paid !== 'PAID', 'OPEN until DRAFT_payment_verification_lockdown.sql (+ Edge Function) is deployed');

console.log('\n── Outlet suspension ──');
const probe = await rpc('admin_set_outlet_ordering_status', { p_outlet_id: outlet.id, p_action: 'FLAG', p_reason: 'staging probe' }, A);
if (missing(probe)) skipT('outlet suspension checks', 'DRAFT_outlet_suspension.sql not deployed');
else {
  ok('non-admin cannot suspend', has(await rpc('admin_set_outlet_ordering_status', { p_outlet_id: outlet.id, p_action: 'SUSPEND', p_reason: 'vendor trying it', p_confirm_name: outlet.name }, vend), /Management access required/));
  await http(`/rest/v1/outlets?id=eq.${outlet.id}`, { method: 'PATCH', token: vend, body: { ordering_status: 'FLAGGED' } });
  ok('vendor cannot edit ordering_status directly', (await svc(`/rest/v1/outlets?id=eq.${outlet.id}&select=ordering_status`)).json[0].ordering_status === 'ACTIVE');
  ok('admin suspends with reason + name confirmation', (await rpc('admin_set_outlet_ordering_status', { p_outlet_id: outlet.id, p_action: 'SUSPEND', p_reason: 'staging suspension test', p_confirm_name: outlet.name }, A)).status < 300);
  const blocked = await order(people.stuB); ok('new order rejected for suspended outlet', has(blocked, /OUTLET_ORDERING_SUSPENDED/), blocked.text.slice(0, 120));
  ok('existing order untouched', (await svc(`/rest/v1/orders?id=eq.${orderId}&select=status`)).json[0].status !== 'CANCELLED');
  ok('admin reactivates', (await rpc('admin_set_outlet_ordering_status', { p_outlet_id: outlet.id, p_action: 'REACTIVATE', p_reason: 'staging reactivation' }, A)).status < 300);
  ok('orders accepted again', (await order(people.stuB)).status < 300);
}

console.log(`\n${pass} passed, ${fail} failed, ${skip} skipped (skipped = depends on an undeployed draft)`);
if (CLEANUP) { for (const p of Object.values(people)) await svc(`/auth/v1/admin/users/${p.id}`, { method: 'DELETE' }); console.log('synthetic auth users removed (data rows are retained by FK RESTRICT; harmless synthetic data)'); }
process.exit(fail ? 1 : 0);
