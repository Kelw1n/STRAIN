package com.texasprogram.app.service

import com.texasprogram.app.model.ProgramProfile

/// Выгрузка записанных подходов таблицей.
///
/// Резервная копия в JSON нужна, чтобы перенести всё на другой телефон, — глазами
/// её не почитаешь. Здесь наоборот: файл, который открывается в Excel и годится,
/// чтобы посчитать своё.
object CSVExport {

    const val FILE_NAME = "strain-log.csv"

    /// Разделитель — точка с запятой, а не запятая.
    ///
    /// В русской локали Excel запятая занята под десятичный знак, и файл с ней
    /// открывается одной колонкой. Вес пишем тоже с запятой — так его сразу
    /// видно числом, а не текстом.
    private const val SEPARATOR = ";"

    fun text(profiles: List<ProgramProfile>): String {
        val rows = mutableListOf("Профиль;Программа;Цикл;Неделя;День;Упражнение;Подход;Повторы;Вес;Тоннаж")

        for (profile in profiles) {
            for (week in profile.workoutPlan.weeks) {
                for (day in week.days) {
                    for (exercise in day.exercises) {
                        for (index in 0 until maxOf(exercise.sets, 0)) {
                            val entry = profile.setEntry(week.number, day.number, exercise, index) ?: continue
                            rows += listOf(
                                escape(profile.name),
                                escape(profile.programKind.title),
                                "${profile.cycleNumber}",
                                "${week.number}",
                                "${day.number}",
                                escape(exercise.name),
                                "${index + 1}",
                                "${entry.reps}",
                                number(entry.weight),
                                number(entry.tonnage)
                            ).joinToString(SEPARATOR)
                        }
                    }
                }
            }
        }

        return rows.joinToString("\r\n") + "\r\n"
    }

    /// Без метки кодировки Excel на Windows читает кириллицу как мусор.
    fun bytes(profiles: List<ProgramProfile>): ByteArray =
        byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text(profiles).toByteArray(Charsets.UTF_8)

    /// Один знак после запятой: веса идут с шагом 2,5, тоннаж — их кратное,
    /// так что второй знак всегда ноль и только мешает читать.
    ///
    /// Локаль фиксируем: иначе на английском телефоне выйдет точка, а на
    /// русском запятая, и файл стал бы зависеть от настроек системы.
    private fun number(value: Double): String {
        val text = if (value == Math.floor(value)) value.toInt().toString()
        else String.format(java.util.Locale.ROOT, "%.1f", value)
        return text.replace('.', ',')
    }

    /// Точка с запятой и кавычки внутри названия сломали бы разбор.
    private fun escape(text: String): String {
        if (!text.contains(SEPARATOR) && !text.contains("\"") && !text.contains("\n")) return text
        return "\"" + text.replace("\"", "\"\"") + "\""
    }
}
