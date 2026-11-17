package com.anthony.djapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anthony.djapp.domain.LibraryEntry
import com.anthony.djapp.engine.TrackInfo
import com.anthony.djapp.presentation.LibraryAction
import com.anthony.djapp.presentation.LibraryUiState

/**
 * Lista das pastas vinculadas e do conteúdo delas (estado e ações do `LibraryViewModel`). Tocar numa
 * pasta a abre; tocar numa faixa chama [onPick]. [status] é a mensagem do carregamento no deck e
 * [rowLoading] o progresso da linha em carregamento (ambos do `DjViewModel`).
 */
@Composable
fun LibraryBrowser(
    state: LibraryUiState,
    status: String?,
    onAction: (LibraryAction) -> Unit,
    onPick: (LibraryEntry) -> Unit,
    modifier: Modifier = Modifier,
    rowLoading: (String) -> Pair<Float, Color>? = { null },
) {
    Column(modifier.fillMaxSize()) {
        LibraryHeader(state.current, onAction)
        if (status != null) {
            Text(status, color = Idle, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        val pending = state.pendingAnalysis
        if (pending > 0) {
            Text(
                "Analisando BPM… ($pending restantes)",
                color = Idle,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }
        when {
            state.atRoot && state.folders.isEmpty() -> Centered(
                "Nenhuma pasta vinculada.\nToque em \"+ Pasta\" e escolha onde estão suas músicas.",
            )
            !state.atRoot && state.loading -> Centered("Carregando…")
            state.visible.isEmpty() -> Centered("Nenhuma música aqui")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(state.visible, key = { it.id }) { e ->
                    LibraryRow(
                        entry = e,
                        info = state.infos[e.id],
                        removable = state.atRoot,
                        loading = rowLoading(e.id),
                        onClick = { if (e.isFolder) onAction(LibraryAction.Open(e)) else onPick(e) },
                        onRemove = { onAction(LibraryAction.RemoveFolder(e)) },
                    )
                    HorizontalDivider(color = Color(0xFF1F2730))
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader(current: LibraryEntry?, onAction: (LibraryAction) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (current != null) {
            TextButton(onClick = { onAction(LibraryAction.Back) }) { Text("‹ Voltar", fontSize = 16.sp) }
        }
        Text(
            current?.name ?: "Arquivos",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        if (current == null) {
            TextButton(onClick = { onAction(LibraryAction.AddFolder) }) { Text("+ Pasta", fontSize = 16.sp) }
        }
    }
}

/** Uma pasta ou faixa. [loading]: progresso e cor do deck, se a faixa está sendo carregada. */
@Composable
private fun LibraryRow(
    entry: LibraryEntry,
    info: TrackInfo?,
    removable: Boolean,
    loading: Pair<Float, Color>?,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            // faixa carregando: a linha se preenche da esquerda para a direita
            .drawBehind {
                val (progress, color) = loading ?: return@drawBehind
                val w = size.width * progress.coerceIn(0f, 1f)
                if (w > 0f) {
                    drawRect(color.copy(alpha = 0.28f), size = Size(w, size.height))
                    val edge = 3.dp.toPx()
                    drawRect(color, Offset((w - edge).coerceAtLeast(0f), 0f), Size(edge, size.height))
                }
            }
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (entry.isFolder) "▸" else "♪", color = if (entry.isFolder) Color(0xFF3B82F6) else Idle, fontSize = 18.sp)
        Text(
            entry.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f).padding(vertical = 10.dp),
        )
        if (!entry.isFolder) {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 16.dp)) {
                Text(
                    bpmLabel(info),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (info?.bpm != null) Color(0xFFE6EDF3) else Idle,
                )
                Text(durationLabel(info), fontSize = 13.sp, color = Idle)
            }
        }
        if (removable) {
            TextButton(onClick = onRemove) { Text("✕", color = Idle) }
        }
    }
}

private fun bpmLabel(info: TrackInfo?): String {
    if (info == null) return "… BPM" // ainda analisando
    val bpm = info.bpm ?: return "-- BPM"
    return "${formatBpm(bpm)} BPM"
}

private fun durationLabel(info: TrackInfo?): String =
    if (info == null || info.durationSec <= 0.0) {
        "--:--"
    } else {
        formatTenths((info.durationSec * 10).toInt()).substringBeforeLast('.')
    }

@Composable
private fun Centered(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Idle, fontSize = 15.sp)
    }
}
