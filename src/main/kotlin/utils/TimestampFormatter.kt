package com.example.utils

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object TimestampFormatter {
    private val formatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.UTC)

    fun createCurrentTimestamp(): String {
        return formatter.format(Instant.now())
    }
}
