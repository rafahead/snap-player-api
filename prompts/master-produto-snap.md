# Master Plan v2 (Produto de Snaps Colaborativos)

## Papel deste arquivo (Master Produto / Snap-first)

Fonte de verdade para domínio e regras de produto (Snap, Video, Assinatura, Usuário, Templates).

Use junto com:
- `prompts/README.md` (governança dos planos)
- `prompts/master-tecnico.md` (Master Técnico)
- `prompts/entregas-api-snap-v2.md` (Plano de Entregas)

## 1. Objetivo

Evoluir o serviço atual de extração de frames/snapshot para um produto de **snaps colaborativos em vídeo**, acessível por:

- **Player (Flutter `snap-player`)**
- **API** (consumo programático)

Um **snap** passa a ser a entidade principal do sistema. O usuário informa a URL do vídeo, navega no player, cria snaps (instantâneos ou em intervalo), preenche um formulário baseado em um template de `subject`, e o sistema processa/armazena os recortes, frames e metadados.

O produto terá aspecto social/controlado:
- vários usuários podem criar snaps para a **mesma URL de vídeo**
- usuário vê snaps no contexto da assinatura ativa (especialmente por vídeo) e edita **apenas os próprios**
- snap pode gerar **link público compartilhável** (recorte + imagens)

## 2. Mudança de foco em relação ao `master-tecnico.md`

O `master-tecnico.md` continua válido como base técnica de:
- processamento assíncrono
- FFmpeg/FFprobe
- storage S3 compatível
- PostgreSQL + workers
- `subject` genérico tipado

O `master-produto-snap.md` redefine o **modelo de produto**:
- `Snap` como entidade principal
- `Video` como entidade agregadora/importante (URL com relevância própria)
- `Assinatura/Empresa` como contexto de trabalho
- `Usuário` com `nickname` + `email`
- `Templates de subject` por assinatura
- consumo via player e via API

## 3. Conceitos de domínio (v2)

### 3.1 `Assinatura` (empresa/tenant lógico)

Representa a empresa/cliente que contratou o serviço.

Observações:
- haverá **um único serviço em um único domínio** (sem separar por subdomínio)
- isolamento será lógico por `assinaturaId`
- cada assinatura possui:
  - `codigoAssinatura` / `codigoEmpresa`
  - `token` para consultas via API
  - templates de `subject`

Primeira empresa prevista:
- **Olho do Dono** (pesagem de animais por câmera 3D)

### 3.2 `Usuário`

Cadastro inicial simplificado (sem tratar autenticação completa agora):
- `nickname`
- `email`

Regras:
- um mesmo `email` pode participar de **várias assinaturas**
- ao entrar no app, se tiver mais de uma assinatura, deve escolher em qual irá trabalhar
- no cadastro, usuário informa `codigoEmpresa` (ou código da assinatura)
- se não houver código informado, entende-se que é o **primeiro assinante** (bootstrap inicial)

### 3.3 `Video`

Entidade importante no produto (não só insumo transitório).

Representa um vídeo analisado a partir de uma URL. Um vídeo pode ter múltiplos snaps.

Campos principais (conceituais):
- `videoId` (interno)
- `assinaturaId`
- `originalUrl`
- `canonicalUrl` (normalizada, se aplicável)
- `videoProbe` (codec, duração, fps, dimensões, formato)
- `createdByUserId`
- `createdAt`

Observação:
- a URL ganha relevância de listagem/consulta
- vários usuários podem criar snaps sobre a mesma URL
- a mesma URL pode existir em **assinaturas diferentes**, mas os dados devem permanecer isolados por `assinaturaId`

### 3.4 `Snap` (entidade principal)

Entidade central do sistema.

Representa uma marcação/processamento sobre um vídeo, podendo ser:
- **instantâneo** (marcação pontual)
- **intervalo** (clip + frames de um trecho)

