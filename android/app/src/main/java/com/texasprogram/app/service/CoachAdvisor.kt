package com.texasprogram.app.service

import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.SetKeyParts
import com.texasprogram.app.model.formatWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/// Ключ доступа к разбору, зашитый в приложение.
///
/// Лежит кусками и в base64 — так его не видно в `strings` над apk. Это не
/// защита: кто захочет, достанет за минуту. Смысл ровно один — чтобы другу
/// не пришлось заводить свой ключ.
object CoachSecrets {
    private val parts = listOf(
        "QVEuQWI4Uk42Sy1aMG1CVnk2",
        "bDBIbTk4ckw2RWFja09vTjJo",
        "RVBETVZ3czlqd1hzZjF5ZlE="
    )

    val embeddedKey: String
        get() = String(Base64.getDecoder().decode(parts.joinToString("")))

    /// Модель по умолчанию. Вынесена в настройки: если Google переименует её,
    /// приложение чинится вводом строки, а не пересборкой.
    const val DEFAULT_MODEL = "gemini-2.5-flash"
}

/// Ошибка разбора с человеческим объяснением.
class CoachException(override val message: String) : Exception(message)

/// Разбор тренировок по заметкам и записанным подходам.
///
/// Считает не модель: веса, прибавки и застой приложение знает само. Модель
/// нужна ровно для прозы — прочитать, что человек написал про самочувствие,
/// и связать это с цифрами.
object CoachAdvisor {

    private val json = Json { ignoreUnknownKeys = true }

    // MARK: - Сбор данных

    /// Есть ли вообще что разбирать.
    fun hasData(profile: ProgramProfile): Boolean =
        profile.workoutNotes.keys.any { profile.isOwnKey(it) } ||
            profile.setLog.isNotEmpty() ||
            profile.liftReports.isNotEmpty()

    /// Собирает всё, что стоит показать модели, в один текст.
    ///
    /// Отправляем только тренировочные цифры и заметки: имя профиля и прочее
    /// личное модели не нужно, поэтому его тут и нет.
    fun context(profile: ProgramProfile): String {
        val lines = mutableListOf<String>()
        val prefix = profile.keyPrefix

        lines += "Программа: ${profile.programKind.title}."
        lines += "Максимумы (${profile.maximumLabel}): присед ${formatWeight(profile.squat5RM)}, " +
            "жим ${formatWeight(profile.bench5RM)}, тяга ${formatWeight(profile.deadlift5RM)} кг."
        lines += "Отмечено тренировок: ${profile.completedDayCount} из ${profile.totalDays}. Цикл ${profile.cycleNumber}."

        val (currentStreak, bestStreak) = profile.weekStreak
        if (currentStreak > 0 || bestStreak > 0) {
            lines += "Серия недель без пропусков: сейчас $currentStreak, лучшая $bestStreak."
        }

        if (profile.activeSkips.isNotEmpty()) {
            lines += "Пропущено: " + profile.activeSkips.joinToString("; ") {
                it.title + if (it.holdsProgression) " (веса задержаны)" else ""
            } + "."
        }

        val tonnage = profile.weeklyTonnage.takeLast(6)
        if (tonnage.isNotEmpty()) {
            lines += "Тоннаж по неделям: " +
                tonnage.joinToString(", ") { "неделя ${it.week} — ${Math.round(it.total)} кг" } + "."
        }

        // Ответы авторегуляции — самое ценное: это оценка усилия своими словами.
        val reports = profile.liftReports.filter { profile.isOwnKey(it.key) }.sortedBy { it.epochDay }.takeLast(9)
        if (reports.isNotEmpty()) {
            lines += "Как проходили тяжёлые дни (от старых к свежим): " +
                reports.joinToString("; ") { "${it.lift.title}: ${it.outcome.title}" } + "."
        }

        if (profile.stalledLifts.isNotEmpty()) {
            lines += "Приложение считает, что встали: " + profile.stalledLifts.joinToString(", ") { it.title } + "."
        }

        // Заметки — ради них всё и затевалось.
        val notes = profile.workoutNotes
            .filterKeys { profile.isOwnKey(it) }
            .toList()
            .sortedBy { order(it.first, prefix) }
            .takeLast(12)
        if (notes.isNotEmpty()) {
            lines += ""
            lines += "Заметки с тренировок:"
            notes.forEach { (key, text) -> lines += "— ${readable(key, prefix)}: $text" }
        }

        val entries = recentEntries(profile)
        if (entries.isNotEmpty()) {
            lines += ""
            lines += "Записанные подходы:"
            lines += entries
        }

        return lines.joinToString("\n")
    }

