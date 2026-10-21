package com.texasprogram.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShortText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.AchievementCalculator
import com.texasprogram.app.model.AchievementGroup
import com.texasprogram.app.model.AchievementKind
import com.texasprogram.app.model.AchievementProgress
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.SocialBadge
import com.texasprogram.app.model.SocialFormat
import com.texasprogram.app.model.SocialPost
import com.texasprogram.app.model.SocialProgramBuilder
import com.texasprogram.app.model.SocialProgramDay
import com.texasprogram.app.model.SocialUser
import com.texasprogram.app.model.formatWeight
import com.texasprogram.app.service.RuPlural
import com.texasprogram.app.service.SocialException
import com.texasprogram.app.service.SocialService
import kotlinx.coroutines.launch

// MARK: - Вкладка «Профиль»

/// Свой профиль. Настройки приложения — за шестерёнкой справа вверху.
@Composable
fun ProfileTabScreen(profile: ProgramProfile, contentPadding: PaddingValues, onSettings: () -> Unit) {
    val navigator = rememberSocialNavigator()
    val context = LocalContext.current
    val posts = remember { mutableStateListOf<SocialPost>() }
    var next by remember { mutableStateOf<Double?>(null) }
    var loadingPosts by remember { mutableStateOf(false) }
    var postsError by remember { mutableStateOf<String?>(null) }
    val me = SocialService.me
    val scope = rememberCoroutineScope()

    fun replace(id: String, value: SocialPost?) {
        val index = posts.indexOfFirst { it.id == id }
        if (index < 0) return
        if (value != null) posts[index] = value else posts.removeAt(index)
    }

    LaunchedEffect(me?.username, SocialService.feedVersion) {
        val username = me?.username ?: return@LaunchedEffect
        loadingPosts = true
        try {
            val page = SocialService.posts(username)
            posts.clear()
            posts.addAll(page.posts)
            next = page.next
            postsError = null
        } catch (e: SocialException) {
            postsError = e.message
        }
        loadingPosts = false
    }
    LaunchedEffect(SocialService.isSignedIn) {
        if (SocialService.isSignedIn && SocialService.me == null) SocialService.refreshMe()
        SocialService.scheduleSync(profile)
    }

    val progress = remember(profile) { AchievementCalculator.progress(profile) }
    val programs = remember(profile) { SocialProgramBuilder.days(profile) }

    SocialHost(navigator, screen = { route ->
        SocialRouteScreen(route, navigator, contentPadding, profile, onPostChange = ::replace)
    }) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            when {
                me != null -> ProfileContent(
                    user = me,
                    badges = AchievementCalculator.badges(progress),
                    achievedCount = progress.count { it.level > 0 },
                    programs = programs,
                    posts = posts,
                    loadingPosts = loadingPosts,
                    postsError = postsError,
                    navigator = navigator,
                    contentPadding = contentPadding,
                    onAchievements = { navigator.push(SocialRoute.Achievements) },
                    onLoadMore = {
                        val cursor = next
                        if (cursor != null && !loadingPosts) {
                            loadingPosts = true
                            scope.launch {
                                runCatching { SocialService.posts(me.username, cursor) }.onSuccess { page ->
                                    // Повтор поста дал бы два одинаковых ключа — LazyColumn падает.
                                    val known = posts.map { it.id }.toSet()
                                    posts.addAll(page.posts.filter { it.id !in known })
                                    next = page.next
                                }
                                loadingPosts = false
                            }
                        }
                    },
                    onPostChange = ::replace,
                    primaryAction = {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .pressable { navigator.push(SocialRoute.EditProfile) }
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFF212121)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Редактировать", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                )
                SocialService.isSignedIn -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
                else -> SignUpScreen(contentPadding)
            }

            // Поделиться и настройки — поверх шапки, как кнопки в REBRN.
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = contentPadding.calculateTopPadding(), end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (me != null) {
                    RoundIcon(Icons.Filled.Share, "Поделиться профилем") {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Я тренируюсь в STRAIN — найди меня: @${me.username}")
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                    }
                }
                RoundIcon(Icons.Filled.Settings, "Настройки", onSettings)
            }
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).pressable(onClick = onClick).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

