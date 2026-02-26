# Master de Produto — Snap Player (Snap-first)

## Papel deste arquivo

Fonte de verdade para domínio e regras de produto.

Use junto com:
- `CONTEXT.md` (estado atual e próximas prioridades)
- `prompts/masters/master-tecnico.md` (arquitetura técnica)
- `prompts/masters/master-roadmap.md` (sequenciamento)

---

## Objetivo do Produto

Plataforma genérica de evidência visual estruturada em vídeo.

O operador assiste um vídeo, marca o momento relevante (snap),
preenche um formulário baseado no template da assinatura,
e o sistema gera frames, clip e registro rastreável e pesquisável.

Consumido via:
- API programática (token por assinatura)
- Player Flutter snap-player (integração futura)

---

## Cliente atual em produção

**Olho do Dono** — pesagem de bovinos por câmera 3D/2D.
Snap Player usado para captura e registro visual individual
de animais ao longo do ciclo produtivo.

Mais de 30 clientes operando no mundo.

SubjectTemplate para Olho do Dono (campos sugeridos):
- brinco (string, obrigatório)
- raca (string, obrigatório)
- sexo (string, obrigatório)
- peso_referencia (number, opcional)
- condicao_corporal (string, opcional)
- lote (string, opcional)
- pasto (string, opcional)
- observacoes (string, opcional)

---

## Conceitos de Domínio

### Assinatura (tenant lógico)

Representa a empresa/cliente que usa o serviço.
Isolamento lógico por assinatura_id em todas as queries.
Um único serviço, um único domínio — sem separação por subdomínio.

Campos principais:
- id, codigo (único), nome, api_token (único), status, created_at

Fase atual: assinatura default única.
Modelo já preparado para multi-assinatura sem retrabalho.

### Usuário

Cadastro simplificado (sem autenticação completa nesta fase):
- nickname (obrigatório, identidade operacional)
- email (obrigatório, persistido)

Um email pode participar de múltiplas assinaturas.
Quando múltiplas assinaturas, usuário escolhe contexto ativo.

### Video

Entidade relevante, não só insumo transitório.
Representa um vídeo analisado a partir de uma URL.
Pode ter múltiplos snaps de múltiplos usuários.

Regras:
- Mesma URL em assinaturas diferentes gera registros distintos
- Reutilizar video existente quando mesma URL na mesma assinatura
  (por url_hash + assinatura_id)
- video_probe_json armazena propriedades detectadas

### SubjectTemplate

Template de formulário para o subject do snap.

Regras:
- Uma assinatura pode ter vários templates
- Uma assinatura deve ter um template padrão (is_default = true)
- Quando subjectTemplateId não informado, usar template padrão
- Persistir subject_template_id efetivamente aplicado no snap
- Template inclui campo observacoes obrigatoriamente
- schema_json define campos, tipos, obrigatoriedade, ordem e defaults

### Snap (entidade principal)

Marcação/processamento sobre um vídeo.

Tipos:
- INSTANT — marcação pontual, snapshot curto e/ou frames próximos
- INTERVAL — duração explícita para frames e clip

Campos principais:
- snapId próprio (gerado pelo sistema)
- vínculo com video e usuário criador
- recorte temporal (startSeconds ou startFrame)
- subject (id + atributos tipados)
- resultados: frames + snapshot.mp4 + videoProbe
- status: PENDING / PROCESSING / COMPLETED / FAILED
- link público opcional (public_share_token)

Regra de subject.id:
- Se subject.id não informado no formulário, copiar snapId

---

## Regras de Negócio

### Visibilidade e edição

- Usuário trabalha no contexto de uma assinatura ativa
- Dentro de um vídeo, usuário vê todos os snaps da assinatura ativa
- Usuário edita apenas os próprios snaps
- Snap compartilhado publicamente é somente leitura

### Processamento

