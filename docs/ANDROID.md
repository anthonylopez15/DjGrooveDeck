# Android (`androidApp`)

## Componentes

| Classe | Papel |
|---|---|
| `MainActivity` | Liga a tela Compose à sessão; controla visibilidade e saída de áudio; pede a permissão de notificação uma vez. |
| `DjSession` | Objeto do processo com `AndroidAudioOutput`, `DjMixer` e `DjController`. `activityVisible` indica se a tela está à vista. |
| `PlaybackService` | Serviço em primeiro plano (`mediaPlayback`) com notificação. |
| `AndroidLibrary` | Biblioteca via Storage Access Framework + cache de análise. |
| `AudioFocus` | Foco de áudio: pedido antes de tocar; perda pausa o set. |

Manifest: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`, `largeHeap`, serviço `.PlaybackService` com `foregroundServiceType="mediaPlayback"` e `stopWithTask`.

## Ciclo de vida e segundo plano

```
onStart / onResume   activityVisible = true, controller.foreground = true
onUserLeaveHint      (início/recentes) se tocando: activityVisible = false e inicia o serviço
onStop               foreground = false, salva; se tocando inicia o serviço, senão output.stop()
```

**Saída de áudio só quando precisa**: a tela não abre a saída. O play (`DjController.playbackGate`) pede o foco e liga a saída; com os dois decks parados por **5 s** (tela à vista), um vigia na `DjSession` a desliga. Com a saída aberta o sistema considera o app tocando (silêncio) e gasta bateria. O primeiro play depois disso abre o `AudioTrack` de novo (alguns ms). Log: `saída aberta` / `saída fechada` na tag `DjAudio`.

- `onUserLeaveHint` é o momento em que o Android ainda permite iniciar o serviço sem restrição; `onStop` fica de reserva (por exemplo, tela desligando).
- **Serviço**: a cada 400 ms um *watcher* checa: se a tela voltou, o serviço se encerra mantendo o áudio; se nada toca, encerra e desliga a saída. `lastStartId` garante que `stopSelf(id)` não seja ignorado quando chega um novo start. Remover o app dos recentes (`onTaskRemoved`) para tudo.
- **Notificação**: toque abre o app; ação **Pausar** pausa os dois decks.
- Sem a permissão `POST_NOTIFICATIONS` (Android 13+) a música segue tocando; só a notificação não aparece.

## Pausas de fora da tela

Toda pausa que não vem de um botão da tela — ação **Pausar** da notificação, perda do foco de áudio, saída desconectada, fim do serviço — passa por `DjSession.pauseAll()` → `DjController.pauseAll()`, que pausa os dois decks, desfaz o encaixe de batidas (`phaseLocked`) e descarta uma largada alinhada ainda pendente no mixer (`DjMixer.cancelAlign`); depois o estado é salvo. Pausar o motor direto deixava o controller achando que as batidas seguiam travadas.

## Foco de áudio (ligações, outros apps)

- Antes de um deck começar a tocar, `DjController.playbackGate` pede o foco (`AUDIOFOCUS_GAIN`, mídia/música). Negado (ex.: ligação em andamento): o play não acontece e aparece "Não dá para tocar agora (ligação em andamento?)". Pausar não pede nada.
- **Outro app começou a tocar música** (perda definitiva): pausa os dois decks, solta o foco e avisa "Música pausada: outro app começou a tocar".
- **Ligação ou assistente de voz** (perda temporária): pausa e avisa "Música pausada (ligação ou outro áudio)". **Não volta a tocar sozinho** quando o foco volta: num set, o DJ decide quando retomar, e os dois decks precisam voltar alinhados (dar play com SYNC realinha).
- **Som de notificação** (perda breve com volume baixo): o próprio Android abaixa o volume e devolve; o app não faz nada.
- Em Android 14+ o sistema faz um *fade* de ~2 s no app que perde o foco antes de avisá-lo; por isso o set ainda soa um instante depois de o outro app começar.
- Verificado no aparelho: com um deck tocando, abrir uma música no YouTube Music tomou o foco e o set pausou. Não verificado: ligação real.

## Troca de saída de áudio (fone, Bluetooth)

- **Saída desconectada com música tocando** (fone com fio sai, Bluetooth desliga ou cai): `DjSession` recebe `ACTION_AUDIO_BECOMING_NOISY`, pausa os dois decks, guarda o estado e mostra "Saída de áudio desconectada: música pausada". Sem isso o Android passaria o som para o alto-falante no meio do set.
- **Saída conectada**: o próprio Android redireciona o `AudioTrack` aberto; não é preciso recriar. Se uma escrita falhar (`DEAD_OBJECT`), `AndroidAudioOutput` recria o track.
- **Diagnóstico**: tag `DjAudio` no logcat — taxa, tamanho de bloco e rota ao abrir a saída, cada mudança de rota e cada recriação:

```bash
adb logcat -s DjAudio
```

Verificado no aparelho com um fone Bluetooth (JBL), duas vezes: o aviso de desconexão chega ~1 s antes de a rota mudar para o alto-falante, então a música já está pausada quando o som sairia no celular; ao religar, a rota volta ao Bluetooth sem recriar a saída e o play funciona. Também verificado com fone USB-C (adaptador ASUS): ao conectar a rota passou para o USB; ao desconectar, o aviso chegou ~1,4 s antes da troca para o alto-falante e a música pausou. O aparelho de teste não tem entrada P2.

## Biblioteca (SAF)

- O usuário escolhe pastas (`OpenDocumentTree`); as permissões são persistidas.
- `LibraryEntry(id, name, isFolder, stamp)`. O `id` é `"<tree uri>\n<documentId>"`; `stamp` muda quando o arquivo muda.
- Duração e BPM de cada arquivo são analisados em segundo plano (trecho de ~100 s) e guardados em `SharedPreferences("analysis")` com chave `id#stamp`; a lista mostra o valor na hora quando já existe.

