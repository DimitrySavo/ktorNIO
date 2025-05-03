package com.example.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.daos.Users
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*


fun Application.configureSecurity() {

    install(Authentication) {
        jwt("auth-jwt") {
            realm = JWTConfig.realm

            verifier(JWT
                .require(Algorithm.HMAC256(JWTConfig.secret))
                .withAudience(JWTConfig.audience)
                .withIssuer(JWTConfig.issuer)
                .build()
            )

            validate { credential ->
                try {
                    val userId = UUID.fromString(credential.payload.getClaim("userId").asString())
                    val user = transaction{
                        Users.selectAll().where { Users.userId eq userId }.singleOrNull()
                    }
                    if(user != null) {
                        JWTPrincipal(credential.payload)
                    } else {
                        null
                    }
                } catch (ex: Exception) {
                    println("Get an exception: $ex")
                    null
                }
            }

            challenge { defaultScheme, realm ->
                call.respond(HttpStatusCode.Unauthorized, "Invalid token")
            }
        }

        jwt("refresh-jwt") {
            realm = JWTConfig.realm

            verifier(JWT
                .require(Algorithm.HMAC256(JWTConfig.refreshSecret))
                .withAudience(JWTConfig.audience)
                .withIssuer(JWTConfig.issuer)
                .build()
            )

            validate { credential ->
                try {
                    val userId =  UUID.fromString(credential.payload.getClaim("userId").asString())
                    val user = transaction{
                        Users.selectAll().where { Users.userId eq userId }.singleOrNull()
                    }
                    if(user != null) {
                        JWTPrincipal(credential.payload)
                    } else {
                        null
                    }
                } catch (ex: Exception) {
                    println("Get an exception")
                    null
                }
            }

            challenge { defaultScheme, realm ->
                call.respond(HttpStatusCode.Unauthorized, "Invalid token")
            }
        }

        jwt("reset-password-jwt") {
            realm = JWTConfig.realm

            verifier(JWT
                .require(Algorithm.HMAC256(JWTConfig.resetPasswordSecret))
                .withAudience(JWTConfig.audience)
                .withIssuer(JWTConfig.issuer)
                .build()
            )

            validate { credential ->
                try {
                    val userId =  UUID.fromString(credential.payload.getClaim("userId").asString())
                    val user = transaction{
                        Users.selectAll().where { Users.userId eq userId }.singleOrNull()
                    }
                    if(user != null) {
                        JWTPrincipal(credential.payload)
                    } else {
                        null
                    }
                } catch (ex: Exception) {
                    println("Get an exception")
                    null
                }
            }

            challenge { defaultScheme, realm ->
                call.respond(HttpStatusCode.Unauthorized, "Invalid token")
            }
        }
    }
}


object JWTConfig {
    val secret = System.getenv("JWT_SECRET") ?: "secret"
    val refreshSecret = System.getenv("JWT_REFRESH") ?:"refreshSecret"
    val resetPasswordSecret = System.getenv("JWT_RESET_PASSWORD") ?: "resetPasswordSecret"
    val audience = "nio_users"
    val issuer = "nio_user"
    val realm = "nio_realm"

    fun getToken(userId: UUID) : String {
        return JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("userId", userId.toString())
            .withExpiresAt(Date.from(Instant.now().plus(1, ChronoUnit.HOURS))) // 1 hour
            .sign(Algorithm.HMAC256(secret))
    }

    fun getRefreshToken(userId: UUID) : String {
        return JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("userId", userId.toString())
            .withExpiresAt(Date.from(Instant.now().plus(1, ChronoUnit.MONTHS)))
            .sign(Algorithm.HMAC256(refreshSecret))
    }

    fun getResetPasswordSecret(userId: UUID): String {
        return JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("userId", userId.toString())
            .withExpiresAt(Date.from(Instant.now().plus(10, ChronoUnit.MINUTES)))
            .sign(Algorithm.HMAC256(resetPasswordSecret))
    }
}