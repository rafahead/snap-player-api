# Master ADRs — Governança de Decisões Arquiteturais

## Papel deste documento

Define as regras para criação, manutenção e uso de ADRs no projeto.
É a fonte de verdade sobre quando registrar uma decisão e como fazê-lo.

Referenciado por:
- `prompts/masters/master-tecnico.md`
- `prompts/masters/master-produto-snap.md`
- `prompts/masters/master-roadmap.md`
- `prompts/adrs/README.md`

---

## O que é um ADR neste projeto

ADR (Architecture Decision Record) é um registro curto de uma decisão
estrutural que:
- muda a direção do projeto
- impacta vários módulos ou entregas
- deve ser lembrada e consultável meses depois
- tem alternativas reais que foram descartadas conscientemente

ADRs **não** são:
- documentação de implementação (isso vai no master técnico)
- checklist de tarefa (isso vai no master-roadmap)
- registro de bug ou correção pontual

---

## Critérios para criar um ADR

Crie um ADR quando a decisão satisfizer **dois ou mais** dos critérios abaixo:

1. Há alternativa real que foi descartada (ex.: broker vs. DB queue)
2. A decisão impacta o schema, o contrato de API ou a infraestrutura
3. Um desenvolvedor novo precisaria entender o *porquê* para não reverter
4. A decisão tem trade-offs relevantes que devem ser explícitos

Exemplos que **merecem** ADR:
- escolha de tecnologia de fila (DB vs. broker vs. in-process)
- estratégia de paginação (in-memory vs. nativa no banco)
- modelo de multi-tenancy (single schema vs. schema por tenant)
- modo de operação padrão (sync vs. async)
- estratégia de storage (local vs. S3 compatível)
- padrão de upsert para entidades compartilhadas

Exemplos que **não merecem** ADR:
- comparação timing-safe de token (implementação de segurança conhecida)
- truncar requestId no MDC (detalhe operacional)
- retornar 201 vs. 200 (correção de contrato HTTP)
- mover um arquivo entre diretórios

---

## Template

```markdown
# ADR XXXX — Título da Decisão

## Status

Proposto | Aceito | Substituído por ADR-XXXX

## Contexto

Descreva o problema, restrição ou motivação.
Por que esta decisão foi necessária?

## Decisão

Descreva a decisão tomada de forma objetiva.
O que foi escolhido e por quê (em comparação às alternativas).

## Consequências

### Positivas

- ...

### Trade-offs / Custos

- ...

## Relação com planos

- `prompts/masters/master-tecnico.md`
- `prompts/masters/master-produto-snap.md`
- `prompts/masters/master-roadmap.md`
```

O template source está em `prompts/adrs/0000-template.md`.

---

## Ciclo de vida de um ADR

- **Proposto** — decisão em análise, ainda não executada no código
- **Aceito** — decisão tomada e executada
- **Substituído por ADR-XXXX** — decisão anterior superada por nova decisão

Regras:
- Não editar o conteúdo de um ADR aceito
- Se a decisão mudar, criar novo ADR marcando o anterior como substituído
- Número é sequencial e permanente — não reutilizar números

---

## Fluxo de criação

```
decisão estrutural identificada
        ↓
verificar critérios (seção acima)
        ↓
atualizar master relevante (técnico ou produto)
        ↓
criar ADR com próximo número disponível
        ↓
adicionar ao índice abaixo e ao prompts/adrs/README.md
        ↓
referenciar no master-roadmap.md se for gatilho de entrega
```

---

## Índice de ADRs

| ADR | Título | Status |
|-----|--------|--------|
| [0001](../adrs/0001-snap-entidade-principal.md) | Snap como entidade principal | Aceito |
| [0002](../adrs/0002-fase-inicial-sincrona.md) | Fase inicial síncrona | Substituído por ADR-0012 |
| [0003](../adrs/0003-single-domain-isolamento-por-assinatura.md) | Single domain com isolamento por assinatura | Aceito |
| [0004](../adrs/0004-template-default-e-fallback-subject-id.md) | Template default e fallback subject.id | Aceito |
| [0005](../adrs/0005-planejamento-em-planos-separados.md) | Planejamento em planos separados | Aceito |
| [0006](../adrs/0006-paginacao-nativa-no-banco.md) | Paginação nativa no banco | Aceito |
| [0007](../adrs/0007-mapeamento-json-column-text.md) | Mapeamento de colunas JSON como `text` | Aceito |
| [0008](../adrs/0008-fila-db-skip-locked.md) | Fila de processamento em banco com SKIP LOCKED | Aceito |
| [0009](../adrs/0009-heartbeat-worker-lock.md) | Heartbeat de locks do worker | Aceito |
| [0010](../adrs/0010-storage-dual-backend.md) | Storage dual-backend: local/S3 | Aceito |
| [0011](../adrs/0011-upsert-otimista-entidades-compartilhadas.md) | Upsert otimista para entidades compartilhadas | Aceito |
| [0012](../adrs/0012-async-como-padrao.md) | Modo assíncrono como padrão | Aceito |

---

*Snap Player API — governança de decisões arquiteturais.*
*Atualizado: fevereiro 2026.*
