package com.texasprogram.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.texasprogram.app.service.SocialService
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val MAX_SCALE = 4f
private const val DOUBLE_TAP_SCALE = 2.5f

/// Сторона аватарки в профиле: маленькая копия того же размера уже лежит в кэше.
private val ProfileAvatarSize = 118.dp

/// Аватарка на весь экран, как в Telegram и WhatsApp: зажми или нажми на неё
/// в профиле. Фото вырастает из аватарки, щипок приближает, двойное касание —
/// ×2,5, смахивание вниз или вверх закрывает.
@Composable
fun AvatarViewer(imageId: String, username: String, onClose: () -> Unit) {
    if (LocalNutritionSheetInline.current) {
        AvatarViewerContent(imageId, username, onClose)
        return
    }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        AvatarViewerContent(imageId, username, onClose)
    }
}

@Composable
private fun AvatarViewerContent(imageId: String, username: String, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptics = rememberHapticEngine()
    val touchSlop = LocalViewConfiguration.current.touchSlop
    // Снимки экранов рисуют только первый кадр — там просмотр сразу открыт.
    val inline = LocalNutritionSheetInline.current
    val appear = remember { Animatable(if (inline) 1f else 0f) }
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    /// Смахивание без приближения: фото едет за пальцем, фон гаснет.
    var pull by remember { mutableStateOf(Offset.Zero) }
    var closing by remember { mutableStateOf(false) }

    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            appear.animateTo(0f, tween(180, easing = FastOutLinearInEasing))
            onClose()
        }
    }

    fun springBack() {
        val from = pull
        scope.launch {
            animate(1f, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ -> pull = from * value }
        }
    }

    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sidePx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val side = maxWidth
        val pullProgress = (abs(pull.y) / (heightPx * 0.4f)).coerceIn(0f, 1f)

        /// Приближенное фото не уезжает краями внутрь экрана.
        fun clamp(offset: Offset, zoom: Float): Offset {
            val maxX = (sidePx * (zoom - 1) / 2).coerceAtLeast(0f)
            val maxY = ((sidePx * zoom - heightPx) / 2).coerceAtLeast(0f)
            return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
        }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) * (1 - pullProgress * 0.75f) }
                .background(Color.Black)
                .pointerInput(Unit) { detectTapGestures { close() } }
        )

        Box(
            Modifier
                .align(Alignment.Center)
                .size(side)
                .graphicsLayer {
                    val grow = 0.35f + 0.65f * appear.value
                    val zoom = grow * scale * (1 - pullProgress * 0.2f)
                    scaleX = zoom
                    scaleY = zoom
                    translationX = pan.x + pull.x
                    translationY = pan.y + pull.y
                    alpha = appear.value.coerceIn(0f, 1f)
                }
                .semantics { contentDescription = "Аватарка @$username" }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        if (closing) return@detectTapGestures
                        haptics.light()
                        val target = if (scale > 1.01f) 1f else DOUBLE_TAP_SCALE
                        val fromScale = scale
                        val fromPan = pan
                        pull = Offset.Zero
                        scope.launch {
                            animate(0f, 1f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ ->
                                scale = fromScale + (target - fromScale) * value
                                pan = fromPan * (1 - value)
                            }
                        }
                    })
                }
                .pointerInput(sidePx, heightPx) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var moved = 0f
                        var pinched = false
                        do {
                            val event = awaitPointerEvent()
                            if (closing) break
                            val zoom = event.calculateZoom()
                            val delta = event.calculatePan()
                            val fingers = event.changes.count { it.pressed }
                            moved += delta.getDistance()
                            if (fingers > 1 || zoom != 1f) {
                                // Щипок: приближаем, смахивание отменяется.
                                pinched = true
                                pull = Offset.Zero
                                scale = (scale * zoom).coerceIn(1f, MAX_SCALE)
                                pan = clamp(pan + delta, scale)
                            } else if (moved > touchSlop) {
                                if (scale > 1.01f) pan = clamp(pan + delta, scale) else if (!pinched) pull += delta
                            } else {
                                continue
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } while (event.changes.any { it.pressed })

                        if (closing) return@awaitEachGesture
                        if (scale <= 1.01f) {
                            scale = 1f
                            pan = Offset.Zero
                        }
                        if (pull != Offset.Zero) {
                            if (abs(pull.y) > 120.dp.toPx()) close() else springBack()
                        }
                    }
                }
        ) {
            // Пока грузится чёткая, под ней — маленькая аватарка из кэша профиля,
            // растянутая на весь квадрат: фото видно сразу, без серой заглушки.
            val thumbScale = side / ProfileAvatarSize
            RemoteImage(
                imageId,
                Modifier
                    .align(Alignment.Center)
                    .size(ProfileAvatarSize)
                    .graphicsLayer {
                        scaleX = thumbScale
                        scaleY = thumbScale
                    }
            )
            AsyncImage(
                model = SocialService.imageUrl(imageId),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) * (1 - (pullProgress * 3).coerceAtMost(1f)) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("@$username", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
            Spacer(Modifier.size(12.dp))
            Box(
                Modifier
                    .size(40.dp)
                    .pressable { close() }
                    .chatGlass(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}
