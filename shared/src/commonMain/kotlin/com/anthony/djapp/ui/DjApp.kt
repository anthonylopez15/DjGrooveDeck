package com.anthony.djapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val DeckAColor = Color(0xFF22D3EE)
private val DeckBColor = Color(0xFFF472B6)
private val PanelColor = Color(0xFF151B23)

private val DjColors = darkColorScheme(
    background = Color(0xFF0B0F14),
    surface = PanelColor,
    primary = DeckAColor,
    onSurface = Color(0xFFE6EDF3),
    onBackground = Color(0xFFE6EDF3),
)

private fun mustPauseMessage(id: DeckId) = "Pause o deck ${id.name} para trocar a faixa"

private enum class Tab(val label: String) { Waves("ONDAS"), Mixer("MIXER") }

@Composable
fun DjApp(controller: DjController, library: Library) {
    // Posição vinda da thread de áudio, atualizada a cada frame de tela.
    LaunchedEffect(controller) {
        while (true) {
            withFrameNanos { }
            controller.poll()
        }
    }

    var browsing by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf(DeckId.A) }
    var tab by remember { mutableStateOf(Tab.Waves) }
    var status by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    // faixas em carregamento: id do arquivo -> deck de destino (para o preenchimento na lista)
    val loadingEntries = remember { mutableStateMapOf<String, DeckId>() }
    val scope = rememberCoroutineScope()

    // Recarrega do arquivo, com a tela à vista: o áudio solto por falta de memória em segundo plano, ou
    // a faixa que estava no deck quando o sistema encerrou o processo (volta pausada, na mesma posição).
    for (id in DeckId.entries) {
        val deck = controller.deck(id)
        val pending = deck.pendingRestore
        LaunchedEffect(deck.audioReleased, pending, controller.foreground) {
            if (!controller.foreground || deck.loading) return@LaunchedEffect
            val entry = pending?.source ?: deck.source?.takeIf { deck.audioReleased } ?: return@LaunchedEffect
            deck.startLoading(entry.name)
            try {
                val track = library.decode(entry, deck::updateLoadProgress)
                if (pending != null) deck.applyRestore(track, pending) else deck.restoreAudio(track)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (pending != null) deck.dropRestore()
                notice = "Não consegui recarregar ${entry.name}: ${e.message}"
            } finally {
                if (deck.loadingName == entry.name) deck.cancelLoading()
            }
        }
    }

    // aviso curto no topo; some sozinho
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(2500)
            notice = null
        }
    }

    MaterialTheme(colorScheme = DjColors) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Header(
                    controller = controller,
                    browsing = browsing,
                    target = target,
                    notice = notice,
                    onLoad = { id ->
                        // regra: não troca a faixa de um deck que está tocando
                        if (controller.deck(id).playingNow) {
                            notice = mustPauseMessage(id)
                        } else {
                            target = id
                            browsing = true
                        }
                    },
                    onDone = { browsing = false; status = null },
                )
                if (browsing) {
                    TransportRow(controller)
                    LibraryBrowser(
                        library,
                        status,
                        Modifier.weight(1f),
                        rowLoading = { entryId ->
                            loadingEntries[entryId]?.let { id ->
                                controller.deck(id).loadProgress to (if (id == DeckId.A) DeckAColor else DeckBColor)
                            }
                        },
                    ) { entry ->
                        val deckId = target
                        val deck = controller.deck(deckId)
                        if (deck.playingNow) {
                            status = mustPauseMessage(deckId)
                        } else if (!deck.loading) scope.launch {
                            status = null
                            deck.startLoading(entry.name)
                            loadingEntries[entry.id] = deckId
                            try {
                                val loaded = controller.loadTrack(deckId, library.decode(entry, deck::updateLoadProgress), entry)
                                if (!loaded) { // o deck começou a tocar durante a decodificação
                                    deck.cancelLoading()
                                    status = mustPauseMessage(deckId)
                                }
                            } catch (e: CancellationException) {
                                deck.cancelLoading()
                                throw e
                            } catch (e: Exception) {
                                deck.cancelLoading()
                                status = "Não consegui abrir: ${e.message}"
                            } finally {
                                loadingEntries.remove(entry.id)
                            }
                        }
                    }
                } else {
                    when (tab) {
                        Tab.Waves -> {
                            Row(
                                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                DeckPlatter(controller, DeckId.A, DeckAColor, Modifier.weight(1f))
                                DeckPlatter(controller, DeckId.B, DeckBColor, Modifier.weight(1f))
                            }
                            WavesPane(controller, Modifier.weight(1.25f))
                        }
                        // No mixer as ondas continuam visíveis em cima; os pratos saem.
                        Tab.Mixer -> {
                            WavesPane(controller, Modifier.weight(0.8f))
                            MixerPane(controller, Modifier.weight(1.6f))
                        }
                    }
                    TabBar(tab, controller.keyLock, controller::toggleKeyLock) { tab = it }
                    TransportRow(controller)
                }
            }
        }
    }
}

