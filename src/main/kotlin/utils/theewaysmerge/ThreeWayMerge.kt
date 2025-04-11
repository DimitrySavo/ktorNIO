package com.example.utils.theewaysmerge

import org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch

class ThreeWayMerge {

    // Экземпляр diff-match-patch для вычисления diff/patch
    private val dmp = DiffMatchPatch()

    /**
     * Выполняет трёхстороннее слияние.
     *
     * @param base Исходный текст, от которого начинаются изменения.
     * @param user Текст, полученный после изменений пользователя.
     * @param server Текст, полученный после изменений на сервере.
     * @return Результирующий текст после объединения версий. В случае конфликта вставляются маркеры.
     */
    fun merge(base: String, user: String, server: String): String {
        // Вычисляем diff от базы к пользовательской версии
        val diffUser = dmp.diffMain(base, user)
        // Вычисляем diff от базы к серверной версии
        val diffServer = dmp.diffMain(base, server)
        // Оптимизируем полученные diff-ы для улучшения качества патча
        dmp.diffCleanupSemantic(diffUser)
        dmp.diffCleanupSemantic(diffServer)

        // Создаём патчи на основе полученных diff-ов
        val patchesUser = dmp.patchMake(base, diffUser)
        val patchesServer = dmp.patchMake(base, diffServer)

        // Пробуем применить патчи к базе
        val userResultPair = dmp.patchApply(patchesUser, base)
        val userResult = userResultPair[0] as String

        val serverResultPair = dmp.patchApply(patchesServer, base)
        val serverResult = serverResultPair[0] as String

        // Если оба результата совпадают, считаем, что конфликт отсутствует
        if (userResult == serverResult) {
            return userResult
        }

        // Если результаты отличаются, выполняем построчное объединение с разметкой конфликтов
        return mergeLineByLine(userResult, serverResult)
    }

    /**
     * Простой алгоритм построчного слияния с разрешением конфликтов.
     * Если строки из обеих версий совпадают – добавляем их без изменений.
     * Если строки отличаются – вставляем конфликт с маркерами:
     * <<<<<<< USER, =======, >>>>>>> SERVER.
     *
     * @param userText Текст, полученный после применения патча пользователя.
     * @param serverText Текст, полученный после применения патча сервера.
     * @return Объединённый текст.
     */
    private fun mergeLineByLine(userText: String, serverText: String): String {
        val userLines = userText.split("\n")
        val serverLines = serverText.split("\n")
        val merged = mutableListOf<String>()
        val maxLines = maxOf(userLines.size, serverLines.size)

        for (i in 0 until maxLines) {
            val lineUser = userLines.getOrNull(i) ?: ""
            val lineServer = serverLines.getOrNull(i) ?: ""

            if (lineUser == lineServer) {
                merged.add(lineUser)
            } else {
                merged.add("<<<<<<< USER")
                merged.add(lineUser)
                merged.add("=======")
                merged.add(lineServer)
                merged.add(">>>>>>> SERVER")
            }
        }

        return merged.joinToString("\n")
    }
}