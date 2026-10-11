package com.texasprogram.app.service

import com.texasprogram.app.model.ProgramProfile
import kotlin.math.roundToInt

enum class MuscleCategory(val displayName: String, val shortName: String) {
    CHEST("Грудь (Пекторальные)", "Грудь"),
    BACK("Спина (Широчайшие и трапеции)", "Спина"),
    LEGS("Ноги (Квадрицепсы и бицепс бедра)", "Ноги"),
    SHOULDERS("Плечи (Дельты)", "Плечи"),
    ARMS("Руки (Бицепс и трицепс)", "Руки");

    companion object {
        fun categorize(exerciseName: String): MuscleCategory {
            val name = exerciseName.lowercase()

            // Ноги
            if (name.contains("присед") || name.contains("ног") || name.contains("выпад") ||
                name.contains("квадрицепс") || name.contains("бицепс бедр") || name.contains("голен") ||
                name.contains("икр") || name.contains("гакк") || name.contains("румынск")
            ) {
                return LEGS
            }

            // Плечи (важно проверять жим стоя / армейский жим до общего жима)
            if (name.contains("армейск") || name.contains("жим стоя") || name.contains("жим сидя") ||
                name.contains("махи") || name.contains("дельт") || name.contains("плеч") ||
                name.contains("к подбородку")
            ) {
                return SHOULDERS
            }

            // Руки
            if (name.contains("бицепс") || name.contains("трицепс") || name.contains("француз") ||
                name.contains("молот") || name.contains("сгибан") || name.contains("разгибан") ||
                name.contains("брусь")
            ) {
                return ARMS
            }

            // Спина
            if (name.contains("тяг") || name.contains("станов") || name.contains("подтягиван") ||
                name.contains("спин") || name.contains("широчайш") || name.contains("трапеци") ||
                name.contains("блок") || name.contains("гиперэкстенз") || name.contains("пулловер")
            ) {
                return BACK
            }

            // Грудь
            if (name.contains("жим") || name.contains("груд") || name.contains("наклон") ||
                name.contains("отжиман") || name.contains("пекторал") || name.contains("кроссовер") ||
                name.contains("разводк") || name.contains("бабочк")
            ) {
                return CHEST
            }

            return CHEST
        }
    }
}

data class MuscleGroupDisplayItem(
    val category: MuscleCategory,
    val sets: Int,
    val percentage: Int,
    val exercises: String,
    val radarValue: Float
)

data class MuscleAnalyticsResult(
    val items: List<MuscleGroupDisplayItem>,
    val radarValues: List<Float>,
    val subtitle: String
)

object MuscleGroupAnalytics {
    fun calculate(profile: ProgramProfile): MuscleAnalyticsResult {
        val groupSets = mutableMapOf<MuscleCategory, Int>()
        val groupExercises = mutableMapOf<MuscleCategory, MutableSet<String>>()

        for (cat in MuscleCategory.entries) {
            groupSets[cat] = 0
            groupExercises[cat] = mutableSetOf()
        }

        // 1. Проверяем фактически выполненные подходы из setLog
        val idToName = mutableMapOf<String, String>()
        for (week in profile.workoutPlan.weeks) {
            for (day in week.days) {
                for (ex in day.exercises) {
                    idToName[ex.id] = ex.name
                }
            }
        }
        for (edit in profile.planEdits) {
            edit.targetID?.let { idToName[it] = edit.name }
        }

        var loggedSetsCount = 0
        for ((key, _) in profile.setLog) {
            val parts = key.split("|")
            if (parts.size >= 2) {
                val exId = parts[1]
                val exName = idToName[exId] ?: exId
                val cat = MuscleCategory.categorize(exName)
                groupSets[cat] = (groupSets[cat] ?: 0) + 1
                groupExercises[cat]?.add(exName)
                loggedSetsCount++
            }
        }

        val isBasedOnHistory = loggedSetsCount >= 3

        // 2. Если выполненных подходов мало или нет — считаем нагрузку по текущему плану тренировок
        if (!isBasedOnHistory) {
            for (cat in MuscleCategory.entries) {
                groupSets[cat] = 0
                groupExercises[cat] = mutableSetOf()
            }

            for (week in profile.workoutPlan.weeks) {
                for (day in week.days) {
                    val exercises = profile.exercises(day, null)
                    for (ex in exercises) {
                        val cat = MuscleCategory.categorize(ex.name)
                        groupSets[cat] = (groupSets[cat] ?: 0) + maxOf(ex.sets, 1)
                        groupExercises[cat]?.add(ex.name)
                    }
                }
            }
        }

        val totalSets = groupSets.values.sum()
        val maxSets = maxOf(1, groupSets.values.maxOrNull() ?: 1)

        val items = MuscleCategory.entries.map { cat ->
            val sets = groupSets[cat] ?: 0
            val pct = if (totalSets > 0) ((sets.toDouble() / totalSets.toDouble()) * 100.0).roundToInt() else 0
            val exList = (groupExercises[cat] ?: emptySet()).toList().sorted()
            val exString = if (exList.isEmpty()) "Базовые движения" else exList.take(3).joinToString(", ")
            val radarVal = maxOf(0.18f, sets.toFloat() / maxSets.toFloat())

            MuscleGroupDisplayItem(
                category = cat,
                sets = sets,
                percentage = pct,
                exercises = exString,
                radarValue = radarVal
            )
        }.sortedByDescending { it.sets }

        val radarValues = MuscleCategory.entries.map { cat ->
            val sets = groupSets[cat] ?: 0
            maxOf(0.20f, sets.toFloat() / maxSets.toFloat())
        }

        val subtitle = if (isBasedOnHistory) {
            "На основе $loggedSetsCount выполненных подходов в вашей истории."
        } else {
            "На основе распределения упражнений в текущей программе."
        }

        return MuscleAnalyticsResult(items, radarValues, subtitle)
    }
}
