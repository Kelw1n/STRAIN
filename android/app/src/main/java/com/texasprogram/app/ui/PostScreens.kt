package com.texasprogram.app.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.tween
import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.SocialComment
import com.texasprogram.app.model.SocialFormat
import com.texasprogram.app.model.SocialChatWorkout
import com.texasprogram.app.model.SocialPost
import com.texasprogram.app.model.SocialWorkout
import com.texasprogram.app.service.RuPlural
import com.texasprogram.app.service.SocialException
import com.texasprogram.app.service.SocialService
import kotlinx.coroutines.launch

// MARK: - Пост в ленте

/// Пост как в REBRN: кто и когда, название, заметка, время, объём и плотность,
/// карусель «фото → тренировка» и под ней лайк с комментариями.
@Composable
fun PostCard(
    post: SocialPost,
    onOpen: () -> Unit,
    onOpenAuthor: () -> Unit,
    onChange: (SocialPost?) -> Unit,
    /// Упражнения с весами и под фото — в списке тренировок профиля.
    showsWorkout: Boolean = false
) {
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PostHeader(post, onOpenAuthor, onDelete = { confirmDelete = true })
        Column(Modifier.fillMaxWidth().pressable(onClick = onOpen), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PostTitleBlock(post)
            PostStatsRow(post)
        }
        // Фото — без сжатия при нажатии: оно дёргало карточку на каждом свайпе.
        PostMedia(post, Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onOpen))
        if (post.photos.isNotEmpty()) {
            if (showsWorkout) {
                Box(Modifier.pressable(onClick = onOpen)) { WorkoutSummary(post.workout, limit = 4) }
            }
        }
        PostActions(post, onComments = onOpen, onChange = { onChange(it) })
        error?.let { Text(it, color = Theme.warning, fontSize = 12.sp) }
    }

    if (confirmDelete) {
        AlertDialog(
            modifier = Modifier.popIn(),
            onDismissRequest = { confirmDelete = false },
            containerColor = Theme.dialog,
            title = { Text("Удалить тренировку из ленты?", color = Theme.textPrimary, fontWeight = FontWeight.Bold) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        try {
                            SocialService.deletePost(post.id)
                            onChange(null)
                        } catch (e: SocialException) {
                            error = e.message
                        }
                    }
                }) { Text("Удалить", color = Theme.record) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена", color = Theme.textSecondary) } }
        )
    }
}

@Composable
fun PostHeader(post: SocialPost, onOpenAuthor: () -> Unit, onDelete: (() -> Unit)?) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    val mine = SocialService.me?.id == post.author.id
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).pressable(onClick = onOpenAuthor),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Avatar(post.author.avatar, post.author.username, 46.dp)
                // Тренировались вместе — аватар напарника рядом.
                post.partner?.let { partner ->
                    Box(Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 4.dp).size(26.dp).clip(CircleShape).background(Color.Black).padding(2.dp)) {
                        Avatar(partner.avatar, partner.username, 22.dp)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    post.partner?.let { "@${post.author.username} и @${it.username}" } ?: "@${post.author.username}",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(SocialFormat.ago(post.instant), color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
            }
        }
        Box {
            Box(
                Modifier.size(38.dp).pressable { menu = true }.clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.MoreHoriz, contentDescription = "Действия с постом", tint = Color.White.copy(alpha = 0.55f))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (SocialService.isSignedIn) {
                    DropdownMenuItem(
                        text = { Text("Отправить другу") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                        onClick = {
                            menu = false
                            sharing = true
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Поделиться текстом") },
                    leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                    onClick = {
                        menu = false
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText(post))
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                    }
                )
                if (mine && onDelete != null) {
                    DropdownMenuItem(
                        text = { Text("Удалить пост", color = Theme.record) },
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
    if (sharing) {
        ChatTargetSheet("Отправить другу", onDismiss = { sharing = false }) { friend ->
            SocialService.send(SocialChatWorkout.from(post), friend.username)
        }
    }
}

private fun shareText(post: SocialPost): String = buildString {
    appendLine("@${post.author.username} — ${post.title}")
    appendLine("Время ${SocialFormat.duration(post.durationSeconds)} · объём ${SocialFormat.grouped(post.volumeKg)} кг")
    post.workout.exercises.forEach { exercise ->
        exercise.best?.let { appendLine("${exercise.name}: ${exercise.sets.size} × ${it.text}") }
    }
    append("STRAIN")
}

@Composable
fun PostTitleBlock(post: SocialPost) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(post.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
        if (post.description.isNotBlank()) {
            Text(post.description, color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp)
        }
    }
}

/// Промежутки сжимаются от 26 до 10 dp: на телефоне шириной 360 dp четыре
/// показателя с крупными числами иначе не влезали и последний обрезался.
@Composable
fun PostStatsRow(post: SocialPost) {
    Layout(content = {
        PostStat(SocialFormat.duration(post.durationSeconds), null, "Время")
        PostStat(SocialFormat.grouped(post.volumeKg), "кг", "Объём")
        if (post.density > 0) PostStat(SocialFormat.grouped(post.density), "кг/мин", "Плотность")
        post.effort?.let { PostStat("$it", "/10", "Усилие") }
    }) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints()) }
        val total = placeables.sumOf { it.width }
        val gaps = (placeables.size - 1).coerceAtLeast(1)
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else total + gaps * 26.dp.roundToPx()
        val spacing = ((width - total) / gaps).coerceIn(10.dp.roundToPx(), 26.dp.roundToPx())
        layout(width, placeables.maxOfOrNull { it.height } ?: 0) {
            var x = 0
            placeables.forEach { placeable ->
                placeable.placeRelative(x, 0)
                x += placeable.width + spacing
            }
        }
    }
}

