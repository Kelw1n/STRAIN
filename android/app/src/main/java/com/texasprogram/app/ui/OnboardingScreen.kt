package com.texasprogram.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.AdditionalExerciseCategory
import com.texasprogram.app.model.ProgramInput
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.TrainingLevel
import com.texasprogram.app.model.TrainingProgramKind
import com.texasprogram.app.model.UpperLowerInput

@Composable
fun OnboardingScreen(
    canCancel: Boolean,
    onCancel: () -> Unit,
    onSave: (ProgramProfile) -> Unit,
    contentPadding: PaddingValues
) {
    var selected by remember { mutableStateOf<TrainingProgramKind?>(null) }

    AnimatedContent(
        targetState = selected,
        transitionSpec = {
            if (targetState == null) {
                (slideInHorizontally(tween(320)) { -it / 3 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(320)) { it / 3 } + fadeOut(tween(180)))
            } else {
                (slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(320)) { -it / 3 } + fadeOut(tween(180)))
            }
        },
        label = "onboarding"
    ) { kind ->
        when (kind) {
            null -> ProgramSelection(canCancel, onCancel, contentPadding) { selected = it }
            TrainingProgramKind.TEXAS -> TexasSetup(contentPadding, onBack = { selected = null }) {
                onSave(ProgramProfile.texas(it))
            }
            TrainingProgramKind.UPPER_LOWER -> UpperLowerSetup(contentPadding, onBack = { selected = null }) {
                onSave(ProgramProfile.upperLower(it))
            }
        }
    }
}

@Composable
private fun ProgramSelection(
    canCancel: Boolean,
    onCancel: () -> Unit,
    contentPadding: PaddingValues,
    onPick: (TrainingProgramKind) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        if (canCancel) {
            item(key = "bar") { TopBar("Новый профиль", onBack = onCancel) }
        }

        item(key = "hero") {
            Column(Modifier.appearIn(0), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier
                        .size(78.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Theme.accentGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.FitnessCenter, contentDescription = null, tint = Color.White, modifier = Modifier.size(38.dp))
                }
                Text("Выбери программу", color = Theme.textPrimary, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text(
                    "После выбора введи свои максимумы. Вернуться сюда можно полным сбросом в настройках.",
                    color = Theme.textSecondary,
                    fontSize = 14.sp
                )
            }
        }

        TrainingProgramKind.entries.forEachIndexed { index, kind ->
            item(key = kind.name) {
                ProgramOptionCard(kind, Modifier.appearIn(index + 1)) { onPick(kind) }
            }
        }
    }
}

