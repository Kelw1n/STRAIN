package com.texasprogram.app.service

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/// Сколько опыта принесла тренировка. Показываем, когда экран тренировки уже
/// закрылся: поверх него праздновать нечего — его сейчас уберут.
object ProgressionEvents {
    data class Gain(val before: Int, val after: Int, val id: Long = System.nanoTime())

    var pending by mutableStateOf<Gain?>(null)

    fun celebrate(before: Int, after: Int) {
        if (after > before) pending = Gain(before, after)
    }
}
