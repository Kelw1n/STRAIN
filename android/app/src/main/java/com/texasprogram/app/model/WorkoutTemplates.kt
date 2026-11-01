@file:UseSerializers(IsoInstantSerializer::class)

package com.texasprogram.app.model

import android.content.Context
import com.texasprogram.app.service.ScheduledWorkout
import com.texasprogram.app.service.RuDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// MARK: - Библиотека упражнений

/// Упражнение библиотеки: из общего файла приложения или своё.
/// Файл один на iOS и Android — `exercise-library.json`.
@Serializable
data class LibraryExercise(
    val id: String,
    val name: String,
    /// Группа мышц — ключ из `ExerciseLibrary.groups`.
    val group: String = "other",
    val secondary: List<String> = emptyList(),
    /// Инвентарь — ключ из `ExerciseLibrary.equipment`.
    val equipment: String = "other",
    val steps: List<String> = emptyList()
) {
    val isCustom: Boolean get() = id.startsWith(CUSTOM_PREFIX)

    companion object {
        /// Свои упражнения — с приставкой, чтобы не спутать с библиотечными.
        const val CUSTOM_PREFIX = "my-"

        fun custom(name: String, group: String, equipment: String) =
            LibraryExercise(CUSTOM_PREFIX + newNutritionId(), name, group, emptyList(), equipment)
    }
}

object ExerciseLibrary {
    @Serializable
    data class Option(val id: String, val title: String)

    @Serializable
    private data class LibraryFile(
        val groups: List<Option> = emptyList(),
        val equipment: List<Option> = emptyList(),
        val exercises: List<LibraryExercise> = emptyList()
    )

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var file = LibraryFile()
    @Volatile
    private var byName: Map<String, LibraryExercise> = emptyMap()

    /// Читает файл из ресурсов приложения. Повторный вызов ничего не делает.
    fun load(context: Context) {
        if (file.exercises.isNotEmpty()) return
        runCatching {
            context.applicationContext.assets.open("exercise-library.json").bufferedReader().use { load(it.readText()) }
        }
    }

    /// Для тестов и снимков — из текста файла.
    fun load(text: String) {
        val parsed = runCatching { json.decodeFromString<LibraryFile>(text) }.getOrNull() ?: return
        file = parsed
        byName = parsed.exercises.associateBy { normalized(it.name) }
    }

    val groups: List<Option> get() = file.groups
    val equipment: List<Option> get() = file.equipment
    val builtIn: List<LibraryExercise> get() = file.exercises

    fun groupTitle(id: String): String = file.groups.firstOrNull { it.id == id }?.title ?: "Другое"
    fun equipmentTitle(id: String): String = file.equipment.firstOrNull { it.id == id }?.title ?: "Другое"

    fun normalized(name: String): String = name.lowercase().replace('ё', 'е').trim()

    /// Библиотека вместе со своими: свои — первыми, они нужнее.
    fun all(custom: List<LibraryExercise>): List<LibraryExercise> = custom + builtIn

    /// Упражнение по названию — для «i» у упражнений шаблонов.
    fun find(name: String, custom: List<LibraryExercise> = emptyList()): LibraryExercise? {
        val key = normalized(name)
        return custom.firstOrNull { normalized(it.name) == key } ?: byName[key]
    }

    /// Поиск по словам в любом порядке: «жим гант» найдёт «Жим гантелей лёжа».
    fun filter(items: List<LibraryExercise>, query: String, group: String?, equipment: String?): List<LibraryExercise> {
        val words = normalized(query).split(' ').filter { it.isNotEmpty() }
        return items.filter { item ->
            if (group != null && item.group != group && group !in item.secondary) return@filter false
            if (equipment != null && item.equipment != equipment) return@filter false
            val name = normalized(item.name)
            words.all { name.contains(it) }
        }
    }

    /// Разделы по группам мышц в порядке файла. При фильтре по мышце
    /// упражнение со второстепенной мышцей идёт в её раздел.
    fun sections(items: List<LibraryExercise>, group: String? = null): List<Pair<Option, List<LibraryExercise>>> {
        val order = groups + Option("other", "Другое")
        val grouped = items.groupBy { item ->
            group ?: if (order.any { it.id == item.group }) item.group else "other"
        }
        return order.mapNotNull { option -> grouped[option.id]?.takeIf { it.isNotEmpty() }?.let { option to it } }
    }
}

