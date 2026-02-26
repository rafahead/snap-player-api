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

### 2. Plano de execução (tático)

- `entregas*` -> sequenciamento por entrega, escopo, prioridade, critérios de aceite

### 3. Decisões estruturais (memória de longo prazo)

- `adrs/` -> decisões estáveis que impactam direção do projeto

### 4. Estudos e integração (apoio)

- `mvp*`, `*-integracao*`, estudos específicos -> validações e planos auxiliares

Regra prática:
- conceito vai para `master`
- sequência de implementação vai para `entregas`
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

### Template padrão de inicialização (reutilizável)

- `prompts/templates/template-app.md`
  - prompt base para iniciar novos projetos/apps com esta metodologia
  - define estrutura de `prompts/`, conceitos de organização e sequência de bootstrap

### Planos de execução

- `prompts/entregas/entregas-api-snap-v2.md`
  - escopo por entrega
  - prioridades
  - cronograma
  - checklists operacionais

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
- `template-app.md` = `Template App` / `Prompt de Bootstrap`
- `entregas-api-snap-v2.md` = `Plano de Entregas`
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
2. Atualize o plano de execução (`entregas-api-snap-v2.md`)
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

### Atualize `prompts/entregas/entregas-api-snap-v2.md` quando mudar

- escopo da próxima entrega
- prioridade
- cronograma/estimativa
- critérios de aceite operacionais
- checklists de execução

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
2. `entregas-api-snap-v2.md`
3. ADR (se estrutural)
4. código

### Mudança técnica de processamento

1. `master-tecnico.md`
2. `entregas-api-snap-v2.md`
3. ADR (se estrutural)
4. código

### Mudança de prazo/escopo sem impacto conceitual

1. `entregas-api-snap-v2.md`

### Novo projeto / novo app (bootstrap)

1. `template-app.md` (gerar estrutura e rascunhos iniciais)
2. `master-*` (produto/técnico)
3. `entregas-*`
4. ADRs iniciais (se houver decisões estruturais)

## ADRs (como usar)

Use ADR para decisões que:
- mudam direção do projeto
- impactam vários módulos
- devem ser lembradas meses depois

Exemplos já decididos neste projeto:
- `Snap` como entidade principal
- fase inicial síncrona
- single domain com isolamento por assinatura
- template padrão por assinatura

Formato:
- curto (1 arquivo)
- com contexto, decisão e consequências
- sem excesso de detalhes operacionais

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

## Pendências consolidadas (código)

### Curto prazo (mais provável próxima execução)

- ativar storage S3 (Linode Object Storage) para produção
- validar smoke manual completo em modo assíncrono com Actuator (`/actuator/health`, `/actuator/metrics`)
- preparar docker-compose de produção com health check e limites de recursos

### Médio prazo (produção / pós-Entrega 4)

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

- manter `prompts/entregas/entregas-api-snap-v2.md` como espelho fiel do progresso das Entregas 5-7 (produção)
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
  - `master-tecnico.md`
  - `master-produto-snap.md`
- `prompts/entregas/`
  - `entregas-api-snap-v2.md`
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
- O `entregas-api-snap-v2.md` reflete essa regra?
- Precisa de ADR?
- O nome usado nos arquivos está consistente (`Snap`, `Video`, `Assinatura`, `subject`)?
- Há impacto no MVP ou no Player a registrar?
