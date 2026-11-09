package com.texasprogram.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.texasprogram.app.model.Supplement
import com.texasprogram.app.model.SupplementData
import com.texasprogram.app.model.SupplementIntake
import com.texasprogram.app.model.SupplementPreset
import com.texasprogram.app.model.SupplementReminder
import com.texasprogram.app.model.SupplementSlot
import com.texasprogram.app.model.SupplementTracker
import com.texasprogram.app.service.RuDate
import com.texasprogram.app.service.RuPlural
import java.time.LocalDate
import java.util.Locale

// MARK: - Карточка «Добавки»

/// Добавки на выбранный день: нажал — выпил, ещё раз — снял отметку. Сверху
/// серия дней, когда выпито всё, снизу последние семь дней. Как на iPhone —
/// `SupplementViews.swift`.
@Composable
fun SupplementsCard(
    day: LocalDate,
    today: LocalDate,
    data: SupplementData,
    trainedDays: Set<LocalDate>,
    actions: NutritionActions
) {
    val haptics = rememberHapticEngine()
    var editing by remember { mutableStateOf<Supplement?>(null) }
    var adding by remember { mutableStateOf(false) }
    val state = remember(data, day, trainedDays) { SupplementTracker.state(day, data, trainedDays) }
    val streak = remember(data, trainedDays, today) { SupplementTracker.streak(data, trainedDays, today) }

    NutritionCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                NutritionCaption("Добавки")
                if (state.required > 0) {
                    Text(
                        if (state.isComplete) "Всё выпито" else "Выпито ${state.taken} из ${state.required}",
                        color = if (state.isComplete) Theme.success else Theme.ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (streak > 0) {
                val days = RuPlural.word(streak, "день", "дня", "дней")
                Row(
                    Modifier
                        .height(30.dp)
                        .clip(CircleShape)
                        .background(NutritionColors.chip)
                        .padding(horizontal = 10.dp)
                        .semantics { contentDescription = "Серия $streak $days подряд" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = Theme.accent, modifier = Modifier.size(14.dp))
                    Text("$streak $days", color = Theme.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            Box(
                Modifier
                    .size(30.dp)
                    .pressable { adding = true }
                    .clip(CircleShape)
                    .background(NutritionColors.chip)
                    .semantics { contentDescription = "Добавить добавку" },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Theme.ink, modifier = Modifier.size(17.dp))
            }
        }

        if (data.items.isEmpty()) {
            Text(
                "Креатин, омега-3, витамины — отмечай в одно касание, а напоминание не даст забыть.",
                color = Theme.ink.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
            NutritionButton("Добавить добавку", icon = Icons.Filled.Add) { adding = true }
        } else {
            val items = remember(data.items) { SupplementTracker.ordered(data.items) }
            Column {
                items.forEachIndexed { index, item ->
                    androidx.compose.runtime.key(item.id) {
                        SupplementRow(
                            item = item,
                            intake = SupplementTracker.intake(item, day, data),
                            today = today,
                            onToggle = { taken ->
                                if (taken) haptics.selection() else haptics.medium()
                                actions.toggleSupplement(item, day)
                            },
                            onEdit = { editing = item },
                            onDelete = {
                                haptics.medium()
                                actions.deleteSupplement(item)
                            }
                        )
                    }
                    if (index < items.size - 1) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Theme.ink.copy(alpha = 0.06f)))
                    }
                }
            }
            SupplementWeek(data, trainedDays, day, today)
        }
    }

    editing?.let { item ->
        SupplementSheet(
            original = item,
            onSave = { actions.saveSupplement(it) },
            onDelete = { actions.deleteSupplement(item) },
            onDismiss = { editing = null }
        )
    }
    if (adding) {
        SupplementSheet(original = null, onSave = { actions.saveSupplement(it) }, onDismiss = { adding = false })
    }
}

