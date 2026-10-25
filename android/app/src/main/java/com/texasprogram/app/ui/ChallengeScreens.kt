package com.texasprogram.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.ChallengeKind
import com.texasprogram.app.model.ChallengeScoring
import com.texasprogram.app.model.SocialChallenge
import com.texasprogram.app.model.SocialUserShort
import com.texasprogram.app.model.SocialWeekWinner
import com.texasprogram.app.service.ChallengeStore
import com.texasprogram.app.service.RuDate
import com.texasprogram.app.service.RuPlural
import com.texasprogram.app.service.SocialException
import com.texasprogram.app.service.SocialService
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.IsoFields

// MARK: - Челленджи с друзьями

val ChallengeKind.color: Color
    get() = when (this) {
        ChallengeKind.WORKOUTS -> Theme.accent
        ChallengeKind.VOLUME -> Color(0xFF338FFA)
        ChallengeKind.STREAK -> Theme.success
        ChallengeKind.STRENGTH -> Color(0xFFFAB829)
    }

val ChallengeKind.icon: ImageVector
    get() = when (this) {
        ChallengeKind.WORKOUTS -> Icons.Filled.LocalFireDepartment
        ChallengeKind.VOLUME -> Icons.Filled.Scale
        ChallengeKind.STREAK -> Icons.Filled.EventAvailable
        ChallengeKind.STRENGTH -> Icons.AutoMirrored.Filled.TrendingUp
    }

object ChallengePlace {
    fun color(place: Int?): Color = when (place) {
        1 -> Color(0xFFFFCC40)
        2 -> Color(0xFFCCCCCC)
        3 -> Color(0xFFD98C4D)
        else -> Color.White.copy(alpha = 0.5f)
    }

    fun ordinal(place: Int): String = "$place-е место"
}

/// Челленджи над рейтингом недели: приглашения, идущие и законченные.
@Composable
fun ChallengesSection() {
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf<String?>(null) }
    var showAllFinished by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { ChallengeStore.refresh() }

    Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionCaption("ЧЕЛЛЕНДЖИ", Modifier.weight(1f))
            Row(
                Modifier.pressable { creating = true }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Theme.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Новый", color = Theme.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        val error = ChallengeStore.error
        when {
            error != null && ChallengeStore.challenges.isEmpty() -> SocialErrorCard(error) { scope.launch { ChallengeStore.refresh() } }
            !ChallengeStore.loaded -> Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
            }
            ChallengeStore.challenges.isEmpty() -> EmptyChallenges { creating = true }
        }
        ChallengeStore.invites.forEach { ChallengeInviteCard(it) }
        ChallengeStore.running.forEach { challenge -> ChallengeCard(challenge) { opened = challenge.id } }
        val finished = ChallengeStore.finished
        (if (showAllFinished) finished else finished.take(2)).forEach { challenge -> ChallengeCard(challenge) { opened = challenge.id } }
        if (finished.size > 2 && !showAllFinished) {
            Text(
                "Все законченные · ${finished.size}",
                color = Theme.accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.pressable { showAllFinished = true }
            )
        }
    }
    if (creating) NewChallengeSheet { creating = false }
    opened?.let { id -> ChallengeDetailSheet(id) { opened = null } }
}

@Composable
private fun EmptyChallenges(onCreate: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = onCreate)
            .clip(RoundedCornerShape(20.dp))
            .background(Theme.surface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(Theme.accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Flag, contentDescription = null, tint = Theme.accent, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Позови друзей помериться", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                "Тренировки, тоннаж, недели без пропусков или рост силы — от недели до месяца.",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 13.sp
            )
        }
    }
}

// MARK: - Карточки

@Composable
private fun KindIcon(kind: ChallengeKind?, size: Dp = 42.dp) {
    val color = kind?.color ?: Theme.accent
    Box(Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Icon(kind?.icon ?: Icons.Filled.Flag, contentDescription = null, tint = color, modifier = Modifier.size(size * 0.46f))
    }
}

/// Сколько времени челленджа прошло.
@Composable
private fun TimeBar(challenge: SocialChallenge) {
    val now = System.currentTimeMillis().toDouble()
    val fraction = if (challenge.endsAt > challenge.startsAt) {
        ((now - challenge.startsAt) / (challenge.endsAt - challenge.startsAt)).coerceIn(0.0, 1.0).toFloat()
    } else 1f
    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceAtLeast(0.02f))
                .fillMaxHeight()
                .clip(CircleShape)
                .background(challenge.challengeKind?.color ?: Theme.accent)
        )
    }
}

