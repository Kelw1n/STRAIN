package com.texasprogram.app.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.vector.ImageVector
import com.texasprogram.app.model.ChatPending
import com.texasprogram.app.model.SocialChatWorkout
import com.texasprogram.app.model.SocialForward
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.composed
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.Canvas
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import com.texasprogram.app.service.VoiceRecording
import com.texasprogram.app.service.VoiceRecorder
import com.texasprogram.app.service.VoicePlayer
import com.texasprogram.app.model.SocialVoice
import androidx.compose.foundation.clickable
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
                Icon(Icons.Filled.ChatBubbleOutline, contentDescription = null, tint = Theme.ink.copy(alpha = 0.35f), modifier = Modifier.size(32.dp))
                Text("Сообщений пока нет", color = Theme.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Открой профиль друга и нажми «Написать».", color = Theme.ink.copy(alpha = 0.55f), fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        }
        if (chats.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Theme.tone(0xFF161617))
            ) {
                chats.forEachIndexed { index, chat ->
                    ChatRow(chat) { onOpen(chat.user.username) }
                    if (index < chats.size - 1) HorizontalDivider(color = Theme.ink.copy(alpha = 0.06f), modifier = Modifier.padding(start = 74.dp))
                }
            }
        }
        if (loading && chats.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Theme.ink, modifier = Modifier.size(24.dp))
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
                        .background(Theme.tone(0xFF161617))
                        .padding(2.5.dp)
                        .clip(CircleShape)
                        .background(Theme.success)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("@${chat.user.username}", color = Theme.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(SocialFormat.ago(chat.last.instant), color = Theme.ink.copy(alpha = 0.4f), fontSize = 12.sp)
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
                        color = Theme.ink.copy(alpha = if (chat.unread > 0) 0.9f else 0.5f),
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

/// Переписка с одним человеком. Новое — сообщения, реакции, прочтение,
/// правки и удаление — подтягивается каждые несколько секунд, пока экран
/// открыт; панель вкладок на это время прячется. Своё сообщение сначала
/// ложится в очередь на телефоне: без связи оно ждёт с часиками и уходит само.
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
    var error by remember { mutableStateOf<String?>(null) }
    /// Фото, выбранное к отправке, — показывается над полем ввода.
    var attachment by rememberSaveable { mutableStateOf<Uri?>(null) }
    var attachMenu by remember { mutableStateOf(false) }
    /// Фото ещё не ушедших сообщений: пока идёт загрузка, видно их.
    val localImages = remember { mutableStateMapOf<String, Any>() }
    var viewing by remember { mutableStateOf<String?>(null) }
    var barHeight by remember { mutableIntStateOf(0) }
    /// На какое сообщение отвечаем — цитата над полем ввода.
    var replyingTo by remember { mutableStateOf<SocialMessage?>(null) }
    /// Какое своё сообщение правим — его текст в поле ввода.
    var editing by remember { mutableStateOf<SocialMessage?>(null) }
    /// Какое сообщение пересылаем — открыт выбор, кому.
    var forwarding by remember { mutableStateOf<SocialMessage?>(null) }
    /// Не ушедшее сообщение, по которому нажали: повторить или удалить.
    var failed by remember { mutableStateOf<SocialMessage?>(null) }
    var openedWorkout by remember { mutableStateOf<Pair<SocialChatWorkout, String?>?>(null) }
    /// Сообщение, к которому прокрутили по цитате, — коротко подсвечено.
    var highlighted by remember { mutableStateOf<String?>(null) }
    /// С этого времени сервера спрашиваем изменения.
    var serverTime by remember { mutableStateOf<Double?>(null) }
    var otherTyping by remember { mutableStateOf(false) }
    var lastSeen by remember { mutableStateOf<Double?>(null) }
    var live by remember { mutableStateOf<SocialLive?>(null) }
    val listState = rememberLazyListState()
    // Голосовое: запись, сдвиг пальца влево (отмена) и подсказка над полем.
    val haptics = rememberHapticEngine()
    val recorder = remember { VoiceRecorder(context.applicationContext) }
    val micDrag = remember { Animatable(0f) }
    var hint by remember { mutableStateOf<String?>(null) }
    val cancelPx = with(density) { 110.dp.toPx() }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) attachment = uri }
    val camera = rememberCameraLauncher { attachment = it }

    fun showHint(text: String) {
        hint = text
        scope.launch {
            delay(2_500)
            if (hint == text) hint = null
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showHint("Готово — теперь зажми микрофон и говори")
        else error = "Без доступа к микрофону голосовое не записать"
    }

    /// Новое добавляется, изменённое заменяется, удалённое пропадает.
    /// Сервер отдаёт новые первыми, на экране — старые сверху.
    fun applyChanges(incoming: List<SocialMessage>) {
        val updated = SocialMessage.merged(messages, incoming)
        if (updated == messages.toList()) return
        messages.clear()
        messages.addAll(updated)
    }

    /// Очередь отправила сообщение: та же строка, только часики сменились
    /// галочкой. Пузырь не пересоздаётся — ничего не мигает и не прыгает.
    fun confirm(clientId: String, sent: SocialMessage) {
        val updated = SocialMessage.merged(messages.filter { it.id != "local-$clientId" }, listOf(sent.copy(rowKey = "local-$clientId")))
        messages.clear()
        messages.addAll(updated)
    }

    /// Чужое голосовое включили — точка гаснет у обоих.
    fun listened(message: SocialMessage) {
        if (message.fromMe || message.heard || message.isLocal) return
        val index = messages.indexOfFirst { it.id == message.id }
        if (index < 0) return
        messages[index] = messages[index].copy(heard = true)
        SocialService.markHeard(message.id)
    }

    /// Следующее голосовое ниже по переписке включается само, как в Telegram.
    fun playNext(voiceId: String) {
        val index = messages.indexOfFirst { it.voice?.id == voiceId }
        if (index < 0) return
        val next = messages.drop(index + 1).firstOrNull { it.voice != null && !it.isLocal } ?: return
        val voice = next.voice ?: return
        listened(next)
        VoicePlayer.play(context, voice)
    }

    /// Сообщения, которые ждали отправки, когда переписку закрыли, — снова на месте.
    fun restorePending() {
        val known = messages.map { it.key }.toSet()
        val waiting = SocialService.pending(username).filter { it.message.key !in known }
        if (waiting.isEmpty()) return
        waiting.filter { it.hasPhotoFile }.forEach { item ->
            SocialService.pendingPhoto(item.clientId)?.let { localImages[item.message.key] = it }
        }
        applyChanges(waiting.map { it.message })
    }

    DisposableEffect(username) {
        SocialService.hidesTabBar = true
        // Ушло из очереди — пузырь с часиками становится обычным на том же месте.
        SocialService.onMessageSent(username) { clientId, sent -> confirm(clientId, sent) }
        // Доиграло голосовое — следующее включается само.
        VoicePlayer.onFinish = { id -> playNext(id) }
        restorePending()
        onDispose {
            SocialService.hidesTabBar = false
            SocialService.onMessageSent(username, null)
            VoicePlayer.onFinish = null
            recorder.cancel()
            VoicePlayer.stop()
        }
    }

    /// Что пришло, пока переписка открыта, — вылетает; старое — сразу на месте.
    val openedAt = remember { System.currentTimeMillis() - 5_000 }

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
            // Связь вернулась — то, что ждало в очереди, уходит.
            if (SocialService.pending(username).isNotEmpty()) SocialService.flushMessages()
            val since = serverTime
            if (since == null) {
                loadLatest()
                continue
            }
            // Всё, что поменялось с прошлого раза: новые сообщения, реакции, прочтение, правки, удаление.
            runCatching { SocialService.messages(username, since = since) }.onSuccess { page ->
                absorb(page)
                if (error != null && SocialService.chatOffline.not()) error = null
                val known = messages.map { it.id }.toSet()
                val incoming = page.messages.any { !it.fromMe && !it.deleted && it.id !in known }
                applyChanges(page.messages)
                if (incoming) SocialService.refreshUnread()
            }
        }
    }

    LaunchedEffect(draft) {
        if (draft.isNotBlank() && editing == null) SocialService.sendTyping(username)
    }

    // Новое сообщение или «печатает» — список доезжает до низа.
    LaunchedEffect(messages.lastOrNull()?.key, otherTyping) {
        val count = (if (older != null) 1 else 0) + messages.size + (if (otherTyping) 1 else 0)
        if (messages.isNotEmpty()) listState.animateScrollToItem(count - 1)
    }

    fun reply(message: SocialMessage) {
        if (message.isLocal) return
        // Правку бросили ради ответа — её текст не должен уйти новым сообщением.
        if (editing != null) {
            editing = null
            draft = ""
        }
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

    fun cancelEditing() {
        editing = null
        draft = ""
    }

    fun startEditing(message: SocialMessage) {
        if (!message.canEdit) return
        haptics.light()
        replyingTo = null
        attachment = null
        editing = message
        draft = message.text
    }

    /// Новый текст виден сразу; не вышло — возвращается прежний.
    fun saveEdit() {
        val original = editing ?: return
        val index = messages.indexOfFirst { it.id == original.id }
        val text = draft.trim()
        if (index < 0 || text == original.text) {
            cancelEditing()
            return
        }
        if (text.isEmpty() && original.image == null) return
        messages[index] = messages[index].copy(text = text, edited = true)
        cancelEditing()
        scope.launch {
            try {
                applyChanges(listOf(SocialService.editMessage(original.id, text)))
                error = null
            } catch (e: SocialException) {
                applyChanges(listOf(original))
                error = e.message
            }
        }
    }

    /// Сообщение сразу на экране и в очереди на телефоне; фото сначала
    /// загружается на сервер. Нет связи — пузырь ждёт с часиками и уходит сам.
    fun send() {
        if (editing != null) {
            saveEdit()
            return
        }
        val text = draft.trim()
        val photo = attachment
        if (text.isEmpty() && photo == null) return
        val item = ChatPending(
            clientId = SocialService.newClientId(),
            username = username,
            text = text,
            replyTo = replyingTo?.quote,
            image = if (photo != null) "local" else null
        )
        val local = item.message
        if (photo != null) localImages[local.key] = photo
        draft = ""
        attachment = null
        replyingTo = null
        messages.add(local)
        if (photo != null) SocialService.queue(context, item, photo) else SocialService.queue(item)
        error = null
    }

    /// Голосовое сразу в переписке, звук — в очередь вместе с сообщением.
    /// В тексте — подпись для старых версий приложения, которые голосовые не играют.
    fun sendVoice(recording: VoiceRecording) {
        val clientId = SocialService.newClientId()
        val item = ChatPending(
            clientId = clientId,
            username = username,
            text = SocialVoice.PREVIEW,
            replyTo = replyingTo?.quote,
            voice = SocialVoice("local-$clientId", recording.durationMs, recording.waveform)
        )
        replyingTo = null
        messages.add(item.message)
        SocialService.queue(item, voice = recording.data)
        error = null
    }

    /// Пересланное уходит той же очередью; переслали сюда же — появляется и здесь.
    fun forward(message: SocialMessage, target: SocialUserShort) {
        val item = ChatPending(
            clientId = SocialService.newClientId(),
            username = target.username,
            text = message.text,
            image = message.image,
            voice = message.voice,
            workout = message.workout,
            forward = message.id,
            forwarded = message.forwarded ?: SocialForward(if (message.fromMe) SocialService.me?.username else username)
        )
        SocialService.queue(item)
        haptics.light()
        if (target.username == username) messages.add(item.message) else showHint("Переслано @${target.username}")
    }

    fun delete(message: SocialMessage) {
        // Не ушедшее — просто убираем из очереди.
        if (message.isLocal) {
            message.clientId?.let { SocialService.discardPending(it) }
            messages.removeAll { it.id == message.id }
            localImages.remove(message.key)
            return
        }
        scope.launch {
            try {
                SocialService.deleteMessage(message.id)
                messages.removeAll { it.id == message.id }
            } catch (e: SocialException) {
                error = e.message
            }
        }
    }

    /// Нажали микрофон: есть разрешение — пишем, нет — спрашиваем.
    fun beginRecording(): Boolean {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
            return false
        }
        if (!recorder.start(scope)) {
            error = "Не удалось включить микрофон"
            return false
        }
        haptics.medium()
        error = null
        hint = null
        return true
    }

    fun finishRecording() {
        if (!recorder.isRecording) return
        val recording = recorder.stop()
        if (recording != null) {
            haptics.light()
            sendVoice(recording)
        } else {
            showHint("Удерживай микрофон, чтобы записать голосовое")
        }
    }

    // Дошли до минуты — голосовое уходит само.
    SideEffect { recorder.onLimit = { finishRecording() } }

    val failures = SocialService.pendingFailures
    val waitingForNetwork = SocialService.chatOffline && SocialService.pending(username).any { it.clientId !in failures }

    Box(
        Modifier
            .fillMaxSize()
            .background(Theme.canvas)
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
                            if (loading) CircularProgressIndicator(color = Theme.ink, modifier = Modifier.size(24.dp))
                            else Text("Напиши первым — например, позови на тренировку.", color = Theme.ink.copy(alpha = 0.5f), fontSize = 14.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
                // Ключ строки, а не id: своё сообщение после ответа сервера остаётся
                // той же строкой. Новое вылетает, удалённое тает, соседи съезжаются.
                itemsIndexed(messages, key = { _, message -> message.key }) { index, message ->
                    Column(
                        Modifier
                            .animateItem(fadeInSpec = null, placementSpec = Motion.snappy(), fadeOutSpec = tween(180))
                            .bubblePop(play = message.isLocal || message.createdAt > openedAt, fromMe = message.fromMe)
                    ) {
                        val day = message.instant.atZone(ZoneId.systemDefault()).toLocalDate()
                        val previous = messages.getOrNull(index - 1)?.instant?.atZone(ZoneId.systemDefault())?.toLocalDate()
                        if (previous != day) {
                            Text(
                                dayTitle(day),
                                color = Theme.ink.copy(alpha = 0.4f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)
                            )
                        }
                        val invite = message.invite
                        if (invite != null) {
                            InviteBubble(message, invite, username) { updated ->
                                val at = messages.indexOfFirst { it.id == message.id }
                                if (at >= 0) messages[at] = messages[at].copy(invite = updated)
                            }
                        } else MessageBubble(
                            message,
                            username = username,
                            localImage = localImages[message.key],
                            highlighted = highlighted == message.id,
                            failure = if (message.isLocal) message.clientId?.let { failures[it] } else null,
                            onOpenImage = { viewing = it },
                            onReply = { reply(message) },
                            onReact = { react(message, it) },
                            onOpenQuote = { openQuote(it) },
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Сообщение", message.text))
                            },
                            onEdit = { startEditing(message) },
                            onForward = { forwarding = message },
                            onOpenWorkout = { workout ->
                                openedWorkout = workout to (message.forwarded?.username ?: if (message.fromMe) null else username)
                            },
                            onListen = { listened(message) },
                            onFailedTap = { failed = message },
                            onDelete = { delete(message) }
                        )
                    }
                }
                if (otherTyping) item(key = "typing") {
                    Box(Modifier.animateItem(fadeInSpec = tween(200), placementSpec = Motion.snappy(), fadeOutSpec = tween(140))) { TypingBubble() }
                }
            }
        }

        // Панель ввода — стеклянные капсулы поверх переписки, как в Telegram:
        // слева прикрепить фото, справа отправить.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { barHeight = it.height }
                .background(Brush.verticalGradient(listOf(Color.Transparent, Theme.canvas.copy(alpha = 0.55f))))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val currentError = error
            if (currentError != null) {
                Text(currentError, color = Theme.warning, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.chatGlass(CircleShape).padding(horizontal = 12.dp, vertical = 6.dp))
            }
            AnimatedVisibility(
                visible = currentError == null && waitingForNetwork,
                enter = fadeIn(tween(180)) + slideInVertically(Motion.snappy()) { it / 2 },
                exit = fadeOut(tween(160))
            ) {
                Row(Modifier.chatGlass(CircleShape).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CloudOff, contentDescription = null, tint = Theme.ink.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Нет связи — сообщения уйдут сами, когда она появится", color = Theme.ink.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            AnimatedVisibility(
                visible = hint != null,
                enter = fadeIn(tween(180)) + slideInVertically(Motion.snappy()) { it / 2 },
                exit = fadeOut(tween(160))
            ) {
                Text(hint.orEmpty(), color = Theme.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.chatGlass(CircleShape).padding(horizontal = 12.dp, vertical = 6.dp))
            }
            val edited = editing
            val quoted = replyingTo
            if (edited != null) {
                ComposerBanner(Icons.Filled.Edit, "Изменить сообщение", edited.text.ifEmpty { "📷 Фото" }, "Не изменять") { cancelEditing() }
            } else if (quoted != null) {
                ComposerBanner(
                    Icons.AutoMirrored.Filled.Reply,
                    if (quoted.fromMe) "Ответ себе" else "Ответ @$username",
                    quoted.quote.preview,
                    "Не отвечать"
                ) { replyingTo = null }
            }
            attachment?.let { uri ->
                Box(Modifier.chatGlass(RoundedCornerShape(20.dp)).padding(6.dp)) {
                    LocalImage(uri, Modifier.size(76.dp).clip(RoundedCornerShape(16.dp)))
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(22.dp)
                            .pressable { attachment = null }
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Убрать фото", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (recorder.isRecording) {
                    VoiceRecordingStrip(recorder, { micDrag.value }, cancelPx, Modifier.weight(1f))
                } else {
                Box {
                    Box(
                        Modifier
                            .size(44.dp)
                            .graphicsLayer { alpha = if (edited == null) 1f else 0.4f }
                            .chatGlass(CircleShape)
                            .pressable(enabled = edited == null) { attachMenu = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.AttachFile, contentDescription = "Прикрепить фото", tint = Theme.ink, modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Снять фото") },
                            leadingIcon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
                            onClick = {
                                attachMenu = false
                                camera.launch()
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
                    if (draft.isEmpty()) Text(if (edited == null) "Сообщение" else "Новый текст", color = Theme.ink.copy(alpha = 0.45f), fontSize = 16.sp)
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it.take(2000) },
                        textStyle = TextStyle(color = Theme.ink, fontSize = 16.sp),
                        cursorBrush = SolidColor(Theme.accent),
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                }
                val canSend = if (edited != null) draft.isNotBlank() || edited.image != null else draft.isNotBlank() || attachment != null
                // Поле пустое и фото нет — вместо «Отправить» микрофон, как в Telegram.
                val showsMic = edited == null && (recorder.isRecording || (attachment == null && draft.isBlank()))
                if (showsMic) {
                    MicButton(
                        recording = recorder.isRecording,
                        drag = { micDrag.value },
                        onPress = { beginRecording() },
                        onDrag = { dx ->
                            scope.launch { micDrag.snapTo(dx) }
                            if (dx < -cancelPx) {
                                recorder.cancel()
                                haptics.medium()
                                true
                            } else false
                        },
                        onRelease = { cancelled ->
                            scope.launch { micDrag.animateTo(0f, Motion.snappy()) }
                            if (!cancelled) finishRecording()
                        }
                    )
                } else {
                Box(
                    Modifier
                        .size(44.dp)
                        .then(if (canSend) Modifier.clip(CircleShape).background(Theme.ink) else Modifier.chatGlass(CircleShape))
                        .pressable(enabled = canSend) { send() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (edited != null) Icons.Filled.Done else Icons.Filled.ArrowUpward,
                        contentDescription = if (edited != null) "Сохранить" else "Отправить",
                        tint = if (canSend) Theme.canvas else Theme.ink.copy(alpha = 0.5f)
                    )
                }
                }
            }
        }
    }

    viewing?.let { id -> ImageViewer(id) { viewing = null } }
    forwarding?.let { message ->
        ChatTargetSheet("Переслать", onDismiss = { forwarding = null }) { target -> forward(message, target) }
    }
    openedWorkout?.let { (workout, author) ->
        ChatWorkoutSheet(workout, author, navigator) { openedWorkout = null }
    }
    failed?.let { message ->
        AlertDialog(
            modifier = Modifier.popIn(),
            onDismissRequest = { failed = null },
            containerColor = Theme.dialog,
            title = { Text("Сообщение не отправлено", color = Theme.textPrimary, fontWeight = FontWeight.Bold) },
            text = { Text(message.clientId?.let { failures[it] } ?: "Попробуй ещё раз.", color = Theme.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    failed = null
                    message.clientId?.let { SocialService.retryPending(it) }
                }) { Text("Повторить", color = Theme.accent) }
            },
            dismissButton = {
                TextButton(onClick = {
                    failed = null
                    delete(message)
                }) { Text("Удалить", color = Theme.record) }
            }
        )
    }
}

