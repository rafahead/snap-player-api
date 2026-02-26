# ADR 0006 — Paginação Nativa no Banco (sem in-memory)

## Status

Aceito

## Contexto

Nas Entregas 1–3, a paginação foi implementada em memória: as queries de lista
(`listSnapsByVideo`, `listMineSnaps`, `listMineVideos`, `search`) carregam **todos**
os registros da assinatura do banco e aplicam `offset/limit` na JVM.

Essa abordagem foi aceita explicitamente no código com o comentário:
> "Entrega 3 prioritizes contract consistency first. Current data volumes are still small."

O produto agora se aproxima de produção real (Olho do Dono, 30+ clientes). Com volume
de snaps crescendo, cada request de lista pode saturar a heap — bloqueante para produção.

A revisão técnica pré-produção classificou isso como **B1 — BLOQUEANTE**.

O `PageMetaResponse` (campos `offset`, `limit`, `returned`, `hasMore`) já está projetado
para suportar paginação nativa sem mudança de contrato com o cliente.

## Decisão

Migrar todas as queries de lista para **paginação nativa no banco**, usando `Pageable`
do Spring Data ou queries com `LIMIT`/`OFFSET` explícitos.

Regras:
- nenhuma query de lista retorna `List<Entity>` sem limite
- o banco aplica `LIMIT` e `OFFSET` antes de retornar dados para a JVM
- `PageMetaResponse` é calculado a partir do resultado paginado (não de coleção total)
- `hasMore` é calculado por `returned == limit` (sem count total — evita query adicional)

Escopo:
- `SnapRepository` — todas as queries de lista
- `SnapV2Service.paginateAndSort()` — lógica em memória deve ser removida após migração
- Métodos afetados: `listSnapsByVideo`, `listMineSnaps`, `listMineVideos`, `searchByStringAttr`

## Consequências

### Positivas

- Escala para qualquer volume de dados sem impacto na heap
- Reduz latência em listas grandes
- Preparado para crescimento multi-cliente real

### Trade-offs / Custos

- Queries de lista precisam ser reescritas (Spring Data `Pageable` ou SQL nativo)
- Sort dinâmico (`sortBy`/`sortDir` livre) exige atenção extra para evitar SQL injection
  — usar enum ou whitelist validada antes de montar `ORDER BY`
- `count(*)` para `totalElements` foi descartado por custo — `hasMore` via `returned == limit`
  é a alternativa aceita (o contrato do `PageMetaResponse` já suporta isso)

### Decisão de sort seguro

Usar validação por whitelist no service antes de construir a cláusula `ORDER BY`.
Nunca interpolar `sortBy` diretamente em SQL — passar sempre como constante de string
validada ou usar `Sort` do Spring Data com campos mapeados.

## Relação com planos

- Revisão técnica: `prompts/estudos/claude/revisao-tecnica-pre-producao.md` (item B1)
- Execução: `prompts/entregas/entregas-api-snap-v2.md` (Entrega 4, Slice 4)
- Arquitetura técnica: `prompts/masters/master-tecnico.md`
