# Governança de Planos e Prompts

## Objetivo

Organizar os arquivos de planejamento/prompt como documentação viva (`docs-as-code`), separando:
- arquitetura técnica
- produto/domínio
- execução por entregas
- decisões arquiteturais (ADRs)

Este diretório é a base de planejamento do projeto e deve ser mantido coerente com o código.

## Conceitos de organização (camadas)

### 1. Fontes de verdade (conceitos)

- `master-tecnico*` -> arquitetura, componentes, limites técnicos
- `master-produto*` -> domínio, regras de negócio, fluxos de produto
- `master-monetizacao*` -> estratégia comercial, posicionamento, GTM e pricing
- `master-roadmap*` -> sequenciamento por entrega, escopo, prioridade, critérios de aceite
- `master-adrs*` -> governança de decisões arquiteturais (quando criar, template, índice)

### 2. Decisões estruturais (memória de longo prazo)

- `adrs/` -> decisões estáveis que impactam direção do projeto

### 4. Estudos e integração (apoio)

- `mvp*`, `*-integracao*`, estudos específicos -> validações e planos auxiliares

Regra prática:
- conceito vai para `master`
- sequência de implementação vai para `master-roadmap`
- decisão estrutural vai para `ADR`
- experimento/estudo vai para plano separado

## Mapa dos arquivos (estado atual)

### Planos mestres (fontes de verdade)

- `prompts/masters/master-tecnico.md`
  - **Master Técnico**
  - arquitetura de processamento, FFmpeg/FFprobe, storage, worker, DB, critérios técnicos

- `prompts/masters/master-produto-snap.md`
  - **Master Produto (Snap-first)**
  - domínio (`Snap`, `Video`, `Assinatura`, `Usuário`, `SubjectTemplate`), regras de produto, API conceitual

- `prompts/masters/master-monetizacao.md`
  - **Master Monetização**
  - estratégia comercial, posicionamento, oferta, GTM e pricing

- `prompts/masters/master-roadmap.md`
  - **Master Roadmap**
  - sequenciamento por entrega, escopo, prioridade, critérios de aceite

- `prompts/masters/master-adrs.md`
  - **Master ADRs**
  - governança de ADRs: critérios de criação, template, ciclo de vida, índice

### Templates e howto (reutilizáveis)

- `prompts/templates/howto-metodologia.md`
  - **Howto completo** — passo a passo para novo projeto e projeto existente
  - inclui stack padrão pré-preenchido (Spring Boot, PostgreSQL, Linode, Flutter web, Linux service)
  - tabela rápida de workflows, checklist de qualidade

- `prompts/templates/template-app.md`
  - prompt de bootstrap para **novos projetos** (sem código existente)
  - estrutura de `prompts/`, conceitos de organização, sequência de bootstrap

- `prompts/templates/template-projeto-existente.md`
  - prompt híbrido para **projetos existentes** (já tem código, sem `prompts/`)
  - o assistente lê o código, extrai estado real e cria os planos populados
  - surfaça decisões implícitas como ADRs candidatos, lista dívidas técnicas


### Planos de validação/experimentação

- `prompts/estudos/mvp-tecnico.md`
  - MVP técnico local/síncrono (frames + snapshot + overlay + `subject`)

- `prompts/estudos/chatgpt-monetizacao-snap-player.md`
  - prompt detalhado para conversa de monetização/GTM/pricing no ChatGPT

### Planos de integração com cliente

- `prompts/estudos/player-integracao.md`
  - estudo e plano de integração do app Flutter `snap-player` (cliente; pode haver path local legado `oddplayer`)

### Decisões arquiteturais

- `prompts/adrs/`
  - ADRs de decisões importantes e estáveis (curtas, versionáveis)

## Convenção de nomenclatura (prática)

### Como chamar os arquivos nas conversas

