package com.texasprogram.app.ui

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** The system gesture dispatches Back itself. This covers touches delivered to the app,
 * including devices using on-screen navigation buttons. */
@Composable
internal fun rememberEdgeSwipeBack(): Modifier {
    val back = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher ?: return Modifier
    return Modifier.pointerInput(back) {
        val edge = 26.dp.toPx()
        val requiredTravel = 72.dp.toPx()
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.position.x !in 0f..edge || !back.hasEnabledCallbacks()) return@awaitEachGesture
            var claimed = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.any { it.id != down.id && it.pressed }) break
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.isConsumed || !back.hasEnabledCallbacks()) break
                val travel = change.position - down.position
                if (!claimed && maxOf(abs(travel.x), abs(travel.y)) > slop) {
                    if (travel.x <= 0 || travel.x <= abs(travel.y) * 1.5f) break
                    claimed = true
                }
                if (claimed) change.consume()
                if (!change.pressed) {
                    if (claimed && travel.x >= requiredTravel && travel.x > abs(travel.y)) {
                        back.onBackPressed()
                    }
                    break
                }
            }
        }
    }
}
