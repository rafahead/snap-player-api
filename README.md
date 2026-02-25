# Snap Player API

API para extracao de frames e snapshot de video com FFmpeg, com foco em um contrato generico de metadados (`subject`) para reutilizacao em diferentes dominios.

## Resumo de Planejamento (Prompts)

Este repositorio usa os arquivos em `prompts/` como documentacao viva (`docs-as-code`).

Fontes de verdade:
- `prompts/master-tecnico.md`: arquitetura técnica alvo (assíncrona, worker, PostgreSQL/Flyway, S3, FFmpeg/FFprobe, consultas por `subject`)
- `prompts/master-produto-snap.md`: modelo de produto `Snap`-first (`Snap`, `Video`, `Assinatura`, `Usuário`, `SubjectTemplate`, regras de colaboração)
- `prompts/entregas-api-snap-v2.md`: sequenciamento por entregas (o que entra em cada fase)
- `prompts/adrs/`: decisões estruturais estáveis (ADRs)

ADRs já aceitos (resumo):
- `Snap` é a entidade principal do produto
- fase inicial da API v2 é síncrona (reaproveitando o MVP)
- single domain com isolamento lógico por `assinatura_id`
- template padrão por assinatura + fallback de `subject.id = snapId`

Fases/entregas planejadas (API):
- `Entrega 1`: API `Snap`-first síncrona (sem player, sem auth, storage local)
- `Entrega 2`: compartilhamento público + listas `mine`
- `Entrega 3`: base para multi-assinatura/token/paginação/observabilidade mínima
- `Entrega 4`: migração para processamento assíncrono com worker/DB (iniciada; slices 1-3 de fila local/worker/retry/cleanup/telemetria)

Regra de atualização dos planos:
1. atualizar `master` correto (`técnico` ou `produto`)
2. atualizar `entregas`
3. registrar ADR se a decisão for estrutural
4. só depois refletir no código

## Estado do Projeto

- `MVP` implementado (sincrono, local, sem banco/S3)
- `Entrega 1` da API `v2` implementada (Snap-first síncrona com persistência local em banco + Flyway)
- `Entrega 2` da API `v2` implementada (share público + listas `mine`)
- `Entrega 3` da API `v2` implementada (contexto de assinatura + token por feature flag + paginação/ordenação + observabilidade mínima)
- `Entrega 4` (slices 1-3) implementada parcialmente: fila em banco (`snap_processing_job`) + worker local com `SKIP LOCKED` + modo assíncrono opcional no `POST /v2/snaps` + retry/backoff/stale-recovery + cleanup + telemetria de jobs
- `Master plan` documentado para evolucao assíncrona com PostgreSQL, workers e storage S3

Arquivos de plano:
- `prompts/mvp-tecnico.md`
- `prompts/master-tecnico.md`
- `prompts/master-produto-snap.md`
- `prompts/entregas-api-snap-v2.md`

Coleção de chamadas HTTP para uso local/manual:
- `http/v1-processing.http`
- `http/v2-snaps.http`
- `http/v2-share-public.http`
- `http/internal-observability.http`

Observação (IntelliJ HTTP Client):
- `http/v2-snaps.http` captura automaticamente `lastSnapId` e `lastVideoId` após `POST /v2/snaps`
- `http/v2-share-public.http` captura automaticamente `lastPublicShareToken` após `POST /share`

Configs compartilhadas do IntelliJ (`.run/`):
- `Snap Player API (Sync)` (modo padrão, create síncrono)
- `Snap Player API (Async)` (habilita `app.snap.asyncCreateEnabled=true`)

## O Que o MVP Faz

- Recebe um JSON array com filmagens
- Faz `ffprobe` por item para validar compatibilidade e ler propriedades do video
- Extrai frames (JPG/PNG)
- Gera `snapshot.mp4`
- Salva tudo em diretorio temporario local
- Retorna JSON consolidado com:
  - `videoProbe`
  - `resolvedStartSeconds`
  - `snapshotVideo`
  - lista de `frames`
