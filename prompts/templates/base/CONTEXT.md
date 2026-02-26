# CONTEXT.md — {{NOME_PROJETO}}
> Ponto de entrada para toda sessão. Leia este arquivo primeiro.

## Formato de resposta (PRIORIDADE MÁXIMA — aplicar sempre)

Gatilho ativo: `modo silencioso`

Regras obrigatórias:
- Sem updates intermediários nem mensagens de progresso (só em bloqueio crítico)
- Só resultado final, em 1 linha — esta regra tem prioridade sobre qualquer outra de formatação
- Sem explicações, sem bullets/listas (salvo pedido explícito)
- Não exibir alterações/exclusões/adições de código, texto ou arquivos
- Não exibir comandos nem passos executados
- Não exibir logs, stacktrace, stdout/stderr, output de testes ou de ferramentas
- Não repetir trechos de arquivos lidos; usar apenas o resumo final
- Em caso de erro, responder apenas o bloqueio em 1 linha (sem logs)
- Se precisar de informação, perguntar antes de detalhar

Autorizações permanentes:
- Rodar comandos
- Acesso completo ao código-fonte, configurações, documentação e planejamento do projeto

---

## Projeto

{{DESCRICAO_CURTA}}
Cliente em produção: **{{CLIENTE}}**
Repositórios: `{{REPO_BACKEND}}` (este) + `{{REPO_FRONTEND}}` ({{FRONTEND_STATUS}}).

**Stack:** {{STACK_RESUMIDO}}

---

## Trabalhando em

{{PRIORIDADE_ATUAL}}

---

## Estado das entregas ({{MES_ANO}})

- Entrega 1 — EM ANDAMENTO

---

## Próximas prioridades

1.
2.

---

## Fora do escopo (fase futura)

---

## Arquivos de referência

**Config/regras (carregados automaticamente):**

| Arquivo | Papel |
|---|---|
| AGENTS.md | Regras de formato e padrões — Codex CLI |
| CLAUDE.md | Regras de formato e padrões — Claude Code CLI |
| README.md | Requisitos, build, run, endpoints principais |

**Planejamento:**

| Arquivo | Papel |
|---|---|
| prompts/masters/master-tecnico.md | Arquitetura, stack, processamento, infra |
| prompts/masters/master-produto.md | Domínio, regras de produto |
| prompts/masters/master-roadmap.md | Sequenciamento tático de entregas |
| prompts/masters/master-adrs.md | Governança de decisões arquiteturais |
| prompts/adrs/ | ADRs individuais |

**Código quente (preencher com os arquivos mais tocados no projeto):**

| Arquivo | Papel |
|---|---|
| {{ARQUIVO_PRINCIPAL}} | {{PAPEL}} |

**Comandos:**

- Test: `{{COMANDO_TESTES}}`
- Run: `{{COMANDO_EXECUCAO}}`

---

## Governança dos planos

- Nova regra técnica/arquitetura → `master-tecnico.md` ou `master-produto.md`
- Mudança de escopo/prioridade → `master-roadmap.md`
- Decisão estrutural → novo ADR em `prompts/adrs/`
- `CONTEXT.md` reflete sempre o estado atual real

*Atualizado: {{MES_ANO}}.*
