# Revisão Técnica Pré-Produção — snap-player-api

> Gerado em: fevereiro 2026
> Objetivo: identificar o que precisa ser corrigido **antes de avançar** para os slices
> 4–6 e produção (S3, hardening, Olho do Dono).
> Quando um item for resolvido, marcar como FEITO com a data.

---

## Contexto da revisão

Revisão completa do código das Entregas 1–4 (slices 1–3) cruzando:
- código implementado via Codex CLI
- paradigmas decididos nos masters e ADRs
- novos paradigmas discutidos neste plano (`prompts/entregas/revisao-tecnica-pre-producao.md`)

Os riscos foram classificados em três faixas:

- **BLOQUEANTE** — impede ida a produção ou vai quebrar em Postgres real
- **IMPORTANTE** — deve ser corrigido no hardening, antes do primeiro cliente
- **MELHORIA** — pode ser adiado para após validação em produção

---

## BLOQUEANTES (corrigir antes de qualquer deploy)

### B1 — Paginação em memória sem LIMIT no banco

**Arquivo:** `SnapV2Service.paginateAndSort` + todos os métodos de lista nos repositórios
**Problema:** As queries no banco retornam **todos** os registros da assinatura. A paginação
é feita em memória na JVM depois. Com volume de produção (Olho do Dono tem 30+ clientes),
uma assinatura com milhares de snaps vai saturar a heap a cada request de lista.
**Onde está:** `SnapRepository` — nenhuma query de lista tem `LIMIT` ou paginação nativa.
`listMineVideos` é o pior caso: carrega todos os snaps do nickname para agregar em memória.
**Correção:** migrar para `Pageable` do Spring Data com `Page<T>` nas queries, ou usar
queries nativas com `LIMIT`/`OFFSET`. O contrato de response (`PageMetaResponse`) já está
preparado — só o lado do banco está faltando.
**Status:** FEITO (2026-02-26) — `OffsetBasedPageRequest` + `Slice<T>` em todas as queries de lista; `JpaSort.unsafe` para agregados; `listMineVideos` com JPQL GROUP BY + query secundária para `latestSnapId`. Testes atualizados: `$.total` agora reflete items da página (sem COUNT total, conforme ADR 0006).

---

### B2 — Query nativa com sintaxe H2 (`datediff`) quebra em Postgres real

**Arquivo:** `SnapProcessingJobRepository.terminalDurationSummary()`
**Problema:** A função `datediff('MILLISECOND', started_at, finished_at)` é sintaxe H2.
Em PostgreSQL real, a sintaxe equivalente é `EXTRACT(EPOCH FROM (finished_at - started_at)) * 1000`.
Essa query **vai falhar** assim que o banco for PostgreSQL.
**Impacto:** Endpoint `/internal/observability/snap-job-metrics` quebra em produção.
**Correção:** substituir por sintaxe compatível com Postgres, ou reescrever o cálculo
em Java após buscar os timestamps.
**Status:** FEITO (2026-02-26) — `datediff(...)` substituído por `extract(epoch from (finished_at - started_at)) * 1000` em `SnapProcessingJobRepository.terminalDurationSummary()`.

---

### B3 — `@Lob` em `String` pode mapear para `OID` em PostgreSQL

**Arquivo:** Todas as entidades com colunas JSON: `SnapEntity` (`subjectJson`, `outputJson`,
`probeJson`), `SnapProcessingJobEntity` (`lastError`), `VideoEntity` (`probeJson`),
`AssinaturaEntity`.
**Problema:** Com Hibernate + PostgreSQL, `@Lob` em campo `String` pode ser mapeado como
`OID` (large object), não como `TEXT`. O comportamento com `OID` é diferente: não é indexável,
não aparece em `SELECT` simples, tem semântica de streaming separado e pode quebrar
transações.
**Correção:** Adicionar `@Column(columnDefinition = "text")` em todas as colunas JSON
que usam `@Lob`, ou remover o `@Lob` e confiar no mapeamento padrão `VARCHAR`/`TEXT`
do Hibernate para `String`.
**Verificar:** rodar a aplicação apontando para PostgreSQL real e confirmar os tipos das
colunas com `\d snap` no psql antes do primeiro deploy.
**Status:** FEITO (2026-02-26) — `@Lob` removido de `SnapEntity`, `SnapProcessingJobEntity`, `VideoEntity`; substituído por `@Column(columnDefinition = "text")`. Migrations V1 e V3 corrigidas de `clob` para `text` (tipo nativo PostgreSQL). Schema validation desabilitada em testes H2 (`ddl-auto=none` via `@TestPropertySource`).

