package com.example.daos

import org.jetbrains.exposed.sql.Table

object AuthTypesTable: Table("authtypes") {
    val authTypeId = integer("authtypeid").autoIncrement()
    val authTypeName = varchar("authtypename", 30)
    override val primaryKey = PrimaryKey(authTypeId)
}
