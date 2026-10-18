package com.texasprogram.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.SocialFeedPage
import com.texasprogram.app.model.SocialFormat
import com.texasprogram.app.model.SocialPost
import com.texasprogram.app.model.SocialUserShort
import com.texasprogram.app.service.SocialException
import com.texasprogram.app.service.SocialService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/// Экраны, на которые ведут переходы из ленты, друзей и профиля.
@Composable
fun SocialRouteScreen(
    route: SocialRoute,
    navigator: SocialNavigator,
    contentPadding: PaddingValues,
    profile: ProgramProfile,
    onPostChange: (String, SocialPost?) -> Unit = { _, _ -> }
) {
    when (route) {
        is SocialRoute.Post -> PostDetailScreen(route.post, contentPadding, navigator) { onPostChange(route.post.id, it) }
        is SocialRoute.User -> UserProfileScreen(route.username, navigator, contentPadding)
        is SocialRoute.Followers -> PeopleListScreen("Подписчики", contentPadding, navigator) { SocialService.followers(route.username) }
        is SocialRoute.Following -> PeopleListScreen("Подписки", contentPadding, navigator) { SocialService.following(route.username) }
        is SocialRoute.Program -> ProgramDayScreen(route.day, contentPadding, navigator, route.all, route.author)
        is SocialRoute.Chat -> ChatScreen(route.username, contentPadding, navigator)
        SocialRoute.Activity -> ActivityScreen(contentPadding, navigator)
        is SocialRoute.PostId -> PostLoaderScreen(route.id, contentPadding, navigator)
        is SocialRoute.CopyProgram -> CopyProgramScreen(route.program, route.author, contentPadding, navigator)
        is SocialRoute.Badges -> BadgeListScreen(route.badges, contentPadding, navigator)
        SocialRoute.Achievements -> AchievementsScreen(remember(profile) { com.texasprogram.app.model.AchievementCalculator.progress(profile) }, contentPadding, navigator)
        SocialRoute.EditProfile -> EditProfileScreen(profile, contentPadding, navigator)
    }
}

// MARK: - Лента

