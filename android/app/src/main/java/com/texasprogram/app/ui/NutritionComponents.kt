package com.texasprogram.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.texasprogram.app.model.Nutrients
import com.texasprogram.app.model.formatWeight
import com.texasprogram.app.model.roundHalfUp
import com.texasprogram.app.service.RuDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

// MARK: - Формат

/// Числа и даты «Питания» — как в iOS-версии: дробь через запятую, время по-русски.
object NutritionFormat {
    fun kcal(value: Double): String = roundHalfUp(value).toLong().toString()

    fun decimal(value: Double): String = formatWeight(value).replace('.', ',')

    /// Граммы: от десяти — целыми, меньше — с десятыми.
    fun grams(value: Double): String =
        if (value >= 10) roundHalfUp(value).toLong().toString() else decimal(roundHalfUp(value * 10) / 10)

    fun macros(value: Nutrients): String = "Б ${grams(value.protein)} · Ж ${grams(value.fat)} · У ${grams(value.carbs)}"

    /// «+0,3 кг», «−0,2 кг», «0 кг».
    fun change(value: Double): String {
        val rounded = roundHalfUp(value * 10) / 10
        val sign = if (rounded > 0) "+" else if (rounded < 0) "−" else ""
        return sign + decimal(abs(rounded)) + " кг"
    }

    fun kilograms(value: Double): String = decimal(value) + " кг"

    /// Число и единица — на одной строке: «30 г» не разъезжается на «30» и «г».
    fun nbsp(text: String): String = unitGap.replace(text, "$1\u00A0")

    private val unitGap = Regex("(\\d) (?=(?:г|ккал|кг|дн\\.)(?![а-яё]))")

