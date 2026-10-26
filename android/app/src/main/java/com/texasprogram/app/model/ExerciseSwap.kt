package com.texasprogram.app.model

import com.texasprogram.app.service.ExerciseArt
import kotlinx.serialization.Serializable

// MARK: - Замена упражнения на ходу
//
// Тренажёр занят — меняем упражнение на похожее прямо в тренировке. Похожие
// ищутся по тому же движению, что и картинка упражнения: присед к приседу,
// тяга к тяге. Вес — по истории нового упражнения, а если его ещё не делали —
// примерно, от веса текущего: у каждого варианта есть доля от основного
// движения группы. Правила те же, что на iPhone.

object ExerciseSwap {
    /// [ratio] — доля от основного движения группы; 0 — с весом тела, `null` — вес не прикинуть.
    data class Option(val name: String, val ratio: Double?)

    data class Family(val title: String, val keys: Set<String>, val options: List<Option>)

    val families = listOf(
        Family("Присед", setOf("squat", "front_squat", "goblet", "leg_press", "lunge"), listOf(
            Option("Приседания", 1.0),
            Option("Фронтальные приседания", 0.8),
            Option("Присед Зерчера", 0.75),
            Option("Гакк-приседания", 1.1),
            Option("Жим ногами", 1.8),
            Option("Гоблет-приседания", 0.35),
            Option("Болгарские выпады", 0.3)
        )),
        Family("Тяга с пола", setOf("deadlift", "rdl", "hip_thrust", "hyperext"), listOf(
            Option("Становая тяга", 1.0),
            Option("Тяга трэп-грифа", 1.05),
            Option("Румынская тяга", 0.7),
            Option("Становая тяга на прямых ногах", 0.65),
            Option("Ягодичный мост со штангой", 0.9),
            Option("Гуд монинг", 0.4),
            Option("Гиперэкстензия", 0.15)
        )),
        Family("Жим лёжа", setOf("bench", "incline", "pushup", "dips"), listOf(
            Option("Жим лёжа", 1.0),
            Option("Жим лёжа на наклонной скамье", 0.8),
            Option("Жим гантелей лёжа", 0.38),
            Option("Жим гантелей на наклонной скамье", 0.33),
            Option("Жим в тренажёре от груди", 0.9),
            Option("Жим лёжа узким хватом", 0.85),
            Option("Отжимания на брусьях", 0.0),
            Option("Отжимания", 0.0)
        )),
        Family("Жим над головой", setOf("ohp", "seated_press"), listOf(
            Option("Жим штанги стоя", 1.0),
            Option("Жим штанги сидя", 1.05),
            Option("Жим гантелей сидя", 0.4),
            Option("Жим в тренажёре сидя", 1.1),
            Option("Жим Арнольда", 0.35)
        )),
        Family("Вертикальная тяга", setOf("pullup", "pulldown"), listOf(
            Option("Тяга верхнего блока", 1.0),
            Option("Тяга верхнего блока узким хватом", 1.0),
            Option("Подтягивания", 0.0),
            Option("Пуловер на блоке", 0.5)
        )),
        Family("Горизонтальная тяга", setOf("row", "cable_row"), listOf(
            Option("Тяга штанги в наклоне", 1.0),
            Option("Тяга горизонтального блока", 0.9),
            Option("Тяга Т-грифа", 0.9),
            Option("Тяга в тренажёре Хаммер", 0.9),
            Option("Тяга гантели в наклоне", 0.45)
        )),
        Family("Бицепс бедра", setOf("leg_curl"), listOf(
            Option("Сгибание ног лёжа", 1.0),
            Option("Сгибание ног сидя", 1.1),
            Option("Нордические сгибания", 0.0)
        )),
        Family("Квадрицепс", setOf("leg_extension"), listOf(
            Option("Разгибание ног", 1.0),
            Option("Сисси-приседания", 0.0),
            Option("Выпады с гантелями", null)
        )),
        Family("Дельты", setOf("lateral_raise"), listOf(
            Option("Махи в стороны", 1.0),
            Option("Махи в кроссовере", 0.6),
            Option("Махи в наклоне", 0.8),
            Option("Тяга штанги к подбородку", 3.0)
        )),
        Family("Бицепс", setOf("curl"), listOf(
            Option("Сгибание рук со штангой", 1.0),
            Option("Сгибание рук с гантелями", 0.42),
            Option("Молотки с гантелями", 0.45),
            Option("Сгибание рук на скамье Скотта", 0.8),
            Option("Сгибание рук на блоке", 0.9)
        )),
        Family("Трицепс", setOf("pushdown"), listOf(
            Option("Разгибание рук на блоке", 1.0),
            Option("Французский жим", 0.9),
            Option("Разгибание рук из-за головы", 0.8),
            Option("Отжимания на брусьях", 0.0)
        )),
        Family("Икры", setOf("calf"), listOf(
            Option("Подъёмы на носки стоя", 1.0),
            Option("Подъёмы на носки сидя", 0.6),
            Option("Носки в жиме ногами", 1.5)
        )),
        Family("Кор", setOf("plank", "crunch", "hanging_raise"), listOf(
            Option("Планка", 0.0),
            Option("Боковая планка", 0.0),
            Option("Подъём ног в висе", 0.0),
            Option("Скручивания на блоке", null),
            Option("Колесо для пресса", 0.0)
        )),
        Family("Трапеции и хват", setOf("shrug"), listOf(
            Option("Шраги со штангой", 1.0),
            Option("Шраги с гантелями", 0.42),
            Option("Прогулка фермера", 0.45)
        ))
    )

