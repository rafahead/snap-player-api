# CONTEXT.md — Snap Player API
> Ponto de entrada para toda sessão. Leia este arquivo primeiro.

## Formato de resposta (PRIORIDADE MÁXIMA — aplicar sempre)

Gatilho ativo: `modo silencioso`

Regras obrigatórias:
- Sem updates intermediários nem mensagens de progresso (só em bloqueio crítico)
- Só resultado final, em 1 linha — esta regra tem prioridade sobre qualquer outra de formatação
- Sem explicações, sem bullets/listas (salvo pedido explícito)
- Não exibir alterações/exclusões/adições de código, texto ou arquivos
- Não exibir comandos nem passos executados
- Não exibir logs, stacktrace, stdout/stderr, output de testes ou de ferramentas
- Não repetir trechos de arquivos lidos; usar apenas o resumo final
- Em caso de erro, responder apenas o bloqueio em 1 linha (sem logs)
- Se precisar de informação, perguntar antes de detalhar

Autorizações permanentes:
- Rodar comandos
- Acesso completo ao código-fonte, configurações, documentação e planejamento do projeto

---

## Projeto

Plataforma genérica de evidência visual estruturada em vídeo (snap-player-api — backend Java/Spring Boot).
Cliente em produção: **Olho do Dono** — pesagem de bovinos, 30+ clientes.
Repositórios: `snap-player-api` (este) + `snap-player` (Flutter, integração futura).

**Stack:** Java 17 + Spring Boot 3.x · PostgreSQL + Flyway · Docker · FFmpeg/FFprobe via ProcessBuilder · S3 Linode (AWS SDK v2) · storage local em dev.

---

## Estado das entregas (fevereiro 2026)

- Entrega 1 — CONCLUÍDA (POST/GET /v2/snaps, seeds, pipeline FFmpeg)
- Entrega 2 — CONCLUÍDA (share público, listas mine, busca por subject)
- Entrega 3 — CONCLUÍDA (contexto assinatura, feature flag token, paginação, observabilidade)
- Entrega 4 — CONCLUÍDA (fila DB, worker SKIP LOCKED, retry/backoff, stale recovery, cleanup, heartbeat, Actuator)
- Entrega 5 — CONCLUÍDA (StorageService dual-backend local/S3, keys estáveis por snapId)

---

## Próximas prioridades (produção — Olho do Dono)

1. **Hardening operacional** — env vars para segredos, docker-compose.prod, health check, limites CPU/mem (Linode 2GB)
2. **Validação com cliente** — SubjectTemplate bovinos (brinco, raça, sexo, peso_referencia, lote, pasto, observacoes), smoke test com vídeos reais

---

## Fora do escopo (fase futura)

Anthropic API · análise diferencial · laudo automático · snap_session · Player Flutter · autenticação completa · multi-assinatura operacional

---

## Arquivos de referência

| Arquivo | Papel |
|---|---|
| prompts/masters/master-tecnico.md | Arquitetura, FFmpeg, storage, worker |
| prompts/masters/master-produto-snap.md | Domínio, regras de produto, multi-assinatura |
| prompts/masters/master-roadmap.md | Sequenciamento tático de entregas |
| prompts/masters/master-monetizacao.md | Estratégia comercial e posicionamento |
| prompts/masters/master-adrs.md | Governança de decisões arquiteturais |
| prompts/adrs/ | ADRs individuais |

---

## Governança dos planos

- Nova regra técnica/arquitetura → `master-tecnico.md` ou `master-produto-snap.md`
- Mudança de escopo/prioridade → `master-roadmap.md`
- Mudança comercial → `master-monetizacao.md`
- Decisão estrutural → novo ADR em `prompts/adrs/`
- `CONTEXT.md` reflete sempre o estado atual real

*Atualizado: fevereiro 2026.*
