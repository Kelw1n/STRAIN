package com.texasprogram.app.model

import com.texasprogram.app.service.RuDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

// MARK: - ИИ-тренер
//
// Отвечает Google Gemini по ключу человека — тому же, что и для фото еды.
// Тренер видит программу, максимумы, подходы за последние недели, тяжесть,
// застои, вес тела и питание. Программу сам не меняет: «Применить» есть
// только у проверенных действий — сброс максимума при застое и шаг прибавки.
// Переписка хранится только на телефоне. Правила те же, что на iPhone.

/// Действие, которое предложил тренер. Применяется только по нажатию.
@Serializable
data class CoachAction(
    val type: String,
    val lift: String? = null,
    val exercise: String? = null,
    val step: Double? = null
)

@Serializable
data class CoachMessage(
    val id: String = UUID.randomUUID().toString(),
    /// «user» или «coach».
    val role: String,
    val text: String,
    val epochMillis: Long = System.currentTimeMillis(),
    val actions: List<CoachAction> = emptyList(),
    /// Номера уже применённых действий — кнопка становится галочкой.
    val applied: List<Int> = emptyList(),
    /// Разбор недели: карточка выглядит иначе, а неделя больше не предлагается.
    val reviewWeek: String? = null
) {
    val isUser: Boolean get() = role == "user"
}

/// Переписка с тренером одного профиля — файл на телефоне.
object CoachHistory {
    const val LIMIT = 80
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun directory(base: File): File = File(base, "coach")

    fun read(directory: File, profileId: String): List<CoachMessage> = runCatching {
        json.decodeFromString<List<CoachMessage>>(File(directory, "$profileId.json").readText())
    }.getOrDefault(emptyList())

    fun write(directory: File, profileId: String, messages: List<CoachMessage>) {
        runCatching {
            directory.mkdirs()
            val target = File(directory, "$profileId.json")
            val temp = File(directory, "$profileId.json.tmp")
            temp.writeText(json.encodeToString(messages.takeLast(LIMIT)))
            if (!temp.renameTo(target)) {
                target.writeText(temp.readText())
                temp.delete()
            }
        }
    }

    fun delete(directory: File, profileId: String) {
        File(directory, "$profileId.json").delete()
    }
}

object Coach {
    /// Быстрые вопросы под полем ввода.
    val quickQuestions = listOf(
        "Что улучшить на этой неделе?",
        "Почему встал вес?",
        "Оцени мой прогресс за месяц",
        "Сколько мне есть белка?",
        "Как восстановиться после тяжёлой недели?"
    )

    const val REVIEW_PROMPT = "Разбери мою прошлую неделю с понедельника по воскресенье: что получилось, где отстаю от плана и что поменять на этой неделе. Коротко, по пунктам."

    val system = """
        Ты — тренер по силовым тренировкам в приложении STRAIN. Отвечай по-русски, коротко и по делу: 2–6 предложений или короткий список. Опирайся на данные человека ниже: программу, максимумы, подходы за последние недели, тяжесть, застои, вес тела и питание. Не выдумывай цифры, которых нет в данных; если данных мало — скажи, что записывать. Ты не меняешь программу сам. Если уместно, предложи действия только из этого списка: stall_reset — сбросить максимум движения на 10% при застое (поле lift: squat, bench или deadlift); weight_step — поменять шаг прибавки упражнения (поле exercise — точное название из данных, поле step — одно из 1, 1.25, 2.5, 5, 10). Не предлагай действие, если для него нет причины в данных. Не советуй лекарства и не ставь диагнозы; при боли советуй врача. Ответ — строго JSON без пояснений вокруг: {"reply": "текст ответа", "actions": [{"type": "stall_reset", "lift": "bench"}]}. Если действий нет, actions — пустой список.
    """.trimIndent()

    // MARK: Данные для тренера

    fun context(profile: ProgramProfile, nutrition: NutritionData?, today: LocalDate = LocalDate.now()): String {
        val lines = mutableListOf<String>()
        lines += "Сегодня: ${RuDate.shortDayMonth(today)} ${today.year}, ${RuDate.full(RuDate.weekdayOf(today))}."
        lines += "Программа: ${profile.programKind.title}, неделя ${profile.currentWeek} из ${profile.workoutPlan.weeks.size}, ${profile.trainingDayCount} тренировки в неделю, цикл ${profile.cycleNumber}."
        maxesLine(profile)?.let { lines += it }
        var steps = "Шаг прибавки по умолчанию: ${formatWeight(profile.defaultWeightStep)} кг"
        if (profile.weightSteps.isNotEmpty()) {
            steps += "; свои: " + profile.weightSteps.toSortedMap().entries.joinToString(", ") { "${it.key} — ${formatWeight(it.value)} кг" }
        }
        lines += "$steps."
        val level = Progression.level(profile)
        lines += "Уровень: ${level.level} (${level.title}), опыт ${level.xp}."
        val stalled = profile.stalledLifts
        if (stalled.isNotEmpty()) lines += "Застой по оценкам тяжёлых дней: " + stalled.joinToString(", ") { it.title } + "."
        val weights = profile.bodyWeightSeries.takeLast(6).reversed()
        lines += if (weights.isNotEmpty()) {
            "Вес тела (новые первыми): " + weights.joinToString(", ") { "${formatWeight(it.weight)} кг (${RuDate.shortDayMonth(LocalDate.ofEpochDay(it.epochDay))})" } + "."
        } else {
            "Вес тела не записан."
        }
        nutritionLine(nutrition, today)?.let { lines += it }
        lines += "Тренировки за 6 недель (новые первыми):"
        lines += workoutLines(profile, today)
        var text = lines.joinToString("\n")
        if (text.length > 9000) text = text.take(9000) + "\n…"
        return text
    }

