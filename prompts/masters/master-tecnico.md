# Plano Master Consolidado (Prompt para Codex) - Snap Player API

**Stack:** Java 17 + Spring Boot 3.x + PostgreSQL + Flyway + Docker + FFmpeg (ProcessBuilder) + S3 (Linode Object Storage)

## Papel deste arquivo (Master Técnico)

Fonte de verdade para arquitetura e decisões técnicas de processamento.

Use junto com:
- `prompts/README.md` (governança dos planos)
- `prompts/masters/master-produto-snap.md` (Master Produto / Snap-first)
- `prompts/entregas/entregas-api-snap-v2.md` (Plano de Entregas)

## Objetivo

Implementar uma API escalável e assíncrona para processar um BATCH (lista) de filmagens. Cada filmagem contém um `videoUrl` (MP4 remoto), `dataFilmagem` e um objeto genérico `subject` (identificador + atributos tipados pesquisáveis). A API extrai frames de um intervalo do vídeo, gera também um snapshot em vídeo (clip do intervalo solicitado), opcionalmente desenha um banner com `drawtext` contendo informações do `subject` (e modos opcionais com frame/timestamp) no canto superior direito em cada imagem/clip, faz upload para Object Storage (S3 compatível), e retorna um JSON consolidado com a mesma lista de entrada enriquecida com a lista de links das imagens (frames) e o link do snapshot em vídeo para cada filmagem, em ordem cronológica (`dataFilmagem` asc). O processamento é assíncrono: o `POST` retorna `batchId` imediatamente; o `GET` permite acompanhar progresso e obter o JSON final ao término. Em uma segunda etapa, a API deve permitir consultar resultados por `subject.id` e por atributos do `subject` (igualdade e faixa numérica).

## Princípios de Arquitetura (Obrigatório)

1. **Assíncrono por batch**
   - `POST` cria `Batch` + `N` `FilmagemJobs` (1 por item da lista).
   - Worker(s) processam `FilmagemJobs` em background.
   - `GET /batches/{id}` retorna progresso e resultados parciais; ao final retorna JSON consolidado.
2. **Escalável horizontalmente**
   - Coordenação de jobs via PostgreSQL usando `SELECT ... FOR UPDATE SKIP LOCKED` (evita duplicidade em múltiplas instâncias).
3. **Controle de recursos (rodar até em Linode 2GB)**
   - Limitar paralelismo de FFmpeg por instância via `Semaphore` (default 1).
   - Pool de workers pequeno (default 2).
   - Limitar duração, fps, tamanho e itens do batch (configurável).
4. **Segurança**
   - Bloquear SSRF: aceitar apenas `http/https` e bloquear hosts/IPs internos (`localhost`, `127.0.0.1`, `10/8`, `172.16/12`, `192.168/16`, link-local etc).
   - Nunca executar comando via shell (sem `bash -c`). Usar `ProcessBuilder` com lista de args.
   - Validar/sanitizar parâmetros e textos do overlay.
5. **Robustez**
   - Timeout do FFmpeg (mata processo).
   - Retentativas (`attempts`) e `next_run_at` para jobs falhos (no máximo 2).
   - Capturar `stderr` do ffmpeg (limitado) para debug.
6. **Limpeza de temp (Obrigatório)**
   - Apagar pasta temporária do job ao final (sucesso/erro).
   - Scheduler de limpeza (1h) remove pastas antigas (ex: > 6h) para casos de crash.
   - Preferir `tmpBase` configurável e volume dedicado no Docker (`/data/tmp/video-frames`).

7. **Documentação e comentários de código (Obrigatório)**
   - Todo código novo/alterado deve ser **documentado e comentado detalhadamente**.
   - Explicar intenção, regras de negócio, decisões de implementação, trade-offs e limitações.
   - Em Java:
     - usar JavaDoc em classes/serviços/endpoints públicos e métodos não triviais
     - usar comentários inline em trechos complexos (validação, montagem de comando, persistência, concorrência)
   - Em SQL/Flyway:
     - nomear migrations de forma clara
     - comentar blocos de schema/índices/constraints quando a intenção não for óbvia
   - Em testes:
     - nome dos testes deve descrever comportamento esperado
     - comentários devem explicitar cenário, regra validada e motivo do teste
   - Em `README` e documentação operacional:
     - registrar endpoints, payloads, exemplos e observações relevantes sempre que houver funcionalidade nova
   - Em `http/*.http` (cliente HTTP local do repositório):
     - manter arquivos de chamadas atualizados conforme evolução dos endpoints
     - incluir variáveis/configuração mínimas para execução local (baseUrl, headers, placeholders)
     - cobrir fluxos principais, endpoints públicos/privados e exemplos de erro relevantes
   - Evitar comentários genéricos sem valor; priorizar explicações úteis para manutenção futura.