// MARK: - Чужой профиль

@Composable
fun UserProfileScreen(username: String, navigator: SocialNavigator, contentPadding: PaddingValues) {
    val scope = rememberCoroutineScope()
    // Вернулись «Назад» из поста — профиль сразу на месте, свежий подгрузится следом.
    val cached = remember(username) { SocialService.userCache[username] }
    var user by remember { mutableStateOf(cached?.first) }
    val posts = remember { mutableStateListOf<SocialPost>().apply { cached?.let { addAll(it.second.posts) } } }
    var next by remember { mutableStateOf(cached?.second?.next) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun reload() {
        loading = true
        try {
            val loaded = SocialService.user(username)
            val page = SocialService.posts(username)
            user = loaded
            posts.clear()
            posts.addAll(page.posts)
            next = page.next
            error = null
            SocialService.userCache[username] = loaded to page
        } catch (e: SocialException) {
            error = e.message
        } finally {
            loading = false
        }
    }
    LaunchedEffect(username) { reload() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val current = user
        when {
            current != null -> ProfileContent(
                user = current,
                badges = current.achievements,
                achievedCount = current.achievements.size,
                programs = current.programs,
                posts = posts,
                loadingPosts = loading,
                postsError = null,
                navigator = navigator,
                contentPadding = contentPadding,
                onAchievements = { navigator.push(SocialRoute.Badges(current.achievements)) },
                onLoadMore = {
                    val cursor = next
                    if (cursor != null && !loading) {
                        loading = true
                        scope.launch {
                            runCatching { SocialService.posts(username, cursor) }.onSuccess { page ->
                                val known = posts.map { it.id }.toSet()
                                posts.addAll(page.posts.filter { it.id !in known })
                                next = page.next
                            }
                            loading = false
                        }
                    }
                },
                onPostChange = { id, value ->
                    val index = posts.indexOfFirst { it.id == id }
                    if (index >= 0) { if (value != null) posts[index] = value else posts.removeAt(index) }
                },
                primaryAction = {
                    if (!current.isMe && SocialService.isSignedIn) Column {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FollowButton(current.isFollowing, modifier = Modifier.weight(1f)) {
                            if (busy) return@FollowButton
                            busy = true
                            val optimistic = current.copy(
                                isFollowing = !current.isFollowing,
                                followers = current.followers + if (current.isFollowing) -1 else 1
                            )
                            user = optimistic
                            scope.launch {
                                try {
                                    user = SocialService.setFollowing(username, optimistic.isFollowing)
                                } catch (e: SocialException) {
                                    user = current
                                } finally {
                                    busy = false
                                }
                            }
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .height(50.dp)
                                .pressable { navigator.push(SocialRoute.Chat(username)) }
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFF292929)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Написать", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        }
                        Spacer(Modifier.height(10.dp))
                        // Приглашение уходит карточкой в переписку — туда и переходим.
                        InviteButton(username) { navigator.push(SocialRoute.Chat(username)) }
                    }
                }
            )
            error != null -> Column(Modifier.padding(contentPadding)) {
                SocialTopBar("@$username", onBack = { navigator.pop() })
                SocialErrorCard(error!!) { scope.launch { reload() } }
            }
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
        }
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(top = contentPadding.calculateTopPadding(), start = 16.dp)
        ) {
            RoundIcon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") { navigator.pop() }
        }
    }
}

// MARK: - Содержимое профиля