// ---------- cabeçalho ----------

@Composable
private fun Header(
    controller: DjController,
    browsing: Boolean,
    target: DeckId,
    notice: String?,
    onLoad: (DeckId) -> Unit,
    onDone: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
        Box(Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.Center) {
            if (browsing) {
                Button(onClick = onDone, contentPadding = ButtonDefaults.ContentPadding) { Text("OK", fontWeight = FontWeight.Bold) }
            } else if (notice != null) {
                Text(notice, color = Color(0xFFFACC15), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DeckHeader(controller.a, DeckAColor, browsing && target == DeckId.A, mirrored = false, { onLoad(DeckId.A) }, Modifier.weight(1f), controller.bpmMatched)
            DeckHeader(controller.b, DeckBColor, browsing && target == DeckId.B, mirrored = true, { onLoad(DeckId.B) }, Modifier.weight(1f), controller.bpmMatched)
        }
    }
}

@Composable
private fun DeckHeader(
    deck: DeckController,
    color: Color,
    selected: Boolean,
    mirrored: Boolean,
    onLoad: () -> Unit,
    modifier: Modifier = Modifier,
    bpmMatched: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!mirrored) LoadButton(color, selected, deck, onLoad)
            Column(Modifier.weight(1f), horizontalAlignment = if (mirrored) Alignment.End else Alignment.Start) {
                val loadingName = deck.loadingName
                Text(
                    when {
                        loadingName != null -> "Carregando $loadingName"
                        deck.hasTrack -> deck.title
                        else -> "Toque para carregar"
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp,
                    color = if (loadingName != null) color else if (deck.hasTrack) MaterialTheme.colorScheme.onSurface else Idle,
                )
                if (loadingName != null) {
                    Text("${(deck.loadProgress * 100).toInt()}%", fontSize = 13.sp, color = color)
                } else if (deck.hasTrack) {
                    // tempo restante e, ao lado, o BPM efetivo (visível também na lista de músicas)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "-" + formatTenths(deck.durationTenths - deck.elapsedTenths).substringBeforeLast('.'),
                            fontSize = 13.sp,
                            color = Idle,
                        )
                        Text(
                            deck.effectiveBpm?.let { "${formatBpm(it)} BPM" } ?: "-- BPM",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (deck.effectiveBpm == null) Idle else if (bpmMatched) SyncBlue else BpmText,
                        )
                    }
                }
            }
            if (mirrored) LoadButton(color, selected, deck, onLoad)
        }
        if (deck.loading) {
            // barra de progresso real no lugar da mini-waveform enquanto decodifica
            Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
                LinearProgressIndicator(
                    progress = { deck.loadProgress },
                    color = color,
                    trackColor = Groove,
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                )
            }
        } else {
            OverviewWaveform(
                peaks = deck.track?.peaks ?: FloatArray(0),
                progress = deck::progress,
                color = color,
                onSeek = deck::seek,
                modifier = Modifier.fillMaxWidth().height(24.dp),
            )
        }
    }
}

@Composable
private fun LoadButton(color: Color, selected: Boolean, deck: DeckController, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .size(52.dp)
            .background(if (selected || deck.loading) color.copy(alpha = 0.25f) else Color(0xFF10151B), shape)
            .border(2.dp, if (selected || deck.loading) color else color.copy(alpha = if (deck.playing) 0.2f else 0.45f), shape)
            .clickable(enabled = !deck.loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (deck.loading) {
            CircularProgressIndicator(
                progress = { deck.loadProgress },
                color = color,
                trackColor = Groove,
                strokeWidth = 3.dp,
                modifier = Modifier.size(30.dp),
            )
        } else {
            Text("♫", color = color.copy(alpha = if (deck.playing) 0.35f else 1f), fontSize = 22.sp)
        }
    }
}

// ---------- pratos ----------

