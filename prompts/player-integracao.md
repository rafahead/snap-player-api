# Plano Player (Flutter `snap-player`)

## Papel deste arquivo (Plano Player)

Estudo e planejamento de integração do cliente Flutter com a API. Não é fonte de verdade de domínio nem de arquitetura de processamento.

Referências:
- `prompts/README.md` (governança)
- `prompts/master-produto-snap.md` (Master Produto / Snap-first)
- `prompts/entregas-api-snap-v2.md` (Plano de Entregas)

## Objetivo

Documentar o estado atual do projeto Flutter `snap-player` (repositório local atualmente em `/home/rafahead/workspaces/flutter/oddplayer`) para planejar a unificação com a API `snap-player-api` (base atual e plano master), sem alterar o app ainda.

Este documento serve como base de conversa para:
- definir o papel do player na arquitetura final
- identificar pontos de integração com a API de extração
- priorizar refatorações no Flutter antes da integração

## Projeto estudado

- Caminho local atual: `/home/rafahead/workspaces/flutter/oddplayer` (path legado; nome do produto: `snap-player`)
- Stack: Flutter + `video_player`
- Descrição atual: `Boi frame a frame`

## Resumo do estado atual (observado)

### O que o app faz hoje

- Tela inicial simples para informar:
  - URL do vídeo MP4
  - FPS
- Abre um player frame-a-frame com:
  - play/pause
  - avanço/retrocesso por frame
  - avanço/retrocesso por blocos de frames
  - salto por segundos
  - atalho de teclado
  - clique/tap no vídeo para play/pause
  - gesto horizontal para step frame
  - zoom (`InteractiveViewer`)
- Exibe timeline + tempo/frame atual + velocidade de reprodução
- Possui painel flutuante de controles
- Possui lista flutuante de “animais” (mock) sincronizada com progresso do vídeo

### O que isso indica para a unificação

O app já resolve bem a camada de inspeção visual frame-a-frame local/remota, mas ainda não conversa com a API de extração/processamento. Ele está mais próximo de um protótipo funcional de UX do que de um cliente integrado.

## Estrutura observada (relevante)

### Arquivos principais

- `lib/main.dart` (app principal atual; ~440 linhas, monolítico)
- `lib/mainv1.dart` (versão anterior)
- `lib/mainv2.dart` (versão intermediária)

### Configuração / dependências

- `pubspec.yaml`
  - `video_player`
  - `cupertino_icons`
- Sem libs de HTTP/estado persistente/serialização explícitas no momento

### Scripts utilitários

- `rodarCelular.sh`
  - sobe em `web-server` na porta `8080`
- `deploy.sh`
  - build web release + `scp` para servidor remoto

### Testes

- `test/widget_test.dart` ainda é o teste padrão de contador (incompatível com o app atual; precisa ser substituído quando iniciar integração real)

## Leitura técnica do `main.dart` (pontos importantes)

### Fluxo atual

1. `VideoSetupScreen`
   - recebe URL e FPS
   - navega para `FrameByFramePlayer`
2. `FrameByFramePlayer`
   - inicializa `VideoPlayerController.networkUrl`
   - calcula stepping por frame baseado em FPS informado manualmente
   - renderiza player + overlay de interação + painel flutuante + lista flutuante

### Pontos fortes

- UX de inspeção já utilizável para análise manual.
- Atalhos e stepping por frame funcionam como base de trabalho.
- Estrutura visual já aponta para um operador humano usando o sistema.

### Limitações para integração

- `main.dart` está monolítico (UI, estado, player, atalhos, mocks no mesmo arquivo).
- FPS é digitado manualmente (não usa `videoProbe` da API).
- Não existe camada HTTP/API client.
- Não existe modelo de dados para `subject`, jobs, frames, snapshots.
- Lista de animais é mock local (`List.generate(80, ...)`), sem vínculo com resultados reais.
- Sem persistência local de sessão/últimos vídeos.
- Teste automatizado não cobre a interface real.

## Como este player pode se integrar ao `snap-player-api`

### Papel sugerido do player no ecossistema

O player pode virar o **cliente de inspeção** dos resultados da API:
- enviar vídeos para processamento (MVP/master)
- visualizar `snapshot.mp4`
- navegar frames extraídos
- consultar resultados por `subject.id` e atributos
- auxiliar rotulação/decisão humana

### Integrações de curto prazo (com MVP atual)

1. Chamar `POST /v1/video-frames/mvp/process`
   - enviar `videoUrl`, recortes, `subject`, `overlay`
   - exibir status/resposta consolidada
2. Abrir `snapshot.mp4` retornado localmente (ou URL futura)
3. Exibir grade/lista de frames retornados
4. Navegar usando frame timestamps retornados pela API

### Integrações de médio prazo (com master)

1. Criar fluxo assíncrono:
   - `POST` cria `batch`
   - `GET` acompanha progresso
   - exibir polling + progresso por item
2. Consulta por `subject.id`
   - carregar histórico de filmagens processadas
3. Busca por atributos (`subject.attributes`)
   - igualdade/string e faixa/number
4. Consumo de URLs S3/Linode Object Storage

## Proposta de arquitetura Flutter para a unificação (sem implementar ainda)

### Fase 1 (refatoração mínima, antes da integração)

Separar `lib/main.dart` em módulos:
- `features/setup/` (tela de entrada)
- `features/player/` (player frame-a-frame)
- `features/results/` (resultado de processamento)
- `shared/` (widgets comuns)
- `core/` (models, api, config)

### Fase 2 (cliente MVP)

Adicionar:
- cliente HTTP para `snap-player-api`
- modelos de request/response do MVP (`subject`, `videoProbe`, `frames`, `snapshotVideo`)
- tela de envio/processamento
- tela de resultado (snapshot + frames)

### Fase 3 (cliente master)

Adicionar:
- polling de batch assíncrono
- listagem de resultados por `subject.id`
- filtros de atributos
- paginação

## Contratos que o player deve adotar (alinhamento com API)

### `subject` (genérico)

O player não deve mais modelar domínio fixo no contrato (ex.: `brinco`, `peso` como campos soltos). Deve adotar:

```json
{
  "subject": {
    "id": "animal-123",
    "attributes": [
      { "key": "brinco", "type": "string", "stringValue": "12334234534" },
      { "key": "peso", "type": "number", "numberValue": 450.0 }
    ]
  }
}
```

### Recorte

- aceitar `startSeconds` ou `startFrame`
- se ambos forem enviados, `startFrame` tem precedência (regra já validada no MVP)

### Durações

- `durationSeconds` = frames/imagens
- `snapshotDurationSeconds` = clip (`snapshot.mp4`)

## Decisões a discutir antes de implementar no player

1. O player será:
   - somente ferramenta de inspeção local?
   - ou cliente completo da API (envio + consulta + revisão)?
2. O fluxo principal começa por:
   - URL do vídeo (como hoje)?
   - ou busca por `subject.id`?
3. A lista flutuante atual representa:
   - subjects reais retornados pela API?
   - anotações do operador?
   - uma timeline de eventos?
4. O player vai exibir:
   - vídeo original
   - `snapshot.mp4`
   - frames extraídos
   - ou combinação dos três?
5. Precisamos de modo offline/local para revisar resultados já baixados?

## Entregável esperado desta etapa

- Este `player-integracao.md` documenta o estado atual do app Flutter e propõe uma rota de integração com `snap-player-api`.
- Nenhuma alteração foi feita no projeto Flutter nesta etapa.
- Próximo passo é definir o papel do player (cliente MVP vs cliente master) e então atualizar o plano de unificação.
