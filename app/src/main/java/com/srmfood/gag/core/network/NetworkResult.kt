package com.srmfood.gag.core.network

/**
 * Sealed class wrapping all network operation results.
 * Used by data sources; mapped to UiState at ViewModel layer.
 */
sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error(
        val code: Int = -1,
        val message: String,
        val errorType: NetworkErrorType = NetworkErrorType.GENERIC
    ) : NetworkResult<Nothing>()
    object Loading : NetworkResult<Nothing>()
}

enum class NetworkErrorType {
    GENERIC,
    NO_INTERNET,
    TIMEOUT,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    SERVER_ERROR,
    VALIDATION,
    PARSE_ERROR
}

/** Maps HTTP status codes to NetworkErrorType */
fun Int.toNetworkErrorType(): NetworkErrorType = when (this) {
    401 -> NetworkErrorType.UNAUTHORIZED
    403 -> NetworkErrorType.FORBIDDEN
    404 -> NetworkErrorType.NOT_FOUND
    422 -> NetworkErrorType.VALIDATION
    in 500..599 -> NetworkErrorType.SERVER_ERROR
    else -> NetworkErrorType.GENERIC
}

/** Safe API call wrapper */
suspend fun <T> safeApiCall(apiCall: suspend () -> T): NetworkResult<T> {
    return try {
        NetworkResult.Success(apiCall.invoke())
    } catch (e: retrofit2.HttpException) {
        val code = e.code()
        val message = e.response()?.errorBody()?.string() ?: e.message()
        NetworkResult.Error(code, message, code.toNetworkErrorType())
    } catch (e: java.net.UnknownHostException) {
        NetworkResult.Error(message = "No internet connection", errorType = NetworkErrorType.NO_INTERNET)
    } catch (e: java.net.SocketTimeoutException) {
        NetworkResult.Error(message = "Request timed out", errorType = NetworkErrorType.TIMEOUT)
    } catch (e: kotlinx.serialization.SerializationException) {
        NetworkResult.Error(message = "Failed to parse response", errorType = NetworkErrorType.PARSE_ERROR)
    } catch (e: Exception) {
        NetworkResult.Error(message = e.message ?: "Unknown error")
    }
}
