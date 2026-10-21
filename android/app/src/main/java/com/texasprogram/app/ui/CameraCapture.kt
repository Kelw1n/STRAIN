package com.texasprogram.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.view.OrientationEventListener
import android.view.Surface
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/// Своя камера вместо системной. Системная на каждом телефоне ведёт себя
/// по-своему: на многих селфи сохраняется не таким, каким его видно в превью,
/// и лицо на фото выглядит непривычно перекошенным. Здесь фронталка всегда
/// сохраняет кадр зеркальным — как «Зеркальная фронтальная камера» на iPhone.
class CameraLauncher internal constructor(private val open: () -> Unit) {
    fun launch() = open()
}

/// Какой камерой снимали в прошлый раз — пока приложение открыто. Сделал селфи —
/// в следующий раз сразу откроется фронталка, как в системной камере.
private var lastFront = false

/// Камера на весь экран; `onPhoto` получает готовый снимок. Разрешение на камеру
/// спрашивается при первом запуске.
@Composable
fun rememberCameraLauncher(onPhoto: (Uri) -> Unit): CameraLauncher {
    val context = LocalContext.current
    var showing by rememberSaveable { mutableStateOf(false) }
    val currentOnPhoto by rememberUpdatedState(onPhoto)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showing = true
        else Toast.makeText(context, "Разреши доступ к камере в настройках телефона", Toast.LENGTH_LONG).show()
    }
    if (showing) {
        CameraCaptureDialog(
            onDismiss = { showing = false },
            onPhoto = { uri ->
                showing = false
                currentOnPhoto(uri)
            }
        )
    }
    return remember(permission) {
        CameraLauncher {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (granted) showing = true else permission.launch(Manifest.permission.CAMERA)
        }
    }
}

/// Файл для снимка во временной папке; потом его читают, сжимают и отправляют.
fun cameraFile(context: Context): File {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    return File(dir, "photo-${System.currentTimeMillis()}.jpg")
}

private suspend fun cameraProvider(context: Context): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching { future.get() }
                .onSuccess { continuation.resume(it) }
                .onFailure { continuation.resumeWithException(it) }
        }, ContextCompat.getMainExecutor(context))
    }

