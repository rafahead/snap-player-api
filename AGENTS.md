# AGENTS.md

## Preferência de saída (economia de tokens)

Quando o usuário pedir `modo silencioso estrito`, usar este formato:
- sem updates intermediários (salvo bloqueio/erro crítico)
- somente resposta final
- 1 linha com o resultado final
- sem logs, sem comandos, sem passo a passo
- sem diff/alterações de código

Gatilhos equivalentes:
- `modo silencioso estrito`
- `só resumo final`
- `somente resultado final`

Se o usuário pedir detalhe depois, responder normalmente a partir desse ponto.
