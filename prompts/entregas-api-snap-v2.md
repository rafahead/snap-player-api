# Plano de Entregas v2 (API Snap-First, sem Player)

## Papel deste arquivo (Plano de Entregas)

Planejamento tático de execução por entregas.

Fontes de verdade conceituais:
- `prompts/master-tecnico.md` (Master Técnico)
- `prompts/master-produto-snap.md` (Master Produto / Snap-first)
- `prompts/README.md` (governança dos planos)

## Objetivo deste arquivo

Este arquivo organiza a **execução incremental** do produto definido em:

- `prompts/master-tecnico.md` (base técnica de processamento)
- `prompts/master-produto-snap.md` (modelo de produto Snap-first / multi-assinatura)

Foco atual:
- **somente API**
- **processamento síncrono**
- **servidor restrito**
- **assinatura default única**
- **sem autenticação**
- **busca por subject básica (igualdade)**

## Papel deste plano vs planos master

- `master-tecnico.md` + `master-produto-snap.md` = **fontes de verdade** (conceitos, regras, arquitetura, domínio)
- `entregas-api-snap-v2.md` = **sequenciamento de implementação** (o que entra em cada entrega)

Regra de governança:
- se surgir regra nova de negócio/arquitetura, atualizar primeiro `master-tecnico.md` e/ou `master-produto-snap.md`
- depois ajustar este arquivo de entregas

## Baseline confirmado para a fase atual

### Simplificações aceitas

- 1 assinatura única já criada (`default`)
- 1 template único já criado (`default`, equivalente ao template do MVP)
- sem autenticação/login
- identidade operacional por `nickname` informado na API
- registrar também `email`
- processamento síncrono (sem worker assíncrono nesta fase)
- armazenamento local (sem S3)
- sem player (integração posterior)

### Regras que permanecem obrigatórias

- `Snap` é a entidade principal
- `startFrame` tem precedência sobre `startSeconds`
- `subject.id` pode cair em fallback para `snapId`
- `snapshotDurationSeconds` com fallback para `durationSeconds`
- `ffprobe` para compatibilidade + motivo de falha
- mesmo vídeo URL em empresas diferentes não deve misturar dados (já modelado para v2; nesta fase usar assinatura `default`)

## Estratégia de implementação

### Abordagem recomendada

Implementar uma **v1 síncrona persistente** reaproveitando o MVP:
- manter pipeline FFmpeg/FFprobe já validado
- adicionar persistência Snap-first
- expor endpoints `v2`

Isso reduz risco e prepara migração futura para processamento assíncrono sem trocar o contrato principal do `snap`.

## Entrega 1 (prioridade máxima) - API Snap-first síncrona

## Meta

Fechar uma primeira versão utilizável da API para:
- criar snap síncrono
- consultar snap
- listar snaps por vídeo
- persistir vídeo/snap/subject

## Prazo estimado

- **4 a 6 dias úteis**

## Escopo funcional

### 1. Criar snap (síncrono)

`POST /v2/snaps`

Requisitos:
- aceitar `videoId` ou `videoUrl`
- se vier `videoUrl`, criar/reutilizar `video` no contexto da assinatura `default`
- receber `nickname` e `email`
- aplicar template `default` se `subjectTemplateId` ausente
- processar síncrono com FFprobe + FFmpeg
- persistir `snap` + `subject` + resultado
- retornar JSON consolidado do snap

### 2. Consultar snap

`GET /v2/snaps/{snapId}`

Requisitos:
- retornar detalhe completo
- incluir status, `videoProbe`, `subject`, `snapshotVideo`, `frames`

### 3. Listar snaps por vídeo

`GET /v2/videos/{videoId}/snaps`

Requisitos:
- listar todos os snaps do vídeo (assinatura `default`)
- ordenação por `resolvedStartSeconds` e/ou `createdAt`
- filtro opcional por `nickname`

### 4. Buscar snaps por subject (básica)

`GET /v2/snaps/search?subjectId=...&attrKey=...&attrValue=...`

Escopo desta entrega:
- igualdade (string)
- igualdade simples para número via query textual (opcional)

Se necessário simplificar ainda mais:
- entregar apenas busca por `subjectId` nesta primeira etapa

## Escopo técnico (Entrega 1)

### Persistência mínima (PostgreSQL + Flyway)

Tabelas:
- `assinatura` (seed `default`)
- `subject_template` (seed `default`)
- `usuario` (mínimo: `nickname`, `email`)
- `video`
- `snap`
- `snap_subject_attr`

Observações:
- manter `assinatura_id` nas tabelas mesmo com uma única assinatura agora
- evita retrabalho na fase multi-assinatura real

### Reuso de código do MVP

Reaproveitar/adaptar:
- `FfmpegService`
- `VideoProbeService`
- regras de recorte/validação (`startFrame`, `snapshotDurationSeconds`, compatibilidade)
- DTOs base (adaptando para `v2` e persistência)

### Storage

