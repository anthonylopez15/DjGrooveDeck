# ADR 0001 — Arquitetura: MVVM com fluxo unidirecional sobre Clean Architecture enxuta

- **Status:** aceita (2026-11-17) — migração em andamento (ver "Plano de migração")
- **Escopo:** módulos `shared` e `androidApp`; o `engine` permanece como está

## Contexto

O app cresceu sem um padrão formal: telas Compose falam com um `DjController` que guarda o estado da
tela **e** as regras de negócio (SYNC, play, carregar, restaurar); a `DjSession` (Android) monta tudo
como um singleton e também decide o que fazer em interrupções (fone desconectado, foco de áudio).

Problemas:
- regra de domínio misturada com estado de tela (`DjController.kt` com ~450 linhas);
- a política de interrupções vive no código Android e teria de ser reescrita para iOS;
- sem padrão escrito, cada mudança escolhe um caminho diferente, o que dificulta a manutenção.

Requisitos que a arquitetura precisa preservar:
- tocar em segundo plano e voltar com tudo como estava (a sessão de áudio vive no **processo**, não na tela);
- restaurar o estado depois que o sistema encerra o processo;
- 60 quadros/s nas ondas e medidores sem recompor a tela inteira;
- reaproveitar o máximo possível no **iOS** (Compose Multiplatform).

## Decisão

**MVVM com fluxo unidirecional de dados (UDF), sobre uma Clean Architecture enxuta**, organizada em
pacotes dentro do módulo `shared` (sem criar um módulo Gradle por camada).

```
ui            Composables sem regra: recebem estado, emitem ações
  │
presentation  ViewModels: StateFlow<XxxUiState> + onAction(XxxAction)
  │
domain        Regras de negócio puras: casos de uso, modelos, interfaces (portas)
  ▲
data          Implementações das portas (biblioteca, estado salvo, sessão de áudio, interrupções)
  │
engine        Motor de áudio em Kotlin puro (módulo próprio, já isolado)
```

### Pacotes

| Camada | Pacote | Onde |
|---|---|---|
| ui | `com.anthony.djapp.ui` | `shared/commonMain` |
| presentation | `com.anthony.djapp.presentation` | `shared/commonMain` |
| domain | `com.anthony.djapp.domain` | `shared/commonMain` |
| data (comum) | `com.anthony.djapp.data` | `shared/commonMain` |
| data (plataforma) | `com.anthony.djapp.data` | `shared/androidMain`, `shared/iosMain` ou `androidApp` quando precisa de `Activity`/`Service` |
| engine | `com.anthony.djapp.engine` | módulo `engine` |

### Regras

1. **Dependências só apontam para dentro.** `ui → presentation → domain ← data`. O `domain` não importa
   `androidx.*`, `android.*`, `androidx.compose.*` nem `platform.*` (iOS). Ele pode usar o `engine`
   (Kotlin puro) e `kotlinx.coroutines`.
2. **Fluxo unidirecional.** A tela lê um `XxxUiState` imutável (`data class`) e envia `XxxAction`
   (`sealed interface`) para o ViewModel. A tela nunca altera estado diretamente.
3. **Regra de negócio só no `domain`.** SYNC, "não trocar a faixa de um deck tocando", largada alinhada,
   restauração, o que fazer numa interrupção: casos de uso (`XxxUseCase`) ou serviços de domínio,
   testáveis sem Compose nem Android.
4. **A plataforma detecta, o `domain` decide.** Android e iOS só detectam eventos (fone saiu, perda de
   foco/interrupção do `AVAudioSession`, falta de memória) e os repassam; a reação (pausar tudo,
   desfazer o encaixe, salvar, avisar) é uma regra única no `domain`, igual nas duas plataformas.
5. **Exceção de desempenho (documentada).** Posição e nível dos medidores mudam a cada quadro e **não**
   entram no `UiState`: seguem num canal próprio lido só na fase de desenho (Canvas), como hoje.
   Qualquer outro dado de alta frequência segue a mesma exceção e deve ser anotado aqui.
6. **Sessão de áudio no processo.** A sessão (mixer, saída, faixas carregadas) vive enquanto o processo
   vive, atrás de uma porta do `domain`; o ViewModel vive com a tela e só a observa. Isso mantém o
   segundo plano e a restauração.
7. **Injeção de dependência com Koin** (multiplataforma). Nada de singletons montando dependências; a
   `DjSession` deixa de ser montadora.