@Composable
private fun PostStat(value: String, unit: String?, title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, softWrap = false)
            if (unit != null) Text(unit, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 2.dp))
        }
        Text(title, color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
    }
}

/// Упражнения коротко: «Жим лёжа · 3 подхода · 80 кг × 5».
@Composable
fun WorkoutSummary(workout: SocialWorkout, limit: Int = Int.MAX_VALUE) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF171717))
    ) {
        if (workout.exercises.isEmpty()) {
            Text("Без записанных подходов", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp, modifier = Modifier.padding(16.dp))
        }
        val shown = workout.exercises.take(limit)
        shown.forEachIndexed { index, exercise ->
            val tint = if (exercise.hasRecord) Theme.warning else Theme.accent
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (exercise.hasRecord) Icons.Filled.EmojiEvents else Icons.Filled.FitnessCenter,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(exercise.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${exercise.sets.size} ${RuPlural.word(exercise.sets.size, "подход", "подхода", "подходов")}",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 12.sp
                    )
                }
                exercise.best?.let { Text(it.text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
            }
            if (index < shown.size - 1) {
                HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(start = 54.dp))
            }
        }
        if (workout.exercises.size > limit) {
            Text(
                "и ещё ${workout.exercises.size - limit}",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
            )
        }
    }
}

@Composable
fun PostActions(post: SocialPost, onComments: () -> Unit, onChange: (SocialPost) -> Unit) {
    val scope = rememberCoroutineScope()
    val haptics = rememberHapticEngine()
    var busy by remember { mutableStateOf(false) }
    // Сердце при лайке сжимается и выпрыгивает с отскоком. Масштаб читается
    // только в отрисовке — сам пост при этом не перекомпонуется.
    val heart = remember { Animatable(1f) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(
            Modifier.pressable {
                if (!SocialService.isSignedIn || busy) return@pressable
                haptics.light()
                // Сразу показываем лайк, не дожидаясь сервера; ошибка вернёт как было.
                val optimistic = post.copy(liked = !post.liked, likes = post.likes + if (post.liked) -1 else 1)
                if (optimistic.liked) {
                    scope.launch {
                        heart.snapTo(0.55f)
                        heart.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 520f))
                    }
                }
                onChange(optimistic)
                busy = true
                scope.launch {
                    try {
                        onChange(SocialService.setLiked(post.id, optimistic.liked))
                    } catch (e: SocialException) {
                        onChange(post)
                    } finally {
                        busy = false
                    }
                }
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                if (post.liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (post.liked) "Убрать лайк" else "Лайк",
                tint = if (post.liked) Theme.record else Color.White,
                modifier = Modifier.size(26.dp).graphicsLayer { scaleX = heart.value; scaleY = heart.value }
            )
            if (post.likes > 0) RollingNumber(post.likes, color = Color.White, fontSize = 16.sp)
        }
        Row(
            Modifier.pressable(onClick = onComments),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Filled.ChatBubbleOutline, contentDescription = "Комментарии", tint = Color.White, modifier = Modifier.size(24.dp))
            if (post.comments > 0) RollingNumber(post.comments, color = Color.White, fontSize = 16.sp)
        }
    }
}

