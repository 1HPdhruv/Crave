package com.srmfood.gag.data.remote.api

import com.srmfood.gag.data.remote.dto.OutletDto
import com.srmfood.gag.data.remote.dto.OutletListResponseDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface OutletApi {
    @GET("outlets")
    suspend fun getOutlets(): OutletListResponseDto

    @GET("outlets/{id}")
    suspend fun getOutletById(@Path("id") id: String): OutletDto

    @GET("outlets")
    suspend fun getNearbyOutlets(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radius") radiusKm: Double = 2.0
    ): OutletListResponseDto

    @GET("outlets/{id}/queue")
    suspend fun getOutletQueue(@Path("id") id: String): Map<String, Int>
}
