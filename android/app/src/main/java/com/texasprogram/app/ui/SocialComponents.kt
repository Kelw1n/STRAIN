package com.texasprogram.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
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
import coil.compose.SubcomposeAsyncImage
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
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier,
        loading = { ImagePlaceholder(Modifier.fillMaxSize(), loading = true) },
        error = { ImagePlaceholder(Modifier.fillMaxSize(), loading = false) }
    )
}

@Composable
private fun ImagePlaceholder(modifier: Modifier, loading: Boolean) {
    Box(modifier.background(Color(0xFF1F1F22)), contentAlignment = Alignment.Center) {
        if (loading) {
            CircularProgressIndicator(color = Color.White.copy(alpha = 0.4f), strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            Icon(Icons.Filled.Image, contentDescription = null, tint = Color.White.copy(alpha = 0.25f), modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun Avatar(imageId: String?, username: String, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape),
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
    else -> listOf(Color(0xFF383838), Color(0xFF242424))
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
            drawPath(path, Color.White.copy(alpha = if (level > 0) 0.35f else 0.08f), style = Stroke(width = maxOf(1f, w * 0.03f)))
        }
        Icon(
            kind?.icon ?: Icons.Filled.QuestionMark,
            contentDescription = kind?.let { "${it.title}, уровень $level" },
            tint = if (level > 0) Color.White else Color.White.copy(alpha = 0.3f),
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
        if (ids.size > 1) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(ids.size) { index ->
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (index == pager.currentPage) 1f else 0.45f))
                    )
                }
            }
        }
    }
}

// MARK: - Мелочи

@Composable
fun SocialStat(value: String, title: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text(title, color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun FollowButton(isFollowing: Boolean, compact: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val haptics = rememberHapticEngine()
    val shape = RoundedCornerShape(if (compact) 16.dp else 18.dp)
    Box(
        modifier
            .then(if (compact) Modifier else Modifier.fillMaxWidth())
            .height(if (compact) 32.dp else 50.dp)
            .clip(shape)
            .background(if (isFollowing) Color(0xFF292929) else Color.White)
            .pressable {
                haptics.light()
                onClick()
            }
            .padding(horizontal = if (compact) 14.dp else 0.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (isFollowing) "Вы подписаны" else "Подписаться",
            color = if (isFollowing) Color.White else Color.Black,
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
            .background(Color(0xFF161617))
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Filled.WifiOff, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(26.dp))
        Text(message, color = Color.White.copy(alpha = 0.65f), fontSize = 14.sp, textAlign = TextAlign.Center)
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
        color = Color.White.copy(alpha = 0.45f),
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
    /// Копия чужой тренировки в конструкторе «Своей программы».
    data class CopyProgram(val program: CustomProgram, val author: String) : SocialRoute
}

/// Стек экранов одной вкладки: «Назад» снимает верхний.
@Stable
class SocialNavigator {
    val stack = mutableStateListOf<SocialRoute>()
    fun push(route: SocialRoute) { stack.add(route) }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
}

@Composable
fun rememberSocialNavigator(): SocialNavigator = remember { SocialNavigator() }

/// Корень вкладки и поверх него — открытые экраны. Новый въезжает справа.
@Composable
fun SocialHost(
    navigator: SocialNavigator,
    screen: @Composable (SocialRoute) -> Unit,
    root: @Composable () -> Unit
) {
    BackHandler(enabled = navigator.stack.isNotEmpty()) { navigator.pop() }
    val covered = navigator.stack.isNotEmpty()
    Box(Modifier.fillMaxSize()) {
        // Корень под открытым экраном не должен читаться TalkBack.
        Box(if (covered) Modifier.fillMaxSize().clearAndSetSemantics {} else Modifier.fillMaxSize()) { root() }
        AnimatedContent(
            targetState = navigator.stack.toList(),
            transitionSpec = {
                if (targetState.size >= initialState.size) {
                    (slideInHorizontally(tween(280)) { it / 3 } + fadeIn(tween(200))) togetherWith fadeOut(tween(160))
                } else {
                    fadeIn(tween(180)) togetherWith (slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(200)))
                }
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
                        .background(Color.Black)
                        .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
                ) {
                    screen(top)
                }
            }
        }
    }
}
