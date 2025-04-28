package com.example.utils

sealed class OperationResult<out T> {
    data class Success<out T>(val data: T) : OperationResult<T>()
    data class UserError(val message: String? = null) : OperationResult<Nothing>()
    data class ServerError(val exception: Throwable? = null, val message: String? = null) : OperationResult<Nothing>()
}