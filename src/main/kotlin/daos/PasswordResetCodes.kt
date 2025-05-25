package com.example.daos

import com.example.utils.FunctionResult
import com.example.utils.OperationResult
import com.example.utils.logging.LogWriter
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.SQLException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.random.Random

object PasswordResetCodes : Table("passwordresetcodes") {
    val userUID = uuid("useruid").references(Users.userId)
    val code = char("code", 6)
    val createdAd = timestamp("createdat")
    val expiresAt = timestamp("expiresat")
    val used = bool("used")

    override val primaryKey = PrimaryKey(userUID)

    fun addResetCode(userUid: UUID) : FunctionResult<String> {
        val codeValue = Random.nextInt(100000, 999999).toString()
        val expiresAtValue = Instant.now().plus(10L, ChronoUnit.MINUTES)

        try {
            transaction {
                PasswordResetCodes.upsert(keys = arrayOf(PasswordResetCodes.userUID)) {
                    it[PasswordResetCodes.userUID] = userUid
                    it[PasswordResetCodes.code] = codeValue
                    it[PasswordResetCodes.expiresAt] = expiresAtValue
                    it[PasswordResetCodes.used] = false
                }
            }

            return FunctionResult.Success(codeValue)
        } catch (sqlEx: SQLException) {
            LogWriter.log("addResetCode - Get an sql exception while adding password reset code: $sqlEx")
            return FunctionResult.Error("Get an sql exception while adding password reset code: $sqlEx")
        } catch (ex: Exception) {
            LogWriter.log("addResetCode - Get an exception while adding password reset code: $ex")
            return FunctionResult.Error("Get an exception while adding password reset code: $ex")
        }
    }

    fun validateAndUseResetCode(userUid: UUID, providedCode: String): OperationResult<Unit> {
        try {
            transaction {
                val record = PasswordResetCodes
                    .selectAll()
                    .where { PasswordResetCodes.userUID eq userUid }
                    .firstOrNull()
                    ?: throw IllegalStateException("Сброс пароля не запрашивался или код не найден")

                val storedCode = record[PasswordResetCodes.code]
                if (storedCode != providedCode) {
                    throw IllegalArgumentException("Неверный код подтверждения")
                }

                val expiresAt = record[PasswordResetCodes.expiresAt]
                if (expiresAt.isBefore(Instant.now())) {
                    throw IllegalStateException("Срок действия кода истёк")
                }

                val alreadyUsed = record[PasswordResetCodes.used]
                if (alreadyUsed) {
                    throw IllegalStateException("Код уже был использован")
                }

                PasswordResetCodes.update({ PasswordResetCodes.userUID eq userUid }) {
                    it[used] = true
                }
            }

            return OperationResult.Success(Unit)
        } catch (e: IllegalArgumentException) {
            LogWriter.log("validateAndUseResetCode - Wrong otp code: ${e.message}")
            return OperationResult.UserError(e.message ?: "Неверный код подтверждения")
        } catch (e: IllegalStateException) {
            LogWriter.log("validateAndUseResetCode - Can't use otp due to: ${e.message}")
            return OperationResult.UserError(e.message ?: "Невозможно использовать код")
        } catch (sqlEx: SQLException) {
            LogWriter.log("validateAndUseResetCode - SQL exception during validateAndUseResetCode: $sqlEx")
            return OperationResult.ServerError(message = "Ошибка базы данных: ${sqlEx.message}")
        } catch (ex: Exception) {
            LogWriter.log("validateAndUseResetCode - Exception during validateAndUseResetCode: $ex")
            return OperationResult.ServerError(message = "Внутренняя ошибка: ${ex.message}")
        }
    }
}