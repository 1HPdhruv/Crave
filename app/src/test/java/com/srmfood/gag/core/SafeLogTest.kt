package com.srmfood.gag.core

import com.srmfood.gag.core.common.SafeLog
import org.junit.Assert.*
import org.junit.Test

class SafeLogTest {
    private val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiYXV0aGVudGljYXRlZCJ9.c2lnbmF0dXJlc2lnbmF0dXJl"

    @Test fun `supabase-kt header dumps are redacted`() {
        // Shape of a real supabase-kt RestException message seen in logcat
        val msg = "permission denied for table outlets\nURL: https://x.supabase.co/rest/v1/outlets\n" +
            "Headers: [Authorization=[Bearer $jwt], apikey=[$jwt], X-Client-Info=[supabase-kt/2.6.1]]"
        val out = SafeLog.redact(msg)
        assertFalse(out.contains(jwt))
        assertTrue(out.contains("permission denied for table outlets"))      // the useful part survives
        assertTrue(out.contains("X-Client-Info=[supabase-kt/2.6.1]"))
    }

    @Test fun `bearer tokens, new-format keys and token fields are redacted`() {
        assertFalse(SafeLog.redact("Authorization: Bearer abc.def-ghi_123").contains("abc.def-ghi_123"))
        assertFalse(SafeLog.redact("key sb_publishable_ABCdef123 used").contains("ABCdef123"))
        assertFalse(SafeLog.redact("""{"access_token":"tok123","refresh_token":"ref456"}""").let { it.contains("tok123") || it.contains("ref456") })
    }

    @Test fun `ordinary messages are untouched`() =
        assertEquals("Failed to load menu for outlet 42", SafeLog.redact("Failed to load menu for outlet 42"))
}
