package com.texasprogram.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.data.GeminiKeyStore
import com.texasprogram.app.data.NutritionStore
import com.texasprogram.app.model.Coach
import com.texasprogram.app.model.CoachAction
import com.texasprogram.app.model.CoachHistory
import com.texasprogram.app.model.CoachMessage
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.service.FoodRecognizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// MARK: - ИИ-тренер

/// Вход в тренера в «Прогрессе». В понедельник и вторник — ещё и разбор
/// прошлой недели одной кнопкой.
@Composable
fun CoachEntryCard(profile: ProgramProfile, onUpdate: (ProgramProfile) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptics = rememberHapticEngine()
    var opened by remember { mutableStateOf<Boolean?>(null) }
    var version by remember { mutableStateOf(0) }
    val review = remember(profile.id, profile.completionLog, version) {
        Coach.needsReview(CoachHistory.read(CoachHistory.directory(context.filesDir), profile.id), profile)
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Theme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().pressable {
                haptics.light()
                opened = false
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(Theme.accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Theme.accent, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("ИИ-тренер", color = Theme.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Спроси про застой, прогресс, восстановление и питание — он видит твои тренировки.",
                    color = Theme.ink.copy(alpha = 0.5f),
                    fontSize = 13.sp
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Theme.ink.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
        }
        if (review) {
            NutritionButton("Разбор прошлой недели", icon = Icons.Filled.EventAvailable) {
                haptics.medium()
                opened = true
            }
        }
    }
    opened?.let { startReview ->
        CoachSheet(profile, onUpdate, startReview) {
            opened = null
            version += 1
        }
    }
}

@Composable
fun CoachSheet(profile: ProgramProfile, onUpdate: (ProgramProfile) -> Unit, startReview: Boolean = false, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHapticEngine()
    val directory = remember { CoachHistory.directory(context.filesDir) }
    val messages = remember { mutableStateListOf<CoachMessage>() }
    var draft by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastQuestion by remember { mutableStateOf<String?>(null) }
    var showingKey by remember { mutableStateOf(false) }
    var hasKey by remember { mutableStateOf(GeminiKeyStore.load(context) != null) }
    var confirmClear by remember { mutableStateOf(false) }
    // Профиль на момент нажатия «Применить»: сброс веса считается от свежих максимумов.
    var current by remember { mutableStateOf(profile) }
    val listState = rememberLazyListState()

    fun save() = CoachHistory.write(directory, profile.id, messages.toList())

    fun ask(raw: String, review: String? = null, retry: Boolean = false) {
        val question = raw.trim()
        if (question.isEmpty() || thinking) return
        val key = GeminiKeyStore.load(context)
        if (key == null) {
            showingKey = true
            return
        }
        error = null
        lastQuestion = question
        if (!retry) {
            messages.add(CoachMessage(role = "user", text = question, reviewWeek = review))
            save()
        }
        thinking = true
        scope.launch {
            try {
                val nutrition = withContext(Dispatchers.IO) { NutritionStore.read(NutritionStore.directory(context), profile.id) }
                val system = Coach.system + "\n\nДАННЫЕ:\n" + Coach.context(current, nutrition)
                // Последние реплики: первой должна идти реплика человека.
                val turns = messages.takeLast(16).map { (if (it.isUser) "user" else "model") to it.text }.dropWhile { it.first == "model" }
                val reply = Coach.parse(FoodRecognizer.converse(system, turns, key))
                val actions = reply.actions.filter { Coach.resolve(it, current) != null }
                haptics.success()
                messages.add(CoachMessage(role = "coach", text = reply.text, actions = actions, reviewWeek = review))
                save()
                lastQuestion = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: FoodRecognizer.Failure.NothingFound) {
                error = "Тренер не ответил. Попробуй ещё раз."
            } catch (e: Exception) {
                error = e.message ?: "Не получилось спросить тренера."
            } finally {
                thinking = false
            }
        }
    }

    fun apply(message: CoachMessage, index: Int) {
        val position = messages.indexOfFirst { it.id == message.id }
        if (position < 0 || index !in message.actions.indices || index in message.applied) return
        val next = Coach.apply(message.actions[index], current)
        current = next
        onUpdate(next)
        haptics.success()
        messages[position] = message.copy(applied = message.applied + index)
        save()
    }

    LaunchedEffect(Unit) {
        messages.addAll(withContext(Dispatchers.IO) { CoachHistory.read(directory, profile.id) })
        if (startReview) ask(Coach.REVIEW_PROMPT, review = Coach.reviewWeek())
    }
    LaunchedEffect(messages.size, thinking) {
        val count = listState.layoutInfo.totalItemsCount
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    LaunchedEffect(profile) { current = profile }

    NutritionSheet(
        title = "ИИ-тренер",
        onDismiss = onDismiss,
        leading = null,
        action = "Готово",
        onAction = { close -> close() },
        scrollable = false
    ) {
        // Первое нажатие «Очистить переписку» спрашивает, второе стирает.
        LaunchedEffect(confirmClear) {
            if (confirmClear) {
                kotlinx.coroutines.delay(3000)
                confirmClear = false
            }
        }
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!hasKey) item(key = "key") { CoachKeyCard { showingKey = true } }
            if (messages.isEmpty()) item(key = "intro") { CoachIntro() }
            items(messages, key = { it.id }) { message ->
                CoachBubble(message, current) { index -> apply(message, index) }
            }
            if (thinking) {
                item(key = "thinking") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(color = Theme.ink, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Text("Тренер думает…", color = Theme.ink.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (messages.isNotEmpty() && !thinking) {
                item(key = "clear") {
                    Text(
                        if (confirmClear) "Нажми ещё раз — переписка удалится" else "Очистить переписку",
                        color = Theme.ink.copy(alpha = 0.35f),
                        fontSize = 13.sp,
                        modifier = Modifier
                            .pressable {
                                if (!confirmClear) {
                                    confirmClear = true
                                    return@pressable
                                }
                                messages.clear()
                                CoachHistory.delete(directory, profile.id)
                                confirmClear = false
                            }
                            .padding(vertical = 8.dp)
                    )
                }
            }
            error?.let { message ->
                item(key = "error") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(message, color = Theme.record, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        lastQuestion?.let { question ->
                            Text(
                                "Повторить",
                                color = Theme.accent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.pressable { ask(question, retry = true) }
                            )
                        }
                    }
                }
            }
        }
        // Быстрые вопросы и поле ввода.
        Column(Modifier.fillMaxWidth().background(Theme.tone(0xFF0E0E0E)).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Coach.quickQuestions.forEach { question ->
                    Box(
                        Modifier
                            .height(32.dp)
                            .pressable(enabled = !thinking) { ask(question) }
                            .clip(CircleShape)
                            .background(Theme.tone(0xFF292929))
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(question, color = Theme.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Theme.tone(0xFF212121))
                        .padding(horizontal = 14.dp, vertical = 11.dp)
                ) {
                    if (draft.isEmpty()) Text("Спроси тренера", color = Theme.ink.copy(alpha = 0.4f), fontSize = 16.sp)
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it.take(1000) },
                        maxLines = 5,
                        textStyle = TextStyle(color = Theme.ink, fontSize = 16.sp),
                        cursorBrush = SolidColor(Theme.accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                val canSend = draft.isNotBlank() && !thinking
                Box(
                    Modifier
                        .size(44.dp)
                        .pressable(enabled = canSend) {
                            val question = draft
                            draft = ""
                            ask(question)
                        }
                        .clip(CircleShape)
                        .background(Theme.accent.copy(alpha = if (canSend) 1f else 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Отправить", tint = Theme.canvas, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
    if (showingKey) {
        GeminiKeySheet(
            title = "Ключ для ИИ-тренера",
            intro = "Тренер отвечает через ИИ Google Gemini по твоему ключу — тому же, что и для фото еды. Ключ бесплатный и хранится только на этом телефоне."
        ) {
            showingKey = false
            hasKey = GeminiKeyStore.load(context) != null
            // Ключ вставили ради разбора недели — сразу его и просим.
            val week = Coach.reviewWeek()
            if (hasKey && startReview && messages.none { it.reviewWeek == week }) ask(Coach.REVIEW_PROMPT, review = week)
        }
    }
}

@Composable
private fun CoachKeyCard(onKey: () -> Unit) {
    NutritionCard(spacing = 10.dp) {
        Text("Нужен ключ Google AI", color = Theme.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(
            "Тренер работает на ИИ Google Gemini по твоему бесплатному ключу. Если ключ уже есть для фото еды — он подойдёт.",
            color = Theme.ink.copy(alpha = 0.6f),
            fontSize = 13.sp
        )
        NutritionButton("Вставить ключ", onClick = onKey)
    }
}

@Composable
private fun CoachIntro() {
    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Theme.accent, modifier = Modifier.size(28.dp))
        Text(
            "Привет! Я вижу твою программу, подходы за полтора месяца, вес тела и питание.",
            color = Theme.ink,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Программу сам не меняю: если предложу сбросить вес или поменять шаг прибавки — появится кнопка «Применить».",
            color = Theme.ink.copy(alpha = 0.5f),
            fontSize = 13.sp
        )
    }
}

/// Реплика: свои — справа оранжевым, тренера — слева с кнопками действий.
@Composable
private fun CoachBubble(message: CoachMessage, profile: ProgramProfile, onApply: (Int) -> Unit) {
    if (message.isUser) {
        Row(Modifier.fillMaxWidth().padding(start = 48.dp), horizontalArrangement = Arrangement.End) {
            Text(
                message.text,
                color = Theme.canvas,
                fontSize = 15.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Theme.accent)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(end = 24.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Theme.surface)
            .padding(14.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (message.reviewWeek != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.EventAvailable, contentDescription = null, tint = Theme.accent, modifier = Modifier.size(14.dp))
                Text("РАЗБОР НЕДЕЛИ", color = Theme.accent, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
        }
        SelectionContainer { Text(plainMarkdown(message.text), color = Theme.ink, fontSize = 15.sp) }
        message.actions.forEachIndexed { index, action -> CoachActionRow(action, profile, index in message.applied) { onApply(index) } }
    }
}

@Composable
private fun CoachActionRow(action: CoachAction, profile: ProgramProfile, applied: Boolean, onApply: () -> Unit) {
    val resolved = Coach.resolve(action, profile)
    if (!applied && resolved == null) return
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Theme.tone(0xFF212121)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(resolved?.title ?: "Применено", color = Theme.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (!applied && resolved != null) Text(resolved.detail, color = Theme.ink.copy(alpha = 0.5f), fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        if (applied) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Theme.success, modifier = Modifier.size(16.dp))
                Text("Готово", color = Theme.success, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Box(
                Modifier.height(34.dp).pressable(onClick = onApply).clip(CircleShape).background(Theme.accent).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Применить", color = Theme.canvas, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/// Звёздочки разметки Gemini без отрисовки — убираем, чтобы текст читался.
internal fun plainMarkdown(text: String): String =
    text.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        .replace(Regex("(?m)^[ \\t]*[*-][ \\t]+"), "• ")
        .replace(Regex("(?<!\\*)\\*(?!\\s)(.+?)(?<!\\s)\\*(?!\\*)"), "$1")
