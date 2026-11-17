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
| `shared` | KMP + Compose Multiplatform | Android, iOS | Camadas `domain`, `data`, `presentation` e `ui` (ver ADR 0001). |
| `androidApp` | `com.android.application` | Android | `GrooveDeckApp` (inicia o Koin), `MainActivity` e `PlaybackService`. As implementações Android das portas ficam em `shared/androidMain` (`data`). |

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
| `TrackRepository.kt` | Porta da biblioteca (`LibraryEntry`, listar, analisar, decodificar) e `EmptyTrackRepository`. |
| `SessionSnapshot.kt` | Modelo do estado guardado (`SessionSnapshot`, `DeckSnapshot`) e a porta `SessionStore`. |
| `AudioSystem.kt` | Porta do sistema de áudio: `requestPlayback()`, `outputRunning` e eventos `PlatformEvent` (interrupção, falta de memória). |
| `SyncRules.kt` | `DeckId`, `SyncState`, `DeckView` (foto de um deck), decisões `SyncDecision` e `PlayDecision`, e as regras: BPM igual, travado, estado do botão, tempo do seguidor (±16%), quando travar, toggle de SYNC, play/largada alinhada, realinhar no restart, não trocar faixa tocando. |
| `Interruptions.kt` | `Interruption` (saída desconectada, outro app, temporária, play negado, pausa pedida) e `InterruptionPolicy`: pausar, salvar, avisar. |

A `MixSession` monta as fotos (`DeckState.view()`; para decidir, com o estado real do motor), pergunta às regras e executa a decisão. A plataforma (`DjSession`) só detecta eventos e chama `mix.onInterruption(...)`.

## Injeção de dependências (Koin)

| Módulo | Onde | Fornece |
|---|---|---|
| `platformModule` | `expect` em `shared/commonMain/di`; `actual` em `androidMain` e `iosMain` | Motor (`AudioOutput`, `DjMixer`) e as portas: `AudioSystem`, `SessionStore`, `TrackRepository`. |
| `sessionModule` | `shared/commonMain/di` | `MixSession` (`single`: uma por processo). |
| `presentationModule` | `shared/commonMain/di` | `DjViewModel` (`viewModelOf`: um por tela). |
| `androidAppModule` | `androidApp` | `ScreenVisibility`. |

Android inicia em `GrooveDeckApp.onCreate`; iOS em `initKoin()`. Telas pegam o ViewModel com
`koinViewModel()`; Activity e serviço usam `by inject()`.

## Fluxo de estado (MVVM + UDF)

```
            onAction(DjAction)                       comandos
Compose ─────────────────────► DjViewModel ─────────────────► MixSession ──► Deck (motor)
(ui)    ◄───────────────────── (presentation) ◄──────────────── (data)
          StateFlow<DjUiState>        combine(MixState, ScreenState)

DeckMeters ◄── onFrame() a cada quadro: posição e nível lidos do motor (fora do UiState)
```

- **`MixSession`** (`data`): a sessão de áudio do processo. Estado imutável em `StateFlow<MixState>`
  (`DeckState` por deck: faixa, tempo, volume, EQ, SYNC, carregamento, restauração). Executa as
  decisões do domínio no motor. Vive enquanto o processo vive.
- **`DjViewModel`** (`presentation`): vive com a tela (sobrevive à recriação da Activity). Junta o
  estado da sessão com o que é só da tela (aba, biblioteca aberta, avisos, linhas carregando) num
  `DjUiState` imutável; recebe `DjAction`; carrega faixas e recarrega do arquivo as liberadas/restauradas.
- **`DeckMeters`** (`presentation`): exceção de desempenho (ADR, regra 5). Posição e nível mudam a
  cada quadro; ficam em estado Compose próprio, lidos só na fase de desenho; textos usam
  `elapsedTenths` (10x/s).
- **Telas** (`ui`): recebem `DjUiState` + `onAction` e não têm regra.

## Quem é dono de quê (Android)

| Objeto | Vive | Observação |
|---|---|---|
| `AndroidAudioOutput`, `DjMixer`, `AndroidAudioSystem`, `MixSession`, `AndroidTrackRepository` | Koin `single` — processo inteiro | Sobrevivem à `Activity`. A tela só se liga a eles. |
| `DjViewModel` | Escopo da tela (`ViewModelStore` da Activity) | Sobrevive à recriação da Activity; não guarda nada da Activity. |
| `MainActivity` | Ciclo de vida da tela | `onStart`/`onResume` ligam a saída; `onStop` desliga se nada toca. |
| `PlaybackService` | Enquanto há música com o app fora da tela | Serviço em primeiro plano; **não** é dono do áudio, só mantém o processo vivo e mostra a notificação. |

Detalhes em [ANDROID.md](ANDROID.md).

## Decisões de projeto

- **PCM em `ShortArray` (16 bits)**, não `Float`: metade da RAM (~53 MB para 5 min a 44,1 kHz). A conversão para float é feita no render.
- **Motor em Kotlin puro** (sem NDK) para compartilhar com iOS e testar na JVM. O custo é a latência do `AudioTrack`; a migração para Oboe fica como plano (ver [DESENVOLVIMENTO.md](DESENVOLVIMENTO.md)).
- **Alinhamento de batidas na thread de áudio**: medir posições na UI e aplicar depois deixaria 20–40 ms de erro entre os decks.
- **Sessão no processo, não na Activity**: é isso que permite tocar em segundo plano e voltar com tudo como estava.
- **Estado persistido em disco** (`SessionSnapshot`, texto `chave=valor`, sem biblioteca de serialização): se o processo for encerrado, a sessão volta com as faixas recarregadas dos arquivos.
- **Liberação de PCM sob pressão de memória**: com o app em segundo plano, decks parados soltam o áudio e o recarregam do arquivo ao voltar (ver [ANDROID.md](ANDROID.md), "Memória").
