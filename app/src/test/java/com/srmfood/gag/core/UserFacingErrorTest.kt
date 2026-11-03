package com.srmfood.gag.core

import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.feature.home.homeLoadErrorMessage
import org.junit.Assert.*
import org.junit.Test

class UserFacingErrorTest {
    private val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiYXV0aGVudGljYXRlZCJ9.c2lnbmF0dXJlc2lnbmF0dXJl"
    // Exact shape of the supabase-kt message observed on the emulator
    private val supabaseMsg = "permission denied for table outlets (Grant the required privileges to the current role with: GRANT SELECT ON public.outlets TO anon;)\n" +
        "URL: https://yatbgxfjlnqpvrftjdmu.supabase.co/rest/v1/outlets?is_active=eq.true&select=%2A\n" +
        "Headers: [Authorization=[Bearer $jwt], apikey=[$jwt]]\nHttp Method: GET"

    @Test fun `UiState Error never carries request dumps or tokens`() {
        val e = UiState.Error(supabaseMsg)
        assertFalse(e.message.contains(jwt)); assertFalse(e.message.contains("Headers")); assertFalse(e.message.contains("URL:"))
        assertTrue(e.message.startsWith("permission denied for table outlets"))
    }

    @Test fun `ordinary messages are unchanged and blanks get a fallback`() {
        assertEquals("Order not found.", UiState.Error("Order not found.").message)
        assertEquals("Something went wrong. Please try again.", UiState.Error("   ").message)
    }

    @Test fun `Error keeps value equality`() = assertEquals(UiState.Error("x"), UiState.Error("x"))

    @Test fun `home shows friendly text for the real staging failures`() {
        assertEquals("The menu isn't available right now. Please try again later.", homeLoadErrorMessage(Exception(supabaseMsg)))
        assertTrue(homeLoadErrorMessage(Exception("Could not find a relationship between 'food_items' and 'food_variants' in the schema cache")).contains("being updated"))
        assertTrue(homeLoadErrorMessage(java.io.IOException("Unable to resolve host")).contains("connection"))
        assertFalse(homeLoadErrorMessage(Exception(supabaseMsg)).contains(jwt))
    }
}
