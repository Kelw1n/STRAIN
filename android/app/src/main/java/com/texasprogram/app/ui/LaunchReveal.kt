package com.texasprogram.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.ImageBitmap
import com.texasprogram.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val MarkColor = Color(0xFFFF6029)
private val MarkWidth = 120.dp

/// Открытие приложения, как у REBRN: на чёрном оранжевый знак, потом он
/// с ускорением растёт и становится окном, сквозь которое проступает приложение.
///
/// Первый кадр совпадает с системным экраном запуска (`Theme.Strain.Starting`:
/// чёрный фон и тот же знак шириной 120dp по центру), поэтому стыка не видно.
/// Всё рисуется одним холстом в фазе отрисовки — без перекомпоновки на кадр.
@Composable
fun LaunchReveal(onFinish: () -> Unit) {
    val image: ImageBitmap = ImageBitmap.imageResource(R.drawable.splash_mark)
    val scale = remember { Animatable(1f) }
    val fill = remember { Animatable(1f) }
    val fade = remember { Animatable(1f) }
    val finish by rememberUpdatedState(onFinish)

    LaunchedEffect(Unit) {
        // Пока знак стоит, под ним успевает собраться первый экран.
        delay(380)
        launch { fill.animateTo(0f, tween(180, easing = FastOutLinearInEasing)) }
        launch { scale.animateTo(24f, tween(550, easing = CubicBezierEasing(0.6f, 0f, 0.9f, 0.5f))) }
        delay(400)
        fade.animateTo(0f, tween(180, easing = LinearOutSlowInEasing))
        finish()
    }

    Canvas(
        Modifier
            .fillMaxSize()
            .clearAndSetSemantics { }
            // Пока заставка закрывает экран, нажатия не проходят к приложению.
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } }
            .graphicsLayer {
                alpha = fade.value
                // Отдельный слой: дырка (DstOut) режет только чёрный фон заставки.
                compositingStrategy = CompositingStrategy.Offscreen
            }
    ) {
        val w = MarkWidth.toPx() * scale.value
        val h = w * image.height / image.width
        val offset = IntOffset(((size.width - w) / 2).roundToInt(), ((size.height - h) / 2).roundToInt())
        val dst = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1))

        // Чёрное всё, кроме знака: в нём дырка, сквозь неё видно приложение.
        drawRect(Theme.canvas)
        drawImage(
            image = image,
            dstOffset = offset,
            dstSize = dst,
            blendMode = BlendMode.DstOut,
            filterQuality = FilterQuality.Low
        )
        // Заливка знака: гаснет, как только знак начинает расти.
        if (fill.value > 0f) {
            drawImage(
                image = image,
                dstOffset = offset,
                dstSize = dst,
                alpha = fill.value,
                colorFilter = ColorFilter.tint(MarkColor),
                filterQuality = FilterQuality.Low
            )
        }
    }
}