// MARK: - Шаблоны тренировок

/// Подход шаблона. Ноль килограммов — вес возьмётся из прошлого раза.
@Serializable
data class TemplateSet(val weight: Double, val reps: Int)

@Serializable
data class TemplateExercise(
    val id: String = "tx-" + newNutritionId(),
    val name: String,
    val restSeconds: Int = 120,
    val sets: List<TemplateSet> = List(3) { TemplateSet(0.0, 10) }
) {
    /// Упражнение для тренировки: подходов столько, сколько строк; вес — из
    /// первой строки, где он вписан, иначе из истории.
    val prescription: ExercisePrescription
        get() {
            val reps = sets.firstOrNull()?.reps ?: 10
            val weight = sets.firstOrNull { it.weight > 0 }?.weight
            return ExercisePrescription(
                name = name,
                sets = maxOf(sets.size, 1),
                reps = "$reps",
                load = weight?.let { LoadPrescription.Kilograms(it) } ?: LoadPrescription.RepRange("$reps"),
                id = id
            )
        }
}

/// Своя тренировка, как в REBRN. Запускается в любой день и не трогает
/// программу: подходы идут в историю, рекорды и серию.
@Serializable
data class WorkoutTemplate(
    val id: String = newNutritionId(),
    val name: String,
    val exercises: List<TemplateExercise> = emptyList(),
    /// Показывать в профиле — тогда шаблон видят в «Сообществе».
    val isShared: Boolean = false,
    val createdAt: Instant = Instant.now()
) {
    val setCount: Int get() = exercises.sumOf { it.sets.size }

    /// «7 упр · 20 подх» — как в списке REBRN.
    val summary: String get() = "${exercises.size} упр · $setCount подх"

    fun groups(custom: List<LibraryExercise> = emptyList()): List<String> =
        exercises.mapNotNull { ExerciseLibrary.find(it.name, custom)?.group }
            .map { ExerciseLibrary.groupTitle(it) }
            .distinct()

    /// Копия под своим именем — из каталога, сообщества или «Дублировать».
    fun duplicate(named: String? = null): WorkoutTemplate = WorkoutTemplate(
        name = named ?: name,
        exercises = exercises.map { TemplateExercise(name = it.name, restSeconds = it.restSeconds, sets = it.sets) }
    )

    companion object {
        /// Программа из чужого профиля: «4 × 8», «80 кг» — в подходы шаблона.
        fun from(title: String, exercises: List<SocialProgramExercise>): WorkoutTemplate = WorkoutTemplate(
            name = title,
            exercises = exercises.map { item ->
                val reps = Regex("\\d+").find(item.reps)?.value?.toIntOrNull() ?: 10
                val weight = Regex("\\d+(?:[.,]\\d+)?").find(item.load)?.value?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
                TemplateExercise(name = item.name, sets = List(item.sets.coerceIn(1, 10)) { TemplateSet(weight, reps) })
            }
        )
    }
}

/// Упражнение программы из чужого профиля — то, что приходит с сервера.
data class SocialProgramExercise(val name: String, val sets: Int, val reps: String, val load: String)

/// Готовые тренировки — вкладка «Каталог». Как на iOS — `TemplateCatalog`.
object TemplateCatalog {
    private fun ex(name: String, sets: Int, reps: Int, rest: Int = 120) =
        TemplateExercise(id = "cat-$name", name = name, restSeconds = rest, sets = List(sets) { TemplateSet(0.0, reps) })

    private val epoch: Instant = Instant.EPOCH

