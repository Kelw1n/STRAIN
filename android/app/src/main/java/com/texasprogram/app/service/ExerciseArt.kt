package com.texasprogram.app.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.texasprogram.app.R
import java.io.File

/// Картинки упражнений — как в iOS-версии.
///
/// Стандартная подбирается по ключевым словам в названии, так её получают
/// и свои упражнения. Поверх можно поставить свою из галереи — она хранится
/// по названию, поэтому одна и та же во всех днях и неделях.
///
/// Декодирование — только в фоне (см. `ExerciseThumb`), готовые картинки
/// лежат в кэше: прокрутка списка не ждёт диска.
object ExerciseArt {
    /// Растёт при каждой смене своей картинки — миниатюры перечитываются.
    var revision by mutableIntStateOf(0)
        private set

    // Прямые ссылки на ресурсы, а не поиск по имени: иначе сжатие ресурсов
    // в release выбросило бы картинки как неиспользуемые.
    private val drawables = mapOf(
        "bench" to R.drawable.ex_bench,
        "cable_row" to R.drawable.ex_cable_row,
        "calf" to R.drawable.ex_calf,
        "crunch" to R.drawable.ex_crunch,
        "curl" to R.drawable.ex_curl,
        "deadlift" to R.drawable.ex_deadlift,
        "dips" to R.drawable.ex_dips,
        "front_squat" to R.drawable.ex_front_squat,
        "generic" to R.drawable.ex_generic,
        "goblet" to R.drawable.ex_goblet,
        "hanging_raise" to R.drawable.ex_hanging_raise,
        "hip_thrust" to R.drawable.ex_hip_thrust,
        "hyperext" to R.drawable.ex_hyperext,
        "incline" to R.drawable.ex_incline,
        "lateral_raise" to R.drawable.ex_lateral_raise,
        "leg_curl" to R.drawable.ex_leg_curl,
        "leg_extension" to R.drawable.ex_leg_extension,
        "leg_press" to R.drawable.ex_leg_press,
        "lunge" to R.drawable.ex_lunge,
        "ohp" to R.drawable.ex_ohp,
        "plank" to R.drawable.ex_plank,
        "pulldown" to R.drawable.ex_pulldown,
        "pullup" to R.drawable.ex_pullup,
        "pushdown" to R.drawable.ex_pushdown,
        "pushup" to R.drawable.ex_pushup,
        "rdl" to R.drawable.ex_rdl,
        "row" to R.drawable.ex_row,
        "seated_press" to R.drawable.ex_seated_press,
        "shrug" to R.drawable.ex_shrug,
        "squat" to R.drawable.ex_squat
    )

    /// Правила по порядку: узкие раньше общих. «Тяга в наклоне» должна стать
    /// тягой раньше, чем «наклон» сделает её наклонным жимом.
    private val rules: List<Pair<String, (String) -> Boolean>> = listOf(
        "front_squat" to { t -> t.contains("фронтальн") || t.contains("зерчер") },
        "goblet" to { t -> t.contains("гоблет") || (t.contains("присед") && (t.contains("гир") || t.contains("гантел") || t.contains("колодец"))) },
        "lunge" to { t -> t.contains("выпад") || t.contains("болгар") },
        "leg_press" to { t -> t.contains("жим ног") || t.contains("жим платформ") },
        "squat" to { t -> t.contains("присед") || t.contains("squat") },
        "rdl" to { t -> t.contains("румынск") || t.contains("прямых ног") || t.contains("гуд монинг") || t.contains("rdl") },
        "deadlift" to { t -> t.contains("станов") || t.contains("трэп") || t.contains("плинт") || t.contains("deadlift") },
        "hip_thrust" to { t -> t.contains("мост") || t.contains("ягодичн") || t.contains("thrust") },
        "hyperext" to { t -> t.contains("гиперэкстенз") || t.contains("glute ham") || t.contains("ghr") },
        "pullup" to { t -> t.contains("подтяг") || t.contains("pull-up") || t.contains("pullup") },
        "row" to { t -> t.contains("тяг") && t.contains("наклон") },
        "pulldown" to { t -> (t.contains("тяг") && (t.contains("верхн") || t.contains("вертикальн") || t.contains("хамер"))) || t.contains("pulldown") },
        "cable_row" to { t -> t.contains("тяг") && (t.contains("сидя") || t.contains("горизонтальн") || t.contains("блок")) },
        "row" to { t -> t.contains("тяг") || t.contains("row") },
        "dips" to { t -> t.contains("брусь") || t.contains("dips") },
        "ohp" to { t -> t.contains("отжиман") && t.contains("стойке") },
        "pushup" to { t -> t.contains("отжиман") || t.contains("push-up") },
        "incline" to { t -> t.contains("жим") && t.contains("наклон") },
        "bench" to { t -> t.contains("жим") && t.contains("леж") },
        "seated_press" to { t -> t.contains("жим") && (t.contains("сидя") || t.contains("гантел")) },
        "ohp" to { t -> t.contains("жим") && (t.contains("стоя") || t.contains("армейск") || t.contains("вертикальн") || t.contains("z-жим") || t.contains("гир") || t.contains("над голов")) },
        "bench" to { t -> t.contains("жим") || t.contains("bench") },
        "lateral_raise" to { t -> t.contains("мах") || t.contains("разведен") || t.contains("дельт") },
        "calf" to { t -> t.contains("носк") || t.contains("икр") || t.contains("голен") },
        "curl" to { t -> t.contains("бицепс") || t.contains("сгибание рук") || t.contains("молот") || t.contains("curl") },
        "pushdown" to { t -> t.contains("трицепс") || t.contains("разгибание рук") || t.contains("французск") },
        "leg_curl" to { t -> t.contains("сгибание ног") },
        "leg_extension" to { t -> t.contains("разгибание ног") },
        "plank" to { t -> t.contains("планк") || t.contains("plank") },
        "hanging_raise" to { t -> t.contains("вис") || t.contains("подъем ног") },
        "crunch" to { t -> t.contains("скруч") || t.contains("пресс") || t.contains("кранч") || t.contains("молитв") || t.contains("колес") },
        "shrug" to { t -> t.contains("шраг") || t.contains("фермер") || t.contains("трапец") || t.contains("предплеч") || t.contains("шея") }
    )

