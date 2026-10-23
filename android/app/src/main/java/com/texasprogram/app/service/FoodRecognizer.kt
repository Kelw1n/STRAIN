package com.texasprogram.app.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.texasprogram.app.model.Nutrients
import com.texasprogram.app.model.newNutritionId
import com.texasprogram.app.model.roundHalfUp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/// Продукт, который ИИ нашёл на фото. До добавления в дневник его можно поправить.
data class RecognizedFood(
    val id: String = newNutritionId(),
    val name: String,
    val grams: Double? = null,
    val nutrients: Nutrients
)

data class RecognizedMeal(
    val title: String,
    val items: List<RecognizedFood>,
    /// «высокая», «средняя», «низкая» или «оценка по фото».
    val confidence: String,
    /// Откуда цифры: этикетка, известный продукт или оценка на глаз.
    val comment: String
) {
    /// Итог — всегда сумма продуктов, а не то, что ИИ написал отдельно.
    val total: Nutrients get() = items.fold(Nutrients.ZERO) { sum, item -> sum + item.nutrients }
}

/// Распознавание еды по фото через Google Gemini со своим ключом — как в Butterfly
/// и в iOS-версии: тот же промпт, те же модели по очереди, тот же разбор ответа.
object FoodRecognizer {
    /// Первая, что ответит. Следующая пробуется, только если модели нет.
    val models = listOf("gemini-3.5-flash", "gemini-3.1-flash-lite", "gemini-flash-latest")
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta"
    /// Фото уходит не больше 1600 точек по длинной стороне и не тяжелее 2,5 МБ.
    const val MAX_SIDE = 1600
    const val MAX_BYTES = 2_520_000

    sealed class Failure(override val message: String) : Exception(message) {
        data object NoKey : Failure("Нужен ключ Google AI — вставь его в настройках питания.")
        data object BadKey : Failure("Google не принял ключ. Проверь, что он скопирован целиком.")
        data object Region : Failure("Google не пускает к ИИ из твоей страны. Включи VPN и попробуй ещё раз.")
        data object Quota : Failure("Лимит запросов по ключу на сейчас исчерпан. Подожди минуту или проверь лимиты в Google AI Studio.")
        data object Busy : Failure("Сервис Google сейчас занят. Попробуй ещё раз через минуту.")
        data object NothingFound : Failure("Еду на фото не нашёл. Сними поближе или при свете.")
        data object Unreadable : Failure("Не получилось разобрать ответ ИИ. Попробуй ещё раз.")
        data class Network(val reason: String) : Failure("Нет связи с Google: $reason")
    }

    /// Модели нет по этому ключу — пробуем следующую.
    private class ModelMissing : Exception()

    private val json = Json { ignoreUnknownKeys = true }

    // MARK: Запросы

    suspend fun recognize(context: Context, photo: Uri, key: String): RecognizedMeal {
        val jpeg = withContext(Dispatchers.IO) { uploadJPEG(context, photo) } ?: throw Failure.Unreadable
        return parse(ask(photoPrompt, jpeg, key))
    }

    /// Пересчёт после поправки словами: «там 200 г, а не 150», «это без сахара».
    suspend fun correct(meal: RecognizedMeal, note: String, key: String): RecognizedMeal =
        parse(ask(correctionPrompt(meal, note), null, key))

    /// Проверка ключа без фото — список моделей отвечает и на бесплатном ключе.
    suspend fun checkKey(key: String) {
        val (status, text) = send("GET", "$BASE/models?pageSize=1", key, null, 20_000)
        verify(status, text)
    }

