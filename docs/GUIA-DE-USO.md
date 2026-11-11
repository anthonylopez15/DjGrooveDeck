# Guia de uso

## Primeiros passos

1. Abra o app e toque em **♫** num deck para abrir a biblioteca.
2. Na primeira vez toque em **+ Pasta** e escolha a pasta com suas músicas. O app mostra BPM e duração de cada faixa conforme as analisa.
3. Toque numa faixa para carregá-la no deck escolhido; um preenchimento da esquerda para a direita mostra o progresso.
4. Repita para o outro deck e toque em **play**.

## Os decks

- **Play/Pause** e **voltar ao início** (botão ao lado do play).
- **Buscar**: toque ou arraste na barra de visão geral, ou arraste a onda ampliada.
- **Tempo** (±16%): muda a velocidade. Com **KEY** ligado a afinação é mantida; desligado, acelerar sobe o tom (toca-discos).
- O número no prato é o BPM atual; fica **azul** quando os dois decks têm o mesmo BPM.

> Um deck que está tocando não troca de faixa: pause antes.

## SYNC

- Toque em **SYNC** num deck para ele seguir o andamento do outro.
- Com os dois tocando, as batidas também se alinham e o botão fica **cheio** (travado).
- Dar play num deck com SYNC enquanto o outro toca faz a largada já na batida certa.
- Para soltar: toque no SYNC do seguidor (volta à velocidade anterior) ou no do líder (cada um fica como está).
- Precisa de BPM detectado nos dois decks; andamentos muito diferentes (>16%) não casam.

## Mixer

Aba **MIXER**: EQ de 3 bandas por deck (esquerda total = corta a banda), fader de volume, medidores de nível, **crossfader** (A ↔ B) e **master**.

## Tocar em segundo plano

Com música tocando, ao sair do app ela continua e aparece uma notificação com **Pausar** e atalho para abrir. Ao voltar, tudo está como deixou. Sem música tocando, o áudio é desligado ao sair da tela. Removendo o app dos recentes, a música para.

Em aparelhos com pouca memória, o Android pode pedir para liberar RAM enquanto o app está em segundo plano: as faixas dos decks parados são recarregadas sozinhas do arquivo ao voltar, com posição, tempo e SYNC preservados.

## Problemas comuns

| Sintoma | Causa provável |
|---|---|
| "Pause o deck X para trocar a faixa" | O deck está tocando. |
| BPM `--` | Faixa sem batida clara, ou ainda analisando. SYNC não liga sem BPM. |
| Sem notificação | Permissão de notificações negada; a música continua tocando mesmo assim. |
| "Não consegui abrir/recarregar…" | O arquivo foi movido/removido ou não é um áudio suportado. |