## Contratos de API (Endpoints)

### 1. Criar batch

`POST /v1/video-frames/batches`

**Body:** JSON array (min 1, max `app.maxBatchItems`)

```json
[
  {
    "videoUrl": "https://appimagens.br-gru-1.linodeobjects.com/57_36081920_20250220T170651_9eb0.mp4",
    "startSeconds": 12.0,
    "startFrame": null,
    "durationSeconds": 3.0,
    "snapshotDurationSeconds": 5.0,
    "fps": 30,
    "maxWidth": 1280,
    "format": "jpg",
    "quality": 10,
    "clientRequestId": "optional-idempotency-key",
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
      "fontSize": 40,
      "boxColor": "black@0.7",
      "fontColor": "white",
      "margin": 20,
      "padding": 10
    },
    "callbackUrl": null
  }
]
```

Notas do exemplo:
- `overlay.mode` (sugestão v1): `SUBJECT | SUBJECT_AND_FRAME | SUBJECT_AND_TIMESTAMP | SUBJECT_AND_BOTH`
- `overlay.position`: v1 apenas `TOP_RIGHT`
- `startFrame` tem precedência sobre `startSeconds` quando ambos forem enviados

**Regras de validação**
- `videoUrl` obrigatório, `http/https`, SSRF blocked
- informar pelo menos um entre `startSeconds` ou `startFrame`
- se ambos forem enviados, utilizar `startFrame` e ignorar `startSeconds`
- `startSeconds >= 0` (quando usado)
- `startFrame >= 0` inteiro (quando informado)
- se `startFrame` for informado, resolver `startSeconds` a partir do FPS do vídeo (via `ffprobe`/metadata; documentar limitações para VFR)
- se o vídeo for incompatível para extração (codec/container/stream), retornar feedback explícito por item com motivo
- `durationSeconds > 0` e `<= app.maxDurationSeconds` (tempo das imagens/frames)
- `snapshotDurationSeconds > 0` e `<= app.maxDurationSeconds` (tempo do clip em vídeo; opcional, default = `durationSeconds`)
- `fps` default `5`, min `1`, max `<= app.maxFps`
- `maxWidth` default `1280`, min `320`, max `<= app.maxWidth`
- `format`: `jpg|png` (default `jpg`)
- `quality`: somente `jpg`, `2..10` (default `3`)
- `dataFilmagem` obrigatório (ISO-8601 com offset)
- `subject` obrigatório
- `subject.id` obrigatório (string, tamanho configurável)
- `subject.attributes` opcional, sem limite funcional fixo; aplicar limite operacional configurável (ex.: `app.maxSubjectAttributes`)
- cada atributo deve ter `key` único por item
- `type`: `string | number`
- `type=string` => exigir `stringValue` e proibir `numberValue`
- `type=number` => exigir `numberValue` e proibir `stringValue`
- permitir pesquisa futura por todos os atributos do `subject` (igualdade para string e faixa para number)
- `overlay` opcional

**Resposta**

`202 Accepted`

```json
{
  "batchId": "uuid",
  "status": "QUEUED",
  "createdAt": "ISO-8601",
  "pollUrl": "/v1/video-frames/batches/{batchId}"
}
```

### 2. Consultar batch (progresso + resultado final)

`GET /v1/video-frames/batches/{batchId}`

**Resposta enquanto processa (parcial)**  
`200 OK`

