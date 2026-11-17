# Arquitetura

Visão geral de como o GrooveDeck DJ é organizado, como o áudio flui e quem é dono de quê.

> **Padrão adotado:** MVVM com fluxo unidirecional sobre Clean Architecture enxuta — ver
> [ADR 0001](adr/0001-arquitetura.md), que define as camadas, as regras e o plano de migração.
> Este documento descreve o estado **atual**; partes dele mudam conforme a migração avança.

## Módulos

```
androidApp ──► shared ──► engine
   (Android)    (UI)       (áudio, Kotlin puro)
```

| Módulo | Plugin | Alvos | Responsabilidade |
|---|---|---|---|
| `engine` | KMP library (`com.android.kotlin.multiplatform.library`) | `jvm()`, Android, `iosArm64`, `iosSimulatorArm64` | DSP e mixagem sem dependência de UI. Interface `AudioOutput`; implementações por plataforma. |
| `shared` | KMP + Compose Multiplatform | Android, iOS | Telas Compose, `DjController` (estado observável), contrato `Library` (biblioteca de arquivos). |
| `androidApp` | `com.android.application` | Android | `MainActivity`, `DjSession` (sessão do processo), `PlaybackService`, `AndroidLibrary` (SAF). |

Regra de dependência: `engine` não conhece Compose nem Android (só `androidMain` conhece). `shared` não conhece Android. Tudo que é de plataforma entra por interface (`AudioOutput`, `Library`).

## Fluxo de áudio

```
thread de áudio (AndroidAudioOutput, prioridade URGENT_AUDIO)
   └─ DjMixer.render(out, frames)
        ├─ resolve pedido de alinhamento pendente (applyAlign)
        ├─ Deck A: renderAdd(out, frames, ganhoCrossfaderA)
        │     leitura (resample linear OU WSOLA se key lock) → EQ 3 bandas → ganho com rampa
        ├─ Deck B: idem, ganhoCrossfaderB
        └─ master × softClip
```

Nada na thread de áudio aloca memória nem toma lock. A UI se comunica com ela apenas por campos `@Volatile` e por objetos imutáveis publicados numa única escrita volátil (ver [MOTOR.md](MOTOR.md), "Threading").

## Domínio (`com.anthony.djapp.domain`)

Regras puras, sem Compose nem plataforma (garantido por `ArchitectureTest`):

| Arquivo | Conteúdo |
|---|---|
| `SyncRules.kt` | `DeckId`, `SyncState`, `DeckView` (foto de um deck), decisões `SyncDecision` e `PlayDecision`, e as regras: BPM igual, travado, estado do botão, tempo do seguidor (±16%), quando travar, toggle de SYNC, play/largada alinhada, realinhar no restart, não trocar faixa tocando. |
| `Interruptions.kt` | `Interruption` (saída desconectada, outro app, temporária, play negado, pausa pedida) e `InterruptionPolicy`: pausar, salvar, avisar. |

O `DjController` monta as fotos (`DeckController.view()` com o estado real do motor; `shownView()` com o estado observável para a tela), pergunta às regras e executa a decisão. A plataforma (`DjSession`) só detecta eventos e chama `controller.onInterruption(...)`.

## Fluxo de estado (UI)

```
Compose (DjApp) ──► DjController ──► DeckController ──► Deck (motor)
        ▲                                   │
        └──── poll() a cada frame ◄─────────┘   (posição, nível, tocando)
```

- `DeckController` espelha o `Deck` em `mutableStateOf` (faixa, tempo, volume, EQ, SYNC, carregamento).
- `poll()` roda a cada frame de tela e copia posição/nível da thread de áudio para o estado. Posição e nível são lidos só na fase de **desenho** (Canvas), para não recompor a tela 60×/s; textos usam `elapsedTenths` (muda 10×/s).
- `DjController` guarda o que envolve os dois decks: crossfader, master, key lock e a lógica de SYNC.

## Quem é dono de quê (Android)

| Objeto | Vive | Observação |
|---|---|---|
| `AndroidAudioOutput`, `DjMixer`, `DjController` | `DjSession.Session` — processo inteiro | Sobrevive à `Activity`. A tela só se liga a eles. |
| `MainActivity` | Ciclo de vida da tela | `onStart`/`onResume` ligam a saída; `onStop` desliga se nada toca. |
| `PlaybackService` | Enquanto há música com o app fora da tela | Serviço em primeiro plano; **não** é dono do áudio, só mantém o processo vivo e mostra a notificação. |
| `AndroidLibrary` | Por `Activity` | Guarda pastas escolhidas (SAF) e cache de análise (BPM/duração). |

Detalhes em [ANDROID.md](ANDROID.md).

## Decisões de projeto

- **PCM em `ShortArray` (16 bits)**, não `Float`: metade da RAM (~53 MB para 5 min a 44,1 kHz). A conversão para float é feita no render.
- **Motor em Kotlin puro** (sem NDK) para compartilhar com iOS e testar na JVM. O custo é a latência do `AudioTrack`; a migração para Oboe fica como plano (ver [DESENVOLVIMENTO.md](DESENVOLVIMENTO.md)).
- **Alinhamento de batidas na thread de áudio**: medir posições na UI e aplicar depois deixaria 20–40 ms de erro entre os decks.
- **Sessão no processo, não na Activity**: é isso que permite tocar em segundo plano e voltar com tudo como estava.
- **Estado persistido em disco** (`SessionSnapshot`, texto `chave=valor`, sem biblioteca de serialização): se o processo for encerrado, a sessão volta com as faixas recarregadas dos arquivos.
- **Liberação de PCM sob pressão de memória**: com o app em segundo plano, decks parados soltam o áudio e o recarregam do arquivo ao voltar (ver [ANDROID.md](ANDROID.md), "Memória").
