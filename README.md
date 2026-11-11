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

Funciona (testado só por testes unitários do DSP + build; ainda não rodado num aparelho):
- 2 decks, play/pause, cue estilo CDJ, busca tocando na waveform
- Tempo ±16% (varispeed: muda o tom junto, como toca-discos)
- EQ de 3 bandas por deck com "kill", volume por deck, crossfader, master

Ainda não tem:
- Time-stretch (mudar tempo sem mudar tom) e sync/detecção de BPM
- Loops, hotcues, efeitos, biblioteca/playlists
- Áudio em segundo plano (precisa de serviço em primeiro plano)
- Saída de latência mínima: hoje usa `AudioTrack`; o plano é migrar para Oboe (C++/NDK) se a latência incomodar
- **iOS**: só placeholders (`IosAudioOutput`, `MainViewController`), nunca compilados. Precisa de Mac (Xcode) para o projeto iOS, `AVAudioEngine` e seletor de arquivos.

## Versões

Kotlin 2.4.21, Compose Multiplatform 1.12.2, AGP 9.3.3, Gradle 9.5.0, compileSdk 37 (exigido pelo Compose 1.12), minSdk 26.
