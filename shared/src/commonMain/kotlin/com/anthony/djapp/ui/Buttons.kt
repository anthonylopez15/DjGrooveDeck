package com.anthony.djapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anthony.djapp.domain.SyncState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Botões do transporte e o SYNC, com ícones desenhados (sem depender de fonte ou emoji).

/** Botão redondo de play/pause com ícone desenhado (sem depender de fonte/emoji). */
@Composable
fun PlayButton(
    playing: Boolean,
    enabled: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Esperando o tempo 1 do outro deck para largar (play com SYNC): anel grosso e preenchido. */
    waiting: Boolean = false,
) {
    val c = (if (playing || waiting) color else Idle).copy(alpha = if (enabled) 1f else 0.35f)
    Box(
        modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(if (waiting) color.copy(alpha = 0.25f) else Color.Transparent)
            .border(if (waiting) 4.dp else 2.dp, c, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            if (playing) {
                val bw = size.width * 0.28f
                drawRect(c, Offset(size.width * 0.12f, 0f), Size(bw, size.height))
                drawRect(c, Offset(size.width * 0.60f, 0f), Size(bw, size.height))
            } else {
                val p = Path().apply {
                    moveTo(size.width * 0.18f, 0f)
                    lineTo(size.width, size.height / 2f)
                    lineTo(size.width * 0.18f, size.height)
                    close()
                }
                drawPath(p, c)
            }
        }
    }
}

/** Volta ao início da faixa (ícone |◄ desenhado, sem depender de fonte). */
@Composable
fun RestartButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Idle.copy(alpha = if (enabled) 1f else 0.35f)
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .border(1.5.dp, c, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp)) {
            drawRect(c, Offset(0f, 0f), Size(size.width * 0.16f, size.height))
            val p = Path().apply {
                moveTo(size.width, 0f)
                lineTo(size.width * 0.28f, size.height / 2f)
                lineTo(size.width, size.height)
                close()
            }
            drawPath(p, c)
        }
    }
}

/**
 * Botão que repete enquanto está pressionado (andamento − e +): um passo ao tocar; segurando, depois de
 * 0,4 s repete a cada 150 ms.
 */
@Composable
fun RepeatButton(label: String, enabled: Boolean, onStep: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    val a = if (enabled) 1f else 0.35f
    val step by rememberUpdatedState(onStep)
    Box(
        modifier
            .height(40.dp)
            .clip(shape)
            .border(1.5.dp, Idle.copy(alpha = a), shape)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        step()
                        coroutineScope {
                            val repeat = launch {
                                delay(REPEAT_START_MS)
                                while (true) {
                                    step()
                                    delay(REPEAT_EVERY_MS)
                                }
                            }
                            tryAwaitRelease()
                            repeat.cancel()
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color(0xFFE6EDF3).copy(alpha = a), fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

private const val REPEAT_START_MS = 400L
private const val REPEAT_EVERY_MS = 150L

/** SYNC de 3 estados: cinza (desligado), contorno azul (ligado) e azul cheio (batidas travadas). */
@Composable
fun SyncButton(state: SyncState, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    val a = if (enabled) 1f else 0.35f
    val off = state == SyncState.Off
    Box(
        modifier
            .height(40.dp)
            .clip(shape)
            .background(if (state == SyncState.Locked) SyncBlue.copy(alpha = a) else Color.Transparent)
            .border(if (off) 1.5.dp else 2.dp, (if (off) Idle else SyncBlue).copy(alpha = a), shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "SYNC",
            color = (if (off) Idle else Color.White).copy(alpha = a),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
    }
}
