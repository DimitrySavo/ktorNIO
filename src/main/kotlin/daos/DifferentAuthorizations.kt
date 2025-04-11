package com.example.daos

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.SQLException

object DifferentAuthorizations : Table("differentauthorizations") {
    val userId = integer("userid").references(Users.userId)
    val typeId = integer("typeid").references(AuthTypesTable.authTypeId)
    val accountId = varchar("accountid", 100)
    override val primaryKey = PrimaryKey(userId, typeId)

    fun addUser(userId: Int, typeId: Int, account: String) : String? {
        return try {
            transaction {
                DifferentAuthorizations.insert {
                    it[DifferentAuthorizations.userId] = userId
                    it[DifferentAuthorizations.typeId] = typeId
                    it[accountId] = account
                }[accountId]
            }
        } catch (sqlEx: SQLException) {
            println("Get an sql exception: $sqlEx")
            null
        } catch (ex: Exception) {
            println("Get an exception: $ex")
            null
        }
    }

    fun isThereAreUser(typeId: Int, account: String) : Int? {
        return try {
            transaction {
                DifferentAuthorizations.selectAll()
                    .where {
                        (DifferentAuthorizations.typeId eq typeId) and
                                (DifferentAuthorizations.accountId eq account)
                    }
            }.limit(1)
                .firstOrNull()
                ?.get(DifferentAuthorizations.userId)
        } catch (sqlEx: SQLException) {
            println("Get an sql exception: $sqlEx")
            null
        } catch (ex: Exception) {
            println("Get an exception: $ex")
            null
        }
    }
}