// MARK: - Пост целиком

@Composable
fun SocialTopBar(title: String, onBack: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).pressable(onClick = onBack).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Text(
            title,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
        )
        trailing?.invoke()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    initial: SocialPost,
    contentPadding: PaddingValues,
    navigator: SocialNavigator,
    onChange: (SocialPost?) -> Unit
) {
    val scope = rememberCoroutineScope()
    var post by remember { mutableStateOf(initial) }
    val comments = remember { mutableStateListOf<SocialComment>().apply { SocialService.commentCache[initial.id]?.let { addAll(it) } } }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var composing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showingSets by remember { mutableStateOf(false) }
    /// На какой комментарий отвечаем; `null` — обычный комментарий к посту.
    var replyingTo by remember { mutableStateOf<SocialComment?>(null) }
    val me = SocialService.me
    // Карусель фото собирается кадром позже: первый кадр въезда экрана —
    // самый тяжёлый, и делить его с фото и слайдом тренировки незачем.
    // Подложка того же размера стоит сразу, поэтому ничего не прыгает.
    var mediaReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        mediaReady = true
    }

    fun update(value: SocialPost) {
        post = value
        onChange(value)
    }

    LaunchedEffect(initial.id) {
        loading = true
        try {
            val loaded = SocialService.comments(initial.id)
            comments.clear()
            comments.addAll(loaded)
            SocialService.commentCache[initial.id] = loaded
            error = null
        } catch (e: SocialException) {
            error = e.message
        } finally {
            loading = false
        }
    }

    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { SocialTopBar(post.title, onBack = { navigator.pop() }) }
        item {
            PostHeader(post, onOpenAuthor = { navigator.push(SocialRoute.User(post.author.username)) }, onDelete = { confirmDelete = true })
        }
        item { PostTitleBlock(post) }
        item { PostStatsRow(post) }
        // Тренировка — последним слайдом, как в REBRN; все подходы — по нажатию.
        item {
            if (mediaReady || post.photos.isEmpty()) {
                PostMedia(post, Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { showingSets = true })
            } else {
                Box(Modifier.fillMaxWidth().aspectRatio(4f / 5f).clip(RoundedCornerShape(22.dp)).background(Color(0xFF1F1F22)))
            }
        }
        item {
            PostActions(post, onComments = {
                if (SocialService.isSignedIn) {
                    replyingTo = null
                    composing = true
                }
            }, onChange = ::update)
        }
        // Чужую тренировку можно взять себе: её саму или программу автора.
        if (me?.id != post.author.id && post.workout.exercises.isNotEmpty()) {
            item(key = "copy") {
                CopyProgramButton(
                    days = emptyList(),
                    author = post.author.username,
                    navigator = navigator,
                    workout = post.title to post.workout,
                    loadsAuthorProgram = true
                )
            }
        }
        item {
            SectionCaption(if (comments.isEmpty()) "КОММЕНТАРИИ" else "КОММЕНТАРИИ · ${comments.size}", Modifier.padding(top = 6.dp))
        }
        if (loading && comments.isEmpty()) {
            item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp)) } }
        } else if (comments.isEmpty()) {
            item { Text("Пока тихо. Напиши первым — поддержи или спроси про технику.", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp) }
        }
        val ordered = SocialComment.threads(comments).flatMap { listOf(it.root) + it.replies }
        items(ordered, key = { it.id }) { comment ->
            CommentRow(
                comment,
                modifier = Modifier.animateItem(fadeInSpec = tween(220), placementSpec = Motion.snappy(), fadeOutSpec = tween(160)),
                canDelete = me != null && (comment.author.id == me.id || post.author.id == me.id),
                canReply = SocialService.isSignedIn,
                onOpenAuthor = { navigator.push(SocialRoute.User(comment.author.username)) },
                onReply = {
                    replyingTo = comment
                    composing = true
                },
                onDelete = {
                    scope.launch {
                        try {
                            SocialService.deleteComment(comment.id)
                            // С корнем ветки на сервере уходят и ответы на него.
                            val removed = comments.count { it.id == comment.id || it.replyTo == comment.id }
                            comments.removeAll { it.id == comment.id || it.replyTo == comment.id }
                            update(post.copy(comments = maxOf(0, post.comments - removed)))
                        } catch (e: SocialException) {
                            error = e.message
                        }
                    }
                }
            )
        }
        error?.let { message -> item { Text(message, color = Theme.warning, fontSize = 12.sp) } }
        item {
            if (SocialService.isSignedIn) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .pressable {
                            replyingTo = null
                            composing = true
                        }
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF1C1C1C))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(me?.avatar, me?.username ?: "s", 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Написать комментарий…", color = Color.White.copy(alpha = 0.45f), fontSize = 15.sp)
                }
            } else {
                Text("Создай профиль во вкладке «Профиль», чтобы комментировать", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            modifier = Modifier.popIn(),
            onDismissRequest = { confirmDelete = false },
            containerColor = Theme.dialog,
            title = { Text("Удалить тренировку из ленты?", color = Theme.textPrimary, fontWeight = FontWeight.Bold) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        try {
                            SocialService.deletePost(post.id)
                            onChange(null)
                            navigator.pop()
                        } catch (e: SocialException) {
                            error = e.message
                        }
                    }
                }) { Text("Удалить", color = Theme.record) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена", color = Theme.textSecondary) } }
        )
    }

    if (showingSets) {
        ModalBottomSheet(
            onDismissRequest = { showingSets = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF121212)
        ) {
            LazyColumn(
                Modifier.fillMaxWidth().navigationBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { Text(post.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black) }
                item { SectionCaption(post.workout.program?.let { "ТРЕНИРОВКА · ${it.uppercase()}" } ?: "ТРЕНИРОВКА") }
                items(post.workout.exercises.size) { index -> WorkoutSetsCard(post.workout.exercises[index]) }
            }
        }
    }

    if (composing) {
        CommentSheet(
            replyingTo = replyingTo,
            onCancelReply = { replyingTo = null },
            onDismiss = {
                composing = false
                replyingTo = null
            },
            onSend = { text ->
                val comment = SocialService.addComment(post.id, text, replyingTo?.id)
                comments.add(comment)
                update(post.copy(comments = post.comments + 1))
            }
        )
    }
}