---

### B4 — Ausência de limpeza de diretórios temporários FFmpeg

**Arquivo:** `TempStorageService` + `VideoFrameSnapProcessingGateway`
**Problema:** Cada snap cria um diretório em `.data/tmp/video-frames-processing/{requestId}/item-{n}/`.
O `TempStorageService` tem métodos de criação e listagem de frames, mas **não há chamada de cleanup**
após o processamento no gateway. Em produção contínua, o disco será esgotado.
**Onde confirmado:** `VideoFrameSnapProcessingGateway` não chama `TempStorageService.deleteDir()`
no `finally` do processamento.
**Correção:** garantir que o diretório temp seja deletado no `finally` do processamento,
tanto em sucesso quanto em falha. O scheduler de cleanup é complementar (último recurso para
crashes), mas o cleanup direto no gateway deve ser a linha principal.
**Status:** FEITO (2026-02-26) — `TempStorageService.deleteRecursively(Path)` adicionado; `ProcessingVideoFrameService` chama cleanup no `finally` por item (sucesso e falha).

---

## IMPORTANTES (corrigir no hardening, antes do primeiro cliente)

### I1 — Comparação de `api_token` não é timing-safe

**Arquivo:** `SnapV2Service.validateAssinaturaApiTokenIfEnabled()`
**Problema:** `Objects.equals(expectedToken, providedToken)` é vulnerável a timing attacks —
um atacante pode inferir o prefixo correto do token medindo tempo de resposta.
**Impacto:** Baixo em desenvolvimento, real em produção com tráfego externo.
**Correção:** usar `MessageDigest.isEqual(token1.getBytes(), token2.getBytes())` para
comparação em tempo constante.
**Status:** FEITO (2026-02-26) — `MessageDigest.isEqual(token.getBytes(UTF_8), provided.getBytes(UTF_8))` em `validateAssinaturaApiTokenIfEnabled()`.

---

### I2 — Race condition em `resolveOrCreateVideo` → 500 não tratado

**Arquivo:** `SnapV2Service.resolveOrCreateVideo()`
**Problema:** A sequência `findByAssinaturaIdAndUrlHash` (busca) → `videoRepository.save` (criação)
tem janela de race condition em requests paralelos com a mesma URL. O índice único `uk_video_ass_urlhash`
protege contra duplicata no banco, mas a exceção resultante (`DataIntegrityViolationException`)
**não está mapeada no `GlobalExceptionHandler`** — escapa como 500 com mensagem técnica do Hibernate.
**Correção:** envolver o save em try/catch de `DataIntegrityViolationException` e,
em caso de conflito, buscar o registro existente (`findByAssinaturaIdAndUrlHash`).
Padrão "upsert otimista".
**Status:** FEITO (2026-02-26) — try/catch de `DataIntegrityViolationException` com re-fetch por hash em `resolveOrCreateVideo()`.

---

### I3 — Race condition em `resolveUsuario` pode sobrescrever nickname

**Arquivo:** `SnapV2Service.resolveUsuario()`
**Problema:** A sequência `findByEmail` (busca) → `setNickname` → `save` não tem lock.
Dois requests paralelos com o mesmo email e nicknames diferentes podem criar duplicidade
ou sobrescrever mutuamente o nickname.
**Impacto:** Baixo na fase atual (Olho do Dono provavelmente tem requests sequenciais),
mas pode surgir com mais de um operador conectado.
**Correção:** usar `@Transactional` com `SELECT FOR UPDATE` na busca, ou aceitar que
a última gravação vence (documentar a decisão). Também adicionar `updated_at` na entidade
`usuario` para rastreabilidade.
**Status:** FEITO (2026-02-26) — decisão documentada: "última gravação vence" para nickname (campo de display sem implicação de segurança). try/catch de `DataIntegrityViolationException` na criação com re-fetch por email. JavaDoc atualizado com a decisão.

---

### I4 — `/internal/observability/*` sem autenticação

**Arquivo:** `HttpObservabilityController`
**Problema:** Os endpoints de métricas internas são acessíveis por qualquer chamador
que alcance a rede. Expõem contagem de requests, durações, padrões de uso e métricas
de jobs — informação útil para reconhecimento.
**Correção:** proteger por rede (apenas loopback/docker network) **ou** exigir o mesmo
token de assinatura nos headers. A opção mais simples para Linode é restringir via
`nginx`/proxy reverso ou adicionar uma verificação de IP de origem.
**Status:** FEITO (2026-02-26) — `InternalApiTokenInterceptor` criado; registrado em `WebObservabilityConfig` para `/internal/**`. Token configurado via `app.internal.accessToken` (blank = aberto para dev; setar via env var em produção). Comparação timing-safe com `MessageDigest.isEqual`.

