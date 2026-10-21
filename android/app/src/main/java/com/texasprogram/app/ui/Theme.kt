package com.texasprogram.app.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.PI

/// Одна тема — REBRN: чистый OLED-чёрный, графитовые карточки и пламенный
/// оранжевый акцент, как в iOS-версии. Других тем нет: экраны свёрстаны под
/// чёрный фон.
object Theme {
    val accent = Color(0xFFFF6029)
    val accentDeep = Color(0xFFE0471A)
    val success = Color(0xFF2ED573)
    val warning = Color(0xFFFFA533)
    val record = Color(0xFFFF5252)

    /// Чистый чёрный, а не почти чёрный.
    val base = Color(0xFF000000)
    val textPrimary = Color(0xFFFFFFFF)
    val textSecondary = Color(0xFF9E9EA4)
    val textTertiary = Color(0xFF606068)

    val surface = Color(0xFF161617)
    val card = surface
    val surfaceSoft = Color(0xFF1C1C1F)
    val surfaceElevated = Color(0xFF242428)
    val dialog = Color(0xFF18181B)
    val hairline = Color(0x1FFFFFFF)

    val hairlineGradient = Brush.linearGradient(
        listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.04f)),
        Offset.Zero,
        Offset.Infinite
    )
    val accentGradient = Brush.linearGradient(listOf(accent, accentDeep), Offset.Zero, Offset.Infinite)
    val deepGradient = Brush.linearGradient(listOf(accentDeep, accent), Offset.Zero, Offset.Infinite)
    val successGradient = Brush.linearGradient(listOf(success, accent), Offset.Zero, Offset.Infinite)
    val recordGradient = Brush.linearGradient(listOf(warning, record), Offset.Zero, Offset.Infinite)
}

/// Пружины с теми же параметрами, что `spring(response:dampingFraction:)` в SwiftUI:
/// stiffness = (2π / response)², dampingRatio = dampingFraction.
object Motion {
    private fun stiffnessFor(response: Double): Float {
        val omega = 2 * PI / response
        return (omega * omega).toFloat()
    }

    // Пружины почти без перелёта: экран не качается и не «дёргается»
    // после движения, а быстро встаёт на место.
    fun <T> card(): SpringSpec<T> = spring(dampingRatio = 0.92f, stiffness = stiffnessFor(0.36))
    fun <T> snappy(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = stiffnessFor(0.28))
    /// Чуть живее — для отметок и наград, но без заметного качания.
    fun <T> bouncy(): SpringSpec<T> = spring(dampingRatio = 0.78f, stiffness = stiffnessFor(0.34))
    fun <T> appear(): SpringSpec<T> = spring(dampingRatio = 0.95f, stiffness = stiffnessFor(0.34))
    fun <T> sheet(): SpringSpec<T> = spring(dampingRatio = 0.92f, stiffness = stiffnessFor(0.36))
    fun <T> reorder(): SpringSpec<T> = spring(dampingRatio = 0.86f, stiffness = stiffnessFor(0.26))
    /// Нажатие: кнопка прижимается сразу, без задержки.
    fun <T> press(): SpringSpec<T> = spring(dampingRatio = 0.95f, stiffness = stiffnessFor(0.16))
    /// Отпускание: мягко пружинит назад с едва заметным отскоком.
    fun <T> release(): SpringSpec<T> = spring(dampingRatio = 0.68f, stiffness = stiffnessFor(0.34))

    val smooth: Easing = FastOutSlowInEasing
    /// Быстрый старт и долгое мягкое торможение — как у переходов iOS.
    val emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    // Переходы экранов. Едут только слоем (сдвиг, масштаб, прозрачность) —
    // компоновку они не трогают, поэтому не тормозят даже на слабых телефонах.

    /// Раздел поверх вкладок: проявляется с лёгким приближением.
    fun modalEnter(): EnterTransition =
        fadeIn(tween(220, easing = LinearOutSlowInEasing)) + scaleIn(tween(360, easing = emphasized), initialScale = 0.94f)
    fun modalExit(): ExitTransition =
        fadeOut(tween(170)) + scaleOut(tween(220, easing = emphasized), targetScale = 0.96f)

    /// Следующий экран въезжает справа целиком; назад — уезжает вправо.
    fun pushEnter(): EnterTransition = slideInHorizontally(tween(360, easing = emphasized)) { it }
    fun pushExit(): ExitTransition = slideOutHorizontally(tween(300, easing = emphasized)) { it }

    /// Лист снизу: выезжает и уезжает по вертикали.
    fun sheetEnter(): EnterTransition =
        slideInVertically(tween(400, easing = emphasized)) { it } + fadeIn(tween(160))
    fun sheetExit(): ExitTransition =
        slideOutVertically(tween(300, easing = emphasized)) { it } + fadeOut(tween(220, delayMillis = 80))
    fun <T> fade(duration: Int = 280) = tween<T>(durationMillis = duration, easing = smooth)

    const val STAGGER_MS = 30
}

/// Фон приложения — ровный чёрный: акцент живёт только в элементах.
@Composable
fun AppBackground(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Theme.base)) { content() }
}
