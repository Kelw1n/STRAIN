package com.texasprogram.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.data.AppStore
import com.texasprogram.app.model.CustomProgram
import com.texasprogram.app.model.ProgramCopy
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.SocialProgramDay
import com.texasprogram.app.model.SocialWorkout
import com.texasprogram.app.service.RuPlural
import com.texasprogram.app.service.SocialService
import kotlinx.coroutines.launch

/// Копия открывается в конструкторе «Своей программы»: можно поправить веса,
/// повторы и число недель. Сохранение — новый профиль, текущий не трогаем.
@Composable
fun CopyProgramScreen(program: CustomProgram, author: String, contentPadding: PaddingValues, navigator: SocialNavigator) {
    val context = LocalContext.current
    var saved by remember { mutableStateOf<ProgramProfile?>(null) }

    CustomProgramBuilder(
        contentPadding = contentPadding,
        onBack = { navigator.pop() },
        initial = program,
        title = "Копия @$author",
        saveTitle = "Сохранить как новый профиль"
    ) { edited ->
        val profile = ProgramProfile.custom(edited).copy(name = "@$author")
        AppStore.get(context).add(profile, activate = false)
        saved = profile
    }

    val profile = saved
    if (profile != null) {
        AlertDialog(
            onDismissRequest = {
                saved = null
                navigator.pop()
            },
            containerColor = Theme.dialog,
            title = { Text("Программа сохранена", color = Theme.textPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Это новый профиль «${profile.name}». Переключаться между профилями можно в настройках.", color = Theme.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    saved = null
                    navigator.pop()
                    AppStore.get(context).setActive(profile.id)
                }) { Text("Перейти на неё", color = Theme.accent) }
            },
            dismissButton = {
                TextButton(onClick = {
                    saved = null
                    navigator.pop()
                }) { Text("Остаться", color = Theme.textSecondary) }
            }
        )
    }
}

/// Кнопка «Скопировать к себе» с выбором: вся программа, один день или тренировка из поста.
@Composable
fun CopyProgramButton(
    days: List<SocialProgramDay>,
    author: String,
    navigator: SocialNavigator,
    workout: Pair<String, SocialWorkout>? = null,
    /// Дни программы автора подгружаются при нажатии, если их не передали.
    loadsAuthorProgram: Boolean = false
) {
    val scope = rememberCoroutineScope()
    var loadedDays by remember { mutableStateOf<List<SocialProgramDay>?>(null) }
    var choosing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    val allDays = loadedDays ?: days

    fun open(program: CustomProgram) {
        choosing = false
        navigator.push(SocialRoute.CopyProgram(program, author))
    }

    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .pressable(enabled = !loading) {
                if (loadsAuthorProgram && loadedDays == null) {
                    loading = true
                    scope.launch {
                        loadedDays = runCatching { SocialService.user(author).programs }.getOrDefault(emptyList())
                        loading = false
                        choosing = true
                    }
                } else {
                    choosing = true
                }
            }
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (loading) CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        else Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Скопировать к себе", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }

    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            containerColor = Theme.dialog,
            title = { Text("Что скопировать?", color = Theme.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Копия станет «Своей программой» в новом профиле — веса и повторы можно поправить перед сохранением.",
                        color = Theme.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    workout?.let { (title, value) ->
                        CopyOption("Эту тренировку с весами") { open(ProgramCopy.program(value, title, title)) }
                    }
                    if (allDays.size > 1) {
                        CopyOption("Всю программу (${allDays.size} ${RuPlural.word(allDays.size, "день", "дня", "дней")})") {
                            open(ProgramCopy.program(allDays, "Программа @$author"))
                        }
                    }
                    allDays.forEach { day ->
                        CopyOption("Только «${day.title}»") { open(ProgramCopy.program(listOf(day), day.title)) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosing = false }) { Text("Отмена", color = Theme.textSecondary) } }
        )
    }
}

@Composable
private fun CopyOption(title: String, onClick: () -> Unit) {
    Column {
        Box(Modifier.fillMaxWidth().pressable(onClick = onClick).padding(vertical = 12.dp)) {
            Text(title, color = Theme.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
    }
}
