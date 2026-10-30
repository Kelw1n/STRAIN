package com.texasprogram.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.LeaderboardMetric
import com.texasprogram.app.model.SocialActivityItem
import com.texasprogram.app.model.SocialFormat
import com.texasprogram.app.model.SocialLeaderboardEntry
import com.texasprogram.app.model.SocialLive
import com.texasprogram.app.model.SocialLiveFriend
import com.texasprogram.app.model.SocialPost
import com.texasprogram.app.service.RuDate
import com.texasprogram.app.service.SocialException
import com.texasprogram.app.service.SocialService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

// MARK: - Тренируется сейчас

/// Зелёная точка, которая расходится кругами, — «идёт прямо сейчас».
@Composable
fun LivePulse(size: Dp = 8.dp) {
    val transition = rememberInfiniteTransition(label = "livePulse")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "livePulsePhase"
    )
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = 1f + phase * 1.4f
                    scaleY = 1f + phase * 1.4f
                    alpha = 1f - phase
                }
                .clip(CircleShape)
                .background(Theme.success.copy(alpha = 0.4f))
        )
        Box(Modifier.size(size).clip(CircleShape).background(Theme.success))
    }
}

/// «Тренируется · Присед · 3/5» — в шапке профиля.
@Composable
fun LiveBadge(live: SocialLive) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(Theme.success.copy(alpha = 0.14f))
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LivePulse()
        Spacer(Modifier.width(7.dp))
        Text(
            if (live.line.isEmpty()) "Тренируется" else "Тренируется · ${live.line}",
            color = Theme.success,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/// Полоса над лентой: кто из подписок сейчас в зале и на каком подходе.
@Composable
fun LiveFriendsStrip(friends: List<SocialLiveFriend>, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LivePulse(7.dp)
            Spacer(Modifier.width(7.dp))
            SectionCaption("СЕЙЧАС ТРЕНИРУЮТСЯ")
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            friends.forEach { friend -> LiveCard(friend) { onOpen(friend.user.username) } }
        }
    }
}

