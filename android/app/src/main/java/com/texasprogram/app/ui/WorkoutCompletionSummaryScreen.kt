package com.texasprogram.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlusOne
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.service.ScheduledWorkout
import com.texasprogram.app.service.WorkoutSessionManager
import com.texasprogram.app.model.SocialPostDraft
import com.texasprogram.app.model.SocialWorkout
import com.texasprogram.app.service.SocialService
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PersonAdd


@Composable
fun WorkoutCompletionSummaryScreen(
    profile: ProgramProfile,
    workout: ScheduledWorkout,
    onUpdateProfile: (ProgramProfile) -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    var workoutTitle by remember { mutableStateOf<String>(workout.fullTitle) }
    var workoutDescription by remember {
        mutableStateOf<String>(
            if (WorkoutSessionManager.noteText.isNotEmpty()) WorkoutSessionManager.noteText
            else profile.note(workout.week, workout.day.number)
        )
    }
    /// Оценка усилия 1–10; пусто — не ставили.
    var effortScore by remember { mutableStateOf<Int?>(null) }
    // Фото и файл снимка переживают пересоздание экрана, пока открыта камера.
    val selectedImages = rememberSaveable(
        saver = listSaver<SnapshotStateList<Uri>, Uri>(save = { it.toList() }, restore = { it.toMutableStateList() })
    ) { mutableStateListOf() }

    var durationSeconds by remember { mutableIntStateOf(maxOf(WorkoutSessionManager.elapsedSeconds, 60)) }
    var showingDurationPicker by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(5)
    ) { uris ->
        // Добавляем к уже выбранным и снятым, а не заменяем их.
        uris.forEach { if (selectedImages.size < 5 && it !in selectedImages) selectedImages.add(it) }
    }
    val context = LocalContext.current
    // Снимок сразу из экрана итогов: не надо выходить в «Камеру» и искать фото в галерее.
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null && selectedImages.size < 5) selectedImages.add(uri)
    }
    fun openCamera() {
        val uri = cameraFileUri(context)
        cameraUri = uri
        runCatching { cameraLauncher.launch(uri) }
    }
    fun openGallery() {
        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    /// Публиковать ли тренировку в ленту — по умолчанию да, если есть профиль.
    var publishToFeed by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = topInset + 8.dp, bottom = 120.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Шапка: кнопка назад + Заголовок
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDismiss()
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Твоя тренировка",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Время, усилие и заметка — в историю",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.size(38.dp))
            }

            // ФОТО И ВИДЕО (0/5)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ФОТО (${selectedImages.size}/5)",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF161616))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedImages.isEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Добавь фото к тренировке",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "до 5 штук · первое ляжет на карточку для сторис",
                                    color = Color.White.copy(alpha = 0.50f),
                                    fontSize = 13.sp
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                PhotoSourceTile(Icons.Filled.CameraAlt, "Снять", large = true, modifier = Modifier.weight(1f), onClick = ::openCamera)
                                PhotoSourceTile(Icons.Filled.PhotoLibrary, "Галерея", large = true, modifier = Modifier.weight(1f), onClick = ::openGallery)
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            selectedImages.forEachIndexed { index, uri ->
                                // Миниатюра 80 dp: 12 Мп целиком в память не тянем — на слабых
                                // телефонах пять таких фото роняли экран итогов.
                                val bitmap = remember(uri) { decodeThumbnail(context, uri, 240) }

                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF242428))
                                ) {
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(22.dp)
                                            .pressable {
                                                if (index < selectedImages.size) {
                                                    selectedImages.removeAt(index)
                                                }
                                            }
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.7f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Удалить",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                            if (selectedImages.size < 5) {
                                PhotoSourceTile(Icons.Filled.CameraAlt, "Снять", large = false, onClick = ::openCamera)
                                PhotoSourceTile(Icons.Filled.PhotoLibrary, "Галерея", large = false, onClick = ::openGallery)
                            }
                        }
                    }
                }
            }

            // НАЗВАНИЕ И ОПИСАНИЕ
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF161616))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = workoutTitle,
                            onValueChange = { workoutTitle = it },
                            placeholder = { Text("Название тренировки", color = Color.White.copy(alpha = 0.35f)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        OutlinedTextField(
                            value = workoutDescription,
                            onValueChange = { workoutDescription = it },
                            placeholder = { Text("Описание", color = Color.White.copy(alpha = 0.35f)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp),
                            minLines = 3,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.35f),
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .size(16.dp)
                        )
                    }
                }
            }

            // ВРЕМЯ
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ВРЕМЯ",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF161616))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = formatDurationRu(durationSeconds),
                                color = Color.White,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "В приложении: ${formatDurationRu(WorkoutSessionManager.elapsedSeconds)}",
                                color = Color.White.copy(alpha = 0.50f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(Modifier.weight(1f))

                        Box(
                            modifier = Modifier
                                .pressable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showingDurationPicker = true
                                }
                                .clip(CircleShape)
                                .background(Theme.accent)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Изменить",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // УСИЛИЕ
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "УСИЛИЕ",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                EffortSlider(score = effortScore, onChange = { effortScore = it })
            }

            // ЛЕНТА STRAIN
            PublishSection(publishToFeed) { publishToFeed = it }

            // КАРТОЧКА ДЛЯ СТОРИС
            ShareCardSection(
                title = workoutTitle.ifBlank { workout.fullTitle },
                durationSeconds = durationSeconds,
                photo = selectedImages.firstOrNull()
            )
        }

        // Нижняя фиксированная кнопка «ДАЛЬШЕ»
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color(0xFF000000).copy(alpha = 0.95f),
                            Color(0xFF000000)
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .pressable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val updatedProfile = if (workoutDescription.isNotEmpty()) {
                            profile.setNote(workoutDescription, workout.week, workout.day.number)
                        } else profile

                        // Пост в ленту — до завершения: потом подходы сессии уже сброшены.
                        if (publishToFeed && SocialService.isSignedIn) {
                            SocialService.enqueue(
                                context,
                                feedDraft(
                                    title = workoutTitle.ifBlank { workout.fullTitle },
                                    description = workoutDescription.trim(),
                                    durationSeconds = durationSeconds,
                                    effort = effortScore,
                                    program = profile.programKind.title
                                ),
                                selectedImages.toList()
                            )
                        }
                        WorkoutSessionManager.finishWorkout(
                            profile = updatedProfile,
                            customDuration = durationSeconds,
                            effort = EffortScale.text(effortScore),
                            onUpdateProfile = onUpdateProfile
                        )
                        WorkoutSessionManager.showingSummary = false
                    }
                    .shadow(12.dp, CircleShape, ambientColor = Color.White.copy(alpha = 0.2f))
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ДАЛЬШЕ",
                    color = Color.Black,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }

    // Диалог редактирования продолжительности тренировки
    if (showingDurationPicker) {
        var tempMinutes by remember { mutableIntStateOf(maxOf(1, durationSeconds / 60)) }

        AlertDialog(
            onDismissRequest = { showingDurationPicker = false },
            title = {
                Text(
                    text = "Время тренировки",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "Фактическая длительность: ${formatDurationRu(tempMinutes * 60)}",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Slider(
                        value = tempMinutes.toFloat(),
                        onValueChange = { tempMinutes = it.toInt() },
                        valueRange = 5f..240f,
                        steps = 46,
                        colors = SliderDefaults.colors(
                            thumbColor = Theme.accent,
                            activeTrackColor = Theme.accent
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        durationSeconds = tempMinutes * 60
                        showingDurationPicker = false
                    }
                ) {
                    Text("Применить", color = Theme.accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showingDurationPicker = false }) {
                    Text("Отмена", color = Color.White.copy(alpha = 0.6f))
                }
            },
            containerColor = Color(0xFF1C1C20)
        )
    }
}

