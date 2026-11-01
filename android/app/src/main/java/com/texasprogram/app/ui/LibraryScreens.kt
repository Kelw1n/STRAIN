package com.texasprogram.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.CommunityProgram
import com.texasprogram.app.model.ExerciseLibrary
import com.texasprogram.app.model.LibraryExercise
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.TemplateCatalog
import com.texasprogram.app.model.TemplateExercise
import com.texasprogram.app.model.TemplateSet
import com.texasprogram.app.model.WorkoutTemplate
import com.texasprogram.app.model.deleteCustomExercise
import com.texasprogram.app.model.deleteTemplate
import com.texasprogram.app.model.exerciseLibrary
import com.texasprogram.app.model.formatWeight
import com.texasprogram.app.model.saveCustomExercise
import com.texasprogram.app.model.saveTemplate
import com.texasprogram.app.service.SocialService

// MARK: - Библиотека упражнений

/// «Упражнения», как в REBRN: поиск, фильтры «Мышцы» и «Инвентарь», разделы
/// по группам мышц. В режиме выбора — галочки и кнопка «Дальше · N»; «i» — техника.
@Composable
fun ExerciseLibrarySheet(
    profile: ProgramProfile,
    onUpdateProfile: (ProgramProfile) -> Unit,
    onDismiss: () -> Unit,
    pickTitle: String = "Дальше",
    onPick: ((List<LibraryExercise>) -> Unit)? = null
) {
    val context = LocalContext.current
    remember { ExerciseLibrary.load(context) }
    val haptics = rememberHapticEngine()
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<String?>(null) }
    var equipment by remember { mutableStateOf<String?>(null) }
    val picked = remember { mutableStateListOf<LibraryExercise>() }
    var detail by remember { mutableStateOf<LibraryExercise?>(null) }
    var creating by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf(profile) }
    LaunchedEffect(profile) { current = profile }
    val items = remember(current.customExercises, query, group, equipment) {
        ExerciseLibrary.filter(current.exerciseLibrary, query, group, equipment)
    }
    val sections = remember(items, group) { ExerciseLibrary.sections(items, group) }
    val picking = onPick != null

    NutritionSheet(
        title = "Упражнения",
        onDismiss = onDismiss,
        leading = if (picking) "Отмена" else "Готово",
        action = "Создать",
        onAction = { creating = true },
        scrollable = false
    ) { close ->
        Box(Modifier.weight(1f)) {
            LazyColumn(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = if (picking) 110.dp else 32.dp)
            ) {
                item(key = "search") {
                    SearchField(query, "Поиск упражнения") { query = it }
                }
                item(key = "filters") {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterMenu("Мышцы", group, ExerciseLibrary.groups, Modifier.weight(1f)) { group = it }
                        FilterMenu("Инвентарь", equipment, ExerciseLibrary.equipment, Modifier.weight(1f)) { equipment = it }
                    }
                }
                if (sections.isEmpty()) {
                    item(key = "empty") {
                        Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Ничего не нашлось", color = Theme.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Создать «${query.ifBlank { "своё" }}»",
                                color = Theme.accent,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 8.dp).pressable { creating = true }
                            )
                        }
                    }
                }
                sections.forEach { (option, list) ->
                    item(key = "h-${option.id}") {
                        Text(
                            option.title.uppercase(),
                            color = Theme.ink.copy(alpha = 0.45f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
                        )
                    }
                    items(list, key = { "${option.id}-${it.id}" }) { item ->
                        val selected = picked.any { it.id == item.id }
                        LibraryRow(
                            item = item,
                            picking = picking,
                            selected = selected,
                            onClick = {
                                if (picking) {
                                    haptics.selection()
                                    if (selected) picked.removeAll { it.id == item.id } else picked.add(item)
                                } else {
                                    detail = item
                                }
                            },
                            onInfo = { detail = item },
                            onDelete = if (item.isCustom) ({
                                current = current.deleteCustomExercise(item.id)
                                onUpdateProfile(current)
                                picked.removeAll { it.id == item.id }
                            }) else null
                        )
                    }
                }
            }
            if (picking && picked.isNotEmpty()) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                        .fillMaxWidth()
                        .height(56.dp)
                        .pressable {
                            haptics.medium()
                            onPick?.invoke(picked.toList())
                            close()
                        }
                        .clip(CircleShape)
                        .background(Theme.ink),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${pickTitle.uppercase()} · ${picked.size}", color = Theme.canvas, fontSize = 16.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                }
            }
        }
    }

    detail?.let { ExerciseDetailSheet(current, it) { detail = null } }
    if (creating) {
        CustomExerciseSheet(initialName = query, onDismiss = { creating = false }) { exercise ->
            current = current.saveCustomExercise(exercise)
            onUpdateProfile(current)
            if (picking) picked.add(exercise)
        }
    }
}