- Suporta `overlay` com `drawtext` (FFmpeg), renderizando **o maximo de informacoes do `subject`** no frame e no snapshot

## Contrato de Entrada (MVP)

Cada item da lista deve conter:

- `videoUrl`
- `dataFilmagem`
- `subject`:
  - `id` (obrigatorio)
  - `attributes[]` (opcional), com atributos tipados:
    - `type = "string"` + `stringValue`
    - `type = "number"` + `numberValue`

Tambem suporta:
- `startSeconds` ou `startFrame` (`startFrame` tem precedencia)
- `durationSeconds` (frames)
- `snapshotDurationSeconds` (clip; opcional, fallback para `durationSeconds`)
- `fps`, `maxWidth`, `format`, `quality`
- `overlay` (opcional)

## Requisitos

- Java 17 (testado: `17.0.7 LTS`)
- Maven 3.9+
- `ffmpeg` no `PATH`
- `ffprobe` no `PATH`
- Para overlay com `drawtext`:
  - FFmpeg com filtro `drawtext` habilitado
  - fonte TTF disponivel (padrao: DejaVu Sans Bold)

## Instalacao (Ambiente)

### Ubuntu/Debian (exemplo)

```bash
sudo apt update
sudo apt install -y ffmpeg fontconfig fonts-dejavu-core
```

Verificar `drawtext`:

```bash
ffmpeg -hide_banner -filters | grep drawtext
```

Se aparecer algo como `drawtext ... using libfreetype`, o overlay deve funcionar.

## Build e Testes

Rodar testes:

```bash
mvn -Dmaven.repo.local=.m2/repository test
```

O projeto possui testes unitarios para:
- validacao de controller
- montagem segura de comandos FFmpeg
- probe (`ffprobe`)
- storage temporario
- overlay `drawtext` no comando

## Como Rodar (MVP)

Subir a API:

```bash
mvn -Dmaven.repo.local=.m2/repository spring-boot:run
```

Se precisar de `fps` maior que o limite padrao:

```bash
mvn -Dmaven.repo.local=.m2/repository spring-boot:run '-Dspring-boot.run.arguments=--app.processing.maxFps=30'
```

## Endpoint MVP

- `POST /v1/video-frames/process` (canônico atual)
- `POST /v1/video-frames/mvp/process` (alias legado compatível)

## Entrega 1 (API v2 Snap-first síncrona)

Implementado nesta fase:
- `POST /v2/snaps` (criação síncrona de snap, com `videoId` ou `videoUrl`)
- `GET /v2/snaps/{snapId}` (consulta detalhada)
- `GET /v2/videos/{videoId}/snaps` (listagem por vídeo, filtro opcional `nickname`, paginação/ordenação)
- `GET /v2/snaps/search` (busca básica por `subjectId` e/ou `attrKey` + `attrValue` string, paginação/ordenação)
- Persistência com Flyway/JPA para:
  - `assinatura` (seed `default`)
  - `subject_template` (seed `default`)
  - `usuario`
  - `video`
  - `snap`
  - `snap_subject_attr`

Regras aplicadas na Entrega 1:
- assinatura ativa fixa: `default`
- template padrão por assinatura (fallback automático)
- fallback de `subject.id` para `snapId` quando ausente
- processamento síncrono reaproveitando o pipeline do MVP (`ffprobe` + `ffmpeg`)
- storage local (paths persistidos no `snap`)

### Banco padrão local (dev)

Por padrão a aplicação sobe com `H2` file-based + `Flyway`:
- `spring.datasource.url=jdbc:h2:file:./.data/snapplayerapi;MODE=PostgreSQL;...`
- `app.processing.tmpBase=./.data/tmp/video-frames-processing` (diretório local gravável por padrão)