Cada snap possui:
- `snapId` próprio (independente de qualquer campo do template)
- vínculo com `videoId`
- vínculo com usuário criador
- recorte temporal/frame
- `subject` (com template aplicado)
- resultados processados (clip + frames)
- status
- link público opcional de compartilhamento

### 3.5 `SubjectTemplate`

Template de formulário para gerar/preencher `subject`.

Regras:
- um assinante pode ter **vários templates**
- cada assinante deve ter **um template padrão**
- inicialmente começar com o template equivalente ao usado no MVP
- formulário do app deve seguir o template selecionado
- `subject` final continua no formato genérico (`id` + `attributes` tipados)
- incluir campo **`observacoes`** no template

## 4. Regras de negócio registradas (novos conceitos)

### 4.1 Visibilidade e edição

- Usuário trabalha no contexto de **uma assinatura ativa**
- Dentro de um vídeo da assinatura ativa, o usuário pode ver **todos os snaps daquele vídeo** (de todos os usuários da assinatura)
- Usuário edita **apenas os próprios snaps**
- Snaps de outros usuários podem existir sobre a mesma URL (e ficam visíveis no contexto da assinatura ativa)
- Snap pode ser compartilhado publicamente por link (somente visualização do conteúdo compartilhado)

### 4.2 Lista inicial do app

A lista inicial pode ser:
- últimos vídeos analisados
- ou últimos snaps

Ambas opções são aceitáveis na v1.

Observação:
- pode começar com filtro `mine` e depois adicionar visão da assinatura (`company`) sem mudar o modelo

### 4.3 Consumo por player e por API

O serviço deve oferecer:
- endpoints voltados ao player (UX de criação/listagem)
- endpoints de API para integração externa (token por assinatura)

### 4.4 Tipos de snap

Snap pode ser:
- **instantâneo**
  - marca um ponto do vídeo
  - pode gerar snapshot curto e/ou frames próximos (configurável)
- **intervalo**
  - usa duração explícita para frames e clip

### 4.5 Identidade do snap vs `subject.id`

- `snapId` é sempre gerado pelo sistema (ou identificado independentemente do template)
- `subject.id` pertence ao domínio do cliente (animal, item, objeto, etc.)
- se `subject.id` não for informado no formulário, **copiar `snapId` para `subject.id`** (assumido a partir da observação do usuário)

## 5. Experiência do Player (visão de produto)

## 5.1 Fluxo principal

1. Usuário entra (nickname/email; escolhe assinatura se necessário)
2. Vê lista inicial (últimos vídeos ou últimos snaps no contexto da assinatura ativa; com filtro opcional `mine`)
3. Abre vídeo (ou informa URL de um novo vídeo)
4. Player entra em ação
5. Clica em botão de `record/snap`
6. Abre formulário com template de `subject`
7. Preenche valores e salva
8. Snap entra na listagem sincronizada com o vídeo principal

## 5.2 Lista sincronizada com player

Para um vídeo aberto:
- exibir lista de snaps associados ao vídeo (todos os snaps do vídeo na assinatura ativa)
- sincronizar com posição do vídeo
- clicar em um snap reposiciona o player
- permitir filtro visual `meus snaps` x `todos`
- (futuro) marcadores na timeline

## 5.3 Compartilhamento de snap (público)

Cada snap poderá gerar link público para visualização contendo:
- vídeo recortado (`snapshot.mp4` / clip)
- imagens (frames)
- metadados essenciais do snap
- dados públicos do `subject` (conforme política futura)

## 6. Modelo de dados (conceitual / PostgreSQL)

## 6.1 Tabelas principais

### `assinatura`

- `id` (PK)
- `codigo` (único)
- `nome`
- `api_token` (único)
- `status`
- `created_at`

### `usuario`

- `id` (PK)
- `nickname`
- `email`
- `status`
- `created_at`

### `usuario_assinatura`

Relação N:N (um email/usuário pode operar em múltiplas assinaturas)

- `id` (PK)
- `usuario_id` (FK)
- `assinatura_id` (FK)
- `papel` (ex.: `USER`, `ADMIN`)
- `created_at`

