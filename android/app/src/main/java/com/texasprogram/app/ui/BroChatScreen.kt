package com.texasprogram.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.texasprogram.app.model.BroChatMessage
import com.texasprogram.app.model.BroMessageType
import com.texasprogram.app.model.BroProfileData
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.WorkoutSharePayload
import com.texasprogram.app.service.BroTrackerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import kotlin.math.min

@Composable
fun BroChatScreen(
    buddy: BroProfileData,
    profile: ProgramProfile,
    service: BroTrackerService,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var messages by remember(buddy.broId) { mutableStateOf(service.getMessages(buddy.broId)) }
    var inputText by remember { mutableStateOf("") }
    var showingWorkoutDialog by remember { mutableStateOf(false) }
    var fullScreenBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isSending by remember { mutableStateOf(false) }

    val quickPhrases = remember {
        listOf(
            "Я в зале 🏋️",
            "Накинь блины 🥞",
            "Подстрахуй 🤝",
            "Закончил день 🏁",
            "Памп бешеный 🔥"
        )
    }

    // Фото-пикер из галереи
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val stream = context.contentResolver.openInputStream(uri)
                    val original = BitmapFactory.decodeStream(stream)
                    stream?.close()
                    if (original != null) {
                        val resized = resizeBitmap(original, 800)
                        val out = ByteArrayOutputStream()
                        resized.compress(Bitmap.CompressFormat.JPEG, 60, out)
                        val bytes = out.toByteArray()
                        val base64 = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)

                        isSending = true
                        service.sendMessage(
                            toBuddyId = buddy.broId,
                            text = "📷 Фотография с тренировки",
                            type = BroMessageType.PHOTO,
                            photoBase64 = base64,
                            profile = profile
                        )
                        messages = service.getMessages(buddy.broId)
                        isSending = false
                    }
                } catch (_: Exception) {
                    isSending = false
                }
            }
        }
    }

    // Автоматическое обновление сообщений и онлайна каждые 3 секунды
    LaunchedEffect(buddy.broId) {
        messages = service.getMessages(buddy.broId)
        while (true) {
            delay(3000)
            service.refreshBuddies()
            messages = service.getMessages(buddy.broId)
        }
    }

    // Автоскролл к последнему сообщению
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    AppBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = statusBarPadding, bottom = navBarPadding)
        ) {
            // Шапка диалога
            val currentBuddy = service.buddies.firstOrNull { it.broId == buddy.broId } ?: buddy
            ChatHeader(buddy = currentBuddy, onBack = onBack)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Theme.hairline)
            )

            // Список сообщений
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (messages.isEmpty()) {
                    item(key = "empty_chat") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.LocalFireDepartment,
                                contentDescription = null,
                                tint = Theme.accent,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                "Здесь начнётся ваш разговор!",
                                color = Theme.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Поделись результатом подхода или черкани пару строк бро.",
                                color = Theme.textSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    items(messages, key = { it.id }) { msg ->
                        val isMe = msg.senderId == service.myBroId
                        ChatMessageBubble(
                            message = msg,
                            isMe = isMe,
                            onPhotoClick = { fullScreenBitmap = it }
                        )
                    }
                }
            }

            // Быстрые фразы
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickPhrases.forEach { phrase ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Theme.surfaceSoft)
                            .border(1.dp, Theme.hairline, RoundedCornerShape(16.dp))
                            .clickable {
                                scope.launch {
                                    service.sendMessage(
                                        toBuddyId = buddy.broId,
                                        text = phrase,
                                        type = BroMessageType.TEXT,
                                        profile = profile
                                    )
                                    messages = service.getMessages(buddy.broId)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            phrase,
                            color = Theme.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Theme.hairline)
            )

            // Нижняя панель ввода
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Theme.surface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Кнопка фото
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Theme.surfaceSoft)
                        .clickable { photoPicker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.CameraAlt,
                        contentDescription = "Фото",
                        tint = Theme.accent,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Кнопка шеринга подхода
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Theme.surfaceSoft)
                        .clickable { showingWorkoutDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.LocalFireDepartment,
                        contentDescription = "Подход",
                        tint = Theme.warning,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Поле ввода текста
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Theme.surfaceSoft)
                        .border(1.dp, Theme.hairline, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    if (inputText.isBlank()) {
                        Text("Сообщение бро...", color = Theme.textTertiary, fontSize = 14.sp)
                    }
                    BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        textStyle = TextStyle(color = Theme.textPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(Theme.accent),
                        singleLine = false,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            val text = inputText.trim()
                            if (text.isNotEmpty()) {
                                inputText = ""
                                scope.launch {
                                    service.sendMessage(
                                        toBuddyId = buddy.broId,
                                        text = text,
                                        type = BroMessageType.TEXT,
                                        profile = profile
                                    )
                                    messages = service.getMessages(buddy.broId)
                                }
                            }
                        })
                    )
                }

                // Кнопка отправки
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (inputText.isNotBlank()) Theme.accentGradient else SolidColor(Theme.surfaceSoft))
                        .clickable(enabled = inputText.isNotBlank() && !isSending) {
                            val text = inputText.trim()
                            if (text.isNotEmpty()) {
                                inputText = ""
                                scope.launch {
                                    service.sendMessage(
                                        toBuddyId = buddy.broId,
                                        text = text,
                                        type = BroMessageType.TEXT,
                                        profile = profile
                                    )
                                    messages = service.getMessages(buddy.broId)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Send,
                        contentDescription = "Отправить",
                        tint = if (inputText.isNotBlank()) Color.White else Theme.textTertiary,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }

    // Диалог отправки результата подхода
    if (showingWorkoutDialog) {
        WorkoutShareDialog(
            profile = profile,
            onDismiss = { showingWorkoutDialog = false },
            onShare = { payload ->
                showingWorkoutDialog = false
                scope.launch {
                    val text = "${if (payload.isPR) "🔥 НОВЫЙ РЕКОРД!" else "💥 Подход выполнен:"} ${payload.exercise} ${payload.weight} ${payload.sets}×${payload.reps}"
                    service.sendMessage(
                        toBuddyId = buddy.broId,
                        text = text,
                        type = BroMessageType.WORKOUT_RESULT,
                        workoutPayload = payload,
                        profile = profile
                    )
                    messages = service.getMessages(buddy.broId)
                }
            }
        )
    }

    // Полноэкранный просмотр фото
    if (fullScreenBitmap != null) {
        Dialog(
            onDismissRequest = { fullScreenBitmap = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                Image(
                    bitmap = fullScreenBitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                Box(
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.TopEnd)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { fullScreenBitmap = null },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ChatHeader(buddy: BroProfileData, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Theme.surfaceSoft)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Назад", tint = Theme.textPrimary, modifier = Modifier.size(18.dp))
        }

        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Theme.accentGradient),
            contentAlignment = Alignment.Center
        ) {
            Text(
                buddy.name.take(1).uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    buddy.name,
                    color = Theme.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (buddy.isOnline) Theme.success else if (buddy.isRecentlyActive) Theme.warning else Color.Gray)
                )
            }
            Text(
                buddy.statusDescription,
                color = if (buddy.isOnline) Theme.success else Theme.textSecondary,
                fontSize = 11.sp
            )
        }

        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "ПРОГРАММА",
                color = Theme.textTertiary,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Нед. ${buddy.currentWeek} · Дн. ${buddy.currentDay}",
                color = Theme.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: BroChatMessage,
    isMe: Boolean,
    onPhotoClick: (Bitmap) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier.width(min(280, 320).dp),
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
        ) {
            if (!isMe) {
                Text(
                    message.senderName,
                    color = Theme.textSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )
            }

            when (message.type) {
                BroMessageType.TEXT -> {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isMe) Theme.accentGradient else SolidColor(Theme.surfaceSoft))
                            .border(1.dp, if (isMe) Color.Transparent else Theme.hairline, RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Text(
                            message.text,
                            color = if (isMe) Color.White else Theme.textPrimary,
                            fontSize = 14.sp
                        )
                    }
                }

                BroMessageType.WORKOUT_RESULT -> {
                    val p = message.workoutPayload
                    if (p != null) {
                        WorkoutResultBubble(payload = p, isMe = isMe)
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Theme.surfaceSoft)
                                .padding(12.dp)
                        ) {
                            Text(message.text, color = Theme.textPrimary, fontSize = 13.sp)
                        }
                    }
                }

                BroMessageType.PHOTO -> {
                    val bmp = remember(message.photoBase64) { decodeBase64Bitmap(message.photoBase64) }
                    if (bmp != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Theme.surfaceSoft)
                                .border(1.dp, Theme.hairline, RoundedCornerShape(14.dp))
                                .clickable { onPhotoClick(bmp) }
                                .padding(4.dp)
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(200.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    } else {
                        Text(message.text, color = Theme.textSecondary, fontSize = 12.sp)
                    }
                }
            }

            Text(
                message.timeFormatted,
                color = Theme.textTertiary,
                fontSize = 9.sp,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
            )
        }
    }
}

