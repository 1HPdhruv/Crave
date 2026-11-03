// DRAFT — NOT DEPLOYED. Reviewed-only: Deno is not available locally, so this file has NOT been executed.
//
// Sends an FCM push for every new row in public.notifications (the SAME table the in-app list reads: no second
// notification system). Wire it as a Supabase Database Webhook on INSERT into public.notifications -> this function.
//
// Required secrets (set in the Supabase dashboard / `supabase secrets set`; NEVER commit them):
//   FCM_SERVICE_ACCOUNT_JSON   Firebase service account JSON (FCM HTTP v1)  — needs Firebase project "gag---srm-food" access
//   PUSH_WEBHOOK_SECRET        shared secret the webhook sends in the `x-webhook-secret` header
//   SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY   provided to Edge Functions automatically
//
// Privacy: the payload is built ONLY from the notifications row (title/body/ids). Ticket internal notes never enter
// that table, so they cannot reach a push. Idempotency: the Android notification id derives from notification_id, so a
// webhook retry re-delivers into the same slot instead of stacking.
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

/** Pure payload builder (kept separate so it can be unit-tested once Deno/Node tooling is available). */
export function buildData(row: { id: string; title: string; body: string; order_id?: string | null; deep_link?: string | null }) {
  const data: Record<string, string> = { notification_id: row.id, title: String(row.title ?? "").slice(0, 80), body: String(row.body ?? "").slice(0, 240) }
  const ticket = row.deep_link?.match(/^ticket:([0-9a-f-]{36})/i)?.[1]
  if (ticket && UUID.test(ticket)) data.ticket_id = ticket
  else if (row.order_id && UUID.test(row.order_id)) data.order_id = row.order_id
  return data
}

async function accessToken(sa: { client_email: string; private_key: string; token_uri?: string }): Promise<string> {
  const now = Math.floor(Date.now() / 1000)
  const enc = (o: unknown) => btoa(JSON.stringify(o)).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_")
  const unsigned = `${enc({ alg: "RS256", typ: "JWT" })}.${enc({ iss: sa.client_email, scope: "https://www.googleapis.com/auth/firebase.messaging", aud: sa.token_uri ?? "https://oauth2.googleapis.com/token", iat: now, exp: now + 3600 })}`
  const pem = sa.private_key.replace(/-----[A-Z ]+-----/g, "").replace(/\s/g, "")
  const key = await crypto.subtle.importKey("pkcs8", Uint8Array.from(atob(pem), c => c.charCodeAt(0)), { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"])
  const sig = new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned)))
  const jwt = `${unsigned}.${btoa(String.fromCharCode(...sig)).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_")}`
  const res = await fetch(sa.token_uri ?? "https://oauth2.googleapis.com/token", { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: jwt }) })
  if (!res.ok) throw new Error("FCM auth failed")
  return (await res.json()).access_token
}

Deno.serve(async (req) => {
  if (req.headers.get("x-webhook-secret") !== Deno.env.get("PUSH_WEBHOOK_SECRET")) return new Response("forbidden", { status: 403 })
  const payload = await req.json()
  const row = payload?.record
  if (payload?.type !== "INSERT" || !row?.id || !row?.user_id || !UUID.test(row.user_id)) return new Response("ignored", { status: 200 })

  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!)
  const { data: tokens, error } = await admin.from("device_push_tokens").select("token").eq("user_id", row.user_id)
  if (error) return new Response("token lookup failed", { status: 500 })      // webhook retries
  if (!tokens?.length) return new Response("no devices", { status: 200 })

  const sa = JSON.parse(Deno.env.get("FCM_SERVICE_ACCOUNT_JSON")!)
  const bearer = await accessToken(sa)
  const data = buildData(row)
  let failed = 0
  for (const { token } of tokens) {
    const res = await fetch(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
      method: "POST", headers: { Authorization: `Bearer ${bearer}`, "Content-Type": "application/json" },
      body: JSON.stringify({ message: { token, data, android: { priority: "HIGH" } } }),
    })
    if (res.ok) continue
    const err = await res.json().catch(() => ({}))
    if (err?.error?.details?.some((d: { errorCode?: string }) => d.errorCode === "UNREGISTERED") || res.status === 404) {
      await admin.from("device_push_tokens").delete().eq("token", token)       // dead token: clean up
    } else failed++
  }
  return new Response(failed ? "partial" : "ok", { status: failed ? 500 : 200 })         // 5xx => webhook retry (idempotent on device)
})
