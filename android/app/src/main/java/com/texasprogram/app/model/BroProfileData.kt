package com.texasprogram.app.model

import kotlinx.serialization.Serializable

@Serializable
data class BroLiftEntry(
    val name: String,
    val prescription: String
)

@Serializable
data class BroExercise(
    val name: String,
    val sets: Int,
    val reps: String,
    val weight: String
)

@Serializable
data class BroWorkoutDay(
    val week: Int,
    val day: Int,
    val title: String,
    val exercises: List<BroExercise> = emptyList()
)

@Serializable
data class BroProfileData(
    val broId: String,
    val name: String,
    val programKind: String,
    val programTitle: String,
    val currentWeek: Int,
    val currentDay: Int,
    val lastActiveEpoch: Long,
    val squat5RM: Double,
    val bench5RM: Double,
    val deadlift5RM: Double,
    val recentLifts: List<BroLiftEntry> = emptyList(),
    val programDays: List<BroWorkoutDay> = emptyList(),
    val rawProgramJson: String? = null,
    val recentChatMessages: List<BroChatMessage> = emptyList()
) {
    val isOnline: Boolean
        get() {
            val epochMs = if (lastActiveEpoch in 1..9_999_999_999L) lastActiveEpoch * 1000L else lastActiveEpoch
            val diffSec = (System.currentTimeMillis() - epochMs) / 1000L
            return diffSec < 300
        }

    val isRecentlyActive: Boolean
        get() {
            val epochMs = if (lastActiveEpoch in 1..9_999_999_999L) lastActiveEpoch * 1000L else lastActiveEpoch
            val diffSec = (System.currentTimeMillis() - epochMs) / 1000L
            return diffSec < 86400 * 2
        }

    val activeStageDescription: String
        get() = "Неделя $currentWeek, День $currentDay"

    val statusDescription: String
        get() {
            val epochMs = if (lastActiveEpoch in 1..9_999_999_999L) lastActiveEpoch * 1000L else lastActiveEpoch
            val diffSec = (System.currentTimeMillis() - epochMs) / 1000L
            return when {
                diffSec < 300 -> "В сети / Только что тренировался"
                diffSec < 3600 -> "Только что тренировался"
                diffSec < 86400 -> {
                    val hours = maxOf(1, (diffSec / 3600).toInt())
                    "Тренировался $hours ч. назад"
                }
                else -> {
                    val days = (diffSec / 86400).toInt()
                    "Был $days дн. назад"
                }
            }
        }
}
