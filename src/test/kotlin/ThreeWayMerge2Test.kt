package com.example

import com.example.utils.theewaysmerge.ThreeWayMerge2
import org.junit.Assert.assertEquals
import kotlin.test.Test

class ThreeWayMerge2Test {
    private val merger = ThreeWayMerge2()

    @Test
    fun testNoChanges() {
        val base = """
            line1
            line2
            line3
        """.trimIndent()
        val user = base
        val server = base

        val result = merger.merge(base, user, server)
        println("Test No Changes:\n$result\n")
        assertEquals(base, result)
    }

    @Test
    fun testOnlyServerChanged() {
        val base = """
            line1
            line2
            line3
        """.trimIndent()
        val user = base
        val server = """
            line1
            modified line2
            line3
        """.trimIndent()

        val expected = """
            line1
            modified line2
            line3
        """.trimIndent()

        val result = merger.merge(base, user, server)
        println("Test Only Server Changed:\n$result\n")
        assertEquals(expected, result)
    }

    @Test
    fun testOnlyUserChanged() {
        val base = """
            line1
            line2
            line3
        """.trimIndent()
        val user = """
            line1
            user modified line2
            line3
        """.trimIndent()
        val server = base

        val expected = """
            line1
            user modified line2
            line3
        """.trimIndent()

        val result = merger.merge(base, user, server)
        println("Test Only User Changed:\n$result\n")
        assertEquals(expected, result)
    }

    @Test
    fun testConflictWhenBothChanged() {
        val base = """
            line1
            line2
            line3
        """.trimIndent()
        val user = """
            line1
            user modified line2
            line3
        """.trimIndent()
        val server = """
            line1
            server modified line2
            line3
        """.trimIndent()

        val expected = """
            line1
            <<<<<<< USER
            user modified line2
            =======
            server modified line2
            >>>>>>> SERVER
            line3
        """.trimIndent()

        val result = merger.merge(base, user, server)
        println("Test Conflict When Both Changed:\n$result\n")
        assertEquals(expected, result)
    }

    @Test
    fun testInsertionNonConflict() {
        // Пользовательская версия вставляет новую строку, сервер остаётся без изменений.
        val base = """
            line1
            line2
            line3
        """.trimIndent()
        val user = """
            line1
            new line between
            line2
            line3
        """.trimIndent()
        val server = base

        val expected = """
            line1
            new line between
            line2
            line3
        """.trimIndent()

        val result = merger.merge(base, user, server)
        println("Test Insertion Non-Conflict:\n$result\n")
        assertEquals(expected, result)
    }

    @Test
    fun testDeletionNonConflict() {
        // Сервер удаляет строку, пользовательская версия без изменений.
        val base = """
            line1
            line2
            line3
        """.trimIndent()
        val user = base
        val server = """
            line1
            line3
        """.trimIndent()

        val expected = """
            line1
            line3
        """.trimIndent()

        val result = merger.merge(base, user, server)
        println("Test Deletion Non-Conflict:\n$result\n")
        assertEquals(expected, result)
    }

    @Test
    fun testBothSidesInsertionConflict() {
        // Обе стороны вставляют строку в одно и то же место, но вставленные строки различны.
        val base = """
            line1
            line2
            line3
        """.trimIndent()
        val user = """
            line1
            user inserted line after line1
            line2
            line3
        """.trimIndent()
        val server = """
            line1
            server inserted line after line1
            line2
            line3
        """.trimIndent()

        val expected = """
            line1
            <<<<<<< USER
            user inserted line after line1
            =======
            server inserted line after line1
            >>>>>>> SERVER
            line2
            line3
        """.trimIndent()

        val result = merger.merge(base, user, server)
        println("Test Both Sides Insertion Conflict:\n$result\n")
        assertEquals(expected, result)
    }

    @Test
    fun testMultipleLineConflict() {
        // Обе стороны изменяют несколько подряд идущих строк, отличающихся от базовой версии.
        val base = """
            line1
            line2
            line3
            line4
            line5
        """.trimIndent()
        val user = """
            line1
            line2 modified by user
            line3 modified by user
            line4
            line5 modified by user
            line6
        """.trimIndent()
        val server = """
            line1
            line2 modified by server
            line3 modified by server
            line4
            line5
            line6
        """.trimIndent()

        val expected = """
            line1
            <<<<<<< USER
            line2 modified by user
            line3 modified by user
            line4
            line5 modified by user
            =======
            line2 modified by server
            line3 modified by server
            line4
            line5
            >>>>>>> SERVER
            line6
        """.trimIndent()

        val result = merger.merge(base, user, server)
        println("Test Multiple Line Conflict:\n$result\n")
        assertEquals(expected, result)
    }

    @Test
    fun testEmptyFiles() {
        // Все версии пустые строки.
        val base = ""
        val user = ""
        val server = ""

        val expected = ""

        val result = merger.merge(base, user, server)
        println("Test Empty Files:\n$result\n")
        assertEquals(expected, result)
    }

    @Test
    fun testWhitespaceChangesConflict() {
        // Изменение только пробельных символов считается изменением.
        val base = """
            line1
            line2
            line3
        """.trimIndent()
        val user = """
            line1
            line2  
            line3
        """.trimIndent()  // Обратите внимание на лишние пробелы после line2
        val server = base

        // Поскольку сравнение идёт по строгому равенству строк, возникнет конфликт.
        val expected = """
            line1
            line2  
            line3
        """.trimIndent()

        val result = merger.merge(base, user, server)
        println("Test Whitespace Changes Conflict:\n$result\n")
        assertEquals(expected, result)
    }
}