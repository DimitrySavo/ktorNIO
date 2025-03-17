package com.example

import com.example.data.configureMinio
import com.example.database.initDatabase
import com.example.routes.configureRouting
import com.example.routes.configureSockets
import com.example.security.configureSecurity
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    install(ContentNegotiation) {
        json()
    }

    initDatabase()
    configureMinio()
    configureSecurity()
    configureSockets()
    configureFrameworks()
    configureRouting()
}
