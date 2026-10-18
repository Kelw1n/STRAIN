package com.texasprogram.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.texasprogram.app.service.ExerciseArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/// Квадратная картинка упражнения. Декодирование в фоне, готовая — из кэша
/// сразу, без мигания: прокрутка не ждёт диска.
@Composable
fun ExerciseThumb(name: String, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val revision = ExerciseArt.revision
    val image by produceState<ImageBitmap?>(
        initialValue = ExerciseArt.cached(name)?.asImageBitmap(),
        name, revision
    ) {
        value = withContext(Dispatchers.IO) { ExerciseArt.load(context, name)?.asImageBitmap() }
    }
    val shape = RoundedCornerShape(size * 0.25f)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(Color(0xFFD9D9D9)),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        } else {
            Icon(
                Icons.Filled.FitnessCenter,
                contentDescription = null,
                tint = Color(0xFF666666),
                modifier = Modifier.size(size * 0.42f)
            )
        }
    }
}

/// Крупная картинка упражнения: поставить свою из галереи или вернуть стандартную.
@Composable
fun ExerciseArtDialog(name: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    // Системный выбор фото: разрешение на всю галерею не нужно.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        loading = true
        scope.launch {
            withContext(Dispatchers.IO) { ExerciseArt.setCustomImage(context, name, uri) }
            loading = false
        }
    }
    val hasCustom = remember(ExerciseArt.revision) { ExerciseArt.hasCustomImage(context, name) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF121212))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(name, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Box(contentAlignment = Alignment.Center) {
                ExerciseThumb(name = name, size = 220.dp)
                if (loading) CircularProgressIndicator(color = Color.White)
            }
            Text(
                "Стандартная иллюстрация подбирается по названию. Можно поставить свою — она будет у этого упражнения во всех тренировках.",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .pressable {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text("Выбрать из галереи", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            if (hasCustom) {
                Text(
                    "Вернуть стандартную",
                    color = Theme.record,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .pressable { ExerciseArt.removeCustomImage(context, name) }
                        .clip(CircleShape)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}