```json
{
  "batchId": "...",
  "status": "RUNNING",
  "createdAt": "...",
  "startedAt": "...",
  "finishedAt": null,
  "progress": { "total": 10, "done": 4, "failed": 1 },
  "filmagens": [
    {
      "itemIndex": 0,
      "status": "SUCCEEDED",
      "dataFilmagem": "...",
      "subject": {
        "id": "animal-123",
        "attributes": [
          { "key": "brinco", "type": "string", "stringValue": "12334234534" },
          { "key": "peso", "type": "number", "numberValue": 450.0 }
        ]
      },
      "videoUrl": "...",
      "startSeconds": 12.0,
      "startFrame": null,
      "resolvedStartSeconds": 12.0,
      "durationSeconds": 3.0,
      "snapshotDurationSeconds": 5.0,
      "fps": 10,
      "maxWidth": 1280,
      "format": "jpg",
      "quality": 3,
      "snapshotVideo": {
        "format": "mp4",
        "durationSeconds": 5.0,
        "url": "https://.../snapshot.mp4"
      },
      "videoProbe": {
        "compatible": true,
        "containerFormat": "mov,mp4,m4a,3gp,3g2,mj2",
        "codecName": "h264",
        "width": 1280,
        "height": 720,
        "durationSeconds": 18.4,
        "sourceFps": 29.97,
        "reason": null
      },
      "frames": [
        { "index": 1, "timestampSeconds": 12.0, "url": "https://.../frame_00001.jpg" }
      ],
      "error": null
    },
    { "itemIndex": 1, "status": "RUNNING" }
  ]
}
```

**Resposta final**  
`200 OK`

```json
{
  "batchId": "...",
  "status": "SUCCEEDED",
  "createdAt": "...",
  "startedAt": "...",
  "finishedAt": "...",
  "progress": { "total": 10, "done": 10, "failed": 0 },
  "filmagens": [
    "... (ORDENADAS por dataFilmagem ASC; frames ordenados por timestampSeconds ASC)"
  ],
  "error": null
}
```

Notas:
- `status`: `SUCCEEDED | PARTIAL | FAILED`

### 3. Consultar resultados por `subject.id` (fase 2)

`GET /v1/video-frames/results?subjectId={subjectId}&from={ISO-8601?}&to={ISO-8601?}&status={optional}&limit=50&cursor={optional}`

**Objetivo**

- Recuperar filmagens já processadas para um `subject.id` específico.
- Ordenação padrão: `dataFilmagem DESC` (mais recentes primeiro).
- Suportar paginação e escolha da ordenação

**Resposta (exemplo resumido)**

```json
{
  "items": [
    {
      "jobId": "uuid",
      "batchId": "uuid",
      "status": "SUCCEEDED",
      "dataFilmagem": "2026-02-24T14:30:00-03:00",
      "subject": {
        "id": "animal-123",
        "attributes": [
          { "key": "brinco", "type": "string", "stringValue": "12334234534" },
          { "key": "peso", "type": "number", "numberValue": 450.0 }
        ]
      },
      "snapshotVideo": { "format": "mp4", "durationSeconds": 5.0, "url": "https://.../snapshot.mp4" },
      "frameCount": 30
    }
  ],
  "nextCursor": "opaque"
}
```

### 4. Buscar resultados por atributos do `subject` (fase 2)

`POST /v1/video-frames/results/search`

**Body (exemplo)**

```json
{
  "subjectId": "animal-123",
  "dataFilmagemFrom": "2026-02-01T00:00:00Z",
  "dataFilmagemTo": "2026-02-28T23:59:59Z",
  "filters": [
    { "key": "brinco", "op": "EQ", "type": "string", "stringValue": "12334234534" },
    { "key": "peso", "op": "GTE", "type": "number", "numberValue": 430.0 },
    { "key": "peso", "op": "LTE", "type": "number", "numberValue": 500.0 }
  ],
  "status": ["SUCCEEDED", "FAILED"],
  "sort": { "field": "dataFilmagem", "direction": "DESC" },
  "limit": 50,
  "cursor": null
}
```

**Regras**

- `subjectId` opcional (quando ausente, busca global por atributos).
- Suportar igualdade para `string` (`EQ`).
- Suportar `EQ`, `GT`, `GTE`, `LT`, `LTE` para `number`.
- Rejeitar operadores incompatíveis com o tipo.
- Paginação obrigatória.

