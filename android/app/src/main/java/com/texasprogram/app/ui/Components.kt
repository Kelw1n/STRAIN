package com.texasprogram.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import kotlinx.coroutines.delay
import kotlin.math.abs

// MARK: - Модификаторы

/// Каскадное появление: прозрачность и сдвиг вверх с задержкой по индексу.
fun Modifier.appearIn(index: Int): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index.coerceAtMost(12) * Motion.STAGGER_MS.toLong())
        progress.animateTo(1f, Motion.appear())
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 18.dp.toPx()
    }
}

/// Нажатие с пружинным сжатием — аналог PressableStyle из SwiftUI.
fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, Motion.snappy(), label = "press")
    graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

/// Тап и долгое нажатие с той же анимацией. Отдельная функция нужна потому,
/// что `clickable` и `combinedClickable` — разные модификаторы.
fun Modifier.combinedPressable(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, Motion.snappy(), label = "press")
    graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            onLongClick = onLongClick,
            onClick = onClick
        )
}

/// Мягкое затухание у краёв экрана. Состояние читается в фазе отрисовки,
/// поэтому прокрутка не вызывает рекомпозицию.
fun Modifier.softScroll(state: LazyListState, index: Int): Modifier = graphicsLayer {
    val info = state.layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return@graphicsLayer
    val viewportStart = info.viewportStartOffset.toFloat()
    val viewportEnd = info.viewportEndOffset.toFloat()
    val itemCenter = item.offset + item.size / 2f
    val viewportCenter = (viewportStart + viewportEnd) / 2f
    val half = (viewportEnd - viewportStart) / 2f
    if (half <= 0f) return@graphicsLayer
    val distance = (abs(itemCenter - viewportCenter) / half).coerceIn(0f, 1f)
    val fade = ((distance - 0.72f) / 0.28f).coerceIn(0f, 1f)
    alpha = 1f - fade * 0.7f
    val shrink = 1f - fade * 0.06f
    scaleX = shrink
    scaleY = shrink
}

@Composable
fun rememberHaptics(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
    }
}

// MARK: - Карточки

@Composable
fun CardView(
    modifier: Modifier = Modifier,
    padding: Dp = 18.dp,
    spacing: Dp = 14.dp,
    corner: Dp = 24.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner))
            .background(Theme.surface)
            .border(1.dp, Theme.hairlineGradient, RoundedCornerShape(corner))
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content
    )
}

/// Карточка с градиентной рамкой — для «следующего» элемента.
@Composable
fun HighlightCard(
    modifier: Modifier = Modifier,
    gradient: Brush = Theme.accentGradient,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Theme.surface)
            .border(1.6.dp, gradient, RoundedCornerShape(26.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

// MARK: - Индикаторы

@Composable
fun RingProgress(
    value: Float,
    modifier: Modifier = Modifier,
    lineWidth: Dp = 12.dp,
    gradient: Brush = Theme.accentGradient,
    content: @Composable () -> Unit
) {
    val animated = remember { Animatable(0f) }
    LaunchedEffect(value) {
        animated.animateTo(value.coerceIn(0f, 1f), Motion.card())
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = lineWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = Color.White.copy(alpha = 0.09f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
            if (animated.value > 0.001f) {
                drawArc(
                    brush = gradient,
                    startAngle = -90f,
                    sweepAngle = 360f * animated.value,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
            }
        }
        content()
    }
}

@Composable
fun LineMeter(
    value: Float,
    modifier: Modifier = Modifier,
    gradient: Brush = Theme.accentGradient,
    height: Dp = 8.dp
) {
    val animated = remember { Animatable(0f) }
    LaunchedEffect(value) { animated.animateTo(value.coerceIn(0f, 1f), Motion.card()) }
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.09f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated.value)
                .height(height)
                .clip(CircleShape)
                .background(gradient)
        )
    }
}

// MARK: - Бейджи

@Composable
fun TagBadge(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    gradient: Brush = Theme.accentGradient
) {
    Row(
        modifier
            .clip(CircleShape)
            .background(gradient)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
        }
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun OutlineBadge(text: String, modifier: Modifier = Modifier, tint: Color = Theme.accent) {
    Text(
        text,
        color = tint,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f))
            .border(1.dp, tint.copy(alpha = 0.35f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

// MARK: - Текст

@Composable
fun ScreenHeader(eyebrow: String, title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GradientText(eyebrow.uppercase(), fontSize = 12.sp, weight = FontWeight.Bold, letterSpacing = 1.4f)
        Text(title, color = Theme.textPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Text(subtitle, color = Theme.textSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
fun GradientText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    weight: FontWeight = FontWeight.Bold,
    gradient: Brush = Theme.accentGradient,
    letterSpacing: Float = 0f,
    textAlign: TextAlign? = null
) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = fontSize,
        fontWeight = weight,
        letterSpacing = letterSpacing.sp,
        textAlign = textAlign,
        style = androidx.compose.ui.text.TextStyle(brush = gradient)
    )
}

// MARK: - Навигация

/// Шапка как в iOS: шеврон слева, заголовок по центру.
@Composable
fun TopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(44.dp)
    ) {
        if (onBack != null) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .size(40.dp)
                    .clip(CircleShape)
                    .pressable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowLeft,
                    contentDescription = "Назад",
                    tint = Theme.textPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Text(
            title,
            color = Theme.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.align(Alignment.Center)
        )
        if (trailing != null) {
            Box(Modifier.align(Alignment.CenterEnd)) { trailing() }
        }
    }
}

// MARK: - Сегментный переключатель

/// Нейтральный серый, как системный segmented control в iOS,
/// а не акцентный градиент.
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEachIndexed { index, title ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) Color.White.copy(alpha = 0.18f) else Color.Transparent)
                    .pressable { onSelect(index) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    title,
                    color = Theme.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

// MARK: - Поле веса

/// Строка веса как в iOS: подпись, минус, редактируемое значение, плюс.
@Composable
fun WeightStepper(
    label: String,
    text: String,
    modifier: Modifier = Modifier,
    step: Double = 2.5,
    onTextChange: (String) -> Unit
) {
    val current = text.replace(",", ".").toDoubleOrNull() ?: 0.0

    fun apply(delta: Double) {
        val next = (current + delta).coerceAtLeast(0.0)
        onTextChange(if (next == kotlin.math.floor(next)) next.toInt().toString() else next.toString())
    }

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = Theme.textPrimary,
            fontSize = 15.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(Modifier.weight(1f))
        StepCircle("−") { apply(-step) }
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = text,
                onValueChange = { new -> onTextChange(new.filter { it.isDigit() || it == '.' || it == ',' }) },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Theme.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End
                ),
                cursorBrush = SolidColor(Theme.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(52.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("кг", color = Theme.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(8.dp))
        StepCircle("+") { apply(step) }
    }
}

@Composable
private fun StepCircle(symbol: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Theme.accent.copy(alpha = 0.16f))
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, color = Theme.accent, fontSize = 17.sp, fontWeight = FontWeight.Black)
    }
}

// MARK: - Кнопки

@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    gradient: Brush = Theme.accentGradient,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(gradient)
            .alphaIf(!enabled)
            .pressable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Text("  ", fontSize = 16.sp)
        }
        Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = Theme.accent,
    onClick: () -> Unit
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.12f))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .pressable(onClick = onClick)
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Text("  ", fontSize = 13.sp)
        }
        Text(text, color = tint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun Modifier.alphaIf(dim: Boolean): Modifier = if (dim) this.graphicsLayer { alpha = 0.5f } else this