    private fun maxesLine(profile: ProgramProfile): String? {
        if (!supportsStallReset(profile.programKind)) return null
        val kind = if (profile.programKind == TrainingProgramKind.UPPER_LOWER) "1ПМ" else "5ПМ"
        return "Максимумы ($kind): присед ${formatWeight(profile.squat5RM)} кг, жим ${formatWeight(profile.bench5RM)} кг, тяга ${formatWeight(profile.deadlift5RM)} кг."
    }

    private fun nutritionLine(data: NutritionData?, today: LocalDate): String? {
        data ?: return null
        val parts = mutableListOf<String>()
        data.plan?.let { plan ->
            plan.goal?.let { parts += "цель — ${it.title.lowercase()}" }
            plan.target?.let { parts += "норма ${it.kcal.toInt()} ккал, белок ${it.protein.toInt()} г" }
        }
        val zone = ZoneId.systemDefault()
        val start = today.minusDays(7)
        val days = data.entries
            .filter { val day = it.date.atZone(zone).toLocalDate(); !day.isBefore(start) && day.isBefore(today.plusDays(1)) && it.date.toEpochMilli() <= System.currentTimeMillis() }
            .groupBy { it.date.atZone(zone).toLocalDate() }
        if (days.isNotEmpty()) {
            val kcal = days.values.sumOf { list -> list.sumOf { it.nutrients.kcal } } / days.size
            val protein = days.values.sumOf { list -> list.sumOf { it.nutrients.protein } } / days.size
            parts += "в среднем за 7 дней ${Math.round(kcal)} ккал, белок ${Math.round(protein)} г (записано дней: ${days.size})"
        }
        return if (parts.isEmpty()) null else "Питание: " + parts.joinToString("; ") + "."
    }

    private fun workoutLines(profile: ProgramProfile, today: LocalDate): List<String> {
        val since = today.minusDays(42).toEpochDay()
        val latest = HashMap<String, CompletionRecord>()
        for (record in profile.completionLog) {
            if (record.epochDay < since) continue
            val current = latest[record.key]
            if (current != null && current.epochDay >= record.epochDay) continue
            latest[record.key] = record
        }
        val records = latest.values.sortedByDescending { it.epochDay }.take(30)
        if (records.isEmpty()) return listOf("— за 6 недель тренировок нет.")

        // Подходы по дню: ключ «день|упражнение|номер».
        val byDay = HashMap<String, MutableMap<String, MutableList<Pair<Int, SetEntry>>>>()
        for ((key, entry) in profile.setLog) {
            if (entry.reps <= 0) continue
            val parts = key.split("|")
            if (parts.size < 3 || latest[parts[0]] == null) continue
            byDay.getOrPut(parts[0]) { mutableMapOf() }.getOrPut(parts[1]) { mutableListOf() }.add((parts[2].toIntOrNull() ?: 0) to entry)
        }
        return records.map { record ->
            var line = "- ${RuDate.shortDayMonth(LocalDate.ofEpochDay(record.epochDay))}, неделя ${record.week}, день ${record.day}"
            record.effort?.takeIf { it.isNotEmpty() }?.let { line += " (тяжесть: $it)" }
            val exercises = (byDay[record.key] ?: emptyMap()).toSortedMap().map { (name, sets) ->
                "$name " + sets.sortedBy { it.first }.joinToString(", ") { "${formatWeight(it.second.weight)}×${it.second.reps}" }
            }
            line + if (exercises.isEmpty()) ": подходы не записаны" else ": " + exercises.joinToString("; ")
        }
    }

    // MARK: Действия

    fun supportsStallReset(kind: TrainingProgramKind): Boolean =
        kind == TrainingProgramKind.TEXAS || kind == TrainingProgramKind.PRO_TEXAS ||
            kind == TrainingProgramKind.FULL_BODY || kind == TrainingProgramKind.UPPER_LOWER

    data class Resolved(val title: String, val detail: String)

    fun lift(raw: String?): MainLift? = when (raw?.lowercase()) {
        "squat" -> MainLift.SQUAT
        "bench" -> MainLift.BENCH
        "deadlift" -> MainLift.DEADLIFT
        else -> null
    }