/// Плашка над полем ввода: на что отвечаем или что правим.
@Composable
private fun ComposerBanner(icon: ImageVector, title: String, text: String, close: String, onClose: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .chatGlass(RoundedCornerShape(18.dp))
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Theme.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Theme.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(text, color = Theme.ink.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(32.dp).pressable(onClick = onClose).clip(CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Close, contentDescription = close, tint = Theme.ink.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
        }
    }
}

/// В сети — если заходил последние две минуты.
private fun isOnline(lastSeen: Double?): Boolean = lastSeen != null && System.currentTimeMillis() - lastSeen < 120_000

/// Строка под именем: печатает, тренируется или когда был в сети.
private fun chatStatus(typing: Boolean, live: SocialLive?, lastSeen: Double?): Pair<String, Color>? = when {
    typing -> "печатает…" to Theme.accent
    live != null -> "тренируется · ${live.line}" to Theme.success
    lastSeen != null -> SocialFormat.lastSeen(Instant.ofEpochMilli(lastSeen.toLong())) to
        Theme.ink.copy(alpha = if (isOnline(lastSeen)) 0.75f else 0.45f)
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
            Modifier.size(40.dp).pressable(onClick = onBack).clip(CircleShape).background(Theme.ink.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Theme.ink, modifier = Modifier.size(20.dp))
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
                            .background(Theme.canvas)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(Theme.success)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("@$username", color = Theme.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                .background(Theme.tone(0xFF292929))
                .padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(3) { index ->
                Box(
                    Modifier
                        .size(7.dp)
                        // Фаза читается только при отрисовке слоя: каждый кадр
                        // перерисовываются точки, а не пузырь целиком.
                        .graphicsLayer {
                            val lift = maxOf(0f, sin(phase - index * 0.7f))
                            scaleX = 0.7f + 0.3f * lift
                            scaleY = 0.7f + 0.3f * lift
                        }
                        .clip(CircleShape)
                        .background(Theme.ink.copy(alpha = 0.7f))
                )
            }
        }
    }
}