/// Нажатие — выпил или снять отметку, долгое нажатие — изменить и удалить.
@Composable
private fun SupplementRow(
    item: Supplement,
    intake: SupplementIntake?,
    today: LocalDate,
    onToggle: (wasTaken: Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val taken = intake != null
    var menu by remember { mutableStateOf(false) }
    val fill by animateColorAsState(if (taken) Theme.accent else Color.Transparent, Motion.snappy(), label = "supplement")
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedPressable(onClick = { onToggle(taken) }, onLongClick = { menu = true })
                .padding(vertical = 10.dp)
                .semantics { contentDescription = "${item.name}, ${if (taken) "выпито" else "не выпито"}" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(fill)
                    .border(2.dp, Theme.ink.copy(alpha = if (taken) 0f else 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (taken) Icon(Icons.Filled.Check, contentDescription = null, tint = Theme.canvas, modifier = Modifier.size(17.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.name, color = Theme.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(supplementDetail(item, intake, today), color = Theme.ink.copy(alpha = 0.45f), fontSize = 12.sp, maxLines = 1)
            }
            item.reminder?.let { reminder ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(Icons.Filled.Notifications, contentDescription = null, tint = Theme.ink.copy(alpha = 0.4f), modifier = Modifier.size(12.dp))
                    Text(reminder.text, color = Theme.ink.copy(alpha = 0.4f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Изменить") },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = {
                    menu = false
                    onEdit()
                }
            )
            DropdownMenuItem(
                text = { Text("Удалить", color = Theme.record) },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = Theme.record) },
                onClick = {
                    menu = false
                    onDelete()
                }
            )
        }
    }
}

private fun supplementDetail(item: Supplement, intake: SupplementIntake?, today: LocalDate): String {
    val parts = mutableListOf<String>()
    item.dose.trim().takeIf { it.isNotEmpty() }?.let { parts += it }
    if (intake != null && intake.date.atZone(java.time.ZoneId.systemDefault()).toLocalDate() == today) {
        parts += "в ${NutritionFormat.time(intake.date)}"
    } else {
        parts += item.slot.title.lowercase()
    }
    return parts.joinToString(" · ")
}

/// Последние семь дней: полный кружок — выпито всё, дуга — часть.
@Composable
private fun SupplementWeek(data: SupplementData, trainedDays: Set<LocalDate>, selected: LocalDate, today: LocalDate) {
    val days = remember(data, trainedDays, today) { SupplementTracker.recent(7, data, trainedDays, today) }
    val accent = Theme.accent
    val empty = NutritionColors.dot
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .semantics { contentDescription = "За неделю всё выпито в ${days.count { it.isComplete }} из 7 дней" }
    ) {
        days.forEach { state ->
            val isSelected = state.day == selected
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    RuDate.short(RuDate.weekdayOf(state.day)),
                    color = Theme.ink.copy(alpha = if (isSelected) 0.9f else 0.4f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    Modifier
                        .size(32.dp)
                        .border(1.5.dp, Theme.ink.copy(alpha = if (isSelected) 0.8f else 0f), CircleShape)
                        .padding(3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.size(26.dp)) {
                        drawCircle(if (state.isComplete) accent else empty)
                        if (!state.isComplete && state.share > 0) {
                            val stroke = 3.dp.toPx()
                            drawArc(
                                color = accent,
                                startAngle = -90f,
                                sweepAngle = (360 * state.share).toFloat(),
                                useCenter = false,
                                topLeft = Offset(stroke / 2, stroke / 2),
                                size = Size(size.width - stroke, size.height - stroke),
                                style = Stroke(width = stroke, cap = StrokeCap.Round)
                            )
                        }
                    }
                    if (state.isComplete) Icon(Icons.Filled.Check, contentDescription = null, tint = Theme.canvas, modifier = Modifier.size(13.dp))
                }
            }
        }
    }
}

// MARK: - Добавить или изменить

