package com.srmfood.gag.core

import com.srmfood.gag.data.repository.supabase.authErrorMessage
import io.github.jan.supabase.gotrue.exception.AuthRestException
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthErrorMessageTest {
    // Same shape GoTrue v2.197 returned for staging: HTTP 400 {"error_code":"invalid_credentials","msg":"Invalid login credentials"}
    private fun err(code: String, msg: String, status: Int) = AuthRestException(code, msg, status)

    @Test fun `the real staging login failure is readable`() =
        assertEquals("Invalid email or password.", authErrorMessage(err("invalid_credentials", "Invalid login credentials", 400)))

    @Test fun `known GoTrue codes map to clear messages`() {
        assertEquals("Please verify your email before logging in. Check your inbox.", authErrorMessage(err("email_not_confirmed", "Email not confirmed", 400)))
        assertEquals("An account with this email already exists.", authErrorMessage(err("user_already_exists", "User already registered", 422)))
        assertEquals("Too many attempts. Please wait a moment and try again.", authErrorMessage(err("over_request_rate_limit", "rate limit", 429)))
    }

    @Test fun `unknown codes and server errors never show raw codes`() {
        assertEquals("Sign-in failed. Please try again.", authErrorMessage(err("some_new_code", "x", 400)))
        assertEquals("CRAVE's sign-in service is unavailable right now. Please try again shortly.", authErrorMessage(err("unexpected_failure", "x", 500)))
    }
}
