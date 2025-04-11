package com.example.utils.theewaysmerge

class ThreeWayMerge2 {

    /**
     * Выполняет трёхстороннее слияние с учетом изменений конкретных строк.
     *
     * Алгоритм:
     * 1. Разбивает базовую, пользовательскую и серверную версии на списки строк (с предварительной нормализацией).
     * 2. Находит "якорные" строки — строки, которые не изменились во всех версиях.
     * 3. Обрабатывает сегменты между якорями:
     *    - Если изменения затронули только одну сторону или обе стороны внесли идентичные изменения,
     *      выбирается изменённый вариант.
     *    - Если обе стороны изменили сегмент относительно базы и эти изменения различны,
     *      вычисляется общий префикс и общий суффикс в этом сегменте, а конфликт формируется только для изменённой
     *      (средней) части.
     *
     * @param base Исходный текст, от которого начинаются изменения.
     * @param user Текст с изменениями пользователя.
     * @param server Текст с изменениями на сервере.
     * @return Результирующий текст после объединения версий.
     */
    fun merge(base: String, user: String, server: String): String {
        // Нормализуем входные данные, убирая завершающие пробелы и переводы строк.
        val baseLines = base.trimEnd().split("\n")
        val userLines = user.trimEnd().split("\n")
        val serverLines = server.trimEnd().split("\n")

        // Находим общие "якорные" строки, которые не изменились во всех версиях.
        val anchors = findCommonAnchors(baseLines, userLines, serverLines)

        val result = mutableListOf<String>()
        var prevBase = 0
        var prevUser = 0
        var prevServer = 0

        // Обрабатываем сегменты между якорями.
        for (anchor in anchors) {
            val (anchorBase, anchorUser, anchorServer) = anchor
            val baseSegment = baseLines.subList(prevBase, anchorBase)
            val userSegment = userLines.subList(prevUser, anchorUser)
            val serverSegment = serverLines.subList(prevServer, anchorServer)

            result.addAll(mergeSegment(baseSegment, userSegment, serverSegment))
            // Якорная строка — неизменённая во всех версиях, добавляем её без изменений.
            result.add(baseLines[anchorBase])

            prevBase = anchorBase + 1
            prevUser = anchorUser + 1
            prevServer = anchorServer + 1
        }

        // Обрабатываем хвостовые сегменты (после последнего якоря).
        val baseSegment = baseLines.subList(prevBase, baseLines.size)
        val userSegment = userLines.subList(prevUser, userLines.size)
        val serverSegment = serverLines.subList(prevServer, serverLines.size)
        result.addAll(mergeSegment(baseSegment, userSegment, serverSegment))

        return result.joinToString("\n")
    }

    /**
     * Ищет общие якорные строки между всеми тремя версиями.
     * Возвращает список троек индексов (индекс в base, user и server), где строки совпадают.
     *
     * Замечание: алгоритм не претендует на 100%-ную точность в сложных случаях,
     * но подходит для сценариев, где изменения небольшие.
     */
    private fun findCommonAnchors(
        baseLines: List<String>,
        userLines: List<String>,
        serverLines: List<String>
    ): List<Triple<Int, Int, Int>> {
        val anchors = mutableListOf<Triple<Int, Int, Int>>()
        var iBase = 0
        var iUser = 0
        var iServer = 0

        while (iBase < baseLines.size && iUser < userLines.size && iServer < serverLines.size) {
            if (baseLines[iBase] == userLines[iUser] && baseLines[iBase] == serverLines[iServer]) {
                anchors.add(Triple(iBase, iUser, iServer))
                iBase++
                iUser++
                iServer++
            } else {
                // Упрощённый подход: смещаем указатели, чтобы найти совпадение.
                if (iUser < userLines.size - 1 && baseLines[iBase] == userLines[iUser + 1] &&
                    iServer < serverLines.size - 1 && baseLines[iBase] == serverLines[iServer + 1]
                ) {
                    iUser++
                    iServer++
                } else if (iUser < userLines.size - 1 && baseLines[iBase] == userLines[iUser + 1]) {
                    iUser++
                } else if (iServer < serverLines.size - 1 && baseLines[iBase] == serverLines[iServer + 1]) {
                    iServer++
                } else {
                    iBase++
                }
            }
        }
        return anchors
    }

    /**
     * Объединяет сегменты между якорными строками.
     *
     * Если изменения отсутствуют или внесены только одной стороной относительно базы – возвращается соответствующий сегмент.
     * Если обе стороны внесли изменения и они различны – выполняется анализ общего префикса и суффикса для данного сегмента,
     * а конфликт формируется только для измененной (средней) части.
     */
    private fun mergeSegment(
        baseSegment: List<String>,
        userSegment: List<String>,
        serverSegment: List<String>
    ): List<String> {
        // Если обе версии совпадают или изменения отсутствуют
        if (userSegment == serverSegment) return userSegment
        if (userSegment == baseSegment) return serverSegment
        if (serverSegment == baseSegment) return userSegment

        // Вычисляем общий префикс и суффикс между пользовательским и серверным сегментами.
        val commonPref = commonPrefix(userSegment, serverSegment)
        val commonSuff = commonSuffix(userSegment, serverSegment)

        // Разбиваем сегменты на три части: общие префикс, измененную (среднюю) часть, общий суффикс.
        val userMiddle = userSegment.subList(commonPref, userSegment.size - commonSuff)
        val serverMiddle = serverSegment.subList(commonPref, serverSegment.size - commonSuff)

        // Если в "средней" части изменений нет – объединяем с учетом неизменных префикс/суффикса.
        if (userMiddle == serverMiddle) return userSegment

        val conflict = mutableListOf<String>()
        // Добавляем общий префикс (если он есть).
        if (commonPref > 0) {
            conflict.addAll(userSegment.subList(0, commonPref))
        }
        // Добавляем конфликтный блок для измененной части.
        conflict.add("<<<<<<< USER")
        conflict.addAll(userMiddle)
        conflict.add("=======")
        conflict.addAll(serverMiddle)
        conflict.add(">>>>>>> SERVER")
        // Добавляем общий суффикс (если он есть).
        if (commonSuff > 0) {
            conflict.addAll(userSegment.subList(userSegment.size - commonSuff, userSegment.size))
        }

        return conflict
    }

    /**
     * Вычисляет длину общего префикса двух списков строк.
     */
    private fun commonPrefix(a: List<String>, b: List<String>): Int {
        var i = 0
        while (i < a.size && i < b.size && a[i] == b[i]) {
            i++
        }
        return i
    }

    /**
     * Вычисляет длину общего суффикса двух списков строк.
     */
    private fun commonSuffix(a: List<String>, b: List<String>): Int {
        var i = 0
        while (i < a.size && i < b.size && a[a.size - 1 - i] == b[b.size - 1 - i]) {
            i++
        }
        return i
    }
}
