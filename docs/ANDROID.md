# Android (`androidApp`)

## Ícones

Tudo em vetor (`androidApp/src/main/res`), sem imagens: dois pratos de DJ, um ciano e um rosa (as
cores dos decks A e B), sobre o fundo escuro do app.

| Recurso | Uso |
|---|---|
| `drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml` | Ícone adaptativo do app (formato definido pelo launcher: círculo, quadrado arredondado...). Os pratos ficam na zona segura de 66 dp. |
| `drawable/ic_launcher_monochrome.xml` | Versão de uma cor, para os **ícones temáticos** do Android 13+ (o sistema pinta com a cor do papel de parede). |
| `mipmap-anydpi/ic_launcher*.xml` | Montam as camadas (`ic_launcher` e `ic_launcher_round`). Sem pasta `-v26`: o minSdk já é 26. |
| `drawable/ic_stat_deck.xml` | Ícone pequeno da notificação (só silhueta; o sistema exige monocromático). |

Para mudar o desenho, edite os vetores. Se o build reclamar de `mipmap/ic_launcher` não encontrado depois
de mexer nessas pastas, é cache de build desatualizado: `./gradlew :androidApp:clean`.

## Componentes

| Classe | Papel |
|---|---|
| `MainActivity` | Liga a tela Compose à sessão; controla visibilidade e saída de áudio; pede a permissão de notificação uma vez. |
| `GrooveDeckApp` | `Application`: inicia o Koin (`appModules()` + `androidAppModule`). As peças da sessão são `single` — uma por processo. |
| `ScreenVisibility` | A tela está à vista (entre `onStart` e `onStop`); o serviço usa para saber quando parar. |
| `PlaybackService` | Serviço em primeiro plano (`mediaPlayback`) com notificação. |
| `AndroidTrackRepository` (`shared/androidMain`, `data`) | Porta `TrackRepository`: biblioteca via Storage Access Framework + cache de análise. |
| `AndroidAudioSystem` (`shared/androidMain`, `data`) | Porta `AudioSystem`: foco de áudio, fone desconectado e falta de memória viram `PlatformEvent`; liga/desliga a saída de áudio. |
| `PrefsSessionStore` (`shared/androidMain`, `data`) | Porta `SessionStore`: `SharedPreferences("session")`, chave `state`, formato do `SessionSnapshotCodec`. |
| `AudioFocus` (`shared/androidMain`, `data`) | Foco de áudio, usado pelo `AndroidAudioSystem`. |

