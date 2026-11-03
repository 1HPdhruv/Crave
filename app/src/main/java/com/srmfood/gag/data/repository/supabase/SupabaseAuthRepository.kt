package com.srmfood.gag.data.repository.supabase

import android.util.Log
import com.srmfood.gag.core.security.TokenManager
import com.srmfood.gag.data.local.dao.UserDao
import com.srmfood.gag.data.local.entity.UserEntity
import com.srmfood.gag.domain.model.User
import com.srmfood.gag.domain.model.UserRole
import com.srmfood.gag.domain.repository.AuthRepository
import com.srmfood.gag.domain.repository.AuthResult
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.exceptions.HttpRequestException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ProfileDto(
    val id: String,
    val name: String,
    val email: String,
    val phone: String? = null,
    val role: String,
    val profile_image_url: String? = null,
    val registration_number: String? = null,
    val is_active: Boolean = true,
    val created_at: String
)

/** Used only for inserting a new profile row. Omits server-generated fields (created_at, etc.). */
@Serializable
data class NewProfileDto(
    val id: String,
    val name: String,
    val email: String,
    val phone: String? = null,
    val role: String = "STUDENT",
    val registration_number: String? = null,
    val is_active: Boolean = true
)

@Singleton
class SupabaseAuthRepository @Inject constructor(
    private val auth: Auth,
    private val postgrest: Postgrest,
    private val userDao: UserDao,
    private val orderDao: com.srmfood.gag.data.local.dao.OrderDao,
    private val cartDao: com.srmfood.gag.data.local.dao.CartDao,
    private val foodDao: com.srmfood.gag.data.local.dao.FoodItemDao,
    private val tokenManager: TokenManager,
    private val pushTokens: com.srmfood.gag.domain.repository.PushTokenRepository
) : AuthRepository {

    private val TAG = "SupabaseAuthRepository"

    override suspend fun login(email: String, password: String): Result<User> = runCatching {
        Log.i(TAG, "Login started")

        // ── Step 1: Authenticate ──────────────────────────────────────────────────
        try {
            auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
        } catch (e: RestException) {
            throw Exception(authErrorMessage(e))
        } catch (e: HttpRequestTimeoutException) {
            throw Exception("Network timeout. Please check your connection.")
        } catch (e: HttpRequestException) {
            throw Exception("Network error. Please check your connection.")
        }

        // ── Step 2: Verify session ────────────────────────────────────────────────
        val userSession = auth.currentSessionOrNull()
            ?: throw Exception("Login failed — no session returned. Please try again.")
        Log.i(TAG, "AUTH_SUCCESS")
        tokenManager.saveTokens(userSession.accessToken, userSession.refreshToken ?: "")

        // ── Step 3: Get authenticated user ID ────────────────────────────────────
        val userId = userSession.user?.id?.toString()
            ?: throw Exception("Session is missing user ID.")
        Log.i(TAG, "AUTH_USER_ID: $userId")

        // ── Step 4: Query profiles ────────────────────────────────────────────────
        var profile = try {
            Log.i(TAG, "PROFILE_QUERY_STARTED")
            postgrest["profiles"].select {
                filter { eq("id", userId) }
            }.decodeSingleOrNull<ProfileDto>()
        } catch (e: Exception) {
            com.srmfood.gag.core.common.SafeLog.w(TAG, "PROFILE_NOT_FOUND (query error: ${e.message})")
            null
        }

        // ── Steps 5–8: Create profile on first verified login ─────────────────────
        if (profile != null) {
            Log.i(TAG, "PROFILE_FOUND")
        } else {
            Log.i(TAG, "PROFILE_NOT_FOUND")

            val userMeta = userSession.user?.userMetadata
            // Supabase wraps JSON primitives in quotes; trim them safely
            fun JsonElement?.safeString(): String? =
                this?.toString()?.trim('"')?.ifBlank { null }

            val nameFromMeta  = userMeta?.get("name").safeString()
            val phoneFromMeta = userMeta?.get("phone").safeString()
            val regNoFromMeta = userMeta?.get("registration_number").safeString()
            // Read the role intent stored during registration.
            // Map VENDOR -> PENDING_VENDOR so active vendor privileges
            // are never granted without explicit admin approval.
            val roleFromMeta = when (userMeta?.get("requested_role").safeString()?.uppercase()) {
                "VENDOR" -> "PENDING_VENDOR"
                "STUDENT" -> "STUDENT"
                else -> "STUDENT"
            }

            val dto = NewProfileDto(
                id   = userId,
                name = nameFromMeta ?: email.substringBefore("@"),
                email = email,
                phone = phoneFromMeta,
                role  = roleFromMeta,
                registration_number = regNoFromMeta,
                is_active = true
            )

            try {
                Log.i(TAG, "PROFILE_INSERT_STARTED")
                postgrest["profiles"].insert(dto)
                Log.i(TAG, "PROFILE_INSERT_SUCCESS")
            } catch (e: Exception) {
                val errorCode = if (e is RestException) e.error else e.javaClass.simpleName
                com.srmfood.gag.core.common.SafeLog.e(TAG, "PROFILE_INSERT_FAILED: $errorCode - ${e.message}")
                throw Exception("Could not create your profile. Ensure the INSERT RLS policy is applied.")
            }

            // ── Step 8: Re-fetch the freshly inserted profile ─────────────────────
            profile = postgrest["profiles"].select {
                filter { eq("id", userId) }
            }.decodeSingleOrNull<ProfileDto>()
                ?: throw Exception("Profile was inserted but could not be retrieved. Please try logging in again.")
        }

        if (!profile.is_active) {
            auth.signOut()
            tokenManager.clearTokens()
            throw Exception("Your account has been disabled. Please contact support.")
        }

        val domainUser = User(
            id = profile.id,
            name = profile.name,
            email = profile.email,
            phone = profile.phone,
            role = UserRole.fromString(profile.role),
            profileImageUrl = profile.profile_image_url,
            registrationNumber = profile.registration_number,
            isActive = profile.is_active,
            createdAt = profile.created_at
        )

        // A different account signing in on the same device must not inherit the previous account's cache.
        val previousUserId = userDao.observeCurrentUser().firstOrNull()?.id
        if (previousUserId != null && previousUserId != domainUser.id) clearPerUserCaches()
        userDao.clearAll()

        userDao.insertUser(UserEntity(
            id = domainUser.id,
            name = domainUser.name,
            email = domainUser.email,
            phone = domainUser.phone,
            role = domainUser.role.name,
            profileImageUrl = domainUser.profileImageUrl,
            registrationNumber = domainUser.registrationNumber,
            isActive = domainUser.isActive,
            createdAt = domainUser.createdAt
        ))

        // ── Step 9: Navigate to Dashboard ─────────────────────────────────────────
        Log.i(TAG, "LOGIN_COMPLETE — role: ${domainUser.role.name}")
        domainUser
    }

    override suspend fun register(
        name: String,
        email: String,
        password: String,
        phone: String?,
        registrationNumber: String?,
        requestedRole: String
    ): Result<AuthResult> = runCatching {
        Log.d(TAG, "Registration started with role: $requestedRole")
        try {
            auth.signUpWith(Email) {
                this.email = email
                this.password = password
                // Store metadata so login() can use it to create the profile later.
                // requested_role is intentionally stored here; the repository
                // maps VENDOR -> PENDING_VENDOR during profile creation.
                this.data = buildJsonObject {
                    put("name", name)
                    put("requested_role", requestedRole)
                    phone?.let { put("phone", it) }
                    registrationNumber?.let { put("registration_number", it) }
                }
            }
        } catch (e: RestException) {
            throw Exception(authErrorMessage(e))
        } catch (e: HttpRequestTimeoutException) {
            throw Exception("Network timeout. Please check your connection.")
        } catch (e: HttpRequestException) {
            throw Exception("Network error. Please check your connection.")
        }

        Log.d(TAG, "Registration succeeded. Session exists: ${auth.currentSessionOrNull() != null}")

        // With email confirmation enabled, Supabase never returns a session here.
        // Profile creation is deferred to the first successful (verified) login.
        AuthResult.EmailConfirmationRequired
    }


    override suspend fun logout() {
        // NonCancellable: navigating away clears the calling ViewModel, which must not abort a half-finished sign-out.
        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
            // Detach this device from the account while we still have a session, so the next user on this phone
            // never receives the previous user's notifications.
            runCatching { pushTokens.unregisterCurrent() }
            runCatching { auth.signOut() }
            tokenManager.clearTokens()
            userDao.clearAll()
            clearPerUserCaches()
        }
    }

    /** Orders, cart rows and favourite flags are cached device-wide; the next account must not see them. */
    private suspend fun clearPerUserCaches() {
        runCatching { orderDao.clearAll() }
        runCatching { cartDao.clearCart() }
        runCatching { foodDao.clearAll() }
    }

    override fun getCurrentUser(): Flow<User?> {
        return userDao.observeCurrentUser().map { entity ->
            entity?.let {
                User(
                    id = it.id,
                    name = it.name,
                    email = it.email,
                    phone = it.phone,
                    role = UserRole.fromString(it.role),
                    profileImageUrl = it.profileImageUrl,
                    registrationNumber = it.registrationNumber,
                    isActive = it.isActive,
                    createdAt = it.createdAt
                )
            }
        }
    }

    override suspend fun isLoggedIn(): Boolean {
        if (!tokenManager.isLoggedIn()) return false

        // 1. Let supabase-kt finish restoring (and refreshing) its own stored session first; racing it would replay
        //    the same refresh token twice.
        val status: io.github.jan.supabase.gotrue.SessionStatus? = runCatching {
            kotlinx.coroutines.withTimeoutOrNull(10_000L) {
                auth.sessionStatus.first { s: io.github.jan.supabase.gotrue.SessionStatus ->
                    s !is io.github.jan.supabase.gotrue.SessionStatus.LoadingFromStorage
                }
            }
        }.getOrNull()
        val restored = auth.currentSessionOrNull()
        if (status is io.github.jan.supabase.gotrue.SessionStatus.Authenticated && restored != null) {
            tokenManager.saveTokens(restored.accessToken, restored.refreshToken)   // keep the rotated pair
            return true
        }

        // 2. No valid restored session: validate the app's stored refresh token WITH THE SERVER. (importAuthToken alone
        //    only stores tokens, so a revoked or other-project token used to count as "logged in": Home then loaded
        //    with every request silently running as the anonymous role.)
        val refreshToken = tokenManager.getRefreshToken()
        if (refreshToken.isNullOrBlank()) { clearLocalSession(); return false }
        return try {
            val fresh = auth.refreshSession(refreshToken)
            auth.importSession(fresh)
            tokenManager.saveTokens(fresh.accessToken, fresh.refreshToken)
            true
        } catch (e: io.github.jan.supabase.exceptions.RestException) {
            if (e.statusCode in 400..499) {
                Log.w(TAG, "Stored session rejected by the server (HTTP ${e.statusCode}); signing out locally")
                clearLocalSession()
                false
            } else keepOfflineSession()                    // server-side failure: keep the user signed in
        } catch (e: Exception) {
            Log.w(TAG, "Session check skipped (${e.javaClass.simpleName}); keeping the saved session")
            keepOfflineSession()                           // offline / timeout: don't log the user out
        }
    }

    /** Offline start: keep using the saved tokens; the next successful request/refresh revalidates them. */
    private suspend fun keepOfflineSession(): Boolean {
        val access = tokenManager.getAccessToken(); val refresh = tokenManager.getRefreshToken()
        if (auth.currentSessionOrNull() == null && access != null && refresh != null) {
            runCatching { auth.importAuthToken(access, refresh) }
        }
        return true
    }

    /** Forget a session the server no longer accepts, without a network call. */
    private suspend fun clearLocalSession() {
        runCatching { auth.clearSession() }
        tokenManager.clearTokens()
        userDao.clearAll()
        clearPerUserCaches()
    }

    override suspend fun getUserRole(): UserRole? {
        val userEntity = userDao.observeCurrentUser().firstOrNull()
        return userEntity?.let { UserRole.fromString(it.role) }
    }

    override suspend fun updateFcmToken(token: String): Result<Unit> = runCatching {
        // Will update in profiles table if needed.
    }

    override suspend fun updateProfile(
        name: String,
        phone: String?,
        registrationNumber: String?
    ): Result<User> = runCatching {
        val userId = auth.currentSessionOrNull()?.user?.id ?: throw Exception("User not logged in")

        // Update profiles table. RLS restricts updates to the user's own row.
        postgrest["profiles"].update({
            set("name", name)
            set("phone", phone)
            set("registration_number", registrationNumber)
            set("updated_at", kotlinx.datetime.Clock.System.now())
        }) {
            filter { eq("id", userId) }
        }

        // Fetch the updated profile and save it to local DB
        val updatedDto = postgrest["profiles"].select {
            filter { eq("id", userId) }
        }.decodeSingle<ProfileDto>()

        val userEntity = UserEntity(
            id = updatedDto.id,
            name = updatedDto.name,
            email = updatedDto.email,
            phone = updatedDto.phone,
            role = updatedDto.role,
            profileImageUrl = updatedDto.profile_image_url,
            registrationNumber = updatedDto.registration_number,
            isActive = updatedDto.is_active,
            createdAt = updatedDto.created_at
        )
        userDao.insertUser(userEntity)

        User(
            id = userEntity.id,
            name = userEntity.name,
            email = userEntity.email,
            phone = userEntity.phone,
            role = UserRole.fromString(userEntity.role),
            profileImageUrl = userEntity.profileImageUrl,
            registrationNumber = userEntity.registrationNumber,
            isActive = userEntity.isActive,
            createdAt = userEntity.createdAt
        )
    }

    override suspend fun getAdminUsers(
        roleFilter: String?,
        searchQuery: String?
    ): Result<List<User>> = runCatching {
        val dtos = postgrest["profiles"].select {
            filter {
                if (roleFilter != null && roleFilter != "ALL") {
                    eq("role", roleFilter.uppercase())
                }
                if (!searchQuery.isNullOrBlank()) {
                    val q = searchQuery.trim()
                    or {
                        ilike("name", "%$q%")
                        ilike("email", "%$q%")
                    }
                }
            }
            order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
        }.decodeList<ProfileDto>()
        
        dtos.map { it.toDomainUser() }
    }

    override suspend fun getAdminUserById(userId: String): Result<User> = runCatching {
        val dto = postgrest["profiles"].select {
            filter { eq("id", userId) }
        }.decodeSingle<ProfileDto>()
        
        dto.toDomainUser()
    }

    private fun ProfileDto.toDomainUser(): User {
        return User(
            id = id,
            name = name,
            email = email,
            phone = phone,
            role = UserRole.fromString(role),
            profileImageUrl = profile_image_url,
            registrationNumber = registration_number,
            isActive = is_active,
            createdAt = created_at
        )
    }
}

