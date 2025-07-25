package com.example.utils

sealed class FunctionResult<out T> {
    data class Success<out T>(val data: T): FunctionResult<T>()
    data class Error(val message: String): FunctionResult<Nothing>()
}