/// Плашка поверх экрана (обновление, приглашение): непрозрачная, с тенью и
/// кромкой. Под ней бывает крупный заголовок — сквозь полупрозрачное стекло
/// он проступал, и текст плашки было не прочитать.
fun Modifier.bannerSurface(shape: Shape): Modifier = this
    .graphicsLayer {
        this.shape = shape
        shadowElevation = 16.dp.toPx()
        clip = false
    }
    .clip(shape)
    .background(Theme.tone(0xFF1C1C21), shape)
    .border(
        1.dp,
        Brush.verticalGradient(listOf(glassEdge(0.16f), glassEdge(0.05f))),
        shape
    )

/// Стеклянная капсула: полупрозрачная тёмная заливка с кромкой-бликом, как у панели вкладок.
fun Modifier.chatGlass(shape: Shape): Modifier = this
    .clip(shape)
    .background(Theme.tone(0xB31C1C20), shape)
    .border(
        1.dp,
        Brush.verticalGradient(listOf(glassEdge(0.22f), glassEdge(0.04f), glassEdge(0.10f))),
        shape
    )

/// Фото с телефона — ещё не загруженное на сервер.
@Composable
private fun LocalImage(model: Any, modifier: Modifier) {
    coil.compose.AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
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
    localImage: Any? = null,
    highlighted: Boolean = false,
    /// Почему своё сообщение не ушло: сервер отказал.
    failure: String? = null,
    onOpenImage: (String) -> Unit = {},
    onReply: () -> Unit = {},
    onReact: (String) -> Unit = {},
    onOpenQuote: (String) -> Unit = {},
    onCopy: () -> Unit,
    onEdit: () -> Unit = {},
    onForward: () -> Unit = {},
    onOpenWorkout: (SocialChatWorkout) -> Unit = {},
    onListen: () -> Unit = {},
    onFailedTap: () -> Unit = {},
    onDelete: () -> Unit
) {
    // На моём оранжевом пузыре текст и значки белые в любой теме.
    val bubbleInk = if (message.fromMe) Color.White else Theme.ink
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptics = rememberHapticEngine()
    var menu by remember { mutableStateOf(false) }
    /// Дотянули до порога: отпустишь — ответ.
    var armed by remember { mutableStateOf(false) }
    val time = message.instant.atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
    val hasImage = message.image != null
    /// Сдвиг при свайпе влево — «ответить», как в Telegram.
    val swipe = remember { Animatable(0f) }
    val replyDistance = with(density) { 64.dp.toPx() }
    val pulled = (-swipe.value / replyDistance).coerceIn(0f, 1f)
    // Пока голосовое играет, палец по волне перематывает, а не отвечает.
    val voicePlaying = message.voice?.let { VoicePlayer.playingId == it.id } == true

    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromMe) Arrangement.End else Arrangement.Start) {
        Box {
            Column(
                Modifier
                    .offset { IntOffset(swipe.value.roundToInt(), 0) }
                    .draggable(
                        orientation = Orientation.Horizontal,
                        enabled = !message.isLocal && !voicePlaying,
                        state = rememberDraggableState { delta ->
                            // Дальше порога — с сопротивлением.
                            val next = (swipe.value + delta * if (swipe.value < -replyDistance) 0.25f else 1f).coerceIn(-replyDistance * 1.5f, 0f)
                            scope.launch { swipe.snapTo(next) }
                            val reached = next <= -replyDistance
                            if (reached != armed) {
                                armed = reached
                                if (reached) haptics.medium()
                            }
                        },
                        onDragStopped = {
                            if (armed) onReply()
                            armed = false
                            swipe.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 700f))
                        }
                    ),
                horizontalAlignment = if (message.fromMe) Alignment.End else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Column(
                    Modifier
                        .widthIn(max = 290.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (message.fromMe) Theme.accent.copy(alpha = if (failure == null) 0.9f else 0.5f) else Theme.tone(0xFF292929))
                        .background(bubbleInk.copy(alpha = if (highlighted) 0.18f else 0f))
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                when {
                                    failure != null -> onFailedTap()
                                    hasImage && !message.isLocal -> message.image?.let(onOpenImage)
                                }
                            },
                            // Двойное нажатие — 🔥, как сердечко в Telegram.
                            onDoubleClick = { onReact("🔥") },
                            onLongClick = { menu = true }
                        )
                        .padding(horizontal = if (hasImage) 4.dp else 13.dp, vertical = if (hasImage) 4.dp else 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    message.forwarded?.let { forwarded ->
                        Row(
                            Modifier.padding(start = if (hasImage) 9.dp else 0.dp, top = if (hasImage) 5.dp else 0.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val tint = if (message.fromMe) bubbleInk.copy(alpha = 0.8f) else Theme.accent
                            Icon(Icons.AutoMirrored.Filled.Forward, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(forwarded.title, color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
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
                                if (message.isLocal && failure == null) CircularProgressIndicator(color = bubbleInk, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                            } else {
                                RemoteImage(message.image, Modifier.fillMaxSize())
                            }
                        }
                    }
                    val voice = message.voice
                    val workout = message.workout
                    if (voice != null) {
                        VoiceMessageContent(voice, fromMe = message.fromMe, isLocal = message.isLocal, heard = message.heard, onListen = onListen)
                    } else if (workout != null) {
                        ChatWorkoutCard(workout, message.fromMe) { onOpenWorkout(workout) }
                    } else if (message.text.isNotEmpty()) {
                        Text(message.text, color = bubbleInk, fontSize = 16.sp, modifier = Modifier.padding(horizontal = if (hasImage) 9.dp else 0.dp))
                    }
                    // Время всегда справа, как в Telegram.
                    Row(
                        Modifier
                            .align(Alignment.End)
                            .padding(start = if (hasImage) 9.dp else 0.dp, end = if (hasImage) 9.dp else 0.dp, bottom = if (hasImage) 4.dp else 0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (message.edited) Text("изменено", color = bubbleInk.copy(alpha = 0.6f), fontSize = 11.sp)
                        Text(time, color = bubbleInk.copy(alpha = 0.6f), fontSize = 11.sp)
                        if (message.fromMe) {
                            Icon(
                                when {
                                    failure != null -> Icons.Filled.Error
                                    message.isLocal -> Icons.Filled.Schedule
                                    message.read -> Icons.Filled.DoneAll
                                    else -> Icons.Filled.Done
                                },
                                contentDescription = when {
                                    failure != null -> "Не отправлено"
                                    message.read -> "Прочитано"
                                    else -> null
                                },
                                tint = if (failure != null) Theme.record else bubbleInk.copy(alpha = 0.6f),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
                if (failure != null) {
                    Row(Modifier.pressable(onClick = onFailedTap), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Error, contentDescription = null, tint = Theme.record, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Не отправлено — нажми", color = Theme.record, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (message.reactions.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        message.reactions.forEach { reaction ->
                            Row(
                                Modifier
                                    .height(26.dp)
                                    .pressable { onReact(reaction.emoji) }
                                    .clip(CircleShape)
                                    .background(if (reaction.mine) Theme.accent.copy(alpha = 0.45f) else Theme.tone(0xFF292929))
                                    .border(1.dp, if (reaction.mine) Theme.accent else Theme.ink.copy(alpha = 0.08f), CircleShape)
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(reaction.emoji, fontSize = 14.sp)
                                if (reaction.count > 1) Text("${reaction.count}", color = Theme.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                    tint = Theme.ink,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset { IntOffset((swipe.value + with(density) { 34.dp.toPx() }).roundToInt(), 0) }
                        .graphicsLayer {
                            alpha = pulled
                            val scale = if (armed) 1.15f else 0.6f + 0.4f * pulled
                            scaleX = scale
                            scaleY = scale
                        }
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (armed) Theme.accent else Theme.ink.copy(alpha = 0.2f))
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
                                    .pressable {
                                        menu = false
                                        onReact(emoji)
                                    }
                                    .clip(CircleShape)
                                    .background(if (mine) Theme.accent.copy(alpha = 0.3f) else Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) { Text(emoji, fontSize = 22.sp) }
                        }
                    }
                    HorizontalDivider(color = Theme.ink.copy(alpha = 0.08f))
                    DropdownMenuItem(
                        text = { Text("Ответить") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null) },
                        onClick = {
                            menu = false
                            onReply()
                        }
                    )
                }
                if (message.canForward) {
                    DropdownMenuItem(
                        text = { Text("Переслать") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Forward, contentDescription = null) },
                        onClick = {
                            menu = false
                            onForward()
                        }
                    )
                }
                if (message.canEdit) {
                    DropdownMenuItem(
                        text = { Text("Изменить") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = {
                            menu = false
                            onEdit()
                        }
                    )
                }
                if (message.text.isNotEmpty() && message.voice == null && message.workout == null) {
                    DropdownMenuItem(text = { Text("Скопировать") }, onClick = {
                        menu = false
                        onCopy()
                    })
                }
                if (failure != null) {
                    DropdownMenuItem(
                        text = { Text("Повторить") },
                        leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                        onClick = {
                            menu = false
                            onFailedTap()
                        }
                    )
                }
                if (message.fromMe) {
                    DropdownMenuItem(text = { Text(if (message.isLocal) "Не отправлять" else "Удалить", color = Theme.record) }, onClick = {
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
            .background(Theme.ink.copy(alpha = if (onMine) 0.16f else 0.07f))
            .padding(start = 6.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(3.dp).height(32.dp).clip(RoundedCornerShape(2.dp)).background(if (onMine) Theme.ink else Theme.accent))
        Spacer(Modifier.width(8.dp))
        quote.image?.let {
            RemoteImage(it, Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)))
            Spacer(Modifier.width(8.dp))
        }
        Column {
            Text(name, color = if (onMine) Theme.ink else Theme.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            // Длинная цитата не раздувает пузырь — обрезается.
            Text(
                quote.preview,
                color = Theme.ink.copy(alpha = 0.75f),
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
        // Фото на весь экран не сжимается при касании — только закрывается.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
        ) {
            RemoteImage(id, Modifier.fillMaxSize().popIn(), ContentScale.Fit)
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

// MARK: - Голосовые

/// Пузырь голосового: ▶/❚❚, волна (проигранное — ярче), время, скорость
/// и точка, пока его не прослушали. Нажатие на волну включает с этого места,
/// а пока играет — палец по волне перематывает. Прогресс обновляется каждый
/// кадр, но читается только в отрисовке волны.
@Composable
internal fun VoiceMessageContent(
    voice: SocialVoice,
    fromMe: Boolean,
    isLocal: Boolean,
    heard: Boolean = true,
    onListen: () -> Unit = {}
) {
    val context = LocalContext.current
    val playing = VoicePlayer.playingId == voice.id
    val loading = VoicePlayer.loadingId == voice.id
    val failed = VoicePlayer.failedId == voice.id
    val progress = remember { mutableFloatStateOf(0f) }
    var positionMs by remember { mutableLongStateOf(0L) }
    /// Куда тянут пальцем по волне, от 0 до 1.
    var scrub by remember { mutableStateOf<Float?>(null) }
    val currentListen by rememberUpdatedState(onListen)
    LaunchedEffect(playing) {
        if (!playing) {
            progress.floatValue = 0f
            positionMs = 0L
            return@LaunchedEffect
        }
        while (true) {
            withFrameMillis { }
            progress.floatValue = VoicePlayer.progress(voice.id)
            val position = VoicePlayer.positionMs(voice.id)
            if (position / 1000 != positionMs / 1000) positionMs = position
        }
    }
    Row(Modifier.padding(vertical = 2.dp).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(38.dp)
                .pressable(enabled = !isLocal) {
                    if (!playing) onListen()
                    VoicePlayer.toggle(context, voice)
                }
                .clip(CircleShape)
                .background(if (fromMe) Color.White else Theme.accent),
            contentAlignment = Alignment.Center
        ) {
            val tint = if (fromMe) Theme.accent else Color.White
            if (loading || isLocal) {
                CircularProgressIndicator(color = tint, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            } else {
                Icon(
                    when {
                        failed -> Icons.Filled.Refresh
                        playing -> Icons.Filled.Pause
                        else -> Icons.Filled.PlayArrow
                    },
                    contentDescription = if (playing) "Пауза" else "Слушать голосовое",
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.width(150.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            VoiceWaveform(
                voice.levels,
                { scrub ?: progress.floatValue },
                fromMe,
                Modifier
                    .size(width = 150.dp, height = 24.dp)
                    .pointerInput(voice.id, isLocal) {
                        if (isLocal) return@pointerInput
                        detectTapGestures { offset ->
                            val at = (offset.x / size.width).coerceIn(0f, 1f)
                            if (VoicePlayer.playingId != voice.id) currentListen()
                            VoicePlayer.seek(context, voice, at)
                        }
                    }
                    .pointerInput(voice.id, playing) {
                        if (!playing) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragStart = { scrub = (it.x / size.width).coerceIn(0f, 1f) },
                            onDragEnd = {
                                scrub?.let { VoicePlayer.seek(context, voice, it) }
                                scrub = null
                            },
                            onDragCancel = { scrub = null }
                        ) { change, _ ->
                            change.consume()
                            scrub = (change.position.x / size.width).coerceIn(0f, 1f)
                        }
                    }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val shown = scrub?.let { (it * voice.durationMs).toLong() } ?: if (playing) positionMs else voice.durationMs.toLong()
                Text(
                    SocialVoice.format(shown),
                    color = Theme.ink.copy(alpha = 0.7f),
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")
                )
                if (!heard && !isLocal) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (fromMe) Color.White else Theme.accent).semantics { contentDescription = "Не прослушано" })
                }
                Spacer(Modifier.weight(1f))
                if (playing) {
                    val rate = VoicePlayer.rate
                    Box(
                        Modifier
                            .height(18.dp)
                            .pressable { VoicePlayer.cycleRate(context) }
                            .clip(CircleShape)
                            .background(if (fromMe) Color.White else Theme.accent.copy(alpha = 0.85f))
                            .padding(horizontal = 6.dp)
                            .popIn(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            when (rate) {
                                1f -> "1×"
                                2f -> "2×"
                                else -> "1,5×"
                            },
                            color = if (fromMe) Theme.accent else Color.White,
                            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Black, fontFeatureSettings = "tnum")
                        )
                    }
                }
            }
        }
    }
}

/// Столбики громкости. Проигранная часть — яркая, остальная — приглушённая.
@Composable
internal fun VoiceWaveform(levels: List<Float>, progress: () -> Float, fromMe: Boolean, modifier: Modifier = Modifier) {
    val played = if (fromMe) Color.White else Theme.accent
    val rest = (if (fromMe) Color.White else Theme.ink).copy(alpha = if (fromMe) 0.45f else 0.35f)
    Canvas(modifier) {
        if (levels.isEmpty()) return@Canvas
        val gap = 2.dp.toPx()
        val count = levels.size
        val width = ((size.width - gap * (count - 1)) / count).coerceAtLeast(1.5.dp.toPx())
        val minHeight = 3.dp.toPx()
        val done = progress()
        levels.forEachIndexed { index, level ->
            val height = maxOf(minHeight, level * size.height)
            drawRoundRect(
                if (index.toFloat() / count < done) played else rest,
                topLeft = Offset(index * (width + gap), (size.height - height) / 2),
                size = Size(width, height),
                cornerRadius = CornerRadius(width / 2)
            )
        }
    }
}

/// Вместо поля ввода, пока идёт запись: красная точка, время, живая волна
/// и «‹ отмена», которая гаснет по мере того, как палец уходит влево.
/// Отдельная функция — 20 раз в секунду перерисовывается только она.
@Composable
private fun VoiceRecordingStrip(recorder: VoiceRecorder, drag: () -> Float, cancelPx: Float, modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "recording")
    val dot by pulse.animateFloat(1f, 0.3f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "recordingDot")
    Row(
        modifier
            .height(44.dp)
            .chatGlass(CircleShape)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).graphicsLayer { alpha = dot }.background(Theme.record, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(
            SocialVoice.format(recorder.elapsedMs),
            color = Theme.ink,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
        )
        Spacer(Modifier.width(10.dp))
        VoiceWaveform(recorder.recentLevels.toList(), { 1f }, fromMe = !Theme.isLight, modifier = Modifier.width(90.dp).height(22.dp))
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.graphicsLayer {
                val pulled = (-drag() / cancelPx).coerceIn(0f, 1f)
                alpha = 1f - pulled
                translationX = drag() * 0.4f
            },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, tint = Theme.ink.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
            Text("отмена", color = Theme.ink.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/// Микрофон: зажал — пишет, отпустил — ушло, увёл влево — отменено. Пока
/// пишет, кнопка крупнее, красная и едет за пальцем.
@Composable
private fun MicButton(
    recording: Boolean,
    drag: () -> Float,
    onPress: () -> Boolean,
    onDrag: (Float) -> Boolean,
    onRelease: (cancelled: Boolean) -> Unit
) {
    val scale by animateFloatAsState(if (recording) 1.3f else 1f, Motion.bouncy(), label = "mic")
    val currentPress by rememberUpdatedState(onPress)
    val currentDrag by rememberUpdatedState(onDrag)
    val currentRelease by rememberUpdatedState(onRelease)
    Box(
        Modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = drag()
            }
            .then(if (recording) Modifier.clip(CircleShape).background(Theme.record) else Modifier.chatGlass(CircleShape))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    if (!currentPress()) {
                        waitForUpOrCancellation()
                        return@awaitEachGesture
                    }
                    var cancelled = false
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        if (currentDrag((change.position.x - down.position.x).coerceAtMost(0f))) {
                            cancelled = true
                            break
                        }
                    }
                    currentRelease(cancelled)
                }
            }
            .semantics { contentDescription = "Голосовое: удерживай и говори" },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Mic, contentDescription = null, tint = Theme.ink, modifier = Modifier.size(20.dp))
    }
}

/// Новое сообщение вылетает: своё — из поля ввода снизу справа, друга — слева,
/// с лёгким отскоком. Только слоем: соседи не перекомпоновываются.
private fun Modifier.bubblePop(play: Boolean, fromMe: Boolean): Modifier = composed {
    val progress = remember { Animatable(if (play) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) progress.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 420f))
    }
    graphicsLayer {
        val p = progress.value
        transformOrigin = TransformOrigin(if (fromMe) 1f else 0f, 1f)
        val scale = 0.5f + 0.5f * p
        scaleX = scale
        scaleY = scale
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 26.dp.toPx()
    }
}