## Memória

Duas faixas de 5 min ocupam ~110 MB de PCM. Em segundo plano isso é um bom motivo para o Android matar o processo.

- `DjSession` registra um `ComponentCallbacks2`. Com `onTrimMemory(level ≥ TRIM_MEMORY_BACKGROUND)` ou `onLowMemory`, **e a tela fora de vista**, chama `controller.releaseIdleAudio()`.
- Com a thread de áudio parada (saída fechada), a sessão também chama `mixer.dropStaleAudio()`: sem isso, o PCM de uma faixa tocada com key lock continuaria preso no stretcher.
- Só decks **parados** e com **origem conhecida** (`LibraryEntry`) soltam o PCM. Ficam: origem, posição, tempo, EQ, volume, SYNC, onda e grade.
- Ao voltar à tela (`controller.foreground = true`), `DjApp` recarrega o arquivo de cada deck liberado, mostrando o efeito de carregamento, e chama `restoreAudio`. O play fica inativo enquanto não há áudio.
- Falha ao recarregar (arquivo removido): aviso na tela; o deck fica sem áudio até outra faixa ser carregada.
- Medido num aparelho real: 187 MB → 79 MB de PSS após o pedido de memória (heap Java 92 → 12 MB).

## Estado após o processo ser encerrado

Se o sistema encerrar o processo mesmo assim (pouca memória, app parado há muito tempo), a sessão volta como estava na próxima abertura:

- `DjSession.Session.save()` grava `controller.snapshot().encode()` em `SharedPreferences("session")`, chave `state`. É chamado em `MainActivity.onStop`, em `pauseAll` (inclui a ação Pausar e o fim do serviço), em `onTrimMemory` e a cada ~5 s pelo *watcher* do serviço enquanto a música toca em segundo plano.
- Guardado por deck (`DeckSnapshot`): origem (`LibraryEntry`), posição, tempo, tempo antes do SYNC, volume, EQ e SYNC. Da sessão: crossfader, master e key lock. O áudio não é guardado.
- Ao nascer, a `Session` lê o estado e chama `controller.restore()`: o mixer volta na hora; cada deck fica com `pendingRestore` e a tela recarrega o arquivo assim que aparece (`applyRestore`), **pausado** e na mesma posição. A grade de batidas é recalculada da análise (mesmo resultado).
- Escolher outra faixa antes de a recarga terminar cancela a restauração daquele deck. Se o arquivo não existir mais, aparece um aviso e o deck fica vazio.
- Faixas sem origem conhecida não são guardadas. Texto corrompido ou de outra versão é ignorado.
- Verificado no aparelho com `adb shell am kill`: as duas faixas, posição (0:06.0), volume (72%) e SYNC armado voltaram; o play com SYNC travou as batidas normalmente.

## Rótulos de nome

Nome exibido: **GrooveDeck DJ** (`strings.xml` e título da notificação). O `applicationId`/pacote continua `com.anthony.djapp` para preservar o app instalado.
