package com.texasprogram.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/// Оценка усилия 1–10, как у REBRN: три клина растут слева направо,
/// палец ведёт по ним — заливка идёт следом, цифра и подпись меняются.
/// Увёл палец к самому левому краю — оценка снимается (её можно не ставить).
object EffortScale {
    val titles = mapOf(
        1 to "разминка", 2 to "очень легко", 3 to "спокойно", 4 to "комфортно", 5 to "умеренно",
        6 to "рабоче", 7 to "тяжеловато", 8 to "тяжело", 9 to "на пределе", 10 to "до отказа"
    )

    val easy = Color(0xFF5CDB4A)
    val normal = Color(0xFFFAC745)
    val hard = Color(0xFFF25224)

    /// Зоны: 1–3 легко, 4–7 нормально, 8–10 очень тяжело. Ширина клина — по числу оценок в нём.
    val zones = listOf(1..3, 4..7, 8..10)

    fun color(score: Int): Color = when {
        score <= 3 -> easy
        score <= 7 -> normal
        else -> hard
    }

    fun zone(score: Int): Int = zones.indexOfFirst { score in it }.coerceAtLeast(0)

    /// Строка для истории: «8/10» — так же пишет iOS.
    fun text(score: Int?): String? = score?.let { "$it/10" }
}

private val BAR_HEIGHT = 30.dp
private val GAP = 8.dp

