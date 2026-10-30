package com.texasprogram.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.texasprogram.app.model.AchievementKind
import com.texasprogram.app.model.CustomProgram
import com.texasprogram.app.model.SocialBadge
import com.texasprogram.app.model.SocialPost
import com.texasprogram.app.model.SocialProgramDay
import com.texasprogram.app.service.SocialService

// MARK: - Фото с сервера

/// Фото по идентификатору. Адрес фото не меняется, поэтому Coil хранит его
/// на диске и второй раз не скачивает.
@Composable
fun RemoteImage(id: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val url = SocialService.imageUrl(id)
    if (url == null) {
        ImagePlaceholder(modifier, loading = false)
        return
    }
    // Лёгкий AsyncImage, а не SubcomposeAsyncImage: тот на каждое фото строил
    // отдельный кусок интерфейса со спиннером — в ленте это рывки прокрутки.
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = contentScale,
        placeholder = PlaceholderPainter,
        error = PlaceholderPainter,
        modifier = modifier
    )
}

/// Ровная подложка, пока фото грузится: без анимации, ничего не перерисовывает.
private val PlaceholderPainter: androidx.compose.ui.graphics.painter.Painter
    get() = androidx.compose.ui.graphics.painter.ColorPainter(Theme.tone(0xFF1F1F22))

