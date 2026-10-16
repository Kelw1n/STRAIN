package com.texasprogram.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.texasprogram.app.R
import com.texasprogram.app.model.formatWeight
import com.texasprogram.app.service.ExerciseArt
import com.texasprogram.app.service.RuDate
import com.texasprogram.app.service.RuPlural
import com.texasprogram.app.service.WorkoutSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/// Что попадает на карточку тренировки. Собирается из активной тренировки
/// до «Дальше»: после завершения сессия очищается.
data class WorkoutShareSummary(
    val title: String,
    val dateText: String,
    val durationSeconds: Int,
    val tonnage: Double,
    val sets: Int,
    val records: Int,
    val lines: List<Line>
) {
    data class Line(
        val name: String,
        val doneSets: Int,
        /// Лучший подход: «100 кг × 5».
        val best: String,
        val isRecord: Boolean
    )

    companion object {
        fun current(title: String, durationSeconds: Int): WorkoutShareSummary {
            val manager = WorkoutSessionManager
            val lines = manager.exercises.mapNotNull { exercise ->
                val done = exercise.sets.filter { it.isDone }
                val top = done.maxWithOrNull(compareBy({ it.weight }, { it.reps })) ?: return@mapNotNull null
                Line(
                    name = exercise.prescription.name,
                    doneSets = done.size,
                    best = "${formatWeight(top.weight)} кг × ${top.reps}",
                    isRecord = done.any { it.isRecord }
                )
            }
            return WorkoutShareSummary(
                title = title,
                dateText = RuDate.dayMonth(LocalDate.now()),
                durationSeconds = durationSeconds,
                tonnage = manager.totalTonnage,
                sets = manager.totalCompletedSets,
                records = manager.exercises.sumOf { ex -> ex.sets.count { it.isRecord } },
                lines = lines
            )
        }
    }
}

/// Карточка тренировки 9:16 для сторис. Рисуется прямо в картинку 1080×1920 —
/// ровно размер сторис, ничего не обрежется. Одна и та же картинка идёт
/// и в превью, и в «Поделиться».
object WorkoutShareCard {
    const val WIDTH = 1080
    const val HEIGHT = 1920
    private const val UNIT = 3f
    private const val MAX_LINES = 5
    private const val ACCENT = 0xFFFF6029.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    private fun u(value: Float) = value * UNIT
    private fun alpha(color: Int, alpha: Float) = (color and 0x00FFFFFF) or ((alpha * 255).toInt() shl 24)