@Composable
private fun DeckPlatter(controller: DjController, id: DeckId, color: Color, modifier: Modifier = Modifier) {
    val deck = controller.deck(id)
    Column(modifier.fillMaxHeight().padding(vertical = 6.dp)) {
        Platter(deck, color, controller.bpmMatched, Modifier.weight(1f).fillMaxWidth())
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SyncButton(
                state = controller.syncState(id),
                enabled = controller.canToggleSync(id),
                onClick = { controller.toggleSync(id) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ---------- painéis do meio ----------

@Composable
private fun WavesPane(controller: DjController, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ScrollingWaveform(controller.a, DeckAColor, "A", Modifier.weight(1f).fillMaxWidth())
        ScrollingWaveform(controller.b, DeckBColor, "B", Modifier.weight(1f).fillMaxWidth())
    }
}

/** Fader de volume nas bordas, knobs de EQ de cada lado e medidores de nível ao centro. */
@Composable
private fun MixerPane(controller: DjController, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val knob = min(maxHeight / 3.7f, 60.dp)
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween) {
                VolumeFader(controller.a, DeckAColor)
                EqKnobs(controller.a, DeckAColor, knob)
                Row(Modifier.fillMaxHeight().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LevelMeter(level = { controller.a.level })
                    LevelMeter(level = { controller.b.level })
                }
                EqKnobs(controller.b, DeckBColor, knob)
                VolumeFader(controller.b, DeckBColor)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("MASTER", fontSize = 11.sp, color = Idle)
            Slider(
                value = controller.master,
                onValueChange = controller::changeMaster,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun VolumeFader(deck: DeckController, color: Color) {
    var dragging by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxHeight()) {
        // mostra a porcentagem enquanto o dedo está no fader e enquanto o volume não for 0
        val showPercent = dragging || formatPercent(deck.volume) != "0%"
        Text(
            if (showPercent) formatPercent(deck.volume) else "VOL",
            fontSize = 11.sp,
            color = if (showPercent) color else Idle,
            fontWeight = if (showPercent) FontWeight.Bold else FontWeight.Normal,
        )
        VerticalFader(
            value = deck.volume,
            onChange = deck::changeVolume,
            range = 0f..1f,
            color = color,
            resetTo = 1f,
            ticks = 5,
            onActive = { dragging = it },
            modifier = Modifier.padding(vertical = 6.dp),
        )
    }
}

@Composable
private fun EqKnobs(deck: DeckController, color: Color, knob: androidx.compose.ui.unit.Dp) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.fillMaxHeight(),
    ) {
        Knob(deck.high, deck::changeHigh, color, "HIGH", knob, readout = ::formatKnobPercent)
        Knob(deck.mid, deck::changeMid, color, "MID", knob, readout = ::formatKnobPercent)
        Knob(deck.low, deck::changeLow, color, "LOW", knob, readout = ::formatKnobPercent)
    }
}

// ---------- rodapé ----------

@Composable
private fun TabBar(selected: Tab, keyLock: Boolean, onToggleKey: () -> Unit, onSelect: (Tab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF10151B)).padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Tab.entries.forEach { t ->
            val c = if (t == selected) Color(0xFF3B82F6) else Idle
            TabItem(t.label, c, if (t == Tab.Waves) TabIconKind.Waves else TabIconKind.Mixer, 28.dp) { onSelect(t) }
        }
        // KEY: mantém o tom ao mudar o andamento (tempo e SYNC); cadeado fechado = ligado
        TabItem(
            "KEY",
            if (keyLock) Color(0xFF3B82F6) else Idle,
            if (keyLock) TabIconKind.KeyLock else TabIconKind.KeyUnlock,
            20.dp,
            onToggleKey,
        )
    }
}

@Composable
private fun TabItem(label: String, color: Color, icon: TabIconKind, hPad: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = hPad, vertical = 6.dp),
    ) {
        TabIcon(icon, color)
        Text(label, textAlign = TextAlign.Center, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

/** CUE e PLAY de cada deck com o crossfader entre eles. */
@Composable
private fun TransportRow(controller: DjController) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF1A2028)).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RestartButton(controller.a.hasTrack, { controller.restart(DeckId.A) })
        PlayButton(controller.a.playing, controller.a.hasTrack, DeckAColor, { controller.togglePlay(DeckId.A) })
        Crossfader(controller.crossfader, controller::changeCrossfader, Modifier.weight(1f))
        PlayButton(controller.b.playing, controller.b.hasTrack, DeckBColor, { controller.togglePlay(DeckId.B) })
        RestartButton(controller.b.hasTrack, { controller.restart(DeckId.B) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Crossfader(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    Slider(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        thumb = {
            Box(Modifier.size(width = 16.dp, height = 40.dp).background(Color(0xFFDDE3EA), RoundedCornerShape(5.dp)))
        },
        track = {
            // guia + 5 marcações (0, 25, 50, 75, 100%) acima e abaixo, alinhadas ao curso da alça
            Canvas(Modifier.fillMaxWidth().height(40.dp)) {
                val half = 8.dp.toPx() // metade da largura da alça
                val usable = size.width - 2 * half
                val cy = size.height / 2f
                drawLine(Groove, Offset(0f, cy), Offset(size.width, cy), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                val gap = 6.dp.toPx()
                for (i in 0..4) {
                    val x = half + usable * i / 4f
                    val major = i % 2 == 0
                    val len = (if (major) 9.dp else 5.dp).toPx()
                    val c = if (major) Color(0xFFC3CCD6) else Idle
                    val w = 1.5.dp.toPx()
                    drawLine(c, Offset(x, cy - gap - len), Offset(x, cy - gap), strokeWidth = w)
                    drawLine(c, Offset(x, cy + gap), Offset(x, cy + gap + len), strokeWidth = w)
                }
            }
        },
    )
}
