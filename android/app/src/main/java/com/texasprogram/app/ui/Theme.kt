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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.PI

/// Две темы — тёмная REBRN (чистый OLED-чёрный, графитовые карточки) и светлая
/// (светло-серый фон, белые карточки) с одним пламенным оранжевым акцентом, как
/// в iOS-версии. Цвета ниже — не константы: каждый смотрит на `isLight`, а
/// чтение в композиции подписывает экран на смену темы. `ink` — текст и значки,
/// `canvas` — фон экрана, `tone` — серые ступени тёмной темы, которые в светлой
/// зеркалятся.
object Theme {
    val accent = Color(0xFFFF6029)
    val accentDeep = Color(0xFFE0471A)
    val success = Color(0xFF2ED573)
    val warning = Color(0xFFFFA533)
    val record = Color(0xFFFF5252)

    /// Светлая тема сейчас на экране. Ставит MainActivity — из настройки и
    /// темы телефона.
    var isLight by mutableStateOf(false)

    /// Неизменная тёмная палитра — для того, что всегда тёмное: фото на весь
    /// экран, «Итоги» историями, карточки для сторис.
    object Dark {
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
    }

    private val inkLight = Color(0xFF0D0D0E)

    /// Текст и значки: белые в тёмной теме, почти чёрные в светлой.
    val ink: Color get() = if (isLight) inkLight else Color.White
    /// Фон экрана: чёрный в тёмной теме, светло-серый в светлой.
    val canvas: Color get() = if (isLight) Color(0xFFF2F2F6) else Color.Black

    /// Чистый чёрный, а не почти чёрный.
    val base: Color get() = canvas
    val textPrimary: Color get() = ink
    val textSecondary: Color get() = if (isLight) Color(0xFF6C6C72) else Dark.textSecondary
    val textTertiary: Color get() = if (isLight) Color(0xFF9A9AA1) else Dark.textTertiary

    val surface: Color get() = if (isLight) Color.White else Dark.surface
    val card: Color get() = surface
    val surfaceSoft: Color get() = if (isLight) Color(0xFFF4F4F7) else Dark.surfaceSoft
    val surfaceElevated: Color get() = if (isLight) Color(0xFFEDEDF1) else Dark.surfaceElevated
    val dialog: Color get() = if (isLight) Color.White else Dark.dialog
    val hairline: Color get() = if (isLight) Color(0x14000000) else Dark.hairline

    val hairlineGradient: Brush
        get() = Brush.linearGradient(
            listOf(ink.copy(alpha = if (isLight) 0.08f else 0.16f), ink.copy(alpha = 0.04f)),
            Offset.Zero,
            Offset.Infinite
        )
    val accentGradient = Brush.linearGradient(listOf(accent, accentDeep), Offset.Zero, Offset.Infinite)
    val deepGradient = Brush.linearGradient(listOf(accentDeep, accent), Offset.Zero, Offset.Infinite)
    val successGradient = Brush.linearGradient(listOf(success, accent), Offset.Zero, Offset.Infinite)
    val recordGradient = Brush.linearGradient(listOf(warning, record), Offset.Zero, Offset.Infinite)

    /// Серый тёмной темы (`0xFF161617`) и его пара в светлой. Фон (почти чёрный)
    /// становится светло-серым, карточки (графит) — белыми, а поля и кнопки на
    /// карточках (светлее графита) — чуть темнее белого. Средние и светлые серые —
    /// значки, заглушки картинок — одинаковы в обеих темах. Так же, как на iOS.
    fun tone(argb: Long): Color {
        val dark = Color(argb)
        if (!isLight) return dark
        val white = (dark.red + dark.green + dark.blue) / 3f
        return Color(lightTone(white), lightTone(white), lightTone(white), dark.alpha)
    }

    fun lightTone(white: Float): Float = when {
        white < 0.03f -> 0.949f
        white < 0.115f -> 1f
        white < 0.3f -> maxOf(0.82f, 1f - (white - 0.08f) * 0.9f)
        else -> white
    }
}

/// Цвета Material-компонентов (переключатели, меню, поля ввода) под тему.
/// Белое на акценте: иначе у включённых переключателей тёмно-фиолетовый кружок.
fun materialColors(light: Boolean): androidx.compose.material3.ColorScheme =
    if (light) {
        androidx.compose.material3.lightColorScheme(
            primary = Theme.accent,
            onPrimary = Color.White,
            background = Color(0xFFF2F2F6),
            surface = Color.White,
            onSurface = Color(0xFF0D0D0E),
            onBackground = Color(0xFF0D0D0E)
        )
    } else {
        androidx.compose.material3.darkColorScheme(
            primary = Theme.accent,
            onPrimary = Color.White,
            background = Theme.Dark.base,
            surface = Theme.Dark.surface,
            onSurface = Theme.Dark.textPrimary
        )
    }

/// Тема приложения на выбор в настройках. По умолчанию тёмная — как было.
enum class AppAppearance(val title: String) {
    DARK("Тёмная"), LIGHT("Светлая"), SYSTEM("Как в системе");

    companion object {
        private const val PREFS = "strain.ui"
        private const val KEY = "appearance"

        var current by mutableStateOf(DARK)
            private set
        private var loaded = false

        fun load(context: android.content.Context): AppAppearance {
            if (!loaded) {
                val saved = context.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getString(KEY, null)
                current = entries.firstOrNull { it.name == saved } ?: DARK
                loaded = true
            }
            return current
        }

        fun set(context: android.content.Context, value: AppAppearance) {
            context.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putString(KEY, value.name).apply()
            loaded = true
            current = value
        }
    }

    fun isLight(systemDark: Boolean): Boolean = when (this) {
        DARK -> false
        LIGHT -> true
        SYSTEM -> !systemDark
    }
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