    val all: List<WorkoutTemplate> = listOf(
        WorkoutTemplate("catalog-fullbody-a", "Фулбади A", listOf(
            ex("Приседания со штангой", 3, 8, 180), ex("Жим штанги лёжа", 3, 8, 180),
            ex("Тяга штанги в наклоне", 3, 10), ex("Жим гантелей сидя", 3, 10),
            ex("Сгибание ног лёжа", 3, 12, 90), ex("Подъём гантелей на бицепс", 2, 12, 90),
            ex("Планка", 3, 45, 60)
        ), createdAt = epoch),
        WorkoutTemplate("catalog-fullbody-b", "Фулбади B", listOf(
            ex("Становая тяга", 3, 5, 180), ex("Жим штанги стоя", 3, 8, 150),
            ex("Тяга верхнего блока", 3, 10), ex("Жим ногами", 3, 12),
            ex("Разведение гантелей лёжа", 3, 12, 90), ex("Разгибания на блоке с канатом", 3, 12, 90),
            ex("Подъём ног в висе", 3, 12, 60)
        ), createdAt = epoch),
        WorkoutTemplate("catalog-fullbody-c", "Фулбади C", listOf(
            ex("Фронтальные приседания", 3, 8, 180), ex("Жим гантелей на наклонной", 3, 10),
            ex("Подтягивания", 3, 8), ex("Румынская тяга", 3, 10, 150),
            ex("Махи гантелями в стороны", 3, 15, 60), ex("Молотки с гантелями", 3, 12, 90),
            ex("Подъём на носки стоя", 3, 15, 60)
        ), createdAt = epoch),
        WorkoutTemplate("catalog-upper", "Верх", listOf(
            ex("Жим штанги лёжа", 4, 6, 180), ex("Тяга штанги в наклоне", 4, 8, 150),
            ex("Жим гантелей сидя", 3, 10), ex("Тяга верхнего блока", 3, 10),
            ex("Подъём штанги на бицепс", 3, 10, 90), ex("Французский жим лёжа", 3, 10, 90)
        ), createdAt = epoch),
        WorkoutTemplate("catalog-lower", "Низ", listOf(
            ex("Приседания со штангой", 4, 6, 180), ex("Румынская тяга", 3, 8, 150),
            ex("Жим ногами", 3, 12), ex("Сгибание ног сидя", 3, 12, 90),
            ex("Подъём на носки стоя", 4, 12, 60), ex("Скручивания на блоке стоя на коленях", 3, 15, 60)
        ), createdAt = epoch),
        WorkoutTemplate("catalog-push", "Жим", listOf(
            ex("Жим штанги лёжа", 4, 8, 180), ex("Жим гантелей на наклонной", 3, 10),
            ex("Жим штанги стоя", 3, 8, 150), ex("Сведение в кроссовере", 3, 12, 90),
            ex("Махи гантелями в стороны", 4, 15, 60), ex("Разгибания на верхнем блоке", 3, 12, 90)
        ), createdAt = epoch),
        WorkoutTemplate("catalog-pull", "Тяга", listOf(
            ex("Становая тяга", 3, 5, 180), ex("Подтягивания", 4, 8, 150),
            ex("Тяга горизонтального блока", 3, 10), ex("Тяга каната к лицу", 3, 15, 60),
            ex("Подъём штанги на бицепс", 3, 10, 90), ex("Молотки с гантелями", 3, 12, 90)
        ), createdAt = epoch),
        WorkoutTemplate("catalog-legs", "Ноги", listOf(
            ex("Приседания со штангой", 4, 8, 180), ex("Жим ногами", 3, 12),
            ex("Румынская тяга", 3, 10, 150), ex("Разгибания ног в тренажёре", 3, 15, 90),
            ex("Сгибание ног лёжа", 3, 12, 90), ex("Подъём на носки сидя", 4, 15, 60)
        ), createdAt = epoch),
        WorkoutTemplate("catalog-arms", "Руки", listOf(
            ex("Жим лёжа узким хватом", 3, 8, 150), ex("Подъём EZ-грифа на бицепс", 3, 10, 90),
            ex("Французский жим лёжа", 3, 10, 90), ex("Молотки с гантелями", 3, 12, 90),
            ex("Разгибания на блоке с канатом", 3, 12, 60), ex("Сгибания на нижнем блоке", 3, 15, 60)
        ), createdAt = epoch),
        WorkoutTemplate("catalog-home", "Дома без железа", listOf(
            ex("Отжимания", 4, 15, 60), ex("Приседания с выпрыгиванием", 4, 15, 60),
            ex("Австралийские подтягивания", 3, 12, 60), ex("Выпады в ходьбе", 3, 20, 60),
            ex("Обратные отжимания от скамьи", 3, 15, 60), ex("Планка", 3, 60, 45),
            ex("Бёрпи", 3, 10, 60)
        ), createdAt = epoch)
    )
}

// MARK: - Свободная тренировка

