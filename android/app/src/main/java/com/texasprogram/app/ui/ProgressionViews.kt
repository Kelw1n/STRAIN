package com.texasprogram.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.Progression
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.SocialUser
import com.texasprogram.app.service.ProgressionEvents
import com.texasprogram.app.service.RuPlural
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.sin

private val bronze = Color(0xFFD98C4D)
private val silver = Color(0xFFCCCCCC)
private val gold = Color(0xFFFFCC40)

private val Progression.MonthTier.color: Color
    get() = when (this) {
        Progression.MonthTier.BRONZE -> bronze
        Progression.MonthTier.SILVER -> silver
        Progression.MonthTier.GOLD -> gold
    }

private val Progression.MonthTier.genitive: String
    get() = when (this) {
        Progression.MonthTier.BRONZE -> "бронзы"
        Progression.MonthTier.SILVER -> "серебра"
        Progression.MonthTier.GOLD -> "золота"
    }

private object ProgressionText {
    fun experience(value: Int): String = String.format(java.util.Locale.forLanguageTag("ru-RU"), "%,d", value) + " опыта"

    fun toNext(level: Progression.Level): String {
        val left = level.toNext ?: return "Все уровни взяты"
        return "до ${level.level + 1}-го уровня — ${experience(left)}"
    }

    /// «Октябрь: 5 тренировок · до бронзы 3».
    fun month(count: Int, next: Progression.MonthTier?, today: LocalDate = LocalDate.now()): String {
        val done = "${Progression.monthNames[today.monthValue]}: $count ${RuPlural.word(count, "тренировка", "тренировки", "тренировок")}"
        if (next == null) return "$done · золото месяца"
        return "$done · до ${next.genitive} ${next.workouts - count}"
    }
}

// MARK: - Кольцо и шкала