private fun formatDurationRu(seconds: Int): String {
    val hours = seconds / 3600
    val mins = maxOf(1, (seconds % 3600) / 60)
    return if (hours > 0) "${hours}ч ${mins}м" else "${mins}м"
}

/// Фото из галереи, уменьшенное при чтении до стороны не меньше `target` пикселей.
private fun decodeThumbnail(context: android.content.Context, uri: Uri, target: Int): android.graphics.Bitmap? = runCatching {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
    context.contentResolver.openInputStream(uri)?.use {
        android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
    }
}.getOrNull()

@Composable
private fun PhotoSourceTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    large: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .then(if (large) Modifier.height(76.dp) else Modifier.size(80.dp))
            .pressable(onClick = onClick)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.07f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(if (large) 24.dp else 20.dp))
        Text(title, color = Color.White.copy(alpha = if (large) 0.9f else 0.6f), fontSize = if (large) 14.sp else 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PublishSection(checked: Boolean, onChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "ЛЕНТА",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF161616))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (SocialService.isSignedIn) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Опубликовать в ленте STRAIN", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Фото, время, объём и все подходы с весами. Друзья смогут поставить лайк и написать комментарий.",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
                androidx.compose.material3.Switch(
                    checked = checked,
                    onCheckedChange = onChange,
                    colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Theme.accent)
                )
            } else {
                Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = Theme.accent)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Создай профиль во вкладке «Профиль» — и тренировки можно будет публиковать в ленту.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
            }
        }
    }
}

/// Пост собирается до finishWorkout: после него менеджер сбрасывает подходы.
private fun feedDraft(title: String, description: String, durationSeconds: Int, effort: Int?, program: String): SocialPostDraft {
    val exercises = WorkoutSessionManager.exercises.mapNotNull { exercise ->
        val done = exercise.sets.filter { it.isDone }
        if (done.isEmpty()) null
        else SocialWorkout.Exercise(exercise.prescription.name, done.map { SocialWorkout.SetLine(it.reps, it.weight, it.isRecord) })
    }
    return SocialPostDraft(
        clientId = SocialService.newClientId(),
        createdAtMillis = System.currentTimeMillis(),
        title = title,
        description = description,
        durationSeconds = durationSeconds,
        volumeKg = WorkoutSessionManager.totalTonnage,
        sets = WorkoutSessionManager.totalCompletedSets,
        effort = effort,
        workout = SocialWorkout(program, exercises),
        // Тренировались вместе — пост выйдет «вместе с» напарником.
        together = SocialService.together.active?.id
    )
}
