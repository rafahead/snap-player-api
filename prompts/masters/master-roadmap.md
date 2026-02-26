# Plano de Entregas v2 — API Snap-First

## Papel deste arquivo

Sequenciamento tático de implementação.

Fontes de verdade:
- `CONTEXT.md` (estado atual)
- `prompts/masters/master-tecnico.md`
- `prompts/masters/master-produto-snap.md`

---

## Estado atual (fevereiro 2026)

- Entrega 1 — CONCLUÍDA
- Entrega 2 — CONCLUÍDA
- Entrega 3 — CONCLUÍDA
- Entrega 4 — CONCLUÍDA (slices 1-8)
  - Slices 1-3: fila DB + worker + async por feature flag
  - Slice prereq B1-B4: CONCLUÍDO em 2026-02-26
  - Slice 5 — hardening contrato/segurança: CONCLUÍDO em 2026-02-26 (I1-I7, M1-M2)
  - Slice 6 — rollout async como padrão: CONCLUÍDO em 2026-02-26
  - Slice 7 — worker hardening: CONCLUÍDO em 2026-02-26
  - Slice 8 — telemetria externa (Actuator): CONCLUÍDO em 2026-02-26
  - Entrega 5 — Storage S3 (Linode Object Storage): CONCLUÍDA em 2026-02-26
  - Próximo: Entrega 6 — Hardening operacional e deploy

---

## Prioridade imediata: ir para produção (Olho do Dono)

Antes de qualquer nova funcionalidade, o objetivo é estabilizar
o que existe e colocar em produção para a equipe do Olho do Dono.

Sequência de produção:
1. Hardening operacional
2. SubjectTemplate de bovinos + smoke test com vídeos reais

---

## Simplificações aceitas (fase atual)

- 1 assinatura default já criada
- 1 template default (+ template bovinos para Olho do Dono)
- Sem autenticação/login completo
- Identidade por nickname + email
- Processamento assíncrono já padrão (`asyncCreateEnabled=true`)
- Storage S3 Linode (substituir local)

## Regras que permanecem obrigatórias

- Snap é a entidade principal
- startFrame tem precedência sobre startSeconds
- subject.id faz fallback para snapId
- snapshotDurationSeconds com fallback para durationSeconds
- ffprobe para compatibilidade + motivo de falha
- assinatura_id em todas as tabelas (preparado para multi-tenant)

---

## Entrega 4 — Assíncrono como padrão (PRIORIDADE MÁXIMA)

### O que já foi implementado (slices 1-3 + prereq B1-B4)

