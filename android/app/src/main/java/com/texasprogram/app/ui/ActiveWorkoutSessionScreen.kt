package com.texasprogram.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.formatWeight
import com.texasprogram.app.service.ActiveExerciseState
import com.texasprogram.app.service.ActiveSetState
import com.texasprogram.app.service.RecordNotice
import com.texasprogram.app.service.RestTimer
import com.texasprogram.app.service.RuPlural
import com.texasprogram.app.service.WorkoutSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

// Цвета экрана — как в iOS-версии: чистый чёрный фон и карточки чуть светлее.
private val ScreenBlack = Color(0xFF000000)
private val CardColor = Color(0xFF161616)
private val CardLifted = Color(0xFF1F1F1F)
private val FieldColor = Color(0xFF242424)
private val FieldFocused = Color(0xFF303030)

/// Высота свёрнутой карточки при перестановке и зазор между ними.
private val ROW_HEIGHT = 76.dp
private val ROW_SPACING = 10.dp

// MARK: - Секундомер

/// Секундомер тренировки. Тикает сам: раз в секунду перерисовывается только
/// этот текст, а не весь экран, как было, когда время читалось в корне.
@Composable
fun SessionClock(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 26.sp,
    color: Color = Color.White,
    rolling: Boolean = false
) {
    val start = WorkoutSessionManager.startMillis
    val seconds by produceState(WorkoutSessionManager.elapsedSeconds(), start) {
        while (true) {
            val now = System.currentTimeMillis()
            value = WorkoutSessionManager.elapsedSeconds(now)
            // До следующей целой секунды от старта — цифры не «плывут» относительно часов.
            val phase = Math.floorMod(now - start, 1000L)
            delay((1000L - phase).coerceAtLeast(16L))
        }
    }
    val style = TextStyle(
        color = color,
        fontSize = fontSize,
        fontWeight = FontWeight.Black,
        fontFeatureSettings = "tnum"
    )
    if (rolling) {
        RollingText(formatClock(seconds), style, modifier)
    } else {
        Text(formatClock(seconds), style = style, modifier = modifier, maxLines = 1)
    }
}

fun formatClock(seconds: Int): String {
    val hours = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, mins, secs)
    else String.format(Locale.US, "%02d:%02d", mins, secs)
}

// MARK: - Свёрнутая панель активной тренировки

@Composable
fun MinimizedWorkoutBar(modifier: Modifier = Modifier) {
    val workout = WorkoutSessionManager.activeWorkout ?: return
    val haptics = rememberHapticEngine()
    val pulse = rememberInfiniteTransition(label = "pulse")
    val phase by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "pulsePhase"
    )
    val total = WorkoutSessionManager.totalSetsCount
    val shape = RoundedCornerShape(26.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .graphicsLayer {
                shadowElevation = 12.dp.toPx()
                this.shape = shape
                clip = true
            }
            .background(Color(0xF21A1A1E))
            .border(
                1.dp,
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.06f))),
                shape
            )
            .pressable {
                haptics.medium()
                WorkoutSessionManager.expand()
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Пульсирующая точка «идёт». Фаза читается в слое — без перекомпоновки.
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        val s = 0.8f + 0.35f * phase
                        scaleX = s
                        scaleY = s
                        alpha = 0.8f - 0.6f * phase
                    }
                    .background(Theme.success.copy(alpha = 0.35f), CircleShape)
            )
            Box(Modifier.size(8.dp).background(Theme.success, CircleShape))
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = workout.fullTitle,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${WorkoutSessionManager.totalCompletedSets} из $total " +
                    RuPlural.word(total, "подхода", "подходов", "подходов"),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.width(8.dp))

        Row(
            Modifier
                .background(Theme.accent.copy(alpha = 0.15f), CircleShape)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(Icons.Filled.Timer, null, tint = Theme.accent, modifier = Modifier.size(12.dp))
            SessionClock(fontSize = 13.sp, color = Theme.accent)
        }

        Spacer(Modifier.width(6.dp))

        Icon(
            Icons.Filled.KeyboardArrowUp,
            contentDescription = "Развернуть",
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp)
        )
    }
}

// MARK: - Действия экрана