@Composable
private fun LibraryRow(
    item: LibraryExercise,
    picking: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onInfo: () -> Unit,
    onDelete: (() -> Unit)?
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedPressable(onClick = onClick, onLongClick = if (onDelete != null) ({ menu = true }) else null)
                .padding(vertical = 8.dp)
                .semantics { contentDescription = item.name + if (selected) ", выбрано" else "" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExerciseThumb(item.name, 60.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.name, color = Theme.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Text(
                    ExerciseLibrary.groupTitle(item.group) + if (item.isCustom) " · своё" else "",
                    color = Theme.ink.copy(alpha = 0.45f),
                    fontSize = 12.sp
                )
            }
            if (picking) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (selected) Theme.accent else Color.Transparent)
                        .border(2.dp, Theme.ink.copy(alpha = if (selected) 0f else 0.25f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Theme.canvas, modifier = Modifier.size(15.dp))
                }
                Icon(
                    Icons.Filled.Info,
                    contentDescription = "Техника: ${item.name}",
                    tint = Theme.ink.copy(alpha = 0.45f),
                    modifier = Modifier.size(22.dp).pressable(onClick = onInfo)
                )
            } else {
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Theme.ink.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
            }
        }
        if (onDelete != null) {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Удалить своё упражнение", color = Theme.record) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = Theme.record) },
                    onClick = {
                        menu = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
private fun SearchField(value: String, placeholder: String, onChange: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(NutritionColors.field)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = Theme.ink.copy(alpha = 0.45f), modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = Theme.ink.copy(alpha = 0.35f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Theme.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                cursorBrush = SolidColor(Theme.accent),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun FilterMenu(
    title: String,
    value: String?,
    options: List<ExerciseLibrary.Option>,
    modifier: Modifier = Modifier,
    onSelect: (String?) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(42.dp)
                .pressable { open = true }
                .clip(RoundedCornerShape(12.dp))
                .background(NutritionColors.field)
                .border(1.dp, if (value == null) Color.Transparent else Theme.accent.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                options.firstOrNull { it.id == value }?.title ?: title,
                color = if (value == null) Theme.ink.copy(alpha = 0.85f) else Theme.accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = Theme.ink.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Все") }, onClick = { open = false; onSelect(null) },
                trailingIcon = { if (value == null) Icon(Icons.Filled.Check, contentDescription = null) })
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option.title) }, onClick = { open = false; onSelect(option.id) },
                    trailingIcon = { if (value == option.id) Icon(Icons.Filled.Check, contentDescription = null) })
            }
        }
    }
}

