package com.srmfood.gag.domain.repository

import com.srmfood.gag.domain.model.admin.VendorProfile

interface ManagementVendorRepository {
    suspend fun getVendors(
        roleFilter: String? = null,
        searchQuery: String? = null
    ): Result<List<VendorProfile>>
    
    suspend fun getVendorById(vendorId: String): Result<VendorProfile>
}