/// Все действия экрана в одном неизменном объекте. Карточкам передаётся он,
/// а не профиль: профиль меняется на каждый отмеченный подход, и с ним
/// перерисовывались бы все карточки и строки. Профиль берётся свежий
/// в момент нажатия — два быстрых нажатия не затирают друг друга.
@Stable
private class SessionActions(
    private val profile: () -> ProgramProfile?,
    private val update: (ProgramProfile) -> Unit,
    private val timer: RestTimer,
    private val haptics: Haptics
) {
    private val manager = WorkoutSessionManager

    private inline fun withProfile(block: (ProgramProfile) -> Unit) {
        profile()?.let(block)
    }

    fun toggle(ex: Int, set: Int) = withProfile {
        val wasDone = manager.exercises.getOrNull(ex)?.sets?.getOrNull(set)?.isDone ?: return@withProfile
        if (wasDone) haptics.light() else haptics.success()
        manager.toggleSetDone(ex, set, it, timer, update)
    }

    fun weight(ex: Int, set: Int, value: Double) = withProfile { manager.setWeight(value, ex, set, it, update) }
    fun reps(ex: Int, set: Int, value: Int) = withProfile { manager.setReps(value, ex, set, it, update) }

    fun previous(ex: Int, set: Int) = withProfile {
        haptics.light()
        manager.applyPrevious(ex, set, it, update)
    }

    fun delete(ex: Int, set: Int) = withProfile {
        haptics.medium()
        manager.deleteSet(ex, set, it, update)
    }

    fun addSet(ex: Int) {
        haptics.light()
        manager.addSet(ex)
    }

    fun move(from: Int, to: Int) {
        haptics.light()
        manager.moveExercise(from, to)
    }

    fun chooseRest(ex: Int) {
        manager.customRestTargetExerciseIndex = ex
    }

    fun chooseArt(name: String) {
        manager.artTarget = name
    }

    fun step(name: String): Double = profile()?.weightStep(name) ?: com.texasprogram.app.model.AccessoryHint.STEP

    fun changeStep(ex: Int, step: Double) = withProfile {
        haptics.light()
        manager.changeWeightStep(step, ex, it, update)
    }
}

// MARK: - Перестановка упражнений

/// Перетаскивание упражнения пальцем — как в iOS-версии.
///
/// Удержание шапки сворачивает все карточки до строк одной высоты: место,
/// куда встанет упражнение, считается делением. Пока палец ведёт, список
/// не трогаем — соседи расступаются смещением в слое (без перекомпоновки).
/// На отпускании строка доезжает до места, и только тогда порядок меняется —
/// в том же кадре смещения сбрасываются, поэтому картинка не дёргается.
@Stable
private class ExerciseReorder(
    private val scope: CoroutineScope,
    private val listState: LazyListState,
    private val haptics: Haptics,
    private val onBegin: () -> Unit
) {
    var stepPx = 0f

    var draggingId by mutableStateOf<Long?>(null)
        private set
    private var startIndex by mutableIntStateOf(0)
    private var target by mutableIntStateOf(0)
    private var translation by mutableFloatStateOf(0f)
    private val shifts = mutableStateMapOf<Long, Float>()
    private val shiftJobs = HashMap<Long, Job>()
    private var ending = false

    val isActive: Boolean get() = draggingId != null

    /// Карточка под точкой касания. Смещения в `visibleItemsInfo` отсчитываются
    /// от начала содержимого, а касание — от края списка: разница в отступ сверху.
    fun itemAt(y: Float): LazyListItemInfo? {
        val info = listState.layoutInfo
        val localY = y + info.viewportStartOffset
        return info.visibleItemsInfo.firstOrNull { localY >= it.offset && localY < it.offset + it.size }
    }

    fun localY(y: Float): Float = y + listState.layoutInfo.viewportStartOffset

    fun begin(position: Offset): Boolean {
        if (draggingId != null || ending) return false
        val item = itemAt(position.y) ?: return false
        val id = item.key as? Long ?: return false
        val index = WorkoutSessionManager.exercises.indexOfFirst { it.id == id }
        if (index < 0) return false
        onBegin()
        haptics.medium()
        draggingId = id
        startIndex = index
        target = index
        translation = 0f
        // Карточки сворачиваются в этом же кадре — сразу просим список поставить
        // схваченную туда, где была её шапка, чтобы она осталась под пальцем.
        listState.requestScrollToItem(index, -item.offset)
        return true
    }

    fun drag(dy: Float) {
        if (draggingId == null || ending) return
        translation += dy
        val last = WorkoutSessionManager.exercises.lastIndex.coerceAtLeast(0)
        val next = (startIndex + (translation / stepPx).roundToInt()).coerceIn(0, last)
        if (next != target) {
            target = next
            haptics.selection()
            retarget()
        }
    }

    private fun desiredShift(index: Int): Float = when {
        startIndex < target && index > startIndex && index <= target -> -stepPx
        target < startIndex && index >= target && index < startIndex -> stepPx
        else -> 0f
    }

    private fun retarget() {
        WorkoutSessionManager.exercises.forEachIndexed { index, exercise ->
            val id = exercise.id
            if (id == draggingId) return@forEachIndexed
            val goal = desiredShift(index)
            val current = shifts[id] ?: 0f
            if (current == goal && shiftJobs[id]?.isActive != true) return@forEachIndexed
            shiftJobs[id]?.cancel()
            shiftJobs[id] = scope.launch {
                animate(current, goal, animationSpec = Motion.reorder()) { value, _ -> shifts[id] = value }
            }
        }
    }

    /// Смещение карточки: схваченная едет за пальцем, соседи уступают место.
    fun offset(id: Long): Float = if (id == draggingId) translation else shifts[id] ?: 0f

    fun end() {
        val id = draggingId ?: return
        if (ending) return
        ending = true
        val from = startIndex
        val to = target
        val startOffset = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }?.offset ?: 0
        scope.launch {
            try {
                animate(translation, (to - from) * stepPx, animationSpec = Motion.reorder()) { value, _ ->
                    translation = value
                }
                shiftJobs.values.toList().joinAll()
            } finally {
                // Всё одним куском, без приостановок: порядок, смещения и размер
                // карточек меняются в одном кадре.
                shiftJobs.clear()
                if (from != to) WorkoutSessionManager.moveExercise(from, to)
                shifts.clear()
                translation = 0f
                startIndex = to
                target = to
                draggingId = null
                // Развернувшиеся карточки выше могут унести упражнение с экрана —
                // ставим его шапку туда, где отпустили, но не ниже трети экрана.
                val landing = startOffset + ((to - from) * stepPx).roundToInt()
                val limit = (listState.layoutInfo.viewportSize.height * 0.35f).roundToInt()
                listState.requestScrollToItem(to, -landing.coerceIn(0, limit.coerceAtLeast(0)))
                haptics.light()
                ending = false
            }
        }
    }
}

