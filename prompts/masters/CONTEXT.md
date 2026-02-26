  # CONTEXT.md — Snap Player / Snap Player API
> Ponto de entrada para toda sessão de desenvolvimento.
> Leia este arquivo primeiro. Depois acesse os masters conforme necessidade.

## Preferência de resposta (economia de tokens)

- Ver `prompts/masters/CONTEXT-CONFIG.md` para formato de resposta preferido.
- Gatilho recomendado: `modo silencioso estrito`
- Significado: sem updates intermediários; só resposta final em 1 linha (salvo bloqueio/erro crítico)

---

## O que é este projeto

Plataforma genérica de evidência visual estruturada em vídeo.
O operador informa a URL de um vídeo, navega, marca um momento
relevante (snap), preenche atributos estruturados via template
configurável, e o sistema gera frames, clip e metadados rastreáveis
e pesquisáveis.

Dois repositórios:
- `snap-player-api` — backend Java/Spring Boot (este repositório)
- `snap-player` — player Flutter (integração posterior)

---

## Stack

- Java 17 + Spring Boot 3.x
- PostgreSQL + Flyway
- Docker + docker-compose
- FFmpeg / FFprobe via ProcessBuilder
- S3 compatível — Linode Object Storage (AWS SDK v2)
- Armazenamento local em desenvolvimento

---

## Estado atual do projeto (fevereiro 2026)

### O que está implementado e funcionando

- Entrega 1 — CONCLUÍDA
  - POST /v2/snaps (síncrono)
  - GET /v2/snaps/{id}
  - GET /v2/videos/{videoId}/snaps
  - Busca básica por subjectId
  - Persistência: assinatura, usuario, video, snap, snap_subject_attr
  - Seeds: assinatura default, template default
  - Pipeline FFmpeg/FFprobe reaproveitado do MVP

- Entrega 2 — CONCLUÍDA
  - POST /v2/snaps/{snapId}/share
  - GET /public/snaps/{token}
  - GET /v2/snaps/mine
  - GET /v2/videos/mine
  - Busca por subject (subjectId + attrKey/attrValue por igualdade)

- Entrega 3 — CONCLUÍDA
  - Abstração de contexto de assinatura
  - Preparação para token (feature flag)
  - Paginação/ordenação padronizadas
  - Observabilidade mínima (X-Request-Id, logs, snapshot interno)