@Composable
fun ProfileContent(
    user: SocialUser,
    badges: List<SocialBadge>,
    achievedCount: Int,
    programs: List<SocialProgramDay>,
    posts: List<SocialPost>,
    loadingPosts: Boolean,
    postsError: String?,
    navigator: SocialNavigator,
    contentPadding: PaddingValues,
    onAchievements: () -> Unit,
    onLoadMore: () -> Unit,
    onPostChange: (String, SocialPost?) -> Unit,
    primaryAction: @Composable () -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = contentPadding.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item(key = "header") { ProfileHeader(user, contentPadding) }
        item(key = "stats") {
            Row(Modifier.fillMaxWidth()) {
                SocialStat("${user.workouts}", "Тренировки", Modifier.weight(1f))
                SocialStat("${user.followers}", "Подписчики", Modifier.weight(1f).pressable { navigator.push(SocialRoute.Followers(user.username)) })
                SocialStat("${user.following}", "Подписки", Modifier.weight(1f).pressable { navigator.push(SocialRoute.Following(user.username)) })
            }
        }
        item(key = "action") { primaryAction() }
        item(key = "achievements") { AchievementsCard(badges, achievedCount, onAchievements) }
        item(key = "tabs") {
            SegmentedControl(options = listOf("Тренировки", "Программы"), selectedIndex = tab) { tab = it }
        }
        if (tab == 0) {
            if (postsError != null && posts.isEmpty()) item(key = "postsError") { SocialErrorCard(postsError) }
            else if (posts.isEmpty() && !loadingPosts) {
                item(key = "noPosts") {
                    Text(
                        if (user.isMe) "Опубликуй тренировку в конце занятия — она появится здесь." else "Тренировок пока нет",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
                    )
                }
            }
            items(posts, key = { it.id }) { post ->
                Column {
                    PostCard(
                        post = post,
                        onOpen = { navigator.push(SocialRoute.Post(post)) },
                        onOpenAuthor = {},
                        onChange = { onPostChange(post.id, it) },
                        showsWorkout = true
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
                }
                if (post.id == posts.lastOrNull()?.id) LaunchedEffect(post.id) { onLoadMore() }
            }
            if (loadingPosts && posts.isEmpty()) {
                item(key = "postsLoading") {
                    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp)) }
                }
            }
        } else {
            if (programs.isEmpty()) {
                item(key = "noPrograms") {
                    Text("Программа не выбрана", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp, modifier = Modifier.padding(vertical = 20.dp))
                }
            }
            items(programs.size, key = { "program-$it" }) { index ->
                val day = programs[index]
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressable { navigator.push(SocialRoute.Program(day, programs, if (user.isMe) null else user.username)) }
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF161617))
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(day.title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black)
                        if (day.groups.isNotEmpty()) Text(day.groups.joinToString(" · "), color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "${day.exercises.size} ${RuPlural.word(day.exercises.size, "упражнение", "упражнения", "упражнений")} · " +
                                "${day.setCount} ${RuPlural.word(day.setCount, "подход", "подхода", "подходов")}",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 14.sp
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.35f))
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(user: SocialUser, contentPadding: PaddingValues) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        // Обложка — от края до края экрана, сквозь боковые отступы списка.
        Box(Modifier.bleed(16.dp).fillMaxWidth().height(contentPadding.calculateTopPadding() + 230.dp)) {
            ProfileCover(
                user.cover,
                Modifier
                    .fillMaxWidth()
                    .height(contentPadding.calculateTopPadding() + 170.dp)
            )
            Avatar(
                user.avatar,
                user.username,
                118.dp,
                Modifier
                    .align(Alignment.BottomCenter)
                    .border(4.dp, Color.Black, CircleShape)
            )
        }
        Text("@${user.username}", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
        user.live?.let { LiveBadge(it) }
        user.bodyLine?.let { Text(it, color = Color.White.copy(alpha = 0.55f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        if (user.bio.isNotBlank()) {
            Text(user.bio, color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp, textAlign = TextAlign.Center)
        }
    }
}

/// Растягивает элемент списка на `horizontal` за его боковые отступы.
private fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val extra = (horizontal * 2).roundToPx()
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra)
    )
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}

