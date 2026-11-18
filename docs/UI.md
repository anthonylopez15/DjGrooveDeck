# Interface (`shared`)

Pacote `com.anthony.djapp.ui`, Compose Multiplatform.

## Telas

- **ONDAS**: dois pratos (deck A e B) em cima; embaixo, a onda ampliada dos dois decks.
- **MIXER**: ondas menores em cima; embaixo EQ (Low/Mid/High), fader de volume por deck, medidores, crossfader, master.
- **Biblioteca**: abre ao tocar no botão ♫ de um deck. Lista pastas/arquivos, com BPM e duração por linha.
- Barra inferior: abas ONDAS / MIXER e botão **KEY**, com ícones (onda, faders, cadeado fechado/aberto) (key lock global); abaixo, o transporte (play, voltar ao início, SYNC por deck) e o crossfader.

## Componentes

Os arquivos abaixo estão em `com.anthony.djapp.ui` e recebem `DjUiState` + `onAction` (sem regra).
Estado, ações e ViewModels ficam em `presentation` (`DjUiState`, `DjAction`, `DjViewModel`,
`DeckMeters`; `LibraryUiState`, `LibraryAction`, `LibraryViewModel` para a lista de arquivos); sessão em `data` (`MixSession`, `MixState`, `SessionSnapshot`); regras e o contrato
`TrackRepository` em `domain`.

| Arquivo | Conteúdo |
|---|---|
| `DjApp.kt` | Raiz: lê `DjViewModel.uiState`, repassa ações e chama `onFrame()` a cada quadro. |
| `Theme.kt` | Cores dos decks, cores compartilhadas (`Idle`, `SyncBlue`, `BpmText`, `Groove`) e tema escuro (`DjColors`, `deckColor`). |
| `DeckHeader.kt` | Cabeçalho: aviso do topo, nome/tempo/BPM **fixo** de cada deck (o do andamento fica no prato), botão ♫ e visão geral da onda. |
| `DecksPane.kt` | Aba ONDAS: pratos com SYNC e ondas ampliadas. |
| `MixerPane.kt` | Aba MIXER: volume, EQ, medidores e master. |
| `BottomBar.kt` | Abas com ícones (ONDAS, MIXER, KEY) e transporte com o crossfader. |
| `Buttons.kt` | `PlayButton`, `RestartButton`, `SyncButton` (ícones desenhados) e `RepeatButton` (− e + do andamento: 1% por toque, repete segurando). |
| `Platter.kt` | Prato: anel girando com a faixa, tempo, BPM e tempos. |
| `VerticalFader.kt` | Fader vertical com marcas (arraste relativo; toque duplo volta ao padrão). |
| `Knob.kt` | Knob rotativo com leitura em % enquanto arrasta ou fora do neutro. |
| `LevelMeter.kt` | Medidor de nível em segmentos. |
| `TabIcons.kt` | Ícones das abas (onda, faders, cadeado). |
| `Formatting.kt` | Formatação de BPM, tempo, porcentagens e duração (testada em `FormatTest`). |
| `Waveform.kt` | `OverviewWaveform` e `ScrollingWaveform`. |
| `LibraryBrowser.kt` | Lista de pastas e faixas (estado do `LibraryViewModel`): navegação, BPM/duração por linha e preenchimento da linha em carregamento. |

A tela é **travada em retrato** (`android:screenOrientation="portrait"` no manifest): o layout foi pensado para o celular em pé; deitado, os pratos sumiam e o mixer ficava espremido.

## Ondas

- **Visão geral** (`OverviewWaveform`): barras desenhadas uma só vez em dois bitmaps (esmaecido e aceso) via `drawWithCache`; a cada quadro só se recorta o aceso até a posição atual. Tocar ou arrastar busca.
- **Ampliada** (`ScrollingWaveform`): ~6 s sob um marcador fixo no centro, colorida por frequência (grave vermelho, agudo azul), com linhas de batida (laranja a cada 4 = compasso). Arrastar move a posição.

## Regras de uso

- **Não troca a faixa com o deck tocando.** Tocar em ♫ ou numa faixa com o deck em play mostra "Pause o deck X para trocar a faixa".
- Carregar mostra um preenchimento da esquerda para a direita sobre a linha da faixa e no deck.
- Adicionar pasta com música tocando não interrompe o áudio.

## SYNC (modelo djay)

Estado por deck: `syncOn`. Estado do par: `phaseLocked` (batidas alinhadas), `locked` (derivado: `phaseLocked` + os dois tocando + algum `syncOn`). O botão tem três aparências (`SyncState`): **Off**, **Armed** (contorno) e **Locked** (cheio).

| Ação | Efeito |
|---|---|
| Ligar SYNC num deck | Passa a seguir o BPM do outro (limite ±20%). Se já toca, iguala o andamento; com o outro tocando, alinha as batidas e trava. Parado, espera o play. |
| Play num deck com SYNC e o outro tocando | Largada quantizada: iguala o andamento e começa na batida certa (alinhamento feito na thread de áudio). |
| Desligar o SYNC do seguidor | Volta ao tempo que tinha antes de sincronizar. |
| Tocar no botão do **líder** com tudo travado | Desfaz o vínculo; cada deck fica no andamento em que está. |
| Pausar, trocar de faixa | Desfaz o encaixe (o SYNC continua ligado, sem travar). |
| Voltar ao início com SYNC | Cai de novo na batida certa. |

Detalhes:
- Só trava quando os **BPM efetivos** (BPM × tempo) já são iguais (`bpmMatched`, tolerância 0,05). Com o ajuste limitado a ±20%, andamentos muito distantes não casam e não aparecem "travados". O número de BPM fica azul quando casa.
- Sem BPM detectado em algum dos decks, o SYNC não liga.
- O key lock impede que acelerar/frear mude o tom.

## Acoplamento com o motor

Só a `MixSession` toca no motor (`DeckSession.engineDeck` é `internal`). A UI não importa nada do motor além do tipo `Track` usado para desenhar as ondas.
