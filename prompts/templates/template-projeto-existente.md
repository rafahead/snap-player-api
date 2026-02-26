# Template — Adoção da Metodologia em Projeto Existente

## Papel deste arquivo

Prompt padrão para aplicar a metodologia docs-as-code em um projeto que já tem código
mas ainda não tem a estrutura `prompts/` organizada.

O assistente deve **ler o código existente** e produzir os planos populados com o estado real,
não inventar ou supor.

> Ver passo a passo manual em `prompts/templates/howto-metodologia.md` → seção "Como adotar em um projeto existente".

---

## Prompt de adoção (copiar e usar)

> Preencha as seções marcadas com `(preencher)` antes de enviar.

```md
# Adoção da Metodologia docs-as-code em Projeto Existente

Quero aplicar a metodologia de organização de planos/prompts neste projeto que já tem código.
Sua tarefa é ler o código, extrair o estado real e criar a estrutura `prompts/` populada.

## Regras de trabalho

- Não inventar nem supor — só registrar o que está implementado ou claramente decidido no código.
- Se uma informação não estiver no código, marcar com `(a confirmar)`.
- Decisões implícitas no código devem ser surfaçadas como ADRs candidatos.
- Hipóteses e dívidas técnicas devem ser listadas explicitamente.
- Usar modo silencioso: só resultado final, sem updates intermediários.

## Stack padrão (confirmar ou ajustar)

- Backend: Java 17 + Spring Boot 3.x
- Banco: PostgreSQL + Flyway
- Storage: Linode Object Storage (S3-compatível, AWS SDK v2)
- Frontend: Flutter web
- Deploy: serviço Linux via systemd (sem Docker na fase inicial)
- Servidor: Linode

## Entradas do projeto (preencher)

### 1. Identidade
- Nome do projeto:
- Slug (para nomes de arquivo):
- Repositório / diretório raiz:

### 2. Estado atual (preencher o que souber — o resto será extraído do código)
- O que está funcionando hoje:
- O que está em andamento:
- O que está quebrado ou pendente:
- Há testes automatizados? (sim/não/parcial)
- Há deploy em produção? (sim/não — se sim, onde):

### 3. Contexto de negócio
- Problema que o sistema resolve:
- Usuário principal:
- Cliente atual (se houver):

### 4. Dívidas técnicas conhecidas (preencher o que souber)
- Item:
- Item:

### 5. Decisões que você lembra ter tomado mas não estão documentadas
- Decisão:
- Decisão:

## Tarefa do assistente

### Etapa 1 — Auditoria do código

Ler e mapear:
- estrutura de pacotes / módulos
- entidades principais (domínio)
- endpoints existentes
- migrations de banco (se houver)
- serviços e responsabilidades
- integrações externas
- configurações em application.yml / .env / .properties
- testes existentes

### Etapa 2 — Produzir os arquivos de planos

Criar em `prompts/`:

1. `masters/CONTEXT-CONFIG.md` — conteúdo fixo obrigatório:

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

2. `masters/CONTEXT.md` — seção obrigatória no topo + estado atual real extraído do código:

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
3. `masters/master-tecnico.md` — arquitetura e stack conforme o código
4. `masters/master-produto-<slug>.md` — domínio e regras conforme o código
5. `masters/master-roadmap.md` — entregas concluídas + próximas prioridades
6. `masters/master-adrs.md` — governança (copiar de snap-player-api, zerar índice)
7. `adrs/0000-template.md` e `adrs/README.md` — copiar de snap-player-api
8. `README.md` — governança adaptada ao projeto

### Etapa 3 — Surfaçar decisões implícitas como ADRs candidatos

Para cada decisão estrutural encontrada no código, propor ADR com:
- título
- contexto (por que esta decisão foi necessária)
- decisão (o que foi escolhido)
- alternativas que provavelmente foram descartadas
- status: `Aceito` (já implementado)

Exemplos de decisões implícitas a buscar:
- entidade principal do domínio
- estratégia de autenticação/autorização
- modelo de multi-tenancy
- estratégia de processamento (sync/async)
- estratégia de storage
- estratégia de paginação
- padrão de upsert para entidades compartilhadas

### Etapa 4 — Listar pendências e dívidas técnicas

Produzir lista de:
- itens marcados como `(a confirmar)` que precisam de resposta do dono do projeto
- dívidas técnicas identificadas no código (sem testes, hardcode, TODOs, etc.)
- próximas prioridades sugeridas para `master-roadmap.md`

## Saída esperada

Ao final, o assistente deve ter produzido:
- estrutura `prompts/` completa e populada com estado real
- lista de ADRs candidatos (para aprovação antes de criar os arquivos)
- lista de `(a confirmar)` para o dono do projeto responder
- lista de dívidas técnicas e próximas prioridades sugeridas
```

---

## Quando usar este template vs. o template de novo projeto

| Situação | Template |
|---|---|
| Projeto novo, sem código | `template-app.md` |
| Projeto existente, sem `prompts/` | **este arquivo** |
| Projeto existente, com `prompts/` parcial | **este arquivo** (o assistente ignora o que já existe e completa o que falta) |
| Projeto existente, com `prompts/` completo mas desatualizado | `howto-metodologia.md` → seção "Checklist de qualidade" |
