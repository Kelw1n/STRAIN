package com.texasprogram.app.data

import com.texasprogram.app.model.CompletionRecord
import com.texasprogram.app.model.CustomProgram
import com.texasprogram.app.model.SkippedWorkout
import com.texasprogram.app.model.LiftOutcome
import com.texasprogram.app.model.LiftReport
import com.texasprogram.app.model.MainLift
import com.texasprogram.app.model.NutritionData
import com.texasprogram.app.model.PlanEdit
import com.texasprogram.app.model.SetEntry
import com.texasprogram.app.service.EuthanasiaInput
import com.texasprogram.app.service.EuthanasiaPattern
import com.texasprogram.app.service.FullBodyLevel
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.ScheduleMode
import com.texasprogram.app.model.TrainingLevel
import com.texasprogram.app.model.TrainingProgramKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/// Снимок профиля для переноса между устройствами.
///
/// Имена полей и представление дат совпадают с iOS-версией: файл, выгруженный
/// на iPhone, читается здесь и наоборот.
@Serializable
data class ProfileSnapshot(
    val id: String,
    val name: String,
    val createdAtEpochDay: Long,
    val programKind: String,
    val squat5RM: Double,
    val bench5RM: Double,
    val deadlift5RM: Double,
    val level: String,
    val pull: String? = null,
    val arms: String? = null,
    val core: String? = null,
    val back: String? = null,
    val press: String? = null,
    val completedDayKeys: List<String> = emptyList(),
    val completedBenchSessions: List<Int> = emptyList(),
    val scheduleWeekdays: List<Int> = emptyList(),
    val scheduleMode: String = "WEEKDAY",
    val cycleStartedEpochDay: Long,
    val lastCompletionEpochDay: Long? = null,
    val peakingActive: Boolean = false,
    val peakSquat5RM: Double? = null,
    val peakBench5RM: Double? = null,
    val peakDeadlift5RM: Double? = null,
    val completionLog: List<CompletionSnapshot> = emptyList(),
    /// Свои упражнения и правки дней. Значение по умолчанию нужно для копий,
    /// снятых до появления правок, и для файлов со старых версий iOS.
    val planEdits: List<PlanEdit> = emptyList(),
    /// Факт подходов, откаты, цикл и своя программа. Значения по умолчанию нужны
    /// для копий, снятых до появления этих полей, и для файлов со старых версий.
    val setLog: Map<String, SetEntry> = emptyMap(),
    val skipped: List<SkippedSnapshot> = emptyList(),
    val keepScreenOn: Boolean = true,
    val cycleNumber: Int = 1,
    val customProgram: CustomProgram? = null,
    val fullBodyLevel: String? = null,
    val euthanasiaInput: EuthanasiaInput? = null,
    val autoregulationEnabled: Boolean = false,
    val liftReports: List<LiftReportSnapshot> = emptyList(),
    val workoutNotes: Map<String, String> = emptyMap(),
    /// Замеры веса тела. Дата — ISO-строка, как пишет iOS: раньше вес тела
    /// в копию не попадал вовсе и терялся при восстановлении.
    val bodyWeightLog: List<BodyWeightSnapshot> = emptyList(),
    /// Шаг прибавки и отклонённые застои. В старых копиях их нет.
    val weightSteps: Map<String, Double> = emptyMap(),
    val defaultWeightStep: Double = 2.5,
    val stallDismissed: Map<String, Double> = emptyMap(),
    /// Дневник и метод питания — из отдельного файла профиля. В копиях,
    /// снятых до вкладки «Питание», его нет, и тогда прежний дневник не трогаем.
    val nutrition: NutritionData? = null,
    /// Опыт за места в челленджах. В старых копиях его нет.
    val challengeAwards: List<com.texasprogram.app.model.ChallengeAward> = emptyList()
)

/// Отчёт авторегуляции в копии. Android хранит номер дня `epochDay`, iOS —
/// дату `date`. Пишем оба и читаем любое: раньше без нужного поля копия
/// с другой платформы не открывалась целиком.
@Serializable
data class LiftReportSnapshot(
    val key: String,
    val lift: MainLift,
    val outcome: LiftOutcome,
    val epochDay: Long? = null,
    val date: String? = null
)

/// Пропущенная тренировка в копии — с той же парой дат, что и отчёт.
@Serializable
data class SkippedSnapshot(
    val id: String? = null,
    val week: Int,
    val day: Int,
    val isPeaking: Boolean = false,
    val epochDay: Long? = null,
    val date: String? = null,
    val holdsProgression: Boolean
)

@Serializable
data class BodyWeightSnapshot(
    val date: String,
    val weight: Double
)