    private suspend fun ask(prompt: String, image: ByteArray?, key: String): String {
        val body = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        addJsonObject { put("text", prompt) }
                        if (image != null) {
                            addJsonObject {
                                putJsonObject("inline_data") {
                                    put("mime_type", "image/jpeg")
                                    put("data", java.util.Base64.getEncoder().encodeToString(image))
                                }
                            }
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", 0.1)
                put("maxOutputTokens", 8192)
                put("responseMimeType", "application/json")
            }
        }.toString()
        var lastFailure: Failure = Failure.Busy
        for (model in models) {
            val (status, text) = send("POST", "$BASE/models/$model:generateContent", key, body, 90_000)
            try {
                verify(status, text)
                return responseText(text)
            } catch (_: ModelMissing) {
                lastFailure = Failure.Network("модель $model недоступна по этому ключу")
            }
        }
        throw lastFailure
    }

    private suspend fun send(method: String, url: String, key: String, body: String?, timeout: Int): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            val connection = try {
                URL(url).openConnection() as HttpURLConnection
            } catch (e: Exception) {
                throw Failure.Network(e.message ?: "неверный адрес")
            }
            try {
                connection.requestMethod = method
                connection.connectTimeout = timeout
                connection.readTimeout = timeout
                connection.setRequestProperty("x-goog-api-key", key)
                connection.setRequestProperty("Accept", "application/json")
                if (body != null) {
                    val payload = body.toByteArray(Charsets.UTF_8)
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setFixedLengthStreamingMode(payload.size)
                    connection.outputStream.use { it.write(payload) }
                }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                status to (stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty())
            } catch (e: IOException) {
                throw Failure.Network(e.message ?: "нет интернета")
            } catch (e: RuntimeException) {
                // Сбой внутри HttpURLConnection не должен ронять приложение.
                throw Failure.Network(e.message ?: "сбой соединения")
            } finally {
                connection.disconnect()
            }
        }

    /// Ошибки Google — в понятные слова. Модели нет — пробуем следующую.
    fun failure(status: Int, text: String): Failure? {
        if (status in 200..299) return null
        val message = runCatching {
            val error = json.parseToJsonElement(text) as? JsonObject
            ((error?.get("error") as? JsonObject)?.get("message") as? JsonPrimitive)?.content
        }.getOrNull().orEmpty()
        val lower = message.lowercase()
        if (status in listOf(400, 403) && lower.contains("location")) return Failure.Region
        if (status == 401 || status == 403 || (status == 400 && lower.contains("api key"))) return Failure.BadKey
        if (status == 429) return Failure.Quota
        if (status >= 500) return Failure.Busy
        return Failure.Network(if (message.isEmpty()) "ошибка $status" else message)
    }

    /// Модели с таким именем нет — не ошибка ключа, а повод взять следующую.
    fun isModelMissing(status: Int, text: String): Boolean {
        val message = runCatching {
            val error = json.parseToJsonElement(text) as? JsonObject
            ((error?.get("error") as? JsonObject)?.get("message") as? JsonPrimitive)?.content
        }.getOrNull().orEmpty().lowercase()
        val modelGone = message.contains("model") &&
            (message.contains("not found") || message.contains("unsupported") || message.contains("not supported"))
        return status == 404 || (status in listOf(400, 422) && modelGone)
    }

    private fun verify(status: Int, text: String) {
        if (status in 200..299) return
        if (isModelMissing(status, text)) throw ModelMissing()
        failure(status, text)?.let { throw it }
    }

    // MARK: Фото

    /// JPEG для отправки: уменьшаем, поворачиваем по EXIF и поджимаем качество,
    /// пока не влезет в лимит.
    fun uploadJPEG(context: Context, uri: Uri): ByteArray? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val exif = resolver.openInputStream(uri)?.use { ExifInterface(it) }
        val rotation = exif?.rotationDegrees?.toFloat() ?: 0f
        val flipped = exif?.isFlipped == true
        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (flipped) postScale(-1f, 1f)
            if (rotation != 0f) postRotate(rotation)
        }
        val bitmap = if (scale < 1f || rotation != 0f || flipped) {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        } else decoded
        var quality = 88
        var data = compress(bitmap, quality)
        while (data.size > MAX_BYTES && quality > 42) {
            quality -= 8
            data = compress(bitmap, quality)
        }
        if (data.size <= MAX_BYTES) data else null
    }.getOrNull()

    private fun compress(bitmap: Bitmap, quality: Int): ByteArray = ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        out.toByteArray()
    }

    // MARK: Разбор ответа

    /// Текст ответа Gemini: части первого варианта, без «мыслей» модели.
    fun responseText(raw: String): String {
        val root = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: throw Failure.Unreadable
        val candidate = (root["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject
        val parts = ((candidate?.get("content") as? JsonObject)?.get("parts") as? JsonArray).orEmpty()
        val text = parts
            .mapNotNull { it as? JsonObject }
            .filter { (it["thought"] as? JsonPrimitive)?.booleanOrNull != true }
            .mapNotNull { (it["text"] as? JsonPrimitive)?.takeIf { value -> value.isString }?.content }
            .joinToString("")
        if (text.isBlank()) throw Failure.NothingFound
        return text
    }

    /// Разбор с запасом, как в Butterfly: ключи бывают разными, числа — строками
    /// с запятой, ответ — в ```json```. Итог пересчитываем сами.
    fun parse(raw: String): RecognizedMeal {
        val root = jsonElement(raw) ?: throw Failure.Unreadable
        val obj = root as? JsonObject ?: JsonObject(emptyMap())
        val list = (root as? JsonArray)
            ?: (first(obj, "items", "foods", "products", "ingredients") as? JsonArray)
            ?: JsonArray(emptyList())
        var items = list.take(100).mapIndexedNotNull { index, value -> (value as? JsonObject)?.let { food(it, index) } }
        if (items.isEmpty() && (obj["name"] != null || obj["title"] != null || obj["calories"] != null || obj["kcal"] != null)) {
            items = listOf(food(obj, 0))
        }
        if (items.isEmpty()) throw Failure.NothingFound
        val title = text(first(obj, "title", "meal_name", "name"), 120)
        return RecognizedMeal(
            title = title.ifEmpty { items.first().name },
            items = items,
            confidence = confidence(text(obj["confidence"], 32)),
            comment = text(first(obj, "comment", "note"), 1000)
        )
    }

    private fun food(item: JsonObject, index: Int): RecognizedFood {
        val name = text(first(item, "name", "title", "food", "product"), 120)
        val grams = number(first(item, "portion_g", "portionG", "grams", "weight_g", "weight"), 100_000.0)
            ?: number(first(item, "amount", "portion"), 100_000.0)
        val protein = number(first(item, "protein_g", "protein", "proteins"), 2000.0) ?: 0.0
        val fat = number(first(item, "fat_g", "fat", "fats"), 2000.0) ?: 0.0
        val carbs = number(first(item, "carbs_g", "carbs", "carbohydrates"), 2000.0) ?: 0.0
        var kcal = number(first(item, "calories", "kcal", "energy_kcal"), 20_000.0) ?: 0.0
        if (kcal == 0.0) kcal = Nutrients.kcal(protein, fat, carbs)
        return RecognizedFood(
            name = name.ifEmpty { "Продукт ${index + 1}" },
            grams = grams?.let(::roundHalfUp),
            nutrients = Nutrients(roundHalfUp(kcal), tenth(protein), tenth(fat), tenth(carbs))
        )
    }

    /// Первое из полей, которое есть в ответе, — как `a ?? b ?? c` в iOS.
    /// `null` в JSON считается отсутствием поля.
    private fun first(obj: JsonObject, vararg keys: String): JsonElement? =
        keys.firstNotNullOfOrNull { key -> obj[key]?.takeIf { it !is JsonNull } }

    /// JSON из ответа: как есть, без ```, по первым фигурным скобкам и с прямыми кавычками.
    private fun jsonElement(raw: String): JsonElement? {
        val clean = raw.replace(Regex("[​-‍﻿]"), "").trim()
        val unfenced = clean
            .replace(Regex("^```(?:json)?\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*```$"), "")
        var braces = unfenced
        val open = unfenced.indexOfFirst { it == '{' || it == '[' }
        val close = unfenced.indexOfLast { it == '}' || it == ']' }
        if (open >= 0 && close > open) braces = unfenced.substring(open, close + 1)
        val straight = braces.replace(Regex("[“”„]"), "\"")
        for (candidate in listOf(clean, unfenced, braces, straight)) {
            val value = runCatching { json.parseToJsonElement(candidate) }.getOrNull()
            // Голая строка или число — не ответ: разбор Swift их тоже не берёт.
            if (value is JsonObject || value is JsonArray) return value
        }
        return null
    }

    /// Число из числа или строки: «12,5», «примерно 150 г». Вне пределов — нет числа.
    fun number(value: JsonElement?, limit: Double): Double? {
        val primitive = value as? JsonPrimitive ?: return null
        val result = if (primitive.isString) {
            Regex("[0-9]+(?:\\.[0-9]+)?").find(primitive.content.replace(',', '.'))?.value?.toDoubleOrNull()
        } else {
            // true/false числом не считаем.
            if (primitive.booleanOrNull != null) null else primitive.doubleOrNull
        }
        if (result == null || !result.isFinite() || result < 0 || result > limit) return null
        return result
    }

    private fun text(value: JsonElement?, limit: Int): String {
        val raw = when (value) {
            null, is JsonNull -> return ""
            is JsonPrimitive -> value.content
            else -> value.toString()
        }
        return raw.filter { it == '\n' || !isLineBreak(it) }.trim().take(limit)
    }

    private fun isLineBreak(char: Char): Boolean = char == '\r' || char == ' ' || char == ' ' || char == '\u000B' || char == '\u000C' || char == '\u0085'

    private fun confidence(raw: String): String {
        val value = raw.lowercase()
        return when {
            value.contains("выс") || value.contains("high") -> "высокая"
            value.contains("низ") || value.contains("low") -> "низкая"
            value.contains("сред") || value.contains("medium") -> "средняя"
            else -> "оценка по фото"
        }
    }

    private fun tenth(value: Double): Double = roundHalfUp(value * 10) / 10

    // MARK: Промпты

    /// Промпт из Butterfly без изменений: этикетка важнее оценки на глаз.
    val photoPrompt = """
        Проанализируй фото еды. Верни только валидный JSON. Не используй Markdown и не добавляй пояснения до или после JSON.

        Используй именно эти ключи на английском:
        {
          "title": "краткое название приёма пищи на русском",
          "items": [
            {
              "name": "название продукта на русском",
              "portion_g": 150,
              "calories": 248,
              "protein_g": 46.5,
              "fat_g": 5.4,
              "carbs_g": 0
            }
          ],
          "total": {
            "calories": 248,
            "protein_g": 46.5,
            "fat_g": 5.4,
            "carbs_g": 0
          },
          "confidence": "высокая|средняя|низкая",
          "comment": "кратко: откуда взяты КБЖУ (этикетка / известный продукт / оценка по виду)"
        }

        Правила:
        - оцени порцию по фото в граммах (portion_g — число, без единиц);
        - если блюдо смешанное, разбей его на понятные продукты или части блюда;
        - если не уверен, всё равно дай наиболее вероятную оценку;
        - числа должны быть числами, не строками и без единиц измерения;
        - total должен быть суммой items;
        - ответ должен начинаться с { и заканчиваться }.

        Упаковки и готовые продукты:
        - если на фото упаковка, этикетка или штрихкод готового продукта — не оценивай «как домашнее блюдо на глаз»;
        - сначала прочитай с упаковки: название, бренд, вес/порцию, таблицу КБЖУ (на 100 г и/или на порцию);
        - если цифры на этикетке читаются — бери именно их (пересчитай на видимую/указанную порцию);
        - если этикетка размыта, но продукт узнаваем (бренд + название) — используй известную пищевую ценность этого SKU/типичные данные производителя из своих знаний (как если бы сверился с карточкой товара), а не произвольную оценку;
        - в name укажи бренд и название с упаковки, если они читаются;
        - в comment коротко напиши: «этикетка», «известный продукт» или «оценка по виду».
    """.trimIndent()

    fun correctionPrompt(meal: RecognizedMeal, note: String): String {
        // Ключи по алфавиту, как `.sortedKeys` в iOS.
        val current = buildJsonObject {
            putJsonArray("items") {
                meal.items.forEach { item ->
                    addJsonObject {
                        put("calories", jsonNumber(item.nutrients.kcal))
                        put("carbs_g", jsonNumber(item.nutrients.carbs))
                        put("fat_g", jsonNumber(item.nutrients.fat))
                        put("name", item.name)
                        item.grams?.let { put("portion_g", jsonNumber(it)) }
                        put("protein_g", jsonNumber(item.nutrients.protein))
                    }
                }
            }
            put("title", meal.title)
            putJsonObject("total") {
                val total = meal.total
                put("calories", jsonNumber(total.kcal))
                put("carbs_g", jsonNumber(total.carbs))
                put("fat_g", jsonNumber(total.fat))
                put("protein_g", jsonNumber(total.protein))
            }
        }
        val pretty = Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), current)
        val fix = note.trim()
        return """
Ты пересчитываешь уже сохранённый прием пищи STRAIN. Пользователь исправил распознавание. Верни только валидный JSON без markdown в том же формате, что ниже.

Текущее распознавание:
$pretty

Исправление пользователя:
${fix.ifEmpty { "Пользователь хочет уточнить состав." }}

Если речь о готовом продукте/упаковке — опирайся на этикетку или известную пищевую ценность бренда, а не на «оценку на глаз».

Формат ответа:
{
  "title": "Название приема пищи",
  "items": [
    {"name": "продукт", "portion_g": 150, "calories": 250, "protein_g": 12, "fat_g": 8, "carbs_g": 30}
  ],
  "total": {"calories": 250, "protein_g": 12, "fat_g": 8, "carbs_g": 30},
  "confidence": "средняя",
  "comment": "что изменилось"
}
""".trim()
    }

    /// Целое — без «.0»: ИИ видит те же числа, что показаны на экране.
    private fun jsonNumber(value: Double): JsonPrimitive =
        if (value.isFinite() && value == Math.floor(value) && kotlin.math.abs(value) < 1e15) JsonPrimitive(value.toLong())
        else JsonPrimitive(value)
}
