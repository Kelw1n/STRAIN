package com.texasprogram.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texasprogram.app.model.SocialInvite
import com.texasprogram.app.model.SocialMessage
import com.texasprogram.app.model.SocialTogether
import com.texasprogram.app.service.SocialException
import com.texasprogram.app.service.SocialService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// MARK: - Совместная тренировка

/// Плашка сверху экрана: друг зовёт тренироваться вместе.
@Composable
fun TogetherInviteBanner(invite: SocialTogether, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    fun respond(accept: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            runCatching {
                if (accept) SocialService.acceptTogether(invite.id) else SocialService.respondTogether(invite.id, "decline")
            }
            busy = false
        }
    }

    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .chatGlass(RoundedCornerShape(22.dp))
            .padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(invite.partner.avatar, invite.partner.username, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("@${invite.partner.username}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("зовёт на тренировку", color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp, lineHeight = 16.sp, maxLines = 2)
        }
        Box(
            Modifier.size(36.dp).pressable(enabled = !busy) { respond(false) }.clip(CircleShape).background(Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Отказаться", tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.height(36.dp).pressable(enabled = !busy) { respond(true) }.clip(CircleShape).background(Color.White).padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Принять", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/// Что сейчас делает напарник: «Жим лёжа · 2/4 · подходов 7 из 18».
@Composable
fun PartnerStatus(together: SocialTogether, compact: Boolean = false, modifier: Modifier = Modifier) {
    val live = together.partnerLive
    val line = when {
        together.partnerDone -> "уже закончил(а)"
        live == null -> "ещё не начал(а)"
        else -> live.line + if (live.setsTotal > 0) " · подходов ${live.setsDone} из ${live.setsTotal}" else ""
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val size = if (compact) 34.dp else 42.dp
        Box(
            Modifier.size(size + 6.dp).border(2.dp, if (live != null) Theme.success else Color.White.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Avatar(together.partner.avatar, together.partner.username, size)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Вместе с @${together.partner.username}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (live != null) {
                    LivePulse(6.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    line,
                    color = if (live != null) Theme.success else Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/// Карточка над лентой: идёт совместная тренировка.
@Composable
fun TogetherCard(together: SocialTogether) {
    val scope = rememberCoroutineScope()
    var confirmLeave by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF161617))
            .border(1.dp, Theme.success.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionCaption("СОВМЕСТНАЯ ТРЕНИРОВКА")
        Row(verticalAlignment = Alignment.CenterVertically) {
            PartnerStatus(together, modifier = Modifier.weight(1f))
            Text(
                "Выйти",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.pressable { confirmLeave = true }.padding(8.dp)
            )
        }
        Text(
            "Начни тренировку как обычно — друг увидит твои подходы, а пост выйдет на двоих.",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 12.sp
        )
    }
    if (confirmLeave) {
        AlertDialog(
            modifier = Modifier.popIn(),
            onDismissRequest = { confirmLeave = false },
            containerColor = Theme.dialog,
            title = { Text("Выйти из совместной тренировки?", color = Theme.textPrimary, fontWeight = FontWeight.Bold) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    scope.launch { runCatching { SocialService.respondTogether(together.id, "leave") } }
                }) { Text("Выйти", color = Theme.record) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Отмена", color = Theme.textSecondary) } }
        )
    }
}

/// Полоса напарника над упражнениями, пока идёт тренировка. Обновляется сама.
@Composable
fun PartnerStrip(modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) {
        SocialService.refreshTogether()
        while (true) {
            delay(15_000)
            if (SocialService.together.active != null) SocialService.refreshTogether()
        }
    }
    val active = SocialService.together.active ?: return
    PartnerStatus(
        active,
        compact = true,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF161616))
            .border(1.dp, Theme.success.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(12.dp)
    )
}

/// Приглашение в переписке: принять, отказаться или отменить своё.
@Composable
internal fun InviteBubble(message: SocialMessage, invite: SocialInvite, username: String, onChange: (SocialInvite) -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun respond(action: String, status: String) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                if (action == "accept") SocialService.acceptTogether(invite.id) else SocialService.respondTogether(invite.id, action)
                onChange(invite.copy(status = status))
                error = null
            } catch (e: SocialException) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromMe) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 290.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF222222))
                .border(1.dp, Theme.success.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Theme.success.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.FitnessCenter, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(if (message.fromMe) "Ты зовёшь @$username" else "Тренируемся вместе?", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(invite.statusText, color = if (invite.status == "accepted") Theme.success else Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                }
            }
            if (invite.isPending) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (message.fromMe) {
                        InviteAction("Отменить", primary = false, enabled = !busy, modifier = Modifier.weight(1f)) { respond("cancel", "cancelled") }
                    } else {
                        InviteAction("Не сейчас", primary = false, enabled = !busy, modifier = Modifier.weight(1f)) { respond("decline", "declined") }
                        InviteAction("Принять", primary = true, enabled = !busy, modifier = Modifier.weight(1f)) { respond("accept", "accepted") }
                    }
                }
            }
            error?.let { Text(it, color = Theme.warning, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun InviteAction(title: String, primary: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(38.dp)
            .pressable(enabled = enabled, onClick = onClick)
            .clip(CircleShape)
            .background(if (primary) Color.White else Color.White.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Text(title, color = if (primary) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/// Кнопка в профиле друга: позвать на совместную тренировку.
@Composable
fun InviteButton(username: String, onSent: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .pressable(enabled = !busy) {
                    busy = true
                    scope.launch {
                        try {
                            SocialService.invite(username)
                            error = null
                            onSent()
                        } catch (e: SocialException) {
                            error = e.message
                        } finally {
                            busy = false
                        }
                    }
                }
                .clip(RoundedCornerShape(18.dp))
                .background(Theme.success.copy(alpha = 0.22f))
                .border(1.dp, Theme.success.copy(alpha = 0.5f), RoundedCornerShape(18.dp)),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            else Icon(Icons.Filled.FitnessCenter, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Тренироваться вместе", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        error?.let { Text(it, color = Theme.warning, fontSize = 12.sp) }
    }
}
