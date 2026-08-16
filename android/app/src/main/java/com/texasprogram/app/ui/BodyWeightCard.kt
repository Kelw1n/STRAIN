package com.texasprogram.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.formatWeight

/// Вес тела и относительная сила.
///
/// Нужно ради второго: на сушке штанга стоит на месте, и по одному графику весов
/// кажется, что прогресса нет. Отношение максимума к своему весу в этот момент
/// как раз растёт.
@Composable
fun BodyWeightCard(
    profile: ProgramProfile,
    modifier: Modifier = Modifier,
    onUpdate: (ProgramProfile) -> Unit
) {
    var input by remember { mutableStateOf("") }
    val series = profile.bodyWeightSeries
    // Запятая и точка на клавиатуре разные, а значат одно и то же.
    val parsed = input.replace(',', '.').trim().toDoubleOrNull()?.takeIf { it > 20 && it < 400 }

    CardView(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Вес тела", color = Theme.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            profile.latestBodyWeight?.let {
                Text("${formatWeight(it)} кг", color = Theme.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Сегодня, кг") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Theme.textPrimary,
                    unfocusedTextColor = Theme.textPrimary,
                    focusedBorderColor = Theme.accent,
                    unfocusedBorderColor = Theme.hairline,
                    focusedLabelColor = Theme.accent,
                    unfocusedLabelColor = Theme.textTertiary,
                    cursorColor = Theme.accent
                )
            )
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (parsed == null) SolidColor(Theme.surfaceSoft) else Theme.accentGradient)
                    .pressable(enabled = parsed != null) {
                        parsed?.let { onUpdate(profile.recordBodyWeight(it)) }
                        input = ""
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(
                    "Записать",
                    color = if (parsed == null) Theme.textTertiary else Theme.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (series.size > 1) {
            val minWeight = series.minOf { it.weight }
            val maxWeight = series.maxOf { it.weight }
            val minDay = series.first().epochDay
            val maxDay = series.last().epochDay

            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                fun x(day: Long): Float =
                    if (maxDay == minDay) size.width / 2
                    else ((day - minDay).toFloat() / (maxDay - minDay).toFloat()) * size.width

                fun y(weight: Double): Float {
                    val span = (maxWeight - minWeight).takeIf { it > 0.5 } ?: 1.0
                    return size.height - (((weight - minWeight) / span).toFloat()) * (size.height - 14f) - 7f
                }

                for (i in 0..2) {
                    val gy = size.height * i / 2f
                    drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
                }

                val path = Path()
                series.forEachIndexed { index, entry ->
                    val px = x(entry.epochDay)
                    val py = y(entry.weight)
                    if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(path, Theme.accent, style = Stroke(width = 5f))
                series.forEach { drawCircle(Theme.accent, radius = 5f, center = Offset(x(it.epochDay), y(it.weight))) }
            }

            val delta = series.last().weight - series.first().weight
            Text(
                (if (delta > 0) "+" else "") + "${formatWeight(delta)} кг с первого замера",
                color = Theme.textSecondary,
                fontSize = 12.sp
            )
        }

        if (profile.latestBodyWeight != null) {
            Text(
                "К СВОЕМУ ВЕСУ",
                color = Theme.textTertiary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Ratio("Присед", profile.relativeStrength(profile.squat5RM), Modifier.weight(1f))
                Ratio("Жим", profile.relativeStrength(profile.bench5RM), Modifier.weight(1f))
                Ratio("Тяга", profile.relativeStrength(profile.deadlift5RM), Modifier.weight(1f))
            }
            Text(
                "Отношение ${profile.maximumLabel} к весу тела. Держится при похудении — сила осталась, лишнее ушло.",
                color = Theme.textTertiary,
                fontSize = 11.sp
            )
        } else {
            Text(
                "Запиши вес — рядом появится отношение максимумов к нему.",
                color = Theme.textSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun Ratio(title: String, value: Double?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            value?.let { String.format(java.util.Locale.ROOT, "%.2f", it) } ?: "—",
            color = Theme.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Text(title, color = Theme.textSecondary, fontSize = 11.sp)
    }
}
