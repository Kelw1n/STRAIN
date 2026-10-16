package com.texasprogram.app.ui

import android.content.ClipData
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import com.texasprogram.app.model.SocialChatPage
import com.texasprogram.app.model.SocialLive
import com.texasprogram.app.model.SocialMessageQuote
import com.texasprogram.app.model.SocialReaction
import java.time.Instant
import kotlin.math.roundToInt
import kotlin.math.sin
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.SocialChat
import com.texasprogram.app.model.SocialFormat
import com.texasprogram.app.model.SocialMessage
import com.texasprogram.app.model.SocialUserShort
import com.texasprogram.app.service.SocialException
import com.texasprogram.app.service.SocialService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

// MARK: - Список переписок

/// Переписки во вкладке «Друзья»: с кем, последнее сообщение и непрочитанные.
@Composable
fun ChatList(onOpen: (String) -> Unit) {
    val chats = remember { mutableStateListOf<SocialChat>() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        loading = true
        try {
            val list = SocialService.chats().chats
            chats.clear()
            chats.addAll(list)
            error = null
        } catch (e: SocialException) {
            error = e.message
        } finally {
            loading = false
        }
    }

    // Пока список открыт, новые сообщения подтягиваются сами.
    LaunchedEffect(Unit) {
        while (true) {
            reload()
            delay(10_000)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val currentError = error
        if (currentError != null && chats.isEmpty()) {
            SocialErrorCard(currentError) { scope.launch { reload() } }
        } else if (chats.isEmpty() && !loading) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.ChatBubbleOutline, contentDescription = null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(32.dp))
                Text("Сообщений пока нет", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Открой профиль друга и нажми «Написать».", color = Color.White.copy(alpha = 0.55f), fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        }
        if (chats.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF161617))
            ) {
                chats.forEachIndexed { index, chat ->
                    ChatRow(chat) { onOpen(chat.user.username) }
                    if (index < chats.size - 1) HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(start = 74.dp))
                }
            }
        }
        if (loading && chats.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
internal fun ChatRow(chat: SocialChat, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(onClick = onOpen).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Avatar(chat.user.avatar, chat.user.username, 48.dp)
            // Друг сейчас тренируется — зелёная точка, как «в сети».
            if (chat.live != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF161617))
                        .padding(2.5.dp)
                        .clip(CircleShape)
                        .background(Theme.success)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("@${chat.user.username}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(SocialFormat.ago(chat.last.instant), color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val live = chat.live
                if (live != null && chat.unread == 0) {
                    Text(
                        "Тренируется · ${live.line}",
                        color = Theme.success,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        (if (chat.last.fromMe) "Вы: " else "") + chat.last.preview,
                        color = Color.White.copy(alpha = if (chat.unread > 0) 0.9f else 0.5f),
                        fontSize = 14.sp,
                        fontWeight = if (chat.unread > 0) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (chat.unread > 0) {
                    Box(
                        Modifier.padding(start = 8.dp).clip(CircleShape).background(Theme.accent).padding(horizontal = 7.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${chat.unread}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// MARK: - Переписка

/// Переписка с одним человеком. Новое — сообщения, реакции, прочтение и
/// удаление — подтягивается каждые несколько секунд, пока экран открыт;
/// панель вкладок на это время прячется.
@Composable
fun ChatScreen(username: String, contentPadding: PaddingValues, navigator: SocialNavigator) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current
    var user by remember { mutableStateOf<SocialUserShort?>(null) }
    val messages = remember { mutableStateListOf<SocialMessage>() }
    var older by remember { mutableStateOf<Double?>(null) }
    var loading by remember { mutableStateOf(true) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    /// Фото, выбранное к отправке, — показывается над полем ввода.
    var attachment by rememberSaveable { mutableStateOf<Uri?>(null) }
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var attachMenu by remember { mutableStateOf(false) }
    /// Фото ещё не ушедших сообщений: пока идёт загрузка, видно их.
    val localImages = remember { mutableStateMapOf<String, Uri>() }
    var viewing by remember { mutableStateOf<String?>(null) }
    var barHeight by remember { mutableIntStateOf(0) }
    /// На какое сообщение отвечаем — цитата над полем ввода.
    var replyingTo by remember { mutableStateOf<SocialMessage?>(null) }
    /// Сообщение, к которому прокрутили по цитате, — коротко подсвечено.
    var highlighted by remember { mutableStateOf<String?>(null) }
    /// С этого времени сервера спрашиваем изменения.
    var serverTime by remember { mutableStateOf<Double?>(null) }
    var otherTyping by remember { mutableStateOf(false) }
    var lastSeen by remember { mutableStateOf<Double?>(null) }
    var live by remember { mutableStateOf<SocialLive?>(null) }
    val listState = rememberLazyListState()

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) attachment = uri }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) attachment = uri
    }

    DisposableEffect(Unit) {
        SocialService.hidesTabBar = true
        onDispose { SocialService.hidesTabBar = false }
    }

    /// Новое добавляется, изменённое заменяется, удалённое пропадает.
    /// Сервер отдаёт новые первыми, на экране — старые сверху.
    fun applyChanges(incoming: List<SocialMessage>) {
        val updated = SocialMessage.merged(messages, incoming)
        if (updated == messages.toList()) return
        messages.clear()
        messages.addAll(updated)
    }

    fun absorb(page: SocialChatPage) {
        user = page.user
        page.serverTime?.let { serverTime = it }
        otherTyping = page.typing
        lastSeen = page.lastSeen ?: lastSeen
        live = page.live
    }

    suspend fun loadLatest() {
        loading = true
        try {
            val page = SocialService.messages(username)
            absorb(page)
            older = page.next
            applyChanges(page.messages)
            error = null
        } catch (e: SocialException) {
            error = e.message
        } finally {
            loading = false
        }
    }

    LaunchedEffect(username) {
        loadLatest()
        SocialService.refreshUnread()
        while (true) {
            delay(3_000)
            val since = serverTime
            if (since == null) {
                loadLatest()
                continue
            }
            // Всё, что поменялось с прошлого раза: новые сообщения, реакции, прочтение, удаление.
            runCatching { SocialService.messages(username, since = since) }.onSuccess { page ->
                absorb(page)
                val known = messages.map { it.id }.toSet()
                val incoming = page.messages.any { !it.fromMe && !it.deleted && it.id !in known }
                applyChanges(page.messages)
                if (incoming) SocialService.refreshUnread()
            }
        }
    }

    LaunchedEffect(draft) {
        if (draft.isNotBlank()) SocialService.sendTyping(username)
    }

    // Новое сообщение или «печатает» — список доезжает до низа.
    LaunchedEffect(messages.lastOrNull()?.id, otherTyping) {
        val count = (if (older != null) 1 else 0) + messages.size + (if (otherTyping) 1 else 0)
        if (messages.isNotEmpty()) listState.animateScrollToItem(count - 1)
    }

    fun reply(message: SocialMessage) {
        if (message.isLocal) return
        replyingTo = message
    }

    /// Реакция видна сразу; ответ сервера её подтверждает или откатывает.
    fun react(message: SocialMessage, emoji: String) {
        if (message.isLocal) return
        val index = messages.indexOfFirst { it.id == message.id }
        if (index < 0) return
        val before = messages[index]
        messages[index] = before.copy(reactions = SocialReaction.toggled(before.reactions, emoji))
        scope.launch {
            try {
                applyChanges(listOf(SocialService.react(message.id, emoji)))
            } catch (e: SocialException) {
                applyChanges(listOf(before))
                error = e.message
            }
        }
    }

    fun openQuote(id: String) {
        val index = messages.indexOfFirst { it.id == id }
        if (index < 0) return
        scope.launch {
            // Перед сообщениями может стоять кнопка «Ранние сообщения».
            listState.animateScrollToItem(index + if (older != null) 1 else 0)
            highlighted = id
            delay(1_200)
            if (highlighted == id) highlighted = null
        }
    }

    /// Сообщение сразу на экране; фото сначала грузится на сервер.
    /// Не ушло — текст, фото и цитата возвращаются в поле.
    fun send() {
        val text = draft.trim()
        val photo = attachment
        val quoted = replyingTo
        if ((text.isEmpty() && photo == null) || sending) return
        val clientId = UUID.randomUUID().toString().lowercase()
        val local = SocialMessage(
            id = "local-$clientId",
            text = text,
            fromMe = true,
            createdAt = System.currentTimeMillis().toDouble(),
            image = if (photo != null) "local" else null,
            replyTo = quoted?.quote
        )
        if (photo != null) localImages[local.id] = photo
        draft = ""
        attachment = null
        replyingTo = null
        sending = true
        messages.add(local)
        scope.launch {
            try {
                val imageId = photo?.let { SocialService.uploadImage(context, it, maxSide = 1600) }
                val sent = SocialService.sendMessage(username, text, clientId, image = imageId, replyTo = quoted?.id)
                messages.remove(local)
                localImages.remove(local.id)
                applyChanges(listOf(sent))
                error = null
            } catch (e: SocialException) {
                messages.remove(local)
                localImages.remove(local.id)
                if (draft.isEmpty()) draft = text
                if (attachment == null) attachment = photo
                if (replyingTo == null) replyingTo = quoted
                error = e.message
            } finally {
                sending = false
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(top = contentPadding.calculateTopPadding())
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(Modifier.fillMaxSize()) {
            ChatTopBar(username, user?.avatar, chatStatus(otherTyping, live, lastSeen), online = live != null || isOnline(lastSeen),
                onBack = { navigator.pop() }, onOpenProfile = { navigator.push(SocialRoute.User(username)) })
            // Переписка уходит под стеклянную панель ввода: снизу отступ на её высоту.
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = with(density) { barHeight.toDp() } + 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (older != null) {
                    item(key = "older") {
                        Text(
                            "Ранние сообщения",
                            color = Theme.accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).pressable {
                                val cursor = older ?: return@pressable
                                scope.launch {
                                    runCatching { SocialService.messages(username, before = cursor) }.onSuccess { page ->
                                        older = page.next
                                        applyChanges(page.messages)
                                    }
                                }
                            }
                        )
                    }
                }
                if (messages.isEmpty()) {
                    item(key = "empty") {
                        Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                            if (loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            else Text("Напиши первым — например, позови на тренировку.", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
                itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                    Column {
                        val day = message.instant.atZone(ZoneId.systemDefault()).toLocalDate()
                        val previous = messages.getOrNull(index - 1)?.instant?.atZone(ZoneId.systemDefault())?.toLocalDate()
                        if (previous != day) {
                            Text(
                                dayTitle(day),
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)
                            )
                        }
                        MessageBubble(
                            message,
                            username = username,
                            localImage = localImages[message.id],
                            highlighted = highlighted == message.id,
                            onOpenImage = { viewing = it },
                            onReply = { reply(message) },
                            onReact = { react(message, it) },
                            onOpenQuote = { openQuote(it) },
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Сообщение", message.text))
                            },
                            onDelete = {
                                scope.launch {
                                    try {
                                        SocialService.deleteMessage(message.id)
                                        messages.removeAll { it.id == message.id }
                                    } catch (e: SocialException) {
                                        error = e.message
                                    }
                                }
                            }
                        )
                    }
                }
                if (otherTyping) item(key = "typing") { TypingBubble() }
            }
        }

        // Панель ввода — стеклянные капсулы поверх переписки, как в Telegram:
        // слева прикрепить фото, справа отправить.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { barHeight = it.height }
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            error?.let {
                Text(it, color = Theme.warning, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.chatGlass(CircleShape).padding(horizontal = 12.dp, vertical = 6.dp))
            }
            replyingTo?.let { quoted ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .chatGlass(RoundedCornerShape(18.dp))
                        .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = Theme.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (quoted.fromMe) "Ответ себе" else "Ответ @$username", color = Theme.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(quoted.quote.preview, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(Modifier.size(32.dp).clip(CircleShape).pressable { replyingTo = null }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Close, contentDescription = "Не отвечать", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                    }
                }
            }
            attachment?.let { uri ->
                Box(Modifier.chatGlass(RoundedCornerShape(20.dp)).padding(6.dp)) {
                    LocalImage(uri, Modifier.size(76.dp).clip(RoundedCornerShape(16.dp)))
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f))
                            .pressable { attachment = null },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Убрать фото", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    Box(
                        Modifier.size(44.dp).chatGlass(CircleShape).pressable { attachMenu = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.AttachFile, contentDescription = "Прикрепить фото", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Снять фото") },
                            leadingIcon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
                            onClick = {
                                attachMenu = false
                                val uri = cameraFileUri(context)
                                cameraUri = uri
                                runCatching { camera.launch(uri) }.onFailure { error = "Камера недоступна" }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Из галереи") },
                            leadingIcon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                            onClick = {
                                attachMenu = false
                                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        )
                    }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .chatGlass(RoundedCornerShape(22.dp))
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (draft.isEmpty()) Text("Сообщение", color = Color.White.copy(alpha = 0.45f), fontSize = 16.sp)
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it.take(2000) },
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                        cursorBrush = SolidColor(Theme.accent),
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                val canSend = !sending && (draft.isNotBlank() || attachment != null)
                Box(
                    Modifier
                        .size(44.dp)
                        .then(if (canSend) Modifier.clip(CircleShape).background(Color.White) else Modifier.chatGlass(CircleShape))
                        .pressable(enabled = canSend) { send() },
                    contentAlignment = Alignment.Center
                ) {
                    if (sending) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    else Icon(Icons.Filled.ArrowUpward, contentDescription = "Отправить", tint = if (canSend) Color.Black else Color.White.copy(alpha = 0.5f))
                }
            }
        }
    }

    viewing?.let { id -> ImageViewer(id) { viewing = null } }
}

/// В сети — если заходил последние две минуты.
private fun isOnline(lastSeen: Double?): Boolean = lastSeen != null && System.currentTimeMillis() - lastSeen < 120_000

/// Строка под именем: печатает, тренируется или когда был в сети.
private fun chatStatus(typing: Boolean, live: SocialLive?, lastSeen: Double?): Pair<String, Color>? = when {
    typing -> "печатает…" to Theme.accent
    live != null -> "тренируется · ${live.line}" to Theme.success
    lastSeen != null -> SocialFormat.lastSeen(Instant.ofEpochMilli(lastSeen.toLong())) to
        Color.White.copy(alpha = if (isOnline(lastSeen)) 0.75f else 0.45f)
    else -> null
}

/// Шапка переписки: назад, аватар с точкой «в сети», имя и статус.
@Composable
internal fun ChatTopBar(
    username: String,
    avatar: String?,
    status: Pair<String, Color>?,
    online: Boolean,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).pressable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Row(Modifier.weight(1f).pressable(onClick = onOpenProfile), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Avatar(avatar, username, 36.dp)
                if (online) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(11.dp)
                            .clip(CircleShape)
                            .background(Color.Black)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(Theme.success)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("@$username", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                status?.let { (text, color) ->
                    Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/// Три точки собеседника, пока он печатает.
@Composable
internal fun TypingBubble() {
    val transition = rememberInfiniteTransition(label = "typing")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "typingPhase"
    )
    Row(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF292929))
                .padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(3) { index ->
                val lift = maxOf(0f, sin(phase - index * 0.7f))
                Box(
                    Modifier
                        .size(7.dp)
                        .graphicsLayer {
                            scaleX = 0.7f + 0.3f * lift
                            scaleY = 0.7f + 0.3f * lift
                        }
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.7f))
                )
            }
        }
    }
}