### 5. (Opcional) health/version

- `GET /actuator/health`
- `GET /v1/video-frames/version` (inclui checagem de ffmpeg/drawtext em cache)

## Requisito do Resultado Final

- O JSON final deve conter a lista de entrada enriquecida com `frames` (lista de URLs) para cada filmagem.
- O JSON final deve conter também `snapshotVideo` (URL do clip em vídeo do intervalo solicitado) para cada filmagem.
- O JSON final deve conter `videoProbe` (propriedades detectadas e compatibilidade) para cada filmagem processada.
- O JSON final deve preservar o objeto `subject` de cada item processado.
- A lista `filmagens` deve ser retornada em ordem cronológica crescente (`dataFilmagem` asc).
- A lista `frames` deve estar em ordem crescente de `timestampSeconds`.
- Quando a entrada usar `startFrame`, o retorno deve incluir `resolvedStartSeconds`.
- Cada frame deve conter:
  - `index` (1..n)
  - `timestampSeconds = resolvedStartSeconds + (index-1)/fps` (aproximação aceitável)
  - `url` (final do storage)
- `snapshotVideo` deve conter ao menos:
  - `format` (ex: `mp4`)
  - `durationSeconds`
  - `url`
- `videoProbe` deve informar ao menos:
  - `compatible` (boolean)
  - `containerFormat`
  - `codecName`
  - `width`
  - `height`
  - `durationSeconds`
  - `sourceFps` (quando detectável)
  - `reason` (quando incompatível/falho)
- A API deve expor consulta por `subject.id` e busca por atributos do `subject` (incluindo faixa numérica para atributos `number`).

## Processamento com FFmpeg (Obrigatório)

- Executar ffmpeg via `ProcessBuilder` com args seguros.
- Executar ffprobe via `ProcessBuilder` com args seguros.
- Trabalhar direto com URL remota (sem baixar arquivo inteiro).
- Se entrada usar `startFrame`, resolver `resolvedStartSeconds` antes de extrair frames/clip.
- Se `startFrame` e `startSeconds` vierem juntos, usar `startFrame`.
- Gerar 2 saídas por item:
  - frames (imagens)
  - snapshot em vídeo (clip MP4 do intervalo)
- `durationSeconds` controla os frames; `snapshotDurationSeconds` controla o clip em vídeo.

**Comando base JPG**

```bash
ffmpeg -hide_banner -loglevel error \
  -ss {start} -t {duration} \
  -i {url} \
  -vf "fps={fps},scale={maxWidth}:-2" \
  -q:v {quality} \
  {outDir}/frame_%05d.jpg
```

**Comando base PNG**

```bash
ffmpeg -hide_banner -loglevel error \
  -ss {start} -t {duration} \
  -i {url} \
  -vf "fps={fps},scale={maxWidth}:-2" \
  {outDir}/frame_%05d.png
```

**Comando base Snapshot Vídeo (MP4)**

```bash
ffmpeg -hide_banner -loglevel error \
  -ss {resolvedStart} -t {duration} \
  -i {url} \
  -vf "scale='min({maxWidth},iw)':-2" \
  -c:v libx264 -preset veryfast -crf 23 \
  -an \
  {outDir}/snapshot.mp4
```

**Quando `startFrame` for informado (resolução de início)**

- Obter FPS do vídeo via `ffprobe`/metadata (`avg_frame_rate` ou `r_frame_rate`, documentando fallback).
- Converter:
  - `resolvedStartSeconds = startFrame / sourceVideoFps`
- Persistir/retornar `resolvedStartSeconds` para transparência.

**Compatibilidade do vídeo (feedback obrigatório)**

- Fazer probe antes da extração e armazenar propriedades detectadas.
- Rejeitar item com erro claro quando:
  - não houver stream de vídeo
  - codec/container não for decodificável pelo FFmpeg instalado
  - `ffprobe`/`ffmpeg` falhar ao abrir a URL
  - `startFrame` exigir FPS e o FPS não puder ser determinado com segurança

**Formatos e propriedades aceitos (documentar no README e retornar no probe)**

