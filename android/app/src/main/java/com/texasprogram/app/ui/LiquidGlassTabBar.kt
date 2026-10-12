package com.texasprogram.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs

/// Вкладки. Залитая иконка видна сквозь линзу, контурная — вне её, как в iOS-версии.
enum class MainTabItem(val title: String, val icon: ImageVector, val outlinedIcon: ImageVector) {
    TODAY("Главное", Icons.Filled.Home, Icons.Outlined.Home),
    WORKOUT("Тренировка", Icons.Filled.FitnessCenter, Icons.Outlined.FitnessCenter),
    PROGRESS("Активность", Icons.Filled.BarChart, Icons.Outlined.BarChart),
    COMMUNITY("Друзья", Icons.Filled.Groups, Icons.Outlined.Groups),
    PROFILE("Профиль", Icons.Filled.AccountCircle, Icons.Outlined.AccountCircle)
}

private val BAR_HEIGHT = 62.dp
private val REST_LENS_HEIGHT = 48.dp
/// На сколько поднятая линза выходит за верх и низ панели.
private val OVERHANG = 7.dp
private const val MAGNIFICATION = 1.28f

/// Пружины с параметрами iOS-версии: stiffness = (2π / response)².
private fun stiffness(response: Double): Float = ((2 * PI / response) * (2 * PI / response)).toFloat()
/// Посадка на вкладку: чуть недодемпфирована, чтобы линза качнулась, как капля.
private val settleSpring = spring<Float>(dampingRatio = 0.66f, stiffness = stiffness(0.42))
/// Подъём линзы в момент касания.
private val liftSpring = spring<Float>(dampingRatio = 0.72f, stiffness = stiffness(0.26))
/// Слежение за пальцем: почти без задержки, но не рывками.
private val followSpring = spring<Float>(dampingRatio = 0.86f, stiffness = stiffness(0.18))

/// Плавающая панель вкладок с линзой из жидкого стекла.
///
/// Палец можно поставить и вести: линза поднимается над панелью, едет за ним,
/// тянется по скорости и увеличивает иконки под собой. Отпустил — линза
/// с пружиной садится на ближайшую вкладку, и та открывается.
///
/// Иконки нарисованы дважды: нижний слой контурный, в линзе в нём дырка;
/// верхний залитый, виден только сквозь линзу и увеличен вокруг её центра.
///
/// Вся анимация — в фазе отрисовки: положение линзы читается только в
/// `draw`-лямбдах, поэтому ведение пальцем не перекомпоновывает ничего.
@Composable
fun LiquidGlassTabBar(
    selectedTab: MainTabItem,
    onTabSelected: (MainTabItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHapticEngine()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val tabs = remember { MainTabItem.entries.toList() }
    val selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)
    val currentSelected by rememberUpdatedState(selectedIndex)
    val currentOnSelect by rememberUpdatedState(onTabSelected)

    val lensX = remember { Animatable(Float.NaN) }
    val lift = remember { Animatable(0f) }
    val stretch = remember { Animatable(0f) }
    val touching = remember { BooleanArray(1) }
    val clip = remember { Path() }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 8.dp)
            .height(BAR_HEIGHT)
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val itemPx = widthPx / tabs.size
        val restLensH = with(density) { REST_LENS_HEIGHT.toPx() }
        val liftedLensH = with(density) { (BAR_HEIGHT + OVERHANG * 2).toPx() }
        val inset = with(density) { 8.dp.toPx() }
        val px = density.density

        fun center(index: Int) = itemPx * (index + 0.5f)
        fun indexAt(x: Float) = (x / itemPx).toInt().coerceIn(0, tabs.lastIndex)

        fun lensRect(heightPx: Float): Rect {
            val l = lift.value.coerceIn(0f, 1.2f)
            val s = stretch.value
            // Поднятая линза шире вкладки, как у REBRN: под её краем видно соседей.
            val w = (lerp(itemPx - inset, itemPx * 1.24f, l)) * (1 + s)
            val h = lerp(restLensH, liftedLensH, l) * (1 - s * 0.4f)
            val half = w / 2
            val raw = lensX.value.takeUnless { it.isNaN() } ?: center(currentSelected)
            val mid = if (widthPx > w + 4) raw.coerceIn(half + 2, widthPx - half - 2) else widthPx / 2
            val cy = heightPx / 2
            return Rect(mid - half, cy - h / 2, mid + half, cy + h / 2)
        }

        // Вкладку сменили не пальцем (кнопкой «Закрыть», ссылкой) — линза едет следом.
        LaunchedEffect(selectedIndex, widthPx) {
            if (touching[0]) return@LaunchedEffect
            if (lensX.value.isNaN()) lensX.snapTo(center(selectedIndex))
            else lensX.animateTo(center(selectedIndex), settleSpring)
        }

        // Подложка панели. Тень отдельным слоем: модификатор тени обрезает
        // содержимое по форме, а поднятая линза выходит за край панели.
        Spacer(
            Modifier
                .matchParentSize()
                .shadow(22.dp, CircleShape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
                .background(Color(0xF0141417), CircleShape)
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.28f),
                        0.35f to Color.White.copy(alpha = 0.06f),
                        0.65f to Color.Transparent,
                        1f to Color.White.copy(alpha = 0.14f)
                    ),
                    CircleShape
                )
        )

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(tabs.size, widthPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        touching[0] = true
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var index = indexAt(down.position.x)
                        var lastX = down.position.x
                        haptics.light()
                        scope.launch {
                            if (lensX.value.isNaN()) lensX.snapTo(center(currentSelected))
                            lensX.animateTo(lastX, liftSpring)
                        }
                        scope.launch { lift.animateTo(1f, liftSpring) }
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    lastX = change.position.x
                                    break
                                }
                                change.consume()
                                tracker.addPosition(change.uptimeMillis, change.position)
                                lastX = change.position.x
                                val target = lastX
                                scope.launch { lensX.animateTo(target, followSpring) }
                                // Растяжение по скорости: быстро ведёшь — капля вытягивается.
                                val velocity = abs(tracker.calculateVelocity().x) / px
                                scope.launch { stretch.animateTo(minOf(velocity / 2600f, 0.24f), followSpring) }
                                val next = indexAt(lastX)
                                if (next != index) {
                                    index = next
                                    haptics.selection()
                                }
                            }
                        } finally {
                            touching[0] = false
                            val target = indexAt(lastX)
                            if (target != currentSelected) {
                                haptics.medium()
                                currentOnSelect(tabs[target])
                            }
                            scope.launch { lensX.animateTo(center(target), settleSpring) }
                            scope.launch { lift.animateTo(0f, settleSpring) }
                            scope.launch { stretch.animateTo(0f, settleSpring) }
                        }
                    }
                }
        ) {
            // Нижний слой: контурные иконки с дыркой там, где линза.
            IconRow(
                tabs = tabs,
                filled = false,
                selectedIndex = selectedIndex,
                onSelect = { currentOnSelect(it) },
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val r = lensRect(size.height)
                        drawRoundRect(
                            color = Color.Black,
                            topLeft = r.topLeft,
                            size = r.size,
                            cornerRadius = CornerRadius(r.height / 2),
                            blendMode = BlendMode.Clear
                        )
                    }
            )

            // Тело линзы.
            Spacer(
                Modifier
                    .matchParentSize()
                    .drawBehind { drawLens(lensRect(size.height), lift.value.coerceIn(0f, 1f)) }
            )

            // Верхний слой: залитые иконки, видны только сквозь линзу и увеличены.
            IconRow(
                tabs = tabs,
                filled = true,
                selectedIndex = selectedIndex,
                onSelect = null,
                modifier = Modifier
                    .matchParentSize()
                    .drawWithContent {
                        val r = lensRect(size.height)
                        clip.reset()
                        clip.addRoundRect(RoundRect(r, CornerRadius(r.height / 2)))
                        val magnify = 1f + (MAGNIFICATION - 1f) * lift.value.coerceIn(0f, 1f)
                        clipPath(clip) {
                            scale(magnify, Offset(r.center.x, size.height / 2)) {
                                this@drawWithContent.drawContent()
                            }
                        }
                    }
            )
        }
    }
}

