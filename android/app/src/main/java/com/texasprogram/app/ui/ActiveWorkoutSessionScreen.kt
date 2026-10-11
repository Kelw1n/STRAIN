package com.texasprogram.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.ExercisePrescription
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.formatWeight
import com.texasprogram.app.service.RestTimer
import com.texasprogram.app.service.WorkoutSessionManager
import java.util.Locale
import kotlin.math.roundToInt

// MARK: - Свернутая панель активной тренировки (Liquid Glass Mini-Player)

@Composable
fun MinimizedWorkoutBar(
    profile: ProgramProfile,
    modifier: Modifier = Modifier
) {
    val workout = WorkoutSessionManager.activeWorkout ?: return
    if (WorkoutSessionManager.isPresented) return

    val haptic = LocalHapticFeedback.current
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .shadow(16.dp, RoundedCornerShape(26.dp), ambientColor = Color.Black, spotColor = Color.Black)
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xE6141416))
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.06f))
                ),
                RoundedCornerShape(26.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                WorkoutSessionManager.expand()
            }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Пульсирующая зеленая точка
            Box(
                modifier = Modifier.size(22.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(Color(0xFF2ED573).copy(alpha = 0.35f))
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2ED573))
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = workout.fullTitle,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${WorkoutSessionManager.totalCompletedSets} из ${WorkoutSessionManager.totalSetsCount} подходов",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.width(8.dp))

            // Таймер
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Theme.accent.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Timer,
                    contentDescription = null,
                    tint = Theme.accent,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = formatTimer(WorkoutSessionManager.elapsedSeconds),
                    color = Theme.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(Modifier.width(6.dp))

            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = "Развернуть",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// MARK: - Экран активной тренировки

@Composable
fun ActiveWorkoutSessionScreen(
    profile: ProgramProfile,
    restTimer: RestTimer,
    onUpdateProfile: (ProgramProfile) -> Unit
) {
    val workout = WorkoutSessionManager.activeWorkout ?: return
    val haptic = LocalHapticFeedback.current
    var menuExpanded by remember { mutableStateOf(false) }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0D))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Шапка экрана
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topInset + 8.dp, start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Свернуть
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        WorkoutSessionManager.minimize()
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Свернуть",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(Modifier.weight(1f))

                // Секундомер
                Text(
                    text = formatTimer(WorkoutSessionManager.elapsedSeconds),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(Modifier.weight(1f))

                // Меню (3 точки)
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Меню",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(Color(0xFF1C1C1F))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Заметка к тренировке", color = Color.White) },
                            onClick = {
                                menuExpanded = false
                                WorkoutSessionManager.showingNoteSheet = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Отменить тренировку", color = Color(0xFFFF5252)) },
                            onClick = {
                                menuExpanded = false
                                WorkoutSessionManager.showingZeroSetsAlert = true
                            }
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Кнопка Завершить
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable {
                            if (WorkoutSessionManager.totalCompletedSets == 0) {
                                WorkoutSessionManager.showingZeroSetsAlert = true
                            } else {
                                WorkoutSessionManager.showingSummary = true
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Завершить",
                        color = Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Список упражнений
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(WorkoutSessionManager.exercises) { exIndex, exState ->
                    ExerciseSessionCard(
                        exIndex = exIndex,
                        profile = profile,
                        restTimer = restTimer,
                        onUpdateProfile = onUpdateProfile
                    )
                }

                item {
                    Spacer(Modifier.height(48.dp))
                }
            }
        }

        // Всплывающее окно подтверждения удаления при 0 подходов
        if (WorkoutSessionManager.showingZeroSetsAlert) {
            ZeroSetsAlertOverlay(
                profile = profile,
                onUpdateProfile = onUpdateProfile,
                onDismiss = { WorkoutSessionManager.showingZeroSetsAlert = false }
            )
        }

        // Диалог завершения тренировки
        if (WorkoutSessionManager.showingSummary) {
            WorkoutSummaryDialog(
                workoutTitle = workout.fullTitle,
                profile = profile,
                onSaveAndClose = {
                    WorkoutSessionManager.finishWorkout(profile, onUpdateProfile)
                    WorkoutSessionManager.showingSummary = false
                },
                onDismiss = { WorkoutSessionManager.showingSummary = false }
            )
        }

        // Диалог заметки
        if (WorkoutSessionManager.showingNoteSheet) {
            WorkoutNoteDialog(
                initialNote = WorkoutSessionManager.noteText,
                onSave = { newNote ->
                    WorkoutSessionManager.noteText = newNote
                    val updated = profile.setNote(workout.week, workout.day.number, newNote)
                    onUpdateProfile(updated)
                    WorkoutSessionManager.showingNoteSheet = false
                },
                onDismiss = { WorkoutSessionManager.showingNoteSheet = false }
            )
        }
    }
}