/// Удержание и ведение по списку. Ловим на ранней фазе (Initial), до прокрутки:
/// пока палец держит упражнение, лента не уезжает из-под него. Короткое
/// касание и обычная прокрутка проходят мимо.
private suspend fun PointerInputScope.detectReorder(
    reorder: ExerciseReorder,
    canStart: (Offset) -> Boolean
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!canStart(down.position)) return@awaitEachGesture
        val slop = viewConfiguration.touchSlop
        val released = withTimeoutOrNull(320L) { holdsStill(down, slop) }
        // Вернулось значение — палец отпустили или повели раньше времени.
        if (released != null) return@awaitEachGesture
        if (!reorder.begin(down.position)) return@awaitEachGesture
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    change.consume()
                    break
                }
                val dy = change.position.y - change.previousPosition.y
                change.consume()
                if (dy != 0f) reorder.drag(dy)
            }
        } finally {
            reorder.end()
        }
    }
}

/// Ждёт, пока палец лежит на месте. Возвращает `false`, если его подняли
/// или сдвинули; пока держат — не возвращается вовсе (её прерывает тайм-аут).
private suspend fun AwaitPointerEventScope.holdsStill(down: PointerInputChange, slop: Float): Boolean {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == down.id } ?: return false
        if (!change.pressed || change.isConsumed) return false
        if ((change.position - down.position).getDistance() > slop) return false
    }
}

// MARK: - Экран активной тренировки

