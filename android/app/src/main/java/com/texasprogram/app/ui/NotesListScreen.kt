package com.texasprogram.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.freeDay

/// Все заметки профиля одним списком.
///
/// Заметка живёт на своей тренировке, и это правильно — писать её удобно там же.
/// Но смысл в ней появляется, только когда месяц можно перечитать подряд.
@Composable
fun NotesListScreen(
    profile: ProgramProfile,
    onUpdate: (ProgramProfile) -> Unit,
    contentPadding: PaddingValues
) {
    data class Note(val key: String, val week: Int, val day: Int, val text: String, val archived: Boolean)
    val notes = remember(profile) {
        val dates = profile.completionLog.associate { it.key to it.epochDay }
        profile.workoutNotes
            .mapNotNull { (key, text) ->
                val current = profile.noteSlot(key)
                val (week, day) = current ?: profile.previousDaySlot(key) ?: return@mapNotNull null
                Note(key, week, day, text, current == null)
            }
            .sortedWith(compareBy<Note> { it.archived }
                .thenByDescending { dates[it.key] ?: Long.MIN_VALUE }
                .thenByDescending { it.week }
                .thenByDescending { it.day }
                .thenBy { it.key })
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "title") { ScreenTitle("Заметки") }

        if (notes.isEmpty()) {
            item(key = "empty") {
                CardView(Modifier.appearIn(0)) {
                    Text("Заметок пока нет", color = Theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Поле для заметки есть на каждой тренировке — под списком упражнений. Через месяц по ним будет понятно, почему неделя вышла такой.",
                        color = Theme.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        items(notes, key = { it.key }) { (key, week, day, text, archived) ->
            CardView(padding = 15.dp, spacing = 7.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (week == ProgramProfile.FREE_WEEK) profile.freeDay(day)?.title ?: "Свободная тренировка"
                        else if (archived) "Прошлый цикл · Неделя $week, день $day"
                        else "Неделя $week, день $day",
                        color = Theme.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "Удалить заметку",
                        tint = Theme.textTertiary,
                        modifier = Modifier
                            .size(18.dp)
                            .pressable { onUpdate(profile.copy(workoutNotes = profile.workoutNotes - key)) }
                    )
                }
                RawText(text, color = Theme.textPrimary, fontSize = 13.sp)
            }
        }
    }
}
