package com.example.handlers

import com.example.daos.PasswordResetCodes
import com.example.daos.Users
import com.example.utils.FunctionResult
import com.example.utils.mail.EmailSender

fun OTPRequestHandler(userEmail: String) : FunctionResult<String> {
    try {
        val userUid = Users.getUserIdByEmail(userEmail)
            ?: return FunctionResult.Error("Can't find user with such email")

        val username = Users.getUserWithUid(userUid)?.username

        if (username == null) {
            println("Username is null for userUid: $userUid (email: $userEmail)")
            return FunctionResult.Error("Username is null for userUid: $userUid (email: $userEmail)")
        }

        when (val code = PasswordResetCodes.addResetCode(userUid)) {
            is FunctionResult.Error -> {
                return code
            }


            is FunctionResult.Success -> {
                val resultOfSending = EmailSender.sendResetEmail(
                    userEmail = userEmail,
                    username = username,
                    resetCode = code.data
                )

                return when (resultOfSending) {
                    is FunctionResult.Error ->
                        resultOfSending

                    is FunctionResult.Success -> {
                        FunctionResult.Success("Code sent successfully")
                    }
                }
            }
        }
    } catch (ex: Exception) {
        println("Get an exception: $ex")
        return FunctionResult.Error("Internal server error during OTP request")
    }
}