Manifest: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`, `largeHeap`, serviço `.PlaybackService` com `foregroundServiceType="mediaPlayback"` e `stopWithTask`.

## Ciclo de vida e segundo plano

```
onStart / onResume   activityVisible = true, mix.setForeground(true)
onUserLeaveHint      (início/recentes) se tocando: activityVisible = false e inicia o serviço
onStop               mix.setForeground(false), salva; se tocando inicia o serviço, senão output.stop()
```

**Saída de áudio só quando precisa**: a tela não abre a saída. O play (`AudioSystem.requestPlayback()`, implementado pelo `AndroidAudioSystem`) pede o foco e liga a saída; com os dois decks parados por **5 s** (tela à vista), um vigia no `AndroidAudioSystem` a desliga. Com a saída aberta o sistema considera o app tocando (silêncio) e gasta bateria. O primeiro play depois disso abre o `AudioTrack` de novo (alguns ms). Log: `saída aberta` / `saída fechada` na tag `DjAudio`.

- `onUserLeaveHint` é o momento em que o Android ainda permite iniciar o serviço sem restrição; `onStop` fica de reserva (por exemplo, tela desligando).
- **Serviço**: a cada 400 ms um *watcher* checa: se a tela voltou, o serviço se encerra mantendo o áudio; se nada toca, encerra e desliga a saída. `lastStartId` garante que `stopSelf(id)` não seja ignorado quando chega um novo start. Remover o app dos recentes (`onTaskRemoved`) para tudo.
- **Notificação**: toque abre o app; ação **Pausar** pausa os dois decks.
- Sem a permissão `POST_NOTIFICATIONS` (Android 13+) a música segue tocando; só a notificação não aparece.

## Pausas de fora da tela

Toda pausa que não vem de um botão da tela — ação **Pausar** da notificação, perda do foco de áudio, saída desconectada, fim do serviço — vira uma `Interruption`: o `AndroidAudioSystem` a detecta e emite `PlatformEvent.Interrupted` (a notificação e o serviço chamam `MixSession.interrupt(PauseRequested)`), e a `MixSession` a trata; a reação vem do domínio (`InterruptionPolicy`) e, ao pausar, usa `MixSession.pauseAll()`, que pausa os dois decks, desfaz o encaixe de batidas (`phaseLocked`) e descarta uma largada alinhada ainda pendente no mixer (`DjMixer.cancelAlign`); depois o estado é salvo. Pausar o motor direto deixava a sessão achando que as batidas seguiam travadas.

## Foco de áudio (ligações, outros apps)

- Antes de um deck começar a tocar, `AudioSystem.requestPlayback()` pede o foco (`AUDIOFOCUS_GAIN`, mídia/música). Negado (ex.: ligação em andamento): o play não acontece e aparece "Não dá para tocar agora (ligação em andamento?)". Pausar não pede nada.
- **Outro app começou a tocar música** (perda definitiva): pausa os dois decks, solta o foco e avisa "Música pausada: outro app começou a tocar".
- **Ligação ou assistente de voz** (perda temporária): pausa e avisa "Música pausada (ligação ou outro áudio)". **Não volta a tocar sozinho** quando o foco volta: num set, o DJ decide quando retomar, e os dois decks precisam voltar alinhados (dar play com SYNC realinha).
- **Som de notificação** (perda breve com volume baixo): o próprio Android abaixa o volume e devolve; o app não faz nada.
- Em Android 14+ o sistema faz um *fade* de ~2 s no app que perde o foco antes de avisá-lo; por isso o set ainda soa um instante depois de o outro app começar.
- Verificado no aparelho: com um deck tocando, abrir uma música no YouTube Music tomou o foco e o set pausou. Não verificado: ligação real.

## Troca de saída de áudio (fone, Bluetooth)

- **Saída desconectada com música tocando** (fone com fio sai, Bluetooth desliga ou cai): o `AndroidAudioSystem` recebe `ACTION_AUDIO_BECOMING_NOISY` e emite a interrupção, pausa os dois decks, guarda o estado e mostra "Saída de áudio desconectada: música pausada". Sem isso o Android passaria o som para o alto-falante no meio do set.
- **Saída conectada**: o próprio Android redireciona o `AudioTrack` aberto; não é preciso recriar. Se uma escrita falhar (`DEAD_OBJECT`), `AndroidAudioOutput` recria o track.
- **Diagnóstico**: tag `DjAudio` no logcat — taxa, tamanho de bloco e rota ao abrir a saída, cada mudança de rota e cada recriação:

```bash
adb logcat -s DjAudio
```

Verificado no aparelho com um fone Bluetooth (JBL), duas vezes: o aviso de desconexão chega ~1 s antes de a rota mudar para o alto-falante, então a música já está pausada quando o som sairia no celular; ao religar, a rota volta ao Bluetooth sem recriar a saída e o play funciona. Também verificado com fone USB-C (adaptador ASUS): ao conectar a rota passou para o USB; ao desconectar, o aviso chegou ~1,4 s antes da troca para o alto-falante e a música pausou. O aparelho de teste não tem entrada P2.

## Biblioteca (SAF)

- A `AndroidTrackRepository` vive no processo (`single` no Koin), não na tela: o `DjViewModel` a usa e sobrevive à
  recriação da Activity. O seletor de pastas depende da Activity, então a Activity o **empresta**
  (`library.folderPicker`) no `onCreate` e o devolve no `onDestroy` — a biblioteca nunca segura uma
  Activity destruída.

- O usuário escolhe pastas (`OpenDocumentTree`); as permissões são persistidas.
- `LibraryEntry(id, name, isFolder, stamp)`. O `id` é `"<tree uri>\n<documentId>"`; `stamp` muda quando o arquivo muda.
- **Limpeza do cache** (`AnalysisCachePruner`, regra comum; o repositório só apaga): ao listar uma pasta,
  somem as entradas de arquivos dela que foram **alterados** (outro `stamp`) ou **removidos/movidos**;
  ao remover uma pasta vinculada, somem todas as entradas dela. Arquivos de subpastas e de outras
  pastas não são tocados. Se a listagem não acontece (provedor indisponível, como um cartão SD fora),
  não limpa nada — vazio por falha não pode apagar o cache.
- Duração e BPM de cada arquivo são analisados em segundo plano (trecho de ~100 s) e guardados em `SharedPreferences("analysis")` com chave `id#stamp`; a lista mostra o valor na hora quando já existe.

