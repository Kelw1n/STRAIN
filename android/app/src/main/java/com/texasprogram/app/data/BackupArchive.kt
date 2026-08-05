package com.texasprogram.app.data

import com.texasprogram.app.model.CompletionRecord
import com.texasprogram.app.model.CustomProgram
import com.texasprogram.app.model.SkippedWorkout
import com.texasprogram.app.model.PlanEdit
import com.texasprogram.app.model.SetEntry
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
    val skipped: List<SkippedWorkout> = emptyList(),
    val keepScreenOn: Boolean = true,
    val cycleNumber: Int = 1,
    val customProgram: CustomProgram? = null,
    val fullBodyLevel: String? = null
)

@Serializable
data class CompletionSnapshot(
    val key: String,
    val epochDay: Long,
    val week: Int,
    val day: Int,
    val squat: Double? = null,
    val bench: Double? = null,
    val deadlift: Double? = null
)

@Serializable
data class BackupArchive(
    val version: Int = 1,
    val exportedAt: String = "",
    val profiles: List<ProfileSnapshot> = emptyList()
)

object BackupService {
    const val FILE_NAME = "strain-backup.json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun export(profiles: List<ProgramProfile>): String =
        json.encodeToString(BackupArchive(profiles = profiles.map(::snapshot)))

    fun parse(text: String): BackupArchive = json.decodeFromString(text)

    private fun snapshot(profile: ProgramProfile) = ProfileSnapshot(
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
            CompletionSnapshot(it.key, it.epochDay, it.week, it.day, it.squat, it.bench, it.deadlift)
        },
        planEdits = profile.planEdits,
        setLog = profile.setLog,
        skipped = profile.skipped,
        keepScreenOn = profile.keepScreenOn,
        cycleNumber = profile.cycleNumber,
        customProgram = profile.customProgram,
        fullBodyLevel = profile.fullBodyLevel.name
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
            CompletionRecord(it.key, it.epochDay, it.week, it.day, it.squat, it.bench, it.deadlift)
        },
        planEdits = snapshot.planEdits,
        setLog = snapshot.setLog,
        skipped = snapshot.skipped,
        keepScreenOn = snapshot.keepScreenOn,
        cycleNumber = snapshot.cycleNumber,
        customProgram = snapshot.customProgram,
        fullBodyLevel = snapshot.fullBodyLevel
            ?.let { name -> FullBodyLevel.entries.firstOrNull { it.name == name } }
            ?: FullBodyLevel.ABOUT_YEAR,
        scheduleWeekdays = snapshot.scheduleWeekdays,
        scheduleMode = if (snapshot.scheduleMode == "QUEUE") ScheduleMode.QUEUE else ScheduleMode.WEEKDAY,
        cycleStartedEpochDay = snapshot.cycleStartedEpochDay,
        lastCompletionEpochDay = snapshot.lastCompletionEpochDay,
        peakingActive = snapshot.peakingActive,
        peakSquat5RM = snapshot.peakSquat5RM,
        peakBench5RM = snapshot.peakBench5RM,
        peakDeadlift5RM = snapshot.peakDeadlift5RM
    )
}
