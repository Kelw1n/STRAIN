package com.texasprogram.app.model

import com.texasprogram.app.service.EuthanasiaLevel
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/// Перечисление с подписью. Android пишет имя константы («MEDIUM»), iOS —
/// русскую подпись («Средний»). Читаем оба вида: одно такое поле из копии
/// с iPhone раньше роняло разбор всей копии.
open class TitledEnumSerializer<T : Enum<T>>(
    name: String,
    private val values: List<T>,
    private val title: (T) -> String
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(name, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(value.name)

    override fun deserialize(decoder: Decoder): T {
        val raw = decoder.decodeString()
        return values.firstOrNull { it.name == raw || title(it) == raw }
            ?: throw SerializationException("Неизвестное значение «$raw»")
    }

    /// То же для строк, которые лежат в копии без типа, — например, стаж фулбади.
    fun parse(raw: String): T? = values.firstOrNull { it.name == raw || title(it) == raw }
}

object EuthanasiaLevelSerializer :
    TitledEnumSerializer<EuthanasiaLevel>("EuthanasiaLevel", EuthanasiaLevel.entries, { it.title })

object MainLiftSerializer : TitledEnumSerializer<MainLift>("MainLift", MainLift.entries, { it.title })

object LiftOutcomeSerializer : TitledEnumSerializer<LiftOutcome>("LiftOutcome", LiftOutcome.entries, { it.title })

object SplitTemplateSerializer : TitledEnumSerializer<SplitTemplate>("SplitTemplate", SplitTemplate.entries, { it.title })
