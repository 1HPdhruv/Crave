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

    /**
     * An error occurred. [message] is shown to users, so it is sanitised here once for every screen: supabase-kt
     * exception messages append "URL: …", "Headers: [Authorization=[Bearer <token>]…]" and "Http Method: …" lines,
     * and several ViewModels pass `exception.message` straight through.
     */
    class Error(
        message: String,
        val errorType: ErrorType = ErrorType.GENERIC,
        val cause: Throwable? = null
    ) : UiState<Nothing>() {
        val message: String = userFacing(message)

        override fun equals(other: Any?): Boolean =
            other is Error && other.message == message && other.errorType == errorType && other.cause == cause
        override fun hashCode(): Int = (message.hashCode() * 31 + errorType.hashCode()) * 31 + (cause?.hashCode() ?: 0)
        override fun toString(): String = "Error(message=$message, errorType=$errorType)"

        companion object {
            /** Keeps only the human part of a message: drops request dumps and redacts any credential that remains. */
            fun userFacing(raw: String): String {
                val head = raw.substringBefore("\nURL:").substringBefore("\nHeaders:").substringBefore("\nHttp Method:").trim()
                return SafeLog.redact(head).take(300).ifBlank { "Something went wrong. Please try again." }
            }
        }
    }
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