/// Номер уровня в кольце: заполнение — путь до следующего.
@Composable
fun LevelRing(level: Int, fraction: Float, size: Dp = 64.dp, stroke: Dp = 6.dp) {
    val accent = Theme.accent
    val deep = Theme.accentDeep
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val width = stroke.toPx()
            val inset = width / 2
            val arc = Size(this.size.width - width, this.size.height - width)
            drawArc(Color.White.copy(alpha = 0.1f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(width))
            rotate(-90f) {
                drawArc(
                    Brush.sweepGradient(listOf(accent, deep, accent)),
                    0f,
                    360f * fraction.coerceIn(0.002f, 1f),
                    false,
                    Offset(inset, inset),
                    arc,
                    style = Stroke(width, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$level", color = Color.White, fontSize = (size.value * 0.36f).sp, fontWeight = FontWeight.Black, lineHeight = (size.value * 0.38f).sp)
            Text("УР.", color = Color.White.copy(alpha = 0.5f), fontSize = maxOf(9f, size.value * 0.13f).sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun XPBar(fraction: Float, height: Dp = 6.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0.03f, 1f))
                .fillMaxHeight()
                .clip(CircleShape)
                .background(Theme.accentGradient)
        )
    }
}

// MARK: - Уровень у ника

/// Плашка рядом с ником в ленте, у друзей и в рейтинге. Нет уровня — старая
/// версия приложения его не присылала — ничего не рисуем.
@Composable
fun LevelChip(level: Int?, large: Boolean = false, opens: Boolean = false, modifier: Modifier = Modifier) {
    level ?: return
    Row(
        modifier
            .clip(CircleShape)
            .background(Theme.accent.copy(alpha = 0.16f))
            .padding(horizontal = if (large) 10.dp else 5.dp, vertical = if (large) 4.dp else 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            if (large) "Уровень $level · ${Progression.title(level)}" else "ур. $level",
            color = Theme.accent,
            fontSize = if (large) 13.sp else 10.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            softWrap = false
        )
        // Стрелка — плашка нажимается и открывает экран уровня.
        if (opens) {
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Theme.accent, modifier = Modifier.size(if (large) 15.dp else 11.dp))
        }
    }
}

// MARK: - Карточка уровня

/// Уровень в «Прогрессе»: звание, шкала до следующего и награда месяца.
@Composable
fun LevelCard(profile: ProgramProfile, modifier: Modifier = Modifier) {
    val haptics = rememberHapticEngine()
    var showing by remember { mutableStateOf(false) }
    val level = remember(profile.completionLog, profile.setLog, profile.programKind, profile.challengeAwards) { Progression.level(profile) }
    val month = remember(profile.completionLog) { Progression.currentMonth(profile.completionLog) }
    Row(
        modifier
            .fillMaxWidth()
            .pressable {
                haptics.light()
                showing = true
            }
            .clip(RoundedCornerShape(22.dp))
            .background(Theme.surface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        LevelRing(level.level, level.fraction.toFloat())
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(level.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            XPBar(level.fraction.toFloat())
            Text(ProgressionText.toNext(level), color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(
                ProgressionText.month(month.first, month.second),
                color = if (month.second == null) gold else Theme.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
    }
    if (showing) LevelSheet(profile) { showing = false }
}

// MARK: - Экран уровня

private data class TitleRange(val levels: String, val title: String, val from: Int)

private val titleRanges = listOf(
    TitleRange("1–4", "Новичок", 1), TitleRange("5–9", "Любитель", 5), TitleRange("10–14", "Атлет", 10),
    TitleRange("15–19", "Силач", 15), TitleRange("20–24", "Ветеран зала", 20), TitleRange("25–29", "Мастер", 25),
    TitleRange("30–34", "Железный", 30), TitleRange("35–39", "Титан", 35), TitleRange("40–44", "Чемпион", 40),
    TitleRange("45–49", "Икона зала", 45), TitleRange("50", "Легенда", 50)
)

/// Всё про уровень: за что опыт, награды месяцев и лестница званий.
@Composable
fun LevelSheet(profile: ProgramProfile, onDismiss: () -> Unit) {
    val level = remember(profile) { Progression.level(profile) }
    val month = remember(profile) { Progression.currentMonth(profile.completionLog) }
    val awards = remember(profile) { Progression.monthAwards(profile.completionLog) }
    val goal = Progression.weeklyGoal(profile)
    NutritionSheet(title = "Уровень", onDismiss = onDismiss, leading = null, action = "Готово", onAction = { close -> close() }) {
        LevelHeroCard(level.level, level)

        LevelRulesCard(goal)

        NutritionCard(spacing = 12.dp) {
            NutritionCaption("Награды месяцев")
            Text(ProgressionText.month(month.first, month.second), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Progression.MonthTier.entries.forEach { tier ->
                    val reached = month.first >= tier.workouts
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(NutritionColors.tile)
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Filled.MilitaryTech, contentDescription = null, tint = if (reached) tier.color else Color.White.copy(alpha = 0.18f), modifier = Modifier.size(22.dp))
                        Text(tier.title, color = if (reached) Color.White else Color.White.copy(alpha = 0.45f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("${tier.workouts} трен.", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                    }
                }
            }
            Text(
                "Награду месяца можно взять только в этом месяце — потом она остаётся в коллекции.",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp
            )
            awards.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { award ->
                        Row(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(NutritionColors.tile)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.MilitaryTech, contentDescription = null, tint = award.tier.color, modifier = Modifier.size(20.dp))
                            Column {
                                Text(award.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text("${award.tier.title} · ${award.workouts} трен.", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        LevelTitlesCard(level.level)
    }
}

/// Уровень другого человека из его профиля: звание, шкала до следующего,
/// сравнение со своим и за что даётся опыт.
@Composable
fun UserLevelSheet(user: SocialUser, myLevel: Int?, onDismiss: () -> Unit) {
    val level = user.level ?: 1
    val progress = remember(level, user.xp) { Progression.progress(level, user.xp) }
    NutritionSheet(title = "Уровень", onDismiss = onDismiss, leading = null, action = "Готово", onAction = { close -> close() }) {
        LevelHeroCard(level, progress, caption = "@${user.username}")
        if (!user.isMe && myLevel != null) LevelComparisonCard(myLevel, level)
        LevelRulesCard(null)
        LevelTitlesCard(level)
    }
}

/// Кольцо, звание и шкала до следующего уровня. Без опыта — только номер и звание.
@Composable
private fun LevelHeroCard(level: Int, progress: Progression.Level?, caption: String? = null) {
    NutritionCard(spacing = 12.dp) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LevelRing(level, progress?.fraction?.toFloat() ?: 0f, size = 120.dp, stroke = 10.dp)
            caption?.let { Text(it, color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp, fontWeight = FontWeight.Bold) }
            Text(Progression.title(level), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            if (progress != null) {
                Text(ProgressionText.experience(progress.xp), color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Box(Modifier.padding(horizontal = 24.dp)) { XPBar(progress.fraction.toFloat(), 8.dp) }
                Text(ProgressionText.toNext(progress), color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Text("Уровень $level", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/// «Ты — Атлет» и на сколько уровней разница.
@Composable
private fun LevelComparisonCard(mine: Int, theirs: Int) {
    val gap = kotlin.math.abs(theirs - mine)
    val levels = "$gap ${RuPlural.word(gap, "уровень", "уровня", "уровней")}"
    val verdict = when {
        gap == 0 -> "Вы на одном уровне"
        theirs > mine -> "Впереди тебя на $levels"
        else -> "Позади тебя на $levels"
    }
    NutritionCard(spacing = 0.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LevelRing(mine, 1f, size = 48.dp, stroke = 4.dp)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Ты — ${Progression.title(mine)}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    verdict,
                    color = if (theirs > mine) Theme.accent else Color.White.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/// За что даётся опыт. Цель недели у каждого своя — у чужого числа не знаем.
@Composable
private fun LevelRulesCard(goal: Int?) {
    NutritionCard(spacing = 10.dp) {
        NutritionCaption("За что опыт")
        RuleRow("Тренировка", "+${Progression.WORKOUT_XP}")
        RuleRow("Рабочий подход", "+${Progression.SET_XP}")
        RuleRow("Личный рекорд", "+${Progression.RECORD_XP}")
        RuleRow(
            if (goal == null) "Цель недели" else "Цель недели: $goal ${RuPlural.word(goal, "тренировка", "тренировки", "тренировок")}",
            "+${Progression.WEEK_GOAL_XP}"
        )
        RuleRow("Серия недель подряд", "до +50%")
        RuleRow("Место в челлендже", "до +300")
        Text(
            "Каждая следующая неделя с тренировкой даёт +10% ко всему опыту недели. Пропустил неделю — серия начинается заново, опыт остаётся.",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 12.sp
        )
    }
}

/// Лестница званий: пройденные — галочкой, текущее — отметкой.
@Composable
private fun LevelTitlesCard(level: Int) {
    NutritionCard(spacing = 4.dp) {
        NutritionCaption("Звания", Modifier.padding(bottom = 6.dp))
        titleRanges.forEach { item ->
            val current = Progression.title(level) == item.title
            val reached = level >= item.from
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.title,
                    color = when {
                        current -> Theme.accent
                        reached -> Color.White
                        else -> Color.White.copy(alpha = 0.4f)
                    },
                    fontSize = 15.sp,
                    fontWeight = if (current) FontWeight.ExtraBold else FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text("ур. ${item.levels}", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Box(Modifier.width(26.dp), contentAlignment = Alignment.CenterEnd) {
                    if (reached) {
                        Icon(
                            if (current) Icons.Filled.Place else Icons.Filled.Check,
                            contentDescription = null,
                            tint = if (current) Theme.accent else Theme.success,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleRow(title: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(value, color = Theme.accent, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
    }
}

// MARK: - Новый уровень

/// После тренировки: сколько опыта пришло, шкала доезжает, а на новом уровне —
/// вспышка, звание и карточка для сторис.
@Composable
fun LevelUpOverlay(gain: ProgressionEvents.Gain, onClose: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHapticEngine()
    val start = remember(gain) { Progression.level(gain.before) }
    val end = remember(gain) { Progression.level(gain.after) }
    var barLevel by remember(gain) { mutableIntStateOf(start.level) }
    val bar = remember(gain) { Animatable(start.fraction.toFloat()) }
    val shown = remember(gain) { Animatable(0f) }
    var reached by remember(gain) { mutableStateOf<Int?>(null) }

    BackHandler(onBack = onClose)

    LaunchedEffect(gain) {
        delay(250)
        launch { shown.animateTo((gain.after - gain.before).toFloat(), tween(800, easing = FastOutSlowInEasing)) }
        if (end.level == start.level) {
            bar.animateTo(end.fraction.toFloat(), tween(900, easing = FastOutSlowInEasing))
            return@LaunchedEffect
        }
        // Сначала шкала доходит до конца, потом — новый уровень и остаток опыта.
        bar.animateTo(1f, tween(600))
        barLevel = end.level
        bar.snapTo(0f)
        haptics.success()
        reached = end.level
        bar.animateTo(end.fraction.toFloat(), tween(800, easing = FastOutSlowInEasing))
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF171717))
                // Касание по карточке не закрывает её — только по подложке.
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(Modifier.height(140.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (reached != null) ConfettiBurst()
                LevelRing(barLevel, bar.value, size = 120.dp, stroke = 10.dp)
            }
            Text("+${shown.value.toInt()} опыта", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
            val reachedLevel = reached
            if (reachedLevel != null) {
                val appear = remember { MutableTransitionState(false).apply { targetState = true } }
                AnimatedVisibility(visibleState = appear, enter = scaleIn(initialScale = 0.8f) + fadeIn()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("НОВЫЙ УРОВЕНЬ", color = Theme.accent, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp)
                        Text("$reachedLevel · ${Progression.title(reachedLevel)}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            } else {
                Text(ProgressionText.toNext(end), color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (reachedLevel != null) {
                    Row(
                        Modifier
                            .weight(1f)
                            .height(50.dp)
                            .pressable {
                                haptics.medium()
                                WorkoutShareCard.share(context, LevelShareCard.render(end), "Поделиться уровнем", "strain-level.png")
                            }
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF2E2E2E)),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Поделиться", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(50.dp)
                        .pressable(onClick = onClose)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Theme.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Отлично", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/// Вспышка конфетти из-за кольца: разлетается и гаснет.
@Composable
private fun ConfettiBurst() {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(1200, easing = LinearEasing)) }
    val colors = listOf(Theme.accent, Theme.success, Theme.warning, Color.White, Color(0xFF5C38F2))
    Canvas(Modifier.fillMaxSize()) {
        val t = progress.value
        val eased = 1f - (1f - t) * (1f - t)
        val center = Offset(size.width / 2, size.height / 2)
        repeat(20) { index ->
            val angle = index / 20f * 2f * Math.PI.toFloat() + if (index % 2 == 0) 0.12f else -0.08f
            val distance = (if (index % 3 == 0) 130 else 100).dp.toPx() * eased
            val position = center + Offset(cos(angle) * distance, sin(angle) * distance)
            val w = 7.dp.toPx()
            val h = (if (index % 2 == 0) 12 else 8).dp.toPx()
            rotate(index * 37f + 220f * eased, position) {
                drawRoundRect(
                    colors[index % colors.size].copy(alpha = 1f - t),
                    topLeft = position - Offset(w / 2, h / 2),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
            }
        }
    }
}

// MARK: - Карточка для сторис

/// Новый уровень картинкой для сторис — в стиле итогов месяца.
object LevelShareCard {
    private const val WIDTH = 1080
    private const val HEIGHT = 1920
    private const val UNIT = 3f
    private fun u(value: Float) = value * UNIT

    private val black: Typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    private val bold: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    private val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private fun paint(size: Float, color: Int, face: Typeface) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = u(size)
        this.color = color
        typeface = face
    }

    private fun white(alpha: Float) = Color.White.copy(alpha = alpha).toArgb()

    fun render(level: Progression.Level): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        val (from, to) = palette(1)
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), from.toArgb(), to.toArgb(), Shader.TileMode.CLAMP)
        })
        val pad = u(26f)
        val inner = WIDTH - pad * 2
        val top = pad + u(18f)
        canvas.drawText("STRAIN", pad, top, paint(18f, white(1f), black).apply { letterSpacing = 0.12f })
        val label = "НОВЫЙ УРОВЕНЬ"
        val labelPaint = paint(11f, white(0.8f), bold).apply { letterSpacing = 0.12f }
        canvas.drawText(label, WIDTH - pad - labelPaint.measureText(label), top, labelPaint)

        // Кольцо с номером уровня по центру.
        val radius = u(85f)
        val cx = WIDTH / 2f
        val cy = HEIGHT * 0.42f
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = u(14f)
            strokeCap = Paint.Cap.ROUND
        }
        ring.color = white(0.15f)
        canvas.drawCircle(cx, cy, radius, ring)
        ring.color = Theme.accent.toArgb()
        canvas.drawArc(RectF(cx - radius, cy - radius, cx + radius, cy + radius), -90f, 360f, false, ring)
        val number = paint(62f, white(1f), black).apply { textAlign = Paint.Align.CENTER }
        canvas.drawText("${level.level}", cx, cy + u(14f), number)
        val unit = paint(16f, white(0.6f), bold).apply { textAlign = Paint.Align.CENTER }
        canvas.drawText("УР.", cx, cy + u(40f), unit)

        var y = HEIGHT * 0.66f
        canvas.drawText("Уровень ${level.level}", pad, y, paint(20f, white(0.8f), bold))
        y += u(52f)
        val titlePaint = paint(44f, white(1f), black)
        canvas.drawText(fit(level.title, titlePaint, inner), pad, y, titlePaint)
        y += u(34f)
        canvas.drawText(ProgressionText.experience(level.xp), pad, y, paint(16f, white(0.75f), medium))
        return bitmap
    }

    private fun fit(text: String, paint: TextPaint, width: Float): String =
        TextUtils.ellipsize(text, paint, width, TextUtils.TruncateAt.END).toString()
}