- Entrega 4 — CONCLUÍDA (slices 1-8)
  - Fila em DB (snap_processing_job) com Flyway
  - Worker local com polling e claim via FOR UPDATE SKIP LOCKED
  - POST /v2/snaps assíncrono opcional por feature flag
  - Retentativas com backoff exponencial
  - Recuperação de jobs RUNNING órfãos
  - Cleanup agendado de jobs terminais
  - /internal/observability/snap-job-metrics
  - Slice 4 (PREREQUISITE): CONCLUÍDO — correções bloqueantes B1-B4 (ADRs 0006/0007):
    - B1: paginação nativa (OffsetBasedPageRequest + Slice<T>)
    - B2: datediff H2 → extract epoch PostgreSQL
    - B3: @Lob → @Column(columnDefinition="text"); migrations clob→text
    - B4: cleanup temp dir no finally (deleteRecursively)
  - Slice 5 (CONCLUÍDO 2026-02-26): hardening de contrato e segurança
    - I1: comparação timing-safe do api_token (MessageDigest.isEqual)
    - I2: upsert otimista em resolveOrCreateVideo (DataIntegrityViolationException)
    - I3: upsert otimista em resolveUsuario; decisão "última escrita vence" documentada
    - I4: InternalApiTokenInterceptor para /internal/** (app.internal.accessToken)
    - I5: GlobalExceptionHandler retorna mensagem genérica em 5xx + log.error com stack
    - I6: handler MissingServletRequestParameterException → 400 ApiErrorResponse
    - I7: POST /v2/snaps → 201 Created (sync) ou 202 Accepted (async)
    - M1: outputDir removido de SnapResponse
    - M2: lockOwner/lockedAt removidos de SnapJobResponse
  - Slice 6 (CONCLUÍDO 2026-02-26): rollout async como padrão
    - asyncCreateEnabled: true em application.yml
    - SnapV2ControllerIntegrationTest e TokenAuthIntegrationTest: asyncCreateEnabled=false fixado
    - .run/Snap Player API (Sync): --app.snap.asyncCreateEnabled=false adicionado
    - http/v2-snaps.http: script pós-resposta 201||202; @internalToken adicionado
    - internal-observability.http: nota X-Internal-Token; exemplos comentados
    - README.md: estado e seções Entrega 4 slices 4-5-6 atualizados
  - Slice 7 (CONCLUÍDO 2026-02-26): hardening do worker
    - workerInstanceId: ${HOSTNAME:local-worker} (container ID no Docker)
    - refreshLockedAtForRunningOwner: @Modifying JPQL heartbeat no repository
    - heartbeatRunningJobs(): @Scheduled(30s) renova locked_at dos RUNNING jobs desta instância
    - workerHeartbeatIntervalMs: 30000; regra: heartbeat < lockTimeout/3
  - Slice 8 (CONCLUÍDO 2026-02-26): telemetria externa básica (Actuator)
    - `spring-boot-starter-actuator` habilitado
    - `/actuator/health` e `/actuator/metrics` expostos
    - métricas customizadas de jobs no Actuator (`snap.jobs.*`)
    - `X-Request-Id` truncado a 64 chars para correlação/logs

### O que está pendente (prioridade para produção)

Ver seção "Próximas prioridades" abaixo.

---

## Próximas prioridades — Foco em produção (Olho do Dono)

### PRIORIDADE 1 — Storage S3 (Linode Object Storage)

Substituir storage local por S3 compatível.
Configuração já prevista em application.yml (master técnico).
Necessário para ambiente de produção.

Pendências:
- Ativar StorageService com AWS SDK v2
- Configurar endpoint, bucket, credenciais via variáveis de ambiente
- Testar upload de frames e snapshot.mp4
- Garantir limpeza de temp após upload bem-sucedido

### PRIORIDADE 2 — Hardening operacional

- Variáveis de ambiente para todos os segredos (sem hardcode)
- docker-compose de produção separado do de desenvolvimento
- Health check funcional (/actuator/health)
- Limites de recursos no Docker (CPU/memória para Linode 2GB)
- README atualizado com instruções de deploy em produção

### PRIORIDADE 3 — Validação com Olho do Dono

- SubjectTemplate específico para bovinos/pesagem
  Campos sugeridos: brinco, raça, sexo, peso_referencia,
  condição_corporal, lote, pasto, observacoes
- Smoke test com vídeos reais da operação
- Validação de performance no servidor de produção (Linode 2GB)

---

## Fora do escopo agora (fase futura)

Os itens abaixo foram discutidos e decididos para um segundo momento,
após estabilização em produção para Olho do Dono:

- Integração com Anthropic API (sugestão de atributos por IA)
- Análise diferencial com imagens de referência
- Geração automática de laudo por sessão de inspeção
- Módulo de sessão de inspeção (snap_session)
- Vertical de petróleo/inspeção industrial
- Player Flutter (snap-player)
- Autenticação completa
- Multi-assinatura operacional (modelagem já preparada)

---

## Arquivos de referência

| Arquivo | Papel |
|---|---|
| prompts/masters/master-tecnico.md | Arquitetura, FFmpeg, storage, worker |
| prompts/masters/master-produto-snap.md | Domínio, regras de produto, multi-assinatura |
| prompts/entregas/entregas-api-snap-v2.md | Sequenciamento tático de entregas |
| prompts/masters/master-monetizacao.md | Estratégia comercial e posicionamento |
| prompts/masters/revisao-tecnica-pre-producao.md | Revisão técnica do código — bloqueantes e melhorias antes de produção |
| CONTEXT.md | Este arquivo — ponto de entrada |

---

## Regras de governança dos planos

- Regra nova de negócio/arquitetura => atualizar master-tecnico.md
  ou master-produto-snap.md primeiro
- Mudança de escopo/prioridade de entrega => atualizar
  entregas-api-snap-v2.md
- Mudança de estratégia comercial => atualizar
  master-monetizacao.md
- CONTEXT.md reflete sempre o estado atual real do projeto

---

## Cliente atual em produção

- Olho do Dono — pesagem de bovinos por câmera 3D/2D
- Mais de 30 clientes operando em produção no mundo
- Snap Player usado como ferramenta de suporte para captura
  e registro visual individual de animais ao longo do ciclo

---

*Snap Player API — plataforma genérica de evidência visual estruturada.*
*Atualizado: fevereiro 2026.*
