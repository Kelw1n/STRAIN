package com.texasprogram.app.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/// Отклик вибрацией, как в iOS-версии: «щелчок» при переходе через вкладку
/// или место в списке, лёгкий и средний толчок. Через View, а не
/// `LocalHapticFeedback`: там всего два вида отклика.
/// Системная настройка «Вибрация при касании» соблюдается сама.
@Stable
class Haptics(private val view: View) {
    fun selection() = perform(HapticFeedbackConstants.CLOCK_TICK)

    fun light() = perform(
        if (Build.VERSION.SDK_INT >= 27) HapticFeedbackConstants.KEYBOARD_PRESS
        else HapticFeedbackConstants.VIRTUAL_KEY
    )

    fun medium() = perform(HapticFeedbackConstants.LONG_PRESS)

    fun success() = perform(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.LONG_PRESS
    )

    private fun perform(constant: Int) {
        runCatching { view.performHapticFeedback(constant) }
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
