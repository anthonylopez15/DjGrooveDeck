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
| Lifecycle (ViewModel KMP) | 2.9.6 |
| Koin | 4.2.2 |

O módulo `engine` usa o plugin KMP library do AGP 9 (`com.android.kotlin.multiplatform.library`): configuração no bloco `android { }`, testes de host com `withHostTest {}`.

## Comandos

```bash
./gradlew :androidApp:assembleDebug     # APK em androidApp/build/outputs/apk/debug/
./gradlew :androidApp:installDebug      # instala no aparelho conectado
./gradlew :engine:jvmTest :shared:testAndroidHostTest
adb shell am start -n com.anthony.djapp/.MainActivity
```

### Antes de cada commit

```bash
./gradlew checkAll       # ktlint + detekt + testes + Android Lint + compilação comum do iOS
./gradlew ktlintFormat   # corrige a formatação automaticamente
```

Os builds e testes são feitos só localmente (não há CI). Ferramentas de lint:

| Ferramenta | O que confere | Configuração |
|---|---|---|
| **ktlint** 1.8.0 | Formatação | `.editorconfig` (estilo `intellij_idea`, 140 colunas; 3 regras desligadas por conflitarem com o estilo adotado) |
| **detekt** 1.23.8 | Problemas de código (complexidade, exceções, nomes...) | `config/detekt/detekt.yml` |
| **Android Lint** (AGP) | Problemas específicos do Android | bloco `lint {}` em `androidApp/build.gradle.kts`; avisos contam como erro |

ktlint e detekt rodam como programas à parte (tarefas `JavaExec` no `build.gradle.kts` da raiz), sem
plugins Gradle, para não depender da compatibilidade com o AGP/KMP.

**Baselines (débito registrado):** o que já existia quando o lint foi adotado está em
`config/detekt/baseline.xml` (25 itens: funções longas no DSP e no decodificador, classes grandes da
sessão, a lista da biblioteca) e em `androidApp/lint-baseline.xml` (targetSdk 36 em vez de 37). Só
problemas **novos** falham. Ao resolver um item, regrave: `./gradlew detektBaseline` /
`./gradlew :androidApp:updateLintBaselineDebug`.

## Convenções

- **Arquitetura:** siga o [ADR 0001](adr/0001-arquitetura.md) (camadas, regras de dependência, nomes, testes).
- **Lint:** `./gradlew checkAll` limpo antes de commitar. Captura de exceção ampla só nas bordas (I/O,
  sistema): se for de propósito ignorar, nomeie a variável `ignored` e diga o motivo num comentário.

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
- targetSdk 37 (hoje 36; está na baseline do Android Lint): subir exige testar as mudanças de comportamento do Android 17.
- Layout para paisagem/tablet (hoje a tela é travada em retrato). Obs.: no Android 16+, em telas grandes (tablets, dobráveis abertos) o sistema ignora a trava de orientação.
- Loops, hotcues, efeitos, histórico de faixas, knobs de filtro e gain.
- Latência mínima: migrar `AudioTrack` → Oboe (C++/NDK) se a latência incomodar.
- **iOS**: só placeholders (`IosAudioOutput`, `MainViewController`), nunca compilados. Precisa de Mac/Xcode, `AVAudioEngine` (source node) e uma biblioteca de arquivos (`Library`) para iOS.

## Como portar para iOS (resumo)

1. Implementar `AudioOutput` com `AVAudioEngine` + `AVAudioSourceNode`, chamando `DjMixer.render`.
2. Decodificador de faixas (`AVAudioFile`/`AVAssetReader`) que produza `Track`.
3. Implementar as portas do `domain` no iOS: `TrackRepository` (seletor de documentos), `SessionStore` (`NSUserDefaults`) e `AudioSystem` (`AVAudioSession`: interrupções, rota, memória) — hoje `IosAudioSystem` e `InMemorySessionStore` são placeholders.
4. Criar o projeto Xcode que hospeda `MainViewController`.
