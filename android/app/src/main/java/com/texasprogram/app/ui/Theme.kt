package com.texasprogram.app.ui

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.PI

/// Палитра один в один с iOS-версией.
object Theme {
    val accent = Color(0xFF29D6C2)
    val accentDeep = Color(0xFF5978FA)
    val success = Color(0xFF45D987)
    val warning = Color(0xFFFFA342)
    val record = Color(0xFFFF6B85)

    val base = Color(0xFF0B0E13)
    val textPrimary = Color(0xFFF2F5F8)
    val textSecondary = Color(0xFF98A2B0)
    val textTertiary = Color(0xFF6B7482)

    /// Заливка карточки полупрозрачная, как в iOS: сквозь неё просвечивают
    /// цветные пятна фона, и карточки получают тот же градиентный оттенок.
    /// Сплошной цвет гасил фон и делал вкладки плоскими.
    val surface = Color.White.copy(alpha = 0.055f)
    val surfaceSoft = Color(0x0DFFFFFF)

    /// Заливка диалога — сплошная, в отличие от карточки.
    ///
    /// Карточка лежит на фоне приложения, и прозрачность ей идёт. Диалог висит
    /// поверх произвольного содержимого: с той же полупрозрачной заливкой сквозь
    /// него читается всё, что под ним.
    val dialog = Color(0xFF161B24)
    val hairline = Color(0x1FFFFFFF)

    /// Рамка карточки: сверху слева светлее, снизу справа почти не видна.
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

    fun <T> card(): SpringSpec<T> = spring(dampingRatio = 0.86f, stiffness = stiffnessFor(0.46))
    fun <T> snappy(): SpringSpec<T> = spring(dampingRatio = 0.78f, stiffness = stiffnessFor(0.32))
    fun <T> bouncy(): SpringSpec<T> = spring(dampingRatio = 0.62f, stiffness = stiffnessFor(0.42))
    fun <T> appear(): SpringSpec<T> = spring(dampingRatio = 0.85f, stiffness = stiffnessFor(0.55))

    val smooth: Easing = FastOutSlowInEasing
    fun <T> fade(duration: Int = 280) = tween<T>(durationMillis = duration, easing = smooth)

    const val STAGGER_MS = 50
}

/// Фон приложения: три статичных радиальных пятна на тех же относительных
/// позициях, что в iOS. Позиции и радиусы считаются от размера экрана, а не
/// в абсолютных пикселях — иначе на разных телефонах пятна уезжают.
@Composable
fun AppBackground(content: @Composable () -> Unit) {
    val glow = 0.26f
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(Theme.base)
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Theme.accent.copy(alpha = glow), Color.Transparent),
                        center = Offset(size.width * 0.08f, size.height * 0.02f),
                        radius = size.width * 1.10f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Theme.accentDeep.copy(alpha = glow), Color.Transparent),
                        center = Offset(size.width * 0.98f, size.height),
                        radius = size.width * 1.20f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Theme.record.copy(alpha = glow * 0.45f), Color.Transparent),
                        center = Offset(size.width, size.height * 0.12f),
                        radius = size.width * 0.82f
                    )
                )
            }
    ) {
        content()
    }
}