/// Вкладка «Главное»: тренировки всех, кто пользуется STRAIN, или только подписок.
@Composable
fun FeedScreen(
    profile: ProgramProfile,
    contentPadding: PaddingValues,
    onFindPeople: () -> Unit,
    onCreateProfile: () -> Unit
) {
    val navigator = rememberSocialNavigator()
    val scope = rememberCoroutineScope()
    var feedScope by rememberSaveable { mutableStateOf(SocialService.FeedScope.ALL) }
    val posts = remember { mutableStateListOf<SocialPost>() }
    var next by remember { mutableStateOf<Double?>(null) }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var scopeMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun cache() {
        SocialService.feedCache[feedScope] = SocialFeedPage(posts.toList(), next)
    }

    fun replace(id: String, value: SocialPost?) {
        val index = posts.indexOfFirst { it.id == id }
        if (index < 0) return
        if (value != null) posts[index] = value else posts.removeAt(index)
        cache()
    }

    suspend fun reload() {
        // Без профиля подписок нет: смена ленты сама перезапустит загрузку.
        if (!SocialService.isSignedIn && feedScope == SocialService.FeedScope.FOLLOWING) {
            feedScope = SocialService.FeedScope.ALL
            return
        }
        // Прошлая версия ленты — сразу, свежая придёт следом без пустого экрана.
        val cached = SocialService.feedCache[feedScope]
        if (cached != null && posts.isEmpty()) {
            posts.addAll(cached.posts)
            next = cached.next
        }
        loading = true
        SocialService.flushOutbox()
        try {
            val page = SocialService.feed(feedScope)
            posts.clear()
            posts.addAll(page.posts)
            next = page.next
            error = null
            cache()
        } catch (e: SocialException) {
            error = e.message
        } finally {
            loading = false
        }
    }

    LaunchedEffect(feedScope, SocialService.feedVersion) {
        launch {
            SocialService.refreshLive()
            SocialService.refreshTogether()
        }
        reload()
    }
    // Кто в зале — раз в минуту, пока лента на экране.
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            SocialService.refreshLive()
        }
    }

    // Подгружаем следующую страницу, когда до конца списка остаётся пара постов.
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, next) {
        val cursor = next
        if (nearEnd && cursor != null && !loadingMore && !loading) {
            loadingMore = true
            runCatching { SocialService.feed(feedScope, cursor) }.onSuccess { page ->
                val known = posts.map { it.id }.toSet()
                posts.addAll(page.posts.filter { it.id !in known })
                next = page.next
                cache()
            }
            loadingMore = false
        }
    }

    SocialHost(navigator, screen = { route ->
        SocialRouteScreen(route, navigator, contentPadding, profile, onPostChange = ::replace)
    }) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentPadding = contentPadding
        ) {
            item(key = "header") {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        Row(
                            Modifier.pressable(enabled = SocialService.isSignedIn) { scopeMenu = true },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (feedScope == SocialService.FeedScope.ALL) "Главное" else "Подписки",
                                color = Color.White,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black
                            )
                            if (SocialService.isSignedIn) {
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Выбрать ленту", tint = Color.White.copy(alpha = 0.5f))
                            }
                        }
                        DropdownMenu(expanded = scopeMenu, onDismissRequest = { scopeMenu = false }) {
                            SocialService.FeedScope.entries.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(if (item == SocialService.FeedScope.ALL) "Все в STRAIN" else "Мои подписки") },
                                    trailingIcon = { if (item == feedScope) Icon(Icons.Filled.Check, contentDescription = null) },
                                    onClick = {
                                        scopeMenu = false
                                        if (item != feedScope) {
                                            // Другая лента — сразу её прошлая версия, если была.
                                            val cached = SocialService.feedCache[item]
                                            posts.clear()
                                            posts.addAll(cached?.posts.orEmpty())
                                            next = cached?.next
                                            feedScope = item
                                        }
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (SocialService.isSignedIn) ActivityBell { navigator.push(SocialRoute.Activity) }
                    Box(
                        Modifier.size(42.dp).pressable(onClick = onFindPeople).clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = "Найти людей", tint = Color.White)
                    }
                }
            }
            if (!SocialService.isSignedIn) item(key = "teaser") { SignUpTeaser(onCreateProfile) }
            SocialService.together.active?.let { active ->
                item(key = "together") { Box(Modifier.animateItem(fadeInSpec = tween(240), placementSpec = Motion.snappy(), fadeOutSpec = tween(160)).padding(top = 8.dp)) { TogetherCard(active) } }
            }
            if (SocialService.isSignedIn && SocialService.liveFriends.isNotEmpty()) {
                item(key = "live") {
                    Box(Modifier.animateItem(fadeInSpec = tween(240), placementSpec = Motion.snappy(), fadeOutSpec = tween(160)).padding(top = 8.dp, bottom = 6.dp)) {
                        LiveFriendsStrip(SocialService.liveFriends) { navigator.push(SocialRoute.User(it)) }
                    }
                }
            }
            if (SocialService.outbox.isNotEmpty()) item(key = "outbox") {
                Box(Modifier.animateItem(fadeInSpec = tween(240), placementSpec = Motion.snappy(), fadeOutSpec = tween(160))) { OutboxCard() }
            }
            val currentError = error
            if (currentError != null && posts.isEmpty()) {
                item(key = "error") {
                    Box(Modifier.padding(top = 20.dp)) { SocialErrorCard(currentError) { scope.launch { reload() } } }
                }
            } else if (posts.isEmpty() && !loading) {
                item(key = "empty") { FeedEmpty(feedScope, onFindPeople) }
            }
            items(posts, key = { it.id }) { post ->
                // Новый пост проявляется, удалённый тает, остальные плавно съезжаются.
                Column(Modifier.animateItem(fadeInSpec = tween(260), placementSpec = Motion.snappy(), fadeOutSpec = tween(180))) {
                    PostCard(
                        post = post,
                        onOpen = { navigator.push(SocialRoute.Post(post)) },
                        onOpenAuthor = { navigator.push(SocialRoute.User(post.author.username)) },
                        onChange = { replace(post.id, it) }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
                }
            }
            if (loadingMore || (loading && posts.isEmpty())) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(26.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SignUpTeaser(onCreateProfile: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF161617))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Делись тренировками", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text(
            "Создай профиль: в конце тренировки сделаешь фото — и оно появится здесь вместе с весами и подходами. Друзья поставят лайк или напишут комментарий.",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp
        )
        Box(
            Modifier.height(44.dp).pressable(onClick = onCreateProfile).clip(CircleShape).background(Color.White).padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Создать профиль", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun OutboxCard() {
    var confirmDiscard by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF161617))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (SocialService.isFlushing) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = Theme.accent)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (SocialService.isFlushing) "Публикую тренировку…" else "Тренировка ждёт отправки",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            val message = SocialService.outboxError
            if (message != null && !SocialService.isFlushing) {
                Text(message, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
            }
        }
        if (!SocialService.isFlushing) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Отправить",
                    color = Theme.accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.pressable { SocialService.flushOutbox() }
                )
                // Сервер отказал — пост не застрянет в очереди навсегда.
                if (SocialService.outboxError != null) {
                    Text(
                        "Удалить",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.pressable { confirmDiscard = true }
                    )
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            containerColor = Theme.dialog,
            title = { Text("Не публиковать тренировку?", color = Theme.textPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Пост не уйдёт в ленту. Сама тренировка останется в приложении.", color = Theme.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    SocialService.outbox.forEach { SocialService.discard(it) }
                }) { Text("Убрать из очереди", color = Theme.record) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Отмена", color = Theme.textSecondary) } }
        )
    }
}

