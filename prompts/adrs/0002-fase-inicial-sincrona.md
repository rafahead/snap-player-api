# ADR 0002 - Fase Inicial com Processamento Síncrono

## Status

Substituído por [ADR 0012](0012-async-como-padrao.md)

## Contexto

O serviço será executado inicialmente em servidor restrito, com necessidade de entregar valor rápido e validar o fluxo de produto/API antes de investir em fila/worker assíncrono.

Já existe um MVP síncrono validado com:
- FFprobe + FFmpeg
- extração de frames
- `snapshot.mp4`
- `subject` genérico
- overlay opcional

## Decisão

Implementar a primeira versão da API v2 em modo **síncrono**.

Nesta fase:
- sem worker assíncrono
- sem fila em DB
- sem `SKIP LOCKED`
- sem S3 (storage local)

O contrato do `snap` deve ser desenhado para migrar ao assíncrono depois sem quebrar clientes.

## Consequências

### Positivas

- Reduz prazo de entrega inicial.
- Reaproveita grande parte do MVP existente.
- Simplifica depuração e validação de regras de negócio.

### Trade-offs / Custos

- Menor escalabilidade e throughput.
- Requisições longas no `POST /v2/snaps`.
- Migração posterior para assíncrono ainda será necessária.

## Relação com planos

- Coerente com baseline de `prompts/masters/master-roadmap.md`
- Aproveita pipeline de `prompts/masters/master-tecnico.md`
- Mantém domínio de `prompts/masters/master-produto-snap.md`