/// Упражнение свободной тренировки — простыми полями, одинаковыми с iOS.
@Serializable
data class FreeExercise(
    val id: String,
    val name: String,
    val sets: Int,
    val reps: String,
    val kilograms: Double? = null
) {
    val prescription: ExercisePrescription
        get() = ExercisePrescription(
            name = name,
            sets = sets,
            reps = reps,
            load = kilograms?.let { LoadPrescription.Kilograms(it) } ?: LoadPrescription.RepRange(reps),
            id = id
        )

    companion object {
        fun of(prescription: ExercisePrescription) = FreeExercise(
            id = prescription.key,
            name = prescription.name,
            sets = prescription.sets,
            reps = prescription.reps,
            kilograms = (prescription.load as? LoadPrescription.Kilograms)?.value
        )
    }
}

/// Тренировка вне программы: из своего шаблона, каталога или пустая.
/// Живёт в «нулевой неделе»: ключ подходов — `free-N`, программа его не видит.
@Serializable
data class FreeWorkoutDay(
    val number: Int,
    val title: String,
    val templateID: String? = null,
    val date: Instant,
    val exercises: List<FreeExercise> = emptyList(),
    val completed: Boolean = false
) {
    val plan: WorkoutDayPlan
        get() = WorkoutDayPlan(ProgramProfile.freeKey(number), number, title, exercises.map { it.prescription })

    fun epochDay(zone: ZoneId = ZoneId.systemDefault()): Long = date.atZone(zone).toLocalDate().toEpochDay()
}

// MARK: - Профиль

fun ProgramProfile.freeDay(number: Int): FreeWorkoutDay? = freeWorkouts.firstOrNull { it.number == number }

/// Свободные тренировки неделей плана — для истории и рекордов.
val ProgramProfile.freeWeekPlan: WorkoutWeekPlan?
    get() = if (freeWorkouts.isEmpty()) null
    else WorkoutWeekPlan(ProgramProfile.FREE_WEEK, freeWorkouts.map { it.plan })

/// Недели, по которым читается история: программа и свободные тренировки.
val ProgramProfile.historyWeeks: List<WorkoutWeekPlan>
    get() = freeWeekPlan?.let { workoutPlan.weeks + it } ?: workoutPlan.weeks

/// День по неделе и номеру — и в программе, и среди свободных.
fun ProgramProfile.dayPlan(week: Int, day: Int): WorkoutDayPlan? =
    if (week == ProgramProfile.FREE_WEEK) freeDay(day)?.plan
    else workoutPlan.weeks.firstOrNull { it.number == week }?.days?.firstOrNull { it.number == day }

/// Подпись дня в истории: «Неделя 3, день 2» или название своей тренировки.
fun ProgramProfile.historyTitle(week: Int, day: Int): String =
    if (week == ProgramProfile.FREE_WEEK) freeDay(day)?.title ?: "Своя тренировка" else "Неделя $week, день $day"

/// Начинает свободную тренировку: день появляется сразу, чтобы подходы
/// писались по ходу. Возвращает профиль с днём и саму тренировку.
fun ProgramProfile.startFreeWorkout(
    title: String,
    exercises: List<ExercisePrescription>,
    templateID: String? = null,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault()
): Pair<ProgramProfile, ScheduledWorkout> {
    val number = (freeWorkouts.maxOfOrNull { it.number } ?: 0) + 1
    val day = FreeWorkoutDay(number, title, templateID, now, exercises.map { FreeExercise.of(it) })
    val date = now.atZone(zone).toLocalDate()
    val workout = ScheduledWorkout(
        week = ProgramProfile.FREE_WEEK,
        day = day.plan,
        date = date,
        weekday = RuDate.weekdayOf(date),
        isCompleted = false
    )
    return copy(freeWorkouts = freeWorkouts + day) to workout
}

fun ProgramProfile.startWorkout(template: WorkoutTemplate, now: Instant = Instant.now()): Pair<ProgramProfile, ScheduledWorkout> =
    startFreeWorkout(template.name, template.exercises.map { it.prescription }, template.id, now)

/// Отменили свободную тренировку, которую ещё не закончили, — её как не было.
fun ProgramProfile.discardFreeWorkout(number: Int): ProgramProfile {
    val day = freeDay(number) ?: return this
    if (day.completed) return this
    val prefix = ProgramProfile.freeKey(number) + "|"
    return copy(
        setLog = setLog.filterKeys { !it.startsWith(prefix) },
        setProgress = setProgress.filterKeys { !it.startsWith(prefix) },
        freeWorkouts = freeWorkouts.filterNot { it.number == number }
    )
}

