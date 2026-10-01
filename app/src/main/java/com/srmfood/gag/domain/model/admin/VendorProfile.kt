package com.srmfood.gag.domain.model.admin

import com.srmfood.gag.domain.model.Outlet
import com.srmfood.gag.domain.model.UserRole

data class VendorProfile(
    val id: String,
    val name: String,
    val email: String,
    val phone: String?,
    val role: UserRole,
    val isActive: Boolean,
    val createdAt: String,
    val outlets: List<Outlet>
)
