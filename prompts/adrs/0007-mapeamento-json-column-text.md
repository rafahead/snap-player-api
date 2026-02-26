# ADR 0007 — Mapeamento de Colunas JSON como `text` no PostgreSQL

## Status

Aceito

## Contexto

As entidades JPA do projeto (`SnapEntity`, `SnapProcessingJobEntity`, `VideoEntity`,
`AssinaturaEntity`) usam `@Lob` em campos `String` para armazenar JSON serializado
(`subjectJson`, `outputJson`, `probeJson`, `lastError`, etc.).

No H2 (banco de desenvolvimento), `@Lob` em `String` mapeia para `CLOB` — funciona.

No PostgreSQL real (banco de produção), o comportamento de `@Lob` em `String` com
Hibernate depende da versão do driver e do dialeto:
- pode mapear para `OID` (large object) — semântica diferente, não indexável, streaming
  separado, com implicações para backup e restore
- pode mapear para `TEXT` — comportamento esperado

Esse comportamento divergente foi identificado como **B3 — BLOQUEANTE** na revisão
técnica pré-produção: se mapear para `OID`, as colunas JSON quebram silenciosamente
(queries, persistência e leitura com comportamento inesperado em Postgres real).

## Decisão

Substituir `@Lob` por `@Column(columnDefinition = "text")` em **todas** as colunas
que armazenam JSON serializado como `String`.

Regra canônica:
- campos de JSON serializado em entidades: `@Column(columnDefinition = "text")`
- campos de texto curto (até 255): anotação padrão do Hibernate (sem `columnDefinition`)
- campos de texto longo não-JSON (ex: `lastError` com mensagem longa): `@Column(columnDefinition = "text")`
- nenhum campo `String` usa `@Lob` — a anotação é removida de todas as entidades

Entidades afetadas e campos:
- `SnapEntity` — `subjectJson`, `outputJson`, `probeJson`
- `SnapProcessingJobEntity` — `lastError`
- `VideoEntity` — `probeJson`
- `AssinaturaEntity` — verificar campos de configuração JSON se existirem

## Consequências

### Positivas

- Comportamento idêntico entre H2 (desenvolvimento) e PostgreSQL (produção)
- Colunas `text` são indexáveis, não requerem gestão de large objects
- Backup e restore funcionam normalmente sem transações de large object
- Limpeza do código: sem `@Lob` em campos que não são binários

### Trade-offs / Custos

- Exige alteração em todas as entidades afetadas (cirúrgico, mas sistemático)
- Migrations Flyway existentes não precisam de alteração (o schema DDL já usa `text`
  nas migrations V1/V3 — o problema era apenas no mapeamento JPA)
- Verificar com `\d snap` no psql após deploy que as colunas são realmente `text`

### Verificação obrigatória antes de produção

Após aplicar a mudança, conectar ao PostgreSQL real e confirmar:
```sql
SELECT column_name, data_type, udt_name
FROM information_schema.columns
WHERE table_name IN ('snap', 'snap_processing_job', 'video', 'assinatura')
  AND data_type IN ('text', 'oid', 'USER-DEFINED');
```
Nenhuma coluna JSON deve aparecer com `udt_name = 'oid'`.

## Relação com planos

- Revisão técnica: `prompts/estudos/claude/revisao-tecnica-pre-producao.md` (item B3)
- Execução: `prompts/entregas/entregas-api-snap-v2.md` (Entrega 4, Slice 4)
- Arquitetura técnica: `prompts/masters/master-tecnico.md`