8. **Plataforma só por interface.** Todo acesso a sistema passa por uma porta definida no `domain`
   (`AudioOutput` já segue isso no `engine`).

### Nomes

| Tipo | Sufixo | Exemplo |
|---|---|---|
| Caso de uso | `UseCase` | `ToggleSyncUseCase` |
| Porta / repositório | `Repository`, `Store`, ou nome do papel | `TrackRepository`, `SessionStore`, `AudioPlayer` |
| ViewModel | `ViewModel` | `DjViewModel` |
| Estado de tela | `UiState` | `DjUiState` |
| Ação da tela | `Action` | `DjAction.TogglePlay` |
| Implementação de plataforma | prefixo da plataforma | `AndroidTrackRepository`, `IosAudioOutput` |

### Testes

- `domain`: testes de unidade puros (`commonTest`), sem Compose — é onde ficam os testes de regra (SYNC etc.).
- `presentation`: testes do ViewModel com portas falsas, verificando `UiState` a partir de `Action`.
- `engine`: como hoje (`jvmTest`).
- `data`/plataforma: verificação no aparelho (ver `docs/TESTES.md`).

### Reaproveitamento no iOS

`engine`, `domain`, `presentation`, `ui` e a DI ficam em `commonMain` (estimativa: 85–90% do código).
Cada plataforma implementa só as portas: saída de áudio, decodificador, biblioteca de arquivos, estado
salvo, detecção de interrupções/rota/memória e o modo de segundo plano.

## Alternativas consideradas

- **Manter como está:** sem custo agora, mas o `DjController` continua crescendo e a política de
  interrupções teria de ser duplicada no iOS.
- **Clean Architecture completa com um módulo Gradle por camada:** isolamento garantido pelo build,
  mas cerimônia demais para o tamanho atual (~4,5 mil linhas). Pode ser adotada depois sem mudar as
  regras, só promovendo os pacotes a módulos.
- **MVI com reducer único:** previsível, mas o motor de áudio já é um estado mutável externo; um
  reducer global não traria ganho sobre UDF com ViewModel.
- **DI manual (`AppContainer`):** uma biblioteca a menos, mas menos reconhecível para quem chegar depois.

## Consequências

- (+) Regras testáveis isoladamente; a política de interrupções vale igual em Android e iOS.
- (+) Padrão conhecido (MVVM/UDF/Clean, Koin): mais fácil para manutenção futura.
- (−) Mais arquivos e alguma indireção (ações, estados, portas).
- (−) Migração incremental: por um tempo convivem o código novo e o antigo.

## Plano de migração

Cada passo: testes passando, verificação no aparelho e um commit próprio.

1. [x] Este ADR.
2. [x] Regras do SYNC, play e interrupções → `domain` (`SyncRules`, `InterruptionPolicy`: núcleo funcional — o domínio decide a partir de uma "foto" de cada deck, `DeckView`, e a apresentação executa).
3. [x] `DjUiState` + `DjAction` + `DjViewModel` no lugar do `DjController`. A sessão virou
   `data.MixSession` (estado imutável em `StateFlow<MixState>`, sem Compose); posição e nível por
   quadro em `presentation.DeckMeters` (regra 5). `TrackRepository`/`LibraryEntry` foram para o `domain`.
   Pendente: a lista de arquivos (`LibraryBrowser`) ainda fala direto com o `TrackRepository` — terá seu
   próprio `LibraryViewModel`.
4. [x] Portas no `domain`: `TrackRepository` (ex-`Library`), `SessionStore` (estado guardado; formato
   em `data.SessionSnapshotCodec`) e `AudioSystem` (pedir para tocar + eventos `PlatformEvent`:
   interrupções e falta de memória). A `MixSession` recebe as portas e decide (pausar, salvar, soltar
   memória só fora da tela). Implementações Android em `shared/androidMain/.../data`
   (`AndroidAudioSystem`, `PrefsSessionStore`, `AndroidTrackRepository`, `AudioFocus`); iOS com
   placeholders (`IosAudioSystem`, `InMemorySessionStore`). A `DjSession` Android só monta as peças.
5. [ ] Koin; a `DjSession` deixa de montar dependências.
6. [ ] Separar `Components.kt` por tipo (botões, faders, knobs, ícones).
7. [x] Teste de arquitetura que falha se o `domain` importar Android/Compose/iOS ou camadas externas (`shared/src/androidHostTest/.../ArchitectureTest.kt`).
