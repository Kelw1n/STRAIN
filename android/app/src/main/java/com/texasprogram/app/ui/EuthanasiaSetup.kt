package com.texasprogram.app.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.texasprogram.app.service.EuthanasiaInput
import com.texasprogram.app.service.EuthanasiaLevel
import com.texasprogram.app.service.EuthanasiaMovement
import com.texasprogram.app.service.EuthanasiaPattern

/// Настройка «Эвтаназии худобы»: три движения, два теста на каждое и уровень.
///
/// У этой программы вход не такой, как у остальных: она считается не от общих
/// максимумов, а от пары «один повторный максимум + время на КПШ» по каждому
/// движению отдельно.
@Composable
fun EuthanasiaSetup(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onSave: (EuthanasiaInput) -> Unit
) {
    var level by remember { mutableStateOf(EuthanasiaLevel.MEDIUM) }
    val chosen = remember { mutableStateMapOf<EuthanasiaPattern, String>() }
    val maxima = remember { mutableStateMapOf<EuthanasiaPattern, String>() }
    val minutes = remember { mutableStateMapOf<EuthanasiaPattern, String>() }

    fun number(text: String?) = text?.replace(',', '.')?.trim()?.toDoubleOrNull()?.takeIf { it > 0 }

    fun movement(pattern: EuthanasiaPattern): EuthanasiaMovement? {
        val max = number(maxima[pattern]) ?: return null
        val time = number(minutes[pattern]) ?: return null
        return EuthanasiaMovement(chosen[pattern] ?: pattern.options[0], max, time)
    }

    val isReady = EuthanasiaPattern.entries.all { movement(it) != null }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "title") {
            ScreenTitle("Эвтаназия худобы") {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Theme.surfaceSoft)
                        .pressable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Назад", tint = Theme.textPrimary, modifier = Modifier.size(22.dp))
                }
            }
        }

        item(key = "level") {
            CardView(Modifier.appearIn(0)) {
                SectionLabel("Уровень сложности")
                SegmentedControl(
                    options = EuthanasiaLevel.entries.map { it.title },
                    selectedIndex = EuthanasiaLevel.entries.indexOf(level)
                ) { level = EuthanasiaLevel.entries[it] }
                Text(
                    level.subtitle + ". Отличается только шагом уплотнения: веса и повторы одинаковые.",
                    color = Theme.textTertiary,
                    fontSize = 11.sp
                )
            }
        }

        EuthanasiaPattern.entries.forEachIndexed { index, pattern ->
            item(key = "pattern-${pattern.name}") {
                CardView(Modifier.appearIn(index + 1)) {
                    SectionLabel(pattern.title)
                    pattern.options.forEach { option ->
                        val selected = (chosen[pattern] ?: pattern.options[0]) == option
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .pressable { chosen[pattern] = option }
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(if (selected) Theme.accent else Theme.surfaceSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(option, color = Theme.textPrimary, fontSize = 13.sp)
                        }
                    }
                    DecimalField(
                        value = maxima[pattern] ?: "",
                        label = "Тест 1ПМ, кг",
                        onValueChange = { maxima[pattern] = it }
                    )
                    DecimalField(
                        value = minutes[pattern] ?: "",
                        label = "Тест ${pattern.totalReps} КПШ, минут",
                        onValueChange = { minutes[pattern] = it }
                    )
                    Text(
                        "Сначала максимум на один раз, потом — за сколько минут пройдёшь ${pattern.totalReps} повторений с 75 % от него, не подходя к отказу. Ориентир: ${pattern.testHint}.",
                        color = Theme.textTertiary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        item(key = "start") {
            CardView(Modifier.appearIn(4)) {
                GradientButton("Начать программу", enabled = isReady) {
                    val pull = movement(EuthanasiaPattern.PULL) ?: return@GradientButton
                    val squat = movement(EuthanasiaPattern.SQUAT) ?: return@GradientButton
                    val press = movement(EuthanasiaPattern.PRESS) ?: return@GradientButton
                    onSave(EuthanasiaInput(pull, squat, press, level))
                }
                Text(
                    "Первая неделя цикла — тестовая: приложение поставит оба теста в план, чтобы числа можно было уточнить прямо на тренировке.",
                    color = Theme.textTertiary,
                    fontSize = 11.sp
                )
            }
        }
    }
}