@Composable
private fun ImagePlaceholder(modifier: Modifier, loading: Boolean) {
    Box(modifier.background(Theme.tone(0xFF1F1F22)), contentAlignment = Alignment.Center) {
        if (loading) {
            CircularProgressIndicator(color = Theme.ink.copy(alpha = 0.4f), strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            Icon(Icons.Filled.Image, contentDescription = null, tint = Theme.ink.copy(alpha = 0.25f), modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun Avatar(imageId: String?, username: String, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, Theme.ink.copy(alpha = 0.08f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (imageId != null) {
            RemoteImage(imageId, Modifier.fillMaxSize())
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(Theme.accent, Theme.accentDeep))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    username.take(1).uppercase(),
                    color = Color.White,
                    fontSize = (size.value * 0.42f).sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

// MARK: - Медаль

val AchievementKind.icon: ImageVector
    get() = when (this) {
        AchievementKind.PATH -> Icons.Filled.Terrain
        AchievementKind.IRON -> Icons.Filled.Scale
        AchievementKind.RECORDS -> Icons.Filled.EmojiEvents
        AchievementKind.STREAK -> Icons.Filled.LocalFireDepartment
        AchievementKind.BENCH -> Icons.Filled.FitnessCenter
        AchievementKind.SQUAT -> Icons.Filled.AccessibilityNew
        AchievementKind.DEADLIFT -> Icons.Filled.Anchor
        AchievementKind.TOTAL -> Icons.Filled.WorkspacePremium
    }

fun medalColors(level: Int): List<Color> = when (level) {
    1 -> listOf(Color(0xFFCC8552), Color(0xFF8C522E))
    2 -> listOf(Color(0xFFDBE0EB), Color(0xFF8C94A3))
    3 -> listOf(Color(0xFFFFD159), Color(0xFFCC8C1A))
    4 -> listOf(Color(0xFF8CEBF2), Color(0xFF338CBF))
    5 -> listOf(Theme.accent, Color(0xFF8C1A73))
    else -> listOf(Theme.tone(0xFF383838), Theme.tone(0xFF242424))
}

/// Шестиугольная медаль: цвет — уровень, значок — что за достижение.
@Composable
fun MedalView(kind: AchievementKind?, level: Int, size: Dp = 64.dp) {
    val colors = medalColors(level)
    Box(Modifier.size(width = size, height = size * 0.9f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val path = Path().apply {
                moveTo(w * 0.25f, h * 0.04f)
                lineTo(w * 0.75f, h * 0.04f)
                lineTo(w, h / 2)
                lineTo(w * 0.75f, h * 0.96f)
                lineTo(w * 0.25f, h * 0.96f)
                lineTo(0f, h / 2)
                close()
            }
            drawPath(path, Brush.linearGradient(colors, Offset.Zero, Offset(w, h)))
            drawPath(path, Theme.ink.copy(alpha = if (level > 0) 0.35f else 0.08f), style = Stroke(width = maxOf(1f, w * 0.03f)))
        }
        Icon(
            kind?.icon ?: Icons.Filled.QuestionMark,
            contentDescription = kind?.let { "${it.title}, уровень $level" },
            tint = if (level > 0) Theme.ink else Theme.ink.copy(alpha = 0.3f),
            modifier = Modifier.size(size * 0.4f)
        )
    }
}

// MARK: - Карусель фото поста

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoPager(ids: List<String>, modifier: Modifier = Modifier) {
    val pager = rememberPagerState { ids.size }
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(4f / 5f)
            .clip(RoundedCornerShape(22.dp))
    ) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            RemoteImage(ids[page], Modifier.fillMaxSize())
        }
        if (ids.size > 1) PageDots(pager, ids.size, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
    }
}

// MARK: - Мелочи

@Composable
fun SocialStat(value: String, title: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(value, color = Theme.ink, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text(title, color = Theme.ink.copy(alpha = 0.55f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun FollowButton(isFollowing: Boolean, compact: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val haptics = rememberHapticEngine()
    val shape = RoundedCornerShape(if (compact) 16.dp else 18.dp)
    val label by animateColorAsState(if (isFollowing) Theme.ink else Theme.canvas, Motion.fade(200), label = "followLabel")
    Box(
        modifier
            .then(if (compact) Modifier else Modifier.fillMaxWidth())
            .height(if (compact) 32.dp else 50.dp)
            .pressable {
                haptics.light()
                onClick()
            }
            .clip(shape)
            .selectionFill(isFollowing, Theme.tone(0xFF292929), Theme.ink, shape)
            .padding(horizontal = if (compact) 14.dp else 0.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (isFollowing) "Вы подписаны" else "Подписаться",
            color = label,
            fontSize = if (compact) 13.sp else 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SocialErrorCard(message: String, retry: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Theme.tone(0xFF161617))
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Filled.WifiOff, contentDescription = null, tint = Theme.ink.copy(alpha = 0.5f), modifier = Modifier.size(26.dp))
        Text(message, color = Theme.ink.copy(alpha = 0.65f), fontSize = 14.sp, textAlign = TextAlign.Center)
        if (retry != null) {
            Text(
                "Повторить",
                color = Theme.accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.pressable(onClick = retry)
            )
        }
    }
}

@Composable
fun SectionCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Theme.ink.copy(alpha = 0.45f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = modifier
    )
}

// MARK: - Переходы внутри вкладки

/// Куда ведут нажатия в ленте и профилях.
sealed interface SocialRoute {
    data class Post(val post: SocialPost) : SocialRoute
    data class User(val username: String) : SocialRoute
    data class Followers(val username: String) : SocialRoute
    data class Following(val username: String) : SocialRoute
    /// День программы; `all` и `author` — чтобы скопировать к себе день или всю программу.
    data class Program(val day: SocialProgramDay, val all: List<SocialProgramDay> = emptyList(), val author: String? = null) : SocialRoute
    data class Badges(val badges: List<SocialBadge>) : SocialRoute
    data object Achievements : SocialRoute
    data object EditProfile : SocialRoute
    data class Chat(val username: String) : SocialRoute
    data object Activity : SocialRoute
    /// Пост, который ещё надо загрузить, — из активности.
    data class PostId(val id: String) : SocialRoute
    /// Копия чужой тренировки в конструкторе «Своей программы».
    data class CopyProgram(val program: CustomProgram, val author: String) : SocialRoute
}

/// Видна ли вкладка, в которой лежит экран: скрытые остаются в памяти,
/// но не должны отвечать на «Назад».
val LocalTabActive = androidx.compose.runtime.compositionLocalOf { true }

/// Стек экранов одной вкладки: «Назад» снимает верхний.
@Stable
class SocialNavigator {
    val stack = mutableStateListOf<SocialRoute>()
    fun push(route: SocialRoute) { stack.add(route) }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
}

@Composable
fun rememberSocialNavigator(): SocialNavigator = remember { SocialNavigator() }

/// Корень вкладки и поверх него — открытые экраны. Как в iOS: новый экран
/// въезжает справа целиком, а прежний в это время чуть уходит влево и темнеет;
/// «Назад» — то же самое в обратную сторону.
@Composable
fun SocialHost(
    navigator: SocialNavigator,
    screen: @Composable (SocialRoute) -> Unit,
    root: @Composable () -> Unit
) {
    // Скрытая вкладка живёт в памяти, но «Назад» не перехватывает.
    BackHandler(enabled = LocalTabActive.current && navigator.stack.isNotEmpty()) { navigator.pop() }
    val covered = navigator.stack.isNotEmpty()
    // Корень уезжает влево вместе с первым открытым экраном. Когда тот лёг
    // целиком, корень не рисуется вовсе — его всё равно не видно.
    val rootShift by animateFloatAsState(
        if (covered) 1f else 0f,
        tween(if (covered) 360 else 300, easing = Motion.emphasized),
        label = "rootShift"
    )
    Box(Modifier.fillMaxSize()) {
        // Корень под открытым экраном не должен читаться TalkBack.
        Box(
            (if (covered) Modifier.fillMaxSize().clearAndSetSemantics {} else Modifier.fillMaxSize())
                .graphicsLayer {
                    translationX = -size.width * 0.22f * rootShift
                    alpha = if (rootShift >= 0.999f) 0f else 1f - 0.35f * rootShift
                }
        ) { root() }
        AnimatedContent(
            targetState = navigator.stack.toList(),
            transitionSpec = {
                val slide = tween<IntOffset>(360, easing = Motion.emphasized)
                val back = tween<IntOffset>(300, easing = Motion.emphasized)
                val transform = if (targetState.size >= initialState.size) {
                    slideInHorizontally(slide) { it } togetherWith
                        (slideOutHorizontally(slide) { -it * 22 / 100 } + fadeOut(tween(360), targetAlpha = 0.65f))
                } else {
                    (slideInHorizontally(back) { -it * 22 / 100 } + fadeIn(tween(300), initialAlpha = 0.65f)) togetherWith
                        slideOutHorizontally(back) { it }
                }
                // Верхним рисуется экран, что глубже в стеке: при «Назад» уходящий — над прежним.
                transform.apply { targetContentZIndex = targetState.size.toFloat() }
            },
            label = "socialStack"
        ) { stack ->
            val top = stack.lastOrNull()
            if (top != null) {
                // Экран ловит все касания: раньше нажатие в пустое место под
                // коротким списком проходило насквозь в ленту и открывало чужой пост.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Theme.canvas)
                        .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
                ) {
                    screen(top)
                }
            }
        }
    }
}