Isso permite testar a `v2` sem configurar PostgreSQL de imediato.

### Exemplo `POST /v2/snaps`

```bash
curl -sS -X POST http://127.0.0.1:8080/v2/snaps \
  -H 'Content-Type: application/json' \
  -d '{
    "videoUrl": "https://appimagens.br-gru-1.linodeobjects.com/57_36081920_20250220T170651_9eb0.mp4",
    "nickname": "operador1",
    "email": "operador1@example.com",
    "dataFilmagem": "2026-02-24T14:30:00-03:00",
    "startFrame": 360,
    "durationSeconds": 1.0,
    "snapshotDurationSeconds": 2.0,
    "fps": 5,
    "maxWidth": 640,
    "format": "jpg",
    "quality": 10,
    "subject": {
      "attributes": [
        { "key": "brinco", "type": "string", "stringValue": "12334234534" },
        { "key": "peso", "type": "number", "numberValue": 450.0 },
        { "key": "observacoes", "type": "string", "stringValue": "animal agitado" }
      ]
    },
    "overlay": {
      "enabled": true,
      "mode": "SUBJECT",
      "position": "TOP_RIGHT"
    }
  }'
```

Observacao:
- no exemplo acima, `subject.id` foi omitido propositalmente; a API usa `snapId` como fallback.

### Smoke test manual (Entrega 1)

Smoke executado em `2026-02-25` (UTC `2026-02-25T21:21:46Z`) com validação ponta a ponta da `v2`.

Cenário usado:
- ambiente local com `ffmpeg`/`ffprobe` instalados
- vídeo MP4 real local (gerado via `ffmpeg`) servido por `http.server` na mesma sessão de execução
- app subida em `8081` com `H2` em memória apenas para o smoke

Endpoints validados no smoke:
- `POST /v2/snaps` -> `200` (`status=COMPLETED`, `frameCount=5`)
- `GET /v2/snaps/{snapId}` -> `200`
- `GET /v2/videos/{videoId}/snaps?nickname=smoke-user` -> `200` (`total=1`)
- `GET /v2/snaps/search?attrKey=brinco&attrValue=SMK-001` -> `200` (`total=1`)

Evidências observadas:
- fallback de `subject.id` confirmado (`subject.id == snapId`)
- `videoProbe.compatible=true` para MP4/H.264 local
- `snapshot.mp4` e `frame_00001.jpg`..`frame_00005.jpg` gerados em `./.data/tmp/video-frames-processing/...`

Script de apoio (smoke automatizado local):
- `tmp/smoke/run_v2_smoke.sh`

Observações de ambiente:
- no sandbox padrão deste agente, abrir sockets locais (`http.server`) pode falhar com `PermissionError`; o smoke foi executado fora do sandbox para validar `localhost`
- durante o smoke foi identificado e corrigido um problema de configuração local: `app.processing.tmpBase` apontava para `/data/...` (não gravável no ambiente) e passou a usar `./.data/tmp/video-frames-processing` por padrão

## Entrega 2 (Compartilhamento + listas "mine")

Implementado nesta fase:
- `POST /v2/snaps/{snapId}/share` (gera ou reutiliza `publicShareToken`, resposta idempotente)
- `GET /public/snaps/{token}` (retorno público com clip/frames e metadados públicos do snap)
- `GET /v2/snaps/mine?nickname=...` (lista snaps por `nickname` na assinatura ativa, com paginação/ordenação)
- `GET /v2/videos/mine?nickname=...` (lista vídeos com atividade do usuário, agregando `snapCount`, com paginação/ordenação)

Regras aplicadas na Entrega 2:
- compartilhamento público depende de token aleatório (`UUID` sem hífens) persistido em `snap.public_share_token`
- `POST /share` é idempotente (mesmo snap retorna o mesmo token se já estiver público)
- `GET /public/snaps/{token}` só retorna snaps com `is_public=true`
- listas `mine` continuam usando `nickname` como identidade temporária (antes de auth/token por assinatura)
- `/v2/videos/mine` ordena por atividade mais recente e agrega quantidade de snaps por vídeo