@Composable
private fun FeedEmpty(scope: SocialService.FeedScope, onFindPeople: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Filled.Groups, contentDescription = null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(36.dp))
        Text(
            if (scope == SocialService.FeedScope.ALL) "В ленте пока пусто" else "Здесь появятся тренировки тех, на кого ты подписан",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            if (scope == SocialService.FeedScope.ALL) "Закончи тренировку и опубликуй её — будешь первым." else "Найди друзей по юзернейму и подпишись.",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        if (scope == SocialService.FeedScope.FOLLOWING) {
            Text("Найти людей", color = Theme.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.pressable(onClick = onFindPeople))
        }
    }
}

// MARK: - Друзья

/// Вкладка «Друзья»: поиск по юзернейму, люди, которые недавно тренировались,
/// рейтинг недели и личные сообщения.
@Composable
fun CommunityScreen(profile: ProgramProfile, contentPadding: PaddingValues, onCreateProfile: () -> Unit) {
    val navigator = rememberSocialNavigator()
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    /// 0 — люди, 1 — рейтинг недели, 2 — личные сообщения.
    var section by rememberSaveable { mutableIntStateOf(0) }
    val people = remember { mutableStateListOf<SocialUserShort>() }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun search() {
        loading = true
        try {
            val found = SocialService.searchUsers(SocialFormat.normalizedUsername(query)).filterNot { it.isMe }
            people.clear()
            people.addAll(found)
            error = null
        } catch (e: SocialException) {
            error = e.message
        }
        loading = false
    }

    LaunchedEffect(query, SocialService.isSignedIn) {
        // Небольшая пауза: не дёргаем сервер на каждую букву.
        if (query.isNotEmpty()) delay(350)
        search()
    }

    SocialHost(navigator, screen = { route -> SocialRouteScreen(route, navigator, contentPadding, profile) }) {
        LazyColumn(
            Modifier.fillMaxSize().background(Color.Black),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { ScreenTitle("Друзья") }
            if (SocialService.isSignedIn) {
                item {
                    val unread = SocialService.unreadMessages
                    SegmentedControl(
                        options = listOf("Люди", "Рейтинг", if (unread > 0) "Чаты · $unread" else "Чаты"),
                        selectedIndex = section
                    ) { section = it }
                }
            }
            if (section == 1 && SocialService.isSignedIn) {
                item { LeaderboardSection { navigator.push(SocialRoute.User(it)) } }
                return@LazyColumn
            }
            if (section == 2 && SocialService.isSignedIn) {
                item { ChatList { navigator.push(SocialRoute.Chat(it)) } }
                return@LazyColumn
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1F1F1F))
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.45f))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("Найти по юзернейму", color = Color.White.copy(alpha = 0.4f), fontSize = 16.sp)
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it.take(24) },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                            cursorBrush = SolidColor(Theme.accent),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Очистить",
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.pressable { query = "" }
                        )
                    }
                }
            }
            if (!SocialService.isSignedIn) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .pressable(onClick = onCreateProfile)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF161617))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = Theme.accent)
                        Spacer(Modifier.width(12.dp))
                        Text("Создай профиль, чтобы подписываться на друзей", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.35f))
                    }
                }
            }
            item { SectionCaption(if (query.isEmpty()) "НЕДАВНО ТРЕНИРОВАЛИСЬ" else "НАЙДЕНО", Modifier.padding(top = 6.dp)) }
            val currentError = error
            if (currentError != null && people.isEmpty()) {
                item { SocialErrorCard(currentError) { scope.launch { search() } } }
            } else if (people.isEmpty() && !loading) {
                item {
                    Text(
                        if (query.isEmpty()) "Пока никого. Позови друзей в STRAIN — их юзернеймы появятся здесь." else "Никого с таким юзернеймом",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp
                    )
                }
            }
            if (people.isNotEmpty()) {
                item {
                    PeopleCard(people, onOpen = { navigator.push(SocialRoute.User(it.username)) }) { updated ->
                        val index = people.indexOfFirst { it.id == updated.id }
                        if (index >= 0) people[index] = updated
                    }
                }
            }
            if (loading && people.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp)) } }
            }
        }
    }
}