// MARK: - Карточка упражнения

@Composable
private fun ExerciseSessionCard(
    exIndex: Int,
    profile: ProgramProfile,
    restTimer: RestTimer,
    onUpdateProfile: (ProgramProfile) -> Unit
) {
    if (exIndex >= WorkoutSessionManager.exercises.size) return
    val exState = WorkoutSessionManager.exercises[exIndex]
    var restMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF141416))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Шапка упражнения
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Иконка упражнения
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Color(0xFF242428)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconForExercise(exState.prescription.name),
                    contentDescription = null,
                    tint = Theme.accent,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Text(
                text = exState.prescription.name,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(Modifier.width(8.dp))

            // Кнопка времени отдыха
            Box {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                        .clickable { restMenuExpanded = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Timer,
                        contentDescription = "Время отдыха",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = formatRestTime(exState.restSeconds),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                DropdownMenu(
                    expanded = restMenuExpanded,
                    onDismissRequest = { restMenuExpanded = false },
                    modifier = Modifier.background(Color(0xFF1C1C1F))
                ) {
                    listOf(60, 90, 120, 150, 180, 240).forEach { sec ->
                        DropdownMenuItem(
                            text = { Text("${formatRestTime(sec)} (${sec / 60} мин)", color = Color.White) },
                            onClick = {
                                WorkoutSessionManager.customRestTargetExerciseIndex = exIndex
                                WorkoutSessionManager.setRestDuration(sec)
                                restMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.width(6.dp))

            // Добавить подход
            IconButton(
                onClick = { WorkoutSessionManager.addSet(exIndex, profile) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Добавить подход",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Таблица подходов
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Заголовки таблицы
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(28.dp)
                )
                Text(
                    text = "БЫЛО",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(66.dp)
                )
                Text(
                    text = "КГ",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "ПОВТ",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(38.dp))
            }

            // Строки подходов
            exState.sets.forEachIndexed { setIndex, _ ->
                SetRow(
                    exIndex = exIndex,
                    setIndex = setIndex,
                    profile = profile,
                    restTimer = restTimer,
                    onUpdateProfile = onUpdateProfile
                )
            }

            // Кнопка «+ Добавить подход»
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF242428))
                    .clickable { WorkoutSessionManager.addSet(exIndex, profile) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Добавить подход",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// MARK: - Строка подхода

@Composable
private fun SetRow(
    exIndex: Int,
    setIndex: Int,
    profile: ProgramProfile,
    restTimer: RestTimer,
    onUpdateProfile: (ProgramProfile) -> Unit
) {
    if (exIndex >= WorkoutSessionManager.exercises.size ||
        setIndex >= WorkoutSessionManager.exercises[exIndex].sets.size
    ) return

    val currentSet = WorkoutSessionManager.exercises[exIndex].sets[setIndex]
    val isCurrent = !currentSet.isDone &&
        WorkoutSessionManager.exercises[exIndex].sets.take(setIndex).all { it.isDone }

    var weightMenuExpanded by remember { mutableStateOf(false) }
    var repsMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Индикатор активного подхода + Номер
        Row(
            modifier = Modifier.width(28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isCurrent) Theme.accent else Color.Transparent)
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = "${setIndex + 1}",
                color = if (isCurrent) Theme.accent else Color.White.copy(alpha = 0.75f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // «БЫЛО»
        Text(
            text = currentSet.previousText,
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.width(66.dp)
        )

        // «КГ» Меню
        Box(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF28282D))
                    .clickable { weightMenuExpanded = true }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = formatWeight(currentSet.weight),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            DropdownMenu(
                expanded = weightMenuExpanded,
                onDismissRequest = { weightMenuExpanded = false },
                modifier = Modifier.background(Color(0xFF1C1C1F))
            ) {
                listOf(5.0, 2.5, 1.0, -1.0, -2.5, -5.0).forEach { delta ->
                    val sign = if (delta > 0) "+" else ""
                    DropdownMenuItem(
                        text = { Text("$sign$delta кг", color = Color.White) },
                        onClick = {
                            WorkoutSessionManager.adjustWeight(exIndex, setIndex, delta)
                            weightMenuExpanded = false
                        }
                    )
                }
            }
        }

        // «ПОВТ» Меню
        Box(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF28282D))
                    .clickable { repsMenuExpanded = true }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${currentSet.reps}",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            DropdownMenu(
                expanded = repsMenuExpanded,
                onDismissRequest = { repsMenuExpanded = false },
                modifier = Modifier.background(Color(0xFF1C1C1F))
            ) {
                listOf(1, 2, -1, -2).forEach { delta ->
                    val sign = if (delta > 0) "+" else ""
                    DropdownMenuItem(
                        text = { Text("$sign$delta повт", color = Color.White) },
                        onClick = {
                            WorkoutSessionManager.adjustReps(exIndex, setIndex, delta)
                            repsMenuExpanded = false
                        }
                    )
                }
            }
        }

        // REBRN Cyan Чекбокс
        Box(
            modifier = Modifier
                .size(38.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    WorkoutSessionManager.toggleSetDone(
                        exIdx = exIndex,
                        sIdx = setIndex,
                        profile = profile,
                        restTimer = restTimer,
                        onUpdateProfile = onUpdateProfile
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (currentSet.isDone) Color(0xFF00E5FF) else Color.Transparent)
                    .border(
                        2.dp,
                        if (currentSet.isDone) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.3f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (currentSet.isDone) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Готово",
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// MARK: - Окно «Ни одного подхода не отмечено» / Удалить тренировку со слайдером

@Composable
private fun ZeroSetsAlertOverlay(
    profile: ProgramProfile,
    onUpdateProfile: (ProgramProfile) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF1E1E22))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(26.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {} // Блокируем клик на подложку
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = null,
                tint = Color(0xFFFF4757),
                modifier = Modifier.size(46.dp)
            )

            Text(
                text = if (WorkoutSessionManager.totalCompletedSets == 0) "Ни одного подхода не отмечено" else "Отменить тренировку?",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Тренировка удалится вместе с введёнными значениями и счётчиком времени.",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            // Слайдер «>>> УДАЛИТЬ»
            SlideToConfirm(
                title = "УДАЛИТЬ",
                onConfirmed = {
                    WorkoutSessionManager.discardWorkout(profile, onUpdateProfile)
                    WorkoutSessionManager.showingZeroSetsAlert = false
                }
            )

            Text(
                text = "Вернуться к тренировке",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable { onDismiss() }
                    .padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun SlideToConfirm(
    title: String,
    onConfirmed: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var dragProgressPx by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(CircleShape)
            .background(Color(0xFFFF4757).copy(alpha = 0.20f))
            .border(1.dp, Color(0xFFFF4757).copy(alpha = 0.40f), CircleShape)
    ) {
        val maxDragPx = (constraints.maxWidth - constraints.maxHeight).toFloat().coerceAtLeast(1f)
        val density = LocalDensity.current

        // Текст слайдера
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = ">>> $title >>>",
                color = Color(0xFFFF4757),
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp
            )
        }

        // Бегунок слайдера
        Box(
            modifier = Modifier
                .offset { IntOffset(dragProgressPx.roundToInt(), 0) }
                .size(54.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF4757))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragProgressPx = (dragProgressPx + dragAmount.x).coerceIn(0f, maxDragPx)
                            if (dragProgressPx >= maxDragPx * 0.95f) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onConfirmed()
                            }
                        },
                        onDragEnd = {
                            dragProgressPx = 0f
                        },
                        onDragCancel = {
                            dragProgressPx = 0f
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

// MARK: - Диалог завершения тренировки

@Composable
private fun WorkoutSummaryDialog(
    workoutTitle: String,
    profile: ProgramProfile,
    onSaveAndClose: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141416),
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🏆",
                    fontSize = 44.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Тренировка завершена!",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = workoutTitle,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryStat(
                    title = "Время",
                    value = formatDuration(WorkoutSessionManager.elapsedSeconds)
                )
                SummaryStat(
                    title = "Объём",
                    value = "${WorkoutSessionManager.totalTonnage.toInt()} кг"
                )
                SummaryStat(
                    title = "Плотность",
                    value = "${WorkoutSessionManager.density.toInt()} кг/м"
                )
            }
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .clickable { onSaveAndClose() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Сохранить и закрыть",
                    color = Color.Black,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

@Composable
private fun SummaryStat(title: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp
        )
    }
}

// MARK: - Диалог заметки

@Composable
private fun WorkoutNoteDialog(
    initialNote: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialNote) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C1F),
        title = {
            Text("Заметка к тренировке", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Как прошла тренировка? Самочувствие, сон...", color = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Theme.accent,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) {
                Text("Сохранить", color = Theme.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = Color.White.copy(alpha = 0.6f))
            }
        }
    )
}

// MARK: - Утилиты

private fun iconForExercise(name: String): ImageVector {
    val lower = name.lowercase()
    return if (lower.contains("присед") || lower.contains("тяг")) {
        Icons.Filled.DirectionsRun
    } else {
        Icons.Filled.FitnessCenter
    }
}

private fun formatTimer(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}

private fun formatRestTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}

private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val mins = (seconds % 3600) / 60
    return if (hours > 0) "${hours}ч ${mins}м" else "$mins мин"
}
