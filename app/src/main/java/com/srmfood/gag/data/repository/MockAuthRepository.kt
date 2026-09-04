package com.srmfood.gag.data.repository

import com.srmfood.gag.core.network.AuthInterceptor
import com.srmfood.gag.data.local.dao.UserDao
import com.srmfood.gag.data.mapper.toDomain
import com.srmfood.gag.data.mapper.toEntity
import com.srmfood.gag.data.remote.api.AuthApi
import com.srmfood.gag.data.remote.dto.FcmTokenRequestDto
import com.srmfood.gag.data.remote.dto.LoginRequestDto
import com.srmfood.gag.data.remote.dto.RegisterRequestDto
import com.srmfood.gag.domain.model.User
import com.srmfood.gag.domain.model.UserRole
import com.srmfood.gag.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockAuthRepository @Inject constructor(
    private val api: AuthApi,
    private val userDao: UserDao,
    private val tokenManager: com.srmfood.gag.core.security.TokenManager
) : AuthRepository {

    override suspend fun login(email: String, password: String): Result<User> = runCatching {
        val response = api.login(LoginRequestDto(email, password))
        tokenManager.saveTokens(response.accessToken, response.refreshToken)
        // Store refresh token somewhere secure ideally, keeping it simple here
        
        val user = response.user.toDomain()
        userDao.insertUser(response.user.toEntity())
        user
    }

    override suspend fun register(
        name: String,
        email: String,
        password: String,
        phone: String?,
        registrationNumber: String?
    ): Result<com.srmfood.gag.domain.repository.AuthResult> = runCatching {
        val response = api.register(RegisterRequestDto(name, email, password, phone, registrationNumber))
        tokenManager.saveTokens(response.accessToken, response.refreshToken)
        
        val user = response.user.toDomain()
        userDao.insertUser(response.user.toEntity())
        com.srmfood.gag.domain.repository.AuthResult.Success(user)
    }

    override suspend fun logout() {
        runCatching { api.logout() }
        tokenManager.clearTokens()
        userDao.clearAll()
    }

    override fun getCurrentUser(): Flow<User?> {
        return userDao.observeCurrentUser().map { it?.toDomain() }
    }

    override suspend fun isLoggedIn(): Boolean {
        return tokenManager.isLoggedIn()
    }

    override suspend fun getUserRole(): UserRole? {
        val userEntity = userDao.observeCurrentUser().firstOrNull()
        return userEntity?.let { UserRole.fromString(it.role) }
    }

    override suspend fun updateFcmToken(token: String): Result<Unit> = runCatching {
        api.updateFcmToken(FcmTokenRequestDto(token))
    }

    override suspend fun updateProfile(name: String, phone: String?, registrationNumber: String?): Result<User> = runCatching {
        val currentUserEntity = userDao.observeCurrentUser().firstOrNull() 
            ?: throw Exception("No authenticated user found in mock database")
        
        val updatedEntity = currentUserEntity.copy(
            name = name,
            phone = phone,
            registrationNumber = registrationNumber
        )
        
        userDao.insertUser(updatedEntity)
        updatedEntity.toDomain()
    }
}
