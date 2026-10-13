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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    hint: AccessoryHint? = null,
    /// Шаг прибавки упражнения и выбор другого. Нет обработчика — выбора нет.
    step: Double? = null,
    onSelectStep: ((Double) -> Unit)? = null,
    /// «Не сбрасывать» в подсказке о застое.
    onKeepWeight: (() -> Unit)? = null
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
            // Картинка движения, как в iOS-версии; значок поверх — волна жима
            // или необязательное упражнение.
            Box(Modifier.size(48.dp)) {
                ExerciseThumb(name = exercise.name, size = 44.dp)
                if (exercise.benchSession != null || exercise.isOptional) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(iconGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                    }
                }
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
                    if (exercise.sets > 0) "${com.texasprogram.app.service.RuPlural.sets(exercise.sets)} × ${exercise.reps}" else exercise.reps,
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
                    when {
                        hint.isDeload -> Icons.Filled.ArrowCircleDown
                        hint.isStepUp -> Icons.Filled.ArrowCircleUp
                        else -> Icons.Filled.TrackChanges
                    },
                    contentDescription = null,
                    tint = when {
                        hint.isDeload -> Theme.warning
                        hint.isStepUp -> Theme.success
                        else -> Theme.accent
                    },
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(hint.text, color = Theme.textSecondary, fontSize = 11.sp)
            }
            if (hint.weight != null && (onSelectStep != null || (hint.isDeload && onKeepWeight != null))) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (step != null && onSelectStep != null) {
                        WeightStepChip(step, onSelectStep)
                    }
                    if (hint.isDeload && onKeepWeight != null) {
                        Text(
                            "Не сбрасывать",
                            color = Theme.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Theme.surfaceSoft)
                                .pressable(onClick = onKeepWeight)
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        if (sets != null && exercise.sets > 0) {
            SetDotsView(exercise.sets, sets)
        }

        // Блины и разминка есть только там, где задан конкретный вес.
    }
}

/// Чип «Шаг 2,5 кг ▾» с выбором: у каждого зала свои блины и тренажёры.
@Composable
fun WeightStepChip(step: Double, onSelect: (Double) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clip(CircleShape)
                .background(Theme.accent.copy(alpha = 0.12f))
                .pressable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(Icons.Filled.Scale, null, tint = Theme.accent, modifier = Modifier.size(11.dp))
            Text("Шаг ${formatWeight(step)} кг", color = Theme.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Icon(Icons.Filled.KeyboardArrowDown, null, tint = Theme.accent, modifier = Modifier.size(12.dp))
        }
        WeightStepMenu(expanded = expanded, current = step, onDismiss = { expanded = false }) {
            expanded = false
            onSelect(it)
        }
    }
}

/// Список вариантов шага с отметкой текущего.
@Composable
fun WeightStepMenu(expanded: Boolean, current: Double, onDismiss: () -> Unit, onSelect: (Double) -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, modifier = Modifier.background(Theme.dialog)) {
        com.texasprogram.app.model.ProgramProfile.weightStepOptions.forEach { option ->
            DropdownMenuItem(
                text = {
                    Text(
                        com.texasprogram.app.model.ProgramProfile.weightStepTitle(option),
                        color = Theme.textPrimary,
                        fontWeight = if (option == current) FontWeight.Bold else FontWeight.Normal
                    )
                },
                trailingIcon = {
                    if (option == current) Icon(Icons.Filled.Check, null, tint = Theme.accent, modifier = Modifier.size(16.dp))
                },
                onClick = { onSelect(option) }
            )
        }
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