Observações de contrato:
- `POST /v2/snaps/{snapId}/share` retorna `publicUrl` relativo por padrão (ex.: `/public/snaps/{token}`)
- para URL absoluta, configurar `app.snap.publicBaseUrl`

Cobertura de testes (integração):
- fluxo share/public (`POST /share` + `GET /public/snaps/{token}`)
- listas `mine` (`/v2/snaps/mine` e `/v2/videos/mine`)
- erros principais (`token` público inexistente, `nickname` em branco)

## Entrega 3 - Contexto de assinatura + token (feature flag) + paginação/ordenação

Item 1 implementado (sem quebrar contrato):
- endpoints `/v2/*` agora aceitam header opcional `X-Assinatura-Codigo`
- quando o header não é enviado, a API mantém fallback para `default`
- quando o header é enviado com código inexistente, a API retorna `404` (`Assinatura not found: ...`)

Item 2 implementado (feature flag / stub de auth por token):
- validação opcional de token por assinatura nas rotas privadas `/v2/*`
- header de token: `X-Assinatura-Token`
- quando `app.snap.requireApiToken=false` (padrão), o header é ignorado (compatível com Entregas 1-2)
- quando `app.snap.requireApiToken=true`, a API exige token que corresponda a `assinatura.api_token`
- falhas de token retornam `401` (`Assinatura API token required` / `Invalid assinatura API token`)
- endpoints públicos (`/public/*`) permanecem sem token

Item 3 implementado (mudança aditiva compatível):
- listagens `/v2/videos/{videoId}/snaps`, `/v2/snaps/search`, `/v2/snaps/mine` e `/v2/videos/mine` agora aceitam:
  - `offset` (default `0`)
  - `limit` (default `50`, max `100`)
  - `sortBy` (varia por endpoint)
  - `sortDir` (`asc`/`desc`)
- respostas dessas listas agora incluem objeto `page` com metadados padronizados:
  - `offset`, `limit`, `returned`, `hasMore`, `sortBy`, `sortDir`
- `total` e `items` foram mantidos para compatibilidade com clientes existentes

Objetivo atingido nesta entrega:
- formalizar contexto de assinatura no request
- preparar autenticação por token de assinatura sem ativar por padrão
- padronizar paginação/ordenação sem quebra de contrato (mudança aditiva)

Item 4 implementado (observabilidade mínima):
- header `X-Request-Id` em todas as respostas HTTP (gerado automaticamente quando ausente)
- logs de acesso por request (`method`, `path`, `status`, `durationMs`, `requestId`)
- logs centralizados de erro no `GlobalExceptionHandler` (4xx em `WARN`, 5xx em `ERROR`)
- métricas HTTP in-memory agregadas por `método + rota` (baixa cardinalidade, usando pattern de rota do Spring)
- endpoint interno de snapshot:
  - `GET /internal/observability/http-metrics`

Observação de uso:
- o endpoint `/internal/observability/http-metrics` é voltado para dev/diagnóstico inicial e pode ser substituído futuramente por Actuator/Prometheus/OpenTelemetry

Exemplo (compatível com o comportamento atual):

```bash
curl -sS http://127.0.0.1:8080/v2/snaps/search \
  -H 'X-Assinatura-Codigo: default' \
  --get \
  --data-urlencode 'subjectId=animal-123'
```

Exemplo com token (feature flag ligada):

```bash
curl -sS http://127.0.0.1:8080/v2/snaps/search \
  -H 'X-Assinatura-Codigo: default' \
  -H 'X-Assinatura-Token: dev-default-token' \
  --get \
  --data-urlencode 'subjectId=animal-123'
```

Exemplo com paginação/ordenação (Entrega 3 item 3):