- Contêineres aceitos (mínimo): `mp4`, `mov`, `mkv`, `webm` (sujeito ao build do FFmpeg).
- Codecs aceitos: os decodificáveis pelo FFmpeg instalado na imagem Docker.
- Propriedades retornadas por item: container, codec, resolução, duração, FPS de origem, pixel format (quando disponível).

## Overlay (Drawtext com Subject) - v1

- Se `overlay.enabled=true`, incluir `drawtext` no `-vf`.
- Fonte padrão (instalar na imagem docker):
  - `/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf`

**Conteúdo do overlay (v1)**

- Renderizar `subject.id`
- Renderizar atributos do `subject` (`key: value`) em múltiplas linhas
- Modos opcionais podem adicionar:
  - número do frame do clip
  - timestamp do clip

**Drawtext base (topo direito)**

```text
drawtext=fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:
  text='{subject_text_multiline}':
  fontsize={fontSize}:
  fontcolor={fontColor}:
  box=1:
  boxcolor={boxColor}:
  boxborderw={padding}:
  x=w-tw-{margin}:
  y={margin}
```

**Mode (sugestão)**
- `SUBJECT`: apenas `subject.id` + atributos
- `SUBJECT_AND_FRAME`: `subject` + `%{n}`
- `SUBJECT_AND_TIMESTAMP`: `subject` + `%{pts\:hms}`
- `SUBJECT_AND_BOTH`: `subject` + frame + timestamp
- (Compatibilidade opcional) aliases antigos: `FRAME_NUMBER`, `TIMESTAMP`, `BOTH`

- Validar disponibilidade do filtro `drawtext` (`ffmpeg -filters` contém `drawtext`).
- Sanitizar strings do `drawtext` (evitar caracteres perigosos).
- Aplicar overlay em frames e, quando configurado, também no snapshot em vídeo.

## Storage (Linode Object Storage / S3)

- Usar AWS SDK v2 (Java 17).
- Configurar `endpointOverride`, `region` e `credentials`.
- Subir frames com key:
  - `{storage.prefix}/frames/{batchId}/{filmagemJobId}/frame_00001.jpg`
- Subir snapshot em vídeo com key:
  - `{storage.prefix}/snapshots/{batchId}/{filmagemJobId}/snapshot.mp4`
- Retornar URL:
  - Se bucket público: `publicBaseUrl + key`
  - (Opcional) Se privado: presigned URL (deixar preparado, mas v1 pode assumir público)
- (Opcional) gerar ZIP com todos frames e snapshot e subir.

## Banco de Dados + Flyway (Obrigatório)

### Tabela: `video_frame_batch`

- `id UUID PK`
- `status VARCHAR`
- `created_at, started_at, finished_at TIMESTAMPTZ`
- `total_count INT, done_count INT, failed_count INT`
- `request_hash VARCHAR (sha256 do payload normalizado)`
- `error_json JSONB`

### Tabela: `video_frame_filmagem_job`

- `id UUID PK`
- `batch_id UUID FK`
- `item_index INT`
- `status VARCHAR (QUEUED/RUNNING/SUCCEEDED/FAILED)`
- `data_filmagem TIMESTAMPTZ`
- `subject_id VARCHAR`
- `subject_json JSONB`
- `video_url TEXT`
- `start_seconds DOUBLE PRECISION`
- `start_frame BIGINT` (opcional)
- `resolved_start_seconds DOUBLE PRECISION`
- `duration_seconds DOUBLE PRECISION`
- `snapshot_duration_seconds DOUBLE PRECISION`
- `fps INT`
- `max_width INT`
- `format VARCHAR`
- `quality INT`
- `overlay_json JSONB`
- `callback_url TEXT (opcional)`
- `client_request_id VARCHAR (opcional)`
- `item_hash VARCHAR (sha256 do item)`
- `attempts INT`
- `next_run_at TIMESTAMPTZ`
- `claimed_by VARCHAR`
- `claimed_at TIMESTAMPTZ`
- `result_json JSONB`
- `error_json JSONB`
- `created_at, started_at, finished_at TIMESTAMPTZ`

### Tabela: `video_frame_filmagem_job_subject_attr`

