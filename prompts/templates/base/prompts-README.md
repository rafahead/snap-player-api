# Governança de Planos e Prompts
> Copiar para `prompts/README.md` no projeto de destino.

## Objetivo

Organizar os arquivos de planejamento como documentação viva (`docs-as-code`), separando:
- arquitetura técnica
- produto/domínio
- execução por entregas
- decisões arquiteturais (ADRs)

---

## Mapa dos arquivos (estado atual)

### Planos mestres (fontes de verdade)

- `prompts/CONTEXT.md` — **ponto de entrada** — ler antes de qualquer sessão
- `prompts/masters/master-tecnico.md` — arquitetura, stack, processamento, infra
- `prompts/masters/master-produto.md` — domínio, regras de produto
- `prompts/masters/master-roadmap.md` — sequenciamento de entregas, slices, critérios de aceite
- `prompts/masters/master-adrs.md` — governança de ADRs: critérios, template, índice

### Decisões arquiteturais

- `prompts/adrs/` — ADRs individuais (decisões estáveis)

### Templates e howto

- `prompts/templates/howto-metodologia.md` — guia completo de uso da metodologia
- `prompts/templates/template-projeto-novo.md` — bootstrap para novos projetos
- `prompts/templates/template-projeto-existente.md` — adoção em projetos com código existente
- `prompts/templates/template-linha-de-corte.md` — adoção sem documentar o passado
- `prompts/templates/base/` — arquivos prontos para copiar

---

## Regra de ouro (ordem de atualização)

1. Atualizar a **fonte de verdade** correta (`master-tecnico.md` ou `master-produto-*.md`)
2. Atualizar `master-roadmap.md`
3. Se estrutural, registrar ADR em `prompts/adrs/`
4. Só então implementar código

---

## Matriz de responsabilidade

| Situação | Atualizar |
|---|---|
| Mudança de arquitetura / stack / processamento | `master-tecnico.md` |
| Mudança de domínio / regras de produto / API | `master-produto.md` |
| Mudança de escopo / prioridade / entrega | `master-roadmap.md` |
| Decisão estrutural com alternativa descartada | novo ADR |
| Mudança de estratégia comercial | `master-monetizacao.md` (se existir) |

---

## Workflow por tipo de mudança

### Bug pontual (óbvio, sem impacto estrutural)
→ Corrige direto no código.

### Bug complexo (múltiplos arquivos, risco de regressão)
→ Adiciona slice em `master-roadmap.md` (entrega em andamento ou nova entrega).

### Bug que revela decisão estrutural
→ `master-tecnico.md` → ADR → slice em `master-roadmap.md` → código.

> **Exemplo:** bug de concorrência revela que o lock por instância é insuficiente.
> → Atualiza estratégia de lock em `master-tecnico.md`.
> → Cria ADR (alternativa real descartada, impacta schema).
> → Adiciona slice. → Implementa.

### Nova feature
→ `master-produto.md` → `master-roadmap.md` → (ADR se estrutural) → código.

> **Exemplo:** snaps privados com expiração de 90 dias.
> → Adiciona regra em `master-produto.md`.
> → Adiciona slice. → ADR se TTL impactar schema com alternativas descartadas. → Implementa.

### Onde adicionar o slice

- Entrega em andamento → adiciona slice nela
- Sem entrega aberta → cria nova entrega:

```markdown
## Entrega N — Correções pós-produção

### Slice 1 — <descrição>
- B1: ...
```

---

## ADRs — quando criar

Criar quando satisfizer **2 ou mais** dos critérios:
1. Há alternativa real descartada
2. Impacta schema, contrato de API ou infraestrutura
3. Um dev novo reverteria sem entender o porquê
4. Tem trade-offs relevantes que devem ser explícitos

> **Merece ADR:** fila em DB vs. broker; paginação nativa vs. in-memory; sync vs. async; storage local vs. S3.
> **Não merece ADR:** typo corrigido; retornar 201 vs. 200; truncar requestId.

---

## Checklist rápido antes de fechar uma sessão

- A regra está no `master` correto?
- O `master-roadmap.md` reflete o estado atual?
- Precisa de ADR?
- Há referências quebradas nos planos?
- `http/*.http` está atualizado?
- `CONTEXT.md` reflete o estado real?

*Atualizado: {{MES_ANO}}.*