@Suppress("DEPRECATION")
@Composable
private fun CameraCaptureDialog(onDismiss: () -> Unit, onPhoto: (Uri) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHapticEngine()

    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var front by remember { mutableStateOf(lastFront) }
    var canSwitch by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf<String?>(null) }
    /// Снятый кадр ждёт «Готово» или «Переснять».
    var shot by remember { mutableStateOf<File?>(null) }
    val blink = remember { Animatable(0f) }
    var flipTurns by remember { mutableFloatStateOf(0f) }
    val flipRotation by animateFloatAsState(flipTurns * 180f, Motion.snappy(), label = "cameraFlip")

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val capture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }

    LaunchedEffect(Unit) {
        provider = runCatching { cameraProvider(context) }.getOrNull()
        if (provider == null) failed = "Камера недоступна"
    }

    // Подключаем выбранную камеру; нет такой на телефоне — берём ту, что есть.
    LaunchedEffect(provider, front) {
        val cameras = provider ?: return@LaunchedEffect
        val hasFront = runCatching { cameras.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false)
        val hasBack = runCatching { cameras.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }.getOrDefault(false)
        canSwitch = hasFront && hasBack
        if (front && !hasFront && hasBack) { front = false; return@LaunchedEffect }
        if (!front && !hasBack && hasFront) { front = true; return@LaunchedEffect }
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        cameras.unbindAll()
        camera = runCatching {
            cameras.bindToLifecycle(
                lifecycleOwner,
                if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                capture
            )
        }.getOrNull()
        failed = if (camera == null) "Камера недоступна" else null
        lastFront = front
    }

    // Снимок повёрнут так, как держат телефон, даже если экран не поворачивается.
    DisposableEffect(Unit) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) return
                capture.targetRotation = when (orientation) {
                    in 45 until 135 -> Surface.ROTATION_270
                    in 135 until 225 -> Surface.ROTATION_180
                    in 225 until 315 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
            }
        }
        listener.enable()
        onDispose {
            listener.disable()
            provider?.unbindAll()
        }
    }

    fun switchCamera() {
        if (!canSwitch || shot != null) return
        haptics.selection()
        flipTurns += 1f
        front = !front
    }

    fun takePhoto() {
        if (busy || camera == null) return
        busy = true
        failed = null
        haptics.light()
        scope.launch {
            blink.snapTo(1f)
            blink.animateTo(0f, tween(260))
        }
        val file = cameraFile(context)
        // Главное: кадр с фронталки сохраняется зеркальным — таким, каким был в превью.
        val metadata = ImageCapture.Metadata().apply { isReversedHorizontal = front }
        capture.flashMode = if (flash && !front) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).setMetadata(metadata).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    busy = false
                    shot = file
                }

                override fun onError(exception: ImageCaptureException) {
                    busy = false
                    file.delete()
                    failed = "Не получилось снять — попробуй ещё раз"
                }
            }
        )
    }

    fun retake() {
        shot?.delete()
        shot = null
        failed = null
    }

    Dialog(
        onDismissRequest = { if (shot != null) retake() else onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            // Щипок — зум, касание — фокус, двойное касание — другая камера.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            val active = camera ?: return@detectTransformGestures
                            val state = active.cameraInfo.zoomState.value ?: return@detectTransformGestures
                            active.cameraControl.setZoomRatio((state.zoomRatio * zoom).coerceIn(state.minZoomRatio, state.maxZoomRatio))
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { switchCamera() },
                            onTap = { offset ->
                                val point = previewView.meteringPointFactory.createPoint(offset.x, offset.y)
                                runCatching { camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build()) }
                            }
                        )
                    }
            )

            shot?.let { file ->
                AsyncImage(
                    model = file,
                    contentDescription = "Снимок",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().background(Color.Black)
                )
            }

            // Вспышка экрана в момент снимка.
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = blink.value * 0.85f }.background(Color.White))

            failed?.let {
                Text(
                    it,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CameraRoundButton(onClick = { if (shot != null) retake() else onDismiss() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.weight(1f))
                if (shot == null && !front && camera?.cameraInfo?.hasFlashUnit() == true) {
                    CameraRoundButton(onClick = { haptics.selection(); flash = !flash }) {
                        Icon(
                            if (flash) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                            contentDescription = if (flash) "Вспышка включена" else "Вспышка выключена",
                            tint = if (flash) Theme.accent else Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 28.dp)
            ) {
                if (shot == null) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(78.dp)
                            .pressable(enabled = !busy) { takePhoto() }
                            .border(4.dp, Color.White, CircleShape)
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (busy) 0.5f else 1f))
                    )
                    if (canSwitch) {
                        CameraRoundButton(modifier = Modifier.align(Alignment.CenterEnd), onClick = ::switchCamera) {
                            Icon(
                                Icons.Filled.Cameraswitch,
                                contentDescription = "Сменить камеру",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp).graphicsLayer { rotationY = flipRotation }
                            )
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CameraTextButton("Переснять", filled = false, modifier = Modifier.weight(1f), onClick = ::retake)
                        CameraTextButton("Готово", filled = true, modifier = Modifier.weight(1f)) {
                            val file = shot ?: return@CameraTextButton
                            haptics.success()
                            onPhoto(FileProvider.getUriForFile(context, "${context.packageName}.files", file))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraRoundButton(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier
            .size(48.dp)
            .pressable(onClick = onClick)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun CameraTextButton(title: String, filled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .padding(vertical = 2.dp)
            .pressable(onClick = onClick)
            .clip(CircleShape)
            .background(if (filled) Color.White else Color.Black.copy(alpha = 0.55f))
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(title, color = if (filled) Color.Black else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
