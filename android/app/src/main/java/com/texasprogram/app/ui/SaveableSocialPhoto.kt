package com.texasprogram.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.texasprogram.app.service.L
import com.texasprogram.app.service.SocialPhotoSave
import com.texasprogram.app.service.SocialService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SaveableSocialPhoto(
    imageId: String?,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val url = SocialService.imageUrl(imageId)
    if (url == null) {
        Box(modifier) { content() }
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember(url) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var waitingForPermission by remember { mutableStateOf(false) }
    fun show(message: String) {
        Toast.makeText(context, L(message), Toast.LENGTH_SHORT).show()
    }
    fun save() {
        if (saving) return
        saving = true
        scope.launch {
            try {
                SocialPhotoSave.save(context.applicationContext, url)
                show("Фото сохранено")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                show("Не удалось сохранить фото")
            } finally {
                saving = false
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (waitingForPermission) {
            waitingForPermission = false
            if (granted) save() else show("Разреши STRAIN сохранять фото в настройках телефона.")
        }
    }
    Box(
        modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onLongClickLabel = L("Сохранить фото"),
            onLongClick = { menu = true },
            onClick = { onTap?.invoke() ?: run { menu = true } }
        )
    ) {
        content()
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Сохранить фото") },
                enabled = !saving && !waitingForPermission,
                onClick = {
                    menu = false
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    ) {
                        waitingForPermission = true
                        permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        save()
                    }
                }
            )
        }
    }
}
