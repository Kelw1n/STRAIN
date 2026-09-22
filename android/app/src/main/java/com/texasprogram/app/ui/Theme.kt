package com.texasprogram.app.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.PI

/// Стили оформления приложения, идентичные iOS-версии.
enum class AppThemeStyle(val displayName: String, val description: String) {
    CLAUDE_LIGHT(
        "Claude Светлая",
        "Нежный молочно-пергаментный фон, карточки слоновой кости и тёплый терракотовый акцент"
    ),
    CLAUDE_DARK(
        "Claude Тёмная",
        "Матовый глубокий графит, бархатный антрацит и тлеющий терракотово-оранжевый"
    ),
    STRAIN_DARK(
        "STRAIN Неон",
        "Оригинальный неоновый бирюзово-синий кибер-стиль"
    ),
    SYSTEM(
        "Системная (Claude)",
        "Автоматически переключает между светлой и тёмной темой Claude в зависимости от настроек Android"
    )
}

/// Управление темой на Android с сохранением в SharedPreferences.
object ThemeManager {
    private var prefs: SharedPreferences? = null
    var style by mutableStateOf(AppThemeStyle.CLAUDE_LIGHT)

    fun init(context: Context) {
        val p = context.getSharedPreferences("strain.theme", Context.MODE_PRIVATE)
        prefs = p
        val raw = p.getString("theme_style", AppThemeStyle.CLAUDE_LIGHT.name)
        style = try {
            AppThemeStyle.valueOf(raw ?: AppThemeStyle.CLAUDE_LIGHT.name)
        } catch (_: Exception) {
            AppThemeStyle.CLAUDE_LIGHT
        }
    }

    fun set(newStyle: AppThemeStyle) {
        style = newStyle
        prefs?.edit()?.putString("theme_style", newStyle.name)?.apply()
    }

    val isDark: Boolean
        @Composable
        get() = when (style) {
            AppThemeStyle.CLAUDE_LIGHT -> false
            AppThemeStyle.CLAUDE_DARK -> true
            AppThemeStyle.STRAIN_DARK -> true
            AppThemeStyle.SYSTEM -> isSystemInDarkTheme()
        }

    val isClaude: Boolean
        get() = style != AppThemeStyle.STRAIN_DARK
}

/// Динамическая палитра оформления, поддерживающая темы Claude и Strain.
object Theme {
    val isClaude: Boolean get() = ThemeManager.isClaude
    val isDark: Boolean @Composable get() = ThemeManager.isDark

    val accent: Color
        get() = if (isClaude) Color(0xFFD97757) else Color(0xFF29D6C2)

    val accentDeep: Color
        get() = if (isClaude) Color(0xFFC16141) else Color(0xFF5978FA)

    val success: Color
        get() = if (isClaude) Color(0xFF4CB079) else Color(0xFF45D987)

    val warning: Color
        get() = if (isClaude) Color(0xFFEBA048) else Color(0xFFFFA342)

    val record: Color
        get() = if (isClaude) Color(0xFFE25E4C) else Color(0xFFFF6B85)

    val base: Color
        @Composable
        get() = when {
            !isClaude -> Color(0xFF0B0E13)
            isDark -> Color(0xFF1B1917)
            else -> Color(0xFFFBF8F3)
        }

    val textPrimary: Color
        @Composable
        get() = when {
            !isClaude -> Color(0xFFF2F5F8)
            isDark -> Color(0xFFF5F3EF)
            else -> Color(0xFF1E1D1B)
        }

    val textSecondary: Color
        @Composable
        get() = when {
            !isClaude -> Color(0xFF98A2B0)
            isDark -> Color(0xFFA69F97)
            else -> Color(0xFF6E6A64)
        }

    val textTertiary: Color
        @Composable
        get() = when {
            !isClaude -> Color(0xFF6B7482)
            isDark -> Color(0xFF756F68)
            else -> Color(0xFF9E9991)
        }

    val surface: Color
        @Composable
        get() = when {
            !isClaude -> Color.White.copy(alpha = 0.055f)
            isDark -> Color(0xFF262422)
            else -> Color(0xFFFFFFFF)
        }

    val surfaceSoft: Color
        @Composable
        get() = when {
            !isClaude -> Color(0x0DFFFFFF)
            isDark -> Color(0xFF32302D)
            else -> Color(0xFFF3EEE7)
        }

    val dialog: Color
        @Composable
        get() = when {
            !isClaude -> Color(0xFF161B24)
            isDark -> Color(0xFF262422)
            else -> Color(0xFFFFFFFF)
        }

    val hairline: Color
        @Composable
        get() = when {
            !isClaude -> Color(0x1FFFFFFF)
            isDark -> Color(0x28FFFFFF)
            else -> Color(0x14000000)
        }

    val hairlineGradient: Brush
        @Composable
        get() = if (!isDark) {
            Brush.linearGradient(
                listOf(Color(0xFFE0DBD3), Color(0xFFEDE8E1)),
                Offset.Zero,
                Offset.Infinite
            )
        } else if (isClaude) {
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.03f)),
                Offset.Zero,
                Offset.Infinite
            )
        } else {
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.04f)),
                Offset.Zero,
                Offset.Infinite
            )
        }

    val accentGradient: Brush
        get() = Brush.linearGradient(listOf(accent, accentDeep), Offset.Zero, Offset.Infinite)

    val deepGradient: Brush
        get() = Brush.linearGradient(listOf(accentDeep, accent), Offset.Zero, Offset.Infinite)

    val successGradient: Brush
        get() = Brush.linearGradient(listOf(success, accent), Offset.Zero, Offset.Infinite)

    val recordGradient: Brush
        get() = Brush.linearGradient(listOf(warning, record), Offset.Zero, Offset.Infinite)
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

/// Фон приложения: адаптивные радиальные пятна, зависящие от выбранной темы.
@Composable
fun AppBackground(content: @Composable () -> Unit) {
    val isClaude = Theme.isClaude
    val isDark = Theme.isDark
    val glow = if (!isClaude) 0.26f else if (isDark) 0.14f else 0.08f
    val base = Theme.base
    val accent = Theme.accent
    val accentDeep = Theme.accentDeep
    val record = Theme.record

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(base)
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = glow), Color.Transparent),
                        center = Offset(size.width * 0.08f, size.height * 0.02f),
                        radius = size.width * 1.10f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(accentDeep.copy(alpha = glow), Color.Transparent),
                        center = Offset(size.width * 0.98f, size.height),
                        radius = size.width * 1.20f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(record.copy(alpha = glow * 0.45f), Color.Transparent),
                        center = Offset(size.width, size.height * 0.12f),
                        radius = size.width * 0.82f
                    )
                )
            }
    ) {
        content()
    }
}
