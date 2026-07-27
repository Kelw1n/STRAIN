package com.texasprogram.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terrain
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.TrainingProgramKind

private data class GuideItem(
    val id: String,
    val icon: ImageVector,
    val title: String,
    val text: String
)

@Composable
fun GuideScreen(programKind: TrainingProgramKind, contentPadding: PaddingValues) {
    var opened by remember { mutableStateOf<String?>(null) }

    val items = buildList {
        add(
            GuideItem(
                "start", Icons.Filled.Flag, "Как начать",
                if (programKind == TrainingProgramKind.TEXAS)
                    "Протестируй настоящий пятиповторный максимум в приседаниях, жиме лёжа и становой тяге. Введи именно проверенные значения — программа рассчитает рабочие веса и постепенное увеличение нагрузки."
                else
                    "Протестируй 1ПМ в приседаниях, жиме лёжа и становой тяге. Программа рассчитает базовые веса с округлением к 5 кг. Для остальных упражнений подбирай вес по указанному RPE."
            )
        )
        if (programKind == TrainingProgramKind.TEXAS) {
            add(
                GuideItem(
                    "extra", Icons.Filled.Add, "Дополнительные упражнения",
                    "Дополнительные упражнения необязательны, но выбранное упражнение следует выполнять на протяжении всего цикла. Растянутый суперсет рук означает чередование бицепса и трицепса с полноценным отдыхом после каждого подхода."
                )
            )
        }
        add(
            GuideItem(
                "rest", Icons.Filled.HourglassEmpty, "Отдых",
                "Отдыхай до полного восстановления. Минимум — 3 минуты; при необходимости отдых может занимать 10 минут и больше."
            )
        )
        if (programKind == TrainingProgramKind.TEXAS) {
            add(
                GuideItem(
                    "peak", Icons.Filled.Terrain, "Пикирование",
                    "Пикирование — необязательная часть после основной 12-недельной программы. Используй его только если хочешь проверить 1ПМ или подготовиться к соревнованиям. Перед запуском внеси свежие 5ПМ."
                )
            )
        }
        if (programKind == TrainingProgramKind.UPPER_LOWER) {
            add(
                GuideItem(
                    "bench14", Icons.Filled.MonitorHeart, "Жим 14",
                    "Волновая прогрессия из 14 жимовых тренировок вынесена в отдельный раздел: там весь цикл по подходам, проценты от 1ПМ и своя отметка выполнения. Негативные повторы выполняй только со страхующим и заранее оговорённой подачей штанги."
                )
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "title") { ScreenTitle("Инструкция") }

        items.forEachIndexed { index, item ->
            item(key = item.id) {
                GuideCard(
                    item = item,
                    isOpen = opened == item.id,
                    modifier = Modifier.appearIn(index)
                ) { opened = if (opened == item.id) null else item.id }
            }
        }

        item(key = "rpe") {
            CardView(Modifier.appearIn(items.size)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Speed, contentDescription = null, tint = Theme.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("RPE", color = Theme.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    "RPE — субъективная оценка оставшихся повторов в запасе. Записывай ощущения и регулируй нагрузку, если восстановление отличается от обычного.",
                    color = Theme.textSecondary,
                    fontSize = 14.sp
                )
                RpeRow("RPE 7", "3 повтора в запасе", 0.7f)
                RpeRow("RPE 8", "2 повтора в запасе", 0.8f)
                RpeRow("RPE 9", "1 повтор в запасе", 0.9f)
                RpeRow("RPE 10", "предел", 1f)
            }
        }
    }
}

@Composable
private fun RpeRow(label: String, detail: String, value: Float) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Theme.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(58.dp))
        Box(Modifier.weight(1f)) {
            LineMeter(
                value = value,
                gradient = if (value >= 0.95f) Theme.recordGradient else Theme.accentGradient,
                height = 6.dp
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(detail, color = Theme.textSecondary, fontSize = 12.sp, modifier = Modifier.width(120.dp))
    }
}

@Composable
private fun GuideCard(
    item: GuideItem,
    isOpen: Boolean,
    modifier: Modifier = Modifier,
    onTap: () -> Unit
) {
    val rotation by animateFloatAsState(if (isOpen) 180f else 0f, Motion.snappy(), label = "guide")
    CardView(modifier, padding = 16.dp) {
        Row(
            Modifier.fillMaxWidth().pressable(onClick = onTap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Theme.accentGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(item.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(13.dp))
            Text(item.title, color = Theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = Theme.textSecondary,
                modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = rotation }
            )
        }
        AnimatedVisibility(
            visible = isOpen,
            enter = fadeIn(Motion.fade()) + expandVertically(Motion.card()),
            exit = fadeOut(Motion.fade(180)) + shrinkVertically(Motion.card())
        ) {
            Text(item.text, color = Theme.textSecondary, fontSize = 14.sp)
        }
    }
}
