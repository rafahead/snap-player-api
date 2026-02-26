# Template — Linha de Corte (projeto existente, sem auditoria)

## Papel deste arquivo

Para projetos existentes onde **não vale a pena documentar o passado**.
O código anterior é reconhecido mas não catalogado.
A metodologia passa a valer a partir do momento em que este template é aplicado.

Diferença dos outros templates:

| Template | Quando usar |
|---|---|
| `template-app.md` | Projeto novo, sem código |
| `template-projeto-existente.md` | Projeto existente — quero documentar o que já existe |
| **este arquivo** | Projeto existente — quero ignorar o passado e aplicar a metodologia daqui em diante |

---

## Prompt de linha de corte (copiar e usar)

```md
# Linha de Corte — Adoção da Metodologia em Projeto Existente

Este projeto já tem código. Não quero documentar o que foi feito antes.
Quero apenas criar a estrutura `prompts/` mínima para aplicar a metodologia daqui em diante.

## Regras de trabalho

- Não auditar nem catalogar o código existente.
- Registrar o estado atual de forma mínima (1-2 linhas por item).
- Foco total nas próximas prioridades.
- Usar modo silencioso: só resultado final, sem updates intermediários.

## Stack padrão (confirmar ou ajustar)

- Backend: Java 17 + Spring Boot 3.x
- Banco: PostgreSQL + Flyway
- Storage: Linode Object Storage (S3-compatível, AWS SDK v2)
- Frontend: Flutter web
- Deploy: serviço Linux via systemd (sem Docker na fase inicial)
- Servidor: Linode

## Entradas (preencher)

### 1. Identidade
- Nome do projeto:
- Slug:

### 2. Estado atual (resumo livre — não precisa ser exato)
- O que o sistema faz hoje em 1-2 linhas:
- Está em produção? (sim/não):
- Há algo quebrado ou crítico agora?:

### 3. Próximas prioridades (o que importa daqui em diante)
- Prioridade 1:
- Prioridade 2:
- Prioridade 3:

### 4. Decisões que preciso registrar (opcional)
Se houver alguma decisão recente ou futura que vale documentar como ADR:
- Decisão:

## Conteúdo fixo obrigatório

### CONTEXT-CONFIG.md (copiar exatamente — não alterar)

```
Utilize essas configurações de contexto:
- gatilho preferencial para economia de tokens: `modo silencioso`
- `modo silencioso` = todos os itens abaixo (prioridade máxima de formato)
- sem updates intermediários (incluindo andamento/progresso), salvo bloqueio crítico
- só resposta final em 1 linha (esta regra tem prioridade sobre qualquer outra de formatação)
- só o resultado, sem explicação
- sem bullets/listas (exceto se eu pedir explicitamente)
- não mostre alterações/exclusões/adições de código, texto ou arquivos
- não mostre comando nem passo a passo
- não mostre output "explored"
- não mostre logs, stacktrace, stdout/stderr, output de testes ou output de ferramentas
- não repita trechos de arquivos lidos; use apenas o resumo final
- se precisar, pergunte antes de detalhar
- quando terminar um tópico, só apresente o resultado final da entrega
- em caso de erro, responda apenas o bloqueio em 1 linha (sem logs)
- você tem autorização para rodar comandos
- você tem acesso ao código-fonte do projeto
- você tem acesso a arquivos de configuração e documentação
- você tem acesso a arquivos de planejamento e estratégia
- você tem acesso ao diretório raiz do projeto, incluindo subdiretórios
```

### Seção obrigatória no topo do CONTEXT.md

```markdown
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
```

## Saída esperada do assistente

Criar apenas:

1. `prompts/masters/CONTEXT-CONFIG.md` — conteúdo fixo acima
2. `prompts/masters/CONTEXT.md` — seção obrigatória no topo + resumo mínimo do estado atual + próximas prioridades
3. `prompts/masters/master-tecnico.md` — stack e arquitetura em tópicos curtos (sem detalhar implementação existente)
4. `prompts/masters/master-produto-<slug>.md` — objetivo e entidades principais em tópicos curtos
5. `prompts/masters/master-roadmap.md` — só as próximas entregas (sem registrar histórico)
6. `prompts/masters/master-adrs.md` — governança padrão + ADRs informados nas entradas (se houver)
7. `prompts/adrs/0000-template.md` e `prompts/adrs/README.md`
8. `prompts/README.md` — governança mínima

Não criar: estudos, análise do código existente, ADRs retroativos não solicitados.
```

---

## Nota sobre o código legado

O código anterior continua existindo e funcionando.
A metodologia não exige documentar o passado para funcionar.
À medida que partes do código legado forem tocadas em novas entregas,
documentar naturalmente no master relevante ou em ADR se for decisão estrutural.
