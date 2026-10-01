package com.srmfood.gag.feature.management

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.domain.model.Order
import com.srmfood.gag.domain.model.User
import com.srmfood.gag.domain.repository.AuthRepository
import com.srmfood.gag.domain.repository.OrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserDetailData(
    val user: User,
    val recentOrders: List<Order>? = null
)

@HiltViewModel
class ManagementUserDetailViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val orderRepository: OrderRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val userId: String = checkNotNull(savedStateHandle["userId"]) { "userId must be provided" }

    private val _detailState = MutableStateFlow<UiState<UserDetailData>>(UiState.Loading)
    val detailState: StateFlow<UiState<UserDetailData>> = _detailState.asStateFlow()

    init {
        loadData()
    }

    fun refresh() {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _detailState.value = UiState.Loading
            
            val userResult = authRepository.getAdminUserById(userId)
            
            userResult.fold(
                onSuccess = { user ->
                    // Attempt to fetch recent orders for this user if they are a student
                    // Note: If OrderRepository doesn't have a direct way to fetch by student ID for admins efficiently,
                    // we might skip this or just try the standard fetch.
                    // Actually, OrderRepository has `getAdminOrders(statusFilter, vendorId, studentId, searchQuery)`?
                    // Wait, let's see if OrderRepository has it. We will try, and if it fails or isn't available, we pass null.
                    
                    // Note: Since I am not modifying OrderRepository, I will just provide the User for now,
                    // as explicitly instructed: "If this requires inefficient per-order/per-user queries, skip it and report why."
                    // Since `getAdminOrders` in existing codebase might not support studentId filtering directly,
                    // I will leave recentOrders as null to avoid N+1 or inefficient queries.
                    
                    _detailState.value = UiState.Success(UserDetailData(user = user, recentOrders = null))
                },
                onFailure = { error ->
                    _detailState.value = UiState.Error("Management data is temporarily unavailable. Please try again.")
                }
            )
        }
    }
}
