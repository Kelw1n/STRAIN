package com.texasprogram.app.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.texasprogram.app.model.BroExercise
import com.texasprogram.app.model.BroLiftEntry
import com.texasprogram.app.model.BroProfileData
import com.texasprogram.app.model.BroWorkoutDay
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.TrainingLevel
import com.texasprogram.app.model.TrainingProgramKind
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
    val service = remember { BroTrackerService.getInstance(context) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var showMyQR by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var addInputText by remember { mutableStateOf("") }
    var selectedBuddyForProgram by remember { mutableStateOf<BroProfileData?>(null) }
    var copyNotice by remember { mutableStateOf<String?>(null) }
    var scanNotice by remember { mutableStateOf<String?>(null) }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            scope.launch {
                val res = service.addBuddy(result.contents)
                scanNotice = res.message
            }
        }
    }

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Theme.surfaceSoft)
                            .pressable {
                                val options = ScanOptions().apply {
                                    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                    setPrompt("Наведи камеру на QR-код бро")
                                    setBeepEnabled(true)
                                    setOrientationLocked(false)
                                }
                                scanLauncher.launch(options)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = "Сканировать QR", tint = Theme.accent, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { showAddDialog = true }) {
                        Text("+ Ввести код", color = Theme.accent, fontWeight = FontWeight.SemiBold)
                    }
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
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Theme.accent)
                                    .pressable {
                                        val options = ScanOptions().apply {
                                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                            setPrompt("Наведи камеру на QR-код бро")
                                            setBeepEnabled(true)
                                            setOrientationLocked(false)
                                        }
                                        scanLauncher.launch(options)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Сканировать QR", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Theme.surfaceSoft)
                                    .pressable { showAddDialog = true }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Ввести код", color = Theme.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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
        val qrLink = remember(profile, service.myBroId) {
            service.buildQRLink(profile)
        }
        val qrBitmap = remember(qrLink) {
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
                            val res = service.addBuddy(input)
                            scanNotice = res.message
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

    val sNotice = scanNotice
    if (sNotice != null) {
        AlertDialog(
            onDismissRequest = { scanNotice = null },
            title = { Text("Бро-трекер", color = Theme.textPrimary, fontWeight = FontWeight.Bold) },
            text = { Text(sNotice, color = Theme.textSecondary) },
            confirmButton = {
                TextButton(onClick = { scanNotice = null }) {
                    Text("ОК", color = Theme.accent)
                }
            }
        )
    }

    // Просмотр программы друга
    val currentBuddy = selectedBuddyForProgram
    if (currentBuddy != null) {
        BuddyProgramDialog(
            buddy = currentBuddy,
            onDismiss = { selectedBuddyForProgram = null },
            onCopyProgram = { newProf ->
                onCopyProgram(newProf)
                selectedBuddyForProgram = null
                copyNotice = "Программа друга «${currentBuddy.name}» успешно скопирована в твои профили!"
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
                    Text("Неделя ${buddy.currentWeek}, День ${buddy.currentDay}", color = Theme.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                    buddy.recentLifts.forEach { lift ->
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

@Composable
private fun BuddyProgramDialog(
    buddy: BroProfileData,
    onDismiss: () -> Unit,
    onCopyProgram: (ProgramProfile) -> Unit
) {
    val availableWeeks = remember(buddy.programDays) {
        val distinct = buddy.programDays.map { it.week }.distinct().sorted()
        if (distinct.isNotEmpty()) distinct else listOf(buddy.currentWeek.coerceAtLeast(1))
    }

    var selectedWeek by remember(buddy.broId) {
        mutableIntStateOf(
            if (availableWeeks.contains(buddy.currentWeek)) buddy.currentWeek
            else availableWeeks.firstOrNull() ?: 1
        )
    }

    val weekListState = rememberLazyListState()
    val daysListState = rememberLazyListState()

    // Auto-scroll the week chips to selectedWeek
    LaunchedEffect(selectedWeek) {
        val targetIdx = availableWeeks.indexOf(selectedWeek)
        if (targetIdx >= 0) {
            weekListState.animateScrollToItem(targetIdx)
        }
    }

    val daysForSelectedWeek = remember(buddy.programDays, selectedWeek) {
        buddy.programDays.filter { it.week == selectedWeek }
    }

    // Auto-scroll day list to active day when active week is selected
    LaunchedEffect(selectedWeek) {
        val activeDayIdx = daysForSelectedWeek.indexOfFirst { it.day == buddy.currentDay }
        if (selectedWeek == buddy.currentWeek && activeDayIdx >= 0) {
            val hasStats = buddy.squat5RM > 0 || buddy.bench5RM > 0 || buddy.deadlift5RM > 0
            val offset = if (hasStats) 2 else 1
            daysListState.animateScrollToItem(activeDayIdx + offset)
        } else {
            daysListState.scrollToItem(0)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(buddy.name, color = Theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        buddy.programTitle,
                        color = Theme.textSecondary,
                        fontSize = 12.sp
                    )
                    Text(" · ", color = Theme.textTertiary, fontSize = 12.sp)
                    Text(
                        "Неделя ${buddy.currentWeek}, День ${buddy.currentDay}",
                        color = Theme.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        text = {
            LazyColumn(
                state = daysListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 360.dp, max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. 5RM Stats
                if (buddy.squat5RM > 0 || buddy.bench5RM > 0 || buddy.deadlift5RM > 0) {
                    item(key = "stats") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Theme.surfaceSoft)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            if (buddy.squat5RM > 0) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Присед", color = Theme.textSecondary, fontSize = 11.sp)
                                    Text("${buddy.squat5RM.toInt()} кг", color = Theme.textPrimary, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (buddy.bench5RM > 0) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Жим", color = Theme.textSecondary, fontSize = 11.sp)
                                    Text("${buddy.bench5RM.toInt()} кг", color = Theme.textPrimary, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (buddy.deadlift5RM > 0) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Тяга", color = Theme.textSecondary, fontSize = 11.sp)
                                    Text("${buddy.deadlift5RM.toInt()} кг", color = Theme.textPrimary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // 2. Horizontal LazyRow of week chips
                item(key = "week_chips") {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "НЕДЕЛЯ ТРЕНИРОВОК",
                                color = Theme.textTertiary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (selectedWeek == buddy.currentWeek) {
                                Text(
                                    "АКТИВНАЯ НЕДЕЛЯ",
                                    color = Theme.accent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        LazyRow(
                            state = weekListState,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(availableWeeks, key = { it }) { weekNum ->
                                val isSelected = weekNum == selectedWeek
                                val isBuddyActiveWeek = weekNum == buddy.currentWeek
                                BuddyWeekChip(
                                    number = weekNum,
                                    selected = isSelected,
                                    isActiveWeek = isBuddyActiveWeek,
                                    onClick = { selectedWeek = weekNum }
                                )
                            }
                        }
                    }
                }

                // 3. Days of the selected week
                if (daysForSelectedWeek.isEmpty()) {
                    item(key = "empty_days") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Дни для недели $selectedWeek не найдены", color = Theme.textSecondary, fontSize = 12.sp)
                        }
                    }
                } else {
                    items(daysForSelectedWeek, key = { "day-${it.week}-${it.day}" }) { day ->
                        val isActiveDay = (selectedWeek == buddy.currentWeek && day.day == buddy.currentDay)
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Theme.surfaceSoft)
                                .then(
                                    if (isActiveDay) Modifier.border(1.5.dp, Theme.accent, RoundedCornerShape(8.dp))
                                    else Modifier
                                )
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "День ${day.day} · ${day.title.ifBlank { "Тренировка" }}",
                                    color = if (isActiveDay) Theme.accent else Theme.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (isActiveDay) {
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Theme.accent.copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("ТЕКУЩАЯ", color = Theme.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            day.exercises.forEach { ex ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        ex.name,
                                        color = Theme.textPrimary,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    val prescription = buildString {
                                        if (ex.sets > 0) append("${ex.sets}×")
                                        append(ex.reps)
                                        if (ex.weight.isNotBlank()) append(" · ${ex.weight}")
                                    }
                                    Text(
                                        prescription,
                                        color = Theme.textSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
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
                    val kind = try {
                        TrainingProgramKind.valueOf(buddy.programKind)
                    } catch (_: Exception) {
                        TrainingProgramKind.TEXAS
                    }
                    val newProf = ProgramProfile(
                        name = "${buddy.name} (копия)",
                        programKind = kind,
                        squat5RM = if (buddy.squat5RM > 0) buddy.squat5RM else 100.0,
                        bench5RM = if (buddy.bench5RM > 0) buddy.bench5RM else 100.0,
                        deadlift5RM = if (buddy.deadlift5RM > 0) buddy.deadlift5RM else 100.0,
                        level = TrainingLevel.BEGINNER
                    )
                    onCopyProgram(newProf)
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
            TextButton(onClick = onDismiss) {
                Text("Закрыть", color = Theme.textSecondary)
            }
        }
    )
}

@Composable
private fun BuddyWeekChip(
    number: Int,
    selected: Boolean,
    isActiveWeek: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(if (selected) 1f else 0.96f, Motion.snappy(), label = "buddyChip")
    Box(
        Modifier
            .size(width = 48.dp, height = 52.dp)
            .clip(CircleShape)
            .background(
                if (selected) Theme.accentGradient
                else androidx.compose.ui.graphics.SolidColor(
                    if (isActiveWeek) Theme.surfaceSoft.copy(alpha = 0.9f) else Theme.surfaceSoft
                )
            )
            .then(
                if (isActiveWeek && !selected) Modifier.border(1.5.dp, Theme.accent, CircleShape)
                else Modifier
            )
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "$number",
                color = if (selected) Color.White else if (isActiveWeek) Theme.accent else Theme.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            if (isActiveWeek) {
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (selected) Color.White else Theme.accent)
                )
            } else {
                Spacer(Modifier.height(5.dp))
            }
        }
    }
}
