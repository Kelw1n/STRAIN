package com.texasprogram.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.BenchSetKind
import com.texasprogram.app.model.AccessoryHint
import com.texasprogram.app.model.ExercisePrescription
import com.texasprogram.app.model.LoadPrescription
import com.texasprogram.app.model.formatWeight

/// Цвета и градиенты для типов подходов волны жима.
val BenchSetKind.tint: Color
    get() = when (this) {
        BenchSetKind.NORMAL -> Theme.accent
        BenchSetKind.NEGATIVE -> Theme.accentDeep
        BenchSetKind.TEST -> Theme.warning
        BenchSetKind.RECORD -> Theme.record
    }

val BenchSetKind.gradient: Brush
    get() = when (this) {
        BenchSetKind.NORMAL -> Theme.accentGradient
        BenchSetKind.NEGATIVE -> Theme.deepGradient
        BenchSetKind.TEST, BenchSetKind.RECORD -> Theme.recordGradient
    }

val BenchSetKind.icon
    get() = when (this) {
        BenchSetKind.NORMAL -> Icons.Outlined.Circle
        BenchSetKind.NEGATIVE -> Icons.Filled.CheckCircle
        BenchSetKind.TEST -> Icons.Filled.LocalFireDepartment
        BenchSetKind.RECORD -> Icons.Filled.LocalFireDepartment
    }

/// Карточка упражнения. Жим волны — кликабельная, ведёт в раздел «Жим 14».
@Composable
fun ExerciseCard(
    exercise: ExercisePrescription,
    modifier: Modifier = Modifier,
    onOpenBench: ((Int) -> Unit)? = null,
    /// Нет трекера — карточка без точек, как на экране пикирования.
    sets: SetTracker? = null,
    /// Открыть историю движения. Нет обработчика — значка нет.
    onOpenHistory: (() -> Unit)? = null,
    /// Подсказка по весу подсобки. Нет — упражнение со своим весом в плане.
    hint: AccessoryHint? = null
) {
    val isBenchLink = exercise.benchSession != null && onOpenBench != null
    val iconGradient = when {
        exercise.benchSession != null -> Theme.recordGradient
        exercise.isOptional -> Theme.recordGradient
        else -> Theme.accentGradient
    }
    val icon = when {
        exercise.benchSession != null -> Icons.Filled.MonitorHeart
        exercise.isOptional -> Icons.Filled.Add
        else -> Icons.Filled.FitnessCenter
    }

    val base = if (isBenchLink) {
        modifier.pressable { onOpenBench?.invoke(exercise.benchSession!!) }
    } else {
        modifier
    }

    CardView(base, padding = 16.dp, spacing = 10.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    exercise.name,
                    color = Theme.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (exercise.sets > 0) "${exercise.sets} подходов × ${exercise.reps}" else exercise.reps,
                    color = Theme.textSecondary,
                    fontSize = 12.sp
                )
            }
            // Значок истории — рядом с названием, а не на всей карточке:
            // внутри неё уже нажимаются точки подходов.
            if (onOpenHistory != null) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Theme.accent.copy(alpha = 0.13f))
                        .pressable(onClick = onOpenHistory),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.ShowChart,
                        contentDescription = "История упражнения",
                        tint = Theme.accent,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
            }

            Spacer(Modifier.width(8.dp))
            when {
                isBenchLink -> Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = Theme.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
                // У подсобки вес ведёт прогрессия — он важнее, чем «РПЕ 8».
                hint?.weight != null -> Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${formatWeight(hint.weight)} кг",
                        color = if (hint.isStepUp) Theme.success else Theme.accent,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(exercise.load.displayText, color = Theme.textTertiary, fontSize = 9.sp)
                }
                exercise.load is LoadPrescription.Kilograms -> GradientText(
                    exercise.load.displayText,
                    fontSize = 17.sp,
                    weight = FontWeight.Bold
                )
                exercise.load is LoadPrescription.TestOneRepMax -> TagBadge(
                    "Тест 1ПМ",
                    icon = Icons.Filled.LocalFireDepartment,
                    gradient = Theme.recordGradient
                )
                else -> OutlineBadge(exercise.load.displayText, tint = Theme.warning)
            }
        }
        if (isBenchLink) {
            GradientText(exercise.load.displayText, fontSize = 13.sp, weight = FontWeight.SemiBold)
        }

        if (hint != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Icon(
                    if (hint.isStepUp) Icons.Filled.ArrowCircleUp else Icons.Filled.TrackChanges,
                    contentDescription = null,
                    tint = if (hint.isStepUp) Theme.success else Theme.accent,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(hint.text, color = Theme.textSecondary, fontSize = 11.sp)
            }
        }

        if (sets != null && exercise.sets > 0) {
            SetDotsView(exercise.sets, sets)
        }

        // Блины и разминка есть только там, где задан конкретный вес.
    }
}

@Composable
fun CompleteButton(isCompleted: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    GradientButton(
        text = if (isCompleted) "Выполнено" else "Отметить тренировку",
        modifier = modifier,
        icon = if (isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
        gradient = if (isCompleted) Theme.successGradient else Theme.accentGradient
    ) {
        haptics()
        onClick()
    }
}

@Composable
fun StatusCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    gradient: Brush,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    CardView(modifier, padding = 16.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(gradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = Theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Theme.textSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = Theme.textPrimary, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}