- startFrame tem precedência sobre startSeconds
- resolvedStartSeconds sempre retornado
- durationSeconds controla frames; snapshotDurationSeconds controla clip
- snapshotDurationSeconds faz fallback para durationSeconds se omitido
- ffprobe obrigatório antes da extração
- Vídeo incompatível retorna feedback claro por snap

### Compartilhamento público

- Snap gera link público com token único e imutável
- Link público exibe: clip, frames, metadados essenciais do snap,
  nome da assinatura (sem dados privados), data e hora
- Snap não pode ser deletado após compartilhamento público
  (apenas ocultado da listagem do usuário)

### Identidade de snap vs subject

- snapId é sempre gerado pelo sistema
- subject.id pertence ao domínio do cliente (animal, ativo, objeto)
- Se subject.id ausente: copiar snapId para subject.id

---

## Contratos de API (v2)

### Criar snap

POST /v2/snaps

Entrada:
```json
{
  "videoId": "uuid-opcional",
  "videoUrl": "https://...",
  "subjectTemplateId": "uuid-opcional",
  "nickname": "operador01",
  "email": "operador@empresa.com",
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
      { "key": "peso_referencia", "type": "number", "numberValue": 450.0 },
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
- videoId ou videoUrl (ao menos um obrigatório)
- Se videoUrl: criar ou reutilizar video na assinatura
- startFrame tem precedência sobre startSeconds
- subjectTemplateId ausente: usar template padrão da assinatura
- subject.id ausente: copiar snapId gerado

### Consultar snap

GET /v2/snaps/{snapId}

Retorna: status, videoProbe, subject, snapshotVideo, frames, job

### Listar snaps por vídeo

GET /v2/videos/{videoId}/snaps?scope=mine|all&limit=&cursor=

- scope=all por padrão (colaboração dentro da assinatura)
- scope=mine para filtro pessoal
- Sempre restrito à assinatura ativa

### Meus snaps

GET /v2/snaps/mine?nickname=&limit=&cursor=&sortBy=&sortDir=

### Meus vídeos

GET /v2/videos/mine?nickname=&limit=&cursor=

### Busca por subject

GET /v2/snaps/search?subjectId=&attrKey=&attrValue=&limit=&cursor=

Suporte atual: igualdade (string e number)
Futuro: faixa numérica (GTE/LTE)

### Compartilhar snap

POST /v2/snaps/{snapId}/share

Saída: publicUrl, publicShareToken

### Visualização pública

GET /public/snaps/{publicShareToken}

Retorna: clip, frames, metadados públicos do snap

---

## Fases de Produto

### Fase atual — API em produção para Olho do Dono

Foco: estabilizar, ir para produção com S3, assinatura default,
template de bovinos, modo assíncrono como padrão.

### Fase futura A — Multi-assinatura e autenticação

- Autenticação por token de assinatura operacional
- Múltiplos clientes isolados
- Onboarding por código de empresa

### Fase futura B — Player Flutter

- snap-player consome /v2/videos e /v2/snaps
- Botão record/snap abre formulário por template
- Lista sincronizada de snaps do vídeo

### Fase futura C — Inteligência por IA (fora do escopo atual)

- Sugestão de atributos via análise de frame (Anthropic API)
- Análise diferencial com imagens de referência
- Geração de laudo por sessão de inspeção
- Vertical de inspeção industrial / petróleo e gás

---

## Critérios de Aceite (Produto)

- Snap é a entidade principal com snapId próprio
- Video pode ter múltiplos snaps de múltiplos usuários
- Mesma URL em assinaturas diferentes não mistura dados
- Usuário vê todos os snaps do vídeo na assinatura ativa
- Usuário edita apenas os próprios snaps
- Snap pode ser instantâneo ou intervalo
- Snap gera link público compartilhável
- Formulário usa template de subject da assinatura
- Assinatura tem template padrão
- Template inclui campo observacoes
- API consumível por token de assinatura
- Email pode participar de múltiplas assinaturas
