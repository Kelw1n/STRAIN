package com.texasprogram.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.ProgramInput
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.service.BroProfileData
import com.texasprogram.app.service.BroTrackerService
import com.texasprogram.app.service.QRCodeService
import kotlinx.coroutines.launch

@Composable
fun BroTrackerScreen(
    profile: ProgramProfile,
    onCopyProgram: (ProgramProfile) -> Unit,
    onClose: () -> Unit,
    contentPadding: PaddingValues
) {
    val context = LocalContext.current
    val service = remember { BroTrackerService(context) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var showMyQR by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var addInputText by remember { mutableStateOf("") }
    var selectedBuddyForProgram by remember { mutableStateOf<BroProfileData?>(null) }
    var copyNotice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        service.syncMyProfile(profile)
        service.refreshBuddies()
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "title") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Theme.surfaceSoft)
                            .pressable(onClick = onClose),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад", tint = Theme.textPrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("Бро-трекер", color = Theme.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Theme.surfaceSoft)
                            .pressable { showMyQR = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.QrCode, contentDescription = "Мой QR", tint = Theme.accent, modifier = Modifier.size(20.dp))
                    }
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Theme.surfaceSoft)
                            .pressable { showAddDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Добавить бро", tint = Theme.accent, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        // Мой профиль / карточка
        item(key = "my_status") {
            CardView(Modifier.appearIn(0)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Theme.accentGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                profile.name.ifBlank { "Ты" },
                                color = Theme.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Theme.success.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Онлайн", color = Theme.success, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            "Неделя ${profile.currentWeek} · ${profile.programKind.title}",
                            color = Theme.textSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Theme.accent.copy(alpha = 0.12f))
                            .pressable { showMyQR = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.QrCode, contentDescription = null, tint = Theme.accent, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("QR", color = Theme.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Список друзей
        item(key = "crew_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Твоя банда (${service.buddies.size})",
                    color = Theme.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { showAddDialog = true }) {
                    Text("+ Добавить бро", color = Theme.accent, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (service.buddies.isEmpty()) {
            item(key = "empty_crew") {
                CardView(Modifier.appearIn(1)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Filled.QrCodeScanner,
                            contentDescription = null,
                            tint = Theme.textTertiary,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            "Пока нет добавленных друзей",
                            color = Theme.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Введи код бро или отсканируй его QR-код, чтобы отслеживать прогресс тренировок в реальном времени.",
                            color = Theme.textSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SecondaryButton("Ввести код", icon = Icons.Filled.Add) {
                                showAddDialog = true
                            }
                            SecondaryButton("Мой QR", icon = Icons.Filled.QrCode) {
                                showMyQR = true
                            }
                        }
                    }
                }
            }
        } else {
            items(service.buddies, key = { it.broId }) { buddy ->
                BuddyCard(
                    buddy = buddy,
                    onViewProgram = { selectedBuddyForProgram = buddy },
                    onDelete = { service.removeBuddy(buddy.broId) }
                )
            }
        }
    }

    // Диалог показа своего QR
    if (showMyQR) {
        val qrLink = "strain://bro/${service.myBroId}"
        val qrBitmap = remember(service.myBroId) {
            QRCodeService.generateQRCode(qrLink, 400)
        }

        AlertDialog(
            onDismissRequest = { showMyQR = false },
            confirmButton = {
                TextButton(onClick = { showMyQR = false }) {
                    Text("Готово", color = Theme.accent)
                }
            },
            title = {
                Text("Твой QR-код бро", color = Theme.textPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (qrBitmap != null) {
                        Box(
                            Modifier
                                .size(220.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "QR Code",
                                modifier = Modifier.size(196.dp)
                            )
                        }
                    }
                    Text(
                        "Пусть твой друг наведёт сканер или введёт код:",
                        color = Theme.textSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Theme.surfaceSoft)
                            .pressable {
                                clipboard.setText(AnnotatedString(qrLink))
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            service.myBroId.ifBlank { "Генерация..." },
                            color = Theme.accent,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Скопировать", tint = Theme.textSecondary, modifier = Modifier.size(14.dp))
                    }
                }
            }
        )
    }

    // Диалог ввода кода друга
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Добавить бро", color = Theme.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Введи ID друга или ссылку (например: strain://bro/xyz...):",
                        color = Theme.textSecondary,
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = addInputText,
                        onValueChange = { addInputText = it },
                        placeholder = { Text("ID или ссылка бро") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val input = addInputText
                        showAddDialog = false
                        addInputText = ""
                        scope.launch {
                            service.addBuddy(input)
                        }
                    }
                ) {
                    Text("Добавить", color = Theme.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Отмена", color = Theme.textSecondary)
                }
            }
        )
    }

    // Просмотр программы друга
    val currentBuddy = selectedBuddyForProgram
    if (currentBuddy != null) {
        AlertDialog(
            onDismissRequest = { selectedBuddyForProgram = null },
            title = {
                Column {
                    Text(currentBuddy.name, color = Theme.textPrimary, fontWeight = FontWeight.Bold)
                    Text(
                        "${currentBuddy.programTitle} · Неделя ${currentBuddy.currentWeek}",
                        color = Theme.textSecondary,
                        fontSize = 12.sp
                    )
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Theme.surfaceSoft)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            if (currentBuddy.squat5RM > 0) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Присед", color = Theme.textSecondary, fontSize = 11.sp)
                                    Text("${currentBuddy.squat5RM.toInt()} кг", color = Theme.textPrimary, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (currentBuddy.bench5RM > 0) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Жим", color = Theme.textSecondary, fontSize = 11.sp)
                                    Text("${currentBuddy.bench5RM.toInt()} кг", color = Theme.textPrimary, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (currentBuddy.deadlift5RM > 0) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Тяга", color = Theme.textSecondary, fontSize = 11.sp)
                                    Text("${currentBuddy.deadlift5RM.toInt()} кг", color = Theme.textPrimary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (currentBuddy.programDays.isEmpty()) {
                        item {
                            Text("Дни программы не загружены", color = Theme.textSecondary, fontSize = 12.sp)
                        }
                    } else {
                        items(currentBuddy.programDays) { day ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Theme.surfaceSoft)
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "Неделя ${day.week} · ${day.title}",
                                    color = Theme.accent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                day.exercises.forEach { ex ->
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(ex.name, color = Theme.textPrimary, fontSize = 12.sp)
                                        Text(
                                            "${if (ex.sets > 0) "${ex.sets}×${ex.reps}" else ex.reps} · ${ex.weight}",
                                            color = Theme.textSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newProf = ProgramProfile(
                            input = ProgramInput(
                                squat5RM = if (currentBuddy.squat5RM > 0) currentBuddy.squat5RM else 100.0,
                                bench5RM = if (currentBuddy.bench5RM > 0) currentBuddy.bench5RM else 100.0,
                                deadlift5RM = if (currentBuddy.deadlift5RM > 0) currentBuddy.deadlift5RM else 100.0
                            )
                        ).copy(name = "${currentBuddy.name} (копия)")
                        onCopyProgram(newProf)
                        selectedBuddyForProgram = null
                        copyNotice = "Программа друга «${currentBuddy.name}» успешно скопирована в твои профили!"
                    }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Скопировать себе", color = Theme.accent, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedBuddyForProgram = null }) {
                    Text("Закрыть", color = Theme.textSecondary)
                }
            }
        )
    }

    // Уведомление о копировании
    val notice = copyNotice
    if (notice != null) {
        AlertDialog(
            onDismissRequest = { copyNotice = null },
            title = { Text("Успешно", color = Theme.textPrimary, fontWeight = FontWeight.Bold) },
            text = { Text(notice, color = Theme.textSecondary) },
            confirmButton = {
                TextButton(onClick = { copyNotice = null }) {
                    Text("ОК", color = Theme.accent)
                }
            }
        )
    }
}

@Composable
private fun BuddyCard(
    buddy: BroProfileData,
    onViewProgram: () -> Unit = {},
    onDelete: () -> Unit
) {
    CardView {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(buddy.name, color = Theme.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (buddy.isRecentlyActive) Theme.success else Color.Gray)
                        )
                    }
                    Text(
                        buddy.statusDescription,
                        color = if (buddy.isRecentlyActive) Theme.success else Theme.textTertiary,
                        fontSize = 11.sp
                    )
                }

                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .pressable(onClick = onDelete),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Удалить", tint = Theme.textTertiary, modifier = Modifier.size(16.dp))
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Theme.hairline)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("ТЕКУЩИЙ ЭТАП", color = Theme.textTertiary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("Неделя ${buddy.currentWeek} · День ${buddy.currentDay}", color = Theme.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("ПРОГРАММА", color = Theme.textTertiary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(buddy.programTitle, color = Theme.textPrimary, fontSize = 13.sp)
                }
            }

            if (buddy.recentLifts.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Theme.surfaceSoft)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    buddy.recentLifts.take(2).forEach { lift ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(lift.name, color = Theme.textPrimary, fontSize = 11.sp)
                            Text(lift.prescription, color = Theme.textSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .pressable(onClick = onViewProgram)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Посмотреть программу", color = Theme.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Theme.accent, modifier = Modifier.size(16.dp))
            }
        }
    }
}
