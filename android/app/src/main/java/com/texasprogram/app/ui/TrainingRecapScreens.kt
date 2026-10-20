package com.texasprogram.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.SocialFormat
import com.texasprogram.app.model.TrainingRecap
import com.texasprogram.app.model.formatWeight
import com.texasprogram.app.service.RuPlural
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// MARK: - Карточка входа в итоги

/// Цвета фона слайдов: от фиолетового к тёплым и обратно.
private val RECAP_PALETTE = listOf(
    Color(0xFF5C38F2) to Color(0xFF140F38),
    Color(0xFFFA5C3D) to Color(0xFF4D0D1A),
    Color(0xFF1AB38C) to Color(0xFF052929),
    Color(0xFFFAB829) to Color(0xFF5C2405),
    Color(0xFFE6339E) to Color(0xFF330833),
    Color(0xFF338FFA) to Color(0xFF081442),
    Color(0xFF8C8C99) to Color(0xFF0F0F14)
)

private fun palette(index: Int) = RECAP_PALETTE[index % RECAP_PALETTE.size]

/// Карточка в «Прогрессе»: итоги этого и прошлого месяца, этого и прошлого года.
@Composable
fun TrainingRecapEntryCard(profile: ProgramProfile, modifier: Modifier = Modifier) {
    val haptics = rememberHapticEngine()
    val periods = remember(profile.completionLog) { TrainingRecap.periods(profile.completionLog) }
    var opened by remember { mutableStateOf<TrainingRecap?>(null) }
    if (periods.isEmpty()) return
    CardView(modifier, spacing = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Theme.accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("ИТОГИ", color = Theme.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            periods.forEachIndexed { index, period ->
                val (from, to) = palette(index)
                Column(
                    Modifier
                        .widthIn(min = 128.dp)
                        .pressable {
                            haptics.medium()
                            opened = TrainingRecap.build(profile, period)
                        }
                        .clip(RoundedCornerShape(18.dp))
                        .background(Brush.linearGradient(listOf(from, to)))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(if (period.isYear) "Год" else "Месяц", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(period.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 1)
                }
            }
        }
    }
    opened?.let { recap -> TrainingRecapDialog(recap) { opened = null } }
}

// MARK: - Итоги историями

private enum class RecapSlide { INTRO, TIME, VOLUME, RECORDS, FAVORITE, STREAK, SUMMARY }

/// Итоги историями, как Spotify Wrapped: слайды сами листаются, нажатие справа —
/// дальше, слева — назад, удержание — пауза. Последний слайд — карточка для сторис.
@Composable
fun TrainingRecapDialog(recap: TrainingRecap, onDismiss: () -> Unit) {
    val haptics = rememberHapticEngine()
    val slides = remember(recap) {
        buildList {
            add(RecapSlide.INTRO)
            add(RecapSlide.TIME)
            add(RecapSlide.VOLUME)
            if (recap.records > 0 || recap.heaviest != null) add(RecapSlide.RECORDS)
            if (recap.favorite != null) add(RecapSlide.FAVORITE)
            if (recap.activeWeeks > 0) add(RecapSlide.STREAK)
            add(RecapSlide.SUMMARY)
        }
    }
    var page by remember { mutableIntStateOf(0) }
    var forward by remember { mutableStateOf(true) }
    var paused by remember { mutableStateOf(false) }
    val progress = remember { mutableFloatStateOf(0f) }
    val slide = slides[page.coerceIn(0, slides.size - 1)]

    fun go(step: Int) {
        val next = page + step
        if (next < 0) {
            progress.floatValue = 0f
            return
        }
        if (next >= slides.size) return
        haptics.selection()
        forward = step > 0
        page = next
    }

    // Полоска текущего слайда заполняется сама; дошла — следующий. На последнем стоим.
    LaunchedEffect(page) {
        progress.floatValue = 0f
        if (slides[page] == RecapSlide.SUMMARY) {
            progress.floatValue = 1f
            return@LaunchedEffect
        }
        while (progress.floatValue < 1f) {
            delay(33)
            if (!paused) progress.floatValue = (progress.floatValue + 0.033f / 6f).coerceAtMost(1f)
        }
        go(1)
    }

    val (from, to) = palette(page)
    val top by animateColorAsState(from, tween(600), label = "recapTop")
    val bottom by animateColorAsState(to, tween(600), label = "recapBottom")

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(top, bottom)))
                .pointerInput(slide) {
                    if (slide == RecapSlide.SUMMARY) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            paused = true
                            tryAwaitRelease()
                            paused = false
                        },
                        onTap = { offset -> go(if (offset.x < size.width / 3f) -1 else 1) }
                    )
                }
        ) {
            AnimatedContent(
                targetState = slide,
                transitionSpec = {
                    (slideInHorizontally(Motion.card()) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(260))) togetherWith fadeOut(tween(160))
                },
                label = "recapSlide",
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(start = 26.dp, end = 26.dp, top = 70.dp, bottom = 32.dp)
            ) { current ->
                RecapSlideContent(current, recap, restart = { go(-page) })
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Заполнение читается только при отрисовке: 30 раз в секунду
                    // перерисовывается полоска, а не весь слайд.
                    slides.indices.forEach { index ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.3f))
                                .drawBehind {
                                    val fill = when {
                                        index < page -> 1f
                                        index > page -> 0f
                                        else -> progress.floatValue
                                    }
                                    drawRect(Color.White, size = Size(size.width * fill, size.height))
                                }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(recap.period.title.uppercase(), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(36.dp).pressable(onClick = onDismiss).clip(CircleShape).background(Color.Black.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecapSlideContent(slide: RecapSlide, recap: TrainingRecap, restart: () -> Unit) {
    val white70 = Color.White.copy(alpha = 0.85f)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
        when (slide) {
            RecapSlide.INTRO -> {
                Text("Твой ${recap.period.name}", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Black, lineHeight = 46.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    CountUp(recap.workouts.toDouble(), 92) { "${it.toInt()}" }
                    Spacer(Modifier.width(10.dp))
                    Text(RuPlural.word(recap.workouts, "тренировка", "тренировки", "тренировок"), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 18.dp))
                }
                if (recap.previousWorkouts > 0) {
                    val difference = recap.workouts - recap.previousWorkouts
                    val word = RuPlural.word(kotlin.math.abs(difference), "тренировку", "тренировки", "тренировок")
                    Text(
                        when {
                            difference > 0 -> "На $difference $word больше, чем ${recap.period.previousPhrase} 💪"
                            difference < 0 -> "На ${-difference} $word меньше, чем ${recap.period.previousPhrase}"
                            else -> "Столько же, сколько ${recap.period.previousPhrase}"
                        },
                        color = white70,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text("Нажми, чтобы листать", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 40.dp))
            }
            RecapSlide.TIME -> {
                Text("Время в зале", color = white70, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.Bottom) {
                    CountUp(recap.hours.toDouble(), 84) { "${it.toInt()}" }
                    Text(" ч ", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 14.dp))
                    CountUp(recap.minutes.toDouble(), 54) { "${it.toInt()}" }
                    Text(" мин", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 10.dp))
                }
                Text(
                    "${RuPlural.sets(recap.sets)} и ${SocialFormat.grouped(recap.reps.toDouble())} ${RuPlural.word(recap.reps, "повтор", "повтора", "повторов")} ${recap.period.inPhrase}",
                    color = white70,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                RecapBars(recap.buckets, if (recap.period.isYear) listOf("я", "ф", "м", "а", "м", "и", "и", "а", "с", "о", "н", "д") else recap.buckets.indices.map { "${it + 1} нед" })
            }
            RecapSlide.VOLUME -> {
                Text("Поднято ${recap.period.inPhrase}", color = white70, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.Bottom) {
                    CountUp(recap.volumeKg / 1000, 92) { String.format(java.util.Locale.US, "%.1f", it).replace('.', ',') }
                    Text(" т", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 16.dp))
                }
                recap.volumeComparison?.let { Text("Это $it 🐘", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                recap.volumeChange?.let { change ->
                    Text(
                        if (change >= 0) "На $change% больше, чем ${recap.period.previousPhrase}" else "На ${-change}% меньше, чем ${recap.period.previousPhrase}",
                        color = white70,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            RecapSlide.RECORDS -> {
                Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp).popIn())
                if (recap.records > 0) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        CountUp(recap.records.toDouble(), 92) { "${it.toInt()}" }
                        Spacer(Modifier.width(10.dp))
                        Text(RuPlural.word(recap.records, "рекорд", "рекорда", "рекордов"), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 18.dp))
                    }
                } else {
                    Text("Рекордов не было — значит, всё впереди", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                }
                recap.heaviest?.let { heaviest ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.22f)).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Самый тяжёлый подход", color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("${heaviest.name} · ${formatWeight(heaviest.bestWeight)} кг × ${heaviest.bestReps}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            RecapSlide.FAVORITE -> {
                Text("Любимое упражнение", color = white70, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                recap.favorite?.let { favorite ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ExerciseThumb(favorite.name, 74.dp)
                        Spacer(Modifier.width(16.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(favorite.name, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 32.sp)
                            Text("${favorite.sessions} ${RuPlural.word(favorite.sessions, "тренировка", "тренировки", "тренировок")}", color = white70, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (recap.topByVolume.isNotEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.22f)).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("БОЛЬШЕ ВСЕГО ПОДНЯТО", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
                        recap.topByVolume.forEachIndexed { index, stat ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${index + 1}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(24.dp))
                                Text(stat.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text("${SocialFormat.grouped(stat.volumeKg)} кг", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
            RecapSlide.STREAK -> {
                Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp).popIn())
                Row(verticalAlignment = Alignment.Bottom) {
                    CountUp(recap.longestWeekStreak.toDouble(), 92) { "${it.toInt()}" }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        RuPlural.word(recap.longestWeekStreak, "неделя", "недели", "недель") + " подряд",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 18.dp)
                    )
                }
                Text(
                    "Самая длинная серия: каждую неделю — хотя бы одна тренировка. Всего недель с тренировками — ${recap.activeWeeks}.",
                    color = white70,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                recap.busiestWeekday?.let {
                    Text("Чаще всего ты тренировался(-ась) по ${TrainingRecap.WEEKDAY_DATIVE[it]}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
            RecapSlide.SUMMARY -> RecapSummary(recap, restart)
        }
    }
}

/// Последний слайд: картинка для сторис и «Поделиться».
@Composable
private fun RecapSummary(recap: TrainingRecap, restart: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHapticEngine()
    val card by produceState<Bitmap?>(null, recap) {
        value = withContext(Dispatchers.Default) { TrainingRecapCard.render(context, recap) }
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically)) {
        Box(
            Modifier.fillMaxWidth(0.82f).aspectRatio(9f / 16f).clip(RoundedCornerShape(22.dp)).background(Color.Black.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            val bitmap = card
            if (bitmap == null) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            else Image(bitmap.asImageBitmap(), contentDescription = "Итоги ${recap.period.title}", modifier = Modifier.fillMaxSize().popIn())
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .pressable(enabled = card != null) {
                    haptics.medium()
                    card?.let { WorkoutShareCard.share(context, it, "Поделиться итогами", "strain-recap.png") }
                }
                .clip(CircleShape)
                .background(Color.White),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Share, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Поделиться в сторис", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Black)
        }
        Text("Сначала", color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.pressable(onClick = restart).padding(8.dp))
    }
}

/// Число, которое при появлении набегает от нуля.
@Composable
private fun CountUp(value: Double, size: Int, format: (Double) -> String) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(value) {
        delay(150)
        shown.animateTo(value.toFloat(), tween(1300, easing = FastOutSlowInEasing))
    }
    Text(
        format(shown.value.toDouble()),
        color = Color.White,
        style = TextStyle(fontSize = size.sp, fontWeight = FontWeight.Black, fontFeatureSettings = "tnum", lineHeight = size.sp)
    )
}

/// Столбики по неделям месяца или месяцам года: вырастают снизу по очереди.
@Composable
private fun RecapBars(values: List<Int>, labels: List<String>) {
    val top = maxOf(values.maxOrNull() ?: 0, 1)
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, Motion.bouncy()) }
    Row(
        Modifier.fillMaxWidth().height(150.dp).padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(if (values.size > 6) 6.dp else 12.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        values.forEachIndexed { index, count ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                if (count > 0) Text("$count", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    val height = (maxHeight * (count.toFloat() / top) * grow.value).coerceAtLeast(4.dp)
                    Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = if (count > 0) 0.95f else 0.2f)))
                }
                Text(labels.getOrElse(index) { "" }, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

// MARK: - Картинка для сторис

/// Итоги одной картинкой 1080×1920 — ровно размер сторис, рисуется прямо в
/// картинку, как карточка тренировки.
object TrainingRecapCard {
    private const val WIDTH = 1080
    private const val HEIGHT = 1920
    private const val UNIT = 3f
    private fun u(value: Float) = value * UNIT

    private val black: Typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    private val bold: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    private val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private fun paint(size: Float, color: Int, face: Typeface) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = u(size)
        this.color = color
        typeface = face
    }

    private fun white(alpha: Float) = Color.White.copy(alpha = alpha).toArgb()

    fun render(@Suppress("UNUSED_PARAMETER") context: Context, recap: TrainingRecap): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val (from, to) = palette(0)
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), from.toArgb(), to.toArgb(), Shader.TileMode.CLAMP)
        })
        val pad = u(26f)
        val inner = WIDTH - pad * 2
        var y = pad + u(18f)
        canvas.drawText("STRAIN", pad, y, paint(18f, white(1f), black).apply { letterSpacing = 0.12f })
        val label = if (recap.period.isYear) "ИТОГИ ГОДА" else "ИТОГИ МЕСЯЦА"
        val labelPaint = paint(11f, white(0.75f), bold).apply { letterSpacing = 0.1f }
        canvas.drawText(label, WIDTH - pad - labelPaint.measureText(label), y, labelPaint)
        y += u(52f)
        canvas.drawText(fit(recap.period.title, paint(38f, white(1f), black), inner), pad, y, paint(38f, white(1f), black))
        y += u(26f)

        // Четыре плитки: тренировки, время, объём, рекорды.
        val gap = u(12f)
        val tileWidth = (inner - gap) / 2
        val tileHeight = u(76f)
        val tiles = listOf(
            "${recap.workouts}" to RuPlural.word(recap.workouts, "тренировка", "тренировки", "тренировок"),
            "${recap.hours} ч ${recap.minutes} м" to "в зале",
            String.format(java.util.Locale.US, "%.1f т", recap.volumeKg / 1000).replace('.', ',') to "поднято",
            "${recap.records}" to RuPlural.word(recap.records, "рекорд", "рекорда", "рекордов")
        )
        val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.Black.copy(alpha = 0.22f).toArgb() }
        tiles.forEachIndexed { index, (value, title) ->
            val left = pad + (index % 2) * (tileWidth + gap)
            val top = y + (index / 2) * (tileHeight + gap)
            canvas.drawRoundRect(RectF(left, top, left + tileWidth, top + tileHeight), u(16f), u(16f), tilePaint)
            canvas.drawText(fit(value, paint(26f, white(1f), black), tileWidth - u(28f)), left + u(14f), top + u(38f), paint(26f, white(1f), black))
            canvas.drawText(title, left + u(14f), top + u(60f), paint(13f, white(0.75f), medium))
        }
        y += tileHeight * 2 + gap + u(40f)

        fun row(title: String, value: String) {
            canvas.drawText(title.uppercase(), pad, y, paint(11f, white(0.7f), bold).apply { letterSpacing = 0.1f })
            y += u(24f)
            canvas.drawText(fit(value, paint(18f, white(1f), bold), inner), pad, y, paint(18f, white(1f), bold))
            y += u(34f)
        }
        recap.favorite?.let { row("Любимое", it.name) }
        recap.heaviest?.let { row("Тяжелее всего", "${it.name} · ${formatWeight(it.bestWeight)} кг × ${it.bestReps}") }
        if (recap.longestWeekStreak > 0) {
            row("Серия", "${recap.longestWeekStreak} ${RuPlural.word(recap.longestWeekStreak, "неделя", "недели", "недель")} подряд")
        }
        return bitmap
    }

    /// Длинная строка обрезается многоточием по ширине.
    private fun fit(text: String, paint: TextPaint, width: Float): String =
        TextUtils.ellipsize(text, paint, width, TextUtils.TruncateAt.END).toString()
}