    /// Ключ стандартной иллюстрации для названия упражнения.
    fun key(name: String): String {
        val text = name.lowercase().replace('ё', 'е')
        return rules.firstOrNull { it.second(text) }?.first ?: "generic"
    }

    // Около 8 МБ: три десятка картинок 240×240 и свои 480×480 с запасом.
    // Лениво: подбор ключа по названию не должен тянуть за собой кэш картинок.
    private val cache by lazy {
        object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
        }
    }

    private fun customFile(context: Context, name: String): File {
        val safe = name.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.joinToString("_")
        return File(File(context.filesDir, "exercise_images"), (safe.ifEmpty { "exercise" }) + ".jpg")
    }

    fun hasCustomImage(context: Context, name: String): Boolean = customFile(context, name).exists()

    /// Уже готовая картинка — без диска. Нет в кэше — `null`, грузить через `load`.
    fun cached(name: String): Bitmap? = cache.get(cacheKey(name))

    private fun cacheKey(name: String): String = "$revision:$name"

    /// Своя картинка, если поставлена, иначе стандартная. Вызывать не в главном потоке.
    fun load(context: Context, name: String): Bitmap? {
        val key = cacheKey(name)
        cache.get(key)?.let { return it }
        val custom = customFile(context, name)
        val bitmap = if (custom.exists()) {
            BitmapFactory.decodeFile(custom.path)
        } else {
            val id = drawables[key(name)] ?: drawables.getValue("generic")
            cache.get("res:$id") ?: BitmapFactory.decodeResource(context.resources, id)?.also {
                it.prepareToDraw()
                cache.put("res:$id", it)
            }
        }
        bitmap?.let { cache.put(key, it) }
        return bitmap
    }

    /// Сохраняет свою картинку квадратом 480×480: больше на экране не нужно,
    /// а полноразмерное фото весило бы мегабайты. Вызывать не в главном потоке.
    fun setCustomImage(context: Context, name: String, uri: Uri): Boolean {
        val source = runCatching {
            // Сначала размеры, потом декодирование с уменьшением — не тянем в память 12 Мп.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 480) sample *= 2
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }.getOrNull() ?: return false
        val square = squareThumbnail(source, 480)
        val file = customFile(context, name)
        file.parentFile?.mkdirs()
        val ok = runCatching { file.outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 85, it) } }.isSuccess
        if (ok) revision++
        return ok
    }

    fun removeCustomImage(context: Context, name: String) {
        runCatching { customFile(context, name).delete() }
        revision++
    }

    fun squareThumbnail(source: Bitmap, side: Int): Bitmap {
        val edge = minOf(source.width, source.height)
        val left = (source.width - edge) / 2
        val top = (source.height - edge) / 2
        val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(
            source,
            Rect(left, top, left + edge, top + edge),
            Rect(0, 0, side, side),
            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
        )
        return out
    }
}
