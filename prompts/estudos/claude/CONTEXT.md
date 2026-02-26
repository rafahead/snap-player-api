# CONTEXT.md — Snap Player / Snap Player API
> Ponto de entrada para toda sessão de desenvolvimento.
> Leia este arquivo primeiro. Depois acesse os masters conforme necessidade.

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

- Entrega 4 — PARCIALMENTE CONCLUÍDA (slices 1-3)
  - Fila em DB (snap_processing_job) com Flyway
  - Worker local com polling e claim via FOR UPDATE SKIP LOCKED
  - POST /v2/snaps assíncrono opcional por feature flag
  - Retentativas com backoff exponencial
  - Recuperação de jobs RUNNING órfãos
  - Cleanup agendado de jobs terminais
  - /internal/observability/snap-job-metrics

### O que está pendente (prioridade para produção)

Ver seção "Próximas prioridades" abaixo.

---

## Próximas prioridades — Foco em produção (Olho do Dono)

### PRIORIDADE 1 — Entrega 4 completa (assíncrono como padrão)

O modo assíncrono já existe por feature flag. Precisa virar padrão
e ser endurecido para produção.

Pendências:
- Decidir e executar rollout do modo assíncrono como padrão
- Heartbeat/renovação de lock para jobs longos
- Tuning de concorrência/claim para múltiplos workers
- Telemetria externa (Actuator/Prometheus ou OpenTelemetry básico)
- Worker separado do processo web (operacional)

### PRIORIDADE 2 — Storage S3 (Linode Object Storage)

Substituir storage local por S3 compatível.
Configuração já prevista em application.yml (master técnico).
Necessário para ambiente de produção.

Pendências:
- Ativar StorageService com AWS SDK v2
- Configurar endpoint, bucket, credenciais via variáveis de ambiente
- Testar upload de frames e snapshot.mp4
- Garantir limpeza de temp após upload bem-sucedido

### PRIORIDADE 3 — Hardening operacional

- Variáveis de ambiente para todos os segredos (sem hardcode)
- docker-compose de produção separado do de desenvolvimento
- Health check funcional (/actuator/health)
- Limites de recursos no Docker (CPU/memória para Linode 2GB)
- README atualizado com instruções de deploy em produção

### PRIORIDADE 4 — Validação com Olho do Dono

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
