package com.texasprogram.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.service.CoachAdvisor
import com.texasprogram.app.service.CoachException
import com.texasprogram.app.service.RuDate
import kotlinx.coroutines.launch
import java.time.LocalDate

/// Разбор тренировок: приложение отправляет заметки и цифры модели и показывает ответ.
///
/// Всё, что можно посчитать, приложение считает само. Сюда уходит только проза —
/// то, что человек написал про самочувствие, и цифры рядом, чтобы совет
/// опирался на реальные веса, а не на общие слова.
@Composable
fun CoachCard(
    profile: ProgramProfile,
    apiKey: String,
    model: String,
    modifier: Modifier = Modifier,
    onUpdate: (ProgramProfile) -> Unit
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val hasData = CoachAdvisor.hasData(profile)
    val advice = profile.lastAdvice

    CardView(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Theme.accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Разбор тренировок", color = Theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            profile.lastAdviceEpochDay?.let {
                Text(RuDate.dayMonth(LocalDate.ofEpochDay(it)), color = Theme.textTertiary, fontSize = 10.sp)
            }
        }

        if (!advice.isNullOrEmpty()) {
            Text(advice, color = Theme.textPrimary, fontSize = 13.sp, lineHeight = 19.sp)
        } else {
            Text(
                if (hasData) "Приложение отправит заметки и записанные подходы за последние недели и вернёт разбор: что видно, что поменять, за чем следить."
                else "Пиши заметки после тренировок и записывай подходы — тогда будет что разбирать.",
                color = Theme.textTertiary,
                fontSize = 11.sp
            )
        }

        errorText?.let { Text(it, color = Theme.record, fontSize = 11.sp) }

        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(13.dp))
                .background(if (hasData && !loading) Theme.accentGradient else SolidColor(Theme.surfaceSoft))
                .pressable(enabled = hasData && !loading) {
                    scope.launch {
                        loading = true
                        errorText = null
                        try {
                            val text = CoachAdvisor.analyze(profile, apiKey, model)
                            onUpdate(
                                profile.copy(
                                    lastAdvice = text,
                                    lastAdviceEpochDay = LocalDate.now().toEpochDay()
                                )
                            )
                        } catch (error: CoachException) {
                            errorText = error.message
                        } catch (error: Exception) {
                            errorText = "Не получилось связаться с сервером. Проверь интернет."
                        }
                        loading = false
                    }
                }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp, color = Theme.textPrimary)
                } else {
                    Icon(
                        if (advice == null) Icons.Filled.AutoAwesome else Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = if (hasData) Theme.textPrimary else Theme.textTertiary,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Text(
                    if (loading) "Смотрю…" else if (advice == null) "Разобрать" else "Обновить разбор",
                    color = if (hasData) Theme.textPrimary else Theme.textTertiary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
