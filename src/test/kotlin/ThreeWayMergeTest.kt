package com.example

import com.example.utils.theewaysmerge.ThreeWayMerge
import org.junit.Assert.assertEquals
import kotlin.test.Test

class ThreeWayMergeTest {
    private val merger = ThreeWayMerge()

    /**
     * Тест, когда никаких изменений нет — все три версии совпадают.
     */
    @Test
    fun testNoChange() {
        val base = "line1\nline2\nline3"
        val user = base
        val server = base

        val expected = "line1\nline2\nline3"
        val result = merger.merge(base, user, server)
        println("testNoChange: merged result:")
        println(result)
        assertEquals(expected, result)
    }

    /**
     * Тест: пользователь вставил строку, а сервер оставил версию без изменений.
     */
    @Test
    fun testUserInsertion() {
        val base = "line1\nline2\nline3"
        val user = "line1\nline2\ninserted by user\nline3"
        val server = base

        val expected =
            "line1\nline2\n<<<<<<< USER\ninserted by user\n=======\nline3\n>>>>>>> SERVER\n<<<<<<< USER\nline3\n=======\n\n>>>>>>> SERVER"
        val result = merger.merge(base, user, server)
        println("testUserInsertion: merged result:")
        println(result)
        assertEquals(expected, result)
    }

    /**
     * Тест: сервер вставил строку, а пользователь оставил исходный вариант.
     */
    @Test
    fun testServerInsertion() {
        val base = "line1\nline2\nline3"
        val user = base
        val server = "line1\ninserted by server\nline2\nline3"

        val expected =
            "line1\n<<<<<<< USER\nline2\n=======\ninserted by server\n>>>>>>> SERVER\n<<<<<<< USER\nline3\n=======\nline2\n>>>>>>> SERVER\n<<<<<<< USER\n\n=======\nline3\n>>>>>>> SERVER"
        val result = merger.merge(base, user, server)
        println("testServerInsertion: merged result:")
        println(result)
        assertEquals(expected, result)
    }

    /**
     * Тест: пользователь удалил строку (удаление line2), сервер оставил нетронутый текст.
     */
    @Test
    fun testUserDeletion() {
        val base = "line1\nline2\nline3"
        val user = "line1\nline3"  // удалена строка "line2"
        val server = base

        val expected =
            "line1\n<<<<<<< USER\nline3\n=======\nline2\n>>>>>>> SERVER\n<<<<<<< USER\n\n=======\nline3\n>>>>>>> SERVER"
        val result = merger.merge(base, user, server)
        println("testUserDeletion: merged result:")
        println(result)
        assertEquals(expected, result)
    }

    /**
     * Тест: сервер удалил строку (удаление line2), пользователь оставил исходное содержимое.
     */
    @Test
    fun testServerDeletion() {
        val base = "line1\nline2\nline3"
        val user = base
        val server = "line1\nline3"  // удалена строка "line2" на сервере

        val expected =
            "line1\n<<<<<<< USER\nline2\n=======\nline3\n>>>>>>> SERVER\n<<<<<<< USER\nline3\n=======\n\n>>>>>>> SERVER"
        val result = merger.merge(base, user, server)
        println("testServerDeletion: merged result:")
        println(result)
        assertEquals(expected, result)
    }

    /**
     * Тест: одновременная вставка одной и той же строки пользователем и сервером.
     * При идентичном изменении патчи совпадают и merge должен вернуть итоговую версию без конфликтов.
     */
    @Test
    fun testSimultaneousInsertionSame() {
        val base = "line1\nline2"
        val user = "line1\ninserted\nline2"
        val server = "line1\ninserted\nline2"

        val expected = "line1\ninserted\nline2"
        val result = merger.merge(base, user, server)
        println("testSimultaneousInsertionSame: merged result:")
        println(result)
        assertEquals(expected, result)
    }

    /**
     * Тест: одновременные изменения, но с конфликтом — изменение разных частей одной и той же версии.
     */
    @Test
    fun testSimultaneousConflictChanges() {
        val base = "line1\nline2\nline3"
        val user = "line1 changed\nline2\nline3"
        val server = "line1\nline2 changed\nline3"

        val expected =
            "<<<<<<< USER\nline1 changed\n=======\nline1\n>>>>>>> SERVER\n<<<<<<< USER\nline2\n=======\nline2 changed\n>>>>>>> SERVER\nline3"
        val result = merger.merge(base, user, server)
        println("testSimultaneousConflictChanges: merged result:")
        println(result)
        assertEquals(expected, result)
    }

    /**
     * Тест: совершенно отличающиеся изменения.
     */
    @Test
    fun testCompletelyDifferentChanges() {
        val base = "A"
        val user = "B"
        val server = "C"

        val expected = "<<<<<<< USER\nB\n=======\nC\n>>>>>>> SERVER"
        val result = merger.merge(base, user, server)
        println("testCompletelyDifferentChanges: merged result:")
        println(result)
        assertEquals(expected, result)
    }

    /**
     * Тест: вставка строк в конце файла с различным содержимым.
     */
    @Test
    fun testInsertionAtEnd() {
        val base = "line1\nline2"
        val user = "line1\nline2\nline3"
        val server = "line1\nline2\nline4"

        val expected = "line1\nline2\n<<<<<<< USER\nline3\n=======\nline4\n>>>>>>> SERVER"
        val result = merger.merge(base, user, server)
        println("testInsertionAtEnd: merged result:")
        println(result)
        assertEquals(expected, result)
    }

    /**
     * Тест: пустая базовая версия. Пользователь вносит изменения, а сервер оставляет пустым.
     */
    @Test
    fun testEmptyBase() {
        val base = ""
        val user = "User text"
        val server = ""

        val expected = "<<<<<<< USER\nUser text\n=======\n\n>>>>>>> SERVER"
        val result = merger.merge(base, user, server)
        println("testEmptyBase: merged result:")
        println(result)
        assertEquals(expected, result)
    }
}