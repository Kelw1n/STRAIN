package com.texasprogram.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.texasprogram.app.model.FoodEntry
import com.texasprogram.app.model.LimarMethod
import com.texasprogram.app.model.NormChange
import com.texasprogram.app.model.Nutrients
import com.texasprogram.app.model.NutritionData
import com.texasprogram.app.model.NutritionGoal
import com.texasprogram.app.model.NutritionPlan
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/// Дневник питания открытого профиля.
///
/// Каждый профиль — свой файл в папке приложения, а не поле профиля:
/// если вкладку «Питание» уберут, профили откроются как ни в чём не бывало.
/// Формат файла тот же, что у питания в копии iOS.
class NutritionStore(private val directory: File) {

    var profileId by mutableStateOf<String?>(null)
        private set
    var data by mutableStateOf(NutritionData())
        private set

    /// Профиль сменился — читаем его файл. Тот же профиль второй раз не перечитываем.
    fun open(profileId: String) {
        if (this.profileId == profileId) return
        this.profileId = profileId
        data = read(directory, profileId) ?: NutritionData()
    }

    /// После восстановления из копии файл мог смениться под открытым профилем.
    fun reload() {
        val id = profileId ?: return
        data = read(directory, id) ?: NutritionData()
    }

    // MARK: Записи

    fun add(entries: List<FoodEntry>) {
        if (entries.isEmpty()) return
        save(data.copy(entries = data.entries + entries))
    }

    fun update(entry: FoodEntry) {
        if (data.entries.none { it.id == entry.id }) return
        save(data.copy(entries = data.entries.map { if (it.id == entry.id) entry else it }))
    }

    fun remove(entry: FoodEntry) {
        save(data.copy(entries = data.entries.filterNot { it.id == entry.id }))
    }

    /// Недавние продукты без повторов по названию — добавить ещё раз в одно касание.
    fun recentFoods(limit: Int = 12): List<FoodEntry> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<FoodEntry>()
        for (entry in data.entries.sortedByDescending { it.date }) {
            val key = entry.name.trim().lowercase()
            if (key.isEmpty() || !seen.add(key)) continue
            result += entry
            if (result.size == limit) break
        }
        return result
    }

    // MARK: Метод

    /// Новая неделя наблюдения с сегодняшнего дня. Записи еды остаются.
    fun startObservation(day: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()) {
        save(data.copy(plan = NutritionPlan(observationStart = LimarMethod.startOf(day, zone))))
    }

    fun choose(goal: NutritionGoal, maintenance: Nutrients, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()) {
        val plan = data.plan ?: return
        val start = LimarMethod.startOf(today, zone)
        val norm = LimarMethod.startingNorm(goal, maintenance)
        save(
            data.copy(
                plan = plan.copy(
                    maintenance = maintenance,
                    goal = goal,
                    target = norm,
                    stepStart = start,
                    history = plan.history + NormChange(start, norm, goal.title)
                )
            )
        )
    }

    /// Новая норма после недельной проверки — или прежняя ещё на неделю.
    fun apply(norm: Nutrients, note: String, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()) {
        val plan = data.plan ?: return
        val start = LimarMethod.startOf(today, zone)
        save(data.copy(plan = plan.copy(target = norm, stepStart = start, history = plan.history + NormChange(start, norm, note))))
    }

    fun resetPlan() {
        save(data.copy(plan = null))
    }

    // MARK: Файлы

    private fun save(next: NutritionData) {
        data = next
        val id = profileId ?: return
        write(directory, id, next)
    }

    companion object {
        @Volatile
        private var instance: NutritionStore? = null

        fun get(context: Context): NutritionStore =
            instance ?: synchronized(this) {
                instance ?: NutritionStore(directory(context)).also { instance = it }
            }

        fun directory(context: Context): File = File(context.applicationContext.filesDir, "nutrition")

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun file(directory: File, profileId: String): File = File(directory, "$profileId.json")

        fun read(directory: File, profileId: String): NutritionData? = runCatching {
            val file = file(directory, profileId)
            if (!file.exists()) return null
            json.decodeFromString<NutritionData>(file.readText())
        }.getOrNull()

        /// Через временный файл: оборванная запись не портит прошлый дневник.
        fun write(directory: File, profileId: String, data: NutritionData) {
            runCatching {
                directory.mkdirs()
                val target = file(directory, profileId)
                val temporary = File(directory, "$profileId.json.tmp")
                temporary.writeText(json.encodeToString(NutritionData.serializer(), data))
                if (!temporary.renameTo(target)) {
                    target.delete()
                    temporary.renameTo(target)
                }
            }
        }

        fun delete(directory: File, profileId: String) {
            runCatching { file(directory, profileId).delete() }
        }
    }
}

/// Свой ключ Google AI у каждого — в закрытых настройках приложения, как код
/// входа в ленту. В коде приложения и в репозитории ключей нет.
object GeminiKeyStore {
    private const val PREFS = "strain.gemini"
    private const val KEY = "api-key"

    fun load(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)?.takeIf { it.isNotBlank() }

    fun save(context: Context, key: String?) {
        val value = key?.trim().orEmpty()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (value.isEmpty()) remove(KEY) else putString(KEY, value)
        }.apply()
    }
}