private val SupplementSlot.icon: ImageVector
    get() = when (this) {
        SupplementSlot.MORNING -> Icons.Filled.WbTwilight
        SupplementSlot.DAY -> Icons.Filled.WbSunny
        SupplementSlot.EVENING -> Icons.Filled.Bedtime
        SupplementSlot.WORKOUT -> Icons.Filled.FitnessCenter
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SupplementSheet(
    original: Supplement?,
    onSave: (Supplement) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptics = rememberHapticEngine()
    var name by remember { mutableStateOf(original?.name.orEmpty()) }
    var dose by remember { mutableStateOf(original?.dose.orEmpty()) }
    var slot by remember { mutableStateOf(original?.slot ?: SupplementSlot.MORNING) }
    var remind by remember { mutableStateOf(original?.reminder != null) }
    var hour by remember { mutableIntStateOf(original?.reminder?.hour ?: 9) }
    var minute by remember { mutableIntStateOf(original?.reminder?.minute ?: 0) }
    val trimmed = name.trim()
    // Напоминание без разрешения на уведомления пришло бы молча — просим его,
    // как только напоминание включили.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    NutritionSheet(
        title = if (original == null) "Новая добавка" else "Добавка",
        onDismiss = onDismiss,
        action = "Сохранить",
        actionEnabled = trimmed.isNotEmpty(),
        onAction = { close ->
            if (trimmed.isNotEmpty()) {
                haptics.success()
                val base = original ?: Supplement(name = trimmed)
                onSave(
                    base.copy(
                        name = trimmed,
                        dose = dose.trim(),
                        slot = slot,
                        reminder = if (remind) SupplementReminder(hour, minute) else null
                    )
                )
                close()
            }
        }
    ) { close ->
        if (original == null) {
            NutritionCard(spacing = 10.dp) {
                NutritionCaption("Частые")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SupplementPreset.ALL.forEach { preset ->
                        val selected = trimmed == preset.name
                        Box(
                            Modifier
                                .height(36.dp)
                                .pressable {
                                    haptics.selection()
                                    name = preset.name
                                    dose = preset.dose
                                    slot = preset.slot
                                }
                                .clip(CircleShape)
                                .background(if (selected) Theme.accent else NutritionColors.chip)
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(preset.name, color = if (selected) Theme.canvas else Theme.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        NutritionCard(spacing = 10.dp) {
            NutritionCaption("Название")
            NutritionTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                placeholder = "Например, креатин",
                keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next)
            )
            NutritionCaption("Доза", Modifier.padding(top = 4.dp))
            NutritionTextField(
                value = dose,
                onValueChange = { dose = it.take(40) },
                placeholder = "5 г, 2 капсулы",
                keyboard = KeyboardOptions(imeAction = ImeAction.Done)
            )
        }
        NutritionCard(spacing = 10.dp) {
            NutritionCaption("Когда")
            SupplementSlot.entries.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { option ->
                        val selected = slot == option
                        Row(
                            Modifier
                                .weight(1f)
                                .height(46.dp)
                                .pressable {
                                    haptics.selection()
                                    slot = option
                                }
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) Theme.accent else NutritionColors.field)
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val tint = if (selected) Theme.canvas else Theme.ink
                            Icon(option.icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                            Text(option.title, color = tint, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
            if (slot == SupplementSlot.WORKOUT) {
                Text("В дни без тренировки её не ждём — серия не рвётся.", color = Theme.ink.copy(alpha = 0.4f), fontSize = 12.sp)
            }
        }
        NutritionCard(spacing = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Напоминание", color = Theme.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Каждый день, пока не отметишь", color = Theme.ink.copy(alpha = 0.45f), fontSize = 12.sp)
                }
                Switch(
                    checked = remind,
                    onCheckedChange = { on ->
                        remind = on
                        if (on && Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            runCatching { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
                        }
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = Theme.accent, checkedThumbColor = Color.White)
                )
            }
            AnimatedVisibility(visible = remind) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TimeStep("−", "Раньше на час") { hour = (hour + 23) % 24 }
                    TimeValue(hour)
                    TimeStep("+", "Позже на час") { hour = (hour + 1) % 24 }
                    Text(":", color = Theme.ink, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp))
                    TimeStep("−", "Раньше на 5 минут") { minute = (minute + 55) % 60 }
                    TimeValue(minute)
                    TimeStep("+", "Позже на 5 минут") { minute = (minute + 5) % 60 }
                }
            }
        }
        if (onDelete != null) {
            Text(
                "Удалить добавку",
                color = Theme.record,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .pressable {
                        onDelete()
                        close()
                    }
                    .padding(vertical = 14.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun TimeValue(value: Int) {
    Text(
        String.format(Locale.US, "%02d", value),
        color = Theme.ink,
        fontSize = 28.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.width(48.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

@Composable
private fun TimeStep(symbol: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .pressable(onClick = onClick)
            .clip(CircleShape)
            .background(NutritionColors.chip)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, color = Theme.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
