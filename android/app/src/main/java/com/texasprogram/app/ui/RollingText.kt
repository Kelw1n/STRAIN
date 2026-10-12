package com.texasprogram.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle

/// Текст, у которого сменившиеся знаки прокатываются, как `numericText` в iOS.
/// Анимируется только изменившийся знак, остальные стоят. Ключ — место
/// от правого края: когда «59:59» становится «1:00:00», секунды остаются секундами.
@Composable
fun RollingText(text: String, style: TextStyle, modifier: Modifier = Modifier, countsDown: Boolean = false) {
    Row(modifier) {
        text.forEachIndexed { index, char ->
            key(text.length - index) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        val direction = if (countsDown) -1 else 1
                        (slideInVertically(tween(220)) { it * direction } + fadeIn(tween(160))) togetherWith
                            (slideOutVertically(tween(220)) { -it * direction } + fadeOut(tween(120))) using
                            SizeTransform(clip = true)
                    },
                    label = "char"
                ) { value ->
                    Text(value.toString(), style = style, maxLines = 1)
                }
            }
        }
    }
}