/// Удаляет закрытую свободную тренировку из истории целиком.
fun ProgramProfile.deleteFreeWorkout(number: Int): ProgramProfile {
    val key = ProgramProfile.freeKey(number)
    return copy(
        setLog = setLog.filterKeys { !it.startsWith("$key|") },
        setProgress = setProgress.filterKeys { !it.startsWith("$key|") },
        completionLog = completionLog.filterNot { it.key == key },
        freeWorkouts = freeWorkouts.filterNot { it.number == number }
    )
}

/// Отметка свободной тренировки: в историю — запись с датой, а дни программы
/// и «последняя тренировка» очереди не трогаются.
fun ProgramProfile.setFreeCompleted(
    number: Int,
    done: Boolean,
    now: Instant = Instant.now(),
    durationSeconds: Int? = null,
    effort: String? = null,
    zone: ZoneId = ZoneId.systemDefault()
): ProgramProfile {
    val day = freeDay(number) ?: return this
    val key = ProgramProfile.freeKey(number)
    val rest = completionLog.filterNot { it.key == key }
    val updated = freeWorkouts.map {
        if (it.number != number) it else it.copy(completed = done, date = if (done) now else it.date)
    }
    if (!done) return copy(freeWorkouts = updated, completionLog = rest)
    val record = CompletionRecord(
        key = key,
        epochDay = now.atZone(zone).toLocalDate().toEpochDay(),
        week = ProgramProfile.FREE_WEEK,
        day = day.number,
        durationSeconds = durationSeconds,
        effort = effort
    )
    return copy(freeWorkouts = updated, completionLog = rest + record)
}

/// Замена упражнения в свободной тренировке — прямо в её дне, без правок программы.
fun ProgramProfile.replaceFreeExercise(number: Int, old: ExercisePrescription, name: String): Pair<ProgramProfile, ExercisePrescription> {
    val fresh = ExercisePrescription(name = name, sets = old.sets, reps = old.reps, load = LoadPrescription.RepRange(old.reps), id = "tx-" + newNutritionId())
    val updated = freeWorkouts.map { day ->
        if (day.number != number) day
        else {
            val index = day.exercises.indexOfFirst { it.id == old.key }
            val list = if (index >= 0) day.exercises.toMutableList().also { it[index] = FreeExercise.of(fresh) }
            else day.exercises + FreeExercise.of(fresh)
            day.copy(exercises = list)
        }
    }
    return copy(freeWorkouts = updated) to fresh
}

/// Добавляет упражнения в идущую свободную тренировку.
fun ProgramProfile.appendFreeExercises(number: Int, list: List<ExercisePrescription>): ProgramProfile =
    copy(freeWorkouts = freeWorkouts.map { day ->
        if (day.number != number) day else day.copy(exercises = day.exercises + list.map { FreeExercise.of(it) })
    })

// MARK: Шаблоны и свои упражнения

fun ProgramProfile.saveTemplate(template: WorkoutTemplate): ProgramProfile =
    if (workoutTemplates.any { it.id == template.id }) copy(workoutTemplates = workoutTemplates.map { if (it.id == template.id) template else it })
    else copy(workoutTemplates = workoutTemplates + template)

fun ProgramProfile.deleteTemplate(id: String): ProgramProfile = copy(workoutTemplates = workoutTemplates.filterNot { it.id == id })

fun ProgramProfile.saveCustomExercise(exercise: LibraryExercise): ProgramProfile =
    if (customExercises.any { it.id == exercise.id }) copy(customExercises = customExercises.map { if (it.id == exercise.id) exercise else it })
    else copy(customExercises = listOf(exercise) + customExercises)

fun ProgramProfile.deleteCustomExercise(id: String): ProgramProfile = copy(customExercises = customExercises.filterNot { it.id == id })

val ProgramProfile.exerciseLibrary: List<LibraryExercise> get() = ExerciseLibrary.all(customExercises)

/// Сегодняшний день — для подписи в истории по дате начала.
fun FreeWorkoutDay.localDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate = date.atZone(zone).toLocalDate()
