# Master Técnico — Snap Player API

## Papel deste arquivo

Fonte de verdade para arquitetura e decisões técnicas de processamento.

Use junto com:
- `CONTEXT.md` (estado atual e próximas prioridades)
- `prompts/masters/master-produto-snap.md` (domínio e regras de produto)
- `prompts/entregas/entregas-api-snap-v2.md` (sequenciamento de entregas)

---

## Stack

Java 17 + Spring Boot 3.x + PostgreSQL + Flyway + Docker +
FFmpeg (ProcessBuilder) + S3 Linode Object Storage (AWS SDK v2)

---

## Objetivo

API assíncrona para processar snaps de vídeo. Cada snap contém
videoUrl (MP4 remoto), recorte temporal/frame, e um objeto genérico
subject (id + atributos tipados pesquisáveis). A API extrai frames
do intervalo, gera clip (snapshot.mp4), opcionalmente aplica overlay
com dados do subject, faz upload para Object Storage e retorna JSON
consolidado com frames e clip.

---

## Princípios de Arquitetura (Obrigatório)

### 1. Assíncrono como padrão (Entrega 4 — rollout pendente)

- POST /v2/snaps retorna snapId imediatamente
- Worker processa em background via snap_processing_job
- GET /v2/snaps/{id} retorna estado parcial ou resultado final
- Feature flag app.snap.asyncCreateEnabled controla o rollout
- Meta: tornar assíncrono o padrão no ambiente de produção

### 2. Escalável horizontalmente

- Coordenação via PostgreSQL com SELECT FOR UPDATE SKIP LOCKED
- Sem dependência de Redis, RabbitMQ ou Kafka
- Múltiplas instâncias não duplicam jobs

### 3. Controle de recursos (Linode 2GB)

- Semaphore para limitar paralelismo FFmpeg (default 1)
- Pool de workers pequeno (default 2)
- Limites configuráveis: duração, fps, largura, itens por batch

### 4. Segurança

- Bloquear SSRF: aceitar apenas http/https, bloquear IPs internos
- Nunca usar shell (bash -c); sempre ProcessBuilder com lista de args
- Sanitizar parâmetros e textos do overlay
- Segredos sempre via variáveis de ambiente (nunca hardcode)

### 5. Robustez

- Timeout do FFmpeg (mata processo)
- Retentativas com backoff exponencial (máximo configurável)
- Recuperação de jobs RUNNING órfãos por timeout de lock
- Capturar stderr do FFmpeg (limitado) para debug

### 6. Limpeza de temp (Obrigatório)

- tmpBase configurável: /data/tmp/video-frames
- Apagar pasta do job ao final (sucesso ou erro)
- Scheduler de limpeza (1h) remove pastas antigas (> app.tmpCleanupHours)

### 7. Documentação e comentários (Obrigatório)

