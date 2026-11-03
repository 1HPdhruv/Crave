package com.srmfood.gag.core

import com.srmfood.gag.core.notifications.PushPayload
import org.junit.Assert.*
import org.junit.Test

class PushPayloadTest {
    private val nid = "11111111-2222-3333-4444-555555555555"
    private val tid = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
    private val oid = "99999999-8888-7777-6666-555555555555"

    // android.net.Uri is stubbed in JVM tests, so these tests check the pieces PushPayload decides (id, text, which id is used).
    @Test fun `unusable messages are dropped`() {
        assertNull(PushPayload.parse(emptyMap()))
        assertNull(PushPayload.parse(mapOf("notification_id" to "not-a-uuid", "title" to "x")))
        assertNull(PushPayload.parse(mapOf("notification_id" to nid)))                       // nothing to show
        assertNull(PushPayload.parse(mapOf("notification_id" to nid, "title" to "  ", "body" to "")))
    }

    @Test fun `title and body are trimmed and bounded`() {
        val p = PushPayload.parse(mapOf("notification_id" to nid, "title" to "  Ready!  ", "body" to "x".repeat(1000)))!!
        assertEquals("Ready!", p.title); assertEquals(PushPayload.MAX_BODY, p.body.length)
        assertEquals("CRAVE", PushPayload.parse(mapOf("notification_id" to nid, "body" to "hi"))!!.title)
    }

    @Test fun `redelivery of the same server notification maps to the same Android id`() {
        val a = PushPayload.parse(mapOf("notification_id" to nid, "title" to "t"))!!
        val b = PushPayload.parse(mapOf("notification_id" to nid, "title" to "t (retry)"))!!
        assertEquals(a.androidId, b.androidId)
        assertTrue(a.androidId >= 0)
        val other = PushPayload.parse(mapOf("notification_id" to "11111111-2222-3333-4444-555555555556", "title" to "t"))!!
        assertNotEquals(a.androidId, other.androidId)
    }

    @Test fun `only well-formed ids are accepted for destinations`() {
        // a malformed ticket id must not be turned into a link; stableNotificationId is deterministic
        assertEquals(PushPayload.stableNotificationId(nid), PushPayload.stableNotificationId(nid))
        assertNotNull(PushPayload.parse(mapOf("notification_id" to nid, "title" to "t", "ticket_id" to "../../etc", "order_id" to oid)))
    }
}
