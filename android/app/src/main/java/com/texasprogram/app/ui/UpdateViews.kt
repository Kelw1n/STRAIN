package com.texasprogram.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.service.AppUpdater
import com.texasprogram.app.service.AppUpdater.State
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/// Плашка сверху экрана: вышла новая версия. «Обновить» — скачать с полоской
/// прогресса и открыть системный установщик; «Что нового» со стрелкой раскрывает
/// изменения всех версий новее установленной; «×» — напомнить при следующем запуске.
@Composable
fun AppUpdateBanner(modifier: Modifier = Modifier, state: State = AppUpdater.state) {
    val context = LocalContext.current
    val visible = state.release != null && !(AppUpdater.dismissed && state is State.Available)
    // Пока плашка уезжает, показываем последнее, что на ней было.
    val shown = remember { arrayOfNulls<State>(1) }
    if (state.release != null) shown[0] = state
    val whatsNew = AppUpdater.whatsNew

    // Только что обновились — сначала расскажем, что нового, если новее ничего нет.
    WhatsNewBanner(visible = !visible && whatsNew.isNotEmpty(), modifier = modifier)

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(Motion.sheet()) { -it } + fadeIn(),
        exit = slideOutVertically(Motion.sheet()) { -it } + fadeOut(),
        modifier = modifier
    ) {
        val current = shown[0] ?: return@AnimatedVisibility
        val release = current.release ?: return@AnimatedVisibility
        val changes = AppUpdater.pending.ifEmpty { release.changes(since = AppUpdater.installedVersionCode(context)) }
        val progress = (current as? State.Downloading)?.progress
        val bar by animateFloatAsState(progress?.coerceAtLeast(0f) ?: 0f, Motion.snappy(), label = "updateProgress")
        var expanded by rememberSaveable { mutableStateOf(false) }
        val accent = Theme.accent
        val shape = RoundedCornerShape(22.dp)

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .bannerSurface(shape)
                // Полоска загрузки по нижнему краю плашки.
                .drawBehind {
                    if (progress == null) return@drawBehind
                    val height = 3.dp.toPx()
                    drawRoundRect(
                        accent,
                        topLeft = Offset(0f, size.height - height),
                        size = Size(size.width * bar, height),
                        cornerRadius = CornerRadius(height / 2)
                    )
                }
                .animateContentSize(Motion.sheet())
                .padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BannerIcon(Icons.Filled.SystemUpdate)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when (current) {
                            is State.Downloading -> "Загружаю обновление"
                            is State.NeedsPermission -> "Разреши установку"
                            is State.Ready -> "Обновление скачано"
                            is State.Failed -> "Не получилось"
                            else -> "Вышло обновление ${release.versionName}"
                        },
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        when (current) {
                            is State.Downloading ->
                                if (current.progress < 0f) "Секунду…" else "${(current.progress * 100).toInt()}%"
                            is State.NeedsPermission -> "Один раз: включи «Разрешить из этого источника» и вернись"
                            is State.Ready -> "Нажми «Обновить» в окне Android"
                            is State.Failed -> current.message
                            else -> summary(changes, release.notes)
                        },
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (current is State.Available) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(32.dp).pressable { AppUpdater.dismiss() }.clip(CircleShape).background(Color.White.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Позже", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (changes.isNotEmpty()) {
                    ExpandToggle(expanded, Modifier.weight(1f)) { expanded = !expanded }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (current !is State.Downloading) {
                    Box(
                        Modifier
                            .height(36.dp)
                            .pressable {
                                if (current is State.NeedsPermission) AppUpdater.openInstallPermission(context)
                                else AppUpdater.update(context)
                            }
                            .clip(CircleShape)
                            .background(Color.White)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            when (current) {
                                is State.NeedsPermission -> "Настройки"
                                is State.Failed -> "Ещё раз"
                                is State.Ready -> "Установить"
                                else -> "Обновить"
                            },
                            color = Color.Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            AnimatedVisibility(visible = expanded, enter = expandVertically(Motion.sheet()) + fadeIn(), exit = shrinkVertically(Motion.sheet()) + fadeOut()) {
                ChangesList(changes)
            }
        }
    }
}

/// После обновления, один раз: «STRAIN обновлён до …» и что нового.
@Composable
private fun WhatsNewBanner(visible: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val changes = AppUpdater.whatsNew
    // Пока плашка уезжает после «Понятно», показываем то, что на ней было.
    val shown = remember { arrayOfNulls<List<AppUpdater.Change>>(1) }
    if (changes.isNotEmpty()) shown[0] = changes
    var expanded by rememberSaveable { mutableStateOf(false) }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(Motion.sheet()) { -it } + fadeIn(),
        exit = slideOutVertically(Motion.sheet()) { -it } + fadeOut(),
        modifier = modifier
    ) {
        val list = shown[0] ?: return@AnimatedVisibility
        val newest = list.firstOrNull() ?: return@AnimatedVisibility
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .bannerSurface(RoundedCornerShape(22.dp))
                .animateContentSize(Motion.sheet())
                .padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BannerIcon(Icons.Filled.AutoAwesome)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("STRAIN обновлён до ${newest.versionName}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(summary(list, newest.notes), color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExpandToggle(expanded, Modifier.weight(1f)) { expanded = !expanded }
                Box(
                    Modifier
                        .height(36.dp)
                        .pressable { AppUpdater.dismissWhatsNew(context) }
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Понятно", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            AnimatedVisibility(visible = expanded, enter = expandVertically(Motion.sheet()) + fadeIn(), exit = shrinkVertically(Motion.sheet()) + fadeOut()) {
                ChangesList(list)
            }
        }
    }
}

