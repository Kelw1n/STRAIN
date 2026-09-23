package com.texasprogram.app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object BroMessageTypeSerializer : KSerializer<BroMessageType> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("BroMessageType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BroMessageType) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): BroMessageType {
        val raw = try { decoder.decodeString().uppercase() } catch (_: Exception) { "TEXT" }
        return when (raw) {
            "TEXT" -> BroMessageType.TEXT
            "WORKOUT_RESULT" -> BroMessageType.WORKOUT_RESULT
            "PHOTO" -> BroMessageType.PHOTO
            else -> BroMessageType.TEXT
        }
    }
}

@Serializable(with = BroMessageTypeSerializer::class)
enum class BroMessageType {
    @SerialName("TEXT") TEXT,
    @SerialName("WORKOUT_RESULT") WORKOUT_RESULT,
    @SerialName("PHOTO") PHOTO
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