Índice/constraint:
- `UNIQUE (usuario_id, assinatura_id)`

### `video`

- `id` (PK / `videoId`)
- `assinatura_id` (FK)
- `original_url`
- `canonical_url`
- `url_hash`
- `video_probe_json` (JSONB)
- `created_by_usuario_id` (FK)
- `created_at`

Índices sugeridos:
- `(assinatura_id, created_at desc)`
- `(assinatura_id, url_hash)`
- `(created_by_usuario_id, created_at desc)`

Constraints/regras:
- `UNIQUE (assinatura_id, url_hash)` (ou equivalente com `canonical_url`)
- mesma URL em assinaturas diferentes gera registros distintos (sem mistura de dados)

### `subject_template`

- `id` (PK)
- `assinatura_id` (FK)
- `nome`
- `slug`
- `ativo`
- `schema_json` (JSONB do template/form)
- `is_default`
- `created_at`
- `updated_at`

Índices:
- `(assinatura_id, ativo)`
- `UNIQUE (assinatura_id, slug)`

Constraint/regra de negócio:
- garantir **um template padrão por assinatura** (ex.: índice único parcial para `is_default = true`)

### `snap`

Entidade principal

- `id` (PK / `snapId`)
- `assinatura_id` (FK)
- `video_id` (FK)
- `created_by_usuario_id` (FK)
- `subject_template_id` (FK, opcional)
- `nickname_snapshot` (copiar nickname no momento da criação)
- `tipo_snap` (`INSTANT`, `INTERVAL`)
- `status` (`PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`, ...)
- `public_share_token` (único, opcional)
- `is_public` (boolean)
- `data_filmagem` (se aplicável)

- `start_seconds` (nullable)
- `start_frame` (nullable)
- `resolved_start_seconds` (nullable)
- `duration_seconds` (frames)
- `snapshot_duration_seconds` (clip)

- `fps`
- `max_width`
- `format`
- `quality`

- `subject_id` (coluna derivada do `subject.id`)
- `subject_json` (JSONB)
- `video_probe_json` (JSONB)

- `snapshot_video_json` (JSONB)
- `frames_json` (JSONB)  // ou tabela separada em fase seguinte

- `created_at`
- `updated_at`
- `processed_at`

Índices sugeridos:
- `(assinatura_id, created_by_usuario_id, created_at desc)`  // lista do usuário
- `(assinatura_id, video_id, created_at asc)`                // snaps por vídeo
- `(assinatura_id, status, created_at desc)`
- `(public_share_token)` único
- `(assinatura_id, subject_id, created_at desc)`             // busca por subject

### `snap_subject_attr`

Tabela auxiliar indexável para atributos do `subject`

- `id` (PK)
- `snap_id` (FK)
- `assinatura_id` (FK)
- `video_id` (FK, denormalizado)
- `subject_id` (denormalizado)
- `attr_key`
- `value_type` (`STRING`, `NUMBER`)
- `string_value`
- `number_value`

Índices sugeridos:
- `(assinatura_id, attr_key, string_value)`
- `(assinatura_id, attr_key, number_value)`
- `(assinatura_id, subject_id, attr_key, string_value)`
- `(assinatura_id, subject_id, attr_key, number_value)`

## 6.2 Observação sobre “single domain”

Como haverá um único serviço/domínio:
- **não** usar isolamento por subdomínio
- o contexto de assinatura deve ser carregado por:
  - sessão selecionada no player/app
  - ou `api_token` nas chamadas de API
- toda query deve filtrar por `assinatura_id`
- URLs iguais entre assinaturas devem permanecer isoladas por modelagem e filtros

## 7. Contratos de API (v2 - conceituais)

## 7.1 Cadastro/entrada simplificada (fase inicial)

Sem tratar autenticação completa agora, mas registrar:
- `nickname`
- `email`
- `codigoEmpresa` (opcional no bootstrap)

Se o usuário tiver vínculo com múltiplas assinaturas:
- retornar lista para escolha de contexto