- `master-tecnico.md` = `Master Técnico`
- `master-produto-snap.md` = `Master Produto`
- `master-monetizacao.md` = `Master Monetização`
- `template-app.md` = `Template App` / `Prompt de Bootstrap`
- `master-roadmap.md` = `Plano de Entregas`
- `mvp-tecnico.md` = `Plano MVP`
- `player-integracao.md` = `Plano Player`

### Quando criar novo arquivo

Criar arquivo novo quando:
- o conteúdo tem ciclo de vida diferente
- a frequência de mudança é diferente
- mistura de responsabilidades começa a causar retrabalho/confusão

Exemplos:
- nova fase de execução -> novo plano de entregas
- decisão estrutural -> novo ADR
- experimento/estudo -> plano separado

## Regra de ouro (ordem de atualização)

Quando surgir uma mudança:

1. Atualize a **fonte de verdade** correta:
   - `master-tecnico.md` (técnico)
   - `master-produto-snap.md` (produto/domínio)
   - `master-monetizacao.md` (estratégia comercial)
2. Atualize o plano de execução (`master-roadmap.md`)
3. Se a decisão for estrutural/estável, registre um ADR em `prompts/adrs/`
4. Só então execute código (ou ajuste o plano de implementação)

## Matriz de responsabilidade (qual plano atualizar?)

### Atualize `prompts/masters/master-tecnico.md` quando mudar

- pipeline FFmpeg/FFprobe
- modo de processamento (sync/async)
- storage (local/S3)
- worker e concorrência
- schema técnico de processamento
- contratos técnicos base de extração

### Atualize `prompts/masters/master-produto-snap.md` quando mudar

- domínio (Snap/Video/Assinatura/Usuário/Templates)
- regras de visibilidade/permissão
- fluxos de produto
- API conceitual do produto
- compartilhamento público
- comportamento multi-assinatura

### Atualize `prompts/masters/master-roadmap.md` quando mudar

- escopo da próxima entrega
- prioridade
- cronograma/estimativa
- critérios de aceite operacionais
- checklists de execução

### Atualize `prompts/masters/master-monetizacao.md` quando mudar

- posicionamento e proposta de valor
- ICP/personas e segmentos-alvo
- estratégia comercial/GTM
- pricing/empacotamento/oferta
- hipóteses de monetização

### Atualize `prompts/estudos/mvp-tecnico.md` quando mudar

- MVP técnico de validação local
- limitações/escopo de MVP
- payload/retorno do MVP

### Atualize `prompts/estudos/player-integracao.md` quando mudar

- papel do app Flutter
- integração com a API
- refatoração planejada do cliente

## Workflow recomendado por mudança (curto)

### Nova regra de produto

1. `master-produto-snap.md`
2. `master-roadmap.md`
3. ADR (se estrutural)
4. código

> **Exemplo:** cliente pede que snaps privados expirem após 90 dias.
> → Adiciona regra de expiração em `master-produto-snap.md`.
> → Adiciona Slice N na entrega em andamento em `master-roadmap.md` (ou cria nova entrega se não houver entrega aberta).
> → Cria ADR se a decisão de TTL impactar schema e tiver alternativas descartadas (ex.: soft delete vs. purge físico).
> → Implementa.

### Mudança técnica de processamento

1. `master-tecnico.md`
2. `master-roadmap.md`
3. ADR (se estrutural)
4. código

> **Exemplo:** trocar storage de Linode para AWS S3 nativo.
> → Atualiza seção de storage em `master-tecnico.md`.
> → Adiciona slice em `master-roadmap.md`.
> → Cria ADR (há alternativa descartada, impacta infra).
> → Implementa.

### Mudança de prazo/escopo sem impacto conceitual

1. `master-roadmap.md`

> **Exemplo:** adiar Entrega 6 para depois do smoke test.
> → Só atualiza prioridade/ordem em `master-roadmap.md`.

### Bug ou correção pontual (sem impacto estrutural)

1. Corrige o código diretamente — sem ADR, sem atualizar master

> **Exemplo:** typo em mensagem de erro do `GlobalExceptionHandler`.
> → Corrige direto no código.