// MARK: - Техника упражнения

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseDetailSheet(profile: ProgramProfile, exercise: LibraryExercise, onDismiss: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    val points = remember(profile, exercise.name) { profile.history(exercise.name) }
    NutritionSheet(title = exercise.name, onDismiss = onDismiss, leading = null, action = "Готово", onAction = { it() }) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(NutritionColors.tile).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("Техника", "Прогресс").forEachIndexed { index, title ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (tab == index) NutritionColors.button else Color.Transparent)
                        .pressable { tab = index },
                    contentAlignment = Alignment.Center
                ) {
                    Text(title, color = if (tab == index) Theme.ink else Theme.ink.copy(alpha = 0.5f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ExerciseThumb(exercise.name, 220.dp) }
        if (tab == 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailChip(ExerciseLibrary.equipmentTitle(exercise.equipment))
                DetailChip(ExerciseLibrary.groupTitle(exercise.group))
            }
            NutritionCaption("Мышцы")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MuscleChip(ExerciseLibrary.groupTitle(exercise.group), main = true)
                exercise.secondary.forEach { MuscleChip(ExerciseLibrary.groupTitle(it), main = false) }
            }
            if (exercise.steps.isNotEmpty()) {
                NutritionCaption("Как выполнять")
                Column {
                    exercise.steps.forEachIndexed { index, step ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("${index + 1}", color = Theme.accent, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(18.dp))
                            Text(step, color = Theme.ink, fontSize = 16.sp)
                        }
                        if (index < exercise.steps.size - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(Theme.ink.copy(alpha = 0.08f)))
                    }
                }
            } else if (exercise.isCustom) {
                Text("Своё упражнение — техники здесь нет.", color = Theme.ink.copy(alpha = 0.5f), fontSize = 14.sp)
            }
        } else if (points.isEmpty()) {
            Text("Ещё не делал. Сделаешь в тренировке — здесь появятся лучший вес и история.", color = Theme.ink.copy(alpha = 0.6f), fontSize = 15.sp)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LibraryStat("Лучший вес", "${formatWeight(points.maxOf { it.best })} кг", Modifier.weight(1f))
                LibraryStat("Тренировок", "${points.size}", Modifier.weight(1f))
                LibraryStat("Прошлый раз", "${formatWeight(points.last().best)} кг", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DetailChip(text: String) {
    Text(
        text,
        color = Theme.ink,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(NutritionColors.tile).padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun MuscleChip(text: String, main: Boolean) {
    Text(
        text,
        color = if (main) Color.White else Theme.ink.copy(alpha = 0.85f),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (main) Theme.accent else Color.Transparent)
            .border(1.dp, if (main) Color.Transparent else Theme.ink.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun LibraryStat(title: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(NutritionColors.tile).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, color = Theme.ink.copy(alpha = 0.5f), fontSize = 12.sp)
        Text(value, color = Theme.ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

// MARK: - Своё упражнение

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomExerciseSheet(initialName: String = "", onDismiss: () -> Unit, onSave: (LibraryExercise) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    var group by remember { mutableStateOf("chest") }
    var equipment by remember { mutableStateOf("barbell") }
    NutritionSheet(
        title = "Создать упражнение",
        onDismiss = onDismiss,
        action = "Сохранить",
        actionEnabled = name.isNotBlank(),
        onAction = { close ->
            if (name.isNotBlank()) {
                onSave(LibraryExercise.custom(name.trim(), group, equipment))
                close()
            }
        }
    ) {
        NutritionCard(spacing = 10.dp) {
            NutritionCaption("Название")
            NutritionTextField(name, { name = it.take(60) }, "Например, жим Ларсена",
                keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
        }
        NutritionCard(spacing = 10.dp) {
            NutritionCaption("Мышцы")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExerciseLibrary.groups.forEach { option -> ChoiceChip(option.title, group == option.id) { group = option.id } }
            }
        }
        NutritionCard(spacing = 10.dp) {
            NutritionCaption("Инвентарь")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExerciseLibrary.equipment.forEach { option -> ChoiceChip(option.title, equipment == option.id) { equipment = option.id } }
            }
        }
    }
}

@Composable
private fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(36.dp)
            .pressable(onClick = onClick)
            .clip(CircleShape)
            .background(if (selected) Theme.accent else NutritionColors.chip)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (selected) Theme.canvas else Theme.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

// MARK: - Мои тренировки

/// «Мои тренировки» как «Выбрать программу» в REBRN: «Мои», «Каталог», «Сообщество».
@Composable
fun TemplatesSheet(
    profile: ProgramProfile,
    onUpdateProfile: (ProgramProfile) -> Unit,
    onStart: (WorkoutTemplate) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    remember { ExerciseLibrary.load(context) }
    var current by remember { mutableStateOf(profile) }
    LaunchedEffect(profile) { current = profile }
    fun update(next: ProgramProfile) {
        current = next
        onUpdateProfile(next)
    }
    var tab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Pair<WorkoutTemplate, Boolean>?>(null) }
    var preview by remember { mutableStateOf<Triple<WorkoutTemplate, String?, Boolean>?>(null) }
    var community by remember { mutableStateOf<List<CommunityProgram>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<WorkoutTemplate?>(null) }
    val signedIn = SocialService.token != null

    LaunchedEffect(tab, query) {
        if (tab != 2 || !signedIn) return@LaunchedEffect
        loading = true
        runCatching { SocialService.communityPrograms(query) }
            .onSuccess { community = it; error = null }
            .onFailure { error = it.message ?: "Не удалось загрузить" }
        loading = false
    }

    fun matches(name: String) = query.isBlank() || ExerciseLibrary.normalized(name).contains(ExerciseLibrary.normalized(query))

    NutritionSheet(
        title = "Мои тренировки",
        onDismiss = onDismiss,
        leading = "Закрыть",
        action = "Создать",
        onAction = { editing = WorkoutTemplate(name = "") to true }
    ) { close ->
        fun start(template: WorkoutTemplate) {
            close()
            onStart(template)
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(NutritionColors.tile).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("Мои", "Каталог", "Сообщество").forEachIndexed { index, title ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (tab == index) NutritionColors.button else Color.Transparent)
                        .pressable { tab = index },
                    contentAlignment = Alignment.Center
                ) {
                    Text(title, color = if (tab == index) Theme.ink else Theme.ink.copy(alpha = 0.5f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        SearchField(query, if (tab == 2) "Поиск программы" else "Поиск тренировки") { query = it }

        when (tab) {
            0 -> {
                val mine = current.workoutTemplates.sortedByDescending { it.createdAt }.filter { matches(it.name) }
                if (current.workoutTemplates.isEmpty()) {
                    NutritionCard(spacing = 12.dp) {
                        Text("Своих тренировок пока нет", color = Theme.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Собери тренировку из упражнений библиотеки или возьми готовую в «Каталоге». Программа при этом не меняется.",
                            color = Theme.ink.copy(alpha = 0.55f),
                            fontSize = 14.sp
                        )
                        NutritionButton("Создать тренировку", icon = Icons.Filled.Add) { editing = WorkoutTemplate(name = "") to true }
                        NutritionButton("Начать пустую", icon = Icons.Filled.PlayArrow, primary = false) { start(WorkoutTemplate(name = "Тренировка")) }
                    }
                } else {
                    mine.forEach { template ->
                        TemplateRow(template.name, template.summary, if (template.isShared) "в профиле" else null,
                            onOpen = { preview = Triple(template, null, true) },
                            actions = listOf(
                                TemplateMenuAction("Начать", Icons.Filled.PlayArrow) { start(template) },
                                TemplateMenuAction("Изменить", Icons.Filled.Edit) { editing = template to false },
                                TemplateMenuAction("Дублировать", Icons.Filled.ContentCopy) { update(current.saveTemplate(template.duplicate(template.name + " (копия)"))) },
                                TemplateMenuAction(
                                    if (template.isShared) "Убрать из профиля" else "Показывать в профиле",
                                    if (template.isShared) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                                ) { update(current.saveTemplate(template.copy(isShared = !template.isShared))) },
                                TemplateMenuAction("Удалить", Icons.Filled.Delete, destructive = true) { deleting = template }
                            )
                        )
                    }
                    NutritionButton("Пустая тренировка", icon = Icons.Filled.PlayArrow, primary = false) { start(WorkoutTemplate(name = "Тренировка")) }
                }
            }
            1 -> TemplateCatalog.all.filter { matches(it.name) }.forEach { template ->
                TemplateRow(template.name, template.summary + " · " + template.groups().take(3).joinToString(", "), null,
                    onOpen = { preview = Triple(template, null, false) },
                    actions = listOf(
                        TemplateMenuAction("Начать", Icons.Filled.PlayArrow) { start(template) },
                        TemplateMenuAction("Сохранить себе", Icons.Filled.Download) { update(current.saveTemplate(template.duplicate())); tab = 0 }
                    )
                )
            }
            else -> when {
                !signedIn -> Message("Создай профиль STRAIN во вкладке «Профиль», чтобы видеть программы других людей.")
                loading && community.isEmpty() -> Message("Загружаю…")
                error != null && community.isEmpty() -> Message(error ?: "")
                community.isEmpty() -> Message("Пока никто не показал свои тренировки. Покажи свою: «…» → «Показывать в профиле».")
                else -> community.forEach { item ->
                    val template = item.program.template
                    TemplateRow(item.title, "@${item.author.username} · ${template.summary}", if (item.program.isTemplate) null else "программа",
                        onOpen = { preview = Triple(template, item.author.username, false) },
                        actions = listOf(
                            TemplateMenuAction("Начать", Icons.Filled.PlayArrow) { start(template) },
                            TemplateMenuAction("Сохранить себе", Icons.Filled.Download) { update(current.saveTemplate(template.duplicate())); tab = 0 }
                        )
                    )
                }
            }
        }

        preview?.let { (template, author, own) ->
            TemplatePreviewSheet(
                template = template,
                author = author,
                isOwn = own,
                onStart = { preview = null; start(template) },
                onCopy = { update(current.saveTemplate(template.duplicate())); tab = 0 },
                onEdit = { editing = template to false },
                onDismiss = { preview = null }
            )
        }
    }

    editing?.let { (template, isNew) ->
        TemplateEditorSheet(current, template, isNew, onUpdateProfile = { update(it) }, onDismiss = { editing = null }) { saved ->
            update(current.saveTemplate(saved))
            tab = 0
        }
    }
    deleting?.let { template ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Удалить «${template.name}»?") },
            text = { Text("Прошлые тренировки по ней останутся в истории.") },
            confirmButton = {
                Text("Удалить", color = Theme.record, fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp).pressable {
                    update(current.deleteTemplate(template.id))
                    deleting = null
                })
            },
            dismissButton = {
                Text("Отмена", modifier = Modifier.padding(12.dp).pressable { deleting = null })
            }
        )
    }
}

private data class TemplateMenuAction(val title: String, val icon: ImageVector, val destructive: Boolean = false, val run: () -> Unit)

@Composable
private fun Message(text: String) {
    Text(text, color = Theme.ink.copy(alpha = 0.55f), fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp))
}

@Composable
private fun TemplateRow(title: String, subtitle: String, badge: String?, onOpen: () -> Unit, actions: List<TemplateMenuAction>) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NutritionColors.tile)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).pressable(onClick = onOpen), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title.ifBlank { "Без названия" }, color = Theme.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (badge != null) {
                    Text(badge, color = Theme.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(CircleShape).background(Theme.accent.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            Text(subtitle, color = Theme.ink.copy(alpha = 0.45f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box {
            Box(Modifier.size(36.dp).pressable { menu = true }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.MoreHoriz, contentDescription = "Действия: $title", tint = Theme.ink.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                actions.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(action.title, color = if (action.destructive) Theme.record else Color.Unspecified) },
                        leadingIcon = { Icon(action.icon, contentDescription = null, tint = if (action.destructive) Theme.record else Theme.ink) },
                        onClick = {
                            menu = false
                            action.run()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplatePreviewSheet(
    template: WorkoutTemplate,
    author: String?,
    isOwn: Boolean,
    onStart: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    NutritionSheet(title = template.name.ifBlank { "Без названия" }, onDismiss = onDismiss, leading = null, action = "Готово", onAction = { it() }) { close ->
        Text(listOfNotNull(author?.let { "@$it" }, template.summary).joinToString(" · "), color = Theme.ink.copy(alpha = 0.5f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        NutritionCard(spacing = 0.dp, padding = 14.dp) {
            template.exercises.forEachIndexed { index, exercise ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ExerciseThumb(exercise.name, 48.dp)
                    Column(Modifier.weight(1f)) {
                        Text(exercise.name, color = Theme.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                        Text(templateDetail(exercise), color = Theme.ink.copy(alpha = 0.45f), fontSize = 12.sp)
                    }
                }
                if (index < template.exercises.size - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(Theme.ink.copy(alpha = 0.06f)))
            }
        }
        NutritionButton("Начать тренировку", icon = Icons.Filled.PlayArrow) { close(); onStart() }
        if (isOwn) NutritionButton("Изменить", icon = Icons.Filled.Edit, primary = false) { close(); onEdit() }
        else NutritionButton("Сохранить себе", icon = Icons.Filled.Download, primary = false) { onCopy(); close() }
    }
}

private fun templateDetail(exercise: TemplateExercise): String {
    val reps = exercise.sets.firstOrNull()?.reps ?: 0
    val weight = exercise.sets.firstOrNull { it.weight > 0 }?.weight
    return listOfNotNull("${exercise.sets.size} × $reps", weight?.let { "${formatWeight(it)} кг" }, "отдых " + restText(exercise.restSeconds)).joinToString(" · ")
}

/// «1:30», «2 мин», «45 с» — отдых между подходами.
fun restText(seconds: Int): String = when {
    seconds < 60 -> "$seconds с"
    seconds % 60 == 0 -> "${seconds / 60} мин"
    else -> "%d:%02d".format(seconds / 60, seconds % 60)
}

private val restOptions = listOf(30, 45, 60, 90, 120, 150, 180, 240, 300)

// MARK: - Конструктор

/// Конструктор тренировки, как в REBRN: название, упражнения с отдыхом и
/// подходами «кг × повт», порядок, добавить из библиотеки, «Выйти без сохранения?».
@Composable
fun TemplateEditorSheet(
    profile: ProgramProfile,
    original: WorkoutTemplate,
    isNew: Boolean,
    onUpdateProfile: (ProgramProfile) -> Unit,
    onDismiss: () -> Unit,
    onSave: (WorkoutTemplate) -> Unit
) {
    val haptics = rememberHapticEngine()
    var draft by remember { mutableStateOf(original) }
    var picking by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<LibraryExercise?>(null) }
    val canSave = draft.name.isNotBlank() && draft.exercises.isNotEmpty()
    // Закрыть лист из подтверждения выхода: держим без состояния, чтобы не перерисовывать.
    val closeSheet = remember { arrayOfNulls<() -> Unit>(1) }

    NutritionSheet(
        title = if (isNew) "Новая тренировка" else "Тренировка",
        onDismiss = onDismiss,
        leading = "Отмена",
        action = "Сохранить",
        actionEnabled = canSave,
        onAction = { close ->
            if (canSave) {
                haptics.success()
                onSave(draft.copy(name = draft.name.trim()))
                close()
            }
        },
        // Есть несохранённые правки — сначала спросим.
        onLeading = { close -> if (draft != original) confirmExit = true else close() }
    ) { close ->
        closeSheet[0] = close
        NutritionTextField(draft.name, { draft = draft.copy(name = it.take(60)) }, "Название тренировки", fontSize = 20, weight = FontWeight.Bold,
            keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
        draft.exercises.forEachIndexed { index, exercise ->
            androidx.compose.runtime.key(exercise.id) {
                EditorExercise(
                    exercise = exercise,
                    index = index,
                    count = draft.exercises.size,
                    onChange = { changed -> draft = draft.copy(exercises = draft.exercises.toMutableList().also { it[index] = changed }) },
                    onMove = { offset ->
                        val target = index + offset
                        if (target in draft.exercises.indices) {
                            haptics.selection()
                            draft = draft.copy(exercises = draft.exercises.toMutableList().also { java.util.Collections.swap(it, index, target) })
                        }
                    },
                    onRemove = { draft = draft.copy(exercises = draft.exercises.filterIndexed { i, _ -> i != index }) },
                    onInfo = { info = ExerciseLibrary.find(exercise.name, profile.customExercises) }
                )
            }
        }
        NutritionButton("Добавить упражнения", icon = Icons.Filled.Add, primary = draft.exercises.isEmpty()) { picking = true }
    }

    if (picking) {
        ExerciseLibrarySheet(profile, onUpdateProfile, onDismiss = { picking = false }, pickTitle = "Добавить") { items ->
            draft = draft.copy(exercises = draft.exercises + items.map { TemplateExercise(name = it.name, restSeconds = profile.defaultRestSeconds) })
        }
    }
    info?.let { ExerciseDetailSheet(profile, it) { info = null } }
    if (confirmExit) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Выйти без сохранения?") },
            text = { Text("Изменения в тренировке пропадут.") },
            confirmButton = {
                Text("Выйти", color = Theme.record, fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp).pressable {
                    confirmExit = false
                    closeSheet[0]?.invoke()
                })
            },
            dismissButton = { Text("Остаться", modifier = Modifier.padding(12.dp).pressable { confirmExit = false }) }
        )
    }
}

@Composable
private fun EditorExercise(
    exercise: TemplateExercise,
    index: Int,
    count: Int,
    onChange: (TemplateExercise) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    onInfo: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    var restMenu by remember { mutableStateOf(false) }
    NutritionCard(spacing = 10.dp, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.pressable(onClick = onInfo)) { ExerciseThumb(exercise.name, 48.dp) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(exercise.name, color = Theme.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                Box {
                    Row(Modifier.pressable { restMenu = true }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.Timer, contentDescription = null, tint = Theme.accent, modifier = Modifier.size(13.dp))
                        Text("Отдых " + restText(exercise.restSeconds), color = Theme.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = Theme.accent, modifier = Modifier.size(14.dp))
                    }
                    DropdownMenu(expanded = restMenu, onDismissRequest = { restMenu = false }) {
                        restOptions.forEach { seconds ->
                            DropdownMenuItem(text = { Text(restText(seconds)) }, onClick = {
                                restMenu = false
                                onChange(exercise.copy(restSeconds = seconds))
                            }, trailingIcon = { if (seconds == exercise.restSeconds) Icon(Icons.Filled.Check, contentDescription = null) })
                        }
                    }
                }
            }
            Box {
                Box(Modifier.size(36.dp).pressable { menu = true }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.MoreHoriz, contentDescription = "Действия: ${exercise.name}", tint = Theme.ink.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (index > 0) DropdownMenuItem(text = { Text("Выше") }, leadingIcon = { Icon(Icons.Filled.ArrowUpward, null) }, onClick = { menu = false; onMove(-1) })
                    if (index < count - 1) DropdownMenuItem(text = { Text("Ниже") }, leadingIcon = { Icon(Icons.Filled.ArrowDownward, null) }, onClick = { menu = false; onMove(1) })
                    DropdownMenuItem(
                        text = { Text("Убрать упражнение", color = Theme.record) },
                        leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Theme.record) },
                        onClick = { menu = false; onRemove() }
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text("ПОДХОД", Modifier.width(64.dp), color = Theme.ink.copy(alpha = 0.4f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("КГ", Modifier.weight(1f), color = Theme.ink.copy(alpha = 0.4f), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("ПОВТ", Modifier.weight(1f), color = Theme.ink.copy(alpha = 0.4f), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Box(Modifier.width(30.dp))
        }
        exercise.sets.forEachIndexed { setIndex, set ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${setIndex + 1}", Modifier.width(58.dp), color = Theme.ink.copy(alpha = 0.7f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                CellField(set.weight, decimal = true, Modifier.weight(1f)) { value ->
                    onChange(exercise.copy(sets = exercise.sets.toMutableList().also { it[setIndex] = set.copy(weight = value) }))
                }
                CellField(set.reps.toDouble(), decimal = false, Modifier.weight(1f)) { value ->
                    onChange(exercise.copy(sets = exercise.sets.toMutableList().also { it[setIndex] = set.copy(reps = value.toInt()) }))
                }
                Icon(
                    Icons.Filled.RemoveCircle,
                    contentDescription = "Убрать подход ${setIndex + 1}",
                    tint = Theme.ink.copy(alpha = if (exercise.sets.size > 1) 0.35f else 0.1f),
                    modifier = Modifier.size(24.dp).pressable(enabled = exercise.sets.size > 1) {
                        onChange(exercise.copy(sets = exercise.sets.filterIndexed { i, _ -> i != setIndex }))
                    }
                )
            }
        }
        NutritionButton("Подход", icon = Icons.Filled.Add, primary = false) {
            onChange(exercise.copy(sets = exercise.sets + (exercise.sets.lastOrNull() ?: TemplateSet(0.0, 10))))
        }
    }
}

/// Число в ячейке подхода: пустое — ноль, запятая и точка — одно и то же.
@Composable
private fun CellField(value: Double, decimal: Boolean, modifier: Modifier = Modifier, onChange: (Double) -> Unit) {
    fun format(v: Double) = if (v <= 0) "" else if (decimal) formatWeight(v) else v.toInt().toString()
    var text by remember { mutableStateOf(format(value)) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value) { if (parseAmount(text) != value) text = format(value) }
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) NutritionColors.fieldFocused else NutritionColors.field)
            .border(1.5.dp, if (focused) Theme.accent else Color.Transparent, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (text.isEmpty()) Text(if (decimal) "—" else "0", color = Theme.ink.copy(alpha = 0.3f), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        BasicTextField(
            value = text,
            onValueChange = { input ->
                val cleaned = input.filter { it.isDigit() || (decimal && (it == ',' || it == '.')) }.take(6)
                text = cleaned
                val parsed = parseAmount(cleaned)
                if (parsed != value) onChange(parsed)
            },
            singleLine = true,
            textStyle = TextStyle(color = Theme.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(Theme.accent),
            keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
        )
    }
}
