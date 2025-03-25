package com.example.routes

import com.example.CreateObject
import com.example.FunctionResult
import com.example.LoginUser
import com.example.RegisterUser
import com.example.UpdateObject
import com.example.daos.AuthTypes
import com.example.daos.StorageItemsIds
import com.example.daos.StorageItemsNames
import com.example.daos.UserItemsTable
import com.example.daos.Users
import com.example.handlers.getUserItemContent
import com.example.handlers.handleItemDelete
import com.example.handlers.updateHandler
import com.example.handlers.userItemCreationHandler
import com.example.security.JWTConfig
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import java.util.UUID
import kotlin.text.Regex

fun Application.configureRouting() {
    routing {
        post("/register/local") {
            println("Get into rofls lol")
            val userRequest = call.receive<RegisterUser>()

            if(userRequest.username == "") {
                println("username issue")
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            if(userRequest.password == "") {
                println("password issue")
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            if(userRequest.userEmail?.matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$")) == false) {
                println("email issue")
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val userId = Users.createUser(
                username = userRequest.username,
                email = userRequest.userEmail,
                password = userRequest.password!!,
                authType = AuthTypes.Local
            )

            if(userId != null) {
                val token = JWTConfig.getToken(userId)
                val refreshToken = JWTConfig.getRefreshToken(userId)
                call.respond(HttpStatusCode.OK, mapOf("token" to token, "refresh token" to refreshToken))
            } else {
                call.respond(HttpStatusCode.Conflict)
                return@post
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
                val refreshToken = JWTConfig.getRefreshToken(userId)
                call.respond(HttpStatusCode.OK, mapOf("token" to token, "refresh token" to refreshToken))
            } else {
                call.respond(HttpStatusCode.Unauthorized, "Bad credentials")
            }
        }

        authenticate("refresh-jwt") {
            get("/refresh") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asInt()

                if(userId != null) {
                    val token = JWTConfig.getToken(userId)
                    call.respond(HttpStatusCode.OK, mapOf("token" to token))
                } else {
                    call.respond(HttpStatusCode.Unauthorized, "Bad credentials")
                }
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

            post("/create") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asInt()

                if(userId != null) {
                    try {
                        val createInstance = call.receive<CreateObject>()
                        val result = userItemCreationHandler(createInstance, userId)

                        when (result) {
                            is FunctionResult.Error -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to result.message))
                            is FunctionResult.Success<*> -> call.respond(HttpStatusCode.Created)
                        }

                        return@post
                    } catch (ex: Exception) {
                        println("Get and exception: ${ex.message}")
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to ex.message))
                        return@post
                    }

                } else {
                    call.respond(HttpStatusCode.Unauthorized, "Bad credentials")
                }
            }

            delete("/delete/{uid}") {
                val uidParameter = call.parameters["uid"]
                if(uidParameter == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing uid parameter")
                    return@delete
                }

                try {
                    val uid = UUID.fromString(uidParameter)

                    when(val result = handleItemDelete(uid)) {
                        is FunctionResult.Success -> call.respond(HttpStatusCode.OK, mapOf("uid" to uid.toString()))
                        is FunctionResult.Error -> call.respond(HttpStatusCode.InternalServerError, mapOf("error" to result.message))
                    }
                    return@delete
                } catch (ex: Exception) {
                    println("Get an exception: ${ex.message}")
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to ex.message))
                }
            }

            put("/test") {
                println("Get into rofls lol")
                call.respond(HttpStatusCode.OK)
            }

            put("/update/{uid}"){
                println("Get into put")

                val uidParameter = call.parameters["uid"]
                val updateInstance = call.receive<UpdateObject>()
                val type = StorageItemsIds.entries.firstOrNull { it.name.equals(updateInstance.type, ignoreCase = true) }

                if (type == null) {
                    call.respond(HttpStatusCode.BadRequest, "Bad type")
                    return@put
                }

                if(uidParameter == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing uid parameter")
                    return@put
                }

                try {
                    val uid = UUID.fromString(uidParameter)

                    val result = updateHandler(
                        instance = updateInstance,
                        uid = uid,
                        type = type
                    )

                    when (result) {
                        is FunctionResult.Success -> call.respond(HttpStatusCode.OK, mapOf("uid" to uid.toString()))
                        is FunctionResult.Error -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to result.message))
                    }

                    return@put
                } catch (ex: Exception) {
                    println("Get an exception: ${ex.message}")
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to ex.message))
                }
            }

            get("/items/{userId}"){
                val userIdParam = call.parameters["userId"]

                if (userIdParam == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing userId parameter")
                    return@get
                }

                val userId = try {
                    userIdParam.toInt()
                } catch (ex: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid userId format")
                    return@get
                }

                try {
                    println("get in user items")
                    val result = UserItemsTable.getUserItems(userId)

                    when (result) {
                        is FunctionResult.Error -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to result.message))
                        is FunctionResult.Success -> call.respond(HttpStatusCode.OK, result.data)
                    }

                    return@get
                } catch (ex: Exception) {
                    println("Get an exception: ${ex.message}")
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to ex.message))
                }
            }

            get("/items/{userId}/{itemUid}") {
                val itemUidParam = call.parameters["itemUid"]

                val itemUid = try {
                    UUID.fromString(itemUidParam)
                } catch (ex: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid item uid format")
                    return@get
                }

                try {
                    val result = getUserItemContent(itemUid)

                    when(result) {
                        is FunctionResult.Error -> call.respond(HttpStatusCode.NotFound, mapOf("error" to result.message))
                        is FunctionResult.Success -> call.respond(HttpStatusCode.OK, result.data)
                    }

                    return@get
                } catch (ex: Exception) {
                    println("Get an exception: ${ex.message}")
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to ex.message))
                }
            }

            get("/items/deleted/{userId}") {
                val userIdParam = call.parameters["userId"]

                if (userIdParam == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing userId parameter")
                    return@get
                }

                val userId = try {
                    userIdParam.toInt()
                } catch (ex: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid userId format")
                    return@get
                }

                try {
                    val result = UserItemsTable.getDeletedUserItems(userId)

                    when (result) {
                        is FunctionResult.Error -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to result.message))
                        is FunctionResult.Success -> call.respond(HttpStatusCode.OK, result.data)
                    }

                    return@get
                } catch (ex: Exception) {
                    println("Get an exception: ${ex.message}")
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to ex.message))
                }
            }
        }
    }
}
