package com.srmfood.gag.data.remote.api

import com.srmfood.gag.data.remote.dto.AuthResponseDto
import com.srmfood.gag.data.remote.dto.FcmTokenRequestDto
import com.srmfood.gag.data.remote.dto.LoginRequestDto
import com.srmfood.gag.data.remote.dto.RefreshTokenRequestDto
import com.srmfood.gag.data.remote.dto.RegisterRequestDto
import com.srmfood.gag.data.remote.dto.UserDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequestDto): AuthResponseDto

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequestDto): AuthResponseDto

    @POST("auth/refresh")
    suspend fun refresh(@Body request: RefreshTokenRequestDto): AuthResponseDto

    @POST("auth/logout")
    suspend fun logout()

    @GET("auth/me")
    suspend fun getMe(): UserDto

    @PUT("auth/fcm-token")
    suspend fun updateFcmToken(@Body request: FcmTokenRequestDto)
}
