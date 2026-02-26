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
- Entrega 4 — PARCIALMENTE CONCLUÍDA (slices 1-3 implementados)

---

## Prioridade imediata: ir para produção (Olho do Dono)

Antes de qualquer nova funcionalidade, o objetivo é estabilizar
o que existe e colocar em produção para a equipe do Olho do Dono.

Sequência de produção:
1. Completar Entrega 4 (assíncrono como padrão)
2. Ativar storage S3 (Linode Object Storage)
3. Hardening operacional
4. SubjectTemplate de bovinos + smoke test com vídeos reais

---

## Simplificações aceitas (fase atual)

- 1 assinatura default já criada
- 1 template default (+ template bovinos para Olho do Dono)
- Sem autenticação/login completo
- Identidade por nickname + email
- Processamento assíncrono por feature flag (tornar padrão)
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

### O que já foi implementado (slices 1-3)

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

### Pendente (próximos slices)

#### Slice 4 — Rollout do modo assíncrono como padrão

- Definir estratégia de rollout (feature flag por ambiente)
- Tornar app.snap.asyncCreateEnabled=true o padrão em produção
- Validar comportamento do GET /v2/snaps/{id} sob carga leve
- Atualizar README com fluxo assíncrono como padrão
- Atualizar exemplos http/*.http

Critério de aceite:
- POST retorna snapId imediatamente em produção
- GET retorna estado correto durante processamento e resultado final

#### Slice 5 — Hardening do worker

- Heartbeat/renovação de lock para jobs longos
  (evitar reprocessamento de jobs que ainda estão rodando)
- Tuning de concorrência e claim para Linode 2GB
- Validar comportamento com múltiplos workers simultâneos
- Documentar limites operacionais

Critério de aceite:
- Jobs longos não são reprocessados indevidamente
- Dois workers simultâneos não duplicam processamento

#### Slice 6 — Telemetria externa básica

- Actuator com /actuator/health e /actuator/metrics expostos
- Métricas de jobs (processados, falhos, tempo médio)
- Logs estruturados com X-Request-Id em todas as saídas
- Documentar métricas disponíveis no README

Critério de aceite:
- Health check funcional para docker-compose
- Métricas básicas acessíveis via Actuator

---

## Entrega 5 — Storage S3 (Linode Object Storage)

### Meta

Substituir storage local por Linode Object Storage para produção.

### Prazo estimado

2 a 3 dias úteis

### Escopo

- Ativar StorageService com AWS SDK v2
- Configurar endpoint, bucket e credenciais via variáveis de ambiente
- Chaves de storage:
  - frames: {prefix}/frames/{snapId}/frame_00001.jpg
  - snapshot: {prefix}/snapshots/{snapId}/snapshot.mp4
- URL pública: publicBaseUrl + key
- Limpeza de temp sempre após upload bem-sucedido
- Fallback para storage local em desenvolvimento (app.storage.local.enabled)
- Testes de integração com mock S3 (Testcontainers LocalStack ou similar)
- docker-compose de produção com variáveis de ambiente via .env
- README atualizado com configuração de produção

### Critérios de aceite

- Frames e snapshot.mp4 sobem para o bucket Linode
- URLs retornadas no snap são públicas e acessíveis
- Temp folder removida após upload bem-sucedido
- Storage local continua funcionando em desenvolvimento
- Credenciais nunca no código (sempre via env vars)

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

## Cronograma resumido (a partir de agora)

| Entrega | Foco | Estimativa |
|---|---|---|
| Entrega 4 (slices 4-6) | Assíncrono padrão + hardening worker + telemetria | 3-5 dias úteis |
| Entrega 5 | Storage S3 Linode | 2-3 dias úteis |
| Entrega 6 | Hardening operacional + deploy | 2-3 dias úteis |
| Entrega 7 | Template bovinos + validação | 1-2 dias úteis |

**Total estimado para produção: 8 a 13 dias úteis**

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
