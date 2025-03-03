package com.example.routes

import com.example.LoginUser
import com.example.RegisterUser
import com.example.daos.AuthTypes
import com.example.daos.Users
import com.example.security.JWTConfig
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        post("/register/local") {
            val userRequest = call.receive<RegisterUser>()

            val userId = Users.createUser(
                username = userRequest.username,
                email = userRequest.userEmail,
                password = userRequest.password!!,
                authType = AuthTypes.Local
            )

            if(userId != null) {
                val token = JWTConfig.getToken(userId)
                call.respond(HttpStatusCode.Created, mapOf("token" to token))
            }
        }

        post("/register/google") {
            TODO()
        }

        post("/register/vk") {
            TODO()
        }

        post("/login/local") {
            val loginRequest = call.receive<LoginUser>()

            val authResult = Users.verifyCredentials(loginRequest.userEmail, loginRequest.password)
            val userId = Users.getUserIdByEmail(loginRequest.userEmail)

            if(authResult == true && userId != null) {
                val token = JWTConfig.getToken(userId)
                call.respond(HttpStatusCode.OK, mapOf("token" to token))
            } else {
                call.respond(HttpStatusCode.Unauthorized, "Bad credentials")
            }
        }

        authenticate("auth-jwt") {
            get("/jwt/test") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asInt()

                if(userId != null) {
                    call.respond(HttpStatusCode.OK, mapOf("userId" to userId))
                } else {
                    call.respond(HttpStatusCode.Unauthorized, "Bad credentials")
                }
            }
        }
    }
}