```bash
curl -sS http://127.0.0.1:8080/v2/snaps/mine \
  -H 'X-Assinatura-Codigo: default' \
  --get \
  --data-urlencode 'nickname=operador1' \
  --data-urlencode 'offset=0' \
  --data-urlencode 'limit=20' \
  --data-urlencode 'sortBy=createdAt' \
  --data-urlencode 'sortDir=desc'
```

Exemplo de snapshot de observabilidade HTTP (Entrega 3 item 4):

```bash
curl -sS http://127.0.0.1:8080/internal/observability/http-metrics
```

## Entrega 4 (parcial / slices 1-3) - Fila em banco + worker local + retry/cleanup/telemetria

Implementado nesta etapa:
- migration `V3` com fila `snap_processing_job`
- worker local de polling em banco com claim via `FOR UPDATE SKIP LOCKED`
- `POST /v2/snaps` com modo assíncrono opcional (feature flag)
  - quando `app.snap.asyncCreateEnabled=false` (padrão): mantém comportamento síncrono atual
  - quando `app.snap.asyncCreateEnabled=true`: retorna `status=PENDING` e enfileira job
- `GET /v2/snaps/{snapId}` preservado como endpoint de polling de estado/resultados
- `SnapResponse` (create/get by id) agora inclui objeto opcional `job` com estado da fila/worker
- retentativas com backoff exponencial e cap (`RETRY_WAIT`)
- recuperação de jobs `RUNNING` órfãos/stale por timeout de lock
- cleanup agendado de rows terminais antigos em `snap_processing_job` (retention-based)
- telemetria interna de jobs via endpoint `/internal/observability/snap-job-metrics`

Propriedades adicionadas (`app.snap.*`):
- `asyncCreateEnabled`
- `workerEnabled`
- `workerPollDelayMs`
- `workerBatchSize`
- `workerMaxAttempts`
- `workerRetryDelaySeconds`
- `workerRetryBackoffMultiplier`
- `workerRetryMaxDelaySeconds`
- `workerLockTimeoutSeconds`
- `workerInstanceId`
- `jobCleanupEnabled`
- `jobCleanupDelayMs`
- `jobRetentionHours`
- `jobCleanupBatchSize`

Fluxo assíncrono (modo ativo):
1. `POST /v2/snaps` cria `snap` com `status=PENDING` e persiste request snapshot
2. API enfileira `snap_processing_job`
3. worker local faz claim do job (`SKIP LOCKED`) e processa o pipeline FFmpeg reaproveitado
4. em falha transitória, job vai para `RETRY_WAIT` com `nextRunAt` (backoff exponencial)
5. em restart/crash, jobs `RUNNING` antigos podem ser recuperados automaticamente por timeout
6. worker atualiza o mesmo `snap` para `COMPLETED` ou `FAILED`
7. cliente faz polling em `GET /v2/snaps/{snapId}`

Observações:
- mudança é protegida por feature flag para rollout gradual
- contrato de `SnapResponse` foi preservado; campos de processamento ficam nulos/vazios enquanto `PENDING`
- o campo `job` é aditivo e expõe estado operacional sem quebrar clientes existentes
- busca/listagens podem retornar snaps `PENDING` (útil para UX de fila/progresso)
- cleanup remove apenas rows internos da fila (`snap_processing_job`), não remove `snap`

Campos de `SnapResponse.job` (quando disponíveis):
- `jobId`, `status`, `attempts`, `maxAttempts`
- `nextRunAt`, `lockedAt`, `lockOwner`
- `startedAt`, `finishedAt`
- `lastError`

Status de job observáveis (internos/async):
- `PENDING`
- `RUNNING`
- `RETRY_WAIT`
- `COMPLETED`
- `FAILED`

Exemplo de snapshot de telemetria de jobs (Entrega 4 slice 3):

