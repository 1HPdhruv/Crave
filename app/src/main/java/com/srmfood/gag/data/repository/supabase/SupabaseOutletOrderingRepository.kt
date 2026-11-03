package com.srmfood.gag.data.repository.supabase

import com.srmfood.gag.domain.model.OutletManagementActionRecord
import com.srmfood.gag.domain.model.OutletOrderingAction
import com.srmfood.gag.domain.model.OutletOrderingStatus
import com.srmfood.gag.domain.repository.OutletOrderingRepository
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class SetOutletOrderingParams(
    @SerialName("p_outlet_id") val outletId: String,
    @SerialName("p_action") val action: String,
    @SerialName("p_reason") val reason: String,
    @SerialName("p_confirm_name") val confirmName: String? = null,
    @SerialName("p_support_ticket_id") val supportTicketId: String? = null
)

@Serializable
data class SetOutletOrderingResult(val to: String)

@Serializable
data class OutletActionRowDto(
    val id: Long,
    val action: String,
    @SerialName("from_status") val fromStatus: String,
    @SerialName("to_status") val toStatus: String,
    val reason: String,
    @SerialName("created_at") val createdAt: String
)

/** Needs DRAFT_outlet_suspension.sql deployed; until then calls fail and the UI shows the error. */
@Singleton
class SupabaseOutletOrderingRepository @Inject constructor(
    private val postgrest: Postgrest
) : OutletOrderingRepository {

    override suspend fun setOrderingStatus(
        outletId: String,
        action: OutletOrderingAction,
        reason: String,
        confirmName: String?,
        supportTicketId: String?
    ): Result<OutletOrderingStatus> = runCatching {
        postgrest.rpc(
            function = "admin_set_outlet_ordering_status",
            parameters = SetOutletOrderingParams(outletId, action.name, reason.trim(), confirmName?.trim(), supportTicketId)
        ).decodeAs<SetOutletOrderingResult>().let { OutletOrderingStatus.fromString(it.to) }
    }.onFailure { com.srmfood.gag.core.common.SafeLog.e("OutletOrderingRepo", "setOrderingStatus failed", it) }

    override suspend fun getActionHistory(outletId: String): Result<List<OutletManagementActionRecord>> = runCatching {
        postgrest["outlet_management_actions"]
            .select(Columns.raw("id,action,from_status,to_status,reason,created_at")) {
                filter { eq("outlet_id", outletId) }
                order("created_at", Order.DESCENDING)
                limit(50)
            }
            .decodeList<OutletActionRowDto>()
            .map { OutletManagementActionRecord(it.id, it.action, it.fromStatus, it.toStatus, it.reason, it.createdAt) }
    }
}
