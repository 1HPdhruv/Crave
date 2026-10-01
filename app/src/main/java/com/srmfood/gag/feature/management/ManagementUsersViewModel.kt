package com.srmfood.gag.feature.management

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.domain.model.User
import com.srmfood.gag.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ManagementUsersViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _usersState = MutableStateFlow<UiState<List<User>>>(UiState.Loading)
    val usersState: StateFlow<UiState<List<User>>> = _usersState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _currentFilter = MutableStateFlow("ALL")
    val currentFilter: StateFlow<String> = _currentFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        loadUsers()
    }

    private fun loadUsers() {
        viewModelScope.launch {
            _usersState.value = UiState.Loading
            fetchUsers()
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            fetchUsers()
            _isRefreshing.value = false
        }
    }

    fun setFilter(filter: String) {
        if (_currentFilter.value == filter) return
        _currentFilter.value = filter
        loadUsers()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        loadUsers()
    }

    private suspend fun fetchUsers() {
        val roleFilter = if (_currentFilter.value == "ALL") null else _currentFilter.value
        val search = _searchQuery.value.ifBlank { null }
        
        authRepository.getAdminUsers(roleFilter = roleFilter, searchQuery = search)
            .onSuccess { users ->
                _usersState.value = UiState.Success(users)
            }
            .onFailure { error ->
                _usersState.value = UiState.Error("Management data is temporarily unavailable. Please try again.")
            }
    }
}