---

### I5 — `IllegalStateException` expõe mensagem interna no body 500

**Arquivo:** `GlobalExceptionHandler.handleIllegalStateException()`
**Problema:** A mensagem da exceção é retornada diretamente no body (`ex.getMessage()`).
Isso pode expor detalhes internos como path de arquivo, stderr do FFmpeg, mensagens
do Hibernate ou stack parcial.
**Correção:** retornar mensagem genérica (`"Internal server error"`) no body para 5xx,
e logar a mensagem real internamente com `log.error("Internal error", ex)`.
Adicionar `log.error(...)` para 5xx — atualmente o stack trace **não é logado**, o que
dificulta diagnóstico em produção.
**Status:** FEITO (2026-02-26) — `handleIllegalState` retorna mensagem genérica + `log.error(..., ex)`. Adicionado handler catch-all `@ExceptionHandler(Exception.class)` com a mesma semântica — nenhum detalhe interno escapa para o body de 5xx.

---

### I6 — `MissingServletRequestParameterException` não mapeada no handler

**Arquivo:** `GlobalExceptionHandler`
**Problema:** Quando `nickname` é omitido em `GET /v2/snaps/mine` (param obrigatório),
o Spring lança `MissingServletRequestParameterException` com mensagem técnica padrão.
O handler atual não captura essa exceção, então o cliente recebe um 400 com JSON do
Spring em vez do formato `ApiErrorResponse` consistente.
**Correção:** adicionar handler para `MissingServletRequestParameterException` retornando
400 com mensagem limpa.
**Status:** FEITO (2026-02-26) — handler `@ExceptionHandler(MissingServletRequestParameterException.class)` adicionado em `GlobalExceptionHandler`; retorna 400 com `"Missing required parameter: {name}"` no formato `ApiErrorResponse`.

---

### I7 — `POST /v2/snaps` retorna 200 em vez de 201 Created

**Arquivo:** `SnapV2Controller.createSnap()`
**Problema:** Criação bem-sucedida retorna `200 OK`. O padrão HTTP REST para criação
de recurso é `201 Created`.
**Impacto:** Clientes que checam o código de status para detectar criação (ex: player
Flutter futuro) receberão código inesperado.
**Correção:** adicionar `@ResponseStatus(HttpStatus.CREATED)` no endpoint de criação,
ou retornar `ResponseEntity.created(uri).body(response)`.
**Observação:** verificar se o snap assíncrono deve retornar `202 Accepted` quando
`asyncCreateEnabled=true`.
**Status:** FEITO (2026-02-26) — `createSnap()` retorna `201 Created` (modo sync) ou `202 Accepted` (modo async, quando `asyncCreateEnabled=true`), via `ResponseEntity.status(status).body(response)`. `SnapProperties` injetado no controller para determinar o status.

---

## MELHORIAS (podem aguardar validação em produção)

### M1 — `outputDir` exposto no `SnapResponse` para clientes autenticados

**Arquivo:** `SnapResponse`
**Problema:** O campo `outputDir` expõe o caminho do filesystem local do servidor.
**Decisão:** remover do DTO público ou mover para um campo interno/admin separado.
**Status:** FEITO (2026-02-26) — campo `outputDir` removido de `SnapResponse`; `snap.getOutputDir()` removido do `toResponse()` em `SnapV2Service`.

---

### M2 — `lockOwner` e `lockedAt` expostos no `SnapJobResponse`

**Arquivo:** `SnapJobResponse`
**Problema:** Campos de diagnóstico interno de concorrência do worker são expostos
no contrato público da API.
**Decisão:** mover para um contexto de observabilidade interna, não no response de negócio.
**Status:** FEITO (2026-02-26) — campos `lockedAt` e `lockOwner` removidos de `SnapJobResponse`; `loadSnapJobResponse()` em `SnapV2Service` atualizado.

---

### M3 — `requestId` sem limite de tamanho no MDC

**Arquivo:** `RequestCorrelationFilter`
**Problema:** O `X-Request-Id` fornecido pelo cliente é aceito sem limite de tamanho
(`incoming.strip()`). Pode ser usado para poluir logs com valores longos.
**Correção:** truncar a no máximo 64 chars antes de inserir no MDC.
**Status:** PENDENTE

---

### M4 — `sortBy`/`sortDir` validados no service em vez do controller