- arquivos em diretório local (temporário/persistente local)
- persistir paths no `snap`

## Regras de negócio da Entrega 1

- `nickname` é obrigatório (identidade operacional temporária)
- `email` deve ser informado e persistido
- `subjectTemplateId` ausente => usar template `default`
- `subject.id` ausente => copiar `snapId`
- `startFrame` > `startSeconds`
- snap pode ser instantâneo ou intervalo
- mesmo `videoUrl` reutiliza `video` no escopo da assinatura `default`

## Critérios de aceite (Entrega 1)

- É possível criar `snap` síncrono com `videoUrl`.
- É possível criar `snap` síncrono com `videoId`.
- API persiste `video`, `snap`, `subject_json` e `snap_subject_attr`.
- API retorna `snapshot.mp4` + frames no snap processado.
- API retorna motivo claro quando vídeo é incompatível.
- `subjectTemplateId` ausente usa template `default`.
- `subject.id` ausente recebe `snapId`.
- `GET /v2/snaps/{snapId}` retorna o snap completo.
- `GET /v2/videos/{videoId}/snaps` retorna todos os snaps do vídeo (com filtro por `nickname` opcional).

## Entrega 2 - Compartilhamento + listas de usuário

## Meta

Fechar o ciclo de uso básico sem player:
- compartilhar snap
- consultar snaps/vídeos por usuário
- busca básica de `subject` mais estável

## Prazo estimado adicional

- **3 a 4 dias úteis** (após Entrega 1)

## Escopo

### 1. Compartilhamento público

- `POST /v2/snaps/{snapId}/share`
- `GET /public/snaps/{token}`

Retorno público:
- clip (`snapshot.mp4`)
- frames
- metadados públicos do snap

### 2. Listas “mine”

- `GET /v2/snaps/mine?nickname=...`
- `GET /v2/videos/mine?nickname=...`

### 3. Busca básica por `subject`

- buscar por `subjectId`
- buscar por par `attrKey + attrValue` (igualdade)

### 4. Hardening mínimo

- validações
- tratamento de erro consistente
- testes de integração principais

## Critérios de aceite (Entrega 2)

- Snap pode gerar link público.
- Link público exibe/retorna recorte e imagens.
- Listas por `nickname` funcionam.
- Busca básica por `subject` retorna snaps corretos.

## Entrega 3 - Base para evolução (sem mudar contrato)

## Meta

Preparar terreno para:
- multi-assinatura operacional completa
- token de API por assinatura
- player integrado

## Prazo estimado adicional

- **2 a 5 dias úteis** (conforme profundidade)

## Escopo sugerido

- formalizar contexto de assinatura no request (mesmo com `default` em uso)
- preparar autenticação por token (desligada/feature flag ou stub)
- refinar schema/índices para volume maior
- padronizar paginação e ordenação
- observabilidade básica (logs estruturados / métricas mínimas)

## Entrega 4 (posterior) - Migração para assíncrono

Fora do escopo imediato deste plano, mas já prevista:
- fila em DB
- worker com `SKIP LOCKED`
- estados de processamento
- retorno de `POST` assíncrono por `snapId`/job

Importante:
- preservar contrato principal do `snap` para evitar retrabalho no player/API clientes

## Cronograma resumido (API somente)

Começando hoje:

- **Entrega 1**: 4 a 6 dias úteis
- **Entrega 2**: +3 a 4 dias úteis
- **Entrega 3**: +2 a 5 dias úteis

Total para v1 síncrona muito boa (sem player, sem auth completa):
- **9 a 15 dias úteis**

## Checklists de execução

## Checklist Entrega 1

- DDL/Flyway das tabelas mínimas
- seeds (`assinatura default`, `template default`)
- endpoint `POST /v2/snaps`
- endpoint `GET /v2/snaps/{id}`
- endpoint `GET /v2/videos/{videoId}/snaps`
- busca básica por subject (ou `subjectId` mínimo)
- testes de serviço e controller
- smoke test com vídeo real

## Checklist Entrega 2

- share público (`POST` + `GET`)
- listagens “mine”
- busca `subject` refinada
- testes de integração dos endpoints públicos

## Checklist Entrega 3

- abstração de contexto de assinatura
- preparação para token
- paginação/ordenação padronizadas
- observabilidade mínima

## Política de atualização dos planos

### Quando atualizar `master-tecnico.md`

Atualizar quando mudar:
- arquitetura de processamento (FFmpeg/worker/storage)
- regras técnicas globais
- contrato técnico base de processamento

### Quando atualizar `master-produto-snap.md`

Atualizar quando mudar:
- regras de produto
- domínio (`Snap`, `Video`, `Assinatura`, `Usuário`, `Template`)
- visibilidade/permissão
- fluxos do player/API

### Quando atualizar `entregas-api-snap-v2.md`

Atualizar quando mudar:
- escopo de uma entrega
- prioridade
- sequenciamento
- estimativa
- critério de aceite operacional
