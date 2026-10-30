package com.texasprogram.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.SocialChatWorkout
import com.texasprogram.app.model.SocialFormat
import com.texasprogram.app.model.SocialUserShort
import com.texasprogram.app.service.RuPlural
import com.texasprogram.app.service.SocialService
import kotlinx.coroutines.delay

// MARK: - Кому отправить

/// Друзья, которым можно что-то отправить: сначала те, с кем переписываешься,
/// потом подписки. Своего профиля здесь нет.
internal suspend fun chatRecipients(): List<SocialUserShort> {
    val me = SocialService.me
    val result = mutableListOf<SocialUserShort>()
    val seen = mutableSetOf<String>()
    fun add(users: List<SocialUserShort>) {
        users.forEach { if (!it.isMe && it.id != me?.id && seen.add(it.id)) result.add(it) }
    }
    runCatching { SocialService.chats().chats.map { it.user } }.onSuccess(::add)
    if (me != null) runCatching { SocialService.following(me.username) }.onSuccess(::add)
    return result
}

/// Лист «Переслать» / «Отправить другу»: недавние переписки, подписки и поиск.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTargetSheet(title: String, onDismiss: () -> Unit, onPick: (SocialUserShort) -> Unit) {
    val haptics = rememberHapticEngine()
    var query by remember { mutableStateOf("") }
    val recent = remember { mutableStateListOf<SocialUserShort>() }
    val found = remember { mutableStateListOf<SocialUserShort>() }
    var loading by remember { mutableStateOf(true) }
    val searching = query.isNotBlank()

    LaunchedEffect(Unit) {
        recent.addAll(chatRecipients())
        loading = false
    }
    LaunchedEffect(query) {
        if (!searching) {
            found.clear()
            return@LaunchedEffect
        }
        // Ждём, пока допечатают, — не шлём запрос на каждую букву.
        delay(300)
        loading = true
        val users = runCatching { SocialService.searchUsers(SocialFormat.normalizedUsername(query)) }.getOrDefault(emptyList())
        found.clear()
        found.addAll(users.filter { !it.isMe })
        loading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Theme.tone(0xFF121212)
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp)) {
            Text(title, color = Theme.ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Theme.ink.copy(alpha = 0.08f))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = Theme.ink.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Юзернейм", color = Theme.ink.copy(alpha = 0.4f), fontSize = 15.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it.take(24) },
                        singleLine = true,
                        textStyle = TextStyle(color = Theme.ink, fontSize = 15.sp),
                        cursorBrush = SolidColor(Theme.accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val shown = if (searching) found else recent
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 460.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (loading && shown.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Theme.ink, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                        }
                    }
                } else if (shown.isEmpty()) {
                    item {
                        Text(
                            if (searching) "Никого не нашли" else "Пока не с кем — найди друга по юзернейму",
                            color = Theme.ink.copy(alpha = 0.5f),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 20.dp)
                        )
                    }
                }
                items(shown, key = { it.id }) { user ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .pressable {
                                haptics.light()
                                onPick(user)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(user.avatar, user.username, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("@${user.username}", color = Theme.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Theme.accent, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/// Друзья кружками в итогах тренировки: отметил — тренировка уйдёт им
/// карточкой в переписку, когда нажмёшь «Дальше».
@Composable
fun WorkoutRecipientsPicker(selected: Set<String>, onToggle: (String) -> Unit) {
    val haptics = rememberHapticEngine()
    val friends = remember { mutableStateListOf<SocialUserShort>() }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!loaded) {
            friends.addAll(chatRecipients())
            loaded = true
        }
    }
    if (loaded && friends.isEmpty()) {
        Text(
            "Подпишись на друзей или напиши кому-нибудь — и здесь можно будет отправить им тренировку.",
            color = Theme.ink.copy(alpha = 0.5f),
            fontSize = 12.sp
        )
        return
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (!loaded) {
            Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Theme.ink, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            }
        }
        friends.forEach { friend ->
            val on = friend.username in selected
            Column(
                Modifier.width(66.dp).pressable {
                    haptics.light()
                    onToggle(friend.username)
                },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box {
                    Box(
                        Modifier
                            .size(60.dp)
                            .border(3.dp, if (on) Theme.accent else Color.Transparent, CircleShape)
                            .padding(4.dp)
                    ) {
                        Avatar(friend.avatar, friend.username, 52.dp)
                    }
                    if (on) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = Theme.accent,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Theme.canvas)
                                .popIn()
                        )
                    }
                }
                Text(
                    "@${friend.username}",
                    color = if (on) Theme.ink else Theme.ink.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// MARK: - Тренировка в переписке

/// Тренировка карточкой в пузыре: название, время, объём, подходы, рекорды
/// и лучшие подходы первых упражнений. По нажатию открывается целиком.
@Composable
internal fun ChatWorkoutCard(workout: SocialChatWorkout, fromMe: Boolean, onOpen: () -> Unit) {
    // На моей карточке (оранжевый пузырь) текст белый в любой теме.
    val cardInk = if (fromMe) Color.White else Theme.ink
    Column(
        Modifier.width(236.dp).pressable(onClick = onOpen),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.FitnessCenter, contentDescription = null, tint = cardInk.copy(alpha = 0.7f), modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text("ТРЕНИРОВКА", color = cardInk.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
            if (workout.records > 0) {
                Spacer(Modifier.weight(1f))
                Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = Theme.warning, modifier = Modifier.size(13.dp))
                Text(" ${workout.records}", color = Theme.warning, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(workout.title, color = cardInk, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CardStat(SocialFormat.duration(workout.durationSeconds), "время")
            CardStat("${SocialFormat.grouped(workout.volumeKg)} кг", "объём")
            CardStat("${workout.sets}", RuPlural.word(workout.sets, "подход", "подхода", "подходов"))
        }
        val exercises = workout.workout.exercises
        if (exercises.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = if (fromMe) 0.18f else 0.25f))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                exercises.take(3).forEach { exercise ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (exercise.hasRecord) {
                            Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = Theme.warning, modifier = Modifier.size(11.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(exercise.name, color = cardInk.copy(alpha = 0.85f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        exercise.best?.let {
                            Spacer(Modifier.width(6.dp))
                            Text(it.text, color = cardInk.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (exercises.size > 3) {
                    val rest = exercises.size - 3
                    Text(
                        "и ещё $rest ${RuPlural.word(rest, "упражнение", "упражнения", "упражнений")}",
                        color = cardInk.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Открыть", color = if (fromMe) Color.White else Theme.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (fromMe) Color.White else Theme.accent,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun CardStat(value: String, title: String) {
    Column {
        Text(value, color = Theme.ink, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(title, color = Theme.ink.copy(alpha = 0.55f), fontSize = 11.sp)
    }
}

/// Тренировка из переписки целиком: все подходы и «Скопировать к себе».
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatWorkoutSheet(workout: SocialChatWorkout, author: String?, navigator: SocialNavigator, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Theme.tone(0xFF121212)
    ) {
        LazyColumn(
            Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Text(workout.title, color = Theme.ink, fontSize = 20.sp, fontWeight = FontWeight.Black) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    SheetStat(SocialFormat.duration(workout.durationSeconds), null, "Время")
                    SheetStat(SocialFormat.grouped(workout.volumeKg), "кг", "Объём")
                    SheetStat("${workout.sets}", null, RuPlural.word(workout.sets, "Подход", "Подхода", "Подходов"))
                    workout.effort?.let { SheetStat("$it", "/10", "Усилие") }
                }
            }
            if (author != null && workout.workout.exercises.isNotEmpty()) {
                item {
                    CopyProgramButton(days = emptyList(), author = author, navigator = navigator, workout = workout.title to workout.workout)
                }
            }
            item { SectionCaption(workout.workout.program?.let { "ТРЕНИРОВКА · ${it.uppercase()}" } ?: "ТРЕНИРОВКА") }
            items(workout.workout.exercises.size) { index -> WorkoutSetsCard(workout.workout.exercises[index]) }
        }
    }
}

@Composable
private fun SheetStat(value: String, unit: String?, title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = Theme.ink, fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 1)
            unit?.let { Text(" $it", color = Theme.ink.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        }
        Text(title, color = Theme.ink.copy(alpha = 0.5f), fontSize = 13.sp)
    }
}
