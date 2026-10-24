package com.texasprogram.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.data.GeminiKeyStore
import com.texasprogram.app.data.NutritionStore
import com.texasprogram.app.model.BodyWeightEntry
import com.texasprogram.app.model.FoodEntry
import com.texasprogram.app.model.LimarMethod
import com.texasprogram.app.model.Nutrients
import com.texasprogram.app.model.NutritionGoal
import com.texasprogram.app.model.NutritionPlan
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.service.RecognizedFood
import com.texasprogram.app.service.RuDate
import java.time.LocalDate
import kotlin.math.abs

/// Вкладка «Питание»: дневник еды и метод Лимара — неделя наблюдения,
/// поддержка, дальше +30 г углеводов на наборе или −13 г жиров на сушке.
@Composable
fun NutritionScreen(
    profile: ProgramProfile,
    contentPadding: PaddingValues,
    onUpdate: (ProgramProfile) -> Unit
) {
    val context = LocalContext.current
    val store = remember { NutritionStore.get(context) }
    // Файл профиля читаем до первого кадра: иначе мелькнул бы чужой или пустой дневник.
    remember(profile.id) { store.open(profile.id) }
    NutritionContent(
        profile = profile,
        data = store.data,
        contentPadding = contentPadding,
        actions = remember(store) { NutritionActions(store) },
        hasKey = { GeminiKeyStore.load(context) != null },
        onUpdate = onUpdate
    )
}

/// Что вкладка делает с дневником. Отдельно от экрана — для снимков экрана без файлов.
class NutritionActions(private val store: NutritionStore?) {
    fun add(entries: List<FoodEntry>) = store?.add(entries)
    fun update(entry: FoodEntry) = store?.update(entry)
    fun remove(entry: FoodEntry) = store?.remove(entry)
    fun recent(): List<FoodEntry> = store?.recentFoods().orEmpty()
    fun startObservation() = store?.startObservation()
    fun choose(goal: NutritionGoal, maintenance: Nutrients) = store?.choose(goal, maintenance)
    fun apply(norm: Nutrients, note: String) = store?.apply(norm, note)
    fun resetPlan() = store?.resetPlan()
}

