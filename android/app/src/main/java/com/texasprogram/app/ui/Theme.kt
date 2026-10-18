package com.texasprogram.app.ui

import android.content.Context
import android.content.SharedPreferences
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
    REBRN_BLACK(
        "REBRN Чёрная (OLED)",
        "Глубокий матовый OLED-чёрный фон, графитовые карточки и фирменный пламенный оранжевый акцент REBRN"
    ),
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
    var appContext: Context? = null
    var style by mutableStateOf(AppThemeStyle.REBRN_BLACK)

    fun init(context: Context) {
        appContext = context.applicationContext
        val p = context.getSharedPreferences("strain.theme", Context.MODE_PRIVATE)
        prefs = p
        val migratedKey = "has_migrated_to_rebrn_black_v2"
        if (!p.getBoolean(migratedKey, false)) {
            p.edit().putBoolean(migratedKey, true).putString("theme_style", AppThemeStyle.REBRN_BLACK.name).apply()
            style = AppThemeStyle.REBRN_BLACK
            return
        }
        val raw = p.getString("theme_style", AppThemeStyle.REBRN_BLACK.name)
        style = try {
            AppThemeStyle.valueOf(raw ?: AppThemeStyle.REBRN_BLACK.name)
        } catch (_: Exception) {
            AppThemeStyle.REBRN_BLACK
        }
    }

    fun set(newStyle: AppThemeStyle) {
        style = newStyle
        prefs?.edit()?.putString("theme_style", newStyle.name)?.apply()
    }

    val isSystemDark: Boolean
        get() {
            val ctx = appContext ?: return false
            val mask = ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            return mask == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }

    val isDark: Boolean
        get() = when (style) {
            AppThemeStyle.REBRN_BLACK -> true
            AppThemeStyle.CLAUDE_LIGHT -> false
            AppThemeStyle.CLAUDE_DARK -> true
            AppThemeStyle.STRAIN_DARK -> true
            AppThemeStyle.SYSTEM -> isSystemDark
        }

    val isRebrn: Boolean
        get() = style == AppThemeStyle.REBRN_BLACK

    val isClaude: Boolean
        get() = style == AppThemeStyle.CLAUDE_LIGHT || style == AppThemeStyle.CLAUDE_DARK || style == AppThemeStyle.SYSTEM
}

/// Динамическая палитра оформления, поддерживающая темы Claude, Strain и REBRN.
object Theme {
    val isRebrn: Boolean get() = ThemeManager.isRebrn
    val isClaude: Boolean get() = ThemeManager.isClaude
    val isDark: Boolean get() = ThemeManager.isDark

    val accent: Color
        get() = when {
            isRebrn -> Color(0xFFFF6029)
            isClaude -> Color(0xFFD97757)
            else -> Color(0xFF29D6C2)
        }

    val accentDeep: Color
        get() = when {
            isRebrn -> Color(0xFFE0471A)
            isClaude -> Color(0xFFC16141)
            else -> Color(0xFF5978FA)
        }

    val success: Color
        get() = when {
            isRebrn -> Color(0xFF2ED573)
            isClaude -> Color(0xFF4CB079)
            else -> Color(0xFF45D987)
        }

    val warning: Color
        get() = when {
            isRebrn -> Color(0xFFFFA533)
            isClaude -> Color(0xFFEBA048)
            else -> Color(0xFFFFA342)
        }

    val record: Color
        get() = when {
            isRebrn -> Color(0xFFFF5252)
            isClaude -> Color(0xFFE25E4C)
            else -> Color(0xFFFF6B85)
        }

    val base: Color
        get() = when {
            // Как у REBRN и в iOS-версии: чистый чёрный, а не почти чёрный.
            isRebrn -> Color(0xFF000000)
            !isClaude -> Color(0xFF0B0E13)
            isDark -> Color(0xFF1B1917)
            else -> Color(0xFFFBF8F3)
        }

    val textPrimary: Color
        get() = when {
            isRebrn -> Color(0xFFFFFFFF)
            !isClaude -> Color(0xFFF2F5F8)
            isDark -> Color(0xFFF5F3EF)
            else -> Color(0xFF1E1D1B)
        }

    val textSecondary: Color
        get() = when {
            isRebrn -> Color(0xFF9E9EA4)
            !isClaude -> Color(0xFF98A2B0)
            isDark -> Color(0xFFA69F97)
            else -> Color(0xFF6E6A64)
        }

    val textTertiary: Color
        get() = when {
            isRebrn -> Color(0xFF606068)
            !isClaude -> Color(0xFF6B7482)
            isDark -> Color(0xFF756F68)
            else -> Color(0xFF9E9991)
        }

    val surface: Color
        get() = when {
            isRebrn -> Color(0xFF161617)
            !isClaude -> Color.White.copy(alpha = 0.055f)
            isDark -> Color(0xFF262422)
            else -> Color(0xFFFFFFFF)
        }

    val card: Color
        get() = surface

    val surfaceSoft: Color
        get() = when {
            isRebrn -> Color(0xFF1C1C1F)
            !isClaude -> Color(0x0DFFFFFF)
            isDark -> Color(0xFF32302D)
            else -> Color(0xFFF3EEE7)
        }

    val surfaceElevated: Color
        get() = when {
            isRebrn -> Color(0xFF242428)
            !isClaude -> Color(0x1AFFFFFF)
            isDark -> Color(0xFF3A3733)
            else -> Color(0xFFEBE6DD)
        }

    val dialog: Color
        get() = when {
            isRebrn -> Color(0xFF18181B)
            !isClaude -> Color(0xFF161B24)
            isDark -> Color(0xFF262422)
            else -> Color(0xFFFFFFFF)
        }

    val hairline: Color
        get() = when {
            isRebrn -> Color(0x1FFFFFFF)
            !isClaude -> Color(0x1FFFFFFF)
            isDark -> Color(0x28FFFFFF)
            else -> Color(0x14000000)
        }

    val hairlineGradient: Brush
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

    // REBRN — ровный чёрный без пятен: три радиальных градиента на весь
    // экран перерисовывались под каждым кадром прокрутки, а видно их не было.
    if (Theme.isRebrn) {
        Box(Modifier.fillMaxSize().background(base)) { content() }
        return
    }

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