```bash
curl -sS http://127.0.0.1:8080/internal/observability/snap-job-metrics
```

O snapshot inclui, entre outros:
- `claimedCount`
- `retryScheduledCount`
- `staleRecoveredCount`
- `cleanupDeletedCount`
- `completedCount` / `failedCount`
- `avgTerminalDurationMs` / `maxTerminalDurationMs`
- `terminalByStatus`

## O Que Falta (Consolidado)

### Código (próximas entregas/slices)

- decidir rollout do modo assíncrono por ambiente (quando ativar `app.snap.asyncCreateEnabled=true` por padrão)
- validar smoke manual completo em modo assíncrono (create/polling/share/public/métricas)
- avaliar endpoint de progresso dedicado (se necessário para payload menor no player)
- endurecimento para múltiplos workers (heartbeat de lock, tuning de claim/concurrency)
- telemetria externa (Actuator/Prometheus/OpenTelemetry) e operação alvo (PostgreSQL + S3 + worker separado)

### Planos / documentação

- manter `prompts/entregas-api-snap-v2.md` atualizado com os próximos slices da Entrega 4
- revisar `prompts/player-integracao.md` conforme integração real do `snap-player` avançar
- registrar ADRs quando houver mudança estrutural em progresso/job/operação

### Estrutura de `prompts/` (avaliação)

- a estrutura atual está funcional, mas já está ficando "flat"
- a proposta de reorganização incremental (sem urgência) foi documentada em `prompts/README.md`

## Exemplo de Chamada (Com Overlay e Subject)

```bash
curl -sS -X POST http://127.0.0.1:8080/v1/video-frames/process \
  -H 'Content-Type: application/json' \
  -d '[
    {
      "videoUrl": "https://appimagens.br-gru-1.linodeobjects.com/57_36081920_20250220T170651_9eb0.mp4",
      "startSeconds": 12.0,
      "startFrame": 360,
      "durationSeconds": 1.0,
      "snapshotDurationSeconds": 2.0,
      "fps": 5,
      "maxWidth": 640,
      "format": "jpg",
      "quality": 10,
      "dataFilmagem": "2026-02-24T14:30:00-03:00",
      "subject": {
        "id": "animal-123",
        "attributes": [
          { "key": "brinco", "type": "string", "stringValue": "12334234534" },
          { "key": "peso", "type": "number", "numberValue": 450.0 },
          { "key": "lote", "type": "string", "stringValue": "A1" }
        ]
      },
      "overlay": {
        "enabled": true,
        "mode": "SUBJECT",
        "position": "TOP_RIGHT",
        "fontSize": 28,
        "boxColor": "black@0.7",
        "fontColor": "white",
        "margin": 20,
        "padding": 10
      }
    }
  ]'
```

## Overlay (`drawtext`) no MVP

### Como funciona

- Se `overlay.enabled=true`, o MVP aplica `drawtext`:
  - nos frames
  - no `snapshot.mp4`
- O texto exibido usa o `subject`:
  - `subject.id`
  - todos os atributos (`key: value`) na ordem recebida

### Modos suportados

- `SUBJECT` (padrao)
- `SUBJECT_AND_FRAME`
- `SUBJECT_AND_TIMESTAMP`
- `SUBJECT_AND_BOTH`

Aliases legados (aceitos por compatibilidade):
- `FRAME_NUMBER` -> `SUBJECT_AND_FRAME`
- `TIMESTAMP` -> `SUBJECT_AND_TIMESTAMP`
- `BOTH` -> `SUBJECT_AND_BOTH`

### Posicao suportada no MVP

- `TOP_RIGHT` (apenas)

## Regras Importantes do Recorte

- Envie `startSeconds` ou `startFrame`
- Se enviar os dois, o MVP usa `startFrame`
- O retorno informa `resolvedStartSeconds`
- `durationSeconds` controla os frames
- `snapshotDurationSeconds` controla o `snapshot.mp4`

