package com.texasprogram.app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/// Что пользователь сделал с упражнением в дне программы.
/// Имена в файле совпадают с iOS-версией, чтобы копия читалась на обеих платформах.
@Serializable
enum class PlanEditKind {
    /// Своё упражнение добавлено в конец дня.
    @SerialName("add") ADD,

    /// Упражнение программы заменено своим — на том же месте в списке.
    @SerialName("replace") REPLACE,

    /// Упражнение программы убрано из дня.
    @SerialName("hide") HIDE,

    /// Свой порядок упражнений внутри дня.
    @SerialName("order") ORDER
}

/// Насколько далеко расходится правка.
enum class PlanEditScope(val title: String) {
    /// Одна конкретная тренировка: эта неделя, этот день.
    SINGLE("Только эта"),

    /// Этот день во всех неделях программы.
    EVERY_WEEK("Каждую неделю");

    val explanation: String
        get() = when (this) {
            SINGLE -> "Изменится только эта тренировка. Остальные недели останутся как были."
            EVERY_WEEK -> "Изменится этот день во всех неделях программы. Правка останется навсегда, пока её не убрать."
        }
}

/// Пользовательская правка дня программы.
///
/// Правки хранятся в профиле, а не в готовом плане: план пересчитывается заново
/// при каждом изменении максимумов, и всё, что лежало бы внутри него, терялось бы.
@Serializable
data class PlanEdit(
    /// Он же идентификатор упражнения, если правка добавляет своё. Префикс
    /// отделяет свои упражнения от программных, у которых ключ — имя.
    val id: String = "custom-" + UUID.randomUUID().toString(),
    val kind: PlanEditKind,
    /// Номер дня программы: 1…3 у «Техаса», 1…4 у «Верх / Низ».
    val day: Int,
    /// Неделя разовой правки. `null` — правка действует во всех неделях.
    val week: Int? = null,
    /// Пикирование нумерует недели с единицы, поэтому его правки лежат отдельно.
    val isPeaking: Boolean = false,
    /// Что заменяем или прячем — ключ упражнения программы, то есть его имя.
    @SerialName("targetID") val targetKey: String? = null,
    /// Своё упражнение. У HIDE не используется.
    val name: String = "",
    val sets: Int = 3,
    val reps: String = "8–12",
    /// Конкретный вес в килограммах, если он задан.
    val kilograms: Double? = null,
    /// Свободная подпись нагрузки, когда веса нет: «RPE 8», «до отказа».
    val loadText: String? = null,
    /// Порядок упражнений дня для правки вида ORDER. Ключи, которых в списке
    /// нет, остаются в конце: план мог измениться после перестановки.
    val order: List<String>? = null
) {
    val scope: PlanEditScope
        get() = if (week == null) PlanEditScope.EVERY_WEEK else PlanEditScope.SINGLE

    /// Действует ли правка на конкретную тренировку.
    fun matches(week: Int, day: Int, isPeaking: Boolean): Boolean {
        if (this.day != day || this.isPeaking != isPeaking) return false
        return this.week == null || this.week == week
    }

    /// Нагрузка: сначала точный вес, потом подпись, иначе RPE по умолчанию.
    val load: LoadPrescription
        get() {
            if (kilograms != null) return LoadPrescription.Kilograms(kilograms)
            if (!loadText.isNullOrBlank()) return LoadPrescription.RepRange(loadText)
            return LoadPrescription.Rpe("RPE 8")
        }

    /// Упражнение, которое встанет в план.
    val prescription: ExercisePrescription
        get() = ExercisePrescription(
            name = name,
            sets = sets,
            reps = reps,
            load = load,
            isOptional = true,
            id = id
        )

    /// Короткое описание для списка правок.
    val summary: String
        get() = when (kind) {
            PlanEditKind.HIDE -> "Убрано"
            PlanEditKind.REPLACE -> "Заменено на «$name»"
            PlanEditKind.ADD -> "Добавлено «$name»"
            PlanEditKind.ORDER -> "Свой порядок"
        }

    val detail: String
        get() = (if (sets > 0) "$sets × $reps" else reps) + " · " + load.displayText
}
