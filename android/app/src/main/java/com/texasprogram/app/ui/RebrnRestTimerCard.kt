package com.texasprogram.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.service.RestTimer

/// Карточка отдыха в стиле REBRN — как «остров» в iOS-версии: кольцо и «ОТДЫХ»,
/// сколько уже отдыхаешь, упражнение с картинкой и точками подходов,
/// крупный отсчёт с «−» и «+» и «Пропустить».
///
/// Секунды читаются только в маленьких частях (кольцо, два текста):
/// раз в секунду перерисовываются они, а не вся карточка.
@Composable
fun RebrnRestTimerCard(
    timer: RestTimer,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHapticEngine()
    val shape = RoundedCornerShape(26.dp)

    AnimatedVisibility(
        visible = timer.isRunning,
        enter = slideInVertically(Motion.sheet()) { it / 2 } + fadeIn(),
        exit = slideOutVertically(Motion.sheet()) { it / 2 } + fadeOut(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .graphicsLayer {
                    shadowElevation = 20.dp.toPx()
                    this.shape = shape
                    clip = true
                }
                .background(Color(0xFF0D0D0F))
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.22f), Theme.accent.copy(alpha = 0.30f), Color.White.copy(alpha = 0.06f))
                    ),
                    shape = shape
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RestRing(timer)
                Spacer(Modifier.width(6.dp))
                Text("ОТДЫХ", color = Theme.accent, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp)
                Spacer(Modifier.weight(1f))
                ElapsedText(timer)
            }

            ExerciseLine(timer)

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RoundButton(Icons.Filled.Remove, "Минус 30 секунд") {
                    haptics.selection()
                    timer.add(-30)
                }
                Box(Modifier.width(86.dp), contentAlignment = Alignment.Center) {
                    RemainingText(timer)
                }
                RoundButton(Icons.Filled.Add, "Плюс 30 секунд") {
                    haptics.selection()
                    timer.add(30)
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .height(46.dp)
                        .pressable {
                            haptics.medium()
                            timer.stop()
                        }
                        .background(Theme.accent, CircleShape)
                        .padding(horizontal = 22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Пропустить", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/// Кольцо, которое закрывается к концу отдыха. Прогресс читается при рисовании.
@Composable
private fun RestRing(timer: RestTimer) {
    val accent = Theme.accent
    Canvas(Modifier.size(15.dp)) {
        val stroke = 2.4.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(accent.copy(alpha = 0.3f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        drawArc(
            accent, -90f, 360f * (1f - timer.progress), false, Offset(inset, inset), arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun ElapsedText(timer: RestTimer) {
    Text(
        text = timer.elapsedText,
        color = Color.White,
        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
    )
}

@Composable
private fun RemainingText(timer: RestTimer) {
    RollingText(
        text = timer.remainingText,
        style = TextStyle(color = Theme.accent, fontSize = 30.sp, fontWeight = FontWeight.Black, fontFeatureSettings = "tnum"),
        countsDown = true
    )
}

/// Картинка, название и точки подходов: сделанные залиты, следующий — кольцом.
@Composable
private fun ExerciseLine(timer: RestTimer) {
    val name = timer.exerciseName
    val total = timer.totalSets
    val done = timer.completedSets
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (name != null) {
            ExerciseThumb(name = name, size = 44.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = name ?: "Отдых между подходами",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (index in 0 until minOf(total, 8)) {
                    val dot = Modifier.size(7.dp)
                    when {
                        index < done -> Box(dot.background(Theme.accent, CircleShape))
                        index == done -> Box(dot.border(1.5.dp, Theme.accent, CircleShape))
                        else -> Box(dot.background(Color.White.copy(alpha = 0.25f), CircleShape))
                    }
                }
                timer.setDetails?.takeIf { it.isNotEmpty() }?.let { details ->
                    Text(
                        text = details,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .pressable(onClick = onClick)
            .background(Color(0xFF2E2E2E), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}
