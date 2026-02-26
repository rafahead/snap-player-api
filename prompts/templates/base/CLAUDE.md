# CLAUDE.md

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

## Inicialização de sessão

1. Carregar `prompts/CONTEXT.md`.
2. Na primeira resposta da sessão, iniciar com uma linha de status:
   `✓ {{SLUG}} · modo silencioso · trabalhando em: <conteúdo da seção "Trabalhando em" do CONTEXT.md>`
3. Processar normalmente o pedido do usuário.

## Padrões de código

- ProcessBuilder (nunca `bash -c`)
- SSRF bloqueado em URLs externas
- JavaDoc em classes/serviços/endpoints públicos
- `http/*.http` sempre atualizado com novos endpoints

## Comandos — suprimir output desnecessário

- `mvn test`: `-q` + `grep -E "Tests run:|BUILD|FAILURE|ERROR"`
- `git`: saídas curtas (`--short`, `--porcelain`)
- Demais: `-q`/`--quiet`/`--silent`; stderr irrelevante → `/dev/null`
