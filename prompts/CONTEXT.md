# CONTEXT.md — Snap Player API
> Ponto de entrada para toda sessão. Leia este arquivo primeiro.


## Projeto

Plataforma genérica de evidência visual estruturada em vídeo (snap-player-api — backend Java/Spring Boot).
Cliente em produção: **Olho do Dono** — pesagem de bovinos, 30+ clientes.
Repositórios: `snap-player-api` (este) + `snap-player` (Flutter, integração futura).

**Stack:** Java 17 + Spring Boot 3.x · PostgreSQL + Flyway · Nginx + systemd (deploy atual em VM Ubuntu/Linode) · FFmpeg/FFprobe via ProcessBuilder · S3 Linode (AWS SDK v2) · storage local em dev · Docker (plano futuro opcional).

---

## Trabalhando em

Entrega 6 — Hardening operacional + deploy em VM Ubuntu (env vars, Nginx/systemd, health check)

---

## Estado das entregas (fevereiro 2026)

- Entrega 1 — CONCLUÍDA (POST/GET /v2/snaps, seeds, pipeline FFmpeg)
- Entrega 2 — CONCLUÍDA (share público, listas mine, busca por subject)
- Entrega 3 — CONCLUÍDA (contexto assinatura, feature flag token, paginação, observabilidade)
- Entrega 4 — CONCLUÍDA (fila DB, worker SKIP LOCKED, retry/backoff, stale recovery, cleanup, heartbeat, Actuator)
- Entrega 5 — CONCLUÍDA (StorageService dual-backend local/S3, keys estáveis por snapId)

---

## Próximas prioridades (produção — Olho do Dono)

1. **Hardening operacional** — env vars para segredos, deploy em VM Ubuntu (Nginx + systemd), health check, limites CPU/mem (Linode 2GB)
2. **Validação com cliente** — SubjectTemplate bovinos (brinco, raça, sexo, peso_referencia, lote, pasto, observacoes), smoke test com vídeos reais

---

## Fora do escopo (fase futura)

Anthropic API · análise diferencial · laudo automático · snap_session · Player Flutter · autenticação completa · multi-assinatura operacional

---

## Arquivos de referência

**Config/regras (carregados automaticamente):**

| Arquivo | Papel |
|---|---|
| AGENTS.md | Regras de formato e padrões — Codex CLI |
| CLAUDE.md | Regras de formato e padrões — Claude Code CLI |
| README.md | Requisitos, build, run, endpoints principais |

**Planejamento:**

| Arquivo | Papel |
|---|---|
| prompts/masters/master-tecnico.md | Arquitetura, FFmpeg, storage, worker |
| prompts/masters/master-produto-snap.md | Domínio, regras de produto, multi-assinatura |
| prompts/masters/master-roadmap.md | Sequenciamento tático de entregas |
| prompts/masters/master-monetizacao.md | Estratégia comercial e posicionamento |
| prompts/masters/master-adrs.md | Governança de decisões arquiteturais |
| prompts/adrs/ | ADRs individuais |

**Código quente:**

| Arquivo | Papel |
|---|---|
| src/main/java/com/snapplayerapi/api/v2/controller/SnapV2Controller.java | Controller principal v2 |
| src/main/java/com/snapplayerapi/api/v2/service/SnapV2Service.java | Serviço principal v2 |
| src/main/java/com/snapplayerapi/api/v2/service/SnapProcessingJobWorker.java | Worker assíncrono |
| src/main/java/com/snapplayerapi/api/v2/service/SnapProcessingJobMaintenanceService.java | Manutenção de jobs |
| src/main/java/com/snapplayerapi/api/web/GlobalExceptionHandler.java | Exception handler global |
| src/main/java/com/snapplayerapi/api/service/FfmpegService.java | Processamento FFmpeg |
| src/main/resources/application.yml | Configurações e feature flags |

> ⚠ `src/main/java/com/snapplayerapi/api/controller/` — v1/MVP legado, não modificar.

**Comandos:**

- Test: `mvn -Dmaven.repo.local=.m2/repository test -q 2>&1 | grep -E "Tests run:|BUILD|FAILURE|ERROR"`
- Run: `mvn -Dmaven.repo.local=.m2/repository spring-boot:run`
- Run sync: adicionar `--app.snap.asyncCreateEnabled=false`

---

## Governança dos planos

- Nova regra técnica/arquitetura → `master-tecnico.md` ou `master-produto-snap.md`
- Mudança de escopo/prioridade → `master-roadmap.md`
- Mudança comercial → `master-monetizacao.md`
- Decisão estrutural → novo ADR em `prompts/adrs/`
- `CONTEXT.md` reflete sempre o estado atual real

*Atualizado: fevereiro 2026.*
