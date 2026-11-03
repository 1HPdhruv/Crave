package com.srmfood.gag.core.common

import android.util.Log

/**
 * Logging for failures that may carry credentials. supabase-kt puts the request headers into its exception
 * messages (`Authorization=[Bearer <access token>]`, `apikey=[…]`), so logging the raw throwable would write a
 * signed-in user's session token to logcat. This redacts those values before anything is logged.
 */
object SafeLog {
    private val patterns = listOf(
        Regex("(Bearer\\s+)[A-Za-z0-9._~+/=-]+"),                       // Authorization header values
        Regex("((?:apikey|Authorization)=\\[)[^\\]]*"),                // header dumps: apikey=[…] / Authorization=[…]
        Regex("eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}"), // any JWT
        Regex("sb_(?:publishable|secret)_[A-Za-z0-9_-]+"),             // new-format keys
        Regex("((?:access_token|refresh_token)\"?\\s*[:=]\\s*\"?)[A-Za-z0-9._-]+")
    )

    fun redact(text: String): String = patterns.fold(text) { acc, r ->
        r.replace(acc) { m -> if (m.groupValues.size > 1 && m.groupValues[1].isNotEmpty()) m.groupValues[1] + "<redacted>" else "<redacted>" }
    }

    private fun describe(t: Throwable?): String {
        if (t == null) return ""
        val trace: String? = Log.getStackTraceString(t)          // platform type: may be null under JVM unit tests
        return "\n" + redact(trace ?: t.toString())
    }

    fun e(tag: String, message: String, t: Throwable? = null) { Log.e(tag, redact(message) + describe(t)) }
    fun w(tag: String, message: String, t: Throwable? = null) { Log.w(tag, redact(message) + describe(t)) }
    fun i(tag: String, message: String) { Log.i(tag, redact(message)) }
    fun d(tag: String, message: String) { Log.d(tag, redact(message)) }
}
