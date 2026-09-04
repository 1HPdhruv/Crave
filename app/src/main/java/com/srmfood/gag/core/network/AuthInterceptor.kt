package com.srmfood.gag.core.network

import com.srmfood.gag.core.security.TokenManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * OkHttp interceptor that attaches Bearer token to all requests,
 * and handles 401 token refresh flow.
 *
 * Security: Token is retrieved from EncryptedSharedPreferences via TokenManager.
 * Never stored in memory longer than necessary.
 */
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Skip auth header for public endpoints
        val skipAuth = originalRequest.url.encodedPath.contains("auth/login") ||
                originalRequest.url.encodedPath.contains("auth/register")

        if (skipAuth) return chain.proceed(originalRequest)

        val accessToken = tokenManager.getAccessToken()
        val request = if (accessToken != null) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $accessToken")
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .build()
        } else {
            originalRequest
        }

        val response = chain.proceed(request)

        // Handle 401 – token expired
        if (response.code == 401 && accessToken != null) {
            response.close()
            // Try refreshing the token
            val newToken = tokenManager.refreshTokenSync()
            if (newToken != null) {
                val retryRequest = originalRequest.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()
                return chain.proceed(retryRequest)
            } else {
                // Refresh failed – force logout
                tokenManager.clearTokens()
                // The ViewModel/NavGraph will react to token being null
            }
        }

        return response
    }
}
