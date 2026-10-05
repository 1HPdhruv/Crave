package com.srmfood.gag.feature.management

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.domain.model.FoodReview
import com.srmfood.gag.domain.repository.ReviewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.srmfood.gag.domain.model.OutletReview

enum class ManagementReviewTab { FOOD, OUTLET }

data class ManagementReviewsUiState(
    val reviewsState: UiState<List<FoodReview>> = UiState.Loading,
    val outletReviewsState: UiState<List<OutletReview>> = UiState.Loading,
    val selectedTab: ManagementReviewTab = ManagementReviewTab.FOOD,
    val searchQuery: String = "",
    val ratingFilter: Int? = null,
    val totalReviews: Int = 0,
    val averageRating: Double = 0.0,
    val total5Star: Int = 0,
    val total4Star: Int = 0,
    val total3Star: Int = 0,
    val total2Star: Int = 0,
    val total1Star: Int = 0,
    val isTogglingVisibilityFor: String? = null
)

@HiltViewModel
class ManagementReviewsViewModel @Inject constructor(
    private val reviewRepository: ReviewRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManagementReviewsUiState())
    val uiState: StateFlow<ManagementReviewsUiState> = _uiState.asStateFlow()

    private var allReviews: List<FoodReview> = emptyList()
    private var allOutletReviews: List<OutletReview> = emptyList()

    init {
        loadReviews()
    }

    fun setTab(tab: ManagementReviewTab) {
        if (_uiState.value.selectedTab == tab) return
        _uiState.update { it.copy(selectedTab = tab) }
        applyFilters()
        if (tab == ManagementReviewTab.FOOD) {
            calculateMetrics(allReviews.map { it.rating })
        } else {
            calculateMetrics(allOutletReviews.map { it.rating })
        }
    }

    fun loadReviews() {
        viewModelScope.launch {
            _uiState.update { it.copy(reviewsState = UiState.Loading, outletReviewsState = UiState.Loading) }
            
            // Limit 200 for management oversight pagination in V1
            reviewRepository.getManagementReviews(limit = 200, offset = 0).fold(
                onSuccess = { page ->
                    allReviews = page.reviews
                    if (_uiState.value.selectedTab == ManagementReviewTab.FOOD) {
                        calculateMetrics(page.reviews.map { it.rating })
                    }
                    applyFilters()
                },
                onFailure = { e ->
                    _uiState.update { it.copy(reviewsState = UiState.Error(e.message ?: "Failed to load management food reviews")) }
                }
            )

            reviewRepository.getManagementOutletReviews(limit = 200, offset = 0).fold(
                onSuccess = { page ->
                    allOutletReviews = page.reviews
                    if (_uiState.value.selectedTab == ManagementReviewTab.OUTLET) {
                        calculateMetrics(page.reviews.map { it.rating })
                    }
                    applyFilters()
                },
                onFailure = { e ->
                    _uiState.update { it.copy(outletReviewsState = UiState.Error(e.message ?: "Failed to load management outlet reviews")) }
                }
            )
        }
    }

    private fun calculateMetrics(ratings: List<Int>) {
        if (ratings.isEmpty()) {
            _uiState.update { it.copy(
                totalReviews = 0, averageRating = 0.0,
                total5Star = 0, total4Star = 0, total3Star = 0, total2Star = 0, total1Star = 0
            ) }
            return
        }
        val avg = ratings.average()
        _uiState.update { it.copy(
            totalReviews = ratings.size,
            averageRating = avg,
            total5Star = ratings.count { r -> r == 5 },
            total4Star = ratings.count { r -> r == 4 },
            total3Star = ratings.count { r -> r == 3 },
            total2Star = ratings.count { r -> r == 2 },
            total1Star = ratings.count { r -> r == 1 }
        ) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilters()
    }

    fun updateRatingFilter(rating: Int?) {
        _uiState.update { it.copy(ratingFilter = rating) }
        applyFilters()
    }

    private fun applyFilters() {
        val state = _uiState.value
        val query = state.searchQuery.lowercase()
        
        if (state.selectedTab == ManagementReviewTab.FOOD) {
            val filtered = allReviews.filter { review ->
                val matchesSearch = state.searchQuery.isBlank() || 
                    (review.foodName?.lowercase()?.contains(query) == true) ||
                    (review.studentName.lowercase().contains(query)) ||
                    (review.reviewText?.lowercase()?.contains(query) == true)
                    
                val matchesRating = state.ratingFilter == null || review.rating == state.ratingFilter
                matchesSearch && matchesRating
            }
            _uiState.update { 
                it.copy(
                    reviewsState = if (filtered.isEmpty() && state.searchQuery.isBlank() && state.ratingFilter == null) {
                        UiState.Empty
                    } else {
                        UiState.Success(filtered)
                    }
                )
            }
        } else {
            val filtered = allOutletReviews.filter { review ->
                val matchesSearch = state.searchQuery.isBlank() || 
                    (review.outletName?.lowercase()?.contains(query) == true) ||
                    (review.studentName.lowercase().contains(query)) ||
                    (review.reviewText?.lowercase()?.contains(query) == true)
                    
                val matchesRating = state.ratingFilter == null || review.rating == state.ratingFilter
                matchesSearch && matchesRating
            }
            _uiState.update { 
                it.copy(
                    outletReviewsState = if (filtered.isEmpty() && state.searchQuery.isBlank() && state.ratingFilter == null) {
                        UiState.Empty
                    } else {
                        UiState.Success(filtered)
                    }
                )
            }
        }
    }

    fun toggleVisibility(reviewId: String, currentVisibility: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTogglingVisibilityFor = reviewId) }
            
            val newVisibility = !currentVisibility
            if (_uiState.value.selectedTab == ManagementReviewTab.FOOD) {
                reviewRepository.setReviewVisibility(reviewId, newVisibility).fold(
                    onSuccess = {
                        allReviews = allReviews.map { 
                            if (it.id == reviewId) it.copy(isVisible = newVisibility) else it 
                        }
                        applyFilters()
                    },
                    onFailure = { e -> }
                )
            } else {
                reviewRepository.setOutletReviewVisibility(reviewId, newVisibility).fold(
                    onSuccess = {
                        allOutletReviews = allOutletReviews.map { 
                            if (it.id == reviewId) it.copy(isVisible = newVisibility) else it 
                        }
                        applyFilters()
                    },
                    onFailure = { e -> }
                )
            }
            
            _uiState.update { it.copy(isTogglingVisibilityFor = null) }
        }
    }
}
