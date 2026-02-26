# ADR 0005 - Planejamento em Planos Separados (Técnico, Produto e Entregas)

## Status

Aceito

## Contexto

O projeto acumulou decisões de:
- arquitetura técnica de processamento
- produto/domínio (Snap-first)
- cronograma/escopo de entrega

Quando tudo fica no mesmo arquivo, o documento cresce rápido, perde foco e gera conflito entre regra estável e planejamento tático.

## Decisão

Manter planejamento em arquivos separados com papéis distintos:

- `prompts/masters/master-tecnico.md` = Master Técnico
- `prompts/masters/master-produto-snap.md` = Master Produto (Snap-first)
- `prompts/entregas/entregas-api-snap-v2.md` = Plano de Entregas
- `prompts/adrs/` = decisões estruturais (ADRs)

## Consequências

### Positivas

- Reduz conflito entre conceito e execução.
- Facilita atualização de escopo sem mexer em regras estáveis.
- Ajuda onboarding e revisão.

### Trade-offs / Custos

- Exige disciplina de atualização cruzada.
- Pode haver duplicidade se a governança não for seguida.

## Relação com planos

- Governança descrita em `prompts/README.md`