- JavaDoc em classes, serviços e endpoints públicos
- Comentários inline em trechos complexos
- Migrations Flyway nomeadas claramente com comentários de intenção
- Testes com nomes descritivos do comportamento esperado
- README e arquivos http/*.http sempre sincronizados

---

## Configurações (application.yml) — Padrão para Linode 2GB

```yaml
app:
  tmpBase: /data/tmp/video-frames
  tmpCleanupHours: 6
  maxDurationSeconds: 5
  maxFps: 10
  maxWidth: 1280
  snap:
    asyncCreateEnabled: true   # tornar true como padrão em produção
  worker:
    enabled: true
    pollIntervalMs: 1000
    threads: 2
    claimSize: 2
    workerLockTimeoutSeconds: 120
  ffmpeg:
    path: ffmpeg
    timeoutSeconds: 60
    maxParallel: 1
    fontFile: /usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf
  storage:
    local:
      enabled: false           # desabilitar em produção
      basePath: /data/storage
    s3:
      enabled: true            # habilitar em produção
      endpoint: ${STORAGE_ENDPOINT}
      region: ${STORAGE_REGION}
      bucket: ${STORAGE_BUCKET}
      accessKey: ${STORAGE_ACCESS_KEY}
      secretKey: ${STORAGE_SECRET_KEY}
      publicBaseUrl: ${STORAGE_PUBLIC_BASE_URL}
      prefix: ${STORAGE_PREFIX:}
```

---

## Processamento com FFmpeg (Obrigatório)

### Regras de precedência

- startFrame tem precedência sobre startSeconds quando ambos enviados
- resolvedStartSeconds sempre retornado para transparência
- durationSeconds controla extração de frames
- snapshotDurationSeconds controla clip; fallback para durationSeconds

### Probe obrigatório antes da extração

- ffprobe via ProcessBuilder antes de qualquer processamento
- Retornar videoProbe com: compatible, containerFormat, codecName,
  width, height, durationSeconds, sourceFps, reason
- Rejeitar com feedback claro: sem stream de vídeo, codec não
  suportado, URL inacessível, FPS não determinável com startFrame

### Saídas por snap

1. Frames (imagens JPG/PNG)
2. Snapshot em vídeo (clip MP4 do intervalo)

### Comandos base

**JPG:**
```
ffmpeg -hide_banner -loglevel error
  -ss {start} -t {duration}
  -i {url}
  -vf "fps={fps},scale={maxWidth}:-2"
  -q:v {quality}
  {outDir}/frame_%05d.jpg
```

**Snapshot MP4:**
```
ffmpeg -hide_banner -loglevel error
  -ss {resolvedStart} -t {duration}
  -i {url}
  -vf "scale='min({maxWidth},iw)':-2"
  -c:v libx264 -preset veryfast -crf 23
  -an
  {outDir}/snapshot.mp4
```

### Overlay (quando habilitado)

- Fonte: /usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf
- Modos: SUBJECT | SUBJECT_AND_FRAME | SUBJECT_AND_TIMESTAMP |
  SUBJECT_AND_BOTH
- Posição v1: TOP_RIGHT
- Validar disponibilidade de drawtext antes de usar
- Sanitizar strings do subject antes de incluir no drawtext

---

## Storage — Linode Object Storage (Produção)

- AWS SDK v2 com endpointOverride
- Keys:
  - frames: {prefix}/frames/{snapId}/frame_00001.jpg
  - snapshot: {prefix}/snapshots/{snapId}/snapshot.mp4
- URL pública: publicBaseUrl + key
- Preparado para presigned URL (privado) em fase futura
- Limpeza de temp sempre após upload bem-sucedido
- um único bucket para todos os clientes (multi-tenant lógico)
- Configuração via variáveis de ambiente (endpoint, bucket, credenciais)


## Banco de Dados + Flyway

### Tabelas principais

**assinatura**
- id, codigo (único), nome, api_token (único), status, created_at

**subject_template**
- id, assinatura_id (FK), nome, slug, ativo, schema_json (JSONB),
  is_default, created_at, updated_at
- UNIQUE (assinatura_id, slug)
- Garantir um template padrão por assinatura

**usuario**
- id, nickname, email, status, created_at

**usuario_assinatura**
- id, usuario_id (FK), assinatura_id (FK), papel, created_at
- UNIQUE (usuario_id, assinatura_id)

**video**
- id, assinatura_id (FK), original_url, canonical_url, url_hash,
  video_probe_json (JSONB), created_by_usuario_id (FK), created_at
- UNIQUE (assinatura_id, url_hash)

**snap**
- id, assinatura_id (FK), video_id (FK), created_by_usuario_id (FK),
  subject_template_id (FK), nickname_snapshot, tipo_snap,
  status, public_share_token (único), is_public, data_filmagem,
  start_seconds, start_frame, resolved_start_seconds,
  duration_seconds, snapshot_duration_seconds, fps, max_width,
  format, quality, subject_id, subject_json (JSONB),
  video_probe_json (JSONB), snapshot_video_json (JSONB),
  frames_json (JSONB), overlay_json (JSONB),
  created_at, updated_at, processed_at

**snap_subject_attr**
- id, snap_id (FK), assinatura_id (FK), video_id (FK),
  subject_id, attr_key, value_type, string_value, number_value,
  created_at

**snap_processing_job**
- id, snap_id (FK), assinatura_id (FK), status, attempts,
  next_run_at, claimed_by, claimed_at, started_at, finished_at,
  error_json (JSONB), created_at
- Índice: (status, next_run_at)

### Índices críticos

- idx_snap_assinatura_created (assinatura_id, created_at desc)
- idx_snap_video (assinatura_id, video_id, created_at asc)
- idx_snap_usuario (assinatura_id, created_by_usuario_id, created_at desc)
- idx_snap_subject (assinatura_id, subject_id, created_at desc)
- idx_snap_share_token (public_share_token) único
- idx_attr_string (assinatura_id, attr_key, string_value)
- idx_attr_number (assinatura_id, attr_key, number_value)
- idx_job_status_next_run (status, next_run_at)

---

## Worker Assíncrono

### JobPoller (Scheduled)

Roda a cada app.worker.pollIntervalMs:

```sql
SELECT * FROM snap_processing_job
 WHERE status = 'QUEUED' AND next_run_at <= now()
 ORDER BY created_at
 LIMIT {claimSize}
   FOR UPDATE SKIP LOCKED
```

- Update para RUNNING + claimed_by + claimed_at + started_at
- Submeter ao ExecutorService (fixo app.worker.threads)
- Adquirir Semaphore antes do FFmpeg (app.ffmpeg.maxParallel)
- Atualizar status e resultado ao finalizar

### Recuperação de órfãos

Jobs RUNNING sem heartbeat por mais de workerLockTimeoutSeconds
são recolocados em QUEUED para reprocessamento.

### Retentativas

- Backoff exponencial entre tentativas
- Máximo de tentativas configurável
- Após máximo: status FAILED com error_json preenchido

---

## Serviços principais

- BatchService / SnapService — criar snap, idempotência, ordenação
- JobClaimService — claim via SKIP LOCKED
- FfmpegService — monta args, executa com timeout, captura stderr
- VideoProbeService — detecta FPS, compatibilidade, propriedades
- StorageService — upload S3 + build URL (local em dev, S3 em prod)
- OverlayService — monta drawtext conforme overlay do snap
- TempCleanupService — deleteRecursively + scheduled cleanup
- ResultQueryService — consulta por subject + filtros de atributos

---

## Docker (Produção)

### Dockerfile

- Java 17 runtime + FFmpeg + fontes DejaVu
- Usuário não-root
- HEALTHCHECK via /actuator/health

### docker-compose (produção)

- Serviços: postgres + app
- Volume dedicado para /data/tmp (temp FFmpeg)
- Volume dedicado para /data/storage (se storage local desabilitado,
  pode ser omitido em produção com S3)
- Variáveis de ambiente via .env (nunca comitar credenciais)
- Limites de CPU e memória para Linode 2GB
- restart: unless-stopped

### Variáveis de ambiente obrigatórias em produção

```
STORAGE_ENDPOINT=
STORAGE_REGION=
STORAGE_BUCKET=
STORAGE_ACCESS_KEY=
STORAGE_SECRET_KEY=
STORAGE_PUBLIC_BASE_URL=
STORAGE_PREFIX=
DB_URL=
DB_USERNAME=
DB_PASSWORD=
```

---

## Testes Mínimos (Obrigatório)

- Validação de request (limites, startFrame > startSeconds)
- Montagem segura de comando FFmpeg (sem shell)
- Conversão startFrame -> resolvedStartSeconds
- Feedback de incompatibilidade de vídeo
- Persistência de atributos do subject (string e number)
- Consulta por subjectId e busca por atributos
- Claim com SKIP LOCKED (Testcontainers Postgres)
- Retentativas e backoff
- Recuperação de jobs órfãos
- Cleanup de temp

---

## Critérios de Aceite Técnicos

- POST retorna snapId imediatamente (modo assíncrono)
- Worker processa com limite de paralelismo
- GET retorna parcial durante execução e resultado final ao término
- Frames em ordem cronológica (timestampSeconds asc)
- Snapshot.mp4 gerado e retornado para cada snap bem-sucedido
- subject genérico persistido e pesquisável
- startFrame ou startSeconds aceitos (startFrame tem precedência)
- Vídeo incompatível retorna feedback claro com videoProbe.reason
- Storage local em dev, S3 em produção
- Temp sempre limpo após job concluído
- Múltiplas instâncias não duplicam jobs (SKIP LOCKED)
- SSRF bloqueado, timeouts aplicados, segredos via env vars