@Composable
private fun PeopleCard(people: List<SocialUserShort>, onOpen: (SocialUserShort) -> Unit, onChange: (SocialUserShort) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF161617))
    ) {
        people.forEachIndexed { index, person ->
            PersonRow(person, onOpen = { onOpen(person) }, onChange = onChange)
            if (index < people.size - 1) HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(start = 64.dp))
        }
    }
}

/// Человек в списке: аватар, юзернейм, о себе и кнопка подписки.
@Composable
fun PersonRow(person: SocialUserShort, onOpen: () -> Unit, onChange: (SocialUserShort) -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.weight(1f).pressable(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
            Avatar(person.avatar, person.username, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("@${person.username}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (person.bio.isNotBlank()) {
                    Text(person.bio, color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (SocialService.isSignedIn && !person.isMe) {
            FollowButton(person.isFollowing, compact = true) {
                if (busy) return@FollowButton
                busy = true
                val updated = person.copy(isFollowing = !person.isFollowing)
                onChange(updated)
                scope.launch {
                    try {
                        SocialService.setFollowing(person.username, updated.isFollowing)
                    } catch (e: SocialException) {
                        onChange(person)
                    } finally {
                        busy = false
                    }
                }
            }
        }
    }
}

/// Подписчики или подписки человека.
@Composable
fun PeopleListScreen(
    title: String,
    contentPadding: PaddingValues,
    navigator: SocialNavigator,
    load: suspend () -> List<SocialUserShort>
) {
    val scope = rememberCoroutineScope()
    val people = remember { mutableStateListOf<SocialUserShort>() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        loading = true
        try {
            people.clear()
            people.addAll(load())
            error = null
        } catch (e: SocialException) {
            error = e.message
        }
        loading = false
    }
    LaunchedEffect(Unit) { reload() }

    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SocialTopBar(title, onBack = { navigator.pop() }) }
        val currentError = error
        if (currentError != null) item { SocialErrorCard(currentError) { scope.launch { reload() } } }
        else if (people.isEmpty() && !loading) item { Text("Пока никого", color = Color.White.copy(alpha = 0.5f), fontSize = 15.sp) }
        if (people.isNotEmpty()) {
            item {
                PeopleCard(people, onOpen = { navigator.push(SocialRoute.User(it.username)) }) { updated ->
                    val index = people.indexOfFirst { it.id == updated.id }
                    if (index >= 0) people[index] = updated
                }
            }
        }
        if (loading) item { Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp)) } }
    }
}
