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

    @Test
    fun postDetail() {
        tall()
        paparazzi.snapshot("03-post-detail") {
            Screen { PostDetailScreen(Fixtures.posts()[1], padding, SocialNavigator(), onChange = {}) }
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
        MaterialTheme(colorScheme = darkColorScheme()) {
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
             {"id":"p1","author":{"id":"u2","username":"bro.lifts"},"createdAt":${now - 25 * 60_000},
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