### Bug que precisa de plano (complexo, sem mudança estrutural)

Não cria ADR. Adiciona slice em `master-roadmap.md`:
- se houver entrega em andamento → adiciona slice nela
- se não houver entrega aberta → cria nova entrega (ex.: `Entrega 6 — Correções pós-produção`)
- formato: `Slice N — <descrição>` com itens `B1`, `B2`... (bloqueantes) ou `I1`, `I2`... (hardening)
- usar quando: afeta múltiplos arquivos, tem risco de regressão ou requer sequenciamento

> **Exemplo:** frame corrompido em vídeos acima de 2GB.
> → Não há entrega aberta → cria `Entrega 6 — Correções pós-produção` em `master-roadmap.md`.
> → Adiciona `Slice 1 — frame corrompido (>2GB)` com B1 (reproduzir), B2 (corrigir FfmpegService), B3 (teste regressão).
> → Implementa na ordem.

### Bug que revela decisão estrutural

1. `master-tecnico.md` ou `master-produto-snap.md` (atualiza a regra)
2. ADR (se satisfizer 2+ critérios)
3. `master-roadmap.md` (adiciona slice na entrega em andamento ou cria nova entrega)
4. código

> **Exemplo:** bug de concorrência no worker revela que o modelo de lock por instância é insuficiente para múltiplos workers.
> → Atualiza estratégia de lock em `master-tecnico.md`.
> → Cria ADR (alternativa real: lock por instância vs. lock distribuído; impacta schema e infra).
> → Adiciona slice em `master-roadmap.md`.
> → Implementa.

### Novo projeto / novo app (bootstrap)

1. `template-app.md` (gerar estrutura e rascunhos iniciais)
2. `master-*` (produto/técnico)
3. `master-roadmap.md`
4. ADRs iniciais (se houver decisões estruturais)

> **Exemplo:** novo módulo de inspeção industrial derivado do snap-player-api.
> → Usa `template-app.md` para gerar estrutura de `prompts/`.
> → Cria `master-tecnico.md` e `master-produto.md` do novo módulo.
> → Define primeiras entregas em `master-roadmap.md`.
> → Registra ADR de multi-tenancy se a decisão for diferente do snap-player-api.

## ADRs (como usar)

Use ADR para decisões que satisfaçam **2 ou mais** dos critérios:
- há alternativa real que foi descartada
- impacta schema, contrato de API ou infraestrutura
- um dev novo reverteria sem entender o porquê
- tem trade-offs relevantes que devem ser explícitos

> **Merece ADR:** escolha de fila em DB vs. broker; paginação nativa vs. in-memory; sync vs. async como padrão; storage local vs. S3.
> **Não merece ADR:** typo corrigido; retornar 201 vs. 200; truncar requestId; comparação timing-safe de token.

## Revisão e higiene dos prompts

### Frequência sugerida

- revisão rápida: a cada entrega
- revisão estrutural: a cada mudança de fase (ex.: síncrono -> assíncrono)

### Sinais de que precisa reorganizar

- mesmo tema repetido em 3 arquivos
- conflito de regra entre `master` e `master-v2`
- plano de entrega falando de regra de produto que não está no master
- prompts grandes demais sem índice/referência

## Estado consolidado (API / fase atual)

Situação resumida da implementação (com base no código e no plano):
- `Entrega 1`: concluída (API `v2` Snap-first síncrona)
- `Entrega 2`: concluída (share público + listas `mine`)
- `Entrega 3`: concluída (assinatura no request, token por feature flag, paginação/ordenação, observabilidade mínima)
- `Entrega 4`: concluída (slices 1-8: fila em DB, worker local, retry/backoff, stale recovery, cleanup, hardening, async padrão, heartbeat e Actuator)
- `Entrega 5`: concluída (storage S3 compatível + fallback local persistente + keys estáveis por `snapId`)

## Pendências consolidadas (código)

