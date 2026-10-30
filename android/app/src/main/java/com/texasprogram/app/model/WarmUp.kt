package com.texasprogram.app.model

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.texasprogram.app.service.ExerciseArt
import kotlin.math.floor
import kotlin.math.max

/// Разминочные подходы перед рабочим весом — чтобы не считать их в голове.
///
/// Только для упражнений со штангой: присед, становая, жимы и тяга в наклоне.
/// Пустой гриф на 10, дальше 40% на 5, 60% на 3 и 80% на 2, от 140 кг ещё
/// 90% на 1. Веса — вниз до 2,5 кг, повторы веса и всё, что не легче рабочего
/// хотя бы на 2,5 кг, выпадают. В становой пустого грифа нет: с пола без блинов
/// не тянут. Расчёт тот же, что на iOS, — `WarmUp.swift`.
object WarmUp {
    data class Step(val weight: Double, val reps: Int)

    const val BAR_WEIGHT = 20.0
    private const val PREFS = "strain.workout"
    private const val KEY = "warmup_enabled"

    private val barbellKeys = setOf("squat", "front_squat", "deadlift", "rdl", "bench", "incline", "ohp", "row")

    /// Нужна ли разминка этому упражнению: штанга, а не гантели или гири.
    fun applies(name: String): Boolean {
        val text = name.lowercase()
        if (listOf("гантел", "гир", "тренажер", "тренажёр", "смит").any { text.contains(it) }) return false
        return ExerciseArt.key(name) in barbellKeys
    }

    fun steps(name: String, working: Double): List<Step> =
        if (!applies(name)) emptyList() else steps(working, fromBar = ExerciseArt.key(name) != "deadlift")

    fun steps(working: Double, fromBar: Boolean = true): List<Step> {
        if (!working.isFinite() || working < BAR_WEIGHT + 15) return emptyList()
        val plan = mutableListOf<Pair<Double?, Int>>(0.4 to 5, 0.6 to 3, 0.8 to 2)
        if (fromBar) plan.add(0, null to 10)
        if (working >= 140) plan.add(0.9 to 1)
        val result = mutableListOf<Step>()
        for ((share, reps) in plan) {
            val raw = share?.let { working * it } ?: BAR_WEIGHT
            val weight = max(BAR_WEIGHT, floor(raw / 2.5 + 1e-9) * 2.5)
            if (weight > working - 2.5 + 1e-9) continue
            val last = result.lastOrNull()
            if (last != null && weight < last.weight + 2.5 - 1e-9) continue
            result += Step(weight, reps)
        }
        return result
    }

    // MARK: Настройка «Разминочные подходы»

    private var loaded = false
    private var state by mutableStateOf(true)

    fun enabled(context: Context): Boolean {
        if (!loaded) {
            state = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)
            loaded = true
        }
        return state
    }

    fun setEnabled(context: Context, value: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply()
        loaded = true
        state = value
    }
}
