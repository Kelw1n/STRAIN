package com.texasprogram.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassEmpty
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.service.RestTimer

// MARK: - Точки подходов

/// Сколько подходов закрыто и что делать по тапу.
data class SetTracker(
    val done: Int,
    /// Долгий тап по точке: записать, что реально получилось.
    val onHold: ((Int) -> Unit)? = null,
    /// Номера подходов, у которых факт уже записан.
    val logged: Set<Int> = emptySet(),
    val onTap: (Int) -> Unit
)

@Composable
fun SetDotsView(total: Int, tracker: SetTracker, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        for (index in 0 until total) {
            val filled = index < tracker.done
            // Записанный факт помечаем ободком: видно, где цифры есть,
            // а где точка закрыта на глазок.
            val hasEntry = tracker.logged.contains(index)
            Box(
                Modifier
                    .padding(end = 8.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (filled) Theme.accentGradient else SolidColor(Color.White.copy(alpha = 0.10f)))
                    .border(
                        if (hasEntry) 2.dp else 1.dp,
                        when {
                            hasEntry -> Theme.warning
                            filled -> Color.Transparent
                            else -> Theme.accent.copy(alpha = 0.28f)
                        },
                        CircleShape
                    )
                    .combinedPressable(
                        onClick = { tracker.onTap(index) },
                        onLongClick = { tracker.onHold?.invoke(index) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (filled) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "${tracker.done} / $total",
            color = if (tracker.done >= total) Theme.success else Theme.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// MARK: - Блины и разминка

@Composable
fun RestTimerLauncher(
    timer: RestTimer,
    selectedSeconds: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    CardView(modifier, padding = 16.dp, spacing = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.HourglassEmpty, contentDescription = null, tint = Theme.accent, modifier = Modifier.size(16.dp))
            Text("Отдых между подходами", color = Theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RestTimer.presets.forEach { preset ->
                val selected = selectedSeconds.toLong() == preset
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Theme.accent.copy(alpha = if (selected) 0.26f else 0.14f))
                        .border(1.dp, Theme.accent.copy(alpha = if (selected) 0.7f else 0.32f), RoundedCornerShape(12.dp))
                        .pressable {
                            onSelect(preset.toInt())
                            timer.start(preset)
                        }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${preset / 60} мин", color = Theme.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(
            "Отмеченный подход запускает отдых сам — выбранной здесь длительности.",
            color = Theme.textTertiary,
            fontSize = 10.sp
        )
    }
}

/// Плашка обратного отсчёта над нижней панелью.
@Composable
fun RestTimerBar(timer: RestTimer, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(CircleShape)
            .background(Color(0xF01A1F27))
            .border(1.dp, Theme.accent.copy(alpha = 0.35f), CircleShape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RingProgress(timer.progress, Modifier.size(34.dp), lineWidth = 4.dp) {}
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                timer.remainingText,
                color = Theme.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text("отдых", color = Theme.textSecondary, fontSize = 10.sp)
        }
        Spacer(Modifier.weight(1f))
        Text(
            "+1 мин",
            color = Theme.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.pressable { timer.add(60) }
        )
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.07f))
                .pressable { timer.stop() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Стоп", tint = Theme.textSecondary, modifier = Modifier.size(14.dp))
        }
    }
}
