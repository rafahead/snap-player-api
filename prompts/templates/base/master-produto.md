# Master de Produto — {{NOME_PROJETO}}

## Papel deste arquivo

Fonte de verdade para domínio e regras de produto.

Use junto com:
- `CONTEXT.md` (estado atual e próximas prioridades)
- `prompts/masters/master-tecnico.md` (arquitetura técnica)
- `prompts/masters/master-roadmap.md` (sequenciamento)

---

## Objetivo do Produto

(a preencher — problema que resolve, usuário principal, resultado esperado)

---

## Entidades principais

| Entidade | Papel |
|---|---|
| (a definir) | (a definir) |

---

## Regras de negócio

- (a definir)

---

## Fluxos principais

### Fluxo 1 — (nome)

1. ...
2. ...

---

## API conceitual

### POST /v1/{{recurso}}

Entrada:
```json
{}
```

Saída `201 Created`:
```json
{}
```

---

## Permissões e visibilidade

- (a definir)

---

## Multi-tenant (se aplicável)

- Isolamento lógico por `{{CAMPO_TENANT}}` em todas as queries e índices

*Atualizado: {{MES_ANO}}.*
