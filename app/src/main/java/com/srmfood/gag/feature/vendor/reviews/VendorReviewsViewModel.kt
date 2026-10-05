package com.srmfood.gag.feature.vendor.reviews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.core.common.UiState
import com.srmfood.gag.domain.model.FoodReview
import com.srmfood.gag.domain.repository.AuthRepository
import com.srmfood.gag.domain.repository.ReviewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.srmfood.gag.domain.model.OutletReview

enum class VendorReviewTab { FOOD, OUTLET }

data class VendorReviewsUiState(
    val reviewsState: UiState<List<FoodReview>> = UiState.Loading,
    val outletReviewsState: UiState<List<OutletReview>> = UiState.Loading,
    val selectedTab: VendorReviewTab = VendorReviewTab.FOOD,
    val searchQuery: String = "",
    val ratingFilter: Int? = null,
    val isReplyingTo: FoodReview? = null,
    val isReplyingToOutletReview: OutletReview? = null,
    val replyText: String = "",
    val isSubmittingReply: Boolean = false,
    val replyError: String? = null,
    val vendorId: String? = null
)

@HiltViewModel
class VendorReviewsViewModel @Inject constructor(
    private val reviewRepository: ReviewRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VendorReviewsUiState())
    val uiState: StateFlow<VendorReviewsUiState> = _uiState.asStateFlow()

    private var allReviews: List<FoodReview> = emptyList()
    private var allOutletReviews: List<OutletReview> = emptyList()

    init {
        loadReviews()
    }

    fun setTab(tab: VendorReviewTab) {
        if (_uiState.value.selectedTab == tab) return
        _uiState.update { it.copy(selectedTab = tab) }
        applyFilters()
    }

    fun loadReviews() {
        viewModelScope.launch {
            _uiState.update { it.copy(reviewsState = UiState.Loading, outletReviewsState = UiState.Loading) }
            
            val vendorId = authRepository.getCurrentUser().firstOrNull()?.id
            _uiState.update { it.copy(vendorId = vendorId) }
            
            reviewRepository.getVendorReviews(limit = 100, offset = 0).fold(
                onSuccess = { page ->
                    allReviews = page.reviews
                    applyFilters()
                },
                onFailure = { e ->
                    _uiState.update { it.copy(reviewsState = UiState.Error(e.message ?: "Failed to load food reviews")) }
                }
            )

            reviewRepository.getVendorOutletReviews(limit = 100, offset = 0).fold(
                onSuccess = { page ->
                    allOutletReviews = page.reviews
                    applyFilters()
                },
                onFailure = { e ->
                    _uiState.update { it.copy(outletReviewsState = UiState.Error(e.message ?: "Failed to load outlet reviews")) }
                }
            )
        }
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
        if (state.selectedTab == VendorReviewTab.FOOD) {
            val filtered = allReviews.filter { review ->
                val matchesSearch = state.searchQuery.isBlank() || 
                    (review.foodName?.contains(state.searchQuery, ignoreCase = true) == true)
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
                    (review.outletName?.contains(state.searchQuery, ignoreCase = true) == true)
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

    fun openReplyComposer(review: FoodReview) {
        _uiState.update { 
            it.copy(
                isReplyingTo = review,
                isReplyingToOutletReview = null,
                replyText = review.vendorReply?.replyText ?: "",
                replyError = null
            )
        }
    }

    fun openOutletReplyComposer(review: OutletReview) {
        _uiState.update { 
            it.copy(
                isReplyingTo = null,
                isReplyingToOutletReview = review,
                replyText = review.vendorReply?.replyText ?: "",
                replyError = null
            )
        }
    }

    fun closeReplyComposer() {
        _uiState.update { 
            it.copy(
                isReplyingTo = null,
                isReplyingToOutletReview = null,
                replyText = "",
                replyError = null
            )
        }
    }

    fun updateReplyText(text: String) {
        if (text.length <= 1000) {
            _uiState.update { it.copy(replyText = text, replyError = null) }
        }
    }

    fun submitReply() {
        val state = _uiState.value
        val foodReview = state.isReplyingTo
        val outletReview = state.isReplyingToOutletReview
        
        if (foodReview == null && outletReview == null) return
        
        val text = state.replyText.trim()
        val vendorId = state.vendorId
        
        if (text.isBlank()) {
            _uiState.update { it.copy(replyError = "Reply text cannot be empty") }
            return
        }
        if (vendorId == null) {
            _uiState.update { it.copy(replyError = "Not authenticated") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingReply = true, replyError = null) }
            
            if (foodReview != null) {
                val result = if (foodReview.vendorReply != null) {
                    reviewRepository.updateVendorReply(replyId = foodReview.vendorReply.id, replyText = text)
                } else {
                    reviewRepository.createVendorReply(reviewId = foodReview.id, vendorId = vendorId, replyText = text)
                }
                result.fold(
                    onSuccess = { reply ->
                        allReviews = allReviews.map { if (it.id == foodReview.id) it.copy(vendorReply = reply) else it }
                        _uiState.update { it.copy(isSubmittingReply = false) }
                        applyFilters()
                        closeReplyComposer()
                    },
                    onFailure = { e ->
                        _uiState.update { it.copy(isSubmittingReply = false, replyError = e.message ?: "Failed to submit reply") }
                    }
                )
            } else if (outletReview != null) {
                val result = if (outletReview.vendorReply != null) {
                    reviewRepository.updateOutletVendorReply(replyId = outletReview.vendorReply.id, replyText = text)
                } else {
                    reviewRepository.submitOutletVendorReply(reviewId = outletReview.id, replyText = text)
                }
                result.fold(
                    onSuccess = { reply ->
                        allOutletReviews = allOutletReviews.map { if (it.id == outletReview.id) it.copy(vendorReply = reply) else it }
                        _uiState.update { it.copy(isSubmittingReply = false) }
                        applyFilters()
                        closeReplyComposer()
                    },
                    onFailure = { e ->
                        _uiState.update { it.copy(isSubmittingReply = false, replyError = e.message ?: "Failed to submit reply") }
                    }
                )
            }
        }
    }

    fun deleteReply(review: FoodReview) {
        val replyId = review.vendorReply?.id ?: return
        
        viewModelScope.launch {
            reviewRepository.deleteVendorReply(replyId).fold(
                onSuccess = {
                    allReviews = allReviews.map {
                        if (it.id == review.id) it.copy(vendorReply = null) else it
                    }
                    applyFilters()
                },
                onFailure = { e -> }
            )
        }
    }

    fun deleteOutletReply(review: OutletReview) {
        val replyId = review.vendorReply?.id ?: return
        
        viewModelScope.launch {
            reviewRepository.deleteOutletVendorReply(replyId).fold(
                onSuccess = {
                    allOutletReviews = allOutletReviews.map {
                        if (it.id == review.id) it.copy(vendorReply = null) else it
                    }
                    applyFilters()
                },
                onFailure = { e -> }
            )
        }
    }
}