## 7.2 Registrar vídeo (ou reutilizar existente)

`POST /v2/videos`

Entrada:
- `url`
- metadados opcionais

Saída:
- `videoId`
- probe (se realizado)
- indicação se foi reaproveitado por URL/hash

Observação de ergonomia:
- este endpoint pode ser opcional para clientes simples
- `POST /v2/snaps` pode aceitar `videoId` **ou** `videoUrl` e criar/reutilizar o `Video` automaticamente no contexto da assinatura

## 7.3 Criar snap (entidade principal)

`POST /v2/snaps`

Entrada (conceitual):

```json
{
  "videoId": "vid_123",
  "videoUrl": "https://appimagens.br-gru-1.linodeobjects.com/...mp4",
  "subjectTemplateId": "tpl_mvp_default",
  "dataFilmagem": "2026-02-24T14:30:00-03:00",
  "startSeconds": 12.0,
  "startFrame": 360,
  "durationSeconds": 1.5,
  "snapshotDurationSeconds": 2.0,
  "fps": 5,
  "maxWidth": 640,
  "format": "jpg",
  "quality": 8,
  "subject": {
    "id": "animal-123",
    "attributes": [
      { "key": "brinco", "type": "string", "stringValue": "12334234534" },
      { "key": "peso", "type": "number", "numberValue": 450.0 },
      { "key": "observacoes", "type": "string", "stringValue": "animal agitado" }
    ]
  },
  "overlay": {
    "enabled": true,
    "mode": "SUBJECT",
    "position": "TOP_RIGHT",
    "fontSize": 18,
    "boxColor": "black@0.7",
    "fontColor": "white",
    "margin": 8,
    "padding": 4
  }
}
```

Regras:
- aceitar `videoId` ou `videoUrl` (ao menos um)
- se vier `videoUrl`, criar/reutilizar `Video` dentro da assinatura ativa (por `assinaturaId + url_hash`)
- `startFrame` tem precedência sobre `startSeconds`
- snap pode ser instantâneo ou intervalo
- se `subjectTemplateId` não for informado, usar o **template padrão da assinatura**
- se `subject.id` ausente, copiar `snapId` gerado para `subject.id`

## 7.4 Listar snaps do usuário

`GET /v2/snaps/mine?sort=recent&limit=50&cursor=...`

Retorna snaps do usuário no contexto da assinatura selecionada.

## 7.5 Listar vídeos do usuário

`GET /v2/videos/mine?sort=recent&limit=50&cursor=...`

Retorna vídeos com atividade do usuário (snaps criados por ele).

Opcional futuro:
- `GET /v2/videos?scope=mine|company&...` para listar também vídeos recentes da assinatura

## 7.6 Listar snaps por vídeo

`GET /v2/videos/{videoId}/snaps?scope=mine|all&limit=...`

Uso no player:
- lista sincronizada com vídeo
- por padrão `scope=all` (regra de colaboração dentro da assinatura)
- opcional `scope=mine` para filtro pessoal
- sempre restrito à assinatura ativa (não mistura snaps de outra empresa para a mesma URL)

## 7.7 Compartilhar snap

`POST /v2/snaps/{snapId}/share`

Saída:
- `publicUrl`
- `publicShareToken`

## 7.8 Visualização pública de snap

`GET /public/snaps/{publicShareToken}`

Retorna página/JSON público com:
- clip (`snapshot.mp4`)
- frames
- metadados públicos do snap

## 7.9 API externa por token de assinatura

O serviço também será consumido por API. Portanto:
- rotas de API devem aceitar autenticação por token de assinatura
- token vincula contexto de `assinatura_id`

Exemplos de uso externo:
- criação de snap por sistemas terceiros
- consulta de snaps/resultados
- busca por `subject`

## 8. Templates de `subject` (v2)

## 8.1 Regra inicial

Template inicial = template equivalente ao usado no MVP (subject genérico tipado).

Regra adicional:
- cada assinatura deve ter um template marcado como **padrão**
- quando a API não receber `subjectTemplateId`, usar o template padrão da assinatura