@Composable
private fun LiveCard(friend: SocialLiveFriend, onClick: () -> Unit) {
    val live = friend.live
    // Минуты с начала тренировки тикают сами.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    Column(
        Modifier
            .width(196.dp)
            .pressable(onClick = onClick)
            .clip(RoundedCornerShape(18.dp))
            .background(Theme.tone(0xFF161617))
            .border(1.dp, Theme.success.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).border(2.dp, Theme.success, CircleShape), contentAlignment = Alignment.Center) {
                Avatar(friend.user.avatar, friend.user.username, 33.dp)
            }
            Spacer(Modifier.width(9.dp))
            Column {
                Text("@${friend.user.username}", color = Theme.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    SocialFormat.duration(((now - live.startedAt.toLong()) / 1000).toInt().coerceAtLeast(0)),
                    color = Theme.success,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Text(
            live.exercise.ifEmpty { live.title },
            color = Theme.ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (live.total > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SetDots(live.done, live.total)
                Spacer(Modifier.width(8.dp))
                Text("${live.done}/${live.total}", color = Theme.ink.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (live.title.isNotEmpty() && live.exercise.isNotEmpty()) {
            Text(live.title, color = Theme.ink.copy(alpha = 0.45f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/// Подходы упражнения чёрточками: сделанные — зелёные.
@Composable
private fun SetDots(done: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(minOf(total, 10)) { index ->
            Box(
                Modifier
                    .width(if (total > 6) 8.dp else 14.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(if (index < done) Theme.success else Theme.ink.copy(alpha = 0.14f))
            )
        }
    }
}

// MARK: - Активность

/// Колокольчик в шапке ленты: точка — есть новое.
@Composable
fun ActivityBell(onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).pressable(onClick = onClick).clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Outlined.Notifications, contentDescription = "Активность", tint = Theme.ink)
        if (SocialService.unseenActivity > 0) {
            Box(
                Modifier
                    .offset(x = 7.dp, y = (-7).dp)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(Theme.record)
            )
        }
    }
}

/// Кто поставил лайк, прокомментировал, ответил или подписался.
@Composable
fun ActivityScreen(contentPadding: PaddingValues, navigator: SocialNavigator) {
    val scope = rememberCoroutineScope()
    val activity = remember { mutableStateListOf<SocialActivityItem>() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        loading = true
        try {
            val page = SocialService.activity()
            activity.clear()
            activity.addAll(page.items)
            error = null
            // Новые подсвечены в этом списке, а точка на колокольчике гаснет.
            SocialService.markActivitySeen()
        } catch (e: SocialException) {
            error = e.message
        }
        loading = false
    }
    LaunchedEffect(Unit) { reload() }

    LazyColumn(Modifier.fillMaxSize().background(Theme.canvas), contentPadding = contentPadding) {
        item { SocialTopBar("Активность", onBack = { navigator.pop() }) }
        val currentError = error
        if (currentError != null && activity.isEmpty()) {
            item { Box(Modifier.padding(top = 12.dp)) { SocialErrorCard(currentError) { scope.launch { reload() } } } }
        } else if (activity.isEmpty() && !loading) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Outlined.Notifications, contentDescription = null, tint = Theme.ink.copy(alpha = 0.35f), modifier = Modifier.size(36.dp))
                    Text("Пока тихо", color = Theme.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Здесь появятся лайки, комментарии, ответы и новые подписчики.",
                        color = Theme.ink.copy(alpha = 0.55f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        // Без ключей: два события с одинаковыми полями не уронят список.
        items(activity) { item ->
            Column {
                ActivityRow(
                    item,
                    onOpenActor = { navigator.push(SocialRoute.User(item.actor.username)) },
                    onOpen = {
                        val postId = item.postId
                        if (item.kind == "follow") navigator.push(SocialRoute.User(item.actor.username))
                        else if (postId != null) navigator.push(SocialRoute.PostId(postId))
                    }
                )
                HorizontalDivider(color = Theme.ink.copy(alpha = 0.06f), modifier = Modifier.padding(start = 58.dp))
            }
        }
        if (loading && activity.isEmpty()) {
            item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Theme.ink, modifier = Modifier.size(24.dp)) } }
        }
    }
}

@Composable
internal fun ActivityRow(item: SocialActivityItem, onOpenActor: () -> Unit, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (item.unseen) Theme.accent.copy(alpha = 0.07f) else Color.Transparent)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.pressable(onClick = onOpenActor)) {
            Avatar(item.actor.avatar, item.actor.username, 44.dp)
            KindIcon(item.kind, Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp))
        }
        Spacer(Modifier.width(12.dp))
        Row(Modifier.weight(1f).pressable(onClick = onOpen), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("@${item.actor.username}") }
                        append(" ")
                        append(item.action)
                    },
                    color = Theme.ink,
                    fontSize = 15.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(SocialFormat.ago(item.instant), color = Theme.ink.copy(alpha = 0.4f), fontSize = 12.sp)
            }
            if (item.unseen) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.padding(top = 5.dp).size(9.dp).clip(CircleShape).background(Theme.accent))
            }
        }
    }
}

@Composable
private fun KindIcon(kind: String, modifier: Modifier = Modifier) {
    val (icon, color) = when (kind) {
        "comment" -> Icons.Filled.ChatBubble to Theme.accent
        "reply" -> Icons.AutoMirrored.Filled.Reply to Theme.accent
        "follow" -> Icons.Filled.PersonAdd to Theme.success
        else -> Icons.Filled.Favorite to Color(0xFFFF455C)
    }
    Box(
        modifier.size(21.dp).clip(CircleShape).background(Theme.canvas).padding(2.dp).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Theme.ink, modifier = Modifier.size(10.dp))
    }
}

/// Пост по ссылке из активности: сначала загружаем, потом показываем.
@Composable
fun PostLoaderScreen(id: String, contentPadding: PaddingValues, navigator: SocialNavigator) {
    val scope = rememberCoroutineScope()
    var post by remember { mutableStateOf<SocialPost?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        try {
            post = SocialService.post(id)
            error = null
        } catch (e: SocialException) {
            error = e.message
        }
    }
    LaunchedEffect(id) { load() }

    val loaded = post
    val currentError = error
    when {
        loaded != null -> PostDetailScreen(loaded, contentPadding, navigator) {}
        currentError != null -> Column(Modifier.fillMaxSize().padding(contentPadding)) {
            SocialTopBar("Тренировка", onBack = { navigator.pop() })
            SocialErrorCard(currentError) { scope.launch { load() } }
        }
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Theme.ink, modifier = Modifier.size(26.dp))
        }
    }
}

// MARK: - Рейтинг недели

