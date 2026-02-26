# ADR 0012 — Modo Assíncrono como Padrão (supersede ADR 0002)

## Status

Aceito — supersede [ADR 0002](0002-fase-inicial-sincrona.md)

## Contexto

O ADR 0002 definiu a fase inicial da API como síncrona, explicitamente temporária:
> "Aceita que a fase inicial será síncrona enquanto o worker não estiver pronto."

A Entrega 4 completou a infraestrutura assíncrona:
- tabela `snap_processing_job` com fila DB (ADR 0008)
- worker com retry, backoff, stale-recovery e heartbeat (ADR 0009)
- `POST /v2/snaps` retorna 202 Accepted + `job.status` para polling
- Actuator expondo `/actuator/health` e `/actuator/metrics`

Com a infraestrutura pronta e estável (31+ testes passando), manter sync como
padrão seria contra o design pretendido do produto.

## Decisão

`app.snap.asyncCreateEnabled=true` como valor padrão em `application.yml`.

- `POST /v2/snaps` retorna `202 Accepted` + corpo com `job.status = PENDING`
- O cliente usa `GET /v2/snaps/{id}` para polling de estado/resultado
- Modo síncrono permanece disponível via `asyncCreateEnabled=false`
  (usado em testes de integração via `@TestPropertySource`)

## Consequências

### Positivas

- Comportamento de produção real desde o primeiro deploy
- Operadores não bloqueiam durante FFmpeg (que pode levar vários segundos)
- Worker isola falhas de processamento da resposta HTTP

### Trade-offs / Custos

- Clientes precisam implementar polling de estado
- Testes de integração que validam o resultado síncrono precisam de
  `@TestPropertySource(properties = "app.snap.asyncCreateEnabled=false")`
- Smoke tests manuais precisam aguardar processamento (ou consultar job metrics)

### Compatibilidade retroativa

O modo síncrono (Entregas 1–3) continua funcional via feature flag.
Nenhuma mudança de contrato — apenas o status HTTP e o fluxo de polling.

## Relação com planos

- `prompts/masters/master-tecnico.md`
- `prompts/masters/master-produto-snap.md`
- `prompts/masters/master-roadmap.md` (Entrega 4, Slice 6)
- Supersede: [ADR 0002](0002-fase-inicial-sincrona.md)
- Depende de: [ADR 0008](0008-fila-db-skip-locked.md), [ADR 0009](0009-heartbeat-worker-lock.md)