@Composable
private fun CommentRow(
    comment: SocialComment,
    canDelete: Boolean,
    canReply: Boolean,
    onOpenAuthor: () -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menu by remember { mutableStateOf(false) }
    // Ответы — со сдвигом под своим комментарием, как в Instagram.
    Row(modifier.padding(start = if (comment.isReply) 44.dp else 0.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.pressable(onClick = onOpenAuthor)) { Avatar(comment.author.avatar, comment.author.username, if (comment.isReply) 28.dp else 34.dp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("@${comment.author.username}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(SocialFormat.ago(comment.instant), color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
            }
            Text(
                buildAnnotatedString {
                    comment.replyToUsername?.let { withStyle(SpanStyle(color = Theme.accent)) { append("@$it ") } }
                    append(comment.text)
                },
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 15.sp
            )
            if (canReply) {
                Text(
                    "Ответить",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 2.dp).pressable(onClick = onReply)
                )
            }
        }
        if (canDelete) {
            Box {
                Box(Modifier.size(30.dp).pressable { menu = true }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.MoreHoriz, contentDescription = "Действия с комментарием", tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Удалить комментарий", color = Theme.record) },
                        onClick = {
                            menu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

/// Лист с полем комментария: перекрывает панель вкладок, а клавиатура
/// поднимает его целиком.
// MARK: - Фото и тренировка

/// Карусель поста: сначала фото, последним слайдом — сама тренировка.
/// Без фото — только тренировка.
@Composable
fun PostMedia(post: SocialPost, modifier: Modifier = Modifier) {
    val hasWorkout = post.workout.exercises.isNotEmpty()
    if (post.photos.isEmpty()) {
        Box(modifier.fillMaxWidth().padding(vertical = 6.dp)) { PostWorkoutSlide(post.workout) }
        return
    }
    val count = post.photos.size + if (hasWorkout) 1 else 0
    val pager = androidx.compose.foundation.pager.rememberPagerState { count }
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(4f / 5f)
            .clip(RoundedCornerShape(22.dp))
    ) {
        androidx.compose.foundation.pager.HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            if (page < post.photos.size) {
                RemoteImage(post.photos[page], Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(Color.Black).padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 34.dp)) {
                    PostWorkoutSlide(post.workout)
                }
            }
        }
        if (count > 1) PageDots(pager, count, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
    }
}

/// Точки страниц: активная вытягивается в капсулу и едет за пальцем прямо
/// во время свайпа. Положение читается только в отрисовке — карточка поста
/// при листании не перекомпонуется.
@Composable
internal fun PageDots(pager: androidx.compose.foundation.pager.PagerState, count: Int, modifier: Modifier) {
    val dot = 7.dp
    val gap = 6.dp
    val stretch = 9.dp
    Spacer(
        modifier
            .size(width = dot * count + gap * (count - 1) + stretch, height = dot)
            .drawBehind {
                val d = dot.toPx()
                val g = gap.toPx()
                val extra = stretch.toPx()
                val position = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, (count - 1).toFloat())
                var x = 0f
                for (index in 0 until count) {
                    val near = (1f - kotlin.math.abs(position - index)).coerceIn(0f, 1f)
                    val width = d + extra * near
                    drawRoundRect(
                        Color.White.copy(alpha = 0.45f + 0.55f * near),
                        topLeft = Offset(x, 0f),
                        size = Size(width, d),
                        cornerRadius = CornerRadius(d / 2)
                    )
                    x += width + g
                }
            }
    )
}

/// Слайд тренировки как в REBRN: картинка упражнения, название и подходы.
@Composable
fun PostWorkoutSlide(workout: SocialWorkout, limit: Int = 4) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (workout.exercises.isEmpty()) {
            Text("Без записанных подходов", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
        }
        workout.exercises.take(limit).forEach { exercise ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExerciseThumb(exercise.name, 54.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            exercise.name,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (exercise.hasRecord) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Filled.EmojiEvents, contentDescription = "Рекорд", tint = Theme.warning, modifier = Modifier.size(14.dp))
                        }
                    }
                    // «4 подхода · 80 кг × 5» — лучший подход виден сразу.
                    val sets = "${exercise.sets.size} ${RuPlural.word(exercise.sets.size, "подход", "подхода", "подходов")}"
                    Text(
                        exercise.best?.let { "$sets · ${it.text}" } ?: sets,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (workout.exercises.size > limit) {
            val rest = workout.exercises.size - limit
            Text(
                "ещё $rest ${RuPlural.word(rest, "упражнение", "упражнения", "упражнений")}",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }
    }
}

/// Все подходы упражнения — в листе, который открывается по нажатию на слайд.
@Composable
internal fun WorkoutSetsCard(exercise: SocialWorkout.Exercise) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF171717))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ExerciseThumb(exercise.name, 40.dp)
            Spacer(Modifier.width(12.dp))
            Text(exercise.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        exercise.sets.forEachIndexed { setIndex, set ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${setIndex + 1}", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
                Text(set.text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                if (set.record) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = Theme.warning, modifier = Modifier.size(13.dp))
                    Text(" рекорд", color = Theme.warning, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentSheet(
    replyingTo: SocialComment?,
    onCancelReply: () -> Unit,
    onDismiss: () -> Unit,
    onSend: suspend (String) -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val canSend = !sending && text.isNotBlank()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Color(0xFF121212)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    replyingTo?.let { "Ответ @${it.author.username}" } ?: "Комментарий",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (replyingTo != null) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Не отвечать, а написать к посту",
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(22.dp).pressable(onClick = onCancelReply)
                    )
                }
            }
            replyingTo?.let {
                Text(it.text, color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF222222))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    if (text.isEmpty()) Text(if (replyingTo == null) "Поддержи или спроси про технику" else "Твой ответ", color = Color.White.copy(alpha = 0.4f), fontSize = 16.sp)
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it.take(500) },
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                        cursorBrush = SolidColor(Theme.accent),
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    )
                }
                Box(
                    Modifier
                        .size(44.dp)
                        .pressable(enabled = canSend) {
                            sending = true
                            scope.launch {
                                try {
                                    onSend(text.trim())
                                    text = ""
                                    onDismiss()
                                } catch (e: SocialException) {
                                    error = e.message
                                } finally {
                                    sending = false
                                }
                            }
                        }
                        .clip(CircleShape)
                        .background(if (canSend) Color.White else Color(0xFF4D4D4D)),
                    contentAlignment = Alignment.Center
                ) {
                    if (sending) CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    else Icon(Icons.Filled.ArrowUpward, contentDescription = "Отправить", tint = Color.Black)
                }
            }
            error?.let { Text(it, color = Theme.warning, fontSize = 12.sp) }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}