/// «Что нового» со стрелкой вниз; раскрыто — стрелка смотрит вверх.
@Composable
private fun ExpandToggle(expanded: Boolean, modifier: Modifier, onToggle: () -> Unit) {
    val turn by animateFloatAsState(if (expanded) 180f else 0f, Motion.snappy(), label = "changesArrow")
    Row(
        modifier
            .height(36.dp)
            .clip(CircleShape)
            .pressable(onClick = onToggle)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(if (expanded) "Скрыть" else "Что нового", color = Theme.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = if (expanded) "Скрыть изменения" else "Показать изменения",
            tint = Theme.accent,
            modifier = Modifier.size(22.dp).rotate(turn)
        )
    }
}

@Composable
private fun BannerIcon(icon: ImageVector) {
    Box(Modifier.size(40.dp).background(Theme.accent.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = Theme.accent, modifier = Modifier.size(20.dp))
    }
}

private val changeDate = DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru"))

/// Изменения по версиям, новые сверху: номер, дата и пункты.
@Composable
fun ChangesList(changes: List<AppUpdater.Change>, modifier: Modifier = Modifier, maxHeight: Int = 280) {
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        changes.forEach { change ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val date = change.date?.let { runCatching { LocalDate.parse(it).format(changeDate) }.getOrNull() }
                Text(
                    listOfNotNull(change.versionName.ifBlank { null }, date).joinToString(" · "),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                val points = change.points
                if (points.isEmpty()) {
                    Text("Исправления и улучшения", color = Color.White, fontSize = 14.sp, lineHeight = 19.sp)
                }
                points.forEach { point ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("•", color = Theme.accent, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold)
                        Text(point, color = Color.White, fontSize = 14.sp, lineHeight = 19.sp)
                    }
                }
            }
        }
    }
}

/// Строка под заголовком: первая новость, а если версий несколько — сколько их.
private fun summary(changes: List<AppUpdater.Change>, fallback: String): String {
    val first = changes.firstOrNull()?.points?.firstOrNull() ?: fallback.lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    return when {
        changes.size > 1 -> "${changes.size} ${com.texasprogram.app.service.RuPlural.word(changes.size, "обновление", "обновления", "обновлений")} с твоей версии · $first"
        first.isNotEmpty() -> first
        else -> "Исправления и улучшения"
    }
}

/// Карточка в настройках: какая версия стоит, есть ли новее и что нового.
@Composable
fun AppVersionCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    /// Итог ручной проверки: `true` — новее нет, `false` — не удалось проверить.
    var checked by remember { mutableStateOf<Boolean?>(null) }
    var expanded by remember { mutableStateOf(false) }
    val state = AppUpdater.state
    val release = state.release

    CardView(modifier) {
        SectionLabel("Версия")
        Text(
            "STRAIN ${AppUpdater.installedVersionName(context)} · сборка ${AppUpdater.installedVersionCode(context)}",
            color = Theme.textSecondary,
            fontSize = 13.sp
        )
        when {
            release != null && state is State.Downloading -> Text(
                if (state.progress < 0f) "Загружаю обновление…" else "Загружаю обновление: ${(state.progress * 100).toInt()}%",
                color = Theme.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            release != null -> {
                val changes = AppUpdater.pending.ifEmpty { release.changes(since = AppUpdater.installedVersionCode(context)) }
                Text(summary(changes, release.notes), color = Theme.textTertiary, fontSize = 12.sp)
                if (changes.isNotEmpty()) {
                    ExpandToggle(expanded, Modifier) { expanded = !expanded }
                    AnimatedVisibility(visible = expanded) { ChangesList(changes) }
                }
                SecondaryButton(
                    if (state is State.NeedsPermission) "Разрешить установку" else "Обновить до ${release.versionName}",
                    icon = Icons.Filled.SystemUpdate
                ) {
                    if (state is State.NeedsPermission) AppUpdater.openInstallPermission(context) else AppUpdater.update(context)
                }
                if (state is State.Failed) Text(state.message, color = Theme.warning, fontSize = 12.sp)
            }
            else -> {
                SecondaryButton(if (checking) "Проверяю…" else "Проверить обновления", icon = Icons.Filled.SystemUpdate) {
                    if (checking) return@SecondaryButton
                    checking = true
                    checked = null
                    scope.launch {
                        val found = AppUpdater.check(context, force = true)
                        checked = if (found == null) false else !found
                        checking = false
                    }
                }
                when (checked) {
                    true -> Text("Стоит последняя версия", color = Theme.success, fontSize = 12.sp)
                    false -> Text("Не удалось проверить — нет связи", color = Theme.warning, fontSize = 12.sp)
                    null -> Unit
                }
            }
        }
    }
}
