package com.example.utils.logging

import kotlinx.coroutines.InternalCoroutinesApi
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import com.example.utils.TimestampFormatter

object LogWriter {
    private val logFile: File = File("log.txt").apply {
        if (exists()) {
            writeText("")
        } else {
            createNewFile()
        }
    }
    private val logQueue = ConcurrentLinkedQueue<String>()
    private val executor = Executors.newSingleThreadScheduledExecutor()

    init {
        executor.scheduleAtFixedRate(::flushLogs, 500, 500, TimeUnit.MILLISECONDS)
    }

    fun log(message: String) {
        val sanitizedMessage = message.replace(Regex("\\s+"), " ")
        logFile.appendText("${TimestampFormatter.createCurrentTimestamp()}:$sanitizedMessage\n")
    }

    @OptIn(InternalCoroutinesApi::class)
    private fun flushLogs() {
        if (logQueue.isEmpty()) return

        kotlinx.coroutines.internal.synchronized(logFile) {
            BufferedWriter(FileWriter(logFile, true)).use { writer ->
                while (true) {
                    val logEntry = logQueue.poll() ?: break
                    writer.appendLine(logEntry)
                }
            }
        }
    }

    fun shutdown() {
        executor.shutdown()
        executor.awaitTermination(1, TimeUnit.SECONDS)
        flushLogs()
    }
}