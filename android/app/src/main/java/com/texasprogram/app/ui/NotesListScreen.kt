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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.ProgramProfile

/// Все заметки цикла одним списком.
///
/// Заметка живёт на своей тренировке, и это правильно — писать её удобно там же.
/// Но смысл в ней появляется, только когда месяц можно перечитать подряд.
@Composable
fun NotesListScreen(
    profile: ProgramProfile,
    onUpdate: (ProgramProfile) -> Unit,
    contentPadding: PaddingValues
) {
    val prefix = profile.keyPrefix
    // От свежих к старым: перечитывают обычно последнее.
    val notes = profile.workoutNotes
        .filterKeys { profile.isOwnKey(it) }
        .mapNotNull { (key, text) ->
            val parts = key.removePrefix(prefix).split("-")
            val week = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val day = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            Triple(week, day, text)
        }
        .sortedWith(compareByDescending<Triple<Int, Int, String>> { it.first }.thenByDescending { it.second })

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

        items(notes, key = { "${it.first}-${it.second}" }) { (week, day, text) ->
            CardView(padding = 15.dp, spacing = 7.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Неделя $week, день $day",
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
                            .pressable { onUpdate(profile.setNote("", week, day)) }
                    )
                }
                Text(text, color = Theme.textPrimary, fontSize = 13.sp)
            }
        }
    }
}