    private val black: Typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    private val bold: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    private val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private fun paint(size: Float, color: Int, face: Typeface) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = u(size)
        this.color = color
        typeface = face
    }

    /// Рисует карточку. Вызывать не в главном потоке: картинки упражнений и фото читаются с диска.
    fun render(context: Context, summary: WorkoutShareSummary, photo: Uri?): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawBackground(context, canvas, photo)

        val pad = u(26f)
        val width = WIDTH - pad * 2

        // Шапка: знак STRAIN и дата.
        runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.splash_mark)
        }.getOrNull()?.let { mark ->
            val h = u(20f)
            val w = h * mark.width / mark.height
            val markPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = PorterDuffColorFilter(ACCENT, PorterDuff.Mode.SRC_IN)
            }
            canvas.drawBitmap(mark, null, RectF(pad, pad, pad + w, pad + h), markPaint)
        }
        val datePaint = paint(13f, alpha(WHITE, 0.75f), medium)
        canvas.drawText(summary.dateText, WIDTH - pad - datePaint.measureText(summary.dateText), pad + u(15f), datePaint)

        // Низ карточки собираем снизу вверх: сначала меряем, потом рисуем.
        val titlePaint = paint(26f, WHITE, black)
        val title = StaticLayout.Builder
            .obtain(summary.title.uppercase(), 0, summary.title.length, titlePaint, width.toInt())
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()

        val gap = u(16f)
        val chipHeight = if (summary.records > 0) u(28f) else 0f
        val statsHeight = u(70f)
        val shown = summary.lines.take(MAX_LINES)
        val rest = summary.lines.size - shown.size
        val linesHeight = shown.size * u(34f) + (shown.size - 1).coerceAtLeast(0) * u(10f) + if (rest > 0) u(26f) else 0f
        val total = title.height + (if (chipHeight > 0) gap + chipHeight else 0f) + gap + statsHeight +
            (if (shown.isNotEmpty()) gap + linesHeight else 0f)
        var y = HEIGHT - pad - total

        canvas.save()
        canvas.translate(pad, y)
        title.draw(canvas)
        canvas.restore()
        y += title.height

        if (chipHeight > 0) {
            y += gap
            val text = "🏆 ${summary.records} " + RuPlural.word(summary.records, "рекорд", "рекорда", "рекордов")
            val chipPaint = paint(13f, 0xFF000000.toInt(), bold)
            val chipWidth = chipPaint.measureText(text) + u(24f)
            canvas.drawRoundRect(RectF(pad, y, pad + chipWidth, y + chipHeight), chipHeight / 2, chipHeight / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT })
            canvas.drawText(text, pad + u(12f), y + chipHeight / 2 + u(4.5f), chipPaint)
            y += chipHeight
        }

        y += gap
        drawStats(canvas, summary, RectF(pad, y, pad + width, y + statsHeight))
        y += statsHeight

        if (shown.isNotEmpty()) {
            y += gap
            shown.forEach { line ->
                drawLine(context, canvas, line, pad, y, width)
                y += u(34f) + u(10f)
            }
            if (rest > 0) {
                canvas.drawText("и ещё ${RuPlural.exercises(rest)}", pad, y + u(10f), paint(12f, alpha(WHITE, 0.5f), medium))
            }
        }
        return bitmap
    }

    private fun drawBackground(context: Context, canvas: Canvas, photo: Uri?) {
        canvas.drawColor(0xFF000000.toInt())
        val image = photo?.let { decodePhoto(context, it) }
        if (image != null) {
            // Своё фото — фоном, по центру с обрезкой; снизу затемнено под текст.
            val scale = maxOf(WIDTH.toFloat() / image.width, HEIGHT.toFloat() / image.height)
            val w = image.width * scale
            val h = image.height * scale
            val left = (WIDTH - w) / 2
            val top = (HEIGHT - h) / 2
            canvas.drawBitmap(image, null, RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG))
            val shade = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, HEIGHT.toFloat(),
                    intArrayOf(alpha(0, 0.35f), alpha(0, 0.15f), alpha(0, 0.85f), alpha(0, 0.95f)),
                    floatArrayOf(0f, 0.3f, 0.62f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), shade)
        } else {
            val glow = Paint().apply {
                shader = RadialGradient(0f, 0f, u(420f), alpha(ACCENT, 0.45f), alpha(ACCENT, 0f), Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), glow)
        }
    }

    /// Фото из галереи с уменьшением при чтении: 12 Мп в память не тянем.
    private fun decodePhoto(context: Context, uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= WIDTH) sample *= 2
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }.getOrNull()

    private fun drawStats(canvas: Canvas, summary: WorkoutShareSummary, box: RectF) {
        val radius = u(18f)
        canvas.drawRoundRect(box, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alpha(WHITE, 0.08f) })
        canvas.drawRoundRect(box, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = u(1f)
            color = alpha(WHITE, 0.1f)
        })
        val values = listOf(
            formatClock(summary.durationSeconds) to "время",
            tonnageText(summary.tonnage) to "тоннаж",
            "${summary.sets}" to RuPlural.word(summary.sets, "подход", "подхода", "подходов")
        )
        val column = box.width() / values.size
        val valuePaint = paint(20f, WHITE, black).apply { textAlign = Paint.Align.CENTER }
        val titlePaint = paint(11f, alpha(WHITE, 0.55f), medium).apply { textAlign = Paint.Align.CENTER }
        values.forEachIndexed { index, (value, title) ->
            val cx = box.left + column * (index + 0.5f)
            canvas.drawText(value, cx, box.top + u(33f), valuePaint)
            canvas.drawText(title, cx, box.top + u(52f), titlePaint)
        }
    }

    private fun drawLine(context: Context, canvas: Canvas, line: WorkoutShareSummary.Line, left: Float, top: Float, width: Float) {
        val thumb = u(34f)
        val rect = RectF(left, top, left + thumb, top + thumb)
        val clip = Path().apply { addRoundRect(rect, thumb * 0.25f, thumb * 0.25f, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawColor(0xFFD9D9D9.toInt())
        ExerciseArt.load(context, line.name)?.let { art ->
            canvas.drawBitmap(art, Rect(0, 0, art.width, art.height), rect, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        canvas.restore()

        val bestPaint = paint(13f, alpha(WHITE, 0.9f), bold)
        val best = (if (line.isRecord) "🏆 " else "") + line.best
        val bestWidth = bestPaint.measureText(best)
        canvas.drawText(best, left + width - bestWidth, top + thumb / 2 + u(4.5f), bestPaint)

        val textLeft = left + thumb + u(10f)
        val namePaint = paint(14f, WHITE, bold)
        val available = (width - thumb - u(10f) - bestWidth - u(10f)).coerceAtLeast(u(40f))
        val name = TextUtils.ellipsize(line.name, namePaint, available, TextUtils.TruncateAt.END).toString()
        canvas.drawText(name, textLeft, top + u(15f), namePaint)
        canvas.drawText(RuPlural.sets(line.doneSets), textLeft, top + u(30f), paint(11f, alpha(WHITE, 0.5f), medium))
    }

    private fun tonnageText(value: Double): String =
        if (value < 1000) "${value.toInt()} кг" else String.format(java.util.Locale.US, "%.1f т", value / 1000).replace('.', ',')

    /// Картинка — во временную папку, оттуда системное «Поделиться» отдаёт её
    /// в Instagram, Telegram или галерею. Разрешение на память не нужно.
    fun share(context: Context, bitmap: Bitmap) {
        runCatching {
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            val file = File(dir, "strain-workout.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
            val send = Intent(Intent.ACTION_SEND)
                .setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(
                Intent.createChooser(send, "Поделиться тренировкой").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

/// Превью карточки и кнопка «Поделиться». Первое фото из итогов — фоном.
@Composable
fun ShareCardSection(title: String, durationSeconds: Int, photo: Uri?) {
    val context = LocalContext.current
    val haptics = rememberHapticEngine()
    val card by produceState<Pair<Bitmap, ImageBitmap>?>(null, title, durationSeconds, photo) {
        // Название печатают по букве — перерисовываем, когда человек остановился.
        delay(300)
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = WorkoutShareCard.render(context, WorkoutShareSummary.current(title, durationSeconds), photo)
                bitmap to bitmap.asImageBitmap()
            }.getOrNull()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "ПОДЕЛИТЬСЯ",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF161616))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                Modifier
                    .width(108.dp)
                    .size(width = 108.dp, height = 192.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black)
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                val ready = card
                if (ready != null) {
                    Image(ready.second, contentDescription = null, contentScale = ContentScale.Fit)
                } else {
                    CircularProgressIndicator(color = Theme.accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Карточка для сторис", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (photo == null) "Время, тоннаж, рекорды и упражнения. Добавь фото выше — оно станет фоном."
                    else "Первое фото выше — фоном карточки.",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp
                )
                Row(
                    Modifier
                        .background(Theme.accent, CircleShape)
                        .clip(CircleShape)
                        .pressable(enabled = card != null) {
                            haptics.medium()
                            card?.let { WorkoutShareCard.share(context, it.first) }
                        }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.Share, null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Text("Поделиться", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
