import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"
import { HmacSha256 } from "https://deno.land/std@0.160.0/hash/sha256.ts";

const RAZORPAY_KEY_SECRET = Deno.env.get('RAZORPAY_KEY_SECRET')

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    const supabaseClient = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_ANON_KEY') ?? '',
      { global: { headers: { Authorization: req.headers.get('Authorization')! } } }
    )

    // Privileged client: ONLY used for mark_payment_verified, and only after the user is authenticated,
    // owns the payment (RLS-scoped select below) and the HMAC signature has been checked.
    // (mark_payment_verified is revoked from anon/authenticated by DRAFT_payment_verification_lockdown.sql.)
    const supabaseAdmin = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    )

    const { data: { user }, error: authError } = await supabaseClient.auth.getUser()
    if (authError || !user) throw new Error('Unauthorized')

    const { razorpay_order_id, razorpay_payment_id, razorpay_signature, order_id } = await req.json()

    if (!razorpay_order_id || !razorpay_payment_id || !razorpay_signature || !order_id) {
        throw new Error('Missing verification parameters')
    }

    // 1. Fetch the stored Razorpay Order ID to ensure client didn't spoof it
    const { data: payment, error: payError } = await supabaseClient
      .from('payments')
      .select('razorpay_order_id, status')
      .eq('order_id', order_id)
      .single()

    if (payError || !payment) throw new Error('Order not found')
    if (payment.razorpay_order_id !== razorpay_order_id) throw new Error('Razorpay Order ID mismatch')

    // 2. Verify Signature
    const expectedSignature = new HmacSha256(RAZORPAY_KEY_SECRET!)
      .update(`${razorpay_order_id}|${razorpay_payment_id}`)
      .toString();

    if (expectedSignature !== razorpay_signature) {
        // Mark payment as FAILED if signature is invalid
        await supabaseAdmin.rpc('mark_payment_verified', {
            p_order_id: order_id,
            p_razorpay_payment_id: razorpay_payment_id,
            p_razorpay_signature: razorpay_signature,
            p_status: 'FAILED'
        })
        throw new Error('Invalid payment signature')
    }

    // 3. Mark as PAID (Idempotent)
    const { error: rpcError } = await supabaseAdmin.rpc('mark_payment_verified', {
        p_order_id: order_id,
        p_razorpay_payment_id: razorpay_payment_id,
        p_razorpay_signature: razorpay_signature,
        p_status: 'PAID'
    })

    if (rpcError) throw rpcError

    return new Response(
      JSON.stringify({ success: true, message: 'Payment verified' }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    )

  } catch (error) {
    return new Response(JSON.stringify({ error: error.message }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 400,
    })
  }
})