@Composable
fun NutritionContent(
    profile: ProgramProfile,
    data: com.texasprogram.app.model.NutritionData,
    contentPadding: PaddingValues,
    actions: NutritionActions,
    hasKey: () -> Boolean,
    onUpdate: (ProgramProfile) -> Unit,
    today: LocalDate = LocalDate.now()
) {
    val haptics = rememberHapticEngine()
    var dayNumber by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    val day = LocalDate.ofEpochDay(dayNumber)
    var photo by remember { mutableStateOf<Uri?>(null) }
    var editing by remember { mutableStateOf<FoodEntry?>(null) }
    var addingManually by remember { mutableStateOf(false) }
    var showingGuide by remember { mutableStateOf(false) }
    var showingKey by remember { mutableStateOf(false) }
    var keySaved by remember { mutableStateOf(hasKey()) }

    LaunchedEffect(Unit) { if (dayNumber > today.toEpochDay()) dayNumber = today.toEpochDay() }
    val listState = rememberLazyListState()
    // Повторное нажатие на «Питание»: открыт прошлый день — назад к сегодня, сегодня — наверх.
    OnTabReselect(MainTabItem.NUTRITION) {
        if (dayNumber != today.toEpochDay()) dayNumber = today.toEpochDay()
        listState.scrollToTop()
    }

    val camera = rememberCameraLauncher { photo = it }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) photo = uri }

    val dayEntries = remember(data.entries, day) { LimarMethod.entries(data.entries, day) }
    val eaten = remember(dayEntries) { LimarMethod.total(dayEntries) }
    val target = data.plan?.target
    val dayWeight = profile.bodyWeightLog.firstOrNull { it.epochDay == dayNumber }

    /// Без ключа фото разобрать нечем — сначала просим ключ.
    fun startPhoto(fromCamera: Boolean) {
        if (!hasKey()) {
            showingKey = true
            return
        }
        if (fromCamera) camera.launch() else gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "title") {
            ScreenTitle("Питание") {
                Box(
                    Modifier
                        .size(36.dp)
                        .pressable { showingKey = true }
                        .clip(CircleShape)
                        .background(NutritionColors.chip),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (keySaved) Icons.Filled.Key else Icons.Outlined.Key,
                        contentDescription = "Ключ Google для фото еды",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        item(key = "stage") {
            Box(Modifier.animateContentSize(Motion.card())) {
                StageCard(profile, data, today, actions, haptics)
            }
        }
        item(key = "picker") {
            DayPicker(day, today) { delta ->
                val next = day.plusDays(delta.toLong())
                if (!next.isAfter(today)) {
                    haptics.selection()
                    dayNumber = next.toEpochDay()
                }
            }
        }
        item(key = "day") { DayCard(eaten, target) }
        item(key = "weight-$dayNumber") {
            WeightCard(dayWeight) { value ->
                haptics.success()
                onUpdate(profile.recordBodyWeight(value, day))
            }
        }
        item(key = "add") {
            AddButtons(
                onPhoto = ::startPhoto,
                onManual = { addingManually = true }
            )
        }
        if (dayEntries.isNotEmpty()) {
            item(key = "entries") {
                EntriesCard(dayEntries, onOpen = { editing = it }, onDelete = {
                    haptics.medium()
                    actions.remove(it)
                })
            }
        }
        item(key = "guide") { GuideLink { showingGuide = true } }
    }

    photo?.let { picked ->
        FoodPhotoSheet(
            photo = picked,
            onAdd = { foods -> actions.add(recognizedEntries(foods, day)) },
            onDismiss = {
                photo = null
                keySaved = hasKey()
            }
        )
    }
    editing?.let { entry ->
        FoodEntrySheet(
            original = entry,
            day = day,
            onSave = { actions.update(it) },
            onDelete = { actions.remove(entry) },
            onDismiss = { editing = null }
        )
    }
    if (addingManually) {
        FoodEntrySheet(
            original = null,
            day = day,
            recent = remember { actions.recent() },
            onSave = { actions.add(listOf(it)) },
            onDismiss = { addingManually = false }
        )
    }
    if (showingGuide) NutritionGuideSheet { showingGuide = false }
    if (showingKey) {
        GeminiKeySheet {
            showingKey = false
            keySaved = hasKey()
        }
    }
}

private fun recognizedEntries(foods: List<RecognizedFood>, day: LocalDate): List<FoodEntry> {
    val moment = NutritionFormat.moment(day)
    return foods.map { FoodEntry(date = moment, name = it.name, grams = it.grams, nutrients = it.nutrients, fromPhoto = true) }
}

// MARK: - Этап метода

@Composable
private fun StageCard(profile: ProgramProfile, data: com.texasprogram.app.model.NutritionData, today: LocalDate, actions: NutritionActions, haptics: Haptics) {
    val plan = data.plan
    when {
        plan == null -> IntroCard {
            haptics.medium()
            actions.startObservation()
        }
        plan.goal != null -> GoalCard(plan, profile.bodyWeightLog, today, actions, haptics)
        else -> when (
            val state = remember(plan, data.entries, profile.bodyWeightLog, today) {
                LimarMethod.observation(plan, data.entries, profile.bodyWeightLog, today)
            }
        ) {
            is LimarMethod.Observation.Collecting -> CollectingCard(state, data.entries, profile.bodyWeightLog, today, actions)
            is LimarMethod.Observation.NotEnough -> NutritionCard {
                NutritionCaption("Неделя прошла", color = Theme.warning)
                Text(
                    "Записано мало: еда — ${state.week.foodDays} из 7 дней, вес — ${state.week.weightDays} из 7. Чтобы среднее не врало, нужно хотя бы ${LimarMethod.MINIMUM_DAYS} дней того и другого.",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 14.sp
                )
                NutritionButton("Начать неделю заново", icon = Icons.Filled.Refresh) { actions.startObservation() }
            }
            is LimarMethod.Observation.Done -> MaintenanceCard(state, actions, haptics)
        }
    }
}

@Composable
private fun IntroCard(onStart: () -> Unit) {
    NutritionCard {
        NutritionCaption("Метод Лимара", color = Theme.accent)
        Text("Найди свою норму калорий", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            "Без формул. Неделю ешь как обычно и записывай всё, что съел, и вес утром натощак. Средние калории при стоящем весе — твоя поддержка.",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp
        )
        NutritionButton("Начать неделю наблюдения", icon = Icons.Filled.PlayArrow, onClick = onStart)
    }
}

@Composable
private fun CollectingCard(
    state: LimarMethod.Observation.Collecting,
    entries: List<FoodEntry>,
    weights: List<BodyWeightEntry>,
    today: LocalDate,
    actions: NutritionActions
) {
    NutritionCard(spacing = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NutritionCaption("Неделя наблюдения · день ${minOf(state.day, 7)} из 7", color = Theme.accent, modifier = Modifier.weight(1f))
            StageMenu(
                listOf(
                    MenuAction("Начать неделю заново", Icons.Filled.Refresh) { actions.startObservation() },
                    MenuAction("Выйти из метода", Icons.Filled.Close, destructive = true) { actions.resetPlan() }
                )
            )
        }
        WeekDots(state.week.start, entries, weights, today)
        Text(
            "Ничего не меняй в питании — просто записывай еду и вес натощак каждое утро.",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp
        )
        Text(
            "Записано: еда — ${state.week.foodDays} дн., вес — ${state.week.weightDays} дн.",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun MaintenanceCard(state: LimarMethod.Observation.Done, actions: NutritionActions, haptics: Haptics) {
    val maintenance = state.maintenance
    NutritionCard {
        NutritionCaption("Твоя поддержка", color = Theme.accent)
        KcalHeadline(maintenance.kcal)
        Text(NutritionFormat.macros(maintenance), color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        state.week.weight?.let { weight ->
            Text(
                "Средний вес ${NutritionFormat.decimal(Math.round(weight * 10) / 10.0)} кг · записано ${state.week.foodDays} дн.",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        state.shift?.let { shift ->
            Text(
                NutritionFormat.nbsp("За неделю вес сдвинулся на ${NutritionFormat.change(shift)} — значит, это ещё не совсем поддержка. Можно понаблюдать ещё неделю или начать так: недельные проверки поправят норму."),
                color = Theme.warning,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        NutritionCaption("Что дальше", Modifier.padding(top = 4.dp))
        NutritionGoal.entries.forEach { goal ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .pressable {
                        haptics.medium()
                        actions.choose(goal, maintenance)
                    }
                    .clip(RoundedCornerShape(16.dp))
                    .background(NutritionColors.tile)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(goal.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(goal.explanation, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
            }
        }
        NutritionButton("Понаблюдать ещё неделю", primary = false) { actions.startObservation() }
    }
}

@Composable
private fun GoalCard(plan: NutritionPlan, weights: List<BodyWeightEntry>, today: LocalDate, actions: NutritionActions, haptics: Haptics) {
    val goal = plan.goal ?: NutritionGoal.HOLD
    NutritionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NutritionCaption("${goal.title} · норма на день", color = Theme.accent, modifier = Modifier.weight(1f))
            StageMenu(buildList {
                plan.maintenance?.let { maintenance ->
                    NutritionGoal.entries.filter { it != plan.goal }.forEach { other ->
                        add(MenuAction("Цель: ${other.title.lowercase()}", Icons.Filled.TrackChanges) { actions.choose(other, maintenance) })
                    }
                }
                add(MenuAction("Заново найти поддержку", Icons.Filled.Refresh) { actions.startObservation() })
                add(MenuAction("Выйти из метода", Icons.Filled.Close, destructive = true) { actions.resetPlan() })
            })
        }
        plan.target?.let { target ->
            KcalHeadline(target.kcal)
            Text(NutritionFormat.macros(target), color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        val check = remember(plan, weights, today) { LimarMethod.check(plan, weights, today) }
        if (check != null) {
            CheckBlock(check, goal, plan, actions, haptics)
        } else {
            Text(nextCheckLine(plan, today), color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun nextCheckLine(plan: NutritionPlan, today: LocalDate): String {
    val parts = mutableListOf<String>()
    plan.maintenance?.let { parts += "Поддержка ${NutritionFormat.kcal(it.kcal)} ккал" }
    plan.stepStart?.let { start ->
        val due = LimarMethod.day(start).plusDays(LimarMethod.WEEK_LENGTH.toLong())
        val left = maxOf(java.time.temporal.ChronoUnit.DAYS.between(today, due), 1L)
        parts += "проверка веса через $left дн."
    }
    return parts.joinToString(" · ")
}

@Composable
private fun CheckBlock(check: LimarMethod.Check, goal: NutritionGoal, plan: NutritionPlan, actions: NutritionActions, haptics: Haptics) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NutritionColors.tile)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        NutritionCaption("Неделя прошла")
        Text(changeLine(check.change), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(NutritionFormat.nbsp(adviceLine(check, goal)), color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp)
        if (check.lowFat) {
            Text(
                NutritionFormat.nbsp("Жиров станет ${NutritionFormat.grams(check.proposed.fat)} г — меньше ${NutritionFormat.decimal(LimarMethod.FAT_FLOOR_PER_KG)} г на кило веса. Ниже лучше не опускаться."),
                color = Theme.warning,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (check.advice == LimarMethod.Advice.KEEP) {
            NutritionButton("Ещё неделю на этой норме", icon = Icons.Filled.Check) { actions.apply(check.proposed, "Без изменений") }
        } else {
            NutritionButton("Применить: ${NutritionFormat.kcal(check.proposed.kcal)} ккал", icon = Icons.Filled.Check) {
                haptics.success()
                actions.apply(check.proposed, stepNote(check.advice))
            }
            NutritionButton("Оставить как есть", primary = false) {
                plan.target?.let { actions.apply(it, "Без изменений") }
            }
        }
    }
}

private fun changeLine(change: Double?): String {
    if (change == null) return "Мало замеров веса — нужно хотя бы ${LimarMethod.MINIMUM_DAYS} за неделю"
    if (abs(change) < 0.05) return "Средний вес не изменился за неделю"
    return "Средний вес ${NutritionFormat.change(change)} к прошлой неделе"
}

private fun adviceLine(check: LimarMethod.Check, goal: NutritionGoal): String {
    val next = "${NutritionFormat.kcal(check.proposed.kcal)} ккал"
    return when (check.advice) {
        LimarMethod.Advice.ADD_CARBS ->
            if (goal == NutritionGoal.HOLD) "Вес уходит вниз — добавь 30 г углеводов, норма станет $next."
            else "Вес почти не растёт — добавь ещё 30 г углеводов, норма станет $next."
        LimarMethod.Advice.REMOVE_CARBS -> "Вес растёт — убери 30 г углеводов, норма станет $next."
        LimarMethod.Advice.CUT_FAT -> "Вес почти не уходит — убери ещё 13 г жиров, норма станет $next."
        LimarMethod.Advice.KEEP -> when {
            check.change == null -> "Записывай вес натощак каждое утро — без него не видно, работает ли норма."
            goal == NutritionGoal.BULK -> "Вес растёт — норма работает, держи её ещё неделю."
            goal == NutritionGoal.CUT -> "Вес уходит — норма работает, держи её ещё неделю."
            else -> "Вес на месте — так и держи."
        }
    }
}

private fun stepNote(advice: LimarMethod.Advice): String = when (advice) {
    LimarMethod.Advice.ADD_CARBS -> "+30 г углеводов"
    LimarMethod.Advice.REMOVE_CARBS -> "−30 г углеводов"
    LimarMethod.Advice.CUT_FAT -> "−13 г жиров"
    LimarMethod.Advice.KEEP -> "Без изменений"
}

private class MenuAction(val title: String, val icon: ImageVector, val destructive: Boolean = false, val onClick: () -> Unit)

@Composable
private fun StageMenu(items: List<MenuAction>) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .size(width = 36.dp, height = 28.dp)
                .pressable { open = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.MoreHoriz, contentDescription = "Ещё", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { item ->
                val tint = if (item.destructive) Theme.record else Color.White
                DropdownMenuItem(
                    text = { Text(item.title, color = tint) },
                    leadingIcon = { Icon(item.icon, contentDescription = null, tint = tint) },
                    onClick = {
                        open = false
                        item.onClick()
                    }
                )
            }
        }
    }
}

/// Семь дней недели наблюдения: кружок — еда записана, полоска — вес.
@Composable
private fun WeekDots(start: LocalDate, entries: List<FoodEntry>, weights: List<BodyWeightEntry>, today: LocalDate) {
    // Дни с едой и с весом — один проход по дневнику, а не семь.
    val foodDays = remember(entries) { entries.mapTo(HashSet()) { LimarMethod.day(it.date).toEpochDay() } }
    val weighedDays = remember(weights) { weights.mapTo(HashSet()) { it.epochDay } }
    Row(Modifier.fillMaxWidth()) {
        repeat(LimarMethod.WEEK_LENGTH) { offset ->
            val date = start.plusDays(offset.toLong())
            val food = date.toEpochDay() in foodDays
            val weight = date.toEpochDay() in weighedDays
            val isToday = date == today
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    RuDate.short(RuDate.weekdayOf(date)),
                    color = Color.White.copy(alpha = if (isToday) 0.9f else 0.4f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(if (food) Theme.accent else NutritionColors.dot)
                        .border(1.5.dp, if (isToday) Color.White.copy(alpha = 0.8f) else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (food) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(15.dp))
                }
                Box(
                    Modifier
                        .size(width = 16.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(if (weight) Theme.success else NutritionColors.bar)
                )
            }
        }
    }
}

// MARK: - День

@Composable
private fun DayPicker(day: LocalDate, today: LocalDate, onShift: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        PickerChevron(Icons.Filled.ChevronLeft, "Предыдущий день", enabled = true) { onShift(-1) }
        Spacer(Modifier.weight(1f))
        Text(NutritionFormat.dayTitle(day, today), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        PickerChevron(Icons.Filled.ChevronRight, "Следующий день", enabled = day.isBefore(today)) { onShift(1) }
    }
}

@Composable
private fun PickerChevron(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.3f }
            .pressable(enabled = enabled, onClick = onClick)
            .clip(CircleShape)
            .background(NutritionColors.chip),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun DayCard(eaten: Nutrients, target: Nutrients?) {
    NutritionCard {
        NutritionCaption("Съедено")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            KcalHeadline(eaten.kcal)
            Spacer(Modifier.weight(1f))
            if (target != null) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                    Text("из ${NutritionFormat.kcal(target.kcal)}", color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    val left = target.kcal - eaten.kcal
                    Text(
                        if (left >= 0) "осталось ${NutritionFormat.kcal(left)}" else "сверху ${NutritionFormat.kcal(-left)}",
                        color = if (eaten.kcal > target.kcal) Theme.warning else Color.White.copy(alpha = 0.45f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        if (target != null && target.kcal > 0) {
            LineMeter((eaten.kcal / target.kcal).toFloat())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MacroTile("Белки", eaten.protein, target?.protein, Modifier.weight(1f))
            MacroTile("Жиры", eaten.fat, target?.fat, Modifier.weight(1f))
            MacroTile("Углеводы", eaten.carbs, target?.carbs, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MacroTile(title: String, value: Double, goal: Double?, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(NutritionColors.tile)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        // Норма — строкой ниже: на узком экране рядом с числом она не влезала.
        Text(
            NutritionFormat.grams(value) + if (goal == null) " г" else "",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false
        )
        if (goal != null) {
            Text("из ${NutritionFormat.grams(goal)} г", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (goal > 0) LineMeter((value / goal).toFloat(), height = 4.dp)
        }
    }
}

// MARK: - Вес натощак

@Composable
private fun WeightCard(dayWeight: BodyWeightEntry?, onRecord: (Double) -> Unit) {
    var input by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val parsed = input.replace(',', '.').trim().toDoubleOrNull()?.takeIf { it > 20 && it < 400 }
    val record = {
        parsed?.let {
            onRecord(it)
            input = ""
            focus.clearFocus()
        }
    }

    NutritionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NutritionCaption("Вес натощак", Modifier.weight(1f))
            dayWeight?.let {
                Text(NutritionFormat.kilograms(it.weight), color = Theme.success, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .height(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (focused) NutritionColors.fieldFocused else NutritionColors.field)
                    .border(1.5.dp, if (focused) Theme.accent else Color.Transparent, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (input.isEmpty()) {
                    Text(
                        if (dayWeight == null) "Утром, кг" else "Поправить, кг",
                        color = Color.White.copy(alpha = 0.3f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                BasicTextField(
                    value = input,
                    onValueChange = { text -> input = text.filter { it.isDigit() || it == ',' || it == '.' }.take(6) },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    cursorBrush = SolidColor(Theme.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { record() }),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
                )
            }
            Box(
                Modifier
                    .height(50.dp)
                    .pressable(enabled = parsed != null) { record() }
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (parsed == null) NutritionColors.button else Theme.accent)
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Записать",
                    color = if (parsed == null) Color.White.copy(alpha = 0.4f) else Color.Black,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            "Попадает в «Замеры веса». Взвешивайся каждое утро натощак — так средний вес недели честный.",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 12.sp
        )
    }
}

// MARK: - Добавить еду

@Composable
private fun AddButtons(onPhoto: (Boolean) -> Unit, onManual: () -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) {
            AddButton("Сфоткать еду", Icons.Filled.CameraAlt, primary = true) { choosing = true }
            DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
                DropdownMenuItem(
                    text = { Text("Снять", color = Color.White) },
                    leadingIcon = { Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = Color.White) },
                    onClick = {
                        choosing = false
                        onPhoto(true)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Из галереи", color = Color.White) },
                    leadingIcon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = Color.White) },
                    onClick = {
                        choosing = false
                        onPhoto(false)
                    }
                )
            }
        }
        AddButton("Вручную", Icons.Filled.EditNote, primary = false, modifier = Modifier.weight(1f), onClick = onManual)
    }
}

@Composable
private fun AddButton(title: String, icon: ImageVector, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val tint = if (primary) Color.Black else Color.White
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .pressable(onClick = onClick)
            .clip(RoundedCornerShape(14.dp))
            .background(if (primary) Theme.accent else NutritionColors.button),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, color = tint, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

// MARK: - Записи дня

@Composable
private fun EntriesCard(entries: List<FoodEntry>, onOpen: (FoodEntry) -> Unit, onDelete: (FoodEntry) -> Unit) {
    NutritionCard(spacing = 0.dp) {
        NutritionCaption("Записи · ${entries.size}", Modifier.padding(bottom = 10.dp))
        entries.forEachIndexed { index, entry ->
            androidx.compose.runtime.key(entry.id) {
                EntryRow(entry, onOpen = { onOpen(entry) }, onDelete = { onDelete(entry) })
            }
            if (index < entries.size - 1) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
            }
        }
    }
}

/// Нажатие — правка, долгое нажатие — удалить, как контекстное меню на iPhone.
@Composable
private fun EntryRow(entry: FoodEntry, onOpen: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedPressable(onClick = onOpen, onLongClick = { menu = true })
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(entry.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Text(entryDetail(entry), color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("${NutritionFormat.kcal(entry.nutrients.kcal)} ккал", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(NutritionFormat.macros(entry.nutrients), color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Удалить", color = Theme.record) },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = Theme.record) },
                onClick = {
                    menu = false
                    onDelete()
                }
            )
        }
    }
}

private fun entryDetail(entry: FoodEntry): String {
    val parts = mutableListOf<String>()
    entry.grams?.let { parts += "${NutritionFormat.grams(it)} г" }
    if (entry.fromPhoto) parts += "по фото"
    parts += NutritionFormat.time(entry.date)
    return parts.joinToString(" · ")
}

@Composable
private fun GuideLink(onClick: () -> Unit) {
    NutritionCard(Modifier.pressable(onClick = onClick), padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null, tint = Theme.accent, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Как работает метод", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("Почему не формулы и как найти свою норму", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
        }
    }
}
