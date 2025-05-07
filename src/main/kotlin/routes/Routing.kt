package com.example.routes

import com.example.*
import com.example.daos.*
import com.example.data.readFromFile
import com.example.handlers.*
import com.example.security.JWTConfig
import com.example.utils.FunctionResult
import com.example.utils.Helpers
import com.example.utils.OperationResult
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.Except
import org.koin.ktor.ext.get
import org.w3c.dom.Text
import java.util.*

fun Application.configureRouting() {
    routing {
        // region Registration
        post("/register/local") {
            val userRequest = call.receive<RegisterUser>()

            if (userRequest.username == "") {
                println("username issue")
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            if (userRequest.password == "") {
                println("password issue")
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            if (userRequest.userEmail?.matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$")) == false) {
                println("email issue")
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val userId = Users.createUser(
                username = userRequest.username,
                email = userRequest.userEmail,
                password = userRequest.password!!
            )

            if (userId != null) {
                val token = JWTConfig.getToken(userId)
                val refreshToken = JWTConfig.getRefreshToken(userId)
                call.respond(HttpStatusCode.OK, mapOf("token" to token, "refresh_token" to refreshToken))
            } else {
                call.respond(HttpStatusCode.Conflict, mapOf("error" to "Already have user with such email"))
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

            if (userId != null) {
                val token = JWTConfig.getToken(userId)
                val refreshToken = JWTConfig.getRefreshToken(userId)
                call.respond(HttpStatusCode.OK, mapOf("token" to token, "refresh_token" to refreshToken))
            } else {
                call.respond(HttpStatusCode.Conflict)
                return@post
            }
        }
        //endregion

        //region Login
        post("/login/local") {
            val loginRequest = call.receive<LoginUser>()

            if (!loginRequest.userEmail.matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$"))) {
                call.respond(HttpStatusCode.BadRequest)
            }

            if (loginRequest.password == "") {
                call.respond(HttpStatusCode.BadRequest)
            }

            val authResult = Users.verifyCredentials(loginRequest.userEmail, loginRequest.password)
            val userId = Users.getUserIdByEmail(loginRequest.userEmail)

            if (authResult && userId != null) {
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
        //endregion

        // region Reset password
        post("get_otp_code") {
            val request = call.receive<ResetPasswordEmail>()
            val userEmail = request.email

            if (userEmail.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing or empty userEmail"))
                return@post
            }

            when (val otpSendResult = OTPRequestHandler(userEmail)) {
                is FunctionResult.Error -> {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to otpSendResult.message))
                    return@post
                }

                is FunctionResult.Success -> {
                    call.respond(HttpStatusCode.OK)
                    return@post
                }
            }
        }

        get("check_otp_code") {
            val request = call.receive<ResetPasswordOtp>()
            val otp = request.otp

            if (request.userEmail.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Empty user email"))
                return@get
            }
            val userId = Users.getUserIdByEmail(request.userEmail)

            if (otp.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing or empty OTP"))
                return@get
            }

            if (userId != null) {
                when (val checkResult = PasswordResetCodes.validateAndUseResetCode(userId, otp)) {
                    is OperationResult.ServerError -> {
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Server error"))
                        return@get
                    }

                    is OperationResult.UserError -> {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Bad otp or otp expired"))
                        return@get
                    }

                    is OperationResult.Success -> {
                        call.respond(HttpStatusCode.OK, mapOf("token" to JWTConfig.getResetPasswordSecret(userId)))
                        return@get
                    }
                }
            } else {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Bad credentials"))
                return@get
            }
        }

        authenticate("reset-password-jwt") {
            patch("reset_password") {
                val request = call.receive<ResetPasswordNew>()
                val principal = call.receive<JWTPrincipal>()
                val userUid = Helpers.getUserUidFromToken(principal)

                if (request.newPassword.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Empty new password"))
                    return@patch
                }

                if (userUid == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Can't find user with such email"))
                    return@patch
                }

                when (val updateResult =
                    Users.updateUserPassword(userUid = userUid, newPassword = request.newPassword)) {
                    is OperationResult.ServerError -> {
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Server error"))
                        return@patch
                    }

                    is OperationResult.UserError -> {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to updateResult.message))
                        return@patch
                    }

                    is OperationResult.Success -> {
                        call.respond(HttpStatusCode.OK)
                        return@patch
                    }
                }
            }
        }
        //endregion

        //region Refresh token
        authenticate("refresh-jwt") {
            get("/refresh") {
                val principal = call.principal<JWTPrincipal>()
                val userId = Helpers.getUserUidFromToken(principal)

                if (userId != null) {
                    val token = JWTConfig.getToken(userId)
                    call.respond(HttpStatusCode.OK, mapOf("token" to token))
                } else {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Bad credentials"))
                }
            }
        }
        //endregion

        authenticate("auth-jwt") {
            get("/jwt/test") {
                val principal = call.principal<JWTPrincipal>()
                val userId = Helpers.getUserUidFromToken(principal)

                if (userId != null) {
                    call.respond(HttpStatusCode.OK, mapOf("userId" to userId))
                } else {
                    call.respond(HttpStatusCode.Unauthorized, "Bad credentials")
                }
            }


            post("/create") {
                val principal = call.principal<JWTPrincipal>()
                val userId = Helpers.getUserUidFromToken(principal)

                if (userId != null) {
                    try {
                        val createInstance = call.receive<CreateObject>()
                        val result = userItemCreationHandler(createInstance, userId)

                        when (result) {
                            is FunctionResult.Error -> call.respond(
                                HttpStatusCode.InternalServerError,
                                mapOf("error" to result.message)
                            )

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

            //region Updating items
            put("update/{uid}") {
                val itemUidParam = call.parameters["uid"]
                val principal = call.principal<JWTPrincipal>()
                val userId = Helpers.getUserUidFromToken(principal)

                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, "Can't get userId form token")
                    return@put
                }

                val itemUid = try {
                    UUID.fromString(itemUidParam)
                } catch (ex: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid item uid format")
                    return@put
                }

                try {
                    when (val isOwned = UserItemsTable.isItemOwnedByUser(userId, itemUid)) {
                        is FunctionResult.Error -> {
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                mapOf("error" to "Can't check owner of selected item")
                            )
                            return@put
                        }

                        is FunctionResult.Success -> {
                            if (isOwned.data) {
                                val updateInstance = call.receive<UpdateRequest>()
                                if (updateInstance == null) {
                                    println("Update instance is null")
                                    return@put
                                }

                                when (updateInstance) {
                                    is TextUpdateRequest -> {
                                        when (val result = UserItemsTable.updateTextFile(
                                            instance = updateInstance,
                                            userUid = userId,
                                            itemUUID = itemUid
                                        )) {
                                            is FunctionResult.Error -> {
                                                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to result.message))
                                                return@put
                                            }

                                            is FunctionResult.Success -> {
                                                call.respond(HttpStatusCode.OK)
                                                return@put
                                            }
                                        }
                                    }

                                    is MetadataUpdateRequest -> {
                                        when (val result = UserItemsTable.updateMetadata(
                                            instance = updateInstance,
                                            userUid = userId,
                                            itemUUID = itemUid
                                        )) {
                                            is FunctionResult.Error -> {
                                                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to result.message))
                                                return@put
                                            }

                                            is FunctionResult.Success -> {
                                                call.respond(HttpStatusCode.OK)
                                                return@put
                                            }
                                        }
                                    }
                                }
                            } else {
                                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Item not owned by user"))
                                return@put
                            }
                        }
                    }
                } catch (ex: Exception) {
                    println("Get and exceptiong while parsing UpdateRequest")
                    return@put
                }
            }
            //endregion

            //region Getting items
            get("/items") {
                val principal = call.principal<JWTPrincipal>()
                val userId = Helpers.getUserUidFromToken(principal)

                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, "Can't get userId form token")
                    return@get
                }

                try {
                    println("get in user items")
                    val result = UserItemsTable.getUserItems(userId)

                    when (result) {
                        is FunctionResult.Error -> call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to result.message)
                        )

                        is FunctionResult.Success -> call.respond(HttpStatusCode.OK, result.data)
                    }

                    return@get
                } catch (ex: Exception) {
                    println("Get an exception: ${ex.message}")
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to ex.message))
                }
            }

            get("/items/{uid}") {
                val itemUidParam = call.parameters["uid"]
                val principal = call.principal<JWTPrincipal>()
                val userId = Helpers.getUserUidFromToken(principal)

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
                    when (val isOwned = UserItemsTable.isItemOwnedByUser(userId, itemUid)) {
                        is FunctionResult.Error -> {
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                mapOf("error" to "Can't check owner of selected item")
                            )
                            return@get
                        }

                        is FunctionResult.Success -> {
                            if (isOwned.data) {
                                val resultMap = mutableMapOf<String, String?>()

                                when (val fileVersion = UserItemsTable.getItemVersion(itemUid)) {
                                    is FunctionResult.Error -> {
                                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to fileVersion.message))
                                        return@get
                                    }

                                    is FunctionResult.Success -> {
                                        resultMap["version"] = fileVersion.data
                                    }
                                }

                                when (val fileContent = readFromFile(itemUid.toString())) {
                                    is FunctionResult.Error -> {
                                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to fileContent.message))
                                        return@get
                                    }

                                    is FunctionResult.Success -> {
                                        resultMap["version"] = fileContent.data
                                    }
                                }

                                call.respond(HttpStatusCode.OK, resultMap)
                                return@get
                            } else {
                                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Item not owned by user"))
                                return@get
                            }
                        }
                    }


                } catch (ex: Exception) {
                    println("Get an exception: ${ex.message}")
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to ex.message))
                }
            }
            //endregion

            // region Deletion
            delete("/delete/{uid}") {
                val itemUidParam = call.parameters["uid"]
                val principal = call.principal<JWTPrincipal>()
                val userId = Helpers.getUserUidFromToken(principal)

                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, "Can't get userId form token")
                    return@delete
                }

                val itemUid = try {
                    UUID.fromString(itemUidParam)
                } catch (ex: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid item uid format")
                    return@delete
                }

                try {
                    when (val isOwned = UserItemsTable.isItemOwnedByUser(userId, itemUid)) {
                        is FunctionResult.Error -> {
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                mapOf("error" to "Can't check owner of selected item")
                            )
                            return@delete
                        }

                        is FunctionResult.Success -> {
                            if (isOwned.data) {
                                when (val result = UserItemsTable.softItemDeletion(itemUid)) {
                                    is FunctionResult.Error -> {
                                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to result.message))
                                        return@delete
                                    }

                                    is FunctionResult.Success -> {
                                        call.respond(HttpStatusCode.OK)
                                        return@delete
                                    }
                                }
                            } else {
                                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Item not owned by user"))
                                return@delete
                            }
                        }
                    }
                } catch (ex: Exception) {
                    println("Get an exception: ${ex.message}")
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to ex.message))
                }
            }

            get("/items/deleted") {
                val principal = call.principal<JWTPrincipal>()
                val userId = Helpers.getUserUidFromToken(principal)

                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, "Can't get userId form token")
                    return@get
                }

                try {
                    val result = UserItemsTable.getDeletedUserItems(userId)

                    when (result) {
                        is FunctionResult.Error -> call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to result.message)
                        )

                        is FunctionResult.Success -> call.respond(HttpStatusCode.OK, result.data)
                    }

                    return@get
                } catch (ex: Exception) {
                    println("Get an exception: ${ex.message}")
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to ex.message))
                }
            }
            //endregion
        }
    }
}
