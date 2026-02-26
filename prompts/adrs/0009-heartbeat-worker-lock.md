# ADR 0009 — Heartbeat de Locks do Worker (Renovação Periódica de `locked_at`)

## Status

Aceito

## Contexto

O worker de processamento (ADR 0008) usa `locked_at` para controlar o tempo
máximo de processamento de um job. Jobs com `status = 'RUNNING'` e
`locked_at < now() - lockTimeout` são considerados órfãos e recuperados
(recolocados em `PENDING`).

Problema: processamentos longos (FFmpeg em vídeos grandes) podem ultrapassar
`lockTimeout` mesmo em workers saudáveis, causando recovery prematuro e
reprocessamento desnecessário de jobs ainda ativos.

## Decisão

O worker renova periodicamente o `locked_at` de todos os jobs `RUNNING`
que ele próprio detém (`claimed_by = workerInstanceId`), via
`@Scheduled` com intervalo `workerHeartbeatIntervalMs`.

Constraint operacional obrigatória:

```
workerHeartbeatIntervalMs < workerLockTimeoutSeconds * 1000 / 3
```

Com os defaults (heartbeat=30s, lockTimeout=120s):
- um worker saudável renova a cada 30s
- um worker crashado perde o lock após 120s sem heartbeat
- janela de recovery: até 4 batidas de heartbeat antes do timeout

`workerInstanceId` usa `${HOSTNAME:local-worker}` para identificar
a instância corretamente em Docker (container ID) e em bare-metal dev.

## Consequências

### Positivas

- Jobs longos não são reprocessados indevidamente
- Diagnóstico: `locked_at` recente indica worker ativo
- `claimed_by` identifica a instância nos logs em ambientes multi-container

### Trade-offs / Custos

- Adiciona `UPDATE` periódico no banco para jobs RUNNING (custo baixo)
- Obriga monitorar constraint `heartbeat < lockTimeout/3` ao mudar parâmetros
- Worker precisa de `@EnableScheduling` no contexto Spring

## Relação com planos

- `prompts/masters/master-tecnico.md`
- `prompts/masters/master-roadmap.md` (Entrega 4, Slice 7)
- ADR relacionado: [ADR 0008](0008-fila-db-skip-locked.md)