- `id BIGSERIAL PK`
- `job_id UUID FK -> video_frame_filmagem_job(id)`
- `batch_id UUID` (denormalizado, opcional)
- `subject_id VARCHAR` (denormalizado)
- `data_filmagem TIMESTAMPTZ` (denormalizado para acelerar busca)
- `attr_key VARCHAR`
- `value_type VARCHAR` (`STRING` | `NUMBER`)
- `string_value TEXT NULL`
- `number_value NUMERIC NULL`
- `created_at TIMESTAMPTZ`

**Regras de persistência dos atributos**

- 1 linha por atributo do `subject`.
- Persistir `subject_json` completo na tabela principal (auditoria/replay) e indexar atributos na tabela auxiliar.
- `attr_key` deve ser normalizado (ex.: lower-case) para busca consistente.
- Garantir coerência de tipo:
  - `STRING` => `string_value` preenchido e `number_value` nulo
  - `NUMBER` => `number_value` preenchido e `string_value` nulo

### Índices

- `idx_job_status_next_run (status, next_run_at)`
- `idx_job_batch (batch_id)`
- `idx_job_subject_data (subject_id, data_filmagem DESC)`
- `idx_job_subject_status (subject_id, status, data_filmagem DESC)`
- `idx_job_attr_string (attr_key, string_value)`
- `idx_job_attr_number (attr_key, number_value)`
- `idx_job_attr_subject_string (subject_id, attr_key, string_value)`
- `idx_job_attr_subject_number (subject_id, attr_key, number_value)`
- `unique_client_itemhash (client_request_id, item_hash) WHERE client_request_id IS NOT NULL`
- `unique_batch_request_hash (request_hash)` (se desejar idempotência por batch)

## Worker (Assíncrono) - Como Implementar

- Um `JobPoller` (Scheduled) roda a cada `app.worker.pollIntervalMs` e clama jobs:
  - Em transação:

```sql
SELECT * FROM video_frame_filmagem_job
 WHERE status='QUEUED' AND next_run_at <= now()
 ORDER BY created_at
 LIMIT app.worker.claimSize
 FOR UPDATE SKIP LOCKED
```

  - Update desses jobs para `RUNNING + claimed_by + claimed_at + started_at`.
- Submeter cada job ao `ExecutorService` (fixo `app.worker.threads`).
- Antes de executar ffmpeg, adquirir `Semaphore` (`app.ffmpeg.maxParallel`) para garantir 1 ffmpeg por instância (default para Linode 2GB).
- Atualizar `result_json/error_json` e `status`.
- Atualizar contadores do batch:
  - `done_count` increment em `SUCCEEDED/FAILED`
  - `failed_count` increment em `FAILED`
- Finalização do batch:
  - Quando `done_count == total_count`:
    - Se `failed_count == 0` => `SUCCEEDED`
    - Se `failed_count == total_count` => `FAILED`
    - Senão => `PARTIAL`
- Callback:
  - Se `callbackUrl` existir (por item ou batch), postar JSON final com retries/backoff.

## Limpeza de Temp (Obrigatório)

- `tmpBase` padrão: `/data/tmp/video-frames` (não usar `/tmp` sem controle).
- Em cada job:
  - criar pasta: `{tmpBase}/{batchId}/{jobId}`
  - após upload e persistência (`SUCCEEDED/FAILED`): `deleteRecursively(pasta)`
- Scheduler cleanup (a cada 1h):
  - varrer `{tmpBase}` e apagar pastas com `mtime > app.tmpCleanupHours` (ex: 6h)
  - logar quantas pastas e tamanho aproximado apagado
- Em Docker: montar volume dedicado para `/data/tmp`.

## Configurações (`application.yml`) - Padrão para Linode 2GB

```yaml
app:
  tmpBase: /data/tmp/video-frames
  tmpCleanupHours: 6
  maxBatchItems: 20
  maxSubjectAttributes: 100
  maxDurationSeconds: 5
  maxFps: 10
  maxWidth: 1280
  worker:
    enabled: true
    pollIntervalMs: 1000
    threads: 2
    claimSize: 2
  ffmpeg:
    path: ffmpeg
    timeoutSeconds: 60
    maxParallel: 1
    fontFile: /usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf
  storage:
    endpoint: (linode endpoint)
    region: br-gru-1
    bucket: (bucket)
    accessKey: (key)
    secretKey: (secret)
    publicBaseUrl: (base url público)
    prefix: (opcional)
```

