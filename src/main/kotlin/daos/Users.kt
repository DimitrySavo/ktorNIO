package com.example.daos

import com.example.utils.FunctionResult
import com.example.utils.OperationResult
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.mindrot.jbcrypt.BCrypt
import java.util.UUID

object Users: Table("users") {
    val userId = uuid("userid").clientDefault { UUID.randomUUID() }
    val username = varchar("username", 100)
    val userEmail = varchar("useremail", 255).nullable()
    val password = varchar("password", 255).nullable()
    val isApproved = bool("isapproved").default(false)

    override val primaryKey = PrimaryKey(userId)

    fun createUser(username: String, email: String?, password: String) : UUID? {
        val hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt(12))
        return transaction {
            if(email != null) {
                Users.selectAll()
                    .where{ userEmail eq email }
                    .singleOrNull()?.let {
                        return@transaction null
                    }
            }

            Users.insert {
                it[Users.username] = username
                it[userEmail] = email
                it[Users.password] = hashedPassword
                it[isApproved] = false
            } get userId
        }
    }

    fun createUser(username: String, authType: AuthTypes, accountId: String) : UUID? {
        return transaction {
            val userId = Users.insert {
                it[Users.username] = username
            } get userId

            val account = DifferentAuthorizations.addUser(
                userId = userId,
                typeId = authType.type,
                account = accountId
            )

            if (account != null) {
                userId
            } else {
                null
            }
        }
    }

    fun verifyCredentials(userEmail: String, userPassword: String): Boolean {
        return transaction{
            val user = Users.selectAll()
                .where { Users.userEmail eq userEmail }
                .singleOrNull()

            if(user != null) {
                BCrypt.checkpw(userPassword, user[password] )
            } else {
                false
            }
        }
    }

    fun getUserIdByEmail(userEmail: String): UUID? {
        return transaction{
            Users.selectAll()
                .where { Users.userEmail eq userEmail}
                .singleOrNull()?.let {
                    return@transaction it[userId]
                }
        }
    }

    fun findUserByUsername(username: String)  {
        val user = transaction {
            Users.selectAll().where {
                Users.username eq username
            }.singleOrNull()
        }
        println(user)
    }

    fun getUserWithUid(userUid: UUID) : User? {
        try {
            return transaction {
                Users.selectAll()
                    .where { userId eq userUid }
                    .singleOrNull()?.let {
                        User(
                            userUid = it[Users.userId],
                            username = it[Users.username],
                            userEmail = it[Users.userEmail],
                            isApproved = it[Users.isApproved]
                        )
                    }
            }
        } catch (ex: Exception) {
            println("Get an exception: $ex")
            return null
        }
    }

    fun updateUserPassword(userUid: UUID, newPassword: String?): OperationResult<Unit> {
        val hashedPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt(12))
        try {
            val updatedCount = transaction {
                Users.update({ Users.userId eq userUid }) {
                    it[password] = hashedPassword
                }
            }
            return if (updatedCount <= 0) {
                OperationResult.UserError("Can't find user with such userId")
            } else {
                OperationResult.Success(Unit)
            }
        } catch (ex: Exception) {
            println("Get and exception: $ex")
            return OperationResult.ServerError(message = "Get and exception while updating user password")
        }
    }
}

enum class AuthTypes(val type: Int) {
    Local(1),
    Vk(2),
    Google(3)
}

data class User(
    val userUid : UUID,
    val username: String,
    val userEmail: String?,
    val isApproved: Boolean
)