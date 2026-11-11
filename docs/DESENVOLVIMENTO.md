# Desenvolvimento

## Requisitos

- JDK 17+ e Android SDK (`local.properties` com `sdk.dir=...`, fora do git).
- Gradle via wrapper (`./gradlew`), 9.5.0.
- Aparelho Android 8.0+ (minSdk 26) ou emulador. Para áudio de baixa latência e Bluetooth, prefira um aparelho real.

## Versões

| Item | Versão |
|---|---|
| Kotlin | 2.4.21 |
| Compose Multiplatform | 1.12.2 |
| AGP | 9.3.3 (a 9.5.1 exige Gradle 9.6) |
| compileSdk | 37 (exigido pelo Compose 1.12) |
| minSdk | 26 |
| Coroutines | 1.10.2 |

O módulo `engine` usa o plugin KMP library do AGP 9 (`com.android.kotlin.multiplatform.library`): configuração no bloco `android { }`, testes de host com `withHostTest {}`.

## Comandos

```bash
./gradlew :androidApp:assembleDebug     # APK em androidApp/build/outputs/apk/debug/
./gradlew :androidApp:installDebug      # instala no aparelho conectado
./gradlew :engine:jvmTest :shared:testAndroidHostTest
adb shell am start -n com.anthony.djapp/.MainActivity
```

## Convenções

- Código e comentários em português; **commits e PRs em inglês**.
- Nada alocado nem bloqueante na thread de áudio. Campos compartilhados com ela são `@Volatile`; mudanças compostas passam por objetos imutáveis publicados de uma vez (ver `SeekRequest`, `AlignRequest`).
- Posição e nível são lidos só em fase de desenho; textos usam `elapsedTenths`.
- Sem `String.format` em `commonMain`/`commonTest` (não existe em KMP).
- Regra de produto: a faixa de um deck tocando não é trocada.

## Armadilhas conhecidas

- Chaves de JVM: métodos `setX` em classes com propriedade `x` colidem; use nomes como `changeX`.
- Sliders experimentais do Material 3 exigem `@OptIn(ExperimentalMaterial3Api::class)`.
- Testes de EQ: renderize em blocos de 512 frames, senão a rampa de ganho cobre um buffer enorme.
- Iniciar serviço em primeiro plano a partir de `onStop` pode ser recusado: por isso `onUserLeaveHint`.

## Roadmap

Ainda não existe:
- Loops, hotcues, efeitos, histórico de faixas, knobs de filtro e gain.
- Latência mínima: migrar `AudioTrack` → Oboe (C++/NDK) se a latência incomodar.
- **iOS**: só placeholders (`IosAudioOutput`, `MainViewController`), nunca compilados. Precisa de Mac/Xcode, `AVAudioEngine` (source node) e uma biblioteca de arquivos (`Library`) para iOS.

## Como portar para iOS (resumo)

1. Implementar `AudioOutput` com `AVAudioEngine` + `AVAudioSourceNode`, chamando `DjMixer.render`.
2. Decodificador de faixas (`AVAudioFile`/`AVAssetReader`) que produza `Track`.
3. Implementar `Library` (seletor de documentos) e passá-la em `MainViewController`.
4. Criar o projeto Xcode que hospeda `MainViewController`.