## Entregáveis (Projeto Completo, Rodável)

1. Projeto Spring Boot 3.x (Java 17), Maven ou Gradle
2. Controllers:
   - `BatchController` (POST create batch, GET status/result)
   - `ResultQueryController` (GET por `subject.id`, POST search por atributos)
3. Services:
   - `BatchService` (criar batch, idempotência por `request_hash`, ordenação por `dataFilmagem`)
   - `JobClaimService` (claim via `SKIP LOCKED`)
   - `FfmpegService` (monta args, executa com timeout, captura stderr limitado; frames + snapshot vídeo)
   - `VideoProbeService` (detecta FPS do vídeo para suporte a `startFrame`)
   - `ResultQueryService` (consulta por `subject.id` + filtros de atributos/range)
   - `OverlayService` (monta drawtext conforme overlay)
   - `StorageService` (upload S3 + build url)
   - `TempCleanupService` (`deleteRecursively` + scheduled cleanup)
   - `CallbackService` (opcional)
4. Persistência:
   - JPA + Flyway migrations SQL
5. Docker:
   - `Dockerfile` com Java 17 runtime + ffmpeg + fontes (DejaVu)
   - `docker-compose.yml` com postgres + app + volume para `/data/tmp`
6. `README.md`:
   - Como subir (`docker-compose up`)
   - Exemplos `curl` do POST/GET
   - Variáveis e configuração do storage
   - Observações de limites e tuning para 2GB

## Testes Mínimos (Obrigatório)

- Testes de validação do request (limites, ISO-8601, `subject`, prioridade `startFrame > startSeconds`, e exigência de pelo menos um dos dois).
- Teste de validação de `subject.attributes` (tipagem, chave única, `stringValue`/`numberValue` coerentes).
- Teste de montagem segura do comando ffmpeg (args em lista; sem shell).
- Teste de conversão `startFrame -> resolvedStartSeconds` (com FPS detectado/mockado).
- Teste de feedback de incompatibilidade (sem stream de vídeo / codec não suportado / falha de probe).
- Teste de persistência/indexação de atributos do `subject` (string e number).
- Teste de consulta por `subject.id`.
- Teste de busca por atributos com faixa numérica (`GTE/LTE`).
- Teste de cleanup `deleteRecursively`.
- (Ideal) Testcontainers Postgres para validar claim com `SKIP LOCKED` (se não fizer, documentar).

## Critérios de Aceite

- POST retorna `202` imediato com `batchId`.
- Worker processa em background com limite de paralelismo.
- GET retorna parcial durante execução e JSON final consolidado ao término.
- Ordenação por `dataFilmagem` asc no retorno final.
- Frames retornados em ordem cronológica (`timestampSeconds` asc).
- Snapshot em vídeo (clip) é gerado e retornado para cada filmagem bem-sucedida.
- O payload aceita `subject` genérico (`id` + atributos tipados) e persiste-o para consulta posterior.
- API aceita informar início por `startSeconds` **ou** `startFrame` (com resolução para `resolvedStartSeconds`).
- Se ambos forem enviados, `startFrame` é o valor utilizado.
- Em vídeo incompatível, a API retorna feedback claro com motivo e propriedades detectadas (`videoProbe`).
- A API permite consulta de resultados por `subject.id`.
- A API permite busca por atributos do `subject` (igualdade para string e faixa para number).
- Overlay `drawtext` com informações do `subject` (e modos opcionais) quando habilitado.
- Temp folders são removidas ao final e também por scheduler.
- Múltiplas instâncias não duplicam jobs (`SKIP LOCKED`).
- SSRF bloqueado e timeouts aplicados.

## Consolidação do MVP (Conhecimentos Validados)

### O que já foi implementado e testado (MVP local)

