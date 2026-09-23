package com.texasprogram.app

import com.texasprogram.app.model.BroChatMessage
import com.texasprogram.app.model.BroMessageType
import com.texasprogram.app.model.WorkoutSharePayload
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BroChatTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    @Test
    fun testChannelIdSymmetry() {
        fun makeChannelId(id1: String, id2: String): String {
            val ids = listOf(id1, id2).sorted()
            return "chat_${ids[0]}_${ids[1]}"
        }

        val ch1 = makeChannelId("bro_alpha", "bro_beta")
        val ch2 = makeChannelId("bro_beta", "bro_alpha")
        assertEquals(ch1, ch2)
        assertEquals("chat_bro_alpha_bro_beta", ch1)
    }

    @Test
    fun testTextMessageSerialization() {
        val msg = BroChatMessage(
            id = "msg_123",
            channelId = "chat_a_b",
            senderId = "a",
            senderName = "Алексей",
            timestamp = 1700000000000L,
            text = "Я в зале 🏋️",
            type = BroMessageType.TEXT
        )

        val raw = json.encodeToString(msg)
        val decoded = json.decodeFromString<BroChatMessage>(raw)

        assertEquals("msg_123", decoded.id)
        assertEquals("chat_a_b", decoded.channelId)
        assertEquals("Алексей", decoded.senderName)
        assertEquals("Я в зале 🏋️", decoded.text)
        assertEquals(BroMessageType.TEXT, decoded.type)
    }

    @Test
    fun testWorkoutResultSharingSerialization() {
        val payload = WorkoutSharePayload(
            exercise = "Жим лёжа",
            weight = "120 кг",
            sets = "5",
            reps = "5",
            isPR = true,
            week = 8,
            day = 1
        )
        val msg = BroChatMessage(
            id = "msg_pr",
            channelId = "chat_a_b",
            senderId = "a",
            senderName = "Алексей",
            timestamp = 1700000010000L,
            text = "🔥 НОВЫЙ РЕКОРД! Жим лёжа 120 кг 5×5",
            type = BroMessageType.WORKOUT_RESULT,
            workoutPayload = payload
        )

        val raw = json.encodeToString(msg)
        val decoded = json.decodeFromString<BroChatMessage>(raw)

        assertEquals(BroMessageType.WORKOUT_RESULT, decoded.type)
        assertNotNull(decoded.workoutPayload)
        assertEquals("Жим лёжа", decoded.workoutPayload?.exercise)
        assertEquals("120 кг", decoded.workoutPayload?.weight)
        assertTrue(decoded.workoutPayload?.isPR == true)
        assertEquals(8, decoded.workoutPayload?.week)
    }

    @Test
    fun testPhotoMessageBase64Serialization() {
        val dummyBase64 = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/2wBD..."
        val msg = BroChatMessage(
            id = "msg_photo",
            channelId = "chat_a_b",
            senderId = "b",
            senderName = "Дмитрий",
            timestamp = 1700000020000L,
            text = "📷 Фотография с тренировки",
            type = BroMessageType.PHOTO,
            photoBase64 = dummyBase64
        )

        val raw = json.encodeToString(msg)
        val decoded = json.decodeFromString<BroChatMessage>(raw)

        assertEquals(BroMessageType.PHOTO, decoded.type)
        assertEquals(dummyBase64, decoded.photoBase64)
    }

    @Test
    fun testMessageMergingAndDeduplication() {
        val m1 = BroChatMessage("1", "ch", "a", "Алексей", 100, "Привет", BroMessageType.TEXT)
        val m2 = BroChatMessage("2", "ch", "b", "Дмитрий", 200, "Здорово", BroMessageType.TEXT)
        val m3 = BroChatMessage("3", "ch", "a", "Алексей", 300, "Жму", BroMessageType.TEXT)

        val local = mutableListOf(m1, m2)
        val incoming = listOf(m2, m3) // m2 is duplicate

        val existingIds = local.map { it.id }.toSet()
        for (msg in incoming) {
            if (msg.id !in existingIds) {
                local.add(msg)
            }
        }
        local.sortBy { it.timestamp }

        assertEquals(3, local.size)
        assertEquals("1", local[0].id)
        assertEquals("2", local[1].id)
        assertEquals("3", local[2].id)
    }

    @Test
    fun testCrossPlatformMessageTypeCompatibility() {
        val rawIosJson = """
            {"id":"ios_1","channelId":"chat_a_b","senderId":"a","senderName":"iOS","timestamp":100,"text":"Привет","type":"text"}
        """.trimIndent()
        val decodedFromIos = json.decodeFromString<BroChatMessage>(rawIosJson)
        assertEquals(BroMessageType.TEXT, decodedFromIos.type)

        val rawIosWorkout = """
            {"id":"ios_2","channelId":"chat_a_b","senderId":"a","senderName":"iOS","timestamp":200,"text":"Жим","type":"workout_result"}
        """.trimIndent()
        val decodedWorkout = json.decodeFromString<BroChatMessage>(rawIosWorkout)
        assertEquals(BroMessageType.WORKOUT_RESULT, decodedWorkout.type)

        val rawIosPhoto = """
            {"id":"ios_3","channelId":"chat_a_b","senderId":"a","senderName":"iOS","timestamp":300,"text":"Фото","type":"photo"}
        """.trimIndent()
        val decodedPhoto = json.decodeFromString<BroChatMessage>(rawIosPhoto)
        assertEquals(BroMessageType.PHOTO, decodedPhoto.type)

        val rawUnknown = """
            {"id":"ios_4","channelId":"chat_a_b","senderId":"a","senderName":"iOS","timestamp":400,"text":"Хей","type":"SOME_FUTURE_TYPE"}
        """.trimIndent()
        val decodedUnknown = json.decodeFromString<BroChatMessage>(rawUnknown)
        assertEquals(BroMessageType.TEXT, decodedUnknown.type)
    }
}
