# ADR 0008 — Fila de Processamento Assíncrono em Banco com FOR UPDATE SKIP LOCKED

## Status

Aceito

## Contexto

A Entrega 4 introduziu processamento assíncrono de snaps. A alternativa principal
era usar um message broker (RabbitMQ, Kafka ou serviço gerenciado como SQS/Pub-Sub).

Restrições do projeto nesta fase:
- infraestrutura single-node (Linode 2 GB)
- PostgreSQL já em uso como banco de dados principal
- operação pelo Olho do Dono sem SRE dedicado
- custo de infraestrutura adicional indesejável para validação inicial

O padrão "DB queue com `FOR UPDATE SKIP LOCKED`" é bem documentado para PostgreSQL
e oferece semântica de fila transacional sem infraestrutura extra.

## Decisão

Usar tabela `snap_processing_job` no PostgreSQL como fila de tarefas, com worker
fazendo polling e claim via:

```sql
SELECT ... FROM snap_processing_job
WHERE status = 'PENDING'
ORDER BY created_at
LIMIT :batch
FOR UPDATE SKIP LOCKED
```

O worker é um `@Scheduled` bean no mesmo processo da API (single-node).
Retry com backoff exponencial é gerenciado no próprio worker via campos
`attempt_count`, `next_retry_at` e `last_error` na tabela de fila.

## Consequências

### Positivas

- Sem infraestrutura adicional — só o PostgreSQL já existente
- Semântica transacional garantida (claim é atômico com a transaction)
- `SKIP LOCKED` evita contenção entre instâncias sem coordenação externa
- Diagnóstico direto via SQL na tabela `snap_processing_job`
- Retry e backoff gerenciados no banco — auditável e inspecionável

### Trade-offs / Custos

- Worker vive no mesmo processo da API — um crash derruba ambos
- Polling (latência mínima de `workerPollDelayMs`) vs. push (0 latência)
- Escalabilidade horizontal limitada: múltiplos workers precisam de
  coordenação explícita via `claimed_by` e `locked_at` (resolvido com heartbeat no ADR 0009)
- Não adequado para filas de altíssimo volume — aceitável para o volume do Olho do Dono

### Decisão de evolução

Se o volume crescer ou se for necessário separar o worker do processo web,
migrar para worker standalone (mesmo padrão de polling, processo separado).
Migrar para broker somente se polling introduzir latência perceptível em produção.

## Relação com planos

- `prompts/masters/master-tecnico.md`
- `prompts/masters/master-roadmap.md` (Entrega 4)
