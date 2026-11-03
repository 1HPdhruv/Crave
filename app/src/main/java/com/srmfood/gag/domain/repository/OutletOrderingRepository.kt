package com.srmfood.gag.domain.repository

import com.srmfood.gag.domain.model.OutletManagementActionRecord
import com.srmfood.gag.domain.model.OutletOrderingAction
import com.srmfood.gag.domain.model.OutletOrderingStatus

/** Flag / suspend / deactivate / reactivate an outlet. Authorization is enforced in the database (ADMIN only). */
interface OutletOrderingRepository {
    suspend fun setOrderingStatus(
        outletId: String,
        action: OutletOrderingAction,
        reason: String,
        confirmName: String?,
        supportTicketId: String? = null
    ): Result<OutletOrderingStatus>

    suspend fun getActionHistory(outletId: String): Result<List<OutletManagementActionRecord>>
}
