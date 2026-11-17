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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.anthony.djapp.domain.Library
import com.anthony.djapp.domain.LibraryEntry
import com.anthony.djapp.engine.TrackInfo
import kotlinx.coroutines.CancellationException

/** Navegador das pastas vinculadas. Tocar numa faixa chama [onPick]. */
@Composable
fun LibraryBrowser(
    library: Library,
    status: String?,
    modifier: Modifier = Modifier,
    rowLoading: (String) -> Pair<Float, Color>? = { null },
    onPick: (LibraryEntry) -> Unit,
) {
    var stack by remember { mutableStateOf(listOf<LibraryEntry>()) } // vazio = raiz
    val folders by library.folders.collectAsState()
    var entries by remember { mutableStateOf(listOf<LibraryEntry>()) }
    var loading by remember { mutableStateOf(false) }
    val current = stack.lastOrNull()
    val infos = remember { mutableStateMapOf<String, TrackInfo>() }

    // Análise de BPM/duração em segundo plano, uma faixa por vez; o que já foi analisado vem do cache.
    LaunchedEffect(entries) {
        val files = entries.filter { !it.isFolder }
        for (f in files) library.cachedInfo(f)?.let { infos[f.id] = it }
        for (f in files) {
            if (f.id in infos) continue
            infos[f.id] = try {
                library.analyze(f) ?: TrackInfo(0.0, null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                TrackInfo(0.0, null)
            }
        }
    }

    LaunchedEffect(current) {
        if (current != null) {
            loading = true
            entries = try {
                library.list(current)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            loading = false
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (current != null) {
                TextButton(onClick = { stack = stack.dropLast(1) }) { Text("‹ Voltar", fontSize = 16.sp) }
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
                TextButton(onClick = library::requestAddFolder) { Text("+ Pasta", fontSize = 16.sp) }
            }
        }
        if (status != null) {
            Text(status, color = Idle, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        val pending = if (current != null && !loading) entries.count { !it.isFolder && it.id !in infos } else 0
        if (pending > 0) {
            Text(
                "Analisando BPM… ($pending restantes)",
                color = Idle,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }

        val list = if (current == null) folders else entries
        when {
            current == null && folders.isEmpty() -> Centered(
                "Nenhuma pasta vinculada.\nToque em \"+ Pasta\" e escolha onde estão suas músicas.",
            )
            current != null && loading -> Centered("Carregando…")
            list.isEmpty() -> Centered("Nenhuma música aqui")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(list, key = { it.id }) { e ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            // faixa carregando: a linha se preenche da esquerda para a direita
                            .drawBehind {
                                val (progress, color) = rowLoading(e.id) ?: return@drawBehind
                                val w = size.width * progress.coerceIn(0f, 1f)
                                if (w > 0f) {
                                    drawRect(color.copy(alpha = 0.28f), size = Size(w, size.height))
                                    val edge = 3.dp.toPx()
                                    drawRect(color, Offset((w - edge).coerceAtLeast(0f), 0f), Size(edge, size.height))
                                }
                            }
                            .clickable { if (e.isFolder) stack = stack + e else onPick(e) }
                            .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(if (e.isFolder) "▸" else "♪", color = if (e.isFolder) Color(0xFF3B82F6) else Idle, fontSize = 18.sp)
                        Text(
                            e.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                        )
                        if (!e.isFolder) {
                            val info = infos[e.id]
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
                        if (current == null) {
                            TextButton(onClick = { library.removeFolder(e) }) { Text("✕", color = Idle) }
                        }
                    }
                    HorizontalDivider(color = Color(0xFF1F2730))
                }
            }
        }
    }
}

private fun bpmLabel(info: TrackInfo?): String {
    if (info == null) return "… BPM" // ainda analisando
    val bpm = info.bpm ?: return "-- BPM"
    return "${formatBpm(bpm)} BPM"
}

private fun durationLabel(info: TrackInfo?): String =
    if (info == null || info.durationSec <= 0.0) "--:--"
    else formatTenths((info.durationSec * 10).toInt()).substringBeforeLast('.')

@Composable
private fun Centered(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Idle, fontSize = 15.sp)
    }
}
