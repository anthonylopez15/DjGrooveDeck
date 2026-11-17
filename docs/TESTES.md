# Testes

## Executar

Tudo de uma vez (lint + testes + Android Lint + compilação comum do iOS): `./gradlew checkAll`.

```bash
./gradlew :engine:jvmTest                                  # motor, na JVM (rápido)
./gradlew :shared:testAndroidHostTest                       # lógica do controller/SYNC
./gradlew :engine:jvmTest :shared:testAndroidHostTest       # tudo
```

Relatórios em `*/build/reports/tests/`. Saída de métricas do teste de carga: use `--info` para ver as linhas `STRESS:` e `STRESS_RTF=`.

## Suítes (97 testes)

| Arquivo | Cobre |
|---|---|
| `EngineTest` | Resample, ganhos, crossfader, EQ (incluindo *kill*), soft clip, posição, regras do deck. |
| `KeyLockTest` | Stretcher: identidade em tempo 1,0, duração, continuidade. |
| `KeyLockQualityTest` | Afinação preservada em tempos 0,97/1,03 (regressão do erro de ~52 centésimos). |
| `BpmDetectorTest` | BPM e fase em faixas sintéticas, oitava 80–160, rejeição de tom puro/ruído/faixa curta. |
| `MixerAlignTest` | Alinhamento na thread de áudio, largada quantizada, fusão de pedidos. |
| `ReleaseAudioTest` | Liberar/restaurar PCM, recusa com deck tocando, render sem áudio, faixa com key lock liberada com a thread parada. |
| `EngineStressTest` | Carga: operações aleatórias reproduzíveis (semente fixa) por ~46 s de áudio e muitas buscas com key lock. |
| `SyncTest` (`shared`, sessão) | Estados do botão SYNC, tempo, trava, desligar, liberação de áudio no controller, pausa de fora da tela (desfaz o encaixe e a largada pendente). |
| `SessionRestoreTest` (`shared`, sessão) | Formato do estado salvo (ida e volta, texto corrompido), restauração num controller novo, cancelamento ao escolher outra faixa. |
| `DjViewModelTest` (`shared`, apresentação) | Ações viram estado, biblioteca só com o deck parado, carregar faixa (progresso e erro), avisos que somem, recarga após o processo morrer e após liberar memória, sem repetir sem parar uma recarga que falhou. |
| `PlaybackGateTest` (`shared`, sessão) | O play passa pela porta `AudioSystem`: negado não toca e avisa; pausar e deck vazio não pedem. |
| `PlatformEventsTest` (`shared`, sessão) | "A plataforma detecta, a sessão decide": restaura do `SessionStore` ao nascer; interrupção tocando pausa, salva e avisa; parado não faz nada; falta de memória só age com a tela fora de cena; pausa pedida sempre salva. |
| `SyncRulesTest` (`shared`, domínio) | Regras puras do SYNC e do transporte: BPM igual, travado, tempo do seguidor e limite, decisões de SYNC e de play. |
| `InterruptionPolicyTest` (`shared`, domínio) | Reação a cada interrupção, tocando e parado. |
| `KoinModulesTest` (`shared`) | Os módulos comuns do Koin montam a sessão (uma só) e o ViewModel (um por tela) com uma plataforma falsa. |
| `AndroidModulesVerifyTest` (`shared`, host) | O grafo Android completo do Koin tem tudo o que os construtores pedem (pega definição faltando antes de o app abrir). |
| `ArchitectureTest` (`shared`, host) | O domínio não importa Android, Compose, iOS nem camadas externas (ADR 0001). |
| `FormatTest` (`shared`) | Porcentagens do volume e dos knobs. |

### Teste de carga do motor

Dois decks com key lock tocando enquanto uma sequência pseudoaleatória de ~6 mil operações (busca, tempo, EQ, volume, crossfader, alinhamento, troca de faixa, key lock) bate no motor. Garante: nenhuma saída `NaN`/infinita, pico ≤ 1, posição dentro da faixa, nenhuma exceção, e **fator de tempo real < 1** (o render precisa ser mais rápido que o áudio).

## Verificação no aparelho (manual, via adb)

Úteis para estressar o app de verdade:

```bash
adb shell input swipe ...                 # arrastar ondas/faders em rajada
adb shell dumpsys meminfo com.anthony.djapp
adb shell dumpsys audio | grep -i state   # streams ativos
adb shell dumpsys gfxinfo com.anthony.djapp   # quadros lentos
adb shell am send-trim-memory com.anthony.djapp BACKGROUND
adb shell kill -10 $(adb shell pidof com.anthony.djapp)   # força GC para medir memória
adb shell uiautomator dump /sdcard/u.xml  # ler textos/estado da tela
```

Roteiros já executados com sucesso:
- Tempestade de toques/arrastos com música tocando.
- 12 trocas de faixa seguidas (verificação de vazamento de memória).
- Análise de pasta inteira enquanto a música toca.
- Sair e voltar rapidamente várias vezes (corrida entre serviço e tela).
- Notificação e ação Pausar; remoção dos recentes.
- Pedido de memória em segundo plano e recarga ao voltar.
- Foco de áudio: com um deck tocando, `adb shell am start -a android.intent.action.VIEW -d content://media/external/audio/media/<id> -t audio/mpeg` abre a música em outro player, que toma o foco; o set deve pausar (`adb shell dumpsys audio | grep -A3 "Audio Focus stack"` mostra quem tem o foco).
- Saída de áudio ociosa: `adb shell dumpsys audio | grep AudioPlaybackConfiguration` não mostra o app com ele aberto e parado; mostra ao tocar; some ~5 s depois de pausar.
- Processo encerrado em segundo plano (`adb shell am kill com.anthony.djapp`) e restauração ao reabrir.

- Desligar e religar um fone Bluetooth com música tocando (acompanhar com `adb logcat -s DjAudio`): pausa na desconexão, rota volta ao religar. O adb não consegue simular isso (o Android bloqueia o aviso vindo do shell); precisa de um fone de verdade.

Também verificado com fone USB-C (desconectar o celular do computador para o teste; o log fica no aparelho e é lido depois com `adb logcat -d`). Não verificado: o caminho de recriação do `AudioTrack` após `DEAD_OBJECT` (não aconteceu nos testes) e o erro de memória com arquivos gigantes (só compilado).