@Composable
private fun WorkoutResultBubble(payload: WorkoutSharePayload, isMe: Boolean) {
    val borderColor = if (payload.isPR) Theme.warning else Theme.accent.copy(alpha = 0.5f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Theme.surface)
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (payload.isPR) Icons.Filled.EmojiEvents else Icons.Filled.LocalFireDepartment,
                contentDescription = null,
                tint = if (payload.isPR) Theme.warning else Theme.accent,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                if (payload.isPR) "ЛИЧНЫЙ РЕКОРД" else "РЕЗУЛЬТАТ ПОДХОДА",
                color = if (payload.isPR) Theme.warning else Theme.accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.weight(1f))
            Text(
                "Нед. ${payload.week} · Дн. ${payload.day}",
                color = Theme.textSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Text(
            payload.exercise,
            color = Theme.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                payload.weight,
                color = Theme.accent,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black
            )
            Text("·", color = Theme.textSecondary)
            Text(
                "${payload.sets}×${payload.reps}",
                color = Theme.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun WorkoutShareDialog(
    profile: ProgramProfile,
    onDismiss: () -> Unit,
    onShare: (WorkoutSharePayload) -> Unit
) {
    var exercise by remember { mutableStateOf("Жим лёжа") }
    var weight by remember { mutableStateOf("100 кг") }
    var sets by remember { mutableStateOf("5") }
    var reps by remember { mutableStateOf("5") }
    var isPR by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val weekPlan = profile.workoutPlan.weeks.firstOrNull { it.number == profile.currentWeek }
        val firstEx = weekPlan?.days?.firstOrNull()?.exercises?.firstOrNull()
        if (firstEx != null) {
            exercise = firstEx.name
            weight = firstEx.load.displayText
            sets = firstEx.sets.toString()
            reps = firstEx.reps
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Что поднял, бро? 💥", color = Theme.textPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = exercise,
                    onValueChange = { exercise = it },
                    label = { Text("Упражнение") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("Рабочий вес (напр. 120 кг)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sets,
                        onValueChange = { sets = it },
                        label = { Text("Подходы") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = reps,
                        onValueChange = { reps = it },
                        label = { Text("Повторения") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isPR, onCheckedChange = { isPR = it })
                    Text("🔥 Личный рекорд (PR)?", color = Theme.textPrimary, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val activeDay = profile.workoutPlan.weeks
                    .firstOrNull { it.number == profile.currentWeek }
                    ?.days?.firstOrNull { !profile.isCompleted(profile.currentWeek, it.number) }
                    ?.number ?: 1
                onShare(
                    WorkoutSharePayload(
                        exercise = exercise.ifBlank { "Упражнение" },
                        weight = weight.ifBlank { "100 кг" },
                        sets = sets.ifBlank { "1" },
                        reps = reps.ifBlank { "5" },
                        isPR = isPR,
                        week = profile.currentWeek,
                        day = activeDay
                    )
                )
            }) {
                Text("Закинуть в чат", color = Theme.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = Theme.textSecondary)
            }
        }
    )
}

private fun resizeBitmap(bitmap: Bitmap, maxDim: Int): Bitmap {
    val w = bitmap.width
    val h = bitmap.height
    val ratio = min(maxDim.toFloat() / w, maxDim.toFloat() / h)
    if (ratio >= 1.0f) return bitmap
    return Bitmap.createScaledBitmap(bitmap, (w * ratio).toInt(), (h * ratio).toInt(), true)
}

private fun decodeBase64Bitmap(base64: String?): Bitmap? {
    if (base64.isNullOrBlank()) return null
    return try {
        val clean = if (base64.contains(",")) base64.substringAfter(",") else base64
        val bytes = Base64.decode(clean, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: Exception) {
        null
    }
}
