package com.srmfood.gag.domain.usecase.outlet

import com.srmfood.gag.domain.model.Outlet
import com.srmfood.gag.domain.repository.OutletRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetOutletsUseCase @Inject constructor(
    private val outletRepository: OutletRepository
) {
    operator fun invoke(): Flow<List<Outlet>> = outletRepository.getOutlets()
}

class RefreshOutletsUseCase @Inject constructor(
    private val outletRepository: OutletRepository
) {
    suspend operator fun invoke(): Result<List<Outlet>> = outletRepository.refreshOutlets()
}

class GetOutletDetailsUseCase @Inject constructor(
    private val outletRepository: OutletRepository
) {
    suspend operator fun invoke(outletId: String): Result<Outlet> =
        outletRepository.getOutletById(outletId)
}
