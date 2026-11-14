package com.texasprogram.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.texasprogram.app.model.CustomProgram
import com.texasprogram.app.model.CycleFeel
import com.texasprogram.app.model.CycleGrowth
import com.texasprogram.app.model.NextCycleStart
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.TrainingProgramKind
import com.texasprogram.app.model.cycleFeelHint
import com.texasprogram.app.model.cycleRecap
import com.texasprogram.app.model.formatTonnage
import com.texasprogram.app.model.formatWeight
import com.texasprogram.app.model.nextCycleStart
import com.texasprogram.app.model.repeatCycle
import com.texasprogram.app.model.startNewCustomCycle
import com.texasprogram.app.service.RuPlural
import kotlin.math.abs

private val CardColor: androidx.compose.ui.graphics.Color get() = Theme.tone(0xFF161616)
private val SheetBackground: androidx.compose.ui.graphics.Color get() = Theme.tone(0xFF121212)

/// Карточка на месте сегодняшней тренировки, когда все недели программы закрыты.
@Composable
fun ProgramFinishedCard(profile: ProgramProfile, onOpen: () -> Unit) {
    val recap = remember(profile) { profile.cycleRecap }
    Column(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = onOpen)
            .clip(RoundedCornerShape(22.dp))
            .background(CardColor)
            .border(1.dp, Theme.ink.copy(alpha = 0.06f), RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ПРОГРАММА ПРОЙДЕНА", color = Theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Text(
                    "${recap.weeks} ${RuPlural.word(recap.weeks, "неделя", "недели", "недель")} позади",
                    color = Theme.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold
                )
            }
            Icon(Icons.Filled.Flag, contentDescription = null, tint = Theme.accent, modifier = Modifier.size(24.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("${recap.workouts}", RuPlural.word(recap.workouts, "тренировка", "тренировки", "тренировок"), Modifier.weight(1f))
            if (recap.tonnage > 0) Stat(formatTonnage(recap.tonnage), "поднято", Modifier.weight(1f))
            val best = recap.growth.maxByOrNull { it.delta }
            if (best != null && best.delta > 0) Stat("+${formatWeight(best.delta)} кг", best.name.lowercase(), Modifier.weight(1f))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(Theme.ink)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Что дальше", color = Theme.canvas, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Theme.canvas, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun Stat(value: String, caption: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, color = Theme.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(caption, color = Theme.ink.copy(alpha = 0.5f), fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/// Итоги цикла и выбор, как начинать следующий. Тот же экран — CycleFinishView на iOS.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleFinishSheet(profile: ProgramProfile, onUpdate: (ProgramProfile) -> Unit, initialFeel: CycleFeel? = null, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = rememberHapticEngine()
    val recap = remember { profile.cycleRecap }
    val hint = remember { profile.cycleFeelHint }
    var feel by remember { mutableStateOf(initialFeel) }
    var editing by remember { mutableStateOf<CustomProgram?>(null) }
    var manual by remember { mutableStateOf(false) }
    var confirmRepeat by remember { mutableStateOf(false) }
    val next = remember(feel) { feel?.let { profile.nextCycleStart(it) } }
    val isCustom = profile.programKind == TrainingProgramKind.CUSTOM
    val nextNumber = profile.cycleNumber + 1

    fun finish(updated: ProgramProfile) {
        onUpdate(updated)
        onDismiss()
    }

    val body: @Composable () -> Unit = {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("Цикл ${profile.cycleNumber} пройден", color = Theme.ink, fontSize = 26.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 4.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("${recap.workouts}", RuPlural.word(recap.workouts, "тренировка", "тренировки", "тренировок"), Modifier.weight(1f))
                Tile("${recap.weeks}", RuPlural.word(recap.weeks, "неделя", "недели", "недель"), Modifier.weight(1f))
                Tile(if (recap.tonnage > 0) formatTonnage(recap.tonnage) else "—", "поднято", Modifier.weight(1f))
            }

            if (recap.growth.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("Как выросли веса")
                    GrowthList(recap.growth.take(8))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("Как шли веса последние две недели?")
                CycleFeel.values().forEach { option ->
                    val selected = feel == option
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .pressable {
                                haptics.light()
                                feel = option
                            }
                            .clip(RoundedCornerShape(18.dp))
                            .background(CardColor)
                            .border(1.5.dp, if (selected) Theme.accent else Theme.ink.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { feel = option },
                            colors = RadioButtonDefaults.colors(selectedColor = Theme.accent, unselectedColor = Theme.ink.copy(alpha = 0.3f))
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(option.title, color = Theme.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(option.caption, color = Theme.ink.copy(alpha = 0.55f), fontSize = 13.sp)
                        }
                    }
                }
                if (hint != null) {
                    Text(hint.text, color = Theme.ink.copy(alpha = 0.55f), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }

            AnimatedVisibility(visible = next != null) {
                val shown = next
                val chosen = feel
                if (shown != null && chosen != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionTitle(if (isCustom) "Цикл $nextNumber начнётся с" else "Новые максимумы")
                        Text(chosen.outcome, color = Theme.ink.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp))
                        if (shown.changes.isEmpty()) {
                            Text(
                                "В программе нет упражнений с прибавкой веса — стартовые веса останутся прежними.",
                                color = Theme.ink.copy(alpha = 0.55f), fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        } else {
                            GrowthList(shown.changes.take(10))
                        }
                        if (!isCustom) {
                            Text(
                                "Это прикидка от весов, до которых ты дошёл. Если есть возможность — проверь ${profile.maximumLabel} на первой тяжёлой тренировке и поправь числа.",
                                color = Theme.ink.copy(alpha = 0.5f), fontSize = 12.5.sp, modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val ready = next != null
                Box(
                    Modifier
                        .fillMaxWidth()
                        .pressable(enabled = ready) {
                            haptics.medium()
                            when (val start = next) {
                                is NextCycleStart.Custom -> finish(profile.startNewCustomCycle(start.program))
                                is NextCycleStart.Maxes -> finish(profile.startNewCycle(start.squat, start.bench, start.deadlift))
                                null -> Unit
                            }
                        }
                        .clip(CircleShape)
                        .background(Theme.ink.copy(alpha = if (ready) 1f else 0.3f))
                        .padding(vertical = 17.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (ready) "Начать цикл $nextNumber" else "Выбери ответ выше",
                        color = Theme.canvas, fontSize = 16.sp, fontWeight = FontWeight.Black
                    )
                }
                if (feel != null) {
                    Secondary(if (isCustom) "Поправить веса и упражнения" else "Ввести максимумы вручную") {
                        if (isCustom) editing = (next as? NextCycleStart.Custom)?.program ?: profile.customProgram else manual = true
                    }
                } else if (isCustom) {
                    Secondary("Изменить программу") { editing = profile.customProgram }
                }
                Secondary("Повторить с теми же весами") { confirmRepeat = true }
            }
        }
    }
    // На снимках экрана лист рисуется прямо на экране: всплывающее окно туда не попадает.
    if (LocalNutritionSheetInline.current) {
        Box(Modifier.background(SheetBackground).padding(top = 24.dp)) { body() }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = state,
            containerColor = SheetBackground,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        ) { body() }
    }

    val base = editing
    if (base != null) {
        Dialog(onDismissRequest = { editing = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Theme.canvas)) {
                CustomProgramBuilder(
                    contentPadding = WindowInsets.systemBars.asPaddingValues(),
                    onBack = { editing = null },
                    initial = base,
                    title = "Цикл $nextNumber",
                    saveTitle = "Начать цикл $nextNumber"
                ) { program ->
                    editing = null
                    finish(profile.startNewCustomCycle(program))
                }
            }
        }
    }
    if (manual) {
        val maxes = next as? NextCycleStart.Maxes
        NewCycleDialog(
            profile = profile,
            onDismiss = { manual = false },
            onUpdate = { finish(it) },
            suggested = maxes?.let { Triple(it.squat, it.bench, it.deadlift) }
        )
    }
    if (confirmRepeat) {
        AlertDialog(
            modifier = Modifier.popIn(),
            onDismissRequest = { confirmRepeat = false },
            containerColor = Theme.dialog,
            title = { Text("Повторить цикл с теми же весами?", color = Theme.textPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Программа начнётся с первой недели с прежними стартовыми весами. История и графики останутся.", color = Theme.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRepeat = false
                    finish(profile.repeatCycle())
                }) { Text("Повторить") }
            },
            dismissButton = { TextButton(onClick = { confirmRepeat = false }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Theme.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
private fun Tile(value: String, caption: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(CardColor)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(value, color = Theme.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(caption, color = Theme.ink.copy(alpha = 0.5f), fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun GrowthList(items: List<CycleGrowth>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardColor)
            .padding(horizontal = 16.dp)
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(color = Theme.ink.copy(alpha = 0.06f))
            Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(item.name, color = Theme.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.size(8.dp))
                Text("${formatWeight(item.from)} → ${formatWeight(item.to)} кг", color = Theme.ink.copy(alpha = 0.6f), fontSize = 14.sp)
                if (item.delta != 0.0) {
                    Text(
                        (if (item.delta > 0) "+" else "−") + formatWeight(abs(item.delta)),
                        color = if (item.delta > 0) Theme.accent else Theme.ink.copy(alpha = 0.5f),
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.widthIn(min = 48.dp).padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun Secondary(title: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .clip(CircleShape)
            .background(CardColor)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(title, color = Theme.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