/// Обложка профиля: своё фото или фирменный узор STRAIN.
@Composable
fun ProfileCover(imageId: String?, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(bottomStart = 0.dp, bottomEnd = 0.dp))) {
        if (imageId != null) {
            RemoteImage(imageId, Modifier.fillMaxSize(), ContentScale.Crop)
        } else {
            Column(
                Modifier.fillMaxSize().background(Color.Black).padding(top = 12.dp),
                horizontalAlignment = Alignment.End
            ) {
                repeat(4) { row ->
                    Text(
                        "STRAINSTRAIN",
                        style = TextStyle(
                            brush = Brush.horizontalGradient(
                                listOf(Theme.accent.copy(alpha = 0.85f - row * 0.2f), Theme.accentDeep.copy(alpha = 0.4f - row * 0.08f))
                            ),
                            fontSize = 64.sp,
                            lineHeight = 50.sp,
                            fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Italic
                        ),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.offset(x = (row * 26 + 90).dp)
                    )
                }
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))))
    }
}

@Composable
private fun AchievementsCard(badges: List<SocialBadge>, achievedCount: Int, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF161617))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ДОСТИЖЕНИЯ", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, modifier = Modifier.weight(1f))
            Text("Все $achievedCount", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
        }
        if (badges.isEmpty()) {
            Text("Первая медаль — за первую тренировку", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        } else {
            Row(Modifier.fillMaxWidth()) {
                badges.forEach { badge ->
                    val kind = AchievementKind.byId(badge.id)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MedalView(kind, badge.level, 62.dp)
                        Text(kind?.title ?: badge.id, color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }
    }
}

// MARK: - Регистрация

@Composable
fun SignUpScreen(contentPadding: PaddingValues) {
    val scope = rememberCoroutineScope()
    var username by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var showingCode by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val normalized = SocialFormat.normalizedUsername(username)
    val valid = SocialFormat.isValidUsername(normalized)

    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(26.dp))) {
                ProfileCover(null, Modifier.fillMaxSize())
                Text(
                    "Профиль STRAIN",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.BottomStart).padding(18.dp)
                )
            }
        }
        item {
            Text(
                "Публикуй тренировки с фото, подписывайся на друзей, ставь лайки и пиши комментарии. Свои веса и программа видны в профиле.",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 15.sp
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionCaption("ЮЗЕРНЕЙМ")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1F1F1F))
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("@", color = Color.White.copy(alpha = 0.4f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.weight(1f)) {
                        if (username.isEmpty()) Text("kelw1n", color = Color.White.copy(alpha = 0.3f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        BasicTextField(
                            value = username,
                            onValueChange = { username = it.take(24) },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                            cursorBrush = SolidColor(Theme.accent),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Text("Латиница, цифры, точка и подчёркивание, от 3 до 24 символов. Поменять можно потом.", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
            }
        }
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .pressable(enabled = valid && !busy) {
                        busy = true
                        error = null
                        scope.launch {
                            try {
                                SocialService.register(normalized)
                            } catch (e: SocialException) {
                                error = e.message
                            } finally {
                                busy = false
                            }
                        }
                    }
                    .clip(CircleShape)
                    .background(if (valid) Color.White else Color(0xFF666666)),
                contentAlignment = Alignment.Center
            ) {
                if (busy) CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                else Text("Создать профиль", color = Color.Black, fontSize = 17.sp, fontWeight = FontWeight.Black)
            }
        }
        error?.let { message -> item { Text(message, color = Theme.warning, fontSize = 14.sp) } }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (showingCode) "У меня уже есть профиль ▴" else "У меня уже есть профиль ▾",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.pressable { showingCode = !showingCode }
                )
                if (showingCode) {
                    Text(
                        "Код входа есть в профиле на старом телефоне («Редактировать» → «Код входа»). Он же лежит в резервной копии STRAIN.",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 13.sp
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF1F1F1F))
                            .padding(12.dp)
                    ) {
                        if (code.isEmpty()) Text("Код входа", color = Color.White.copy(alpha = 0.3f), fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                        BasicTextField(
                            value = code,
                            onValueChange = { code = it.take(80) },
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontFamily = FontFamily.Monospace),
                            cursorBrush = SolidColor(Theme.accent),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Text(
                        "Войти по коду",
                        color = Theme.accent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.pressable(enabled = code.isNotBlank() && !busy) {
                            busy = true
                            error = null
                            scope.launch {
                                try {
                                    SocialService.signIn(code)
                                } catch (e: SocialException) {
                                    error = e.message
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

// MARK: - Редактирование

@Composable
fun EditProfileScreen(profile: ProgramProfile, contentPadding: PaddingValues, navigator: SocialNavigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val me = SocialService.me
    var username by rememberSaveable { mutableStateOf(me?.username.orEmpty()) }
    var bio by rememberSaveable { mutableStateOf(me?.bio.orEmpty()) }
    var height by rememberSaveable { mutableStateOf(me?.heightCm?.toString().orEmpty()) }
    var showBody by rememberSaveable { mutableStateOf(me?.showBody ?: true) }
    var saving by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var avatarMenu by remember { mutableStateOf(false) }
    var showingCode by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun upload(uri: Uri, avatar: Boolean) {
        uploading = true
        error = null
        scope.launch {
            try {
                val id = SocialService.uploadImage(context, uri, if (avatar) 600 else 1280)
                SocialService.update(if (avatar) SocialService.ProfilePatch(avatar = id) else SocialService.ProfilePatch(cover = id))
            } catch (e: SocialException) {
                error = e.message
            } finally {
                uploading = false
            }
        }
    }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { upload(it, avatar = true) } }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { upload(it, avatar = false) } }
    val camera = rememberCameraLauncher { upload(it, avatar = true) }

    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            SocialTopBar("Профиль", onBack = { navigator.pop() }) {
                Text(
                    if (saving) "…" else "Готово",
                    color = Theme.accent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.pressable(enabled = !saving && !uploading) {
                        val name = SocialFormat.normalizedUsername(username)
                        if (!SocialFormat.isValidUsername(name)) {
                            error = "Юзернейм: 3–24 символа — латиница, цифры, точка и подчёркивание"
                            return@pressable
                        }
                        val heightValue = height.trim().toIntOrNull()?.takeIf { it in 100..250 }
                        if (height.isNotBlank() && heightValue == null) {
                            error = "Рост — от 100 до 250 см или пусто"
                            return@pressable
                        }
                        saving = true
                        error = null
                        scope.launch {
                            try {
                                SocialService.update(
                                    SocialService.ProfilePatch(
                                        username = name.takeIf { it != me?.username },
                                        bio = bio.trim(),
                                        heightCm = heightValue,
                                        clearHeight = height.isBlank(),
                                        showBody = showBody,
                                        weightKg = profile.latestBodyWeight
                                    )
                                )
                                navigator.pop()
                            } catch (e: SocialException) {
                                error = e.message
                            } finally {
                                saving = false
                            }
                        }
                    }
                )
            }
        }
        item {
            Box(Modifier.fillMaxWidth().height(210.dp)) {
                // Тонкая рамка: чёрная обложка на чёрном фоне иначе сливается с экраном.
                ProfileCover(
                    me?.cover,
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
                )
                Row(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .pressable { coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Image, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Изменить обложку", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.align(Alignment.BottomCenter)) {
                    Box(Modifier.pressable { avatarMenu = true }) {
                        Avatar(me?.avatar, me?.username ?: "s", 104.dp, Modifier.border(4.dp, Color.Black, CircleShape))
                        if (uploading) CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center).size(28.dp))
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF262626))
                                .border(3.dp, Color.Black, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = "Сменить фото", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                    DropdownMenu(expanded = avatarMenu, onDismissRequest = { avatarMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Снять фото") },
                            leadingIcon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
                            onClick = {
                                avatarMenu = false
                                camera.launch()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Выбрать из галереи") },
                            leadingIcon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                            onClick = {
                                avatarMenu = false
                                avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        )
                        if (me?.avatar != null) {
                            DropdownMenuItem(
                                text = { Text("Убрать фото", color = Theme.record) },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = Theme.record) },
                                onClick = {
                                    avatarMenu = false
                                    scope.launch {
                                        try {
                                            SocialService.update(SocialService.ProfilePatch(avatar = ""))
                                        } catch (e: SocialException) {
                                            error = e.message
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF161617))
            ) {
                EditRow(Icons.Filled.AlternateEmail, "Юзернейм") {
                    InlineField(username, "юзернейм", KeyboardType.Ascii) { username = it.take(24) }
                }
                EditDivider()
                EditRow(Icons.Filled.ShortText, "О себе") {
                    InlineField(bio, "Не заполнено", KeyboardType.Text) { bio = it.take(160) }
                }
                EditDivider()
                EditRow(Icons.Filled.Height, "Рост, см") {
                    InlineField(height, "—", KeyboardType.Number) { value -> height = value.filter { it.isDigit() }.take(3) }
                }
                EditDivider()
                EditRow(Icons.Filled.MonitorWeight, "Вес") {
                    Text(profile.latestBodyWeight?.let { "${formatWeight(it)} кг" } ?: "из «Замеров веса»", color = Color.White.copy(alpha = 0.55f), fontSize = 16.sp)
                }
                EditDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Показывать рост и вес", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("В шапке профиля. Сила к весу в достижениях видна всегда.", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                    }
                    Switch(checked = showBody, onCheckedChange = { showBody = it }, colors = SwitchDefaults.colors(checkedTrackColor = Theme.accent))
                }
                EditDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Показывать, что я тренируюсь", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("Подписчики увидят упражнение и подходы, пока идёт тренировка.", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                    }
                    Switch(
                        checked = SocialService.sharesLive,
                        onCheckedChange = { SocialService.updateSharesLive(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Theme.accent)
                    )
                }
            }
        }
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF161617))
            ) {
                Row(
                    Modifier.fillMaxWidth().pressable { showingCode = !showingCode }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Key, contentDescription = null, tint = Theme.accent)
                    Spacer(Modifier.width(12.dp))
                    Text("Код входа на другом телефоне", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                }
                val token = SocialService.token
                if (showingCode && token != null) {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(token, color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        Row(
                            Modifier.pressable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Код входа STRAIN", token))
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = Theme.accent, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Скопировать", color = Theme.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "С этим кодом в профиль может войти кто угодно — не публикуй его. Он также сохраняется в резервной копии.",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 12.sp
                        )
                    }
                }
                EditDivider()
                Text(
                    "Выйти из профиля",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().pressable { confirmSignOut = true }.padding(16.dp)
                )
                EditDivider()
                Text(
                    "Удалить профиль",
                    color = Theme.record,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().pressable { confirmDelete = true }.padding(16.dp)
                )
            }
        }
        error?.let { message -> item { Text(message, color = Theme.warning, fontSize = 14.sp) } }
    }

    if (confirmSignOut) {
        AlertDialog(
            modifier = Modifier.popIn(),
            onDismissRequest = { confirmSignOut = false },
            containerColor = Theme.dialog,
            title = { Text("Выйти из профиля на этом телефоне?", color = Theme.textPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Профиль останется на сервере. Вернуться можно по коду входа — сохрани его перед выходом.", color = Theme.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    SocialService.signOut()
                    navigator.pop()
                }) { Text("Выйти", color = Theme.record) }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Отмена", color = Theme.textSecondary) } }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            modifier = Modifier.popIn(),
            onDismissRequest = { confirmDelete = false },
            containerColor = Theme.dialog,
            title = { Text("Удалить профиль навсегда?", color = Theme.textPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Пропадут все посты, фото, лайки и комментарии. Тренировки в приложении останутся.", color = Theme.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        try {
                            SocialService.deleteAccount()
                            navigator.pop()
                        } catch (e: SocialException) {
                            error = e.message
                        }
                    }
                }) { Text("Удалить", color = Theme.record) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена", color = Theme.textSecondary) } }
        )
    }
}