/**
 * Maps a Supabase Auth failure to text for the login/registration screens. In supabase-kt 2.x [RestException.error]
 * is GoTrue's machine error CODE (e.g. "invalid_credentials"), not the message text the old checks compared it with,
 * so every failure used to fall through and show the raw code. Never returns the raw code or server text.
 */
internal fun authErrorMessage(e: RestException): String {
    val code = e.error.lowercase()
    val text = (e.message ?: "").lowercase()
    return when {
        code == "invalid_credentials" || "invalid login credentials" in text -> "Invalid email or password."
        code == "email_not_confirmed" || "email not confirmed" in text -> "Please verify your email before logging in. Check your inbox."
        code == "user_already_exists" || code == "email_exists" || "already registered" in text -> "An account with this email already exists."
        code == "weak_password" -> "That password is too weak. Use at least 8 characters with letters and numbers."
        code == "signup_disabled" || code == "email_provider_disabled" -> "New sign-ups are currently disabled."
        code == "user_banned" -> "This account has been disabled. Please contact support."
        code.startsWith("over_") || "too many" in text || e.statusCode == 429 -> "Too many attempts. Please wait a moment and try again."
        e.statusCode in 500..599 -> "CRAVE's sign-in service is unavailable right now. Please try again shortly."
        else -> "Sign-in failed. Please try again."
    }
}