@Serializable
data class CompletionSnapshot(
    val key: String,
    val epochDay: Long,
    val week: Int,
    val day: Int,
    val squat: Double? = null,
    val bench: Double? = null,
    val deadlift: Double? = null,
    /// Факт, время и усилие тренировки. Необязательные: в копиях, снятых
    /// раньше, их нет — без них история теряла бы итоги тренировок.
    val actualSquat: Double? = null,
    val actualBench: Double? = null,
    val actualDeadlift: Double? = null,
    val durationSeconds: Int? = null,
    val effort: String? = null
)

@Serializable
data class BackupArchive(
    val version: Int = 1,
    /// ISO 8601 без долей секунды: так дату читает и iOS-версия.
    val exportedAt: String = "",
    val profiles: List<ProfileSnapshot> = emptyList(),
    /// Код входа в профиль ленты: после переустановки профиль возвращается
    /// вместе с данными. В старых копиях его нет.
    val socialToken: String? = null
)

object BackupService {
    const val FILE_NAME = "strain-backup.json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    /// `nutrition` — питание профиля по его идентификатору: оно лежит не в профиле, а в файле.
    fun export(
        profiles: List<ProgramProfile>,
        socialToken: String? = null,
        nutrition: (String) -> NutritionData? = { null }
    ): String =
        json.encodeToString(
            BackupArchive(
                exportedAt = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString(),
                profiles = profiles.map { snapshot(it, nutrition(it.id)) },
                socialToken = socialToken
            )
        )

    fun parse(text: String): BackupArchive = json.decodeFromString(text)

    /// Питание из копии — в файлы профилей. В старой копии его нет — прежнее остаётся.
    fun restoreNutrition(archive: BackupArchive, directory: java.io.File) {
        archive.profiles.forEach { snapshot ->
            snapshot.nutrition?.let { NutritionStore.write(directory, snapshot.id, it) }
        }
    }

    private fun snapshot(profile: ProgramProfile, nutrition: NutritionData?) = ProfileSnapshot(
        id = profile.id,
        name = profile.name,
        createdAtEpochDay = profile.createdAtEpochDay,
        programKind = profile.programKind.name,
        squat5RM = profile.squat5RM,
        bench5RM = profile.bench5RM,
        deadlift5RM = profile.deadlift5RM,
        level = if (profile.level == TrainingLevel.BEGINNER) "BEGINNER" else "INTERMEDIATE",
        pull = profile.pull,
        arms = profile.arms,
        core = profile.core,
        back = profile.back,
        press = profile.press,
        completedDayKeys = profile.completedDayKeys,
        completedBenchSessions = profile.completedBenchSessions,
        scheduleWeekdays = profile.scheduleWeekdays,
        scheduleMode = if (profile.scheduleMode == ScheduleMode.QUEUE) "QUEUE" else "WEEKDAY",
        cycleStartedEpochDay = profile.cycleStartedEpochDay,
        lastCompletionEpochDay = profile.lastCompletionEpochDay,
        peakingActive = profile.peakingActive,
        peakSquat5RM = profile.peakSquat5RM,
        peakBench5RM = profile.peakBench5RM,
        peakDeadlift5RM = profile.peakDeadlift5RM,
        completionLog = profile.completionLog.map {
            CompletionSnapshot(
                it.key, it.epochDay, it.week, it.day, it.squat, it.bench, it.deadlift,
                it.actualSquat, it.actualBench, it.actualDeadlift, it.durationSeconds, it.effort
            )
        },
        planEdits = profile.planEdits,
        setLog = profile.setLog,
        skipped = profile.skipped.map {
            SkippedSnapshot(it.id, it.week, it.day, it.isPeaking, it.epochDay, isoDate(it.epochDay), it.holdsProgression)
        },
        keepScreenOn = profile.keepScreenOn,
        cycleNumber = profile.cycleNumber,
        customProgram = profile.customProgram,
        fullBodyLevel = profile.fullBodyLevel.name,
        euthanasiaInput = profile.euthanasiaInput,
        autoregulationEnabled = profile.autoregulationEnabled,
        liftReports = profile.liftReports.map {
            LiftReportSnapshot(it.key, it.lift, it.outcome, it.epochDay, isoDate(it.epochDay))
        },
        workoutNotes = profile.workoutNotes,
        bodyWeightLog = profile.bodyWeightLog.map { BodyWeightSnapshot(isoDate(it.epochDay), it.weight) },
        weightSteps = profile.weightSteps,
        defaultWeightStep = profile.defaultWeightStep,
        stallDismissed = profile.stallDismissed,
        nutrition = nutrition,
        challengeAwards = profile.challengeAwards
    )

