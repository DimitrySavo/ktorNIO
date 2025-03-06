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
import kotlin.text.Regex

fun Application.configureRouting() {
    routing {
        post("/register/local") {
            println("Get into rofls lol")
            val userRequest = call.receive<RegisterUser>()

            if(userRequest.username == "") {
                println("username issue")
                call.respond(HttpStatusCode.BadRequest)
            }

            if(userRequest.password == "") {
                println("password issue")
                call.respond(HttpStatusCode.BadRequest)
            }

            if(userRequest.userEmail?.matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$")) == false) {
                println("email issue")
                call.respond(HttpStatusCode.BadRequest)
            }

            val userId = Users.createUser(
                username = userRequest.username,
                email = userRequest.userEmail,
                password = userRequest.password!!,
                authType = AuthTypes.Local
            )

            if(userId != null) {
                val token = JWTConfig.getToken(userId)
                call.respond(HttpStatusCode.Created, mapOf("token" to token))
            } else {
                call.respond(HttpStatusCode.Conflict)
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

            if(!loginRequest.userEmail.matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$"))) {
                call.respond(HttpStatusCode.BadRequest)
            }

            if(loginRequest.password == "") {
                call.respond(HttpStatusCode.BadRequest)
            }

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
