package com.example

import kotlinx.serialization.Serializable

@Serializable
data class RegisterUser(
    val username: String,
    val userEmail: String? = null,
    val password: String? = null
)

@Serializable
data class LoginUser(
    val userEmail: String,
    val password: String
)