@Composable
private fun ProgramOptionCard(kind: TrainingProgramKind, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val gradient = if (kind == TrainingProgramKind.TEXAS) Theme.accentGradient else Theme.recordGradient
    val icon = if (kind == TrainingProgramKind.TEXAS) Icons.Filled.ShowChart else Icons.Filled.ViewAgenda

    CardView(modifier.pressable(onClick = onClick), padding = 20.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(gradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(kind.title, color = Theme.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(kind.subtitle, color = Theme.textSecondary, fontSize = 14.sp)
                if (kind == TrainingProgramKind.UPPER_LOWER) {
                    TagBadge("Раздел «Жим 14»", icon = Icons.Filled.MonitorHeart, gradient = Theme.recordGradient)
                }
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Theme.textSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun TexasSetup(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onSave: (ProgramInput) -> Unit
) {
    var squat by remember { mutableStateOf("100") }
    var bench by remember { mutableStateOf("100") }
    var deadlift by remember { mutableStateOf("100") }
    var level by remember { mutableStateOf(TrainingLevel.BEGINNER) }
    var showAllOptions by remember { mutableStateOf(false) }
    val selection = remember { mutableStateOf(mapOf<AdditionalExerciseCategory, String?>()) }

    val valid = listOf(squat, bench, deadlift).all { parseWeight(it) > 0 }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item(key = "bar") { TopBar("Техасский метод", onBack = onBack) }
        item(key = "head") {
            ScreenHeader(
                "12 недель · 3 дня",
                "Техасская программа",
                "Введи реальные протестированные 5ПМ — приложение рассчитает 12 недель автоматически.",
                Modifier.appearIn(0)
            )
        }
        item(key = "maxes") {
            CardView(Modifier.appearIn(1)) {
                Text("Твои 5ПМ", color = Theme.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                WeightStepper("Приседания", squat) { squat = it }
                WeightStepper("Жим лёжа", bench) { bench = it }
                WeightStepper("Становая тяга", deadlift) { deadlift = it }
                Text("Шаг округления: 2,5 кг", color = Theme.textTertiary, fontSize = 11.sp)
            }
        }
        item(key = "level") {
            CardView(Modifier.appearIn(2)) {
                Text("Уровень пикирования", color = Theme.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                SegmentedControl(
                    options = TrainingLevel.entries.map { it.title },
                    selectedIndex = TrainingLevel.entries.indexOf(level)
                ) { level = TrainingLevel.entries[it] }
                Text("Для первого прохождения рекомендуется «Начальный».", color = Theme.textTertiary, fontSize = 11.sp)
            }
        }
        item(key = "extra") {
            CardView(Modifier.appearIn(3)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // weight без распорки: иначе заголовок получал половину ширины
                    // и «Дополнительные» ломалось посреди слова.
                    Text(
                        "Дополнительные упражнения",
                        color = Theme.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (showAllOptions) "Скрыть" else "Выбрать",
                        color = Theme.accent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.pressable { showAllOptions = !showAllOptions }
                    )
                }
                AnimatedVisibility(
                    visible = showAllOptions,
                    enter = fadeIn(tween(200)) + expandVertically(tween(260)),
                    exit = fadeOut(tween(150)) + shrinkVertically(tween(240))
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AdditionalExerciseCategory.entries.forEach { category ->
                            ExercisePicker(category, selection.value[category]) { value ->
                                selection.value = selection.value.toMutableMap().apply { put(category, value) }
                            }
                        }
                    }
                }
                if (!showAllOptions) {
                    Text(
                        "Необязательные упражнения можно добавить сейчас или позже в настройках.",
                        color = Theme.textSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }
        item(key = "cta") {
            GradientButton("Построить программу", Modifier.appearIn(4), icon = Icons.Filled.AutoAwesome, enabled = valid) {
                onSave(
                    ProgramInput(
                        squat5RM = parseWeight(squat),
                        bench5RM = parseWeight(bench),
                        deadlift5RM = parseWeight(deadlift),
                        level = level,
                        pull = selection.value[AdditionalExerciseCategory.PULL],
                        arms = selection.value[AdditionalExerciseCategory.ARMS],
                        core = selection.value[AdditionalExerciseCategory.CORE],
                        back = selection.value[AdditionalExerciseCategory.BACK],
                        press = selection.value[AdditionalExerciseCategory.PRESS]
                    )
                )
            }
        }
    }
}

@Composable
private fun UpperLowerSetup(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onSave: (UpperLowerInput) -> Unit
) {
    var squat by remember { mutableStateOf("100") }
    var bench by remember { mutableStateOf("100") }
    var deadlift by remember { mutableStateOf("130") }
    val valid = listOf(squat, bench, deadlift).all { parseWeight(it) > 0 }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item(key = "bar") { TopBar("Верх / Низ", onBack = onBack) }
        item(key = "head") {
            ScreenHeader(
                "7 недель · 4 дня",
                "Верх / Низ",
                "Введи протестированные 1ПМ — базовые веса и волна из 14 жимовых тренировок рассчитаются автоматически.",
                Modifier.appearIn(0)
            )
        }
        item(key = "maxes") {
            CardView(Modifier.appearIn(1)) {
                Text("Твои 1ПМ", color = Theme.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                WeightStepper("Приседания", squat) { squat = it }
                WeightStepper("Жим лёжа", bench) { bench = it }
                WeightStepper("Становая тяга", deadlift) { deadlift = it }
                Text("Рабочие веса округляются к 5 кг, как в исходной таблице.", color = Theme.textTertiary, fontSize = 11.sp)
            }
        }
        item(key = "info") {
            CardView(Modifier.appearIn(2)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(Theme.recordGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.MonitorHeart, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Жим 14", color = Theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("Отдельный раздел с волной жима и своим прогрессом.", color = Theme.textSecondary, fontSize = 11.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = Theme.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("7 недель · 28 тренировок", color = Theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "Понедельник — тяжёлый верх, вторник — тяжёлый низ, четверг — объёмный верх, пятница — объёмный низ. Дни недели можно поменять в настройках.",
                    color = Theme.textTertiary,
                    fontSize = 11.sp
                )
            }
        }
        item(key = "cta") {
            GradientButton("Построить программу", Modifier.appearIn(3), icon = Icons.Filled.AutoAwesome, enabled = valid) {
                onSave(UpperLowerInput(parseWeight(squat), parseWeight(bench), parseWeight(deadlift)))
            }
        }
    }
}

private fun parseWeight(text: String): Double =
    text.replace(",", ".").toDoubleOrNull() ?: 0.0