    /// Группа движения по названию: по тем же правилам, что и картинка.
    fun family(name: String): Family? {
        val key = ExerciseArt.key(name)
        return families.firstOrNull { key in it.keys }
    }

    private fun option(name: String, family: Family): Option? {
        val key = ProgramProfile.weightStepKey(name)
        return family.options.firstOrNull { ProgramProfile.weightStepKey(it.name) == key }
    }

    /// Вес нового упражнения от веса текущего — только если оба есть в группе
    /// и у обоих есть доля. С весом тела — ноль.
    fun estimate(from: String, weight: Double, to: String, step: Double): Double? {
        val family = family(from) ?: return null
        val target = option(to, family)?.ratio ?: return null
        if (target == 0.0) return 0.0
        val source = option(from, family)?.ratio ?: return null
        if (weight <= 0 || source <= 0) return null
        val value = ProgramProfile.roundWeight(weight / source * target, step)
        return value.takeIf { it > 0 }
    }

    // MARK: Варианты

    enum class Source { HISTORY, ESTIMATE, UNKNOWN }

    data class Suggestion(val name: String, val weight: Double?, val reps: Int?, val source: Source) {
        val detail: String
            get() = when (source) {
                Source.HISTORY -> {
                    val load = if ((weight ?: 0.0) > 0) "${formatWeight(weight ?: 0.0)} кг" else "свой вес"
                    if (reps != null) "В прошлый раз: $load × $reps" else "В прошлый раз: $load"
                }
                Source.ESTIMATE -> when {
                    weight == null -> "Вес подберёшь сам"
                    weight == 0.0 -> "С весом тела"
                    else -> "≈ ${formatWeight(weight)} кг — примерно, подбери по ощущениям"
                }
                Source.UNKNOWN -> "Вес подберёшь сам"
            }
    }

    data class Found(val done: List<Suggestion>, val similar: List<Suggestion>)

    /// Что предложить вместо упражнения: сначала то, что уже делал из этой группы,
    /// потом остальные варианты группы.
    fun suggestions(replacing: String, currentWeight: Double, profile: ProgramProfile, week: Int, day: Int): Found {
        val family = family(replacing)
        val seen = mutableSetOf(ProgramProfile.weightStepKey(replacing))
        val done = mutableListOf<Suggestion>()
        if (family != null) {
            for (other in profile.loggedExerciseNames) {
                val key = ProgramProfile.weightStepKey(other)
                if (key in seen || ExerciseArt.key(other) !in family.keys) continue
                seen += key
                val last = lastEntries(other, profile, week, day) ?: continue
                val top = last.maxByOrNull { it.weight }
                done += Suggestion(other, top?.weight, top?.reps, Source.HISTORY)
            }
        }
        val similar = mutableListOf<Suggestion>()
        for (option in family?.options.orEmpty()) {
            val key = ProgramProfile.weightStepKey(option.name)
            if (key in seen) continue
            seen += key
            val weight = estimate(replacing, currentWeight, option.name, profile.weightStep(option.name))
            similar += Suggestion(option.name, weight, null, if (weight == null) Source.UNKNOWN else Source.ESTIMATE)
        }
        return Found(done, similar)
    }

    /// Своё название — вес из истории, если такое уже делал.
    fun suggestion(named: String, currentName: String, currentWeight: Double, profile: ProgramProfile, week: Int, day: Int): Suggestion {
        lastEntries(named, profile, week, day)?.let { last ->
            val top = last.maxByOrNull { it.weight }
            return Suggestion(named, top?.weight, top?.reps, Source.HISTORY)
        }
        val weight = estimate(currentName, currentWeight, named, profile.weightStep(named))
        return Suggestion(named, weight, null, if (weight == null) Source.UNKNOWN else Source.ESTIMATE)
    }

