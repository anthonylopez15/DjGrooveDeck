# Testes

## Executar

```bash
./gradlew :engine:jvmTest                                  # motor, na JVM (rápido)
./gradlew :shared:testAndroidHostTest                       # lógica do controller/SYNC
./gradlew :engine:jvmTest :shared:testAndroidHostTest       # tudo
```

Relatórios em `*/build/reports/tests/`. Saída de métricas do teste de carga: use `--info` para ver as linhas `STRESS:` e `STRESS_RTF=`.

## Suítes (61 testes)

| Arquivo | Cobre |
|---|---|
| `EngineTest` | Resample, ganhos, crossfader, EQ (incluindo *kill*), soft clip, posição, regras do deck. |
| `KeyLockTest` | Stretcher: identidade em tempo 1,0, duração, continuidade. |
| `KeyLockQualityTest` | Afinação preservada em tempos 0,97/1,03 (regressão do erro de ~52 centésimos). |
| `BpmDetectorTest` | BPM e fase em faixas sintéticas, oitava 80–160, rejeição de tom puro/ruído/faixa curta. |
| `MixerAlignTest` | Alinhamento na thread de áudio, largada quantizada, fusão de pedidos. |
| `ReleaseAudioTest` | Liberar/restaurar PCM, recusa com deck tocando, render sem áudio. |
| `EngineStressTest` | Carga: operações aleatórias reproduzíveis (semente fixa) por ~46 s de áudio e muitas buscas com key lock. |
| `SyncTest` (`shared`) | Estados do botão SYNC, tempo, trava, desligar, liberação de áudio no controller. |
| `SessionRestoreTest` (`shared`) | Formato do estado salvo (ida e volta, texto corrompido), restauração num controller novo, cancelamento ao escolher outra faixa. |
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
- Processo encerrado em segundo plano (`adb shell am kill com.anthony.djapp`) e restauração ao reabrir.

- Desligar e religar um fone Bluetooth com música tocando (acompanhar com `adb logcat -s DjAudio`): pausa na desconexão, rota volta ao religar. O adb não consegue simular isso (o Android bloqueia o aviso vindo do shell); precisa de um fone de verdade.

Não verificado: fone USB-C (o aparelho de teste não tem entrada P2), o caminho de recriação do `AudioTrack` após `DEAD_OBJECT` (não aconteceu nos testes) e o erro de memória com arquivos gigantes (só compilado).
