# ADR 0001 - `Snap` como Entidade Principal

## Status

Aceito

## Contexto

O projeto começou com foco em extração de frames por vídeo/lote, mas o produto evoluiu para um fluxo de anotação colaborativa sobre vídeos, com:
- marcações pontuais ou em intervalo
- formulário com `subject`
- compartilhamento público de resultado
- consumo por player e por API

Nesse cenário, “processamento de vídeo” deixa de ser o centro do produto; o objeto de negócio passa a ser a marcação/anotação processada.

## Decisão

Adotar `Snap` como entidade principal do produto.

Consequências práticas:
- `video` torna-se entidade agregadora/importante
- `subject` é metadado do snap
- processamento (FFmpeg/FFprobe) é parte da execução do snap
- links públicos e consultas passam a orbitar `snapId`

## Consequências

### Positivas

- Modelo de produto alinhado com uso real (player + criação de snaps).
- Facilita compartilhamento público por snap.
- Permite histórico e buscas por `subject` sem perder contexto do vídeo.

### Trade-offs / Custos

- Requer modelagem de `video` + `snap` + `subject` (mais complexa que pipeline puro).
- Planos técnicos precisam ser reinterpretados sob a ótica Snap-first.

## Relação com planos

- Baseado em `prompts/masters/master-produto-snap.md`
- Executado via `prompts/masters/master-roadmap.md`
- Reaproveita pipeline técnico de `prompts/masters/master-tecnico.md`