    /// Упражнение из данных: тренер иногда пишет название чуть иначе.
    fun exercise(raw: String?, profile: ProgramProfile): String? {
        raw ?: return null
        val key = ProgramProfile.weightStepKey(raw)
        return profile.loggedExerciseNames.firstOrNull { ProgramProfile.weightStepKey(it) == key }
    }

    fun max(lift: MainLift, profile: ProgramProfile): Double = when (lift) {
        MainLift.SQUAT -> profile.squat5RM
        MainLift.BENCH -> profile.bench5RM
        MainLift.DEADLIFT -> profile.deadlift5RM
    }

    fun resetWeight(lift: MainLift, profile: ProgramProfile): Double {
        val current = max(lift, profile)
        val step = profile.weightStep(lift.title)
        var next = ProgramProfile.roundWeight(current * 0.9, step)
        if (next >= current) next = current - step
        return kotlin.math.max(step, next)
    }

    /// Только то, что приложение умеет и что безопасно: иначе кнопки нет.
    fun resolve(action: CoachAction, profile: ProgramProfile): Resolved? = when (action.type) {
        "stall_reset" -> {
            val lift = lift(action.lift)
            if (!supportsStallReset(profile.programKind) || lift == null || max(lift, profile) <= 0) null
            else Resolved(
                "Сбросить ${lift.title.lowercase()} на 10%",
                "Максимум ${formatWeight(max(lift, profile))} кг → ${formatWeight(resetWeight(lift, profile))} кг, веса программы пересчитаются"
            )
        }
        "weight_step" -> {
            val name = exercise(action.exercise, profile)
            val step = action.step
            if (name == null || step == null || step !in ProgramProfile.weightStepOptions || step == profile.weightStep(name)) null
            else Resolved("Шаг прибавки: ${formatWeight(step)} кг", "$name: сейчас ${formatWeight(profile.weightStep(name))} кг")
        }
        else -> null
    }

    fun apply(action: CoachAction, profile: ProgramProfile): ProgramProfile {
        if (resolve(action, profile) == null) return profile
        return when (action.type) {
            "stall_reset" -> when (val lift = lift(action.lift)) {
                MainLift.SQUAT -> profile.copy(squat5RM = resetWeight(lift, profile))
                MainLift.BENCH -> profile.copy(bench5RM = resetWeight(lift, profile))
                MainLift.DEADLIFT -> profile.copy(deadlift5RM = resetWeight(lift, profile))
                null -> profile
            }
            "weight_step" -> {
                val name = exercise(action.exercise, profile) ?: return profile
                profile.setWeightStep(action.step ?: return profile, name)
            }
            else -> profile
        }
    }

    // MARK: Ответ

    data class Reply(val text: String, val actions: List<CoachAction>)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /// Ответ JSON-ом; если модель всё же написала просто текст — берём его как есть.
    fun parse(raw: String): Reply {
        val trimmed = raw.trim()
        val unfenced = trimmed
            .replace(Regex("^```(?:json)?\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*```$"), "")
        val root = runCatching { json.parseToJsonElement(unfenced) as? JsonObject }.getOrNull()
        if (root != null) {
            val text = listOf("reply", "text", "answer").firstNotNullOfOrNull { (root[it] as? JsonPrimitive)?.contentOrNull }.orEmpty()
            val actions = (root["actions"] as? JsonArray).orEmpty().take(3).mapNotNull { item ->
                val obj = item as? JsonObject ?: return@mapNotNull null
                val type = (obj["type"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                val stepValue = obj["step"] as? JsonPrimitive
                val step = stepValue?.doubleOrNull ?: stepValue?.contentOrNull?.replace(',', '.')?.toDoubleOrNull()
                CoachAction(type, (obj["lift"] as? JsonPrimitive)?.contentOrNull, (obj["exercise"] as? JsonPrimitive)?.contentOrNull, step)
            }
            if (text.isNotEmpty()) return Reply(text, actions)
        }
        return Reply(trimmed, emptyList())
    }

    /// Ключ недели для разбора: «2026-W43».
    fun reviewWeek(today: LocalDate = LocalDate.now()): String = WeeklyStatsBuilder.weekKey(today)

    /// Разбор предлагаем в понедельник и вторник, если за эту неделю его ещё не было
    /// и на прошлой неделе были тренировки.
    fun needsReview(messages: List<CoachMessage>, profile: ProgramProfile, today: LocalDate = LocalDate.now()): Boolean {
        val weekday = today.dayOfWeek.value
        if (weekday != 1 && weekday != 2) return false
        val week = reviewWeek(today)
        if (messages.any { it.reviewWeek == week }) return false
        val thisWeek = RuDate.mondayOf(today).toEpochDay()
        val lastWeek = thisWeek - 7
        return profile.completionLog.any { it.epochDay in lastWeek until thisWeek }
    }
}
