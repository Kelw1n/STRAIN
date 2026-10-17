package com.texasprogram.app

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.texasprogram.app.model.AchievementCalculator
import com.texasprogram.app.model.ProgramInput
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.SocialBadge
import com.texasprogram.app.model.SocialPost
import com.texasprogram.app.model.SocialProgramBuilder
import com.texasprogram.app.model.SocialUser
import com.texasprogram.app.service.SocialService
import com.texasprogram.app.ui.AchievementsScreen
import com.texasprogram.app.ui.CommunityScreen
import com.texasprogram.app.ui.EditProfileScreen
import com.texasprogram.app.ui.FeedScreen
import com.texasprogram.app.ui.FollowButton
import com.texasprogram.app.ui.LiquidGlassTabBar
import com.texasprogram.app.ui.MainTabItem
import com.texasprogram.app.ui.PostCard
import com.texasprogram.app.ui.PostDetailScreen
import com.texasprogram.app.ui.ProfileContent
import com.texasprogram.app.ui.ProgramDayScreen
import com.texasprogram.app.ui.SignUpScreen
import com.texasprogram.app.ui.SocialNavigator
import com.texasprogram.app.ui.Theme
import com.texasprogram.app.ui.WorkoutCompletionSummaryScreen
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/// Снимки экранов ленты и профиля: по ним на CI видно вёрстку — ничего не
/// вылезает за края и не прячется под панелью вкладок. Запускаются только
/// workflow `snapshots.yml` (`gradle -Psnapshots recordPaparazziDebug`).
class SnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.NoActionBar",
        maxPercentDifference = 100.0
    )

    /// Отступы как у вкладок в MainActivity: строка состояния сверху, панель вкладок снизу.
    private val padding = PaddingValues(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 134.dp)

    private val profile = ProgramProfile.texas(ProgramInput.demo)

    /// Высокий кадр — чтобы увидеть экран целиком, а не только первый экран телефона.
    private fun tall(height: Int = 7000) {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = height))
    }

    @Test
    fun feedTab() = paparazzi.snapshot("01-feed-tab") {
        Screen {
            FeedScreen(profile, padding, onFindPeople = {}, onCreateProfile = {})
            LiquidGlassTabBar(MainTabItem.TODAY, onTabSelected = {}, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    @Test
    fun feedPosts() = paparazzi.snapshot("02-feed-posts") {
        Screen {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
                items(Fixtures.posts(), key = { it.id }) { post ->
                    Column {
                        PostCard(post, onOpen = {}, onOpenAuthor = {}, onChange = {})
                        HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
                    }
                }
            }
            LiquidGlassTabBar(MainTabItem.TODAY, onTabSelected = {}, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    /// Узкий телефон 360 dp: четыре показателя с крупными числами должны влезть.
    @Test
    fun feedPostsNarrow() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5)
        paparazzi.snapshot("02b-feed-posts-narrow") {
            Screen {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
                    items(Fixtures.posts(), key = { it.id }) { post ->
                        Column {
                            PostCard(post, onOpen = {}, onOpenAuthor = {}, onChange = {})
                            HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun postDetail() {
        tall()
        paparazzi.snapshot("03-post-detail") {
            Screen { PostDetailScreen(Fixtures.posts()[2], padding, SocialNavigator(), onChange = {}) }
        }
    }

    @Test
    fun profile() {
        val content: @Composable () -> Unit = {
            ProfileContent(
                user = Fixtures.user(),
                badges = listOf(SocialBadge("path", 2), SocialBadge("streak", 3), SocialBadge("bench", 1)),
                achievedCount = 3,
                programs = SocialProgramBuilder.days(profile),
                posts = Fixtures.posts(),
                loadingPosts = false,
                postsError = null,
                navigator = SocialNavigator(),
                contentPadding = padding,
                onAchievements = {},
                onLoadMore = {},
                onPostChange = { _, _ -> },
                primaryAction = { FollowButton(isFollowing = false) {} }
            )
        }
        paparazzi.snapshot("04-profile") { Screen { content() } }
        tall()
        paparazzi.snapshot("05-profile-full") { Screen { content() } }
    }

    @Test
    fun signUp() = paparazzi.snapshot("06-signup") { Screen { SignUpScreen(padding) } }

    @Test
    fun editProfile() {
        tall(4500)
        paparazzi.snapshot("07-edit-profile") { Screen { EditProfileScreen(profile, padding, SocialNavigator()) } }
    }

    @Test
    fun achievements() {
        tall()
        val done = profile.toggleCompleted(1, 1, LocalDate.now())
        paparazzi.snapshot("08-achievements") {
            Screen { AchievementsScreen(AchievementCalculator.progress(done), padding, SocialNavigator()) }
        }
    }

    @Test
    fun community() = paparazzi.snapshot("09-community") {
        Screen { CommunityScreen(profile, padding, onCreateProfile = {}) }
    }

    @Test
    fun programDay() = paparazzi.snapshot("10-program-day") {
        Screen { ProgramDayScreen(SocialProgramBuilder.days(profile).first(), padding, SocialNavigator()) }
    }

    /// Переписка: пузыри своих и чужих сообщений и список переписок.
    @Test
    fun chat() {
        val now = System.currentTimeMillis().toDouble()
        val messages = listOf(
            com.texasprogram.app.model.SocialMessage("1", "Го сегодня в зал?", false, now - 3_600_000),
            com.texasprogram.app.model.SocialMessage("2", "Давай в 7. Сегодня присед 140 на 5, подстрахуешь?", true, now - 3_500_000, read = true),
            com.texasprogram.app.model.SocialMessage(
                "3", "Конечно 💪", false, now - 3_400_000,
                reactions = listOf(com.texasprogram.app.model.SocialReaction("🔥", 1, true)),
                replyTo = com.texasprogram.app.model.SocialMessageQuote("2", "Давай в 7. Сегодня присед 140 на 5, подстрахуешь?", fromMe = true)
            ),
            com.texasprogram.app.model.SocialMessage(
                "4", "Возьми пояс", true, now - 60_000,
                reactions = listOf(com.texasprogram.app.model.SocialReaction("👍", 1, false), com.texasprogram.app.model.SocialReaction("💪", 2, true))
            )
        )
        val chats = SocialService.json.decodeFromString<List<com.texasprogram.app.model.SocialChat>>(
            """[{"user":{"id":"u2","username":"bro.lifts"},"last":{"id":"m1","text":"Возьми пояс","fromMe":false,"createdAt":${now - 60_000}},"unread":2},
               {"user":{"id":"u3","username":"heavy.anna"},"last":{"id":"m2","text":"Скинь программу","fromMe":true,"createdAt":${now - 86_400_000}},"unread":0,
                "live":{"title":"День 2","exercise":"Становая тяга","done":2,"total":5,"startedAt":${now - 1_800_000}}}]"""
        )
        paparazzi.snapshot("15-chat") {
            Screen {
                Column(Modifier.padding(padding), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                    com.texasprogram.app.ui.ChatTopBar("bro.lifts", null, "печатает…" to com.texasprogram.app.ui.Theme.accent, online = true, onBack = {}, onOpenProfile = {})
                    messages.forEach { com.texasprogram.app.ui.MessageBubble(it, username = "bro.lifts", onCopy = {}, onDelete = {}) }
                    com.texasprogram.app.ui.TypingBubble()
                    chats.forEach { com.texasprogram.app.ui.ChatRow(it) {} }
                }
            }
        }
        paparazzi.snapshot("16-chat-screen") {
            Screen { com.texasprogram.app.ui.ChatScreen("bro.lifts", padding, SocialNavigator()) }
        }
    }

    /// Тренируются сейчас, колокольчик активности и рейтинг недели.
    @Test
    fun socialExtras() {
        tall(4400)
        val now = System.currentTimeMillis().toDouble()
        val week = com.texasprogram.app.model.WeeklyStatsBuilder.weekKey(LocalDate.now())
        val json = SocialService.json
        val live = json.decodeFromString<List<com.texasprogram.app.model.SocialLiveFriend>>(
            """[{"user":{"id":"u2","username":"bro.lifts"},"live":{"title":"День 1 · Объём","exercise":"Приседания","done":3,"total":5,"setsDone":3,"setsTotal":18,"startedAt":${now - 1_560_000}}},
               {"user":{"id":"u3","username":"heavy.anna"},"live":{"title":"День 2","exercise":"Жим лёжа с очень длинным названием","done":1,"total":8,"startedAt":${now - 300_000}}}]"""
        )
        val activity = json.decodeFromString<com.texasprogram.app.model.SocialActivityPage>(
            """{"items":[
                 {"kind":"like","actor":{"id":"u2","username":"bro.lifts"},"postId":"p1","postTitle":"День 1 · Объём","createdAt":${now - 120_000},"unseen":true},
                 {"kind":"reply","actor":{"id":"u3","username":"heavy.anna"},"postId":"p1","postTitle":"День 1","text":"Спасибо, попробую с поясом!","createdAt":${now - 3_600_000},"unseen":true},
                 {"kind":"comment","actor":{"id":"u4","username":"semobsessed"},"postId":"p2","postTitle":"День 3 · Сила","text":"Мощно 🔥 сколько отдыхал между подходами?","createdAt":${now - 7_200_000}},
                 {"kind":"follow","actor":{"id":"u5","username":"tim"},"createdAt":${now - 90_000_000}}],"unseen":2}"""
        )
        val board = json.decodeFromString<List<com.texasprogram.app.model.SocialLeaderboardEntry>>(
            """[{"user":{"id":"u1","username":"kelw1n","isMe":true},"stats":{"week":"$week","workouts":3,"volumeKg":14250,"records":2,"streakWeeks":6}},
               {"user":{"id":"u2","username":"bro.lifts"},"stats":{"week":"$week","workouts":4,"volumeKg":18900,"records":1,"streakWeeks":3}},
               {"user":{"id":"u3","username":"heavy.anna"},"stats":{"week":"$week","workouts":2,"volumeKg":7300,"records":3,"streakWeeks":12}},
               {"user":{"id":"u4","username":"semobsessed"},"stats":{}}]"""
        )
        val metric = com.texasprogram.app.model.LeaderboardMetric.VOLUME
        val together = json.decodeFromString<com.texasprogram.app.model.SocialTogetherState>(
            """{"incoming":[{"id":"t1","status":"pending","fromMe":false,"partner":{"id":"u3","username":"heavy.anna"}}],
               "active":{"id":"t2","status":"accepted","fromMe":true,"partner":{"id":"u2","username":"bro.lifts"},
                         "partnerLive":{"title":"День 1","exercise":"Приседания","done":3,"total":5,"setsDone":8,"setsTotal":18,"startedAt":${now - 1_500_000}}}}"""
        )
        val invite = json.decodeFromString<com.texasprogram.app.model.SocialMessage>(
            """{"id":"m9","text":"Го тренироваться вместе 💪","fromMe":false,"createdAt":$now,"invite":{"id":"t1","status":"pending","fromMe":false}}"""
        )
        paparazzi.snapshot("17-social-extras") {
            Screen {
                Column(Modifier.padding(padding), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
                    com.texasprogram.app.ui.TogetherInviteBanner(together.incoming.first())
                    com.texasprogram.app.ui.TogetherCard(together.active!!)
                    com.texasprogram.app.ui.InviteBubble(invite, invite.invite!!, "heavy.anna") {}
                    com.texasprogram.app.ui.LiveFriendsStrip(live) {}
                    com.texasprogram.app.ui.LiveBadge(live[0].live)
                    Column { activity.items.forEach { com.texasprogram.app.ui.ActivityRow(it, onOpenActor = {}, onOpen = {}) } }
                    com.texasprogram.app.ui.LeaderboardCard(com.texasprogram.app.model.LeaderboardMetric.ranked(board, metric), metric) {}
                }
            }
        }
    }

    @Test
    fun summary() {
        tall()
        val workout = profile.schedule(LocalDate.now()).focus ?: return
        paparazzi.snapshot("11-summary") {
            Screen { WorkoutCompletionSummaryScreen(profile, workout, onUpdateProfile = {}, onDismiss = {}) }
        }
    }
}

/// Тёмная тема и то, что в приложении даёт Activity: «Назад» и запуск камеры с галереей.
@Composable
private fun Screen(content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    @Suppress("DEPRECATION")
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val backOwner = remember {
        object : OnBackPressedDispatcherOwner {
            override val onBackPressedDispatcher = OnBackPressedDispatcher()
            override val lifecycle: Lifecycle get() = lifecycleOwner.lifecycle
        }
    }
    val registryOwner = remember {
        object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(
                    requestCode: Int,
                    contract: ActivityResultContract<I, O>,
                    input: I,
                    options: ActivityOptionsCompat?
                ) = Unit
            }
        }
    }
    // Режим просмотра: Coil не ходит в сеть, а рисует заглушки фото.
    CompositionLocalProvider(
        LocalInspectionMode provides true,
        LocalOnBackPressedDispatcherOwner provides backOwner,
        LocalActivityResultRegistryOwner provides registryOwner
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Theme.accent,
                onPrimary = Color.White,
                background = Theme.base,
                surface = Theme.surface,
                onSurface = Theme.textPrimary
            )
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black), content = content)
        }
    }
}

/// Посты и профиль как с сервера: те же JSON, что отдаёт `/v2`.
private object Fixtures {
    fun user(): SocialUser = SocialService.json.decodeFromString(
        """
        {"id":"u1","username":"kelw1n","bio":"Техасская программа, второй месяц. Жим 120 к Новому году.",
         "heightCm":182,"weightKg":86.4,"showBody":true,"workouts":27,"followers":14,"following":9,
         "isMe":true,"isFollowing":false,
         "achievements":[{"id":"path","level":2},{"id":"streak","level":3},{"id":"bench","level":1}],
         "programs":[]}
        """
    )

    fun posts(): List<SocialPost> {
        val now = System.currentTimeMillis().toDouble()
        return SocialService.json.decodeFromString(
            """
            [
             {"id":"p3","author":{"id":"u3","username":"heavy.anna"},"createdAt":${now - 3 * 60_000},
              "title":"Объёмный понедельник","description":"",
              "durationSeconds":6000,"volumeKg":123456,"sets":30,"effort":10,
              "photos":[],
              "workout":{"exercises":[{"name":"Приседания со штангой","sets":[{"reps":8,"weight":120}]}]},
              "likes":12,"comments":4,"liked":false},
             {"id":"p1","author":{"id":"u2","username":"bro.lifts"},"partner":{"id":"u4","username":"semobsessed"},"createdAt":${now - 25 * 60_000},
              "title":"Неделя 3 · День 1 — объём","description":"Присед зашёл легко, жим тяжело. Завтра отдых.",
              "durationSeconds":4980,"volumeKg":12450,"sets":18,"effort":8,
              "photos":["0123456789abcdef01234567","0123456789abcdef01234568"],
              "workout":{"program":"Техасская программа","exercises":[
                {"name":"Приседания со штангой","sets":[{"reps":5,"weight":140,"record":true},{"reps":5,"weight":140},{"reps":5,"weight":140}]},
                {"name":"Жим лёжа","sets":[{"reps":5,"weight":100},{"reps":5,"weight":100}]}]},
              "likes":5,"comments":2,"liked":true},
             {"id":"p2","author":{"id":"u1","username":"kelw1n"},"createdAt":${now - 26 * 3_600_000},
              "title":"Тяжёлая пятница","description":"",
              "durationSeconds":3720,"volumeKg":8320.5,"sets":12,
              "photos":[],
              "workout":{"program":"Техасская программа","exercises":[
                {"name":"Приседания со штангой","sets":[{"reps":5,"weight":150,"record":true}]},
                {"name":"Жим лёжа широким хватом с паузой на груди","sets":[{"reps":3,"weight":107.5},{"reps":3,"weight":107.5}]},
                {"name":"Становая тяга","sets":[{"reps":5,"weight":180}]},
                {"name":"Подтягивания","sets":[{"reps":12,"weight":0},{"reps":10,"weight":0}]},
                {"name":"Пресс","sets":[{"reps":20,"weight":0}]}]},
              "likes":0,"comments":0,"liked":false}
            ]
            """
        )
    }
}
