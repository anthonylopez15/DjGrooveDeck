# Motor de áudio (`engine`)

Pacote `com.anthony.djapp.engine`. Kotlin puro em `commonMain`; só a saída de áudio e o decodificador são específicos de plataforma.

## Componentes

| Arquivo | Papel |
|---|---|
| `Track.kt` | Faixa decodificada: PCM `ShortArray` estéreo intercalado, `WaveformDetail`, picos da visão geral, `BeatGrid`. |
| `Deck.kt` | Um deck: tocar, buscar, tempo, volume, EQ, key lock, nível. |
| `DjMixer.kt` | Dois decks + crossfader + master + soft clip + alinhamento de batidas. |
| `Eq3.kt`, `Biquad.kt` | EQ de 3 bandas com filtros biquad. |
| `Crossfader.kt` | Curva de potência constante. |
| `WsolaStretcher.kt` | Time-stretch para o key lock. |
| `BpmDetector.kt` | BPM e grade de batidas. |
| `TrackInfo.kt` | Duração + BPM para listas. |
| `AudioOutput.kt` | Interface da saída de áudio (`start`, `stop`, `running`). |
| `androidMain/AndroidAudioOutput.kt` | `AudioTrack` float, baixa latência. |
| `androidMain/AndroidTrackDecoder.kt` | Decodificação via `MediaCodec`. |
| `iosMain/IosAudioOutput.kt` | Placeholder (nunca compilado). |

## Track

```kotlin
Track(name, sampleRate, pcm, detail, peaks, beatGrid, frames)
```

- `pcm`: 16 bits, `L,R,L,R…`. Deve ter tamanho par.
- `detail: WaveformDetail`: um "balde" a cada 256 frames com `peak` (0..1, normalizado) e `bass` (fração de energia abaixo de ~250 Hz, usada para colorir: grave = vermelho, agudo = azul).
- `peaks`: 1000 picos da faixa inteira para a visão geral, derivados de `detail` sem reler o PCM.
- `beatGrid`: BPM + posição da primeira batida, ou `null` se não há ritmo claro.
- **Faixa liberada** (`released`): cópia sem PCM (`releasedCopy()`), mantendo `frames`, onda e grade. `withPcm()` devolve o áudio. Usado pela liberação de memória.

## Deck

Campos públicos (todos `@Volatile`, escritos pela UI, lidos pela thread de áudio): `tempo` (1,0 = original), `volume`, `lowDb/midDb/highDb`, `keyLock`. Somente leitura: `track`, `playing`, `positionFrames`, `level`.

**Regras**
- `load(t)` devolve `false` se o deck está tocando (a faixa não troca com o deck tocando).
- `play()` não toca uma faixa liberada.
- `releaseAudio()` / `restoreAudio(pcm)`: solta/devolve o PCM de um deck parado; posição, tempo, EQ etc. ficam. O stretcher solta a referência antiga na própria thread de áudio; com ela parada, quem chama é `DjMixer.dropStaleAudio()` (`Deck.dropStaleStretchSource`).

**Leitura**
- Sem key lock: resample linear, com `readStep = taxaFaixa/taxaSaída × tempo` (velocidade e tom mudam juntos, como toca-discos).
- Com key lock: lê da fila do `WsolaStretcher`, que já entrega o andamento alterado com o tom original.

**Saída**: EQ → ganho `volume × ganhoCrossfader` com rampa linear dentro do bloco (evita *zipper noise*) → medidor de pico (queda suave).

### Threading e busca

A busca é um objeto imutável `SeekRequest(frame)` publicado em `seekRef` numa única escrita volátil. A thread de áudio compara a **identidade** com `appliedSeek` e nunca zera o campo; assim uma busca nova postada no meio de um bloco jamais é apagada. `effectivePositionFrames` devolve a posição da busca ainda não aplicada, quando houver — é o que o SYNC deve usar para decidir.

## DjMixer

- `render(out, frames)`: aplica o alinhamento pendente, soma os decks com os ganhos do crossfader, aplica `masterVolume` e `softClip`.
- **Crossfader** `0 = só A … 1 = só B`, ganhos `cos`/`sin` (potência constante).
- **Soft clip**: linear até 0,9; acima, curva suave que nunca passa de ±1.
- `keyLock`: propaga para os dois decks.

### Alinhamento de batidas

`requestAlign(follower, leader, followerGrid, leaderGrid, startFollower)` só registra um pedido (`AlignRequest`). No início do próximo `render`, a thread de áudio:

1. lê as posições efetivas dos dois decks no mesmo instante;
2. calcula a fase da batida de cada um (`BeatGrid.phaseAt`);
3. move o seguidor pelo caminho **mais curto** (`beatPhaseDelta`, de −0,5 a +0,5 batida);
4. se `startFollower`, dá play no mesmo bloco (largada quantizada).

Dois pedidos para o mesmo seguidor antes do bloco se fundem (o "começar a tocar" não se perde).

## WsolaStretcher (key lock)

WSOLA em tempo real. A cada `HOP` amostras de saída pega da entrada uma janela de Hann de `WIN` amostras, numa posição "natural" ajustada em até ±`TOL` para casar com a continuação do trecho anterior, e soma com 50% de sobreposição.

Constantes: `WIN = 2048`, `HOP = 1024`, `TOL = 768`. Com tempo 1,0 a continuação exata é escolhida e a saída é idêntica à entrada. A janela e a tolerância foram aumentadas depois que valores menores davam erro de afinação de até ~±52 centésimos em tempos de 0,97/1,03 (coberto por `KeyLockQualityTest`).

A saída fica numa fila estéreo; o consumidor usa `ensure` / `left`/`right` / `consume`. Sem alocação no uso normal.

## EQ

`Eq3`: low shelf 250 Hz, peaking 1 kHz (Q 0,7), high shelf 4 kHz. Os coeficientes só são recalculados quando o ganho muda. Mapeamento do knob `0..1` → dB: `0,5` = 0 dB; à esquerda, até **−30 dB** (*kill*); à direita, até **+6 dB**.

## BpmDetector

Assume andamento constante.

1. Envelope RMS por trecho de 256 frames → *onsets* (subidas de energia, descontada a média local).
2. Busca em duas etapas por pente de batidas: varredura de 80 a 160 BPM (passo 0,05) num trecho de 30 s; refinamento com passo 0,005 BPM em até 90 s (o erro de BPM vira deriva da grade).
3. A fase vem do melhor alinhamento do pente; a grade é estendida até o começo da faixa.

Resultado sempre em [80, 160): uma faixa de 170 BPM sai como 85. Devolve `null` quando não há periodicidade clara — faixa curta (< 10 s), confiança < 1,5, taxa de acerto < 40% ou modulação < 0,01 (protege contra tons puros e ruído).

## Saída e decodificação (Android)

**`AndroidAudioOutput`**: `AudioTrack` em float, `PERFORMANCE_MODE_LOW_LATENCY`, thread com `THREAD_PRIORITY_URGENT_AUDIO`. Cada thread tem seu próprio `Worker.alive`, então reiniciar a saída nunca "ressuscita" uma thread antiga. Se a escrita falha (por exemplo, troca de rota), o `AudioTrack` é recriado.

**`AndroidTrackDecoder`**
- `decode`: `MediaExtractor` + `MediaCodec` para PCM 16 bits estéreo, com progresso 0..1.
- `analyze`: decodifica só ~100 s do meio da faixa para obter BPM e duração sem custo de faixa inteira.
- Limite de memória: 7,5% do `Runtime.maxMemory()` em frames; `OutOfMemoryError` é tratado e vira erro amigável em vez de derrubar o app.
