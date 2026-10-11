package com.texasprogram.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

enum class MainTabItem(val title: String, val icon: ImageVector) {
    TODAY("Главное", Icons.Filled.Home),
    WORKOUT("Тренировка", Icons.Filled.FitnessCenter),
    PROGRESS("Активность", Icons.Filled.BarChart),
    COMMUNITY("Друзья", Icons.Filled.Group),
    PROFILE("Профиль", Icons.Filled.Person)
}

@Composable
fun LiquidGlassTabBar(
    selectedTab: MainTabItem,
    onTabSelected: (MainTabItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val tabs = remember { MainTabItem.entries }
    var isInteracting by remember { mutableStateOf(false) }
    var hoveredIndex by remember { mutableIntStateOf(tabs.indexOf(selectedTab).coerceAtLeast(0)) }
    var dragXPx by remember { mutableStateOf<Float?>(null) }

    val activeIndex = if (isInteracting) hoveredIndex else tabs.indexOf(selectedTab).coerceAtLeast(0)
    val activeTab = tabs.getOrElse(activeIndex) { selectedTab }

    val barHeight = 62.dp
    val lensHeight = 48.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .height(barHeight + 46.dp), // Место под плавающий HUD pill
        contentAlignment = Alignment.BottomCenter
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
        ) {
            val totalWidthPx = constraints.maxWidth.toFloat()
            val itemWidthPx = if (tabs.isNotEmpty()) totalWidthPx / tabs.size else totalWidthPx
            val density = LocalDensity.current
            val itemWidthDp = with(density) { itemWidthPx.toDp() }
            val lensWidthDp = (itemWidthDp - 8.dp).coerceAtLeast(20.dp)
            val lensWidthPx = with(density) { lensWidthDp.toPx() }
            val lensPaddingPx = with(density) { 4.dp.toPx() }

            val targetLensOffsetPx = if (isInteracting && dragXPx != null) {
                (dragXPx!! - lensWidthPx / 2f).coerceIn(lensPaddingPx, totalWidthPx - lensWidthPx - lensPaddingPx)
            } else {
                activeIndex * itemWidthPx + lensPaddingPx
            }

            val animatedLensOffsetPx by animateFloatAsState(
                targetValue = targetLensOffsetPx,
                animationSpec = spring(
                    dampingRatio = if (isInteracting) 0.82f else 0.72f,
                    stiffness = if (isInteracting) 600f else 420f
                ),
                label = "lensOffset"
            )

            val barScale by animateFloatAsState(
                targetValue = if (isInteracting) 0.985f else 1.0f,
                animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
                label = "barScale"
            )

            // Основная капсула дока
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(barScale)
                    .shadow(
                        elevation = 20.dp,
                        shape = RoundedCornerShape(32.dp),
                        ambientColor = Color.Black,
                        spotColor = Color.Black
                    )
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color(0xE6141416))
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.35f),
                                Color.White.copy(alpha = 0.08f),
                                Color.Transparent,
                                Color.White.copy(alpha = 0.18f)
                            )
                        ),
                        shape = RoundedCornerShape(32.dp)
                    )
                    .pointerInput(tabs) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                isInteracting = true
                                dragXPx = offset.x
                                val idx = ((offset.x / itemWidthPx).toInt()).coerceIn(0, tabs.size - 1)
                                if (idx != hoveredIndex) {
                                    hoveredIndex = idx
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                dragXPx = change.position.x
                                val idx = ((change.position.x / itemWidthPx).toInt()).coerceIn(0, tabs.size - 1)
                                if (idx != hoveredIndex) {
                                    hoveredIndex = idx
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                            onDragEnd = {
                                if (hoveredIndex in tabs.indices) {
                                    onTabSelected(tabs[hoveredIndex])
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                isInteracting = false
                                dragXPx = null
                            },
                            onDragCancel = {
                                isInteracting = false
                                dragXPx = null
                            }
                        )
                    }
            ) {
                // Скользящая физическая стеклянная линза
                val lensScale by animateFloatAsState(
                    targetValue = if (isInteracting) 1.05f else 1.0f,
                    animationSpec = spring(dampingRatio = 0.70f, stiffness = 450f),
                    label = "lensScale"
                )

                Box(
                    modifier = Modifier
                        .offset { IntOffset(animatedLensOffsetPx.roundToInt(), with(density) { 7.dp.toPx() }.roundToInt()) }
                        .width(lensWidthDp)
                        .height(lensHeight)
                        .scale(lensScale)
                        .shadow(
                            elevation = if (isInteracting) 12.dp else 8.dp,
                            shape = CircleShape,
                            ambientColor = Theme.accent.copy(alpha = if (isInteracting) 0.55f else 0.35f),
                            spotColor = Theme.accent.copy(alpha = if (isInteracting) 0.55f else 0.35f)
                        )
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = if (isInteracting) 0.22f else 0.16f))
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                listOf(
                                    Color.White.copy(alpha = if (isInteracting) 0.55f else 0.40f),
                                    Color.White.copy(alpha = 0.08f)
                                )
                            ),
                            shape = CircleShape
                        )
                )

                // Иконки вкладок
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val isActive = index == activeIndex
                        val iconScale by animateFloatAsState(
                            targetValue = if (isActive) (if (isInteracting) 1.20f else 1.10f) else 1.0f,
                            animationSpec = spring(dampingRatio = 0.70f, stiffness = 400f),
                            label = "iconScale"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onTabSelected(tab)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                tint = if (isActive) Color.White else Color.White.copy(alpha = 0.42f),
                                modifier = Modifier
                                    .scale(iconScale)
                                    .height(22.dp)
                                    .width(22.dp)
                            )
                        }
                    }
                }
            }

            // Всплывающая стеклянная плашка с названием меню (HUD pill)
            AnimatedVisibility(
                visible = isInteracting,
                enter = scaleIn(initialScale = 0.8f) + fadeIn(),
                exit = scaleOut(targetScale = 0.8f) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        val pillWidthPx = with(density) { 90.dp.toPx() }
                        val clampedX = (animatedLensOffsetPx + (with(density) { lensWidthDp.toPx() } / 2f) - (pillWidthPx / 2f))
                            .coerceIn(10f, totalWidthPx - pillWidthPx - 10f)
                        IntOffset(clampedX.roundToInt(), with(density) { (-44).dp.toPx() }.roundToInt())
                    }
            ) {
                Box(
                    modifier = Modifier
                        .shadow(10.dp, shape = CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                        .clip(CircleShape)
                        .background(Color(0xF018181C))
                        .border(1.dp, Theme.accent.copy(alpha = 0.6f), CircleShape)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = activeTab.title,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
