package com.example.database

import com.example.daos.Users
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

class DatabaseFactory {
    fun init() {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = "jdbc:postgresql://localhost:5432/NIOServer"
            driverClassName = "org.postgresql.Driver"
            username = "postgres"
            password = "zxc_ghoul"
            maximumPoolSize = 3
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        }

        val dataSource = HikariDataSource(hikariConfig)

        Database.connect(dataSource)


        transaction {
            SchemaUtils.create(Users)
        }
    }
}

fun Application.initDatabase() {
    val database = DatabaseFactory().init()
}