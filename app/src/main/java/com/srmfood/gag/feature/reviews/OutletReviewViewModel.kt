package com.srmfood.gag.feature.reviews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.domain.repository.ReviewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OutletReviewState(
    val orderId: String = "",
    val outletId: String = "",
    val existingReviewId: String? = null,
    val rating: Int = 0,
    val reviewText: String = "",
    val isSubmitting: Boolean = false,
    val isComplete: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class OutletReviewViewModel @Inject constructor(
    private val reviewRepository: ReviewRepository
) : ViewModel() {

    private val _state = MutableStateFlow(OutletReviewState())
    val state: StateFlow<OutletReviewState> = _state.asStateFlow()

    fun initialize(
        orderId: String,
        outletId: String,
        existingReviewId: String?,
        existingRating: Int?,
        existingText: String?
    ) {
        _state.value = OutletReviewState(
            orderId = orderId,
            outletId = outletId,
            existingReviewId = existingReviewId,
            rating = existingRating ?: 0,
            reviewText = existingText ?: ""
        )
    }

    fun setRating(rating: Int) {
        _state.update { it.copy(rating = rating, error = null) }
    }

    fun setReviewText(text: String) {
        if (text.length <= 2000) {
            _state.update { it.copy(reviewText = text, error = null) }
        }
    }

    fun submitReview() {
        val currentState = _state.value
        if (currentState.rating == 0) {
            _state.update { it.copy(error = "Please select a rating") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }

            val result = if (currentState.existingReviewId != null) {
                reviewRepository.updateOutletReview(
                    reviewId = currentState.existingReviewId,
                    rating = currentState.rating,
                    reviewText = currentState.reviewText.takeIf { it.isNotBlank() }
                )
            } else {
                reviewRepository.submitOutletReview(
                    outletId = currentState.outletId,
                    orderId = currentState.orderId,
                    rating = currentState.rating,
                    reviewText = currentState.reviewText.takeIf { it.isNotBlank() }
                )
            }

            result.fold(
                onSuccess = {
                    _state.update { it.copy(isSubmitting = false, isComplete = true) }
                },
                onFailure = { e ->
                    _state.update { it.copy(isSubmitting = false, error = e.message ?: "Failed to submit outlet review") }
                }
            )
        }
    }
}
