package com.example.utils

import io.ktor.server.auth.jwt.*
import java.util.*

object Helpers {
    fun getUserUidFromToken(principal: JWTPrincipal?) : UUID? {
        return try {
            UUID.fromString(principal?.payload?.getClaim("userId")?.asString())
        } catch (ex: Exception) {
            println("Can't get userId from token: $ex")
            null
        }
    }
}