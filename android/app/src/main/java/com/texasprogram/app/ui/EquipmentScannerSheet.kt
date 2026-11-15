package com.texasprogram.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.texasprogram.app.data.GeminiKeyStore
import com.texasprogram.app.service.EquipmentKind
import com.texasprogram.app.service.EquipmentScan
import com.texasprogram.app.service.EquipmentScanner
import com.texasprogram.app.service.FoodRecognizer
import com.texasprogram.app.service.L
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun EquipmentScannerSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var front by rememberSaveable { mutableStateOf<Uri?>(null) }
    var side by rememberSaveable { mutableStateOf<Uri?>(null) }
    var capturingSide by rememberSaveable { mutableStateOf(false) }
    var result by remember { mutableStateOf<EquipmentScan?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var needsKey by remember { mutableStateOf(false) }
    var showingKey by remember { mutableStateOf(false) }

    fun accept(photo: Uri) {
        if (capturingSide) {
            side = photo
        } else {
            front = photo
            side = null
        }
        result = null
        failure = null
    }

    val camera = rememberCameraLauncher(::accept)
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { photo ->
        if (photo != null) accept(photo)
    }

    fun openPhoto(sideView: Boolean, fromCamera: Boolean) {
        capturingSide = sideView
        if (fromCamera) {
            camera.launch()
        } else {
            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    fun scan() {
        val mainPhoto = front ?: return
        val sidePhoto = side
        if (working) return
        val key = GeminiKeyStore.load(context)
        if (key == null) {
            needsKey = true
            failure = "Для сканера нужен ключ Google Gemini."
            return
        }
        working = true
        failure = null
        scope.launch {
            try {
                result = EquipmentScanner.recognize(context, mainPhoto, sidePhoto, key)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                needsKey = e == FoodRecognizer.Failure.BadKey || e == FoodRecognizer.Failure.NoKey
                failure = when (e) {
                    FoodRecognizer.Failure.NothingFound -> "Не удалось получить ответ. Попробуй ещё раз."
                    else -> e.message ?: "Не удалось получить ответ. Попробуй ещё раз."
                }
            } finally {
                working = false
            }
        }
    }

    NutritionSheet(title = "Что за тренажёр?", leading = "Закрыть", onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Сними тренажёр целиком. Фото отправляется в Google Gemini для распознавания.", color = Theme.textSecondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                front?.let { EquipmentPhoto(it, "Общий вид", Modifier.weight(1f)) }
                side?.let { EquipmentPhoto(it, "Фото сбоку", Modifier.weight(1f)) }
            }
            result?.let { EquipmentResult(it) }
            failure?.let { Text(it, color = Theme.warning) }

            if (result?.status == EquipmentScan.Status.SIDE_VIEW || side != null) {
                NutritionButton("Снять сбоку", icon = Icons.Filled.PhotoCamera, primary = false, enabled = !working) {
                    openPhoto(sideView = true, fromCamera = true)
                }
                NutritionButton("Выбрать фото сбоку", primary = false, enabled = !working) {
                    openPhoto(sideView = true, fromCamera = false)
                }
            }
            if (working) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Theme.ink, strokeWidth = 2.dp)
                    Text("Распознаю тренажёр…", color = Theme.ink)
                }
            } else if (needsKey) {
                NutritionButton("Настроить ключ Gemini") { showingKey = true }
            } else if (front != null && result == null) {
                NutritionButton(if (side == null) "Распознать тренажёр" else "Уточнить по двум фото", onClick = ::scan)
            }
            NutritionButton(
                if (front == null) "Снять тренажёр" else "Снять заново",
                icon = Icons.Filled.PhotoCamera,
                primary = false,
                enabled = !working
            ) { openPhoto(sideView = false, fromCamera = true) }
            NutritionButton("Выбрать фото тренажёра", primary = false, enabled = !working) {
                openPhoto(sideView = false, fromCamera = false)
            }
        }
    }
    if (showingKey) {
        GeminiKeySheet(
            title = "Ключ для сканера тренажёров",
            intro = "Сканер использует тот же ключ Google Gemini, что и распознавание еды.",
            onDismiss = {
                showingKey = false
                if (GeminiKeyStore.load(context) != null) {
                    needsKey = false
                    failure = null
                }
            }
        )
    }
}

@Composable
private fun EquipmentPhoto(uri: Uri, title: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().height(145.dp).clip(RoundedCornerShape(14.dp)).background(Theme.surface)
        )
        Text(title, color = Theme.textSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun EquipmentResult(scan: EquipmentScan) {
    Column(
        Modifier.fillMaxWidth().background(Theme.surface, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (scan.status) {
            EquipmentScan.Status.SIDE_VIEW -> {
                Text("Нужно фото сбоку", color = Theme.ink, fontWeight = FontWeight.Bold)
                Text("Покажи спинку, рукоятки и рычаги в одном кадре.", color = Theme.textSecondary)
            }
            EquipmentScan.Status.UNKNOWN -> {
                Text("Не удалось определить тренажёр", color = Theme.ink, fontWeight = FontWeight.Bold)
                Text("Попробуй снять тренажёр целиком при хорошем свете.", color = Theme.textSecondary)
            }
            EquipmentScan.Status.IDENTIFIED -> Unit
        }
        for (candidate in scan.candidates) EquipmentMuscles(candidate.label)
        if (scan.observation.isNotEmpty()) Text(scan.observation, color = Theme.ink)
    }
}

@Composable
private fun EquipmentMuscles(label: EquipmentKind.Label) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label.name, color = Theme.ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        if (label.primary.isEmpty()) {
            Text("Мышцы зависят от упражнения и положения блока.", color = Theme.textSecondary)
        } else {
            Text(L("Основные мышцы") + ": " + label.primary.joinToString(), color = Theme.textSecondary)
        }
        if (label.secondary.isNotEmpty()) {
            Text(L("Помогают") + ": " + label.secondary.joinToString(), color = Theme.textSecondary)
        }
    }
}
