# Guia de uso

## Primeiros passos

1. Abra o app e toque em **♫** num deck para abrir a biblioteca.
2. Na primeira vez toque em **+ Pasta** e escolha a pasta com suas músicas. O app mostra BPM e duração de cada faixa conforme as analisa. Se você adicionar ou apagar músicas na pasta pelo gerenciador de arquivos do celular, a lista aberta no app **se atualiza sozinha** (e ao abrir a biblioteca de novo), sem recarregar a pasta toda.
3. Toque numa faixa para carregá-la no deck escolhido; um preenchimento da esquerda para a direita mostra o progresso.
4. Repita para o outro deck e toque em **play**.

## Os decks

- **Play/Pause** e **voltar ao início** (botão ao lado do play).
- **Buscar**: toque ou arraste na barra de visão geral, ou arraste a onda ampliada.
- **Tempo** (±20%): muda a velocidade. Com **KEY** ligado a afinação é mantida; desligado, acelerar sobe o tom (toca-discos).
- O número no prato é o BPM atual; fica **azul** quando os dois decks têm o mesmo BPM.

> Um deck que está tocando não troca de faixa: pause antes.

## Andamento (acelerar e desacelerar)

Embaixo de cada prato há **[ − ] SYNC [ + ]**:

- **+ / −**: cada toque muda o andamento de **1 em 1%** (+1,0%, +2,0%...). **Segurando**, repete (um passo a
  cada 0,15 s). O limite é **±20%**; o percentual aparece no prato, e o BPM efetivo muda junto.
- **Tocar no percentual** (o "+6.7%" no prato) volta ao andamento original (0%).
- O **BPM embaixo do nome da música** (no cabeçalho) é sempre o **BPM fixo** da faixa, o mesmo da lista de
  músicas, e não muda com o andamento. O número grande no **prato** é o BPM com o andamento aplicado (BPM fixo
  × andamento): é nele que você vê o efeito dos botões.
- Com **KEY** ligado, a música acelera ou desacelera **sem mudar o tom**; desligado, o tom acompanha (como num toca-discos).
- **SYNC:** mexer no andamento de um deck que **segue** o outro desliga o SYNC dele (você assumiu o controle).
  Mexer no de quem **lidera** faz o seguidor acompanhar, e as batidas se alinham de novo se os dois tocam.

## SYNC

- Toque em **SYNC** num deck para ele seguir o andamento do outro.
- Com os dois tocando, as batidas também se alinham e o botão fica **cheio** (travado).
- **Largada com o outro deck tocando:** o play espera só a **próxima batida** do outro deck (menos de 1 s) e entra
  no ritmo; a faixa começa de onde estava, não no tempo 1.
- Enquanto espera a batida, o botão de play fica com o **anel grosso e preenchido**; tocar de novo **cancela**.
- **Largada com o outro deck parado:** com o SYNC ligado,
  - o play toca já, a partir do tempo 1 mais próximo de onde estava (a posição "encaixa").
  - Sem SYNC, nada muda: a faixa toca de onde parou.
  - O "tempo 1" é o das linhas laranja (uma a cada 4 batidas, contadas da primeira batida detectada). O app
    acha o ritmo, mas não sabe qual batida abre o compasso: se a detectada não é o "um" musical, os dois decks
    ainda entram juntos, só que num tempo diferente do "um" da música.
- Para soltar: toque no SYNC do seguidor (volta à velocidade anterior) ou no do líder (cada um fica como está).
- Precisa de BPM detectado nos dois decks; andamentos muito diferentes (>20%) não casam.

## Mixer

Aba **MIXER**: EQ de 3 bandas por deck (esquerda total = corta a banda), fader de volume, medidores de nível, **crossfader** (A ↔ B) e **master**.

O botão **FADE** (na barra de abas, ao lado de KEY, visível nas duas abas, junto do SYNC e do crossfader) passa o crossfader sozinho, de forma suave (cerca de 5 s de ponta a ponta), até a ponta do outro deck quando ele está no último quarto do lado de um deck que está tocando (até 25% para A, a partir de 75% para B). Se o deck de destino está parado (e tem faixa), ele dá play quando o fade começa e o deck de origem **só pausa quando o fade chega a 100%**; se o destino já toca, ninguém é pausado. Ao chegar, o botão **solta** (volta a não apertado) e o crossfader fica na ponta; para passar de volta, aperte de novo. Mexer no crossfader com o dedo, desligar o botão interrompe a passagem; se a faixa de origem acabar (ou for pausada) no meio do fade, o crossfader segue até o fim. O botão fica apertado durante o fade. Ela anda junto com a tela (a cada quadro); com a tela fora de cena espera. O botão volta desligado ao reabrir o app.

## Ficar muito tempo sem usar

Se o app ficar **mais de 30 minutos sem uso**, ele solta as faixas da memória e **abre limpo**, sem reabrir as
últimas músicas (aparece o aviso "Faixas descarregadas: o app ficou mais de 30 min sem uso"). Vale tanto
quando o Android encerrou o app quanto quando você volta a um app que ficou aberto em segundo plano. O que
**não** é descarregado: volume, EQ, crossfader, master e KEY. Música **tocando** em segundo plano conta como
uso e nunca é descarregada. Menos de 30 minutos: tudo volta como estava, como antes.

## Tocar em segundo plano

Com música tocando, ao sair do app ela continua e aparece uma notificação com **Pausar** e atalho para abrir. Ao voltar, tudo está como deixou. Sem música tocando, o áudio é desligado ao sair da tela. Removendo o app dos recentes, a música para.

Em aparelhos com pouca memória, o Android pode pedir para liberar RAM enquanto o app está em segundo plano: as faixas dos decks parados são recarregadas sozinhas do arquivo ao voltar, com posição, tempo e SYNC preservados. Mesmo que o sistema feche o app, na próxima abertura as faixas voltam (pausadas), no ponto em que estavam.

## Interrupções

- **Fone ou Bluetooth desconectou**: a música pausa (não vai para o alto-falante do celular).
- **Outro app começou a tocar, ou chegou uma ligação**: a música pausa. Ela não volta sozinha: dê play quando quiser (com SYNC, as batidas se alinham de novo).
- **Som de notificação**: o volume abaixa um instante e volta.

## Problemas comuns

| Sintoma | Causa provável |
|---|---|
| "Pause o deck X para trocar a faixa" | O deck está tocando. |
| "Faixa longa demais (17:20): o limite é de 15 min" | O app aceita faixas de até 15 min (na lista, elas aparecem com "· longa" em amarelo). |
| "Faixa longa demais para a memória livre agora" | Com o que já está carregado no outro deck, não sobra memória para esta faixa. |
| BPM `--` | Faixa sem batida clara, ou ainda analisando. SYNC não liga sem BPM. |
| "Não dá para tocar agora" | Há uma ligação em andamento (o sistema não libera o áudio). |
| Sem notificação | Permissão de notificações negada; a música continua tocando mesmo assim. |
| "Não consegui abrir/recarregar…" | O arquivo foi movido/removido ou não é um áudio suportado. |
