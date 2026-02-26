# Master ADRs — Governança de Decisões Arquiteturais

## Papel deste documento

Define as regras para criação, manutenção e uso de ADRs no projeto.
É a fonte de verdade sobre quando registrar uma decisão e como fazê-lo.

Referenciado por:
- `prompts/masters/master-tecnico.md`
- `prompts/masters/master-produto.md`
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
- `prompts/masters/master-produto.md`
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

---

*Atualizado: {{DATA}}.*
