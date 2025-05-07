package com.example.utils

import io.ktor.server.auth.jwt.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

object Helpers {
    fun getUserUidFromToken(principal: JWTPrincipal?) : UUID? {
        return try {
            UUID.fromString(principal?.payload?.getClaim("userId")?.asString())
        } catch (ex: Exception) {
            println("Can't get userId from token: $ex")
            null
        }
    }
}

object Locker {
    class ResourceLockException(message: String) : RuntimeException(message)

    inline fun <T> withAdvisoryLock(uid: UUID, crossinline block: () -> T): T = transaction {
        val gotLock: Boolean = exec(
            "SELECT pg_try_advisory_lock(hashtext('${uid}'))"
        ) { rs ->
            rs.next()
            rs.getBoolean(1)
        } ?: false

        if (!gotLock) {
            throw ResourceLockException("Resource $uid is already locked")
        }

        try {
            block()
        } finally {
            exec("SELECT pg_advisory_unlock(hashtext('${uid}'))")
        }
    }
}