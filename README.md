# DjAppKmp

App de DJ (2 decks) em Kotlin Multiplatform + Compose Multiplatform. Android primeiro; iOS depois.

## Módulos

| Módulo | O que tem |
|---|---|
| `engine` | Motor de áudio em Kotlin puro: `Deck`, `DjMixer`, EQ de 3 bandas (biquads), crossfader de potência constante, `Track`. Saída Android (`AudioTrack`) e decoder (`MediaCodec`) em `androidMain`. |
| `shared` | UI Compose (`DjApp`) e `DjController` (estado observável). |
| `androidApp` | `MainActivity`: seletor de arquivos + liga motor e UI. |

Fluxo de áudio: `AudioOutput` (thread de áudio) → `DjMixer.render()` → `Deck.renderAdd()` (resample linear + EQ + ganho) → soma com crossfader → master + soft clip.

## Comandos

```bash
./gradlew :engine:jvmTest            # testes do DSP (rápido, na JVM)
./gradlew :androidApp:assembleDebug  # gera o APK em androidApp/build/outputs/apk/debug/
./gradlew :androidApp:installDebug   # instala no aparelho/emulador conectado
```

Requer JDK 17+ e Android SDK (`local.properties` com `sdk.dir`, que não vai pro git).

## Estado atual

Funciona (testado por testes unitários do DSP e no aparelho; o áudio com música real ainda está sendo avaliado):
- 2 decks, play/pause, botão de voltar ao início, busca tocando na waveform
- Navegador de pastas (Storage Access Framework) para carregar faixas
- Waveform ampliada colorida (grave/agudo) com grade de batidas
- Detecção de BPM (80–160, andamento fixo)
- SYNC: iguala o BPM e alinha as batidas; ao desligar volta à velocidade anterior
- Tempo ±16% com **key lock** (time-stretch WSOLA): muda a velocidade sem mudar o tom; botão KEY desliga (modo toca-discos)
- Música em segundo plano: serviço em primeiro plano com notificação (abrir e Pausar); mixer, faixas e estado vivem na sessão do processo, então ao voltar tudo está como antes. Sem música tocando, o áudio desliga ao sair da tela
- EQ de 3 bandas por deck com "kill", volume por deck, crossfader, master, medidores de nível

Ainda não tem:
- Loops, hotcues, efeitos, histórico de faixas, knobs de filtro e gain
- Saída de latência mínima: hoje usa `AudioTrack`; o plano é migrar para Oboe (C++/NDK) se a latência incomodar
- **iOS**: só placeholders (`IosAudioOutput`, `MainViewController`), nunca compilados. Precisa de Mac (Xcode) para o projeto iOS, `AVAudioEngine` e biblioteca de arquivos.

## Versões

Kotlin 2.4.21, Compose Multiplatform 1.12.2, AGP 9.3.3, Gradle 9.5.0, compileSdk 37 (exigido pelo Compose 1.12), minSdk 26.
