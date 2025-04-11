package com.example.routes

import com.example.*
import com.example.daos.*
import com.example.handlers.getUserItemContent
import com.example.handlers.handleItemDelete
import com.example.handlers.updateHandler
import com.example.handlers.userItemCreationHandler
import com.example.security.JWTConfig
import com.example.utils.FunctionResult
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Application.configureRouting() {
    routing {
        post("/register/local") {
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
                password = userRequest.password!!
            )

            if(userId != null) {
                val token = JWTConfig.getToken(userId)
                val refreshToken = JWTConfig.getRefreshToken(userId)
                call.respond(HttpStatusCode.OK, mapOf("token" to token, "refresh_token" to refreshToken))
            } else {
                call.respond(HttpStatusCode.Conflict)
                return@post
            }
        }

        post("/register/other") {
            val userRequest = call.receive<RegisterUserWithOAuth>()
            val authType = AuthTypes.entries.find { userRequest.type == it.name }

            if (authType == null) {
                call.respond(HttpStatusCode.BadRequest, "Wrong authorization type name")
                return@post
            }

            val userId = Users.createUser(
                username = userRequest.username,
                authType = authType,
                accountId = userRequest.accountId
            )

            if(userId != null) {
                val token = JWTConfig.getToken(userId)
                val refreshToken = JWTConfig.getRefreshToken(userId)
                call.respond(HttpStatusCode.OK, mapOf("token" to token, "refresh_token" to refreshToken))
            } else {
                call.respond(HttpStatusCode.Conflict)
                return@post
            }
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

            if(authResult && userId != null) {
                val token = JWTConfig.getToken(userId)
                val refreshToken = JWTConfig.getRefreshToken(userId)
                call.respond(HttpStatusCode.OK, mapOf("token" to token, "refresh_token" to refreshToken))
            } else {
                call.respond(HttpStatusCode.Unauthorized, "Bad credentials")
            }
        }

        post("login/other") {
            val loginRequest = call.receive<LoginUserOAuth>()
            val authType = AuthTypes.entries.find { loginRequest.type == it.name }

            if (authType == null) {
                call.respond(HttpStatusCode.BadRequest, "Wrong authorization type name")
                return@post
            }

            val authResult = DifferentAuthorizations.isThereAreUser(authType.type, loginRequest.accountId)
            if (authResult != null) {
                val token = JWTConfig.getToken(authResult)
                val refreshToken = JWTConfig.getRefreshToken(authResult)
                call.respond(HttpStatusCode.OK, mapOf("token" to token, "refresh_token" to refreshToken))
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

            put("/updateVersioned/{uid}") {
                println("Get into update with version")

                val uidParameter = call.parameters["uid"]
                val updateInstance = call.receive<UpdateObjectWithVersion>()
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

                    //replace with a handler or mb replace request at all
                    val result = UserItemsTable.updateItemWithVersion(
                        uid = uid,
                        name = updateInstance.name,
                        parentId = updateInstance.parentId,
                        version = updateInstance.version,
                        baseline = updateInstance.baseline,
                        modifiedText = updateInstance.modifiedText,
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

            get("/items"){
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asInt()

                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, "Can't get userId form token")
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

            get("/items/{itemUid}") {
                val itemUidParam = call.parameters["itemUid"]
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asInt()

                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, "Can't get userId form token")
                    return@get
                }

                val itemUid = try {
                    UUID.fromString(itemUidParam)
                } catch (ex: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid item uid format")
                    return@get
                }

                try {
                    val result = getUserItemContent(itemUid, userId)

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

            get("/items/deleted") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asInt()

                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, "Can't get userId form token")
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
