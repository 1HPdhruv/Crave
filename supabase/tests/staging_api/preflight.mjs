// Key + project preflight for the CRAVE-Staging API script. Pure logic with an injectable fetch, so it is
// unit-tested offline (preflight.test.mjs) and run.mjs can import it unchanged.
//
// New-format keys (sb_publishable_… / sb_secret_…) are opaque, not JWTs, so there is no `ref` claim to read.
// Project identity is therefore established by (1) the exact pinned URL and (2) READ-ONLY requests that only
// succeed when the key belongs to the project behind that URL. Nothing here writes. Keys are never put in
// messages, logs or thrown errors; only the key's *name* and an HTTP status are.

export const STAGING_REF = 'yatbgxfjlnqpvrftjdmu';
export const STAGING_URL = `https://${STAGING_REF}.supabase.co`;
const KNOWN_OTHER_REFS = ['btdmhveaqssuuhyoyanz', 'cloxhkpvgarnnwqnxfkr']; // production, paused project

export class PreflightError extends Error {}
const fail = m => { throw new PreflightError(m); };

/** Offline checks: exact URL, key shape. Throws PreflightError. Does no network I/O. */
export function checkStatic({ url, publishableKey, secretKey }) {
  const missing = [];
  if (!url) missing.push('CRAVE_STAGING_URL');
  if (!publishableKey) missing.push('CRAVE_STAGING_PUBLISHABLE_KEY');
  if (!secretKey) missing.push('CRAVE_STAGING_SECRET_KEY');
  if (missing.length) fail(`missing environment variable(s): ${missing.join(', ')}`);

  const clean = url.replace(/\/$/, '');
  if (clean !== STAGING_URL) fail(`CRAVE_STAGING_URL must be exactly ${STAGING_URL}`);
  if (KNOWN_OTHER_REFS.some(r => clean.includes(r))) fail('CRAVE_STAGING_URL points at a non-staging project');

  if (publishableKey.startsWith('eyJ') || secretKey.startsWith('eyJ'))
    fail('legacy JWT keys (anon / service_role) are refused; use the new sb_publishable_ / sb_secret_ keys');
  if (!publishableKey.startsWith('sb_publishable_')) fail('CRAVE_STAGING_PUBLISHABLE_KEY must start with sb_publishable_');
  if (!secretKey.startsWith('sb_secret_')) fail('CRAVE_STAGING_SECRET_KEY must start with sb_secret_');
  if (publishableKey === secretKey) fail('publishable and secret keys must be different values');
  if (/\s/.test(publishableKey + secretKey)) fail('a key contains whitespace');
  return clean;
}

/**
 * Online, read-only checks (GET only). Abort (throw) on the first failure.
 *  1. publishable key accepted by this project's Auth gateway  -> GET /auth/v1/settings
 *  2. publishable key is NOT privileged                         -> admin users list must be refused
 *  3. secret key IS privileged for this project                 -> admin users list (per_page=1) must succeed
 *  4. the two keys are not swapped                              (implied by 2 + 3)
 * (The REST root is deliberately not probed: publishable keys get 401 there because schema introspection needs the
 *  secret key. Observed on staging.) A key from any other project is rejected by Auth at 1/3, tying both keys to this URL.
 */
export async function preflight({ url, publishableKey, secretKey, fetchImpl = fetch, timeoutMs = 15000 }) {
  const base = checkStatic({ url, publishableKey, secretKey });
  const get = async (path, key) => {
    let res;
    try {
      // sb_ keys go in `apikey` only; `redirect: error` so a redirect can never carry a key elsewhere.
      res = await fetchImpl(base + path, { method: 'GET', headers: { apikey: key }, redirect: 'error', signal: AbortSignal.timeout(timeoutMs) });
    } catch (e) {
      fail(`network error during preflight (${path.split('?')[0]}): ${e?.name ?? 'error'}`);
    }
    return res.status;
  };

  const authStatus = await get('/auth/v1/settings', publishableKey);
  if (authStatus !== 200) fail(`publishable key rejected by Auth (HTTP ${authStatus})`);

  const pubAdmin = await get('/auth/v1/admin/users?per_page=1', publishableKey);
  if (pubAdmin >= 200 && pubAdmin < 300) fail('publishable key has admin access: keys may be swapped or the wrong key was supplied');

  const secAdmin = await get('/auth/v1/admin/users?per_page=1', secretKey);
  if (secAdmin !== 200) fail(`secret key rejected by Auth admin (HTTP ${secAdmin})`);

  return { url: base, checks: ['auth-settings', 'publishable-not-privileged', 'secret-privileged'] };
}