    /// Момент записи для выбранного дня: сегодня — сейчас, прошлый день — полдень.
    fun moment(day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Instant =
        if (day == LocalDate.now(zone)) Instant.now().truncatedTo(ChronoUnit.SECONDS)
        else day.atTime(12, 0).atZone(zone).toInstant()

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /// «14:05» — в 24 часа, как на iPhone.
    fun time(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String = timeFormatter.format(instant.atZone(zone))

    fun dayTitle(day: LocalDate, today: LocalDate = LocalDate.now()): String = when (day) {
        today -> "Сегодня"
        today.minusDays(1) -> "Вчера"
        else -> RuDate.dayMonth(day)
    }
}

// MARK: - Цвета

/// Оттенки серого из iOS-версии: `Color(white: 0.12)` и соседние.
internal object NutritionColors {
    val tile = Color(0xFF1F1F1F)
    val field = Color(0xFF212121)
    val fieldFocused = Color(0xFF2B2B2B)
    val chip = Color(0xFF242424)
    val button = Color(0xFF292929)
    val dot = Color(0xFF242424)
    val bar = Color(0xFF292929)
}

// MARK: - Карточка и подписи

/// Графитовая карточка питания — как карточки «Замеров веса».
@Composable
fun NutritionCard(
    modifier: Modifier = Modifier,
    padding: Dp = 18.dp,
    spacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    CardView(modifier, padding = padding, spacing = spacing, corner = 22.dp, content = content)
}

/// Мелкая заглавная подпись над блоком.
@Composable
fun NutritionCaption(text: String, modifier: Modifier = Modifier, color: Color = Color.White.copy(alpha = 0.45f)) {
    Text(text.uppercase(), color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, modifier = modifier)
}

/// Основная кнопка: оранжевая с чёрным текстом; вторая — графитовая.
@Composable
fun NutritionButton(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val tint = if (primary) Color.Black else Color.White
    Row(
        modifier
            .fillMaxWidth()
            .height(50.dp)
            .pressable(enabled = enabled, onClick = onClick)
            .clip(RoundedCornerShape(14.dp))
            .background(if (primary) Theme.accent else NutritionColors.button)
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
            Box(Modifier.width(8.dp))
        }
        Text(title, color = tint, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/// Крупные калории: «2480 ккал».
@Composable
fun KcalHeadline(kcal: Double, size: Int = 44) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            NutritionFormat.kcal(kcal),
            color = Color.White,
            fontSize = size.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            modifier = Modifier.alignByBaseline()
        )
        Text(
            "ккал",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = (size * 0.41f).sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.alignByBaseline()
        )
    }
}

// MARK: - Поле числа

/// Число в поле ввода: запятая и точка — одно и то же, пустое поле — ноль.
@Composable
fun AmountField(
    title: String,
    value: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    unit: String = "г",
    placeholder: String = "0"
) {
    var text by remember { mutableStateOf(formatAmount(value)) }
    var focused by remember { mutableStateOf(false) }
    // Значение поменяли снаружи — например, после пересчёта ИИ или порции.
    LaunchedEffect(value) {
        if (parseAmount(text) != value) text = formatAmount(value)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            title.uppercase(),
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            maxLines = 1
        )
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (focused) NutritionColors.fieldFocused else NutritionColors.field)
                .border(1.5.dp, if (focused) Theme.accent else Color.Transparent, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (text.isEmpty()) {
                    Text(placeholder, color = Color.White.copy(alpha = 0.3f), fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                BasicTextField(
                    value = text,
                    onValueChange = { input ->
                        val cleaned = input.filter { it.isDigit() || it == ',' || it == '.' }.take(8)
                        text = cleaned
                        val parsed = parseAmount(cleaned)
                        if (parsed != value) onValueChange(parsed)
                    },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold),
                    cursorBrush = SolidColor(Theme.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
                )
            }
            Text(unit, color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

fun parseAmount(text: String): Double {
    val value = text.replace(',', '.').trim().toDoubleOrNull() ?: 0.0
    return if (value.isFinite()) maxOf(0.0, value) else 0.0
}

fun formatAmount(value: Double): String = if (value == 0.0) "" else NutritionFormat.decimal(roundHalfUp(value * 10) / 10)

/// Однострочное поле на графитовой подложке: название еды, вес, поправка словами.
@Composable
fun NutritionTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    fontSize: Int = 17,
    weight: FontWeight = FontWeight.SemiBold,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .fillMaxWidth()
            .then(if (singleLine) Modifier.height(50.dp) else Modifier)
            .clip(RoundedCornerShape(14.dp))
            .background(if (focused) NutritionColors.fieldFocused else NutritionColors.field)
            .border(1.5.dp, if (focused) Theme.accent else Color.Transparent, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = if (singleLine) 0.dp else 13.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = Color.White.copy(alpha = 0.3f), fontSize = fontSize.sp, fontWeight = weight, fontFamily = fontFamily)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            maxLines = if (singleLine) 1 else 4,
            textStyle = TextStyle(color = Color.White, fontSize = fontSize.sp, fontWeight = weight, fontFamily = fontFamily),
            cursorBrush = SolidColor(Theme.accent),
            keyboardOptions = keyboard,
            visualTransformation = visualTransformation,
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
        )
    }
}

// MARK: - Лист поверх экрана

/// Снимки экранов рисуют лист прямо на месте: отдельное окно диалога на них не видно.
val LocalNutritionSheetInline = staticCompositionLocalOf { false }

/// Лист на весь экран, как `.sheet` с навигацией на iPhone: выезжает снизу,
/// слева «Отмена», по центру заголовок, справа главное действие.
@Composable
fun NutritionSheet(
    title: String,
    onDismiss: () -> Unit,
    leading: String? = "Отмена",
    action: String? = null,
    actionEnabled: Boolean = true,
    onAction: (close: () -> Unit) -> Unit = {},
    /// Без своей прокрутки: экран сам раскладывает список и поле ввода, как чат.
    scrollable: Boolean = true,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit
) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    val close = { visible.targetState = false }
    // Уехал вниз — только теперь убираем окно, иначе лист исчез бы рывком.
    LaunchedEffect(visible.currentState, visible.isIdle) {
        if (visible.isIdle && !visible.currentState && !visible.targetState) onDismiss()
    }
    val frame: @Composable () -> Unit = {
        NutritionSheetFrame(title, leading, action, actionEnabled, close, { onAction(close) }, scrollable) { content(close) }
    }
    if (LocalNutritionSheetInline.current) {
        frame()
        return
    }
    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        AnimatedVisibility(visibleState = visible, enter = Motion.sheetEnter(), exit = Motion.sheetExit()) {
            frame()
        }
    }
}

@Composable
private fun NutritionSheetFrame(
    title: String,
    leading: String?,
    action: String?,
    actionEnabled: Boolean,
    close: () -> Unit,
    onAction: () -> Unit,
    scrollable: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .imePadding()
    ) {
        Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp)) {
            if (leading != null) {
                Text(
                    leading,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 17.sp,
                    modifier = Modifier.align(Alignment.CenterStart).pressable(onClick = close).padding(vertical = 8.dp)
                )
            }
            Text(
                title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 96.dp)
            )
            if (action != null) {
                Text(
                    action,
                    color = if (actionEnabled) Theme.accent else Color.White.copy(alpha = 0.3f),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .pressable(enabled = actionEnabled, onClick = onAction)
                        .padding(vertical = 8.dp)
                )
            }
        }
        if (!scrollable) {
            Column(Modifier.fillMaxWidth().weight(1f).navigationBarsPadding()) { content() }
            return@Column
        }
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 32.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            content()
        }
    }
}