    fun profile(snapshot: ProfileSnapshot) = ProgramProfile(
        id = snapshot.id,
        name = snapshot.name,
        createdAtEpochDay = snapshot.createdAtEpochDay,
        // Код строковый и совпадает с iOS. Неизвестное имя — старый файл,
        // такие всегда были техасом.
        programKind = TrainingProgramKind.entries.firstOrNull { it.name == snapshot.programKind }
            ?: TrainingProgramKind.TEXAS,
        squat5RM = snapshot.squat5RM,
        bench5RM = snapshot.bench5RM,
        deadlift5RM = snapshot.deadlift5RM,
        level = if (snapshot.level == "INTERMEDIATE") TrainingLevel.INTERMEDIATE else TrainingLevel.BEGINNER,
        pull = snapshot.pull,
        arms = snapshot.arms,
        core = snapshot.core,
        back = snapshot.back,
        press = snapshot.press,
        completedDayKeys = snapshot.completedDayKeys,
        completedBenchSessions = snapshot.completedBenchSessions,
        completionLog = snapshot.completionLog.map {
            CompletionRecord(
                it.key, it.epochDay, it.week, it.day, it.squat, it.bench, it.deadlift,
                it.actualSquat, it.actualBench, it.actualDeadlift, it.durationSeconds, it.effort
            )
        },
        planEdits = snapshot.planEdits.map { edit ->
            edit.copy(targetKey = edit.targetKey?.let(::exerciseKey), order = edit.order?.map(::exerciseKey))
        },
        setLog = snapshot.setLog.mapKeys { exerciseKey(it.key) },
        skipped = snapshot.skipped.mapNotNull { entry ->
            val day = entry.epochDay ?: entry.date?.let(::epochDayOf) ?: return@mapNotNull null
            SkippedWorkout(entry.id ?: java.util.UUID.randomUUID().toString(), entry.week, entry.day, entry.isPeaking, day, entry.holdsProgression)
        },
        keepScreenOn = snapshot.keepScreenOn,
        cycleNumber = snapshot.cycleNumber,
        customProgram = snapshot.customProgram,
        // С iPhone стаж приходит подписью («Меньше года»), а не именем константы.
        fullBodyLevel = snapshot.fullBodyLevel
            ?.let { raw -> FullBodyLevel.entries.firstOrNull { it.name == raw || it.title == raw } }
            ?: FullBodyLevel.ABOUT_YEAR,
        euthanasiaInput = snapshot.euthanasiaInput,
        autoregulationEnabled = snapshot.autoregulationEnabled,
        liftReports = snapshot.liftReports.mapNotNull { entry ->
            val day = entry.epochDay ?: entry.date?.let(::epochDayOf) ?: return@mapNotNull null
            LiftReport(entry.key, entry.lift, entry.outcome, day)
        },
        workoutNotes = snapshot.workoutNotes,
        bodyWeightLog = snapshot.bodyWeightLog.mapNotNull { entry ->
            epochDayOf(entry.date)?.let { com.texasprogram.app.model.BodyWeightEntry(it, entry.weight) }
        },
        weightSteps = snapshot.weightSteps,
        defaultWeightStep = snapshot.defaultWeightStep,
        stallDismissed = snapshot.stallDismissed,
        challengeAwards = snapshot.challengeAwards,
        scheduleWeekdays = snapshot.scheduleWeekdays,
        scheduleMode = if (snapshot.scheduleMode == "QUEUE") ScheduleMode.QUEUE else ScheduleMode.WEEKDAY,
        cycleStartedEpochDay = snapshot.cycleStartedEpochDay,
        lastCompletionEpochDay = snapshot.lastCompletionEpochDay,
        peakingActive = snapshot.peakingActive,
        peakSquat5RM = snapshot.peakSquat5RM,
        peakBench5RM = snapshot.peakBench5RM,
        peakDeadlift5RM = snapshot.peakDeadlift5RM
    )

    /// «Эвтаназия» собирает ключи упражнений из движения: Android пишет его
    /// именем константы («test-max-PULL»), iOS — подписью («test-max-Тяга»).
    /// Подходы и правки дня лежат под этими ключами, поэтому переводим их
    /// в здешний вид — иначе после переноса с iPhone история программы пропадает.
    fun exerciseKey(key: String): String = key.split("|").joinToString("|") { segment ->
        val prefix = patternPrefixes.firstOrNull(segment::startsWith) ?: return@joinToString segment
        val rest = segment.removePrefix(prefix)
        val code = rest.substringBefore("-")
        val pattern = EuthanasiaPattern.entries.firstOrNull { it.title == code } ?: return@joinToString segment
        prefix + pattern.name + rest.removePrefix(code)
    }

    private val patternPrefixes = listOf("test-max-", "test-time-", "density-", "main-")

    /// Дата для iOS: начало дня в местном часовом поясе.
    private fun isoDate(epochDay: Long): String =
        java.time.LocalDate.ofEpochDay(epochDay).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toString()

    /// Дата из копии iOS обратно в номер дня — в местном часовом поясе.
    private fun epochDayOf(iso: String): Long? = runCatching {
        java.time.Instant.parse(iso).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
    }.getOrNull()
}