    private fun lastEntries(name: String, profile: ProgramProfile, week: Int, day: Int): List<SetEntry>? =
        profile.history(name)
            .filterNot { it.week == week && it.day == day }
            .lastOrNull { it.entries.isNotEmpty() }?.entries
            ?.takeIf { it.isNotEmpty() }
}

/// До какого места действует замена.
enum class SwapScope(val title: String, val explanation: String) {
    TODAY("Только сегодня", "В следующий раз будет упражнение по программе."),
    REST("До конца программы", "Этот день будет с новым упражнением и в следующие недели. Прошлые тренировки не меняются.")
}

/// Что поменялось в правках дня — чтобы отмена тренировки вернула как было.
@Serializable
data class SwapUndo(val added: List<String> = emptyList(), val originals: List<PlanEdit> = emptyList()) {
    val isEmpty: Boolean get() = added.isEmpty() && originals.isEmpty()
}

/// Упражнение программы с весом от расчёта — основное движение. Его меняем
/// только на сегодня: программа строится вокруг него.
fun ProgramProfile.isProgramLift(exercise: ExercisePrescription): Boolean =
    // В своей программе веса вписаны руками — её упражнения меняются как любые.
    programKind != TrainingProgramKind.CUSTOM &&
        exercise.load is LoadPrescription.Kilograms && planEdits.none { it.id == exercise.key }

/// Результат замены: новый профиль, упражнение на сегодня и обновлённый откат.
data class SwapResult(val profile: ProgramProfile, val prescription: ExercisePrescription, val undo: SwapUndo)

/// Меняет упражнение прямо в тренировке. Подходы старого упражнения за этот
/// день к этому моменту уже сняты. «До конца программы» — разовыми правками
/// на эту и следующие недели: прошлые тренировки и их история остаются.
fun ProgramProfile.swapExercise(
    old: ExercisePrescription,
    name: String,
    week: Int,
    day: Int,
    scope: SwapScope,
    undo: SwapUndo
): SwapResult {
    var edits = planEdits
    var added = undo.added
    var originals = undo.originals
    fun remember(edit: PlanEdit) {
        if (originals.none { it.id == edit.id } && edit.id !in added) originals = originals + edit
    }
    fun append(target: String, forWeek: Int): PlanEdit {
        val edit = PlanEdit(
            kind = PlanEditKind.REPLACE,
            day = day,
            week = forWeek,
            isPeaking = isPeaking,
            targetKey = target,
            name = name,
            sets = old.sets,
            reps = old.reps
        )
        edits = edits + edit
        added = added + edit.id
        return edit
    }

    // Сегодня: разовая правка этой недели — переименовываем, иначе новая замена.
    // Правку на все недели не трогаем: переименование задело бы прошлые
    // тренировки и «только сегодня» стало бы «навсегда».
    val index = edits.indexOfFirst { it.id == old.key }
    val existing = edits.getOrNull(index)
    val todayId: String
    val target: String
    if (existing != null && existing.week == week) {
        remember(existing)
        val renamed = existing.copy(name = name, kilograms = null, loadText = null)
        edits = edits.toMutableList().also { it[index] = renamed }
        todayId = renamed.id
        if (existing.kind == PlanEditKind.ADD) {
            val next = copy(planEdits = edits)
            return SwapResult(next, renamed.prescription, SwapUndo(added, originals))
        }
        target = existing.targetKey ?: old.key
    } else {
        todayId = append(old.key, week).id
        target = old.key
    }

    var next = copy(planEdits = edits)
    if (scope == SwapScope.REST) {
        // Следующие недели: там, где на месте этого упражнения ещё стоит прежнее.
        val plan = next.workoutPlan
        for (later in plan.weeks.map { it.number }.filter { it > week }) {
            val exercises = plan.weeks.firstOrNull { it.number == later }?.days?.firstOrNull { it.number == day }?.exercises ?: continue
            if (exercises.none { it.key == target }) continue
            append(target, later)
        }
        next = next.copy(planEdits = edits)
    }
    val today = edits.first { it.id == todayId }.prescription
    return SwapResult(next, today, SwapUndo(added, originals))
}

/// Отмена тренировки: правки дня — как до замен.
fun ProgramProfile.undoSwaps(undo: SwapUndo): ProgramProfile {
    if (undo.isEmpty) return this
    var edits = planEdits.filterNot { it.id in undo.added }
    for (original in undo.originals) {
        val index = edits.indexOfFirst { it.id == original.id }
        edits = if (index >= 0) edits.toMutableList().also { it[index] = original } else edits + original
    }
    return copy(planEdits = edits)
}