- Projeto Spring Boot 3.x em Java 17 (`17.0.7`), com endpoint síncrono `POST /v1/video-frames/mvp/process`.
- Recebimento de JSON array com metadados de filmagem e tolerância a campos extras (ex.: `overlay`, `callbackUrl`) no MVP.
- MVP foi validado inicialmente com metadados de domínio específicos; no plano master final, esses metadados devem ser generalizados para `subject` (`id` + atributos tipados pesquisáveis).
- `ffprobe` executado antes do processamento para:
  - validar compatibilidade do vídeo
  - detectar propriedades (`containerFormat`, `codecName`, `width`, `height`, `durationSeconds`, `sourceFps`, `pixelFormat`)
  - retornar motivo explícito em caso de incompatibilidade (`videoProbe.reason`)
- Geração de 2 saídas por item:
  - frames (`frame_00001.jpg` / `.png`)
  - `snapshot.mp4`
- Execução de `ffmpeg`/`ffprobe` com `ProcessBuilder` (sem shell), timeout e captura limitada de saída para erro/debug.
- Resposta consolidada por item com:
  - `resolvedStartSeconds`
  - `videoProbe`
  - `snapshotVideo`
  - `frameCount`
  - `frames[]`
  - `error`
- Status agregado do batch síncrono (`COMPLETED | PARTIAL | FAILED`) já validado como modelo útil para o `GET` do fluxo assíncrono.

### Regras de contrato validadas (manter no projeto completo)

- Início do recorte pode ser informado por `startSeconds` ou `startFrame`.
- Se `startFrame` e `startSeconds` forem enviados juntos, `startFrame` tem precedência.
- O retorno deve informar `resolvedStartSeconds`.
- `startFrame -> resolvedStartSeconds` depende de `sourceFps` detectado via `ffprobe`.
- Em vídeo sem FPS detectável (ou incompatível), retornar feedback claro e não tentar extrair.
- `durationSeconds` controla a extração de frames (imagens).
- `snapshotDurationSeconds` controla a duração do clip em vídeo (`snapshot.mp4`).
- Se `snapshotDurationSeconds` não for enviado, usar fallback para `durationSeconds`.
- A validação de faixa deve considerar a maior duração solicitada entre frames e snapshot.

### Compatibilidade e formato (baseline validado)

- Contêineres aceitos no MVP: `mp4`, `mov`, `mkv`, `webm` (configurável).
- Compatibilidade real depende do build local de FFmpeg/ffprobe (codecs habilitados).
- Falhas de abertura/decodificação devem ser refletidas no retorno com mensagem legível (`videoProbe.reason`).
- O plano completo deve manter esse comportamento por item, mesmo no fluxo assíncrono.

### Evidências práticas já verificadas (MVP)

- Testes automatizados passando (`mvn test`) com cobertura mínima de:
  - validação de request
  - montagem segura de comandos `ffmpeg`
  - montagem segura de comando `ffprobe`
  - parsing de FPS
  - storage temporário
- Smoke test com vídeo real (URL do plano) validou:
  - extração de frames compatível com o vídeo
  - geração de `snapshot.mp4`
  - precedência `startFrame > startSeconds`
  - retorno de `videoProbe`
  - uso de durações distintas (`durationSeconds` para frames e `snapshotDurationSeconds` para snapshot)

### Implicações para a implementação assíncrona (master)

- Reaproveitar o contrato/semântica do MVP no worker (não reinventar payload/retorno por item).
- Persistir também `snapshotDurationSeconds` no job/item para reproduzir exatamente o processamento.
- Persistir `subject_id`, `subject_json` e atributos indexados para suportar consulta por `subject` e busca por ranges.
- Persistir `resolvedStartSeconds` e propriedades do `videoProbe` para auditoria e troubleshooting.
- Manter feedback por item mesmo quando o batch geral falhar parcialmente.
- Preservar a distinção:
  - duração dos frames (`durationSeconds`)
  - duração do clip (`snapshotDurationSeconds`)

## Instrução Final para Codex

Gere TODOS os arquivos do projeto (sem pseudo-código), garantindo que:
- compile em Java 17,
- rode via `docker-compose` sem ajustes manuais,
- e cumpra integralmente os contratos JSON e critérios de aceite acima.

Obrigatório em toda entrega:
- documentar e comentar detalhadamente o código gerado/alterado (incluindo testes e documentação operacional),
- deixando explícitas regras, decisões e limitações para manutenção futura.

**FIM.**
