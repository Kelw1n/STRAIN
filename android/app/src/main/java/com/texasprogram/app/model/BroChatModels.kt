package com.texasprogram.app.model

import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Serializable
enum class BroMessageType {
    TEXT,
    WORKOUT_RESULT,
    PHOTO
}

@Serializable
data class WorkoutSharePayload(
    val exercise: String,
    val weight: String,
    val sets: String,
    val reps: String,
    val isPR: Boolean = false,
    val week: Int = 1,
    val day: Int = 1
)

@Serializable
data class BroChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val channelId: String,
    val senderId: String,
    val senderName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val text: String = "",
    val type: BroMessageType = BroMessageType.TEXT,
    val workoutPayload: WorkoutSharePayload? = null,
    val photoBase64: String? = null
) {
    val timeFormatted: String
        get() {
            val date = Date(timestamp)
            val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            return formatter.format(date)
        }
}

@Serializable
data class BroChatChannelPayload(
    val channelId: String,
    val messages: List<BroChatMessage> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)