**Arquivo:** `SnapV2Controller` + `SnapV2Service.resolveListQuerySpec()`
**Problema:** Parâmetros de sort são `String` livre no controller — a validação da
lista de valores permitidos ocorre dentro do service. Um parâmetro inválido produz
`IllegalArgumentException` que é tratada como 400 pelo handler, o que funciona,
mas a validação poderia ser antecipada para o nível do controller.
**Melhoria:** usar `@Pattern` ou enum com `@RequestParam` para sort fields.
**Status:** PENDENTE — baixa prioridade

---

### M5 — Ausência de testes unitários para `SnapV2Service` e `SnapProcessingJobWorker`

**Problema:** Toda cobertura dessas classes é via integração. Métodos como `paginateAndSort`,
`buildEffectiveSubject`, `sha256`, `canonicalizeUrl`, comparadores de sort e lógica de
backoff do worker são testados apenas indiretamente.
**Melhoria:** adicionar testes unitários focados, especialmente para `paginateAndSort` e
lógica de backoff — onde bugs seriam mais difíceis de diagnosticar via integração.
**Status:** PENDENTE — pode aguardar estabilização do código

---

### M6 — `workerInstanceId` hardcoded como `"local-worker"` no default

**Arquivo:** `SnapProperties`
**Problema:** Em produção com múltiplas instâncias (ou mesmo reinicializações), o
`claimed_by` nos jobs sempre mostrará `"local-worker"`, tornando diagnóstico de
jobs travados mais difícil.
**Correção:** default para `${HOSTNAME:local-worker}` ou gerar um UUID por instância
na inicialização.
**Status:** PENDENTE

---

## Itens já corretos (não precisam de ação)

- Construção de comandos FFmpeg/FFprobe via `ProcessBuilder` (list de args, sem shell) — correto
- SKIP LOCKED para claim de jobs — correto e compatível com Postgres
- Deduplicação de vídeo por hash SHA-256 da URL canonical — correto
- Token de share como UUID sem hífens + índice UNIQUE — correto
- Isolamento por `assinatura_id` em todas as queries — correto (ADR 0003)
- Fallback em cascata para template padrão (`isDefault=true` > `slug=default`) — correto (ADR 0004)
- Fallback `subject.id = snapId` quando não informado — correto (ADR 0004)
- `@Transactional` nos métodos de escrita, sem overhead em leituras — correto
- Testes de integração com H2 isolado por suite — correto
- Stub do gateway via `@TestConfiguration @Primary` — correto
- `RequestCorrelationFilter` com MDC limpo no `finally` — correto
- `LongAdder` / `AtomicLong` em `HttpObservabilityRegistry` — correto para escrita concorrente
- Escape de valores no filtro `drawtext` — correto contra command injection

---

## Sequência recomendada de correção

### Antes de qualquer deploy (bloqueantes)

1. **B3** — Confirmar mapeamento `@Lob → text` (verificar com Postgres real antes de tudo)
2. **B1** — Paginação nativa no banco (impacto mais amplo, mas pode ser feito por query)
3. **B2** — Corrigir `datediff` para sintaxe Postgres
4. **B4** — Garantir cleanup de temp no `finally` do gateway

### Durante o hardening (importantes)

5. **I5** — Mensagem genérica em 500 + log do stack trace
6. **I2** — Race condition em `resolveOrCreateVideo` (upsert otimista)
7. **I4** — Proteger `/internal/observability/*` por rede/token
8. **I7** — 201 Created no POST de snap
9. **I1** — Timing-safe para comparação de token
10. **I6** — Mapear `MissingServletRequestParameterException`
11. **I3** — Decidir e documentar comportamento de `resolveUsuario` concorrente

### Após validação em produção (melhorias)

12. **M1** — Remover `outputDir` do response público
13. **M2** — Remover `lockOwner`/`lockedAt` do response de negócio
14. **M6** — `workerInstanceId` dinâmico
15. **M3** — Truncar `requestId` no MDC
16. **M5** — Testes unitários para `SnapV2Service` e worker
17. **M4** — Validação de `sortBy`/`sortDir` no controller

---

## Decisões que podem virar ADR

Se durante a correção dos bloqueantes uma decisão estrutural for tomada, registrar ADR:

- `ADR 0006` — Paginação nativa no banco vs. in-memory (decisão de quando migrar)
- `ADR 0007` — Estratégia de upsert otimista para entidades compartilhadas (vídeo, usuário)

---

*Próxima ação: corrigir B1–B4 antes de conectar ao Postgres de produção ou avançar para
os slices 4–6 da Entrega 4.*
