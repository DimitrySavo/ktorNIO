package com.example.daos

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt

object Users: Table("users") {
    val userId = integer("userid").autoIncrement()
    val username = varchar("username", 100)
    val userEmail = varchar("useremail", 255).nullable()
    val password = varchar("password", 255).nullable()
    val isApproved = bool("isapproved").default(false)

    override val primaryKey = PrimaryKey(userId)

    fun createUser(username: String, email: String?, password: String) : Int? {
        val hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt(12))
        return transaction {
            if(email != null) {
                Users.selectAll()
                    .where{ Users.userEmail eq email }
                    .singleOrNull()?.let {
                        return@transaction null
                    }
            }

            Users.insert {
                it[Users.username] = username
                it[Users.userEmail] = email
                it[Users.password] = hashedPassword
                it[Users.isApproved] = false
            } get Users.userId
        }
    }

    fun createUser(username: String, authType: AuthTypes, accountId: String) : Int? {
        return transaction {
            val userId = Users.insert {
                it[Users.username] = username
            } get Users.userId

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
                BCrypt.checkpw(userPassword, user[Users.password] )
            } else {
                false
            }
        }
    }

    fun getUserIdByEmail(userEmail: String): Int? {
        return transaction{
            Users.selectAll()
                .where { Users.userEmail eq userEmail}
                .singleOrNull()?.let {
                    return@transaction it[Users.userId]
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
}

enum class AuthTypes(val type: Int) {
    Local(1),
    Google(3),
    Vk(2)
}