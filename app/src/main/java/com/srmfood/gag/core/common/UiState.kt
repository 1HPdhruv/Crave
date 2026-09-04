package com.srmfood.gag.core.common

/**
 * Sealed class representing the state of any UI screen or data operation.
 * All ViewModels should use UiState<T> as their primary state holder.
 */
sealed class UiState<out T> {
    /** Initial state before any data loading has been triggered. */
    object Idle : UiState<Nothing>()

    /** Data is being loaded. */
    object Loading : UiState<Nothing>()

    /** Data loaded successfully. */
    data class Success<T>(val data: T) : UiState<T>()

    /** No data found. */
    object Empty : UiState<Nothing>()

    /** An error occurred. */
    data class Error(
        val message: String,
        val errorType: ErrorType = ErrorType.GENERIC,
        val cause: Throwable? = null
    ) : UiState<Nothing>()
}

enum class ErrorType {
    GENERIC,
    NETWORK,
    TIMEOUT,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    SERVER,
    VALIDATION,
    OUTLET_CLOSED,
    FOOD_UNAVAILABLE,
    SLOT_FULL,
    PAYMENT_FAILED,
    ORDER_REJECTED,
    ORDER_CANCELLED
}

/** Extension to simplify success data extraction */
fun <T> UiState<T>.dataOrNull(): T? = (this as? UiState.Success)?.data

fun <T> UiState<T>.isLoading() = this is UiState.Loading
fun <T> UiState<T>.isSuccess() = this is UiState.Success
fun <T> UiState<T>.isError() = this is UiState.Error
fun <T> UiState<T>.isEmpty() = this is UiState.Empty
