package com.srmfood.gag.feature.reviews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.domain.repository.AuthRepository
import com.srmfood.gag.domain.repository.ReviewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewComposerState(
    val orderId: String = "",
    val orderItemId: String = "",
    val foodItemId: String = "",
    val existingReviewId: String? = null,
    val rating: Int = 0,
    val reviewText: String = "",
    val isSubmitting: Boolean = false,
    val isComplete: Boolean = false,
    val error: String? = null
)

sealed class ReviewEvent {
    data class ShowSnackbar(val message: String) : ReviewEvent()
    object ReviewSubmitted : ReviewEvent()
}

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val reviewRepository: ReviewRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ReviewComposerState())
    val state: StateFlow<ReviewComposerState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ReviewEvent>()
    val events: SharedFlow<ReviewEvent> = _events.asSharedFlow()

    fun initialize(orderId: String, orderItemId: String, foodItemId: String, reviewId: String?, rating: Int?, text: String?) {
        _state.value = ReviewComposerState(
            orderId = orderId,
            orderItemId = orderItemId,
            foodItemId = foodItemId,
            existingReviewId = reviewId,
            rating = rating ?: 0,
            reviewText = text ?: ""
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

            val studentId = authRepository.getCurrentUser().firstOrNull()?.id

            if (studentId == null) {
                _state.update { it.copy(isSubmitting = false, error = "You must be logged in to review") }
                return@launch
            }

            val result = if (currentState.existingReviewId != null) {
                reviewRepository.updateReview(
                    reviewId = currentState.existingReviewId,
                    rating = currentState.rating,
                    reviewText = currentState.reviewText.takeIf { it.isNotBlank() }
                )
            } else {
                reviewRepository.createReview(
                    studentId = studentId,
                    foodItemId = currentState.foodItemId,
                    orderId = currentState.orderId,
                    orderItemId = currentState.orderItemId,
                    rating = currentState.rating,
                    reviewText = currentState.reviewText.takeIf { it.isNotBlank() }
                )
            }

            result.fold(
                onSuccess = {
                    _state.update { it.copy(isSubmitting = false, isComplete = true) }
                    _events.emit(ReviewEvent.ReviewSubmitted)
                },
                onFailure = { e ->
                    // We don't expose raw backend errors.
                    _state.update { 
                        it.copy(
                            isSubmitting = false, 
                            error = if (currentState.existingReviewId != null) "Unable to update your review." else "Unable to submit your review."
                        ) 
                    }
                    android.util.Log.e("ReviewViewModel", "Submit review failed", e)
                }
            )
        }
    }
}