## 8.2 Requisitos do template

Cada template deve definir:
- nome/slug
- campos visíveis no formulário
- obrigatoriedade
- tipo (`string`/`number`)
- ordem
- default values
- validação

## 8.3 Campo obrigatório novo

Registrar no template um campo:
- `observacoes` (string)

## 8.4 Persistência do template aplicado

Ao criar snap:
- persistir `subject_template_id`
- persistir `subject_json` completo preenchido
- não depender do template para reconstruir histórico
- persistir o `subject_template_id` efetivamente aplicado, inclusive quando houve fallback para template padrão

## 9. Regras de processamento (aproveitando base do MVP/master)

Manter regras já validadas:
- `startFrame > startSeconds` (precedência)
- `resolvedStartSeconds`
- `durationSeconds` (frames)
- `snapshotDurationSeconds` (clip), com fallback quando omitido
- `ffprobe` para compatibilidade
- `videoProbe.reason` em falha
- `drawtext` opcional

Overlay:
- no player/produto social, overlay pode ser ativado por snap
- manter comportamento configurável por snap

## 10. Segurança e autorização (fase inicial)

Sem implementar auth completa agora, mas registrar regra de produto:
- usuário pode visualizar snaps do vídeo no contexto da assinatura ativa
- usuário só edita próprios snaps
- snaps públicos são read-only
- APIs privadas sempre exigem contexto de assinatura + usuário ou token de assinatura

## 11. Lista inicial e navegação (produto)

Como requisito foi deixado flexível:
- lista inicial pode mostrar **últimos vídeos** ou **últimos snaps**

Recomendação de implementação:
- começar por **últimos snaps** (mais simples e já centrado na entidade principal)
- depois adicionar aba de vídeos

## 12. Fases sugeridas de implementação (v2)

## Fase A - Modelo e backend de `Snap`

- Introduzir entidades `assinatura`, `usuario`, `usuario_assinatura`, `video`, `snap`, `subject_template`
- Criar `snap_subject_attr`
- Endpoints privados básicos:
  - registrar vídeo
  - criar snap
  - listar meus snaps
  - listar snaps por vídeo

## Fase B - Compartilhamento público

- gerar `public_share_token`
- endpoint público de visualização/consulta do snap
- página pública simples (clip + frames)

## Fase C - Player integrado

- app Flutter consome `/v2/videos` e `/v2/snaps`
- botão de `record/snap` abre formulário por template
- lista sincronizada de snaps do vídeo

## Fase D - API externa por token

- autenticação por token de assinatura
- endpoints de consulta e criação para sistemas terceiros

## 13. Critérios de aceite (v2 - produto)

- `Snap` é a entidade principal e possui `snapId` próprio.
- Um vídeo pode ter múltiplos snaps.
- Vários usuários podem criar snaps para a mesma URL de vídeo.
- A mesma URL pode ser usada em assinaturas diferentes sem mistura de dados.
- Usuário pode visualizar todos os snaps do vídeo dentro da assinatura ativa.
- Usuário só edita os próprios snaps.
- Snap pode ser instantâneo ou intervalo.
- Snap pode gerar link público compartilhável.
- O formulário usa template de `subject`.
- Assinatura suporta múltiplos templates (inicialmente template MVP).
- Assinatura possui template padrão; quando `subjectTemplateId` não é informado, o padrão é aplicado.
- Template inclui campo `observacoes`.
- Serviço é consumível por player e por API.
- Assinatura possui token para consultas via API.
- Um email pode participar de múltiplas assinaturas e exige seleção de contexto quando aplicável.

## 14. Observações de alinhamento com planos existentes

- `prompts/mvp-tecnico.md` permanece como validação técnica local/síncrona da extração.
- `prompts/master-tecnico.md` permanece como base da arquitetura assíncrona e persistência de processamento.
- `prompts/master-produto-snap.md` adiciona/organiza a visão de **produto**, **multi-assinatura**, **Snap-first** e **integração player+API**.
