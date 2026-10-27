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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.texasprogram.app.model.AchievementCalculator
import com.texasprogram.app.model.BodyWeightEntry
import com.texasprogram.app.model.FoodEntry
import com.texasprogram.app.model.Nutrients
import com.texasprogram.app.model.NutritionData
import com.texasprogram.app.model.NutritionGoal
import com.texasprogram.app.model.NutritionPlan
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
import com.texasprogram.app.ui.FoodEntrySheet
import com.texasprogram.app.ui.LocalNutritionSheetInline
import com.texasprogram.app.ui.NutritionActions
import com.texasprogram.app.ui.NutritionContent
import com.texasprogram.app.ui.NutritionGuideSheet
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

    /// Новое в переписке: пересланное, изменённое, тренировка карточкой,
    /// непрослушанное голосовое, сообщение в очереди и не ушедшее; плашка обновления.
    @Test
    fun chatExtras() {
        tall(4400)
        val now = System.currentTimeMillis().toDouble()
        val messages = SocialService.json.decodeFromString<List<com.texasprogram.app.model.SocialMessage>>(
            """[{"id":"1","text":"Смотри, как надо","fromMe":false,"createdAt":${now - 3_600_000},"forwarded":{"username":"heavy.anna"}},
               {"id":"2","text":"Жму 105 на 5","fromMe":true,"createdAt":${now - 3_500_000},"read":true,"edited":true},
               {"id":"3","text":"🏋️ День 1 · Объём","fromMe":false,"createdAt":${now - 3_000_000},
                "workout":{"title":"День 1 · Объём","durationSeconds":4380,"volumeKg":12850,"sets":18,"effort":8,
                           "workout":{"exercises":[{"name":"Приседания","sets":[{"reps":5,"weight":140,"record":true},{"reps":5,"weight":140}]},
                                                   {"name":"Жим лёжа","sets":[{"reps":5,"weight":100}]},
                                                   {"name":"Тяга в наклоне","sets":[{"reps":8,"weight":70}]},
                                                   {"name":"Подъём на бицепс","sets":[{"reps":12,"weight":30}]}]}}},
               {"id":"4","text":"🏋️ День 2","fromMe":true,"createdAt":${now - 2_000_000},
                "workout":{"title":"День 2 · Восстановление","durationSeconds":2700,"volumeKg":6400,"sets":12,"workout":{"exercises":[{"name":"Жим стоя","sets":[{"reps":5,"weight":60}]}]}}},
               {"id":"5","text":"🎤 Голосовое сообщение","fromMe":false,"createdAt":${now - 120_000},"heard":false,
                "voice":{"id":"v1","durationMs":14200,"waveform":"2468ace8642468aceca8642"}}]"""
        )
        val pending = com.texasprogram.app.model.ChatPending(clientId = "c1", username = "bro.lifts", createdAt = now - 30_000, text = "Уже в зале, жду")
        val failed = com.texasprogram.app.model.ChatPending(clientId = "c2", username = "bro.lifts", createdAt = now - 10_000, text = "Ответ на удалённое")
        val release = com.texasprogram.app.service.AppUpdater.Release(1200, "3.1.120", "Переписка без рывков, итоги месяца")
        paparazzi.snapshot("18-chat-extras") {
            Screen {
                Column(Modifier.padding(padding), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                    com.texasprogram.app.ui.AppUpdateBanner(state = com.texasprogram.app.service.AppUpdater.State.Downloading(release, 0.94f))
                    com.texasprogram.app.ui.AppUpdateBanner(state = com.texasprogram.app.service.AppUpdater.State.Available(release))
                    messages.forEach { com.texasprogram.app.ui.MessageBubble(it, username = "bro.lifts", onCopy = {}, onDelete = {}) }
                    com.texasprogram.app.ui.MessageBubble(pending.message, username = "bro.lifts", onCopy = {}, onDelete = {})
                    com.texasprogram.app.ui.MessageBubble(failed.message, username = "bro.lifts", failure = "Сообщение удалено", onCopy = {}, onDelete = {})
                }
            }
        }
    }

    /// Итоги месяца: первый слайд, слайд объёма и карточка для сторис.
    @Test
    fun recap() {
        val today = LocalDate.now()
        val log = (0 until 9).map { index ->
            com.texasprogram.app.model.CompletionRecord(key = "1-$index", epochDay = today.minusDays(index * 3L).toEpochDay(), week = 1, day = index, durationSeconds = 3600 + index * 120)
        }
        val sets = buildMap {
            for (index in 0 until 9) {
                put("1-$index|Приседания|0", com.texasprogram.app.model.SetEntry(5, 120 + index * 2.5))
                put("1-$index|Жим лёжа|0", com.texasprogram.app.model.SetEntry(5, 90.0 + index))
                put("1-$index|Становая тяга|0", com.texasprogram.app.model.SetEntry(3, 160.0))
            }
        }
        val recap = com.texasprogram.app.model.TrainingRecap.build(log, sets, period = com.texasprogram.app.model.TrainingRecap.Period.Month(today.year, today.monthValue))
        for ((name, slide) in listOf("19-recap-intro" to com.texasprogram.app.ui.RecapSlide.INTRO, "20-recap-volume" to com.texasprogram.app.ui.RecapSlide.VOLUME)) {
            paparazzi.snapshot(name) {
                Screen {
                    val (from, to) = com.texasprogram.app.ui.palette(0)
                    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(from, to))).padding(26.dp)) {
                        com.texasprogram.app.ui.RecapSlideContent(slide, recap) {}
                    }
                }
            }
        }
        paparazzi.snapshot("21-recap-card") {
            Screen {
                val context = androidx.compose.ui.platform.LocalContext.current
                val bitmap = remember { com.texasprogram.app.ui.TrainingRecapCard.render(context, recap) }
                androidx.compose.foundation.Image(bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
            }
        }
    }

    /// «Питание»: неделя наблюдения, цель с недельной проверкой (и на узком
    /// экране), справка по методу и запись еды вручную.
    @Test
    fun nutrition() {
        val today = LocalDate.now()
        val zone = java.time.ZoneId.systemDefault()
        fun at(daysAgo: Long, hour: Int) = today.minusDays(daysAgo).atTime(hour, 0).atZone(zone).toInstant()
        fun midnight(daysAgo: Long) = today.minusDays(daysAgo).atStartOfDay(zone).toInstant()
        val food = listOf(
            FoodEntry(date = at(0, 9), name = "Овсянка с бананом", grams = 350.0, nutrients = Nutrients(420.0, 14.0, 9.0, 72.0)),
            FoodEntry(date = at(0, 14), name = "Куриная грудка", grams = 150.0, nutrients = Nutrients(248.0, 46.5, 5.4, 0.0), fromPhoto = true),
            FoodEntry(date = at(0, 14), name = "Рис", grams = 200.0, nutrients = Nutrients(260.0, 5.4, 0.6, 57.0), fromPhoto = true)
        ) + (1L..3L).map { FoodEntry(date = at(it, 13), name = "Обед", nutrients = Nutrients(2400.0, 140.0, 80.0, 280.0)) }
        // Вес за две недели чуть снижается — на наборе это совет добавить углеводов.
        val weighed = profile.copy(bodyWeightLog = (0L..13L).map { BodyWeightEntry(today.minusDays(it).toEpochDay(), 82.0 + it * 0.02) })
        val observe = NutritionData(food, NutritionPlan(observationStart = midnight(3)))
        val goal = NutritionData(
            food,
            NutritionPlan(
                observationStart = midnight(21),
                maintenance = Nutrients(2800.0, 150.0, 90.0, 340.0),
                goal = NutritionGoal.BULK,
                target = Nutrients(2920.0, 150.0, 90.0, 370.0),
                stepStart = midnight(7)
            )
        )
        val tab: @Composable (NutritionData) -> Unit = { data ->
            NutritionContent(weighed, data, padding, NutritionActions(null), hasKey = { true }, onUpdate = {})
        }
        tall(2600)
        paparazzi.snapshot("22-nutrition-observe") { Screen { tab(observe) } }
        paparazzi.snapshot("23-nutrition-goal") { Screen { tab(goal) } }
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5.copy(screenHeight = 2600))
        paparazzi.snapshot("23b-nutrition-goal-narrow") { Screen { tab(goal) } }
        tall(2600)
        paparazzi.snapshot("24-nutrition-guide") {
            Screen { CompositionLocalProvider(LocalNutritionSheetInline provides true) { NutritionGuideSheet {} } }
        }
        paparazzi.snapshot("25-nutrition-entry") {
            Screen {
                CompositionLocalProvider(LocalNutritionSheetInline provides true) {
                    FoodEntrySheet(original = null, day = today, recent = food.take(3), onSave = {}, onDismiss = {})
                }
            }
        }
    }

    /// Уровень: карточка в «Прогрессе», экран уровня, новый уровень и карточка для сторис.
    @Test
    fun levels() {
        val today = LocalDate.now()
        val log = (0 until 30).map { index ->
            com.texasprogram.app.model.CompletionRecord("${index / 3 + 1}-${index % 3 + 1}", today.minusDays(index * 7L / 3 + 1).toEpochDay(), index / 3 + 1, index % 3 + 1)
        }
        val sets = log.flatMap { record ->
            val index = log.indexOf(record)
            (0 until 5).flatMap { set ->
                listOf(
                    "${record.key}|Приседания|$set" to com.texasprogram.app.model.SetEntry(5, 140 - index * 1.25),
                    "${record.key}|Жим лёжа|$set" to com.texasprogram.app.model.SetEntry(5, 100.0 - index)
                )
            }
        }.toMap()
        val leveled = profile.copy(completionLog = log, setLog = sets)
        val xp = com.texasprogram.app.model.Progression.xp(leveled)
        val level = com.texasprogram.app.model.Progression.level(xp)
        paparazzi.snapshot("26-level-card") {
            Screen { Column(Modifier.padding(16.dp)) { com.texasprogram.app.ui.LevelCard(leveled) } }
        }
        tall(2600)
        paparazzi.snapshot("27-level-sheet") {
            Screen { CompositionLocalProvider(LocalNutritionSheetInline provides true) { com.texasprogram.app.ui.LevelSheet(leveled) {} } }
        }
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5)
        paparazzi.snapshot("28-level-up") {
            Screen {
                com.texasprogram.app.ui.LevelUpOverlay(com.texasprogram.app.service.ProgressionEvents.Gain(level.floor - 40, xp)) {}
            }
        }
        paparazzi.snapshot("29-level-share") {
            Screen {
                val bitmap = remember { com.texasprogram.app.ui.LevelShareCard.render(level) }
                androidx.compose.foundation.Image(bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
            }
        }
    }

    /// Уровень другого человека и его аватарка на весь экран.
    @Test
    fun userLevelAndAvatar() {
        val other = Fixtures.user().copy(isMe = false)
        tall(2200)
        paparazzi.snapshot("34-user-level") {
            Screen { CompositionLocalProvider(LocalNutritionSheetInline provides true) { com.texasprogram.app.ui.UserLevelSheet(other, 9) {} } }
        }
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5)
        paparazzi.snapshot("35-avatar-viewer") {
            Screen {
                CompositionLocalProvider(LocalNutritionSheetInline provides true) {
                    com.texasprogram.app.ui.AvatarViewer("0123456789abcdef01234567", other.username) {}
                }
            }
        }
    }

    /// Челленджи: приглашение, идущий и законченный, таблица целиком и карточка итога.
    @Test
    fun challenges() {
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        fun member(id: String, name: String, place: Int, score: Int, detail: String, me: Boolean = false, level: Int? = null) =
            """{"user":{"id":"$id","username":"$name","isMe":$me${level?.let { ",\"level\":$it" } ?: ""}},"status":"joined","score":$score,"detail":"$detail","place":$place}"""
        val list = SocialService.json.decodeFromString<List<com.texasprogram.app.model.SocialChallenge>>(
            """[
            {"id":"inv","kind":"volume","weeks":2,"startsAt":${now - day},"endsAt":${now + 13 * day},"status":"active","myStatus":"invited",
             "owner":{"id":"u2","username":"bro.lifts"},"members":[${member("u2", "bro.lifts", 1, 4200, "4200 кг")}]},
            {"id":"run","kind":"workouts","weeks":2,"startsAt":${now - 5 * day},"endsAt":${now + 9 * day},"status":"active","myStatus":"joined",
             "members":[${member("u2", "bro.lifts", 1, 4, "4 тренировки", level = 12)},${member("u1", "kelw1n", 2, 3, "3 тренировки", true, 9)},
                        ${member("u3", "heavy.anna", 3, 1, "1 тренировка", level = 21)},{"user":{"id":"u4","username":"slowpoke"},"status":"invited"}]},
            {"id":"fin","kind":"strength","weeks":4,"startsAt":${now - 33 * day},"endsAt":${now - 5 * day},"status":"finished","myStatus":"joined",
             "members":[${member("u1", "kelw1n", 1, 6, "+5,2 кг · 6,3% веса", true, 9)},${member("u3", "heavy.anna", 2, 4, "+3,1 кг · 4,8% веса")}]}
            ]"""
        )
        com.texasprogram.app.service.ChallengeStore.setForPreview(list)
        tall(2400)
        paparazzi.snapshot("30-challenges") {
            Screen { Column(Modifier.padding(16.dp)) { com.texasprogram.app.ui.ChallengesSection() } }
        }
        paparazzi.snapshot("31-challenge-detail") {
            Screen { CompositionLocalProvider(LocalNutritionSheetInline provides true) { com.texasprogram.app.ui.ChallengeDetailSheet("fin") {} } }
        }
        paparazzi.snapshot("32-challenge-new") {
            Screen { CompositionLocalProvider(LocalNutritionSheetInline provides true) { com.texasprogram.app.ui.NewChallengeSheet {} } }
        }
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5)
        paparazzi.snapshot("33-challenge-share") {
            Screen {
                val bitmap = remember { com.texasprogram.app.ui.ChallengeShareCard.render(list[2]) }
                androidx.compose.foundation.Image(bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
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
         "isMe":true,"isFollowing":false,"level":12,"xp":8000,
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
