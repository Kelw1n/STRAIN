package com.texasprogram.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.drawWithCache
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import kotlinx.coroutines.delay
import kotlin.math.abs

// MARK: - Модификаторы

/// Каскадное появление: прозрачность и сдвиг вверх с задержкой по индексу.
/// Только для первого экрана и один раз: список помнит, что карточку уже
/// показали, и при прокрутке назад она не проявляется заново.
fun Modifier.appearIn(index: Int): Modifier = composed {
    var shown by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (shown || index > 7) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            delay(index * Motion.STAGGER_MS.toLong())
            progress.animateTo(1f, Motion.appear())
        }
        shown = true
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 10.dp.toPx()
    }
}

/// Появление окна поверх экрана: проявляется и чуть приближается, как
/// всплывающие окна в iOS. Только слоем — компоновку не трогает.
fun Modifier.popIn(): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, Motion.sheet()) }
    graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        val scale = 0.92f + 0.08f * p
        scaleX = scale
        scaleY = scale
    }
}

/// Нажатие с пружинным сжатием — аналог PressableStyle из SwiftUI.
fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        if (pressed) Motion.press() else Motion.release(),
        label = "press"
    )
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
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        if (pressed) Motion.press() else Motion.release(),
        label = "press"
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            onLongClick = onLongClick,
            onClick = onClick
        )
}

/// Число, которое при смене прокатывается: больше — новое въезжает снизу,
/// меньше — сверху. Как `contentTransition(.numericText())` в SwiftUI.
@Composable
fun RollingNumber(
    value: Int,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight = FontWeight.Bold
) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            val up = targetState > initialState
            val enter = slideInVertically(Motion.snappy()) { if (up) it else -it } + fadeIn(tween(160))
            val exit = slideOutVertically(Motion.snappy()) { if (up) -it else it } + fadeOut(tween(120))
            (enter togetherWith exit).using(SizeTransform(clip = false))
        },
        label = "rollingNumber"
    ) { number ->
        Text("$number", color = color, fontSize = fontSize, fontWeight = fontWeight)
    }
}

/// Заливка выбранного: при смене выбора цвет плавно перетекает, а не
/// перескакивает. Прогресс читается только в отрисовке — без перекомпоновки.
fun Modifier.selectionFill(selected: Boolean, fill: Brush, idle: Color, shape: Shape): Modifier = composed {
    val progress = animateFloatAsState(if (selected) 1f else 0f, Motion.fade(200), label = "selection")
    drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        onDrawBehind {
            val p = progress.value
            if (p < 1f && idle.alpha > 0f) drawOutline(outline, idle)
            if (p > 0f) drawOutline(outline, fill, alpha = p)
        }
    }
}

fun Modifier.selectionFill(selected: Boolean, fill: Color, idle: Color, shape: Shape): Modifier =
    selectionFill(selected, SolidColor(fill), idle, shape)

/// Кружок с галочкой: при выборе галочка выпрыгивает с отскоком.
@Composable
fun CheckDot(selected: Boolean, size: Dp = 20.dp, iconSize: Dp = 12.dp) {
    val pop by animateFloatAsState(if (selected) 1f else 0f, Motion.bouncy(), label = "check")
    Box(
        Modifier.size(size).selectionFill(selected, Theme.accent, Theme.surfaceSoft, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(iconSize).graphicsLayer {
                scaleX = pop
                scaleY = pop
                alpha = pop.coerceIn(0f, 1f)
            }
        )
    }
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
    alpha = 1f - fade * 0.45f
    val shrink = 1f - fade * 0.02f
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
                color = Theme.surfaceSoft,
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
            .background(Theme.surfaceSoft)
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
                    .pressable(onClick = onBack)
                    .clip(CircleShape),
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
/// а не акцентный градиент. Подложка выбранного едет к новому сегменту
/// с пружиной; её положение читается только в отрисовке.
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    val position by animateFloatAsState(selectedIndex.toFloat(), Motion.snappy(), label = "segment")
    val card = Theme.card
    val hairline = Theme.hairline
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Theme.surfaceSoft)
            .border(1.dp, Theme.hairline, RoundedCornerShape(10.dp))
            .padding(3.dp)
            .drawBehind {
                if (selectedIndex !in options.indices) return@drawBehind
                val gap = 3.dp.toPx()
                val width = (size.width - gap * (options.size - 1)) / options.size
                val left = position * (width + gap)
                val radius = CornerRadius(8.dp.toPx())
                val half = 0.5.dp.toPx()
                drawRoundRect(card, Offset(left, 0f), Size(width, size.height), radius)
                drawRoundRect(
                    hairline,
                    Offset(left + half, half),
                    Size(width - half * 2, size.height - half * 2),
                    radius,
                    style = Stroke(1.dp.toPx())
                )
            },
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEachIndexed { index, title ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .weight(1f)
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
                .background(Theme.surfaceSoft)
                .border(1.dp, Theme.hairline, RoundedCornerShape(12.dp))
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
            .pressable(onClick = onClick)
            .clip(CircleShape)
            .background(Theme.accent.copy(alpha = 0.16f)),
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
            .pressable(enabled = enabled, onClick = onClick)
            .clip(RoundedCornerShape(18.dp))
            .background(gradient)
            .alphaIf(!enabled)
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
            .pressable(onClick = onClick)
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.12f))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
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