@Composable
fun EffortSlider(score: Int?, onChange: (Int?) -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHapticEngine()
    val current by rememberUpdatedState(score)
    val change by rememberUpdatedState(onChange)
    val active = score?.let { EffortScale.zone(it) }

    Column(
        modifier
            .fillMaxWidth()
            .background(Theme.tone(0xFF161616), RoundedCornerShape(22.dp))
            .border(1.dp, Theme.ink.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
            .padding(18.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Усилие"
                stateDescription = score?.let { "$it из 10, ${EffortScale.titles[it]}" } ?: "не указано"
                progressBarRangeInfo = ProgressBarRangeInfo((score ?: 0).toFloat(), 0f..10f, steps = 9)
                setProgress { value ->
                    val next = value.roundToInt().coerceIn(0, 10)
                    change(if (next == 0) null else next)
                    true
                }
            },
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(38.dp)) {
            if (score != null) {
                RollingText(
                    text = score.toString(),
                    style = TextStyle(color = Theme.ink, fontSize = 30.sp, fontWeight = FontWeight.Black, fontFeatureSettings = "tnum")
                )
                Spacer(Modifier.width(3.dp))
                Text("/10", color = Theme.ink.copy(alpha = 0.35f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Box(Modifier.width(22.dp).height(4.dp).background(Theme.ink.copy(alpha = 0.15f), CircleShape))
            }
            Spacer(Modifier.weight(1f))
            AnimatedContent(
                targetState = score,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                label = "effortTitle"
            ) { value ->
                Text(
                    text = value?.let { EffortScale.titles[it] } ?: "Можно пропустить",
                    color = value?.let { EffortScale.color(it) } ?: Theme.ink.copy(alpha = 0.4f),
                    fontSize = 14.sp,
                    fontWeight = if (value == null) FontWeight.Medium else FontWeight.SemiBold
                )
            }
        }

        val fillTarget = score?.toFloat() ?: 0f
        val fill by animateFloatAsState(fillTarget, Motion.snappy(), label = "effortFill")
        val color by animateColorAsState(score?.let { EffortScale.color(it) } ?: Color.Transparent, tween(180), label = "effortColor")

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT)
                .pointerInput(Unit) {
                    val gapPx = GAP.toPx()
                    fun pick(x: Float) {
                        val next = scoreAt(x, size.width.toFloat(), gapPx)
                        if (next != current) {
                            haptics.selection()
                            change(next)
                        }
                    }
                    // Ведение вбок двигает оценку, касание ставит её. Вертикальное
                    // движение не перехватываем: экран итогов прокручивается.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                            change.consume()
                            pick(change.position.x)
                        }
                        if (start != null) {
                            horizontalDrag(start.id) { change ->
                                change.consume()
                                pick(change.position.x)
                            }
                        } else {
                            // Отпустили, не сдвинув, — это касание. Ушли в прокрутку — не трогаем.
                            val last = currentEvent.changes.firstOrNull { it.id == down.id }
                            if (last != null && !last.pressed && !last.isConsumed) pick(down.position.x)
                        }
                    }
                }
        ) {
            val gapPx = GAP.toPx()
            val segments = segmentFrames(size.width, size.height, gapPx)
            val fillWidth = fillWidth(fill, segments)
            segments.forEach { frame ->
                drawWedge(frame, size.width, Theme.ink.copy(alpha = 0.07f))
                if (fillWidth > frame.left) {
                    clipRect(left = frame.left, top = 0f, right = minOf(frame.right, fillWidth), bottom = size.height) {
                        drawWedge(frame, size.width, color)
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth()) {
            ZoneLabel("ЛЕГКО", EffortScale.easy, active == 0)
            Spacer(Modifier.weight(1f))
            ZoneLabel("НОРМАЛЬНО", EffortScale.normal, active == 1)
            Spacer(Modifier.weight(1f))
            ZoneLabel("ОЧЕНЬ ТЯЖЕЛО", EffortScale.hard, active == 2)
        }
    }
}

@Composable
private fun ZoneLabel(text: String, color: Color, on: Boolean) {
    val alpha by animateFloatAsState(if (on) 1f else 0.35f, tween(200), label = "zone")
    Text(text, color = color.copy(alpha = alpha), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.4.sp)
}

/// Клинья по ширине зон (3 : 4 : 3) с зазорами между ними.
private fun segmentFrames(width: Float, height: Float, gap: Float): List<Rect> {
    val usable = width - gap * (EffortScale.zones.size - 1)
    var x = 0f
    return EffortScale.zones.map { zone ->
        val w = usable * zone.count() / 10f
        val rect = Rect(x, 0f, x + w, height)
        x += w + gap
        rect
    }
}

/// Ширина заливки для дробной оценки — так заливка едет плавно между делениями.
private fun fillWidth(value: Float, segments: List<Rect>): Float {
    if (value <= 0f) return 0f
    EffortScale.zones.forEachIndexed { index, zone ->
        val frame = segments[index]
        if (value <= zone.last) {
            val part = ((value - (zone.first - 1)) / zone.count()).coerceIn(0f, 1f)
            return frame.left + frame.width * part
        }
    }
    return segments.last().right
}

/// Какая оценка под пальцем. Совсем у левого края — оценки нет.
private fun scoreAt(x: Float, width: Float, gap: Float): Int? {
    if (width <= 0f || x <= 6f) return null
    val frames = segmentFrames(width, 1f, gap)
    EffortScale.zones.forEachIndexed { index, zone ->
        val frame = frames[index]
        // Зазор между клиньями относим к левому.
        val right = if (index == frames.lastIndex) Float.MAX_VALUE else frame.right + gap
        if (x < right) {
            val part = ((x - frame.left) / frame.width).coerceIn(0f, 1f)
            return minOf(zone.last, zone.first + (part * zone.count()).toInt())
        }
    }
    return 10
}

/// Клин: нижний край ровный, верхний поднимается слева направо от трети до полной высоты.
private fun DrawScope.drawWedge(frame: Rect, total: Float, color: Color) {
    if (color.alpha == 0f) return
    val h = size.height
    fun top(x: Float) = h - h * (0.34f + 0.66f * (x / total).coerceIn(0f, 1f))
    val r = 5.dp.toPx()
    val left = frame.left
    val right = frame.right
    val topLeft = top(left)
    val topRight = top(right)
    val path = Path().apply {
        moveTo(left + r, h)
        lineTo(right - r, h)
        quadraticBezierTo(right, h, right, h - r)
        lineTo(right, topRight + r)
        quadraticBezierTo(right, topRight, right - r, topRight)
        lineTo(left + r, topLeft)
        quadraticBezierTo(left, topLeft, left, topLeft + r)
        lineTo(left, h - r)
        quadraticBezierTo(left, h, left + r, h)
        close()
    }
    drawPath(path, color)
}