- Tabela snap_processing_job com migration Flyway
- Worker local com polling e claim via FOR UPDATE SKIP LOCKED
- POST /v2/snaps assíncrono por feature flag
- GET /v2/snaps/{id} como polling de estado/resultado
- Campo job em SnapResponse
- Retentativas com backoff exponencial
- Recuperação de jobs RUNNING órfãos (stale lock timeout)
- Cleanup agendado de jobs terminais
- /internal/observability/snap-job-metrics
- Testes de integração cobrindo async, retry, stale recovery, cleanup
- Arquivos http/*.http atualizados
- **Prereq B1-B4 CONCLUÍDO (2026-02-26):** paginação nativa (OffsetBasedPageRequest+Slice),
  datediff→extract epoch, @Lob→@Column(text)+migrations, cleanup temp no finally

### Slices finais (concluídos)

#### Slice 5 — Hardening de contrato e segurança — CONCLUÍDO 2026-02-26

I1 (timing-safe token), I2 (upsert resolveOrCreateVideo), I3 (upsert resolveUsuario),
I4 (InternalApiTokenInterceptor /internal/**), I5 (500 genérico + log),
I6 (MissingServletRequestParameterException → 400), I7 (201/202 em POST),
M1 (outputDir removido do SnapResponse), M2 (lockOwner/lockedAt removidos do SnapJobResponse).
Resultado: 31 testes, 0 falhas.

#### Slice 6 — Rollout do modo assíncrono como padrão — CONCLUÍDO 2026-02-26

`asyncCreateEnabled=true` default; testes sync fixados; `.run/Sync` atualizado; http/*.http e README atualizados. 31 testes, 0 falhas.

#### Slice 7 — Hardening do worker — CONCLUÍDO 2026-02-26

workerInstanceId=${HOSTNAME:local-worker}; heartbeatRunningJobs() @Scheduled(30s); refreshLockedAtForRunningOwner @Modifying JPQL; workerHeartbeatIntervalMs=30000; constraints documentados. 31 testes, 0 falhas.

Critério de aceite:
- Jobs longos não são reprocessados indevidamente
- claimed_by identifica a instância corretamente nos logs

#### Slice 8 — Telemetria externa básica — CONCLUÍDO 2026-02-26

- Actuator com /actuator/health e /actuator/metrics expostos
- Métricas de jobs no Actuator (`snap.jobs.*`: processados/completed, falhos, tempo médio)
- Logs estruturados com X-Request-Id em todas as saídas
- Truncamento de `X-Request-Id` para 64 chars (correlação/logs)

Resultado: 33 testes, 0 falhas.

Critério de aceite: 
- Health check funcional para docker-compose
- Métricas básicas acessíveis via Actuator

---

## Entrega 5 — Storage S3 (Linode Object Storage) — CONCLUÍDA 2026-02-26

### Meta

Substituir storage local por Linode Object Storage para produção.

### Prazo estimado

2 a 3 dias úteis

### Escopo implementado

- `StorageService` com backend local (fallback dev) e backend S3 compatível (AWS SDK v2)
- Configuração `app.storage.local.*` e `app.storage.s3.*` em `application.yml`
- Chaves de storage:
  - frames: `{prefix}/frames/{snapId}/frame_00001.jpg`
  - snapshot: `{prefix}/snapshots/{snapId}/snapshot.mp4`
- `clientRequestId = snapId` no fluxo `v2` (sync/async worker) para gerar keys estáveis
- Persistência dos artefatos no storage antes da limpeza de temp
- Fallback local padrão em `./.data/storage`
- Teste de serviço (`StorageServiceTest`) + suíte completa verde (`34 testes`)

### Escopo planejado (referência histórica / já parcialmente coberto)

- Ativar StorageService com AWS SDK v2
- Configurar endpoint, bucket e credenciais via variáveis de ambiente
- Chaves de storage:
  - frames: {prefix}/frames/{snapId}/frame_00001.jpg
  - snapshot: {prefix}/snapshots/{snapId}/snapshot.mp4
- URL pública: publicBaseUrl + key
- Limpeza de temp sempre após upload bem-sucedido
- Fallback para storage local em desenvolvimento (app.storage.local.enabled)
- Testes de integração com mock S3 (Testcontainers LocalStack ou similar) [opcional para hardening]
- docker-compose de produção com variáveis de ambiente via .env
- README atualizado com configuração de produção

### Critérios de aceite

- Integração S3 compatível implementada via AWS SDK v2
- URLs/paths de artefatos deixam de apontar para temp (persistidos em storage)
- Temp folder removida após persistência em storage (cleanup em `finally`)
- Storage local continua funcionando em desenvolvimento (fallback padrão)
- Credenciais não ficam hardcoded (uso de env vars em `application.yml`)

---

## Entrega 6 — Hardening operacional e deploy

### Meta

Ambiente de produção estável e documentado para Olho do Dono.

### Prazo estimado

2 a 3 dias úteis

### Escopo

- docker-compose.prod.yml separado do de desenvolvimento
  - Sem volumes de código
  - Variáveis de ambiente via .env
  - restart: unless-stopped
  - Limites de CPU e memória (Linode 2GB)
  - Health check via /actuator/health
  - Volume dedicado para /data/tmp
- Dockerfile revisado para produção
  - Usuário não-root
  - Imagem mínima com Java 17 + FFmpeg + fontes
  - HEALTHCHECK configurado
- Validações de configuração na inicialização
  - Falhar rápido se variáveis obrigatórias ausentes
- README de produção completo
  - Pré-requisitos
  - Configuração de variáveis de ambiente
  - Comandos de deploy
  - Comandos de manutenção (restart, logs, backup)

### Critérios de aceite

- docker-compose up funciona em servidor limpo seguindo o README
- Aplicação reinicia automaticamente após crash
- Health check retorna 200 quando tudo está OK
- Logs acessíveis via docker logs
- Nenhuma credencial no código ou docker-compose commitado

---

## Entrega 7 — SubjectTemplate Olho do Dono + validação

### Meta

Template específico para bovinos e validação com vídeos reais
da operação do Olho do Dono.

### Prazo estimado

1 a 2 dias úteis

### Escopo

- Seed ou migration com SubjectTemplate de bovinos:
  ```json
  {
    "nome": "Bovino — Olho do Dono",
    "slug": "bovino_odd",
    "is_default": false,
    "campos": [
      { "key": "brinco", "type": "string", "obrigatorio": true },
      { "key": "raca", "type": "string", "obrigatorio": true },
      { "key": "sexo", "type": "string", "obrigatorio": true },
      { "key": "peso_referencia", "type": "number", "obrigatorio": false },
      { "key": "condicao_corporal", "type": "string", "obrigatorio": false },
      { "key": "lote", "type": "string", "obrigatorio": false },
      { "key": "pasto", "type": "string", "obrigatorio": false },
      { "key": "observacoes", "type": "string", "obrigatorio": false }
    ]
  }
  ```
- Smoke test com vídeos reais da operação
- Validação de performance no servidor de produção
- Ajustes de configuração baseados em vídeos reais
  (resolução, duração, FPS, largura máxima)

### Critérios de aceite

- Template de bovinos disponível por subjectTemplateId
- Snap criado com template de bovinos persiste atributos corretamente
- Smoke test com vídeo real da Olho do Dono passa sem erros
- Performance aceitável para o fluxo de uso esperado

---

## Cronograma resumido (atualizado 2026-02-26)

| Entrega | Foco | Status | Estimativa restante |
|---|---|---|---|
| Entrega 4 — prereq (slices 1-4) | Correções bloqueantes B1-B4 | ✓ CONCLUÍDO | — |
| Entrega 4 — slices 5-8 | Hardening contrato + async padrão + worker + Actuator | ✓ CONCLUÍDO (2026-02-26) | — |
| Entrega 5 | Storage S3 Linode | ✓ CONCLUÍDO (2026-02-26) | — |
| Entrega 6 | Hardening operacional + deploy | PENDENTE | 2-3 dias úteis |
| Entrega 7 | Template bovinos + validação | PENDENTE | 1-2 dias úteis |

**Total estimado para produção: 3 a 5 dias úteis**

---

## Fora do escopo agora (fase futura)

Decidido para após estabilização em produção:

- Integração com Anthropic API (sugestão de atributos por IA)
- Análise diferencial com imagens de referência
- Geração automática de laudo
- Sessão de inspeção (snap_session)
- Vertical de petróleo e gás / inspeção industrial
- Player Flutter
- Autenticação completa
- Multi-assinatura operacional

---

## Política de atualização dos planos

- Regra nova de negócio/arquitetura =>
  atualizar master-tecnico.md ou master-produto-snap.md primeiro
- Mudança de escopo/prioridade =>
  atualizar este arquivo
- Mudança de estratégia comercial =>
  atualizar master-monetizacao.md
- CONTEXT.md sempre reflete o estado atual real