@Composable
private fun EditRow(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { content() }
    }
}

@Composable
private fun InlineField(value: String, placeholder: String, type: KeyboardType, onChange: (String) -> Unit) {
    Box(contentAlignment = Alignment.CenterEnd) {
        if (value.isEmpty()) Text(placeholder, color = Color.White.copy(alpha = 0.35f), fontSize = 16.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp, textAlign = TextAlign.End),
            cursorBrush = SolidColor(Theme.accent),
            keyboardOptions = KeyboardOptions(keyboardType = type),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun EditDivider() {
    HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(start = 50.dp))
}

// MARK: - Достижения

@Composable
fun AchievementsScreen(progress: List<AchievementProgress>, contentPadding: PaddingValues, navigator: SocialNavigator) {
    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { SocialTopBar("Достижения", onBack = { navigator.pop() }) }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF161617))
                    .padding(20.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text("${progress.count { it.level > 0 }}", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Black)
                Text(" из ${progress.size}", color = Color.White.copy(alpha = 0.5f), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
                Spacer(Modifier.weight(1f))
                Text("ВЗЯТО", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, modifier = Modifier.padding(bottom = 12.dp))
            }
        }
        AchievementGroup.entries.forEach { group ->
            val items = progress.filter { it.kind.group == group }
            item(key = group.name) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row {
                        Text(group.title, color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, modifier = Modifier.weight(1f))
                        Text("${items.count { it.level > 0 }}/${items.size}", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0xFF161617))) {
                        items.forEachIndexed { index, item ->
                            AchievementRow(item)
                            if (index < items.size - 1) HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(start = 92.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AchievementRow(item: AchievementProgress) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        MedalView(item.kind, item.level, 60.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.kind.title, color = if (item.level > 0) Color.White else Color.White.copy(alpha = 0.6f), fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                Text(if (item.level > 0) "${item.level}/${item.kind.thresholds.size}" else "—", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            LevelTrack(item.kind.thresholds.size, item.level, item.segmentFraction.toFloat())
            Text(item.caption, color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp)
        }
    }
}

/// Шкала уровней: точки-пороги и отрезки между ними; текущий отрезок заполнен частично.
@Composable
private fun LevelTrack(levels: Int, reached: Int, fraction: Float) {
    val accent = Theme.accent
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(12.dp)) {
        val step = size.width / levels
        val y = size.height / 2
        drawLine(Color.White.copy(alpha = 0.1f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        val filled = step * (reached + if (reached < levels) fraction else 0f)
        if (filled > 0) drawLine(accent, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(filled, y), strokeWidth = 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        for (index in 1..levels) {
            val radius = if (index == reached) 6.dp.toPx() else 4.dp.toPx()
            val x = (step * index).coerceAtMost(size.width - radius)
            drawCircle(if (index <= reached) accent else Color(0xFF404040), radius, androidx.compose.ui.geometry.Offset(x, y))
        }
    }
}

/// Медали чужого профиля: уровни без подробностей.
@Composable
fun BadgeListScreen(badges: List<SocialBadge>, contentPadding: PaddingValues, navigator: SocialNavigator) {
    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SocialTopBar("Достижения", onBack = { navigator.pop() }) }
        if (badges.isEmpty()) item { Text("Медалей пока нет", color = Color.White.copy(alpha = 0.5f), fontSize = 15.sp) }
        items(badges.chunked(3)) { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { badge ->
                    val kind = AchievementKind.byId(badge.id)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MedalView(kind, badge.level, 76.dp)
                        Text(kind?.title ?: badge.id, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Уровень ${badge.level}", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// MARK: - День программы

@Composable
fun ProgramDayScreen(
    day: SocialProgramDay,
    contentPadding: PaddingValues,
    navigator: SocialNavigator,
    allDays: List<SocialProgramDay> = emptyList(),
    /// Чья программа; `null` — своя, копировать нечего.
    author: String? = null
) {
    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SocialTopBar(day.title, onBack = { navigator.pop() }) }
        if (day.groups.isNotEmpty()) item { Text(day.groups.joinToString(" · "), color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0xFF161617))) {
                day.exercises.forEachIndexed { index, exercise ->
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        ExerciseThumb(exercise.name, 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(exercise.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("${exercise.sets} × ${exercise.reps}", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                        }
                        Text(exercise.load, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                    }
                    if (index < day.exercises.size - 1) HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(start = 70.dp))
                }
            }
        }
        if (author != null) {
            item { CopyProgramButton(allDays.ifEmpty { listOf(day) }, author, navigator) }
        }
    }
}
