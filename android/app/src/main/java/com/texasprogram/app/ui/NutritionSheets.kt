package com.texasprogram.app.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.texasprogram.app.data.GeminiKeyStore
import com.texasprogram.app.model.FoodEntry
import com.texasprogram.app.model.Nutrients
import com.texasprogram.app.model.newNutritionId
import com.texasprogram.app.model.roundHalfUp
import com.texasprogram.app.service.FoodRecognizer
import com.texasprogram.app.service.RecognizedFood
import com.texasprogram.app.service.RecognizedMeal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate

// MARK: - Запись вручную

/// Запись еды вручную или правка уже записанной. Калории считаются по Б/Ж/У
/// сами; знаешь калории с упаковки — впиши их, тогда они главнее.
@Composable
fun FoodEntrySheet(
    original: FoodEntry?,
    day: LocalDate,
    recent: List<FoodEntry> = emptyList(),
    onSave: (FoodEntry) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val haptics = rememberHapticEngine()
    var name by remember { mutableStateOf(original?.name.orEmpty()) }
    var grams by remember { mutableDoubleStateOf(original?.grams ?: 0.0) }
    var kcal by remember { mutableDoubleStateOf(original?.nutrients?.kcal ?: 0.0) }
    var protein by remember { mutableDoubleStateOf(original?.nutrients?.protein ?: 0.0) }
    var fat by remember { mutableDoubleStateOf(original?.nutrients?.fat ?: 0.0) }
    var carbs by remember { mutableDoubleStateOf(original?.nutrients?.carbs ?: 0.0) }

    val byMacros = Nutrients.kcal(protein, fat, carbs)
    val effectiveKcal = if (kcal > 0) kcal else byMacros

    fun use(entry: FoodEntry) {
        name = entry.name
        grams = entry.grams ?: 0.0
        kcal = entry.nutrients.kcal
        protein = entry.nutrients.protein
        fat = entry.nutrients.fat
        carbs = entry.nutrients.carbs
        haptics.selection()
    }

    NutritionSheet(
        title = if (original == null) "Добавить еду" else "Запись",
        onDismiss = onDismiss,
        action = "Сохранить",
        actionEnabled = effectiveKcal > 0,
        onAction = { close ->
            if (effectiveKcal > 0) {
                val title = name.trim()
                haptics.success()
                onSave(
                    FoodEntry(
                        id = original?.id ?: newNutritionId(),
                        date = original?.date ?: NutritionFormat.moment(day),
                        name = title.ifEmpty { "Без названия" },
                        grams = grams.takeIf { it > 0 },
                        nutrients = Nutrients(roundHalfUp(effectiveKcal), protein, fat, carbs),
                        fromPhoto = original?.fromPhoto ?: false
                    )
                )
                close()
            }
        }
    ) { close ->
        NutritionCard(spacing = 10.dp) {
            NutritionCaption("Что съел")
            NutritionTextField(
                value = name,
                onValueChange = { name = it.take(120) },
                placeholder = "Например, гречка с курицей",
                keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next)
            )
        }

        NutritionCard {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AmountField("Порция", grams, { grams = it }, Modifier.weight(1f))
                AmountField(
                    "Калории", kcal, { kcal = it }, Modifier.weight(1f),
                    unit = "ккал",
                    placeholder = if (byMacros > 0) NutritionFormat.kcal(byMacros) else "0"
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AmountField("Белки", protein, { protein = it }, Modifier.weight(1f))
                AmountField("Жиры", fat, { fat = it }, Modifier.weight(1f))
                AmountField("Углеводы", carbs, { carbs = it }, Modifier.weight(1f))
            }
            Text(
                NutritionFormat.nbsp("Не вписал калории — они посчитаются по Б/Ж/У: 4 ккал в грамме белка и углеводов, 9 — в грамме жира. Порцию можно не писать."),
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp
            )
        }

        if (original == null && recent.isNotEmpty()) {
            NutritionCard(spacing = 0.dp) {
                NutritionCaption("Недавние", Modifier.padding(bottom = 10.dp))
                recent.forEach { entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .pressable { use(entry) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(entry.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(NutritionFormat.macros(entry.nutrients), color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                        }
                        Text(
                            "${NutritionFormat.kcal(entry.nutrients.kcal)} ккал",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (onDelete != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .pressable {
                        haptics.medium()
                        onDelete()
                        close()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("Удалить запись", color = Theme.record, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// MARK: - Фото еды

/// Фото еды → продукты с граммами и Б/Ж/У. До добавления в дневник всё можно
/// поправить руками или словами: «там 200 г, а не 150».
@Composable
fun FoodPhotoSheet(photo: Uri, onAdd: (List<RecognizedFood>) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHapticEngine()

    var title by remember { mutableStateOf("") }
    var items by remember { mutableStateOf(listOf<RecognizedFood>()) }
    var confidence by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var needsKey by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var showingKey by remember { mutableStateOf(false) }

    val total = items.fold(Nutrients.ZERO) { sum, item -> sum + item.nutrients }
    val canAdd = !working && items.isNotEmpty() && total.kcal > 0
    val sendable = !working && note.isNotBlank()

    fun applyMeal(meal: RecognizedMeal) {
        title = meal.title
        items = meal.items
        confidence = meal.confidence
        comment = meal.comment
        haptics.success()
    }

    fun show(error: Throwable) {
        val known = error as? FoodRecognizer.Failure
        needsKey = known == FoodRecognizer.Failure.BadKey || known == FoodRecognizer.Failure.NoKey
        failure = known?.message ?: (error.message ?: "Что-то пошло не так. Попробуй ещё раз.")
        haptics.error()
    }

    fun recognize() {
        val key = GeminiKeyStore.load(context)
        if (key == null) {
            needsKey = true
            failure = FoodRecognizer.Failure.NoKey.message
            return
        }
        needsKey = false
        failure = null
        working = true
        scope.launch {
            try {
                applyMeal(FoodRecognizer.recognize(context, photo, key))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                show(e)
            } finally {
                working = false
            }
        }
    }

    fun correct() {
        val key = GeminiKeyStore.load(context)
        if (key == null) {
            needsKey = true
            failure = FoodRecognizer.Failure.NoKey.message
            return
        }
        val current = RecognizedMeal(title, items, confidence, comment)
        val text = note
        failure = null
        working = true
        scope.launch {
            try {
                applyMeal(FoodRecognizer.correct(current, text, key))
                note = ""
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                show(e)
            } finally {
                working = false
            }
        }
    }

    LaunchedEffect(Unit) { if (items.isEmpty() && !working) recognize() }

    NutritionSheet(
        title = title.ifEmpty { "Фото еды" },
        onDismiss = onDismiss,
        action = "Добавить",
        actionEnabled = canAdd,
        onAction = { close ->
            if (canAdd) {
                haptics.success()
                onAdd(items)
                close()
            }
        }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Theme.surface)
        ) {
            AsyncImage(model = photo, contentDescription = "Фото еды", contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            if (working) {
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(15.dp))
                    Text("ИИ разбирает фото…", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        failure?.let { message ->
            NutritionCard {
                Text(message, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                if (needsKey) {
                    NutritionButton("Вставить ключ Google", icon = Icons.Filled.Key) { showingKey = true }
                } else {
                    NutritionButton("Попробовать ещё раз", icon = Icons.Filled.Refresh, enabled = !working) { recognize() }
                }
            }
        }

        if (items.isNotEmpty()) {
            NutritionCard(spacing = 8.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    NutritionCaption("Всего на фото")
                    Spacer(Modifier.weight(1f))
                    Text(
                        "точность: $confidence",
                        color = if (confidence == "низкая") Theme.warning else Color.White.copy(alpha = 0.45f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                KcalHeadline(total.kcal, size = 40)
                Text(NutritionFormat.macros(total), color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (comment.isNotEmpty()) {
                    Text(comment, color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                }
            }

            items.forEach { item ->
                androidx.compose.runtime.key(item.id) {
                    RecognizedFoodEditor(
                        item = item,
                        onChange = { changed -> items = items.map { if (it.id == changed.id) changed else it } },
                        onRemove = {
                            haptics.medium()
                            items = items.filterNot { it.id == item.id }
                        }
                    )
                }
            }

            NutritionCard(spacing = 10.dp) {
                NutritionCaption("Поправить словами")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NutritionTextField(
                        value = note,
                        onValueChange = { note = it.take(500) },
                        placeholder = "Например: там 200 г риса, курица без кожи",
                        modifier = Modifier.weight(1f),
                        fontSize = 15,
                        weight = FontWeight.Normal,
                        singleLine = false,
                        keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                    )
                    Box(
                        Modifier
                            .size(48.dp)
                            .pressable(enabled = sendable) { correct() }
                            .clip(CircleShape)
                            .background(if (sendable) Theme.accent else NutritionColors.button),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.ArrowUpward,
                            contentDescription = "Пересчитать",
                            tint = if (sendable) Color.Black else Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(
                    "ИИ оценивает порцию на глаз и может ошибиться на 20–30%. Для метода это не страшно: главное — считать одинаково каждый день.",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 12.sp
                )
            }
        }
    }

    if (showingKey) {
        GeminiKeySheet(onDismiss = {
            showingKey = false
            if (needsKey && GeminiKeyStore.load(context) != null) recognize()
        })
    }
}

/// Один продукт с фото: название, порция и Б/Ж/У. Меняешь порцию —
/// калории и Б/Ж/У пересчитываются в той же пропорции.
@Composable
private fun RecognizedFoodEditor(item: RecognizedFood, onChange: (RecognizedFood) -> Unit, onRemove: () -> Unit) {
    // От чего считать пропорцию: последняя порция, которую правили руками.
    var baseGrams by remember { mutableDoubleStateOf(item.grams ?: 0.0) }
    var base by remember { mutableStateOf(item.nutrients) }
    fun setGrams(grams: Double) {
        var next = item.copy(grams = grams.takeIf { it > 0 })
        if (baseGrams > 0 && grams > 0) next = next.copy(nutrients = base.scaled(grams / baseGrams))
        onChange(next)
    }

    /// Правка калорий или Б/Ж/У руками — новая точка отсчёта для пропорции.
    fun setNutrients(nutrients: Nutrients) {
        if (nutrients == item.nutrients) return
        base = nutrients
        baseGrams = item.grams ?: 0.0
        onChange(item.copy(nutrients = nutrients))
    }

    NutritionCard(padding = 16.dp, spacing = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NutritionTextField(
                value = item.name,
                onValueChange = { onChange(item.copy(name = it.take(120))) },
                placeholder = "Продукт",
                modifier = Modifier.weight(1f),
                fontSize = 16,
                weight = FontWeight.Bold,
                keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
            Box(
                Modifier.size(36.dp).pressable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Убрать продукт", tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AmountField("Порция", item.grams ?: 0.0, ::setGrams, Modifier.weight(1f))
            AmountField("Калории", item.nutrients.kcal, { setNutrients(item.nutrients.copy(kcal = it)) }, Modifier.weight(1f), unit = "ккал")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AmountField("Белки", item.nutrients.protein, { setNutrients(item.nutrients.copy(protein = it)) }, Modifier.weight(1f))
            AmountField("Жиры", item.nutrients.fat, { setNutrients(item.nutrients.copy(fat = it)) }, Modifier.weight(1f))
            AmountField("Углеводы", item.nutrients.carbs, { setNutrients(item.nutrients.copy(carbs = it)) }, Modifier.weight(1f))
        }
    }
}

// MARK: - Как это работает

/// Метод Лимара простыми словами: почему формулы не работают и как найти
/// свою норму по записям. Отдельно — что приложение добавило от себя.
@Composable
fun NutritionGuideSheet(onDismiss: () -> Unit) {
    NutritionSheet(
        title = "Как это работает",
        onDismiss = onDismiss,
        leading = null,
        action = "Готово",
        onAction = { close -> close() }
    ) {
        GuideSection(
            "Почему формулы не работают",
            listOf(
                "«Белок — вес × 2, жиры — вес × 0,8, калории — вес × 35, остальное — углеводы». Такие формулы гуляют по интернету, но у каждого своя активность и свой обмен веществ.",
                "У двух людей с одинаковым весом расход за день может отличаться на сотни калорий: работа, шаги, обмен веществ. Формула этого не видит — а твои записи видят."
            )
        )
        NutritionCard(spacing = 14.dp) {
            NutritionCaption("Метод Лимара", color = Theme.accent)
            GuideStep(1, "Неделю ничего не меняй", "Ешь как обычно — ел пиццу по вторникам, ешь и дальше. Каждый день записывай, что съел, и вес утром натощак.")
            GuideStep(2, "Смотри среднее", "Средние калории за неделю при весе, который стоит на месте, — это твоя поддержка.")
            GuideStep(3, "Набор: +30 г углеводов", "Каждую неделю добавляй к норме 30 г углеводов и смотри, как меняется вес. Пошёл вверх — держи норму.")
            GuideStep(4, "Сушка: минус жиры", "Каждую неделю убирай 13 г жиров — это те же 120 ккал — и смотри на вес. Пошёл вниз — держи норму.")
        }
        GuideSection(
            "Как приложение это проверяет",
            listOf(
                "Вес сравнивается средним за неделю, а не по дням: за день он может прыгать на килограмм — вода, соль, еда в желудке.",
                "Неделя считается, если записаны еда и вес хотя бы 5 дней из 7. Иначе средние врут.",
                "Вес «стоит», если средний сдвинулся меньше чем на 0,2 кг за неделю.",
                "Если за неделю наблюдения вес заметно уехал, приложение скажет об этом и предложит понаблюдать ещё неделю.",
                "На сушке предупредит, если жиры опустятся ниже 0,6 г на кило веса.",
                "Норма меняется только по твоему нажатию — приложение лишь подсказывает."
            )
        )
        GuideSection(
            "Фото еды",
            listOf(
                "ИИ Google раскладывает тарелку на продукты и оценивает граммы на глаз — ошибка бывает 20–30%. Этикетку он читает точнее.",
                "Для метода точность не главное: если каждый день считать одинаково, среднее за неделю всё равно найдёт поддержку."
            )
        )
        Text(
            "Метод — Лимар, @freedom_limar. Приложение просто ведёт записи и считает за тебя.",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun GuideStep(number: Int, title: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Theme.accent), contentAlignment = Alignment.Center) {
            Text("$number", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(NutritionFormat.nbsp(text), color = Color.White.copy(alpha = 0.65f), fontSize = 14.sp)
        }
    }
}

@Composable
private fun GuideSection(title: String, lines: List<String>) {
    NutritionCard(spacing = 10.dp) {
        NutritionCaption(title)
        lines.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.padding(top = 8.dp).size(5.dp).clip(CircleShape).background(Theme.accent))
                Text(NutritionFormat.nbsp(line), color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}

// MARK: - Ключ Google

/// Свой ключ Google AI: где взять, проверить и сохранить. Хранится только
/// на этом телефоне и уходит только в Google.
@Composable
fun GeminiKeySheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHapticEngine()
    val uriHandler = LocalUriHandler.current

    var key by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(GeminiKeyStore.load(context) != null) }
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }
    val trimmed = key.trim()

    fun check() {
        if (trimmed.isEmpty() || checking) return
        val candidate = trimmed
        checking = true
        scope.launch {
            try {
                FoodRecognizer.checkKey(candidate)
                GeminiKeyStore.save(context, candidate)
                saved = true
                success = true
                key = ""
                message = "Ключ работает и сохранён. Можно фотографировать еду."
                haptics.success()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                success = false
                message = (e as? FoodRecognizer.Failure)?.message ?: (e.message ?: "Не получилось проверить ключ.")
                haptics.error()
            } finally {
                checking = false
            }
        }
    }

    NutritionSheet(
        title = "Ключ Google",
        onDismiss = onDismiss,
        leading = null,
        action = "Готово",
        onAction = { close -> close() }
    ) {
        NutritionCard {
            NutritionCaption("Ключ для фото еды", color = Theme.accent)
            Text(
                "Фото разбирает ИИ Google Gemini по твоему ключу. Ключ бесплатный и хранится только на этом телефоне.",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 14.sp
            )
            KeyInstruction(1, "Открой Google AI Studio и войди в Google-аккаунт.")
            KeyInstruction(2, "Нажми «Create API key» и скопируй ключ.")
            KeyInstruction(3, "Вставь его ниже и нажми «Проверить и сохранить».")
            Row(
                Modifier.pressable { runCatching { uriHandler.openUri("https://aistudio.google.com/apikey") } }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = Theme.accent, modifier = Modifier.size(18.dp))
                Text("Открыть Google AI Studio", color = Theme.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        NutritionCard {
            NutritionCaption(if (saved) "Ключ сохранён" else "Твой ключ")
            NutritionTextField(
                value = key,
                onValueChange = { key = it.trim().take(200) },
                placeholder = if (saved) "Вставь новый, чтобы заменить" else "AIza…",
                fontSize = 16,
                weight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                keyboard = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Done
                ),
                visualTransformation = PasswordVisualTransformation()
            )
            message?.let {
                Text(it, color = if (success) Theme.success else Theme.warning, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            NutritionButton(
                if (checking) "Проверяю…" else "Проверить и сохранить",
                icon = Icons.Filled.Verified,
                enabled = !checking && trimmed.isNotEmpty()
            ) { check() }
            if (saved) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .pressable {
                            GeminiKeyStore.save(context, null)
                            saved = false
                            success = false
                            message = "Ключ удалён с телефона."
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Удалить ключ", color = Theme.record, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Text("Если Google пишет, что страна не поддерживается, — включи VPN.", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun KeyInstruction(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("$number.", color = Theme.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(text, color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, modifier = Modifier.weight(1f))
    }
}
