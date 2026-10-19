package com.texasprogram.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.service.AppUpdater
import com.texasprogram.app.service.AppUpdater.State
import kotlinx.coroutines.launch

/// Плашка сверху экрана: вышла новая версия. «Обновить» — скачать с полоской
/// прогресса и открыть системный установщик; «×» — напомнить при следующем запуске.
@Composable
fun AppUpdateBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state = AppUpdater.state
    val visible = state.release != null && !(AppUpdater.dismissed && state is State.Available)
    // Пока плашка уезжает, показываем последнее, что на ней было.
    val shown = remember { arrayOfNulls<State>(1) }
    if (state.release != null) shown[0] = state

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(Motion.sheet()) { -it } + fadeIn(),
        exit = slideOutVertically(Motion.sheet()) { -it } + fadeOut(),
        modifier = modifier
    ) {
        val current = shown[0] ?: return@AnimatedVisibility
        val release = current.release ?: return@AnimatedVisibility
        val progress = (current as? State.Downloading)?.progress
        val bar by animateFloatAsState(progress?.coerceAtLeast(0f) ?: 0f, Motion.snappy(), label = "updateProgress")
        val accent = Theme.accent
        val shape = RoundedCornerShape(22.dp)

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .chatGlass(shape)
                // Полоска загрузки по нижнему краю плашки.
                .drawBehind {
                    if (progress == null) return@drawBehind
                    val height = 3.dp.toPx()
                    drawRoundRect(
                        accent,
                        topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - height),
                        size = Size(size.width * bar, height),
                        cornerRadius = CornerRadius(height / 2)
                    )
                }
                .padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(40.dp).background(accent.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when (current) {
                        is State.Downloading -> "Загружаю обновление"
                        is State.NeedsPermission -> "Разреши установку"
                        is State.Ready -> "Обновление скачано"
                        is State.Failed -> "Не получилось"
                        else -> "Вышла новая версия"
                    },
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    when (current) {
                        is State.Downloading ->
                            if (current.progress < 0f) "Секунду…" else "${(current.progress * 100).toInt()}%"
                        is State.NeedsPermission -> "Один раз: включи «Разрешить из этого источника» и вернись"
                        is State.Ready -> "Нажми «Обновить» в окне Android"
                        is State.Failed -> current.message
                        else -> release.notes.ifBlank { "STRAIN ${release.versionName}" }
                    },
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (current !is State.Downloading) {
                if (current is State.Available) {
                    Box(
                        Modifier.size(36.dp).pressable { AppUpdater.dismiss() }.clip(CircleShape).background(Color.White.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Позже", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Box(
                    Modifier
                        .height(36.dp)
                        .pressable {
                            if (current is State.NeedsPermission) AppUpdater.openInstallPermission(context)
                            else AppUpdater.update(context)
                        }
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(horizontal = 14.dp),
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
    }
}

/// Карточка в настройках: какая версия стоит и есть ли новее.
@Composable
fun AppVersionCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    /// Итог ручной проверки: `true` — новее нет, `false` — не удалось проверить.
    var checked by remember { mutableStateOf<Boolean?>(null) }
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
                Text(
                    release.notes.ifBlank { "Вышла версия ${release.versionName}" },
                    color = Theme.textTertiary,
                    fontSize = 12.sp
                )
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
