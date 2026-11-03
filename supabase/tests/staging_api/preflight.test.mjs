// Offline tests for preflight.mjs using dummy values and a stub fetch. Makes NO network requests.
//   node --test supabase/tests/staging_api/preflight.test.mjs
import test from 'node:test';
import assert from 'node:assert/strict';
import { checkStatic, preflight, PreflightError, STAGING_URL } from './preflight.mjs';

const PUB = 'sb_publishable_DUMMYDUMMYDUMMY', SEC = 'sb_secret_DUMMYDUMMYDUMMY';
const good = { url: STAGING_URL, publishableKey: PUB, secretKey: SEC };

/** Stub gateway: behaves like a correct staging project unless overridden. Records every request. */
function stub(overrides = {}) {
  const calls = [];
  const fetchImpl = async (u, init) => {
    calls.push({ u, method: init.method, headers: init.headers, redirect: init.redirect });
    const key = init.headers.apikey, path = u.replace(STAGING_URL, '');
    const o = overrides[path.split('?')[0] + '|' + (key === SEC ? 'sec' : 'pub')];
    if (o !== undefined) return { status: o };
    if (path.startsWith('/auth/v1/admin')) return { status: key === SEC ? 200 : 401 };
    return { status: 200 };
  };
  return { fetchImpl, calls };
}
const rejects = (p, re) => assert.rejects(p, e => e instanceof PreflightError && re.test(e.message));

test('static: names every missing variable', () => {
  assert.throws(() => checkStatic({}), e => /CRAVE_STAGING_URL, CRAVE_STAGING_PUBLISHABLE_KEY, CRAVE_STAGING_SECRET_KEY/.test(e.message));
});
test('static: production / other / lookalike URLs refused', () => {
  for (const url of ['https://btdmhveaqssuuhyoyanz.supabase.co', 'https://cloxhkpvgarnnwqnxfkr.supabase.co',
                     'https://yatbgxfjlnqpvrftjdmu.supabase.co.evil.test', 'http://yatbgxfjlnqpvrftjdmu.supabase.co', 'https://example.com'])
    assert.throws(() => checkStatic({ ...good, url }), PreflightError);
  assert.equal(checkStatic({ ...good, url: STAGING_URL + '/' }), STAGING_URL);
});
test('static: legacy JWT keys refused (they are treated as compromised)', () => {
  assert.throws(() => checkStatic({ ...good, publishableKey: 'eyJhbGciOi.x.y' }), e => /legacy/.test(e.message));
  assert.throws(() => checkStatic({ ...good, secretKey: 'eyJhbGciOi.x.y' }), e => /legacy/.test(e.message));
});
test('static: wrong prefixes, swapped or identical keys refused', () => {
  assert.throws(() => checkStatic({ ...good, publishableKey: SEC, secretKey: PUB }), PreflightError);
  assert.throws(() => checkStatic({ ...good, publishableKey: 'sb_publishable_x', secretKey: 'sb_publishable_x' }), PreflightError);
  assert.throws(() => checkStatic({ ...good, secretKey: 'random' }), PreflightError);
  assert.throws(() => checkStatic({ ...good, publishableKey: PUB + ' ' }), PreflightError);
});
test('preflight: passes against a correct staging gateway and only issues read-only GETs', async () => {
  const { fetchImpl, calls } = stub();
  const r = await preflight({ ...good, fetchImpl });
  assert.equal(r.checks.length, 3);
  assert.ok(calls.length === 3 && calls.every(c => c.method === 'GET' && c.redirect === 'error' && c.u.startsWith(STAGING_URL)));
  assert.ok(calls.every(c => Object.keys(c.headers).join() === 'apikey'), 'sb_ keys must only travel in the apikey header');
});
test('preflight: invalid/foreign publishable key aborts', async () => {
  await rejects(preflight({ ...good, fetchImpl: stub({ '/auth/v1/settings|pub': 401 }).fetchImpl }), /rejected by Auth/);
});
test('preflight: privileged "publishable" key (swapped keys) aborts', async () => {
  await rejects(preflight({ ...good, fetchImpl: stub({ '/auth/v1/admin/users|pub': 200 }).fetchImpl }), /admin access/);
});
test('preflight: invalid/foreign secret key aborts', async () => {
  await rejects(preflight({ ...good, fetchImpl: stub({ '/auth/v1/admin/users|sec': 401 }).fetchImpl }), /secret key rejected/);
  await rejects(preflight({ ...good, fetchImpl: stub({ '/auth/v1/admin/users|sec': 500 }).fetchImpl }), /secret key rejected/);
});
test('preflight: network errors abort, and never echo a key', async () => {
  const boom = async () => { throw Object.assign(new Error(`connect failed apikey=${PUB}`), { name: 'FetchError' }); };
  await assert.rejects(preflight({ ...good, fetchImpl: boom }), e => e instanceof PreflightError && !e.message.includes(PUB) && !e.message.includes(SEC));
});
test('preflight: no request is made when static validation fails', async () => {
  const { fetchImpl, calls } = stub();
  await rejects(preflight({ ...good, url: 'https://btdmhveaqssuuhyoyanz.supabase.co', fetchImpl }), /exactly/);
  await rejects(preflight({ ...good, secretKey: 'eyJ.legacy.key', fetchImpl }), /legacy/);
  assert.equal(calls.length, 0);
});
test('no error message from any failure path contains a key value', async () => {
  const cases = [{ '/auth/v1/settings|pub': 401 }, { '/auth/v1/admin/users|pub': 200 }, { '/auth/v1/admin/users|sec': 401 }];
  for (const o of cases) await assert.rejects(preflight({ ...good, fetchImpl: stub(o).fetchImpl }), e => !e.message.includes('DUMMY'));
});
