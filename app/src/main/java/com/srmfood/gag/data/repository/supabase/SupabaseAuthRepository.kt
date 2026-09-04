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
    private val tokenManager: TokenManager
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
            val errorBody = e.error.lowercase()
            when {
                errorBody.contains("invalid login credentials") ->
                    throw Exception("Invalid email or password.")
                errorBody.contains("email not confirmed") ->
                    throw Exception("Please verify your email before logging in. Check your inbox.")
                errorBody.contains("too many requests") ->
                    throw Exception("Too many login attempts. Please wait a moment and try again.")
                else -> throw Exception(e.error)
            }
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
            Log.w(TAG, "PROFILE_NOT_FOUND (query error: ${e.message})")
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

            val dto = NewProfileDto(
                id   = userId,
                name = nameFromMeta ?: email.substringBefore("@"),
                email = email,
                phone = phoneFromMeta,
                role  = "STUDENT",
                registration_number = regNoFromMeta,
                is_active = true
            )

            try {
                Log.i(TAG, "PROFILE_INSERT_STARTED")
                postgrest["profiles"].insert(dto)
                Log.i(TAG, "PROFILE_INSERT_SUCCESS")
            } catch (e: Exception) {
                val errorCode = if (e is RestException) e.error else e.javaClass.simpleName
                Log.e(TAG, "PROFILE_INSERT_FAILED: $errorCode - ${e.message}")
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
        registrationNumber: String?
    ): Result<AuthResult> = runCatching {
        Log.d(TAG, "Registration started")
        try {
            auth.signUpWith(Email) {
                this.email = email
                this.password = password
                // Store metadata so login() can use it to create the profile later
                this.data = buildJsonObject {
                    put("name", name)
                    phone?.let { put("phone", it) }
                    registrationNumber?.let { put("registration_number", it) }
                }
            }
        } catch (e: RestException) {
            val errorBody = e.error.lowercase()
            when {
                errorBody.contains("already registered") || errorBody.contains("user already registered") ->
                    throw Exception("An account with this email already exists.")
                else -> throw Exception(e.error)
            }
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
        runCatching { auth.signOut() }
        tokenManager.clearTokens()
        userDao.clearAll()
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
        
        if (auth.currentSessionOrNull() != null) return true

        val accessToken = tokenManager.getAccessToken()
        val refreshToken = tokenManager.getRefreshToken()
        
        if (accessToken != null && refreshToken != null) {
            try {
                auth.importAuthToken(accessToken, refreshToken)
                return true
            } catch (e: Exception) {
                tokenManager.clearTokens()
                return false
            }
        }
        
        return false
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
}