### Curto prazo (mais provável próxima execução)

- configurar variáveis reais do S3/Linode e validar upload em ambiente alvo
- validar smoke manual completo em modo assíncrono com Actuator (`/actuator/health`, `/actuator/metrics`)
- preparar docker-compose de produção com health check e limites de recursos

### Médio prazo (produção / pós-Entrega 5)

- endurecimento para ambiente distribuído:
  - tuning de claim/batch/timeout por ambiente
  - estratégia de idempotência/reprocessamento explícita
- Prometheus/OpenTelemetry (sobre Actuator) para telemetria externa mais completa
- persistência/infra alvo do master técnico:
  - PostgreSQL
  - storage S3 compatível
  - worker(s) fora do processo web

### Produto / integrações (fora do core técnico imediato)

- autenticação/autorização real por usuário/assinatura (hoje há apenas token por feature flag em rotas privadas)
- integração do player `snap-player` com contratos `v2`
- revisão de UX/contrato para snaps `PENDING`/`FAILED` no cliente

## Pendências consolidadas (planos/documentação)

- manter `prompts/masters/master-roadmap.md` como espelho fiel do progresso das Entregas 5-7 (produção)
- revisar `prompts/estudos/player-integracao.md` para refletir nomenclatura `snap-player` e integração com `v2` assíncrona
- quando houver decisão estrutural nova (ex.: progresso dedicado / worker externo), registrar ADR novo antes da implementação
- manter `http/*.http` atualizado a cada endpoint/parâmetro novo (regra já vigente)

## Avaliação da estrutura de diretório (`prompts/`)

Resposta curta: **a reorganização incremental foi aplicada** e a estrutura atual ficou melhor para crescer.

Pontos positivos (após reorganização):
- baixo atrito (arquivos importantes ficam fáceis de achar)
- convenções claras entre `master`, `entregas` e `adrs`
- `prompts/README.md` já funciona como guia de governança

Pontos que ainda exigem disciplina:
- evitar criar arquivos novos no topo de `prompts/` (manter o padrão por subpastas)
- manter `prompts/README.md` sincronizado ao criar novos estudos/planos
- revisar referências internas sempre que mover/renomear arquivos

## Estrutura atual (aplicada)

Estrutura em uso:
- `prompts/README.md` (índice/governança)
- `prompts/masters/`
  - `CONTEXT.md` (ponto de entrada — ler primeiro)
  - `master-tecnico.md`
  - `master-produto-snap.md`
  - `master-monetizacao.md`
  - `master-roadmap.md`
  - `master-adrs.md`
- `prompts/adrs/`
- `prompts/estudos/`
  - `mvp-tecnico.md`
  - `player-integracao.md`
- `prompts/templates/`
  - `template-app.md`

### Estratégia para próximas reorganizações (se necessário)

1. Fazer `move` em commit isolado
2. Atualizar referências em `README.md` + `prompts/README.md` + masters + ADRs
3. Rodar busca global por paths antigos
4. Registrar no plano quando a reorganização impactar workflow/documentação

## Padrões recomendados (referências)

### 1. ADR (Architecture Decision Records)

Recomendação principal:
- Michael Nygard, **Documenting Architecture Decisions**

### 2. Diátaxis (estrutura de documentação)

Útil para separar:
- tutorial
- how-to
- referência
- explicação

Aplicação aqui:
- `master*` = explicação + referência
- `entregas` = how-to de execução
- `mvp/player` = planos de experimento/integração

### 3. Docs-as-code

Tratar os prompts como parte do projeto:
- versionados
- revisáveis
- vinculados ao código
- atualizados junto com mudanças relevantes

## Checklist rápido antes de fechar uma decisão

- A regra está no `master` correto?
- O `master-roadmap.md` reflete essa regra?
- Precisa de ADR?
- O nome usado nos arquivos está consistente (`Snap`, `Video`, `Assinatura`, `subject`)?
- Há impacto no MVP ou no Player a registrar?
