# Snap Player API

API para extração de frames e snapshot de vídeo com FFmpeg, com metadados rastreáveis via `subject` configurável.

## Planejamento

Documentação viva em `prompts/`. Ponto de entrada: **`prompts/CONTEXT.md`**.

## Requisitos

- Java 17+
- Maven 3.9+
- `ffmpeg` e `ffprobe` no `PATH`
- Para overlay `drawtext`: FFmpeg com `libfreetype` + fonte TTF (padrão: DejaVu Sans Bold)

```bash
# Ubuntu/Debian
sudo apt install -y ffmpeg fontconfig fonts-dejavu-core
```

## Build e Testes

```bash
mvn -Dmaven.repo.local=.m2/repository test
```

## Como Rodar

```bash
# Modo assíncrono (padrão)
mvn -Dmaven.repo.local=.m2/repository spring-boot:run

# Modo síncrono (dev/debug)
mvn -Dmaven.repo.local=.m2/repository spring-boot:run \
  '-Dspring-boot.run.arguments=--app.snap.asyncCreateEnabled=false'
```

Configs IntelliJ (`.run/`):
- `Snap Player API (Sync)` — create síncrono
- `Snap Player API (Async)` — asyncCreateEnabled=true

Banco padrão local: `H2` file-based em `./.data/snapplayerapi` (sem configurar PostgreSQL).

## Endpoints

Coleção HTTP em `http/`:
- `http/v2-snaps.http` — CRUD principal de snaps (captura `lastSnapId`, `lastVideoId`)
- `http/v2-share-public.http` — compartilhamento público (captura `lastPublicShareToken`)
- `http/internal-observability.http` — métricas HTTP e de jobs
- `http/v1-processing.http` — endpoint legado MVP síncrono

Principais endpoints (v2):
- `POST /v2/snaps` — cria snap (async: `202`, sync: `201`)
- `GET /v2/snaps/{snapId}` — consulta/polling de estado
- `GET /v2/snaps/search?attrKey=&attrValue=` — busca por atributos
- `GET /v2/snaps/mine?nickname=` — snaps do usuário
- `GET /v2/videos/{videoId}/snaps` — snaps de um vídeo
- `GET /v2/videos/mine?nickname=` — vídeos do usuário
- `POST /v2/snaps/{snapId}/share` — gera token de compartilhamento público
- `GET /public/snaps/{token}` — acesso público ao snap
- `GET /actuator/health` — health check
- `GET /actuator/metrics` — métricas Spring Boot
- `GET /internal/observability/snap-job-metrics` — telemetria interna de jobs

## Exemplo Rápido

```bash
curl -sS -X POST http://127.0.0.1:8080/v2/snaps \
  -H 'Content-Type: application/json' \
  -d '{
    "videoUrl": "https://exemplo.com/video.mp4",
    "nickname": "operador1",
    "email": "operador1@example.com",
    "dataFilmagem": "2026-02-24T14:30:00-03:00",
    "startFrame": 360,
    "durationSeconds": 1.0,
    "fps": 5,
    "subject": {
      "attributes": [
        { "key": "brinco", "type": "string", "stringValue": "123" },
        { "key": "peso", "type": "number", "numberValue": 450.0 }
      ]
    }
  }'
```

## Estrutura do Projeto

```
src/main/java/com/snapplayerapi/api/
  controller/          endpoints (v1 legado + v2)
  service/             FFmpeg, probe, worker, manutenção de jobs
  web/                 interceptors, filtros, exception handler
  dto/                 contratos JSON
src/main/resources/
  application.yml      configurações principais
  db/migration/        Flyway (V1, V2, V3)
http/                  coleções IntelliJ HTTP Client
prompts/               documentação viva (masters, ADRs, templates)
```

## Licença / Uso

Definir conforme necessidade de distribuição (interno, privado, OSS, etc.).
