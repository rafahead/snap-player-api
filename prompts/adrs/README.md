# ADRs (Architecture Decision Records)

## Governança

Ver `prompts/masters/master-adrs.md` para:
- critérios de quando criar um ADR
- template
- ciclo de vida (proposto → aceito → substituído)
- fluxo de criação

## Índice

| ADR | Título | Status |
|-----|--------|--------|
| [0001](0001-snap-entidade-principal.md) | Snap como entidade principal | Aceito |
| [0002](0002-fase-inicial-sincrona.md) | Fase inicial síncrona | Substituído por ADR-0012 |
| [0003](0003-single-domain-isolamento-por-assinatura.md) | Single domain com isolamento por assinatura | Aceito |
| [0004](0004-template-default-e-fallback-subject-id.md) | Template default e fallback subject.id | Aceito |
| [0005](0005-planejamento-em-planos-separados.md) | Planejamento em planos separados | Aceito |
| [0006](0006-paginacao-nativa-no-banco.md) | Paginação nativa no banco | Aceito |
| [0007](0007-mapeamento-json-column-text.md) | Mapeamento de colunas JSON como `text` | Aceito |
| [0008](0008-fila-db-skip-locked.md) | Fila de processamento em banco com SKIP LOCKED | Aceito |
| [0009](0009-heartbeat-worker-lock.md) | Heartbeat de locks do worker | Aceito |
| [0010](0010-storage-dual-backend.md) | Storage dual-backend: local/S3 | Aceito |
| [0011](0011-upsert-otimista-entidades-compartilhadas.md) | Upsert otimista para entidades compartilhadas | Aceito |
| [0012](0012-async-como-padrao.md) | Modo assíncrono como padrão | Aceito |