## Compatibilidade do Video (Probe)

Antes da extracao, o MVP roda `ffprobe` e retorna `videoProbe` com:

- `containerFormat`
- `codecName`
- `width`
- `height`
- `durationSeconds`
- `sourceFps`
- `pixelFormat`
- `compatible`
- `reason` (em erro/incompatibilidade)

Contêineres aceitos por padrao:
- `mp4`
- `mov`
- `mkv`
- `webm`

Observacao: a compatibilidade real depende do FFmpeg instalado no ambiente.

## Estrutura de Saida Local (MVP)

Exemplo:

```text
/tmp/video-frames-processing/
  {requestId}/
    item-000/
      snapshot.mp4
      frame_00001.jpg
      frame_00002.jpg
      ...
```

## Configuracao (`application.yml`)

Arquivo: `src/main/resources/application.yml`

Principais chaves:
- `app.processing.tmpBase`
- `app.processing.maxBatchItems`
- `app.processing.maxSubjectAttributes`
- `app.processing.maxDurationSeconds`
- `app.processing.maxFps`
- `app.processing.maxWidth`
- `app.processing.acceptedContainers`
- `app.processing.ffmpeg.path`
- `app.processing.ffmpeg.timeoutSeconds`
- `app.processing.ffmpeg.fontFile`
- `app.processing.ffprobe.path`
- `app.processing.ffprobe.timeoutSeconds`

## Troubleshooting

### 1. Overlay falha com `drawtext filter is not available`

Verifique:

```bash
ffmpeg -hide_banner -filters | grep drawtext
```

Se nao aparecer `drawtext`, seu FFmpeg foi compilado sem esse filtro.

### 2. Fonte nao encontrada

Verifique o arquivo configurado:

```bash
ls -l /usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf
```

Trocar fonte no startup:

```bash
mvn -Dmaven.repo.local=.m2/repository spring-boot:run \
  '-Dspring-boot.run.arguments=--app.processing.ffmpeg.fontFile=/caminho/fonte.ttf'
```

### 3. Video incompatível

Veja no JSON de resposta:
- `filmagens[i].videoProbe.reason`
- `filmagens[i].error`

## Estrutura do Projeto (Resumo)

- `src/main/java/com/snapplayerapi/api/controller` - endpoints (v1 legado + v2)
- `src/main/java/com/snapplayerapi/api/service` - probe, ffmpeg, fluxo síncrono legado, serviços v2
- `src/main/java/com/snapplayerapi/api/dto` - contratos JSON base/legado
- `src/main/resources/application.yml` - configuracao
- `src/test/java` - testes unitarios
- `prompts/mvp-tecnico.md` - plano MVP
- `prompts/master-tecnico.md` - plano assíncrono completo

## Roadmap (Master)

Planejado em `prompts/master-tecnico.md`:
- processamento assíncrono por batch
- PostgreSQL + Flyway
- worker com `SKIP LOCKED`
- upload S3/Linode Object Storage
- consulta por `subject.id`
- busca por atributos do `subject` (igualdade e faixa numérica)

## Contribuicao

- Defina primeiro o escopo no plano correspondente:
  - `prompts/mvp-tecnico.md` para evolucoes locais/sincronas
  - `prompts/master-tecnico.md` para arquitetura assíncrona/persistente
- Rode testes antes de enviar alteracoes:
  - `mvn -Dmaven.repo.local=.m2/repository test`
- Para mudancas no contrato JSON, atualize:
  - DTOs
  - validacoes
  - testes
  - README/plano correspondente

## Versionamento / Releases

- MVP atual: processamento síncrono local (sem PostgreSQL/S3)
- Evolucoes futuras previstas no `master` devem introduzir versionamento de contrato/endpoints quando houver quebra de compatibilidade

## Licenca / Uso

Definir conforme sua necessidade de distribuicao (interno, privado, OSS, etc.).