@Composable
fun ActiveWorkoutSessionScreen(
    profile: () -> ProgramProfile?,
    restTimer: RestTimer,
    onUpdateProfile: (ProgramProfile) -> Unit
) {
    val workout = WorkoutSessionManager.activeWorkout ?: return
    val haptics = rememberHapticEngine()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current

    val currentProfile by rememberUpdatedState(profile)
    val currentUpdate by rememberUpdatedState(onUpdateProfile)
    val actions = remember(restTimer, haptics) {
        SessionActions({ currentProfile() }, { currentUpdate(it) }, restTimer, haptics)
    }
    val reorder = remember(listState, haptics) {
        ExerciseReorder(scope, listState, haptics) { focusManager.clearFocus() }
    }
    reorder.stepPx = with(density) { (ROW_HEIGHT + ROW_SPACING).toPx() }
    val headerZonePx = with(density) { 84.dp.toPx() }
    val trailingZonePx = with(density) { 124.dp.toPx() }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val exercises = WorkoutSessionManager.exercises
    val compact = reorder.isActive

    Box(
        Modifier
            .fillMaxSize()
            .background(ScreenBlack)
    ) {
        Column(Modifier.fillMaxSize().imePadding()) {
            SessionHeader(
                topInset = topInset,
                onMinimize = {
                    haptics.light()
                    focusManager.clearFocus()
                    WorkoutSessionManager.minimize()
                },
                onFinish = {
                    haptics.medium()
                    focusManager.clearFocus()
                    if (WorkoutSessionManager.totalCompletedSets == 0) {
                        WorkoutSessionManager.showingZeroSetsAlert = true
                    } else {
                        WorkoutSessionManager.showingSummary = true
                    }
                }
            )

            // Совместная тренировка: что сейчас делает напарник. Над списком,
            // чтобы не сбивать перетаскивание упражнений.
            PartnerStrip(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            LazyColumn(
                state = listState,
                userScrollEnabled = !compact,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = bottomInset + if (restTimer.isRunning) 220.dp else 60.dp
                ),
                verticalArrangement = Arrangement.spacedBy(if (compact) ROW_SPACING else 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(reorder) {
                        detectReorder(reorder) { position ->
                            // Только шапка карточки и не кнопки справа.
                            val item = reorder.itemAt(position.y) ?: return@detectReorder false
                            val withinItem = reorder.localY(position.y) - item.offset
                            withinItem < headerZonePx && position.x < size.width - trailingZonePx
                        }
                    }
            ) {
                itemsIndexed(exercises, key = { _, exercise -> exercise.id }) { index, exercise ->
                    val lifted = reorder.draggingId == exercise.id
                    val lift by animateFloatAsState(if (lifted) 1f else 0f, Motion.sheet(), label = "lift")
                    ExerciseSessionCard(
                        exercise = exercise,
                        index = index,
                        count = exercises.size,
                        compact = compact,
                        lifted = lifted,
                        actions = actions,
                        modifier = Modifier
                            .zIndex(if (lifted) 1f else 0f)
                            .graphicsLayer {
                                // Смещение читается здесь, в слое: перетаскивание
                                // не перекомпоновывает карточки, только двигает их.
                                translationY = reorder.offset(exercise.id)
                                val scale = 1f + 0.03f * lift
                                scaleX = scale
                                scaleY = scale
                                shadowElevation = 18.dp.toPx() * lift
                                shape = RoundedCornerShape(24.dp)
                            }
                    )
                }
            }
        }

        // Карточка отдыха в стиле REBRN
        RebrnRestTimerCard(
            timer = restTimer,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomInset + 12.dp)
        )

        RecordBanner(
            record = WorkoutSessionManager.lastRecord,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = topInset + 60.dp)
        )

        AnimatedVisibility(
            visible = WorkoutSessionManager.showingZeroSetsAlert,
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.95f),
            exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.95f)
        ) {
            ZeroSetsAlertOverlay(
                onConfirm = {
                    val current = currentProfile() ?: return@ZeroSetsAlertOverlay
                    haptics.medium()
                    WorkoutSessionManager.discardWorkout(current, currentUpdate)
                },
                onDismiss = { WorkoutSessionManager.showingZeroSetsAlert = false }
            )
        }

        if (WorkoutSessionManager.showingSummary) {
            val current = currentProfile()
            if (current != null) {
                WorkoutCompletionSummaryScreen(
                    profile = current,
                    workout = workout,
                    onUpdateProfile = onUpdateProfile,
                    onDismiss = { WorkoutSessionManager.showingSummary = false }
                )
            }
        }

        if (WorkoutSessionManager.showingNoteSheet) {
            WorkoutNoteDialog(
                initialNote = WorkoutSessionManager.noteText,
                onSave = { text ->
                    WorkoutSessionManager.updateNote(text)
                    // Пустой текст — тоже ответ: заметку стёрли, её надо удалить.
                    currentProfile()?.let { currentUpdate(it.setNote(text, workout.week, workout.day.number)) }
                    WorkoutSessionManager.showingNoteSheet = false
                },
                onDismiss = { WorkoutSessionManager.showingNoteSheet = false }
            )
        }

        if (WorkoutSessionManager.customRestTargetExerciseIndex != null) {
            RestDurationDialog(
                onSelect = { seconds ->
                    haptics.light()
                    WorkoutSessionManager.setRestDuration(seconds)
                },
                onDismiss = { WorkoutSessionManager.customRestTargetExerciseIndex = null }
            )
        }

        WorkoutSessionManager.artTarget?.let { name ->
            ExerciseArtDialog(name = name, onDismiss = { WorkoutSessionManager.artTarget = null })
        }
    }
}

// MARK: - Шапка

