package com.example

import com.example.data.configureMinio
import com.example.database.initDatabase
import com.example.routes.configureRouting
import com.example.routes.configureSockets
import com.example.security.configureSecurity
import com.example.utils.logging.LogWriter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    val module = SerializersModule {
        polymorphic(UpdateRequest::class) {
            subclass(MetadataUpdateRequest::class, MetadataUpdateRequest.serializer())
            subclass(TextUpdateRequest::class, TextUpdateRequest.serializer())
        }
    }

    val json = Json {
        serializersModule = module
        classDiscriminator = "type"
        ignoreUnknownKeys = true
    }

    install(ContentNegotiation) {
        json(json = json)
    }

    initDatabase()
    configureMinio()
    configureSecurity()
    configureSockets()
    configureFrameworks()
    configureRouting()

    Runtime.getRuntime().addShutdownHook(
        Thread {
            LogWriter.shutdown()
        }
    )
}