/// Рейтинг недели среди подписок: тоннаж, тренировки, серия и рекорды.
@Composable
fun LeaderboardSection(onOpen: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val entries = remember { mutableStateListOf<SocialLeaderboardEntry>() }
    var metric by rememberSaveable { mutableStateOf(LeaderboardMetric.VOLUME) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showingWinners by remember { mutableStateOf(false) }

    suspend fun reload() {
        loading = true
        try {
            val loaded = SocialService.leaderboard()
            entries.clear()
            entries.addAll(loaded)
            error = null
        } catch (e: SocialException) {
            error = e.message
        }
        loading = false
    }
    LaunchedEffect(Unit) { reload() }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LeaderboardMetric.entries.forEach { item ->
                val selected = item == metric
                val label by animateColorAsState(if (selected) Theme.canvas else Theme.ink, Motion.fade(200), label = "metricLabel")
                Box(
                    Modifier
                        .height(34.dp)
                        .pressable { metric = item }
                        .clip(CircleShape)
                        .selectionFill(selected, Theme.ink, Theme.tone(0xFF242424), CircleShape)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(item.title, color = label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionCaption(weekTitle(), Modifier.weight(1f))
            Text(
                "👑 Победители",
                color = ChallengePlace.color(1),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.pressable { showingWinners = true }.padding(vertical = 4.dp)
            )
        }
        val currentError = error
        when {
            currentError != null && entries.isEmpty() -> SocialErrorCard(currentError) { scope.launch { reload() } }
            loading && entries.isEmpty() -> Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Theme.ink, modifier = Modifier.size(24.dp))
            }
            else -> {
                LeaderboardCard(LeaderboardMetric.ranked(entries, metric), metric, onOpen)
                if (entries.size < 2) {
                    Text(
                        "Подпишись на друзей — и меряйтесь тоннажем, тренировками и рекордами каждую неделю.",
                        color = Theme.ink.copy(alpha = 0.5f),
                        fontSize = 14.sp
                    )
                }
            }
        }
        Text(
            "Считаются все тренировки в приложении, а не только опубликованные. Неделя — с понедельника. Корона — тому, у кого за прошлую неделю больше всех тренировок.",
            color = Theme.ink.copy(alpha = 0.35f),
            fontSize = 12.sp
        )
    }
    if (showingWinners) WeekWinnersSheet { showingWinners = false }
}

private fun weekTitle(today: LocalDate = LocalDate.now()): String {
    val monday = RuDate.mondayOf(today)
    return "НЕДЕЛЯ · ${RuDate.shortDayMonth(monday)} – ${RuDate.shortDayMonth(monday.plusDays(6))}".uppercase()
}

@Composable
internal fun LeaderboardCard(ranked: List<SocialLeaderboardEntry>, metric: LeaderboardMetric, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Theme.tone(0xFF161617))) {
        ranked.forEachIndexed { index, entry ->
            LeaderboardRow(index + 1, entry, metric) { onOpen(entry.user.username) }
            if (index < ranked.size - 1) HorizontalDivider(color = Theme.ink.copy(alpha = 0.06f), modifier = Modifier.padding(start = 98.dp))
        }
    }
}

@Composable
private fun LeaderboardRow(place: Int, entry: SocialLeaderboardEntry, metric: LeaderboardMetric, onClick: () -> Unit) {
    val scored = metric.value(entry.stats) > 0
    val placeColor = when {
        !scored -> Theme.ink.copy(alpha = 0.4f)
        place == 1 -> if (Theme.isLight) Color(0xFFDB9E00) else Color(0xFFFFCC40)
        place == 2 -> if (Theme.isLight) Color(0xFF8C8C8C) else Color(0xFFC7C7C7)
        place == 3 -> Color(0xFFD98C4D)
        else -> Theme.ink.copy(alpha = 0.5f)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .background(if (entry.user.isMe) Theme.accent.copy(alpha = 0.1f) else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$place", color = placeColor, fontSize = 16.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.width(30.dp))
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier.size(46.dp).then(if (place <= 3 && scored) Modifier.border(2.dp, placeColor, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Avatar(entry.user.avatar, entry.user.username, 40.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (entry.user.isMe) "Ты" else "@${entry.user.username}",
                    color = Theme.ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                // Корона — победителю прошлой недели.
                if (entry.crown) Text("👑", fontSize = 12.sp)
                LevelChip(entry.user.level ?: entry.stats.level)
            }
            // Остальные цифры недели мелко — чтобы было видно, за счёт чего место.
            Text(
                ((if (entry.wins > 0) listOf("👑 ${entry.wins}") else emptyList()) +
                    LeaderboardMetric.entries.filter { it != metric }.map { it.format(entry.stats) }).joinToString(" · "),
                color = Theme.ink.copy(alpha = 0.45f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            metric.format(entry.stats),
            color = if (scored) Theme.ink else Theme.ink.copy(alpha = 0.35f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Black
        )
    }
}
