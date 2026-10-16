package com.texasprogram.app.ui

import android.content.ClipData
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
private fun ChatRow(chat: SocialChat, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(onClick = onOpen).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(chat.user.avatar, chat.user.username, 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("@${chat.user.username}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(SocialFormat.ago(chat.last.instant), color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (chat.last.fromMe) "Вы: " else "") + chat.last.text,
                    color = Color.White.copy(alpha = if (chat.unread > 0) 0.9f else 0.5f),
                    fontSize = 14.sp,
                    fontWeight = if (chat.unread > 0) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
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

/// Переписка с одним человеком. Новые сообщения подтягиваются каждые несколько
/// секунд, пока экран открыт; панель вкладок на это время прячется.
@Composable
fun ChatScreen(username: String, contentPadding: PaddingValues, navigator: SocialNavigator) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var user by remember { mutableStateOf<SocialUserShort?>(null) }
    val messages = remember { mutableStateListOf<SocialMessage>() }
    var older by remember { mutableStateOf<Double?>(null) }
    var loading by remember { mutableStateOf(true) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    DisposableEffect(Unit) {
        SocialService.hidesTabBar = true
        onDispose { SocialService.hidesTabBar = false }
    }

    /// Сервер отдаёт новые первыми, на экране — старые сверху.
    fun merge(incoming: List<SocialMessage>) {
        val known = messages.map { it.id }.toSet()
        val fresh = incoming.filter { it.id !in known }
        if (fresh.isEmpty()) return
        val all = (messages + fresh).sortedBy { it.createdAt }
        messages.clear()
        messages.addAll(all)
    }

    suspend fun loadLatest() {
        loading = true
        try {
            val page = SocialService.messages(username)
            user = page.user
            older = page.next
            merge(page.messages)
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
            delay(4_000)
            val latest = messages.lastOrNull { !it.isLocal }
            if (latest == null) {
                loadLatest()
                continue
            }
            runCatching { SocialService.messages(username, after = latest.createdAt) }.onSuccess { page ->
                if (page.messages.isNotEmpty()) {
                    merge(page.messages)
                    SocialService.refreshUnread()
                }
            }
        }
    }

    // Новое сообщение — список доезжает до него.
    LaunchedEffect(messages.lastOrNull()?.id) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size)
    }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || sending) return
        val clientId = UUID.randomUUID().toString().lowercase()
        val local = SocialMessage(id = "local-$clientId", text = text, fromMe = true, createdAt = System.currentTimeMillis().toDouble())
        draft = ""
        sending = true
        messages.add(local)
        scope.launch {
            try {
                val sent = SocialService.sendMessage(username, text, clientId)
                messages.remove(local)
                merge(listOf(sent))
                error = null
            } catch (e: SocialException) {
                messages.remove(local)
                if (draft.isEmpty()) draft = text
                error = e.message
            } finally {
                sending = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(top = contentPadding.calculateTopPadding())
            .navigationBarsPadding()
            .imePadding()
    ) {
        SocialTopBar("@$username", onBack = { navigator.pop() }) {
            Box(Modifier.pressable { navigator.push(SocialRoute.User(username)) }) {
                Avatar(user?.avatar, username, 34.dp)
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
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
                                    merge(page.messages)
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
                        onCopy = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Сообщение", message.text))
                        },
                        onDelete = {
                            scope.launch {
                                try {
                                    SocialService.deleteMessage(message.id)
                                    messages.remove(message)
                                } catch (e: SocialException) {
                                    error = e.message
                                }
                            }
                        }
                    )
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().background(Color(0xFF0E0E10)).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            error?.let { Text(it, color = Theme.warning, fontSize = 12.sp) }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF222222))
                        .padding(horizontal = 14.dp, vertical = 11.dp)
                ) {
                    if (draft.isEmpty()) Text("Сообщение", color = Color.White.copy(alpha = 0.4f), fontSize = 16.sp)
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it.take(2000) },
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                        cursorBrush = SolidColor(Theme.accent),
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                val canSend = !sending && draft.isNotBlank()
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (canSend) Color.White else Color(0xFF4D4D4D))
                        .pressable(enabled = canSend) { send() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Отправить", tint = Color.Black)
                }
            }
        }
    }
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
private fun MessageBubble(message: SocialMessage, onCopy: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val time = message.instant.atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromMe) Arrangement.End else Arrangement.Start) {
        Box {
            Column(
                Modifier
                    .widthIn(max = 290.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (message.fromMe) Theme.accent.copy(alpha = 0.9f) else Color(0xFF292929))
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                        onLongClick = { menu = true }
                    )
                    .padding(horizontal = 13.dp, vertical = 8.dp),
                horizontalAlignment = if (message.fromMe) Alignment.End else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(message.text, color = Color.White, fontSize = 16.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Скопировать") }, onClick = {
                    menu = false
                    onCopy()
                })
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