@Composable
private fun SessionHeader(topInset: Dp, onMinimize: () -> Unit, onFinish: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topInset + 8.dp, start = 16.dp, end = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Как у REBRN: свернуть и крупный секундомер слева, действия справа.
        CircleIconButton(Icons.Filled.KeyboardArrowDown, "Свернуть тренировку", onMinimize)
        Spacer(Modifier.width(12.dp))
        SessionClock(fontSize = 26.sp, rolling = true)
        Spacer(Modifier.weight(1f))

        Box {
            CircleIconButton(Icons.Filled.MoreHoriz, "Меню") { menuExpanded = true }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.background(Color(0xFF1C1C1F))
            ) {
                DropdownMenuItem(
                    text = { Text("Заметка к тренировке", color = Color.White) },
                    leadingIcon = { Icon(Icons.Filled.Notes, null, tint = Color.White) },
                    onClick = {
                        menuExpanded = false
                        WorkoutSessionManager.showingNoteSheet = true
                    }
                )
                DropdownMenuItem(
                    text = { Text("Отменить тренировку", color = Color(0xFFFF5252)) },
                    leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Color(0xFFFF5252)) },
                    onClick = {
                        menuExpanded = false
                        WorkoutSessionManager.showingZeroSetsAlert = true
                    }
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Box(
            Modifier
                .height(36.dp)
                .background(Color.White, CircleShape)
                .clip(CircleShape)
                .pressable(onClick = onFinish)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Завершить", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CircleIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .background(Color.White.copy(alpha = 0.12f), CircleShape)
            .clip(CircleShape)
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

// MARK: - Карточка упражнения

/// Значения на входе, без профиля: при вводе цифры перерисуется только
/// карточка этого упражнения, а в ней — только изменившаяся строка.
@Composable
private fun ExerciseSessionCard(
    exercise: ActiveExerciseState,
    index: Int,
    count: Int,
    compact: Boolean,
    lifted: Boolean,
    actions: SessionActions,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(24.dp)
    val background by animateColorAsState(if (lifted) CardLifted else CardColor, Motion.fade(200), label = "card")
    val borderAlpha by animateFloatAsState(if (lifted) 0.7f else 0f, Motion.fade(200), label = "border")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (compact) Modifier.height(ROW_HEIGHT) else Modifier.animateContentSize(Motion.snappy()))
            .background(background, shape)
            .border(1.5.dp, Theme.accent.copy(alpha = borderAlpha), shape)
            .padding(if (compact) 12.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ExerciseHeader(exercise, index, count, compact, lifted, actions)
        if (!compact) {
            SetsTable(exercise, index, actions)
        }
    }
}

@Composable
private fun ExerciseHeader(
    exercise: ActiveExerciseState,
    index: Int,
    count: Int,
    compact: Boolean,
    lifted: Boolean,
    actions: SessionActions
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var stepMenuExpanded by remember { mutableStateOf(false) }
    val name = exercise.prescription.name

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Filled.DragHandle,
            contentDescription = "Удерживайте, чтобы переставить",
            tint = Color.White.copy(alpha = if (lifted) 0.9f else 0.3f),
            modifier = Modifier.size(18.dp)
        )
        ExerciseThumb(name = name, size = if (compact) 48.dp else 52.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = name,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!compact) {
                // Отдых — под названием, как в REBRN: справа он отнимал место,
                // и длинные названия рвались посреди слова.
                Row(
                    Modifier
                        .background(Color.White.copy(alpha = 0.10f), CircleShape)
                        .clip(CircleShape)
                        .pressable { actions.chooseRest(index) }
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(Icons.Filled.Timer, "Время отдыха", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(13.dp))
                    Text(
                        text = String.format(Locale.US, "%02d:%02d", exercise.restSeconds / 60, exercise.restSeconds % 60),
                        color = Color.White.copy(alpha = 0.85f),
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
                    )
                }
            }
            if (compact) {
                Text(
                    text = "${exercise.doneCount} из ${RuPlural.sets(exercise.sets.size)}",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (!compact) {
            Box {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .clickable { menuExpanded = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.MoreHoriz, "Действия с упражнением", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(Color(0xFF1C1C1F))
                ) {
                    MenuItem("Добавить подход", Icons.Filled.Add) {
                        menuExpanded = false
                        actions.addSet(index)
                    }
                    MenuItem("Картинка упражнения", Icons.Filled.Image) {
                        menuExpanded = false
                        actions.chooseArt(name)
                    }
                    // Шаг прибавки этого движения: у кого есть блины 1,25 — 2,5 кг,
                    // у кого нет — 5; на блоке бывает и 1 кг.
                    MenuItem("Шаг прибавки: ${formatWeight(actions.step(name))} кг", Icons.Filled.Scale) {
                        menuExpanded = false
                        stepMenuExpanded = true
                    }
                    if (index > 0) {
                        MenuItem("Переместить выше", Icons.Filled.ArrowUpward) {
                            menuExpanded = false
                            actions.move(index, index - 1)
                        }
                    }
                    if (index < count - 1) {
                        MenuItem("Переместить ниже", Icons.Filled.ArrowDownward) {
                            menuExpanded = false
                            actions.move(index, index + 1)
                        }
                    }
                }
                if (stepMenuExpanded) {
                    WeightStepMenu(
                        expanded = true,
                        current = actions.step(name),
                        onDismiss = { stepMenuExpanded = false }
                    ) { step ->
                        stepMenuExpanded = false
                        actions.changeStep(index, step)
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(title: String, icon: ImageVector, color: Color = Color.White, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(title, color = color) },
        leadingIcon = { Icon(icon, null, tint = color) },
        onClick = onClick
    )
}

// MARK: - Подходы

@Composable
private fun SetsTable(exercise: ActiveExerciseState, exIndex: Int, actions: SessionActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ColumnTitle("#", Modifier.width(38.dp))
            ColumnTitle("БЫЛО", Modifier.width(62.dp))
            // Поля ввода — в килограммах, как и вся арифметика программы.
            ColumnTitle("КГ", Modifier.weight(1f))
            ColumnTitle("ПОВТ", Modifier.weight(1f))
            Spacer(Modifier.width(40.dp))
        }

        val sets = exercise.sets
        sets.forEachIndexed { position, set ->
            androidx.compose.runtime.key(set.id) {
                SetRow(
                    set = set,
                    position = position,
                    exIndex = exIndex,
                    isCurrent = !set.isDone && (0 until position).all { sets[it].isDone },
                    canDelete = sets.size > 1,
                    actions = actions
                )
            }
        }

        Row(
            Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .background(FieldColor, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .pressable { actions.addSet(exIndex) }
                .padding(vertical = 11.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Add, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text("Добавить подход", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ColumnTitle(text: String, modifier: Modifier) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.4f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

@Composable
private fun SetRow(
    set: ActiveSetState,
    position: Int,
    exIndex: Int,
    isCurrent: Boolean,
    canDelete: Boolean,
    actions: SessionActions
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SetNumber(set, position, exIndex, isCurrent, canDelete, actions)

        // «БЫЛО»: нажал — подставилось то, что было в прошлый раз.
        Box(
            Modifier
                .width(62.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .pressable(enabled = set.previous != null) { actions.previous(exIndex, position) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = set.previousText,
                color = Color.White.copy(alpha = if (set.previous == null) 0.25f else 0.45f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }

        SetNumberField(
            value = set.weight,
            decimal = true,
            isDone = set.isDone,
            modifier = Modifier.weight(1f)
        ) { value -> actions.weight(exIndex, position, value) }

        SetNumberField(
            value = set.reps.toDouble(),
            decimal = false,
            isDone = set.isDone,
            modifier = Modifier.weight(1f)
        ) { value -> actions.reps(exIndex, position, value.toInt()) }

        CheckButton(done = set.isDone) { actions.toggle(exIndex, position) }
    }
}

/// Номер подхода: оранжевая черта у текущего, кубок у рекорда. Нажатие — меню подхода.
@Composable
private fun SetNumber(
    set: ActiveSetState,
    position: Int,
    exIndex: Int,
    isCurrent: Boolean,
    canDelete: Boolean,
    actions: SessionActions
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val currentAlpha by animateFloatAsState(if (isCurrent) 1f else 0f, Motion.snappy(), label = "current")
    val recordScale by animateFloatAsState(if (set.isRecord) 1f else 0f, Motion.bouncy(), label = "record")
    val hasMenu = set.previous != null || canDelete

    Box(Modifier.width(38.dp).height(44.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-8).dp)
                .width(3.dp)
                .height(22.dp)
                .graphicsLayer { alpha = currentAlpha }
                .background(Theme.accent, CircleShape)
        )
        Box(
            Modifier
                .size(34.dp)
                .background(if (set.isRecord) Theme.accent else Color(0xFF262626), CircleShape)
                .clip(CircleShape)
                .pressable(enabled = hasMenu) { menuExpanded = true },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${position + 1}",
                color = if (set.isRecord) Color.Black else Color.White,
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
            )
        }
        if (recordScale > 0.01f) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .graphicsLayer {
                        scaleX = recordScale
                        scaleY = recordScale
                        alpha = recordScale.coerceIn(0f, 1f)
                    }
                    .size(16.dp)
                    .background(Color.Black, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.EmojiEvents, null, tint = Color.White, modifier = Modifier.size(10.dp))
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.background(Color(0xFF1C1C1F))
        ) {
            if (set.previous != null) {
                MenuItem("Как в прошлый раз", Icons.Filled.History) {
                    menuExpanded = false
                    actions.previous(exIndex, position)
                }
            }
            if (canDelete) {
                MenuItem("Удалить подход", Icons.Filled.Delete, Color(0xFFFF5252)) {
                    menuExpanded = false
                    actions.delete(exIndex, position)
                }
            }
        }
    }
}

@Composable
private fun CheckButton(done: Boolean, onClick: () -> Unit) {
    val progress by animateFloatAsState(if (done) 1f else 0f, Motion.bouncy(), label = "check")
    Box(
        Modifier
            .width(40.dp)
            .height(44.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = if (done) "Снять отметку" else "Отметить подход",
                onClick = onClick
            )
            .semantics { contentDescription = if (done) "Подход сделан" else "Отметить подход" },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(30.dp)
                .graphicsLayer { alpha = 1f - progress.coerceIn(0f, 1f) }
                .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
        )
        Box(
            Modifier
                .size(30.dp)
                .graphicsLayer {
                    val s = 0.4f + 0.6f * progress
                    scaleX = s
                    scaleY = s
                    alpha = progress.coerceIn(0f, 1f)
                }
                .background(Theme.accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Check, contentDescription = if (done) "Выполнен" else "Отметить", tint = Color.White, modifier = Modifier.size(17.dp))
        }
    }
}

// MARK: - Поле веса / повторов

/// Ячейка подхода с вводом с клавиатуры.
///
/// Текст живёт в своём состоянии, а не вычисляется из модели: иначе стёртое
/// поле сразу заполнялось бы старым числом и вписать новое было нельзя.
/// В модель уходит каждое число, которое удалось разобрать.
@Composable
private fun SetNumberField(
    value: Double,
    decimal: Boolean,
    isDone: Boolean,
    modifier: Modifier = Modifier,
    onCommit: (Double) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var text by remember { mutableStateOf(TextFieldValue(formatNumber(value, decimal))) }
    var focused by remember { mutableStateOf(false) }
    val currentValue by rememberUpdatedState(value)

    LaunchedEffect(value) {
        if (!focused) {
            val formatted = formatNumber(value, decimal)
            if (formatted != text.text) text = TextFieldValue(formatted, TextRange(formatted.length))
        }
    }

    val shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = text,
        onValueChange = { next ->
            val raw = next.text
            val allowed = raw.all { it.isDigit() || (decimal && (it == '.' || it == ',')) }
            val separators = raw.count { it == '.' || it == ',' }
            if (!allowed || separators > 1 || raw.length > 6) return@BasicTextField
            text = next
            parseNumber(raw, decimal)?.let { if (it != currentValue) onCommit(it) }
        },
        singleLine = true,
        textStyle = TextStyle(
            // План — приглушённым, факт — белым, как у REBRN.
            color = if (isDone || focused) Color.White else Color.White.copy(alpha = 0.6f),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            fontFeatureSettings = "tnum"
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        cursorBrush = SolidColor(Theme.accent),
        modifier = modifier
            .height(44.dp)
            .background(if (focused) FieldFocused else FieldColor, shape)
            .border(1.5.dp, if (focused) Theme.accent else Color.Transparent, shape)
            .onFocusChanged { state ->
                if (focused && !state.isFocused) {
                    // Ушли из поля пустым — показываем то, что в модели.
                    val formatted = formatNumber(currentValue, decimal)
                    text = TextFieldValue(formatted, TextRange(formatted.length))
                }
                focused = state.isFocused
            },
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { inner() }
        }
    )
}

private fun formatNumber(value: Double, decimal: Boolean): String =
    if (decimal) formatWeight(value) else value.toInt().toString()

private fun parseNumber(text: String, decimal: Boolean): Double? {
    val cleaned = text.replace(',', '.').trim()
    if (cleaned.isEmpty() || cleaned == ".") return null
    val number = cleaned.toDoubleOrNull() ?: return null
    if (!number.isFinite() || number < 0) return null
    return if (decimal) minOf(number, 1000.0) else minOf(kotlin.math.floor(number), 999.0)
}

// MARK: - Плашка рекорда

@Composable
private fun RecordBanner(record: RecordNotice?, modifier: Modifier = Modifier) {
    // Последний показанный рекорд держим, пока плашка уезжает.
    val shown = remember { arrayOfNulls<RecordNotice>(1) }
    if (record != null) shown[0] = record

    if (record != null) {
        LaunchedEffect(record.id) {
            delay(2600)
            if (WorkoutSessionManager.lastRecord?.id == record.id) WorkoutSessionManager.lastRecord = null
        }
    }

    AnimatedVisibility(
        visible = record != null,
        enter = slideInVertically(Motion.sheet()) { -it } + fadeIn() + scaleIn(initialScale = 0.9f),
        exit = slideOutVertically(Motion.sheet()) { -it } + fadeOut() + scaleOut(targetScale = 0.9f),
        modifier = modifier
    ) {
        val notice = shown[0] ?: return@AnimatedVisibility
        val shape = RoundedCornerShape(50)
        Row(
            Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    shadowElevation = 16.dp.toPx()
                    this.shape = shape
                    clip = true
                    spotShadowColor = Theme.accent
                    ambientShadowColor = Theme.accent
                }
                .background(Color(0xFF1F1F1F))
                .border(1.dp, Theme.accent.copy(alpha = 0.6f), shape)
                .clickable { WorkoutSessionManager.lastRecord = null }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(30.dp).background(Theme.accent, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.EmojiEvents, null, tint = Color.Black, modifier = Modifier.size(17.dp))
            }
            Column {
                Text("Новый рекорд", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
                Text(
                    "${notice.exercise} · ${formatWeight(notice.weight)} кг",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// MARK: - Окно «Ни одного подхода не отмечено» / отмена тренировки

@Composable
private fun ZeroSetsAlertOverlay(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .background(Color(0xFF1F1F1F), RoundedCornerShape(26.dp))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(26.dp))
                // Нажатие по самой карточке не закрывает окно.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(Icons.Filled.Delete, null, tint = Color(0xFFFF4757), modifier = Modifier.size(46.dp))
            Text(
                text = if (WorkoutSessionManager.totalCompletedSets == 0) "Ни одного подхода не отмечено" else "Отменить тренировку?",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Тренировка удалится вместе с введёнными значениями и счётчиком времени.",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            SlideToConfirm(title = "УДАЛИТЬ", onConfirmed = onConfirm)
            Text(
                text = "Вернуться к тренировке",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onDismiss() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun SlideToConfirm(title: String, onConfirmed: () -> Unit) {
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    // Срабатывает один раз: раньше вызов повторялся на каждый кадр у края.
    var confirmed by remember { mutableStateOf(false) }
    val red = Color(0xFFFF4757)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(red.copy(alpha = 0.20f), CircleShape)
            .border(1.dp, red.copy(alpha = 0.40f), CircleShape)
    ) {
        val maxDrag = (constraints.maxWidth - constraints.maxHeight).toFloat().coerceAtLeast(1f)
        Text(
            text = ">>> $title >>>",
            color = red,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp,
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer { alpha = 1f - (drag.value / maxDrag) * 0.8f }
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(drag.value.roundToInt(), 0) }
                .size(54.dp)
                .background(red, CircleShape)
                .pointerInput(maxDrag) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, amount ->
                            if (confirmed) return@detectHorizontalDragGestures
                            change.consume()
                            scope.launch { drag.snapTo((drag.value + amount).coerceIn(0f, maxDrag)) }
                            if (drag.value + amount >= maxDrag * 0.95f) {
                                confirmed = true
                                onConfirmed()
                            }
                        },
                        onDragEnd = { if (!confirmed) scope.launch { drag.animateTo(0f, Motion.snappy()) } },
                        onDragCancel = { if (!confirmed) scope.launch { drag.animateTo(0f, Motion.snappy()) } }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Delete, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

// MARK: - Время отдыха

@Composable
private fun RestDurationDialog(onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C1F),
        title = { Text("Время отдыха", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(60, 90, 120, 150, 180, 240).forEach { seconds ->
                    val minutes = if (seconds % 60 == 0) "${seconds / 60}" else "${seconds / 60},5"
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp))
                            .pressable { onSelect(seconds) }
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "$minutes мин (${String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)})",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = Color.White.copy(alpha = 0.6f)) }
        }
    )
}

// MARK: - Заметка

@Composable
private fun WorkoutNoteDialog(initialNote: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initialNote) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C1F),
        title = { Text("Заметка к тренировке", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Как прошла тренировка? Самочувствие, сон...", color = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Theme.accent,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    cursorColor = Theme.accent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) {
                Text("Готово", color = Theme.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = Color.White.copy(alpha = 0.6f)) }
        }
    )
}
