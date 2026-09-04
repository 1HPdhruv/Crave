package com.srmfood.gag.data.repository

import com.srmfood.gag.domain.repository.AdminRepository
import com.srmfood.gag.domain.usecase.admin.SystemStats
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ToggleOutletStatusParams(
    @SerialName("p_outlet_id") val outletId: String,
    @SerialName("p_is_open") val isOpen: Boolean
)

@Singleton
class AdminRepositoryImpl @Inject constructor(
    private val postgrest: Postgrest,
    private val auth: Auth
) : AdminRepository {

    override suspend fun getSystemStats(): Result<SystemStats> = runCatching {
        if (auth.currentSessionOrNull() == null) throw Exception("User not logged in")
        postgrest.rpc("get_admin_stats").decodeAs<SystemStats>()
    }

    override suspend fun toggleOutletStatus(outletId: String, isOpen: Boolean): Result<Unit> = runCatching {
        if (auth.currentSessionOrNull() == null) throw Exception("User not logged in")
        postgrest.rpc(
            function = "admin_toggle_outlet_status",
            parameters = ToggleOutletStatusParams(outletId, isOpen)
        )
    }
}