@Composable
private fun ChallengeCard(challenge: SocialChallenge, onOpen: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = onOpen)
            .clip(RoundedCornerShape(20.dp))
            .background(Theme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KindIcon(challenge.challengeKind)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(challenge.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(challenge.timeLine(), color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            challenge.me?.place?.let { place ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$place", color = ChallengePlace.color(place), fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text("место", color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (!challenge.isFinished) TimeBar(challenge)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            challenge.joined.take(3).forEach { member ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${member.place ?: 0}",
                        color = ChallengePlace.color(member.place),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.width(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Avatar(member.user.avatar, member.user.username, 22.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (member.user.isMe) "Ты" else "@${member.user.username}",
                        color = if (member.user.isMe) Theme.accent else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(member.detail.ifEmpty { "—" }, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

/// Приглашение: кто зовёт, во что и на сколько — принять или отказаться.
@Composable
private fun ChallengeInviteCard(challenge: SocialChallenge) {
    val scope = rememberCoroutineScope()
    val haptics = rememberHapticEngine()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun answer(action: String) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val updated = SocialService.challenge(challenge.id, action)
                haptics.success()
                if (action == "join") ChallengeStore.replace(updated) else ChallengeStore.remove(challenge.id)
                if (action == "join") ChallengeStore.syncAgain()
            } catch (e: SocialException) {
                error = e.message
            }
            busy = false
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Theme.surface)
            .border(1.dp, Theme.accent.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KindIcon(challenge.challengeKind)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "@${challenge.owner?.username ?: "друг"} зовёт в челлендж",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("${challenge.title} · ${challenge.weeks} нед.", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(challenge.challengeKind?.subtitle.orEmpty(), color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
        error?.let { Text(it, color = Theme.record, fontSize = 12.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NutritionButton("Отказаться", Modifier.weight(1f), primary = false, enabled = !busy) { answer("decline") }
            NutritionButton("Принять", Modifier.weight(1f), enabled = !busy) { answer("join") }
        }
    }
}

// MARK: - Челлендж целиком

@Composable
internal fun ChallengeDetailSheet(id: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmLeave by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val challenge = ChallengeStore.challenges.firstOrNull { it.id == id }

    NutritionSheet(title = "Челлендж", onDismiss = onDismiss, leading = null, action = "Готово", onAction = { close -> close() }) { close ->
        if (challenge == null) {
            Text("Челлендж больше не доступен", color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(24.dp))
            return@NutritionSheet
        }
        val kind = challenge.challengeKind
        NutritionCard(spacing = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KindIcon(kind, 52.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(challenge.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "${challenge.weeks} нед. · ${RuDate.shortDayMonth(challenge.startDay())} – ${RuDate.shortDayMonth(challenge.endDay())}",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Text(kind?.subtitle.orEmpty(), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            if (!challenge.isFinished) TimeBar(challenge)
            Text(challenge.timeLine(), color = kind?.color ?: Theme.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        val mine = challenge.me
        val place = mine?.place
        if (challenge.isFinished && mine != null && place != null) {
            val xp = ChallengeScoring.xp(place, challenge.joined.size, mine.score)
            NutritionCard(spacing = 10.dp) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        if (place <= 3) Icons.Filled.EmojiEvents else Icons.Filled.Flag,
                        contentDescription = null,
                        tint = ChallengePlace.color(place),
                        modifier = Modifier.size(38.dp)
                    )
                    Text(if (place == 1) "Победа!" else ChallengePlace.ordinal(place), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    Text("из ${challenge.joined.size} · ${mine.detail}", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    if (xp > 0) {
                        Text(
                            if (challenge.isFinal()) "+$xp опыта" else "+$xp опыта — когда места станут окончательными",
                            color = Theme.accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                    NutritionButton("Поделиться", icon = Icons.Filled.Share, primary = false) {
                        WorkoutShareCard.share(context, ChallengeShareCard.render(challenge), "Поделиться итогом", "strain-challenge.png")
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Theme.surface)) {
            challenge.joined.forEachIndexed { index, member ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (member.user.isMe) Theme.accent.copy(alpha = 0.1f) else Color.Transparent)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${member.place ?: 0}",
                        color = ChallengePlace.color(member.place),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(26.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Avatar(member.user.avatar, member.user.username, 38.dp)
                    Spacer(Modifier.width(12.dp))
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (member.user.isMe) "Ты" else "@${member.user.username}",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        LevelChip(member.user.level)
                    }
                    Text(member.detail.ifEmpty { "—" }, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1)
                }
                if (index < challenge.joined.size - 1) HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(start = 90.dp))
            }
        }
        if (challenge.invited.isNotEmpty()) {
            Text(
                "Ждём ответа: " + challenge.invited.joinToString(", ") { "@${it.user.username}" },
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 13.sp
            )
        }
        Text(kind?.rules.orEmpty(), color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
        error?.let { Text(it, color = Theme.record, fontSize = 13.sp) }
        if (!challenge.isFinished && challenge.myStatus == "joined") {
            Text(
                if (confirmLeave) "Точно выйти? Счёт пропадёт из таблицы" else "Выйти из челленджа",
                color = Theme.record,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .pressable {
                        if (!confirmLeave) {
                            confirmLeave = true
                            return@pressable
                        }
                        scope.launch {
                            try {
                                SocialService.challenge(id, "leave")
                                ChallengeStore.remove(id)
                                close()
                            } catch (e: SocialException) {
                                error = e.message
                            }
                        }
                    }
                    .padding(vertical = 12.dp)
            )
        }
    }
}

// MARK: - Новый челлендж

@Composable
internal fun NewChallengeSheet(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptics = rememberHapticEngine()
    var kind by remember { mutableStateOf(ChallengeKind.WORKOUTS) }
    var weeks by remember { mutableStateOf(2) }
    val friends = remember { mutableStateListOf<SocialUserShort>() }
    val picked = remember { mutableStateListOf<String>() }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val me = SocialService.me
        if (me != null) runCatching { SocialService.following(me.username) }.onSuccess { friends.addAll(it) }
        loading = false
    }

    NutritionSheet(
        title = "Новый челлендж",
        onDismiss = onDismiss,
        action = if (busy) "…" else "Начать",
        actionEnabled = picked.isNotEmpty() && !busy,
        onAction = { close ->
            busy = true
            scope.launch {
                try {
                    val created = SocialService.createChallenge(kind, weeks, picked.sorted())
                    haptics.success()
                    ChallengeStore.replace(created)
                    close()
                    ChallengeStore.syncAgain()
                } catch (e: SocialException) {
                    error = e.message
                }
                busy = false
            }
        }
    ) {
        NutritionCaption("Во что меряемся")
        ChallengeKind.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { item ->
                    val selected = item == kind
                    Column(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 128.dp)
                            .pressable {
                                haptics.light()
                                kind = item
                            }
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) item.color.copy(alpha = 0.16f) else Theme.surface)
                            .border(1.5.dp, if (selected) item.color else Color.Transparent, RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(item.icon, contentDescription = null, tint = item.color, modifier = Modifier.size(20.dp))
                        Text(item.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(item.subtitle, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, maxLines = 3)
                    }
                }
            }
        }
        Text(kind.rules, color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)

        NutritionCaption("Сколько")
        SegmentedControl(options = (1..4).map { "$it нед." }, selectedIndex = weeks - 1) { weeks = it + 1 }

        NutritionCaption("Кого позвать")
        when {
            loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
            }
            friends.isEmpty() -> Text(
                "Подпишись на друзей во вкладке «Люди» — звать можно тех, на кого подписан.",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp
            )
            else -> Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Theme.surface)) {
                friends.forEach { friend ->
                    val chosen = friend.username in picked
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .pressable {
                                haptics.light()
                                if (chosen) picked.remove(friend.username) else picked.add(friend.username)
                            }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(friend.avatar, friend.username, 38.dp)
                        Spacer(Modifier.width(12.dp))
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "@${friend.username}",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            LevelChip(friend.level)
                        }
                        Icon(
                            if (chosen) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (chosen) Theme.accent else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
        if (picked.isNotEmpty()) {
            Text(
                "Позовём ${picked.size} ${RuPlural.word(picked.size, "друга", "друзей", "друзей")} — счёт пойдёт с сегодняшнего дня.",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 13.sp
            )
        }
        error?.let { Text(it, color = Theme.record, fontSize = 13.sp) }
    }
}

// MARK: - Победители недель

@Composable
fun WeekWinnersSheet(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val winners = remember { mutableStateListOf<SocialWeekWinner>() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        loading = true
        try {
            val loaded = SocialService.leaderboardHistory()
            winners.clear()
            winners.addAll(loaded)
            error = null
        } catch (e: SocialException) {
            error = e.message
        }
        loading = false
    }
    LaunchedEffect(Unit) { load() }

    NutritionSheet(title = "Победители недель", onDismiss = onDismiss, leading = null, action = "Готово", onAction = { close -> close() }) {
        val currentError = error
        when {
            currentError != null && winners.isEmpty() -> SocialErrorCard(currentError) { scope.launch { load() } }
            loading -> Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            }
            winners.isEmpty() -> Text(
                "Пока никто не выигрывал неделю. Победитель — у кого больше тренировок, при равенстве — больше тоннаж.",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp
            )
            else -> {
                // Кто сколько недель выиграл — сверху самые частые победители.
                val tally = winners.groupBy { it.user.id }
                    .map { (_, items) -> items.first().user to items.size }
                    .sortedWith(compareByDescending<Pair<SocialUserShort, Int>> { it.second }.thenBy { it.first.username })
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    tally.forEach { (user, count) ->
                        Column(
                            Modifier.width(84.dp).clip(RoundedCornerShape(16.dp)).background(Theme.surface).padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Avatar(user.avatar, user.username, 44.dp)
                            Text(if (user.isMe) "Ты" else "@${user.username}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("👑 $count", color = ChallengePlace.color(1), fontSize = 13.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Theme.surface)) {
                    winners.forEach { winner ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("👑", fontSize = 14.sp)
                            Spacer(Modifier.width(12.dp))
                            Avatar(winner.user.avatar, winner.user.username, 34.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (winner.user.isMe) "Ты" else "@${winner.user.username}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text(weekRange(winner.week), color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                            }
                            Text("${winner.workouts} трен.", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

/// «2026-W42» → «12 окт. – 18 окт.».
internal fun weekRange(key: String): String {
    val parts = key.split("-W")
    val year = parts.getOrNull(0)?.toIntOrNull() ?: return key
    val week = parts.getOrNull(1)?.toIntOrNull() ?: return key
    val monday = LocalDate.of(year, 1, 4)
        .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week.toLong())
        .let { RuDate.mondayOf(it) }
    return "${RuDate.shortDayMonth(monday)} – ${RuDate.shortDayMonth(monday.plusDays(6))}"
}

// MARK: - Карточка для сторис

/// Итог челленджа картинкой для сторис — в стиле итогов месяца.
object ChallengeShareCard {
    private const val WIDTH = 1080
    private const val HEIGHT = 1920
    private const val UNIT = 3f
    private fun u(value: Float) = value * UNIT

    private val black: Typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    private val bold: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    private val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private fun paint(size: Float, color: Int, face: Typeface) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = u(size)
        this.color = color
        typeface = face
    }

    private fun white(alpha: Float) = Color.White.copy(alpha = alpha).toArgb()

    fun render(challenge: SocialChallenge): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        val (from, to) = palette(3)
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), from.toArgb(), to.toArgb(), Shader.TileMode.CLAMP)
        })
        val pad = u(26f)
        val inner = WIDTH - pad * 2
        val top = pad + u(18f)
        canvas.drawText("STRAIN", pad, top, paint(18f, white(1f), black).apply { letterSpacing = 0.12f })
        val label = "ЧЕЛЛЕНДЖ"
        val labelPaint = paint(11f, white(0.8f), bold).apply { letterSpacing = 0.12f }
        canvas.drawText(label, WIDTH - pad - labelPaint.measureText(label), top, labelPaint)

        val place = challenge.me?.place ?: 0
        // Медаль места: круг цвета места и номер.
        val cx = WIDTH / 2f
        val cy = HEIGHT * 0.32f
        val radius = u(70f)
        canvas.drawCircle(cx, cy, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ChallengePlace.color(place).toArgb() })
        canvas.drawCircle(cx, cy, radius - u(10f), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = u(4f)
            color = white(0.5f)
        })
        canvas.drawText("$place", cx, cy + u(22f), paint(64f, Color(0xFF1A1A1A).toArgb(), black).apply { textAlign = Paint.Align.CENTER })

        var y = HEIGHT * 0.52f
        canvas.drawText(fit(challenge.title, paint(20f, white(0.8f), bold), inner), pad, y, paint(20f, white(0.8f), bold))
        y += u(56f)
        val headline = paint(48f, white(1f), black)
        canvas.drawText(fit(if (place == 1) "Победа" else ChallengePlace.ordinal(place), headline, inner), pad, y, headline)
        y += u(34f)
        val sub = paint(16f, white(0.75f), medium)
        canvas.drawText(fit("из ${challenge.joined.size} · ${challenge.weeks} нед. · ${challenge.me?.detail.orEmpty()}", sub, inner), pad, y, sub)

        // Тройка лидеров на тёмной плашке.
        y += u(30f)
        val rows = challenge.joined.take(3)
        val box = RectF(pad, y, WIDTH - pad, y + u(28f) + rows.size * u(28f))
        canvas.drawRoundRect(box, u(16f), u(16f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.Black.copy(alpha = 0.22f).toArgb() })
        var rowY = y + u(34f)
        rows.forEach { member ->
            val number = paint(15f, ChallengePlace.color(member.place).toArgb(), black)
            canvas.drawText("${member.place ?: 0}", pad + u(14f), rowY, number)
            val detail = paint(15f, white(1f), black)
            val detailWidth = detail.measureText(member.detail)
            canvas.drawText(member.detail, WIDTH - pad - u(14f) - detailWidth, rowY, detail)
            val name = paint(15f, white(1f), bold)
            canvas.drawText(fit("@${member.user.username}", name, inner - u(60f) - detailWidth), pad + u(40f), rowY, name)
            rowY += u(28f)
        }
        return bitmap
    }

    private fun fit(text: String, paint: TextPaint, width: Float): String =
        TextUtils.ellipsize(text, paint, width, TextUtils.TruncateAt.END).toString()
}
