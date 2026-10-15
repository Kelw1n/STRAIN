package com.texasprogram.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChatBubbleOutline
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.SocialComment
import com.texasprogram.app.model.SocialFormat
import com.texasprogram.app.model.SocialPost
import com.texasprogram.app.model.SocialWorkout
import com.texasprogram.app.service.RuPlural
import com.texasprogram.app.service.SocialException
import com.texasprogram.app.service.SocialService
import kotlinx.coroutines.launch

// MARK: - Пост в ленте

/// Пост как в REBRN: кто и когда, название, заметка, время, объём и плотность,
/// фото и под ними лайк с комментариями. Без фото вместо них — упражнения.
@Composable
fun PostCard(
    post: SocialPost,
    onOpen: () -> Unit,
    onOpenAuthor: () -> Unit,
    onChange: (SocialPost?) -> Unit
) {
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PostHeader(post, onOpenAuthor, onDelete = { confirmDelete = true })
        Column(Modifier.pressable(onClick = onOpen), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PostTitleBlock(post)
            PostStatsRow(post)
            if (post.photos.isEmpty()) WorkoutSummary(post.workout, limit = 4) else PhotoPager(post.photos)
        }
        PostActions(post, onComments = onOpen, onChange = { onChange(it) })
        error?.let { Text(it, color = Theme.warning, fontSize = 12.sp) }
    }

    if (confirmDelete) {
        AlertDialog(
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
    val mine = SocialService.me?.id == post.author.id
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).pressable(onClick = onOpenAuthor),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(post.author.avatar, post.author.username, 46.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("@${post.author.username}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(SocialFormat.ago(post.instant), color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
            }
        }
        Box {
            Box(
                Modifier.size(38.dp).clip(CircleShape).pressable { menu = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.MoreHoriz, contentDescription = "Действия с постом", tint = Color.White.copy(alpha = 0.55f))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
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

@Composable
fun PostStatsRow(post: SocialPost) {
    Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
        PostStat(SocialFormat.duration(post.durationSeconds), null, "Время")
        PostStat(SocialFormat.grouped(post.volumeKg), "кг", "Объём")
        if (post.density > 0) PostStat(SocialFormat.grouped(post.density), "кг/мин", "Плотность")
        post.effort?.let { PostStat("$it", "/10", "Усилие") }
    }
}

@Composable
private fun PostStat(value: String, unit: String?, title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
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
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(
            Modifier.pressable {
                if (!SocialService.isSignedIn || busy) return@pressable
                haptics.light()
                // Сразу показываем лайк, не дожидаясь сервера; ошибка вернёт как было.
                val optimistic = post.copy(liked = !post.liked, likes = post.likes + if (post.liked) -1 else 1)
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
                modifier = Modifier.size(26.dp)
            )
            if (post.likes > 0) Text("${post.likes}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Row(
            Modifier.pressable(onClick = onComments),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Filled.ChatBubbleOutline, contentDescription = "Комментарии", tint = Color.White, modifier = Modifier.size(24.dp))
            if (post.comments > 0) Text("${post.comments}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
            Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).pressable(onClick = onBack),
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
    val comments = remember { mutableStateListOf<SocialComment>() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var composing by remember { mutableStateOf(false) }
    val me = SocialService.me

    fun update(value: SocialPost) {
        post = value
        onChange(value)
    }

    LaunchedEffect(initial.id) {
        loading = true
        try {
            comments.clear()
            comments.addAll(SocialService.comments(initial.id))
            error = null
        } catch (e: SocialException) {
            error = e.message
        }
        loading = false
    }

    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { SocialTopBar(post.title, onBack = { navigator.pop() }) }
        item {
            PostHeader(post, onOpenAuthor = { navigator.push(SocialRoute.User(post.author.username)) }, onDelete = {
                scope.launch {
                    runCatching { SocialService.deletePost(post.id) }.onSuccess {
                        onChange(null)
                        navigator.pop()
                    }
                }
            })
        }
        item { PostTitleBlock(post) }
        item { PostStatsRow(post) }
        if (post.photos.isNotEmpty()) item { PhotoPager(post.photos) }
        item { PostActions(post, onComments = { if (SocialService.isSignedIn) composing = true }, onChange = ::update) }
        if (post.workout.exercises.isNotEmpty()) {
            item {
                SectionCaption(post.workout.program?.let { "ТРЕНИРОВКА · ${it.uppercase()}" } ?: "ТРЕНИРОВКА")
            }
            items(post.workout.exercises.size) { index ->
                val exercise = post.workout.exercises[index]
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF171717))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(exercise.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
        }
        item {
            SectionCaption(if (comments.isEmpty()) "КОММЕНТАРИИ" else "КОММЕНТАРИИ · ${comments.size}", Modifier.padding(top = 6.dp))
        }
        if (loading && comments.isEmpty()) {
            item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp)) } }
        } else if (comments.isEmpty()) {
            item { Text("Пока тихо. Напиши первым — поддержи или спроси про технику.", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp) }
        }
        items(comments, key = { it.id }) { comment ->
            CommentRow(
                comment,
                canDelete = me != null && (comment.author.id == me.id || post.author.id == me.id),
                onOpenAuthor = { navigator.push(SocialRoute.User(comment.author.username)) },
                onDelete = {
                    scope.launch {
                        try {
                            SocialService.deleteComment(comment.id)
                            comments.remove(comment)
                            update(post.copy(comments = maxOf(0, post.comments - 1)))
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
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF1C1C1C))
                        .pressable { composing = true }
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

    if (composing) {
        CommentSheet(
            onDismiss = { composing = false },
            onSend = { text ->
                val comment = SocialService.addComment(post.id, text)
                comments.add(comment)
                update(post.copy(comments = post.comments + 1))
            }
        )
    }
}

@Composable
private fun CommentRow(comment: SocialComment, canDelete: Boolean, onOpenAuthor: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.pressable(onClick = onOpenAuthor)) { Avatar(comment.author.avatar, comment.author.username, 34.dp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("@${comment.author.username}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(SocialFormat.ago(comment.instant), color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
            }
            Text(comment.text, color = Color.White.copy(alpha = 0.88f), fontSize = 15.sp)
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentSheet(onDismiss: () -> Unit, onSend: suspend (String) -> Unit) {
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
            Text("Комментарий", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF222222))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    if (text.isEmpty()) Text("Поддержи или спроси про технику", color = Color.White.copy(alpha = 0.4f), fontSize = 16.sp)
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
                        .clip(CircleShape)
                        .background(if (canSend) Color.White else Color(0xFF4D4D4D))
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
                        },
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
