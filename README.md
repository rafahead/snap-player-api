# Snap Player API

API para extração de frames e snapshot de vídeo com FFmpeg, com metadados rastreáveis via `subject` configurável.

## Planejamento

Documentação viva em `prompts/`. Ponto de entrada: **`prompts/CONTEXT.md`**.
HOWTO de deploy manual em Linode/Ubuntu: `deploy/ubuntu/HOWTO.md`.

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

## Produção (VM Ubuntu/Linode + Nginx + systemd)

Deploy atual de produção é em VM Ubuntu na Linode (sem Docker no curto prazo). O plano Docker fica
como trilha futura.

Arquivos de apoio versionados:
- `src/main/resources/application-prod.yml` — perfil `prod` (PostgreSQL + S3 + fail-fast)
- `deploy/ubuntu/systemd/snap-player-api.service` — unit file base do serviço
- `deploy/ubuntu/nginx/snap-player-api.conf` — reverse proxy base
- `deploy/ubuntu/env/snap-player-api.env.example` — variáveis de ambiente de produção (exemplo)

Pré-requisitos no servidor (Ubuntu):
- Java 17
- FFmpeg/FFprobe + fontes DejaVu
- PostgreSQL
- Nginx

```bash
sudo apt update
sudo apt install -y openjdk-17-jre-headless ffmpeg fonts-dejavu-core postgresql nginx
```

Build local do artefato:

```bash
mvn -Dmaven.repo.local=.m2/repository package
```

Provisionamento base (exemplo):

```bash
sudo useradd --system --home /opt/snap-player-api --shell /usr/sbin/nologin snapplayer || true
sudo mkdir -p /opt/snap-player-api /data/tmp/video-frames-processing /etc/snap-player-api
sudo chown -R snapplayer:snapplayer /opt/snap-player-api /data/tmp/video-frames-processing
sudo cp target/snap-player-api-0.0.1-SNAPSHOT.jar /opt/snap-player-api/snap-player-api.jar
sudo cp deploy/ubuntu/systemd/snap-player-api.service /etc/systemd/system/
sudo cp deploy/ubuntu/env/snap-player-api.env.example /etc/snap-player-api/snap-player-api.env
sudo cp deploy/ubuntu/nginx/snap-player-api.conf /etc/nginx/sites-available/snap-player-api.conf
sudo ln -sf /etc/nginx/sites-available/snap-player-api.conf /etc/nginx/sites-enabled/snap-player-api.conf
```

Ajuste `/etc/snap-player-api/snap-player-api.env` com:
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `STORAGE_*` (Linode Object Storage)
- `SNAP_PUBLIC_BASE_URL` (URL pública da API)
- `APP_INTERNAL_ACCESS_TOKEN` (recomendado para `/internal/**`, opcional se protegido por rede/Nginx)

Subir serviço:

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now snap-player-api
sudo nginx -t
sudo systemctl reload nginx
```

Comandos de manutenção:

```bash
sudo systemctl status snap-player-api
sudo systemctl restart snap-player-api
sudo journalctl -u snap-player-api -f
curl -fsS http://127.0.0.1:8080/actuator/health
curl -fsS https://api.example.com/actuator/health
```

Notas operacionais:
- O perfil `prod` falha na inicialização se detectar H2, storage local habilitado, S3 desabilitado
  ou `app.processing.tmpBase` relativo.
- O perfil `prod` também valida a relação `workerHeartbeatIntervalMs < workerLockTimeoutSeconds * 1000 / 3`.
- Configure TLS no Nginx (ex.: Certbot) antes de expor publicamente.

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
