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

Antes de iniciar, copiar os arquivos base para o projeto:
```bash
cp prompts/templates/base/AGENTS.md            ./
cp prompts/templates/base/CLAUDE.md            ./
cp prompts/templates/base/
cp prompts/templates/base/master-adrs.md       prompts/masters/
cp prompts/templates/base/adrs/                prompts/adrs/
```

Depois criar (populado com estado real do código):
1. `prompts/CONTEXT.md` — usar `base/CONTEXT.md` como base + estado real extraído do código
2. `masters/master-tecnico.md` — usar `base/master-tecnico.md` como base + preencher com stack/arquitetura real
3. `masters/master-produto.md` — usar `base/master-produto.md` como base + preencher com domínio real
4. `masters/master-roadmap.md` — usar `base/master-roadmap.md` como base + marcar entregas concluídas e próximas prioridades
5. `prompts/README.md` — usar `base/prompts-README.md` como base + adaptar ao projeto
6. `README.md` raiz — usar `base/README.md` como base + preencher com stack e instruções reais

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
| Projeto novo, sem código | `template-projeto-novo.md` |
| Projeto existente, sem `prompts/` | **este arquivo** |
| Projeto existente, com `prompts/` parcial | **este arquivo** (o assistente ignora o que já existe e completa o que falta) |
| Projeto existente, com `prompts/` completo mas desatualizado | `howto-metodologia.md` → seção "Checklist de qualidade" |
