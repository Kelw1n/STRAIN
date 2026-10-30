package com.texasprogram.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.texasprogram.app.model.ChallengeScoring
import com.texasprogram.app.model.PersonalRecords
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.SocialRecord
import com.texasprogram.app.model.formatWeight
import java.time.format.DateTimeFormatter
import java.util.Locale

// MARK: - Рекорды в профиле

/// Витрина в профиле: присед, жим и становая по расчётному максимуму и сумма
/// троеборья. Нажатие — все рекорды; в своём профиле оттуда видна история движения.
@Composable
fun ProfileRecordsCard(records: List<SocialRecord>, isMe: Boolean, bodyWeight: Double?, onOpen: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .pressable(enabled = records.isNotEmpty(), onClick = onOpen)
            .clip(RoundedCornerShape(22.dp))
            .background(Theme.tone(0xFF161617))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("РЕКОРДЫ", color = Theme.ink.copy(alpha = 0.5f), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, modifier = Modifier.weight(1f))
            if (records.isNotEmpty()) {
                Text("Все ${records.size}", color = Theme.ink.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Theme.ink.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
            }
        }
        if (records.isEmpty()) {
            Text(
                if (isMe) "Рекорды появятся после первой тренировки с весами" else "Рекордов пока нет",
                color = Theme.ink.copy(alpha = 0.5f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PersonalRecords.Lift.entries.forEach { lift ->
                    RecordTile(lift, PersonalRecords.best(records, lift), Modifier.weight(1f))
                }
            }
            PersonalRecords.total(records)?.let { RecordTotalLine(it, bodyWeight) }
        }
    }
}

@Composable
private fun RecordTile(lift: PersonalRecords.Lift, record: SocialRecord?, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Theme.tone(0xFF1F1F1F))
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(lift.title, color = Theme.ink.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                record?.let { formatWeight(it.estimate) } ?: "—",
                color = if (record == null) Theme.ink.copy(alpha = 0.3f) else Theme.ink,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
            if (record != null) {
                Text("кг", color = Theme.ink.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
        Text(
            record?.setText ?: "нет подходов",
            color = if (record == null) Theme.ink.copy(alpha = 0.3f) else Theme.accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/// «Сумма ≈ 478 кг · ×5,6 веса тела».
@Composable
private fun RecordTotalLine(total: Double, bodyWeight: Double?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Filled.Functions, contentDescription = null, tint = Theme.accent, modifier = Modifier.size(16.dp))
        Text("Сумма троеборья", color = Theme.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text("≈ ${formatWeight(total)} кг", color = Theme.ink, fontSize = 15.sp, fontWeight = FontWeight.Black)
        if (bodyWeight != null && bodyWeight > 0) {
            Text("· ×${ChallengeScoring.decimal(total / bodyWeight)}", color = Theme.ink.copy(alpha = 0.5f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// MARK: - Все рекорды

private val recordDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("ru"))

/// Все рекорды человека. В своём профиле строка открывает историю движения.
@Composable
fun RecordsSheet(username: String, records: List<SocialRecord>, bodyWeight: Double?, profile: ProgramProfile?, onDismiss: () -> Unit) {
    var historyFor by remember { mutableStateOf<String?>(null) }
    NutritionSheet(
        title = if (profile == null) "Рекорды @$username" else "Рекорды",
        onDismiss = onDismiss,
        leading = null,
        action = "Готово",
        onAction = { close -> close() }
    ) {
        PersonalRecords.total(records)?.let { total ->
            NutritionCard(spacing = 0.dp, padding = 16.dp) { RecordTotalLine(total, bodyWeight) }
        }
        NutritionCard(spacing = 0.dp, padding = 0.dp) {
            records.forEachIndexed { index, record ->
                RecordRow(record, opens = profile != null) { historyFor = record.name }
                if (index < records.size - 1) HorizontalDivider(color = Theme.ink.copy(alpha = 0.06f), modifier = Modifier.padding(start = 16.dp))
            }
        }
        Text(
            "Число справа — расчётный максимум на один раз по лучшему подходу (формула Эпли). Сумма троеборья — по приседу, жиму лёжа и становой.",
            color = Theme.ink.copy(alpha = 0.4f),
            fontSize = 12.sp
        )
    }
    val name = historyFor
    if (profile != null && name != null) {
        Dialog(
            onDismissRequest = { historyFor = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            val insets = WindowInsets.systemBars.asPaddingValues()
            Box(Modifier.fillMaxSize().background(Theme.base)) {
                ExerciseHistoryScreen(
                    profile = profile,
                    name = name,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = insets.calculateTopPadding() + 16.dp,
                        bottom = insets.calculateBottomPadding() + 32.dp
                    )
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 12.dp, end = 16.dp)
                        .size(40.dp)
                        .pressable { historyFor = null }
                        .clip(CircleShape)
                        .background(Theme.surfaceSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = Theme.ink, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun RecordRow(record: SocialRecord, opens: Boolean, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(enabled = opens, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExerciseThumb(record.name, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(record.name, color = Theme.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, modifier = Modifier.weight(1f, fill = false))
                if (record.lift != null) {
                    Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = Theme.accent, modifier = Modifier.size(12.dp))
                }
            }
            val detail = if (record.date > 0) "${record.setText} · ${PersonalRecords.localDate(record).format(recordDate)}" else record.setText
            Text(detail, color = Theme.ink.copy(alpha = 0.5f), fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text("≈ ${formatWeight(record.estimate)}", color = Theme.accent, fontSize = 16.sp, fontWeight = FontWeight.Black)
        if (opens) {
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Theme.ink.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
        }
    }
}