    private fun recentEntries(profile: ProgramProfile, limit: Int = 24): List<String> {
        val prefix = profile.keyPrefix
        val byDay = mutableMapOf<String, MutableList<String>>()
        for ((key, entry) in profile.setLog) {
            val dayKey = SetKeyParts.dayKey(key)
            if (!profile.isOwnKey(dayKey)) continue
            byDay.getOrPut(dayKey) { mutableListOf() } += "${entry.reps}×${formatWeight(entry.weight)}"
        }
        return byDay.toList()
            .sortedBy { order(it.first, prefix) }
            .takeLast(limit)
            .map { "— ${readable(it.first, prefix)}: ${it.second.joinToString(", ")} кг" }
    }

    /// Ключ дня в вид «неделя 3, день 2» — модель не должна разбирать наши префиксы.
    private fun readable(key: String, prefix: String): String {
        val rest = key.removePrefix(prefix)
        val parts = rest.split("-")
        if (parts.size != 2) return rest
        return "неделя ${parts[0]}, день ${parts[1]}"
    }

    /// Порядок дня внутри цикла: неделя важнее дня, префикс в счёт не идёт.
    private fun order(key: String, prefix: String): Int {
        val parts = key.removePrefix(prefix).split("-").mapNotNull { it.toIntOrNull() }
        val week = parts.firstOrNull() ?: return 0
        return week * 100 + (parts.getOrNull(1) ?: 0)
    }

    // MARK: - Запрос

    private val instruction = """
        Ты опытный тренер по силовым. Отвечай по-русски, коротко и по делу, без вступлений и без списка того, что человек и так видит в приложении.

        Тебе дают цифры тренировок и заметки, которые человек писал после занятий. Цифры приложение считает само — твоя задача связать заметки с цифрами.

        Ответь ровно тремя разделами:
        ЧТО ВИЖУ — два-три предложения о том, что происходит: где растёт, где стоит, что видно по самочувствию из заметок.
        ЧТО ПОМЕНЯТЬ — два-четыре конкретных пункта. Каждый пункт — действие, а не рассуждение: какое упражнение заменить, где снять объём, где добавить.
        НА ЧТО СМОТРЕТЬ — одно-два предложения о том, что отслеживать на ближайших тренировках.

        Не выдумывай данные, которых нет. Если заметок мало, так и скажи в первом разделе и дай советы только по цифрам. Не ставь диагнозов: при боли отправляй к врачу, а не советуй лечение. Не используй markdown-разметку.
    """.trimIndent()

    suspend fun analyze(profile: ProgramProfile, key: String, model: String): String = withContext(Dispatchers.IO) {
        if (!hasData(profile)) throw CoachException("Пока нечего разбирать: нет ни заметок, ни записанных подходов.")

        val name = model.trim().ifEmpty { CoachSecrets.DEFAULT_MODEL }
        val apiKey = key.trim().ifEmpty { CoachSecrets.embeddedKey }
        val body = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { add(buildJsonObject { put("text", instruction) }) }
            }
            putJsonArray("contents") {
                add(buildJsonObject {
                    put("role", "user")
                    putJsonArray("parts") { add(buildJsonObject { put("text", context(profile)) }) }
                })
            }
            putJsonObject("generationConfig") {
                put("temperature", 0.4)
                put("maxOutputTokens", 1200)
            }
        }.toString()

        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$name:generateContent")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            // Ключ уходит заголовком, а не в адресе: в адресной строке он попал бы в логи.
            setRequestProperty("x-goog-api-key", apiKey)
        }

        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()

            if (code !in 200..299) throw CoachException(explain(code, text))
            val answer = firstText(text)
            if (answer.isNullOrBlank()) throw CoachException("Ответ пришёл пустым. Попробуй ещё раз.")
            answer
        } catch (error: CoachException) {
            throw error
        } catch (error: Exception) {
            throw CoachException("Не получилось связаться с сервером. Проверь интернет.")
        } finally {
            connection.disconnect()
        }
    }

    /// Переводит ответ Google в понятную строку.
    private fun explain(code: Int, raw: String): String {
        val error = runCatching { json.parseToJsonElement(raw).jsonObject["error"]?.jsonObject }.getOrNull()
        val message = error?.get("message")?.jsonPrimitive?.content.orEmpty()
        val status = error?.get("status")?.jsonPrimitive?.content.orEmpty()
        return when {
            status == "FAILED_PRECONDITION" || message.contains("location is not supported") ->
                "Google не отвечает из этого региона. Включи VPN и попробуй снова."
            code == 401 || code == 403 || message.contains("API key not valid") ->
                "Ключ не принят. Проверь его в настройках."
            code == 429 -> "Лимит запросов исчерпан. Попробуй позже."
            else -> "Google ответил ошибкой $code. $message"
        }
    }

    /// Достаёт текст ответа, не полагаясь на то, что часть ровно одна.
    private fun firstText(raw: String): String? = runCatching {
        json.parseToJsonElement(raw).jsonObject["candidates"]?.jsonArray
            ?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?.mapNotNull { (it as? JsonObject)?.get("text")?.jsonPrimitive?.content }
            ?.joinToString("")
            ?.trim()
    }.getOrNull()
}