## Memória

Duas faixas de 5 min ocupam ~110 MB de PCM. Em segundo plano isso é um bom motivo para o Android matar o processo.

- O `AndroidAudioSystem` registra um `ComponentCallbacks2` e emite `PlatformEvent.MemoryPressure` com
  `onTrimMemory` em `TRIM_MEMORY_BACKGROUND` ou acima, em `RUNNING_LOW`/`RUNNING_CRITICAL`, ou com
  `onLowMemory`. A `MixSession` decide: **só com a tela fora de cena**, guarda o estado e chama
  `releaseIdleAudio()` (decks parados soltam o áudio; o que toca segue tocando).
- **Deck que para depois do pedido**: a sessão lembra que houve pedido de memória com a tela fora de
  cena; todo deck que parar em seguida (pausa pela notificação, interrupção, fim da faixa — tudo passa
  por `MixSession.interrupt`) solta o áudio também. Voltar à tela zera esse aviso.
- **O que o Android realmente manda** (verificado num Android 16): com o serviço tocando, o processo é
  "de primeiro plano" e **não recebe** `TRIM_MEMORY_BACKGROUND` (o `am send-trim-memory` é recusado);
  do Android 14 em diante os níveis `RUNNING_*` estão obsoletos. Na prática, o aviso chega depois que a
  música para e o serviço se encerra. Até o Android 13, `RUNNING_LOW`/`CRITICAL` chegam mesmo tocando, e
  o deck parado solta na hora. Parado em segundo plano, alguns fabricantes (ex.: Motorola) congelam o
  processo e jogam a memória dele para o swap comprimido.
- Com a thread de áudio parada (saída fechada), a sessão também chama `mixer.dropStaleAudio()`: sem isso, o PCM de uma faixa tocada com key lock continuaria preso no stretcher.
- Só decks **parados** e com **origem conhecida** (`LibraryEntry`) soltam o PCM. Ficam: origem, posição, tempo, EQ, volume, SYNC, onda e grade.
- Ao voltar à tela (`mix.setForeground(true)`), o `DjViewModel` recarrega o arquivo de cada deck liberado, mostrando o efeito de carregamento, e chama `restoreAudio`. O play fica inativo enquanto não há áudio.
- Falha ao recarregar (arquivo removido): aviso na tela; o deck fica sem áudio até outra faixa ser carregada.
- Medido num aparelho real: 187 MB → 79 MB de PSS após o pedido de memória (heap Java 92 → 12 MB).

## Estado após o processo ser encerrado

Se o sistema encerrar o processo mesmo assim (pouca memória, app parado há muito tempo), a sessão volta como estava na próxima abertura:

- `MixSession.save()` grava o snapshot pela porta `SessionStore` (no Android, `PrefsSessionStore`: `SharedPreferences("session")`, chave `state`). É chamado em `MainActivity.onStop`, em `pauseAll` (inclui a ação Pausar e o fim do serviço), em `onTrimMemory` e a cada ~1,2 s pelo *watcher* do serviço enquanto a música toca em segundo plano (se o processo for encerrado tocando, perde no máximo isso).
- Guardado por deck (`DeckSnapshot`): origem (`LibraryEntry`), posição, tempo, tempo antes do SYNC, volume, EQ e SYNC. Da sessão: crossfader, master e key lock. O áudio não é guardado.
- Ao nascer, a `MixSession` lê o estado pela porta `SessionStore` e chama `restore()`: o mixer volta na hora; cada deck fica com `pendingRestore` e a tela recarrega o arquivo assim que aparece (`applyRestore`), **pausado** e na mesma posição. A grade de batidas é recalculada da análise (mesmo resultado).
- Escolher outra faixa antes de a recarga terminar cancela a restauração daquele deck. Se o arquivo não existir mais, aparece um aviso e o deck fica vazio.
- Faixas sem origem conhecida não são guardadas. Texto corrompido ou de outra versão é ignorado.
- Verificado no aparelho com `adb shell am kill`: as duas faixas, posição (0:06.0), volume (72%) e SYNC armado voltaram; o play com SYNC travou as batidas normalmente.

## Rótulos de nome

Nome exibido: **GrooveDeck DJ** (`strings.xml` e título da notificação). O `applicationId`/pacote continua `com.anthony.djapp` para preservar o app instalado.