/// Стеклянная капсула: полупрозрачная тёмная заливка с кромкой-бликом, как у панели вкладок.
fun Modifier.chatGlass(shape: Shape): Modifier = this
    .clip(shape)
    .background(Color(0xB31C1C20), shape)
    .border(
        1.dp,
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.10f))),
        shape
    )

/// Фото с телефона — ещё не загруженное на сервер.
@Composable
private fun LocalImage(uri: Uri, modifier: Modifier) {
    coil.compose.AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
}

private fun dayTitle(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> "Сегодня"
        today.minusDays(1) -> "Вчера"
        else -> day.format(DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru")))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MessageBubble(
    message: SocialMessage,
    username: String = "",
    localImage: Uri? = null,
    highlighted: Boolean = false,
    onOpenImage: (String) -> Unit = {},
    onReply: () -> Unit = {},
    onReact: (String) -> Unit = {},
    onOpenQuote: (String) -> Unit = {},
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var menu by remember { mutableStateOf(false) }
    val time = message.instant.atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
    val hasImage = message.image != null
    /// Сдвиг при свайпе влево — «ответить», как в Telegram.
    val swipe = remember { Animatable(0f) }
    val replyDistance = with(density) { 64.dp.toPx() }
    val pulled = (-swipe.value / replyDistance).coerceIn(0f, 1f)

    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromMe) Arrangement.End else Arrangement.Start) {
        Box {
            Column(
                Modifier
                    .offset { IntOffset(swipe.value.roundToInt(), 0) }
                    .draggable(
                        orientation = Orientation.Horizontal,
                        enabled = !message.isLocal,
                        state = rememberDraggableState { delta ->
                            // Дальше порога — с сопротивлением.
                            val next = (swipe.value + delta * if (swipe.value < -replyDistance) 0.25f else 1f).coerceIn(-replyDistance * 1.5f, 0f)
                            scope.launch { swipe.snapTo(next) }
                        },
                        onDragStopped = {
                            if (swipe.value <= -replyDistance) onReply()
                            swipe.animateTo(0f, spring(dampingRatio = 0.7f))
                        }
                    ),
                horizontalAlignment = if (message.fromMe) Alignment.End else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Column(
                    Modifier
                        .widthIn(max = 290.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (message.fromMe) Theme.accent.copy(alpha = 0.9f) else Color(0xFF292929))
                        .background(Color.White.copy(alpha = if (highlighted) 0.18f else 0f))
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { if (hasImage && !message.isLocal) message.image?.let(onOpenImage) },
                            // Двойное нажатие — 🔥, как сердечко в Telegram.
                            onDoubleClick = { onReact("🔥") },
                            onLongClick = { menu = true }
                        )
                        .padding(horizontal = if (hasImage) 4.dp else 13.dp, vertical = if (hasImage) 4.dp else 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    message.replyTo?.let { quote ->
                        QuoteBlock(
                            quote = quote,
                            name = if (quote.fromMe) "Вы" else "@$username",
                            onMine = message.fromMe,
                            modifier = Modifier
                                .padding(start = if (hasImage) 5.dp else 0.dp, end = if (hasImage) 5.dp else 0.dp, top = if (hasImage) 5.dp else 0.dp)
                                .pressable { onOpenQuote(quote.id) }
                        )
                    }
                    if (hasImage) {
                        Box(Modifier.size(width = 230.dp, height = 270.dp).clip(RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                            if (localImage != null) {
                                LocalImage(localImage, Modifier.fillMaxSize())
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                            } else {
                                RemoteImage(message.image, Modifier.fillMaxSize())
                            }
                        }
                    }
                    if (message.text.isNotEmpty()) {
                        Text(message.text, color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(horizontal = if (hasImage) 9.dp else 0.dp))
                    }
                    // Время всегда справа, как в Telegram.
                    Row(
                        Modifier
                            .align(Alignment.End)
                            .padding(start = if (hasImage) 9.dp else 0.dp, end = if (hasImage) 9.dp else 0.dp, bottom = if (hasImage) 4.dp else 0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(time, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        if (message.fromMe) {
                            Icon(
                                when {
                                    message.isLocal -> Icons.Filled.Schedule
                                    message.read -> Icons.Filled.DoneAll
                                    else -> Icons.Filled.Done
                                },
                                contentDescription = if (message.read) "Прочитано" else null,
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
                if (message.reactions.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        message.reactions.forEach { reaction ->
                            Row(
                                Modifier
                                    .height(26.dp)
                                    .clip(CircleShape)
                                    .background(if (reaction.mine) Theme.accent.copy(alpha = 0.45f) else Color(0xFF292929))
                                    .border(1.dp, if (reaction.mine) Theme.accent else Color.White.copy(alpha = 0.08f), CircleShape)
                                    .pressable { onReact(reaction.emoji) }
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(reaction.emoji, fontSize = 14.sp)
                                if (reaction.count > 1) Text("${reaction.count}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            // Стрелка «ответить» выезжает справа, пока тянем пузырь.
            if (pulled > 0f) {
                Icon(
                    Icons.AutoMirrored.Filled.Reply,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset { IntOffset((swipe.value + with(density) { 34.dp.toPx() }).roundToInt(), 0) }
                        .graphicsLayer {
                            alpha = pulled
                            scaleX = 0.6f + 0.4f * pulled
                            scaleY = 0.6f + 0.4f * pulled
                        }
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .padding(4.dp)
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (!message.isLocal) {
                    // Реакции — строкой сверху, как в Telegram.
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        SocialReaction.choices.forEach { emoji ->
                            val mine = message.reactions.any { it.mine && it.emoji == emoji }
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (mine) Theme.accent.copy(alpha = 0.3f) else Color.Transparent)
                                    .pressable {
                                        menu = false
                                        onReact(emoji)
                                    },
                                contentAlignment = Alignment.Center
                            ) { Text(emoji, fontSize = 22.sp) }
                        }
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    DropdownMenuItem(
                        text = { Text("Ответить") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null) },
                        onClick = {
                            menu = false
                            onReply()
                        }
                    )
                }
                if (message.text.isNotEmpty()) {
                    DropdownMenuItem(text = { Text("Скопировать") }, onClick = {
                        menu = false
                        onCopy()
                    })
                }
                if (message.fromMe && !message.isLocal) {
                    DropdownMenuItem(text = { Text("Удалить", color = Theme.record) }, onClick = {
                        menu = false
                        onDelete()
                    })
                }
            }
        }
    }
}

/// Цитата сообщения, на которое ответили.
@Composable
private fun QuoteBlock(quote: SocialMessageQuote, name: String, onMine: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = if (onMine) 0.16f else 0.07f))
            .padding(start = 6.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(3.dp).height(32.dp).clip(RoundedCornerShape(2.dp)).background(if (onMine) Color.White else Theme.accent))
        Spacer(Modifier.width(8.dp))
        quote.image?.let {
            RemoteImage(it, Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)))
            Spacer(Modifier.width(8.dp))
        }
        Column {
            Text(name, color = if (onMine) Color.White else Theme.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            // Длинная цитата не раздувает пузырь — обрезается.
            Text(
                quote.preview,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp)
            )
        }
    }
}

/// Фото из переписки на весь экран: нажми, чтобы закрыть.
@Composable
private fun ImageViewer(id: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).pressable(onClick = onClose)) {
            RemoteImage(id, Modifier.fillMaxSize(), ContentScale.Fit)
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .statusBarsPadding()
                    .size(40.dp)
                    .chatGlass(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}