@Composable
private fun IconRow(
    tabs: List<MainTabItem>,
    filled: Boolean,
    selectedIndex: Int,
    onSelect: ((MainTabItem) -> Unit)?,
    modifier: Modifier
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        tabs.forEachIndexed { index, tab ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(
                        if (onSelect != null) {
                            // Нажатия ловит панель целиком; здесь — только для экранного диктора.
                            Modifier.semantics {
                                role = Role.Tab
                                selected = index == selectedIndex
                                contentDescription = tab.title
                                onClick { onSelect(tab); true }
                            }
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (filled) tab.icon else tab.outlinedIcon,
                    contentDescription = null,
                    tint = if (filled) Color.White else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

private val rimColors = listOf(
    Color(0xFFFF7A7A), Color(0xFFFFD27A), Color(0xFF7AFFC0),
    Color(0xFF7AC8FF), Color(0xFFC77AFF), Color(0xFFFF7A7A)
)

/// В покое линза — тёмная таблетка, как у REBRN; поднятая — прозрачное
/// стекло с бликом сверху и радужной каймой по краю, как стекло iOS 26.
private fun DrawScope.drawLens(r: Rect, l: Float) {
    val radius = CornerRadius(r.height / 2)
    if (l < 1f) {
        drawRoundRect(Color.Black.copy(alpha = 0.55f * (1f - l)), r.topLeft, r.size, radius)
    }
    if (l > 0f) {
        drawRoundRect(Color.White.copy(alpha = 0.07f * l), r.topLeft, r.size, radius)
        val pad = 2.dp.toPx()
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.24f * l), Color.Transparent),
                startY = r.top,
                endY = r.center.y
            ),
            topLeft = Offset(r.left + pad, r.top + pad),
            size = Size(r.width - pad * 2, r.height - pad * 2),
            cornerRadius = CornerRadius((r.height - pad * 2) / 2)
        )
        val rim = 2.dp.toPx()
        drawRoundRect(
            brush = Brush.sweepGradient(rimColors.map { it.copy(alpha = 0.32f * l) }, center = r.center),
            topLeft = Offset(r.left + rim / 2, r.top + rim / 2),
            size = Size(r.width - rim, r.height - rim),
            cornerRadius = CornerRadius((r.height - rim) / 2),
            style = Stroke(rim)
        )
    }
    val stroke = lerp(1.dp.toPx(), 1.2.dp.toPx(), l)
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = lerp(0.12f, 0.75f, l)),
            0.45f to Color.White.copy(alpha = lerp(0.04f, 0.12f, l)),
            1f to Color.White.copy(alpha = lerp(0.06f, 0.35f, l)),
            startY = r.top,
            endY = r.bottom
        ),
        topLeft = Offset(r.left + stroke / 2, r.top + stroke / 2),
        size = Size(r.width - stroke, r.height - stroke),
        cornerRadius = CornerRadius((r.height - stroke) / 2),
        style = Stroke(stroke)
    )
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
