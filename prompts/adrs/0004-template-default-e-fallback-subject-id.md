# ADR 0004 - Template Padrão por Assinatura e Fallback de `subject.id`

## Status

Aceito

## Contexto

A API será consumida por player e por integrações externas. Para reduzir atrito:
- o cliente nem sempre informará `subjectTemplateId`
- o formulário/template de `subject` pode variar por assinatura
- o `subject.id` pode não ser informado em alguns fluxos manuais

Também foi definido que `Snap` tem identidade própria (`snapId`), independente do template.

## Decisão

1. Cada assinatura deve possuir **um template padrão**.
2. Se `subjectTemplateId` não for informado, a API usa o template padrão da assinatura.
3. Se `subject.id` não for informado no snap, a API copia `snapId` para `subject.id`.

## Consequências

### Positivas

- API mais simples de consumir.
- Reduz falhas por payload incompleto.
- Mantém rastreabilidade (`snapId`) mesmo sem `subject.id` explícito.

### Trade-offs / Custos

- Requer regra/constraint para garantir um único template padrão por assinatura.
- Fallback implícito deve ser bem documentado para evitar surpresa em clientes.

## Relação com planos

- Regras de produto em `prompts/master-produto-snap.md`
- Execução da primeira fase em `prompts/entregas-api-snap-v2.md`
