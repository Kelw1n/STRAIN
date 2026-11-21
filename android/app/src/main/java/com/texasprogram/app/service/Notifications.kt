package com.texasprogram.app.service

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

internal fun Context.postNotification(id: Int, notification: Notification) {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return
    // Разрешение может измениться между проверкой и отправкой.
    runCatching { NotificationManagerCompat.from(this).notify(id, notification) }
}
