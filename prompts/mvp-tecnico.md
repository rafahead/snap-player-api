# Plano MVP (Prompt para Codex) - Odd Video Frames API

## Papel deste arquivo (Plano MVP técnico)

Plano de validação técnica local/síncrona da extração (frames + snapshot + `subject`), usado para aprender e estabilizar regras antes da arquitetura completa.

Referências:
- `prompts/README.md` (governança)
- `prompts/master-tecnico.md` (Master Técnico)
- `prompts/master-produto-snap.md` (Master Produto / Snap-first)

## Objetivo do MVP

Construir uma versão mínima da API para validar somente o fluxo local:

1. Receber o JSON de entrada (lista de filmagens) com `subject` genérico.
2. Validar compatibilidade do vídeo (probe com FFmpeg/ffprobe).
3. Processar os frames e gerar um snapshot em vídeo (clip) com FFmpeg.
4. Salvar os arquivos em um diretório temporário local.
5. Retornar um JSON com status, propriedades detectadas do vídeo e caminhos/nomes dos arquivos gerados.

Este MVP serve para validar contrato de entrada (incluindo `subject` genérico), execução do FFmpeg e estrutura de saída antes de adicionar banco, processamento assíncrono e upload em S3.

## Escopo (Incluído)

- Endpoint `POST` para receber batch de filmagens.
- Validação básica do payload.
- Probe de vídeo por item (propriedades e compatibilidade).
- Extração de frames por item usando FFmpeg (`ProcessBuilder`, sem shell).
- Geração de snapshot em vídeo (`snapshot.mp4`) por item.
- Overlay opcional com `drawtext`, renderizando `subject.id` + atributos no frame e no snapshot.
- Criação de pasta temporária por requisição e por item.
- Resposta HTTP com resultado consolidado (sincrono neste MVP).
- Limpeza opcional por configuração (manter arquivos para inspeção).

## Fora de Escopo (Não Implementar no MVP)

- PostgreSQL / Flyway
- Worker assíncrono / filas / `SKIP LOCKED`
- S3 / Linode Object Storage
- Busca/pesquisa por `subject` (fica para a fase com persistência)
- Callback URL
- Idempotência por hash
- Docker Compose completo com Postgres

## Stack do MVP

- Java 17
- Spring Boot 3.x
- FFmpeg (instalado no ambiente)
- Maven ou Gradle

## Contrato de API (MVP)

### Criar e processar batch (sincrono)

`POST /v1/video-frames/mvp/process`

**Body:** JSON array (min 1)

```json
[
  {
    "videoUrl": "https://.../video.mp4",
    "startSeconds": 12.0,
    "startFrame": null,
    "durationSeconds": 3.0,
    "snapshotDurationSeconds": 5.0,
    "fps": 5,
    "maxWidth": 1280,
    "format": "jpg",
    "quality": 3,
    "dataFilmagem": "2026-02-24T14:30:00-03:00",
    "subject": {
      "id": "animal-123",
      "attributes": [
        { "key": "brinco", "type": "string", "stringValue": "12334234534" },
        { "key": "peso", "type": "number", "numberValue": 450.0 }
      ]
    },
    "overlay": {
      "enabled": true,
      "mode": "SUBJECT",
      "position": "TOP_RIGHT",
      "fontSize": 28,
      "boxColor": "black@0.7",
      "fontColor": "white",
      "margin": 20,
      "padding": 10
    }
  }
]
```

### Validações mínimas

- `videoUrl` obrigatório e `http/https`
- informar pelo menos um entre `startSeconds` ou `startFrame`
- se ambos forem informados, usar `startFrame` (precedência)
- `startSeconds >= 0` (quando usado)
- `startFrame >= 0` inteiro (quando informado)
- `durationSeconds > 0` (tempo das imagens/frames)
- `snapshotDurationSeconds > 0` (tempo do clip em vídeo; opcional, default = `durationSeconds`)
- `fps >= 1` (default `5`)
- `maxWidth >= 320` (default `1280`)
- `format` = `jpg|png` (default `jpg`)
- `quality` somente para `jpg` (default `3`)
- `dataFilmagem` obrigatório (ISO-8601 com offset)
- `subject` obrigatório
- `subject.id` obrigatório
- `subject.attributes` opcional (sem limite funcional fixo no contrato; pode haver limite operacional configurável)
- cada atributo deve ter `key` único por item
- `type`: `string | number`
- `type=string` => exigir `stringValue` e proibir `numberValue`
- `type=number` => exigir `numberValue` e proibir `stringValue`
- `overlay` opcional
- se `overlay.enabled=true`, aplicar `drawtext` em frames e no `snapshot.mp4`
- o texto do overlay deve exibir o máximo de informações do `subject` (`id` + atributos)

### Compatibilidade do vídeo (feedback obrigatório)

- Antes de extrair, executar `ffprobe` para coletar propriedades do vídeo.
- Se o vídeo não for compatível para extração, retornar falha por item com motivo explícito.
- O retorno por item deve informar propriedades detectadas do vídeo (quando disponíveis).

### Formatos e propriedades aceitos (MVP)

- Contêineres aceitos (mínimo): `mp4`, `mov`, `mkv`, `webm` (desde que o FFmpeg local consiga decodificar).
- Deve existir ao menos 1 stream de vídeo decodificável.
- Propriedades mínimas a informar no retorno (probe):
  - `containerFormat`
  - `codecName`
  - `width`
  - `height`
  - `durationSeconds`
  - `sourceFps` (ou `null` se não detectável)
  - `pixelFormat` (se disponível)
- Casos que devem retornar feedback de incompatibilidade:
  - sem stream de vídeo
  - codec não suportado pelo FFmpeg instalado
  - `ffprobe`/`ffmpeg` falha ao abrir URL
  - duração inválida/indisponível para o recorte solicitado

### Resposta de sucesso (exemplo)

`200 OK`

```json
{
  "requestId": "uuid",
  "status": "COMPLETED",
  "tmpBaseDir": "/data/tmp/video-frames-mvp",
  "requestDir": "/data/tmp/video-frames-mvp/{requestId}",
  "processedAt": "ISO-8601",
  "filmagens": [
    {
      "itemIndex": 0,
      "status": "SUCCEEDED",
      "dataFilmagem": "2026-02-24T14:30:00-03:00",
      "subject": {
        "id": "animal-123",
        "attributes": [
          { "key": "brinco", "type": "string", "stringValue": "12334234534" },
          { "key": "peso", "type": "number", "numberValue": 450.0 }
        ]
      },
      "videoUrl": "https://.../video.mp4",
      "startSeconds": 12.0,
      "startFrame": null,
      "resolvedStartSeconds": 12.0,
      "videoProbe": {
        "compatible": true,
        "containerFormat": "mov,mp4,m4a,3gp,3g2,mj2",
        "codecName": "h264",
        "width": 1280,
        "height": 720,
        "durationSeconds": 18.4,
        "sourceFps": 29.97,
        "pixelFormat": "yuv420p",
        "reason": null
      },
      "outputDir": "/data/tmp/video-frames-mvp/{requestId}/item-000",
      "snapshotVideo": {
        "fileName": "snapshot.mp4",
        "path": "/data/tmp/video-frames-mvp/{requestId}/item-000/snapshot.mp4",
        "durationSeconds": 5.0
      },
      "frameCount": 15,
      "frames": [
        {
          "index": 1,
          "timestampSeconds": 12.0,
          "fileName": "frame_00001.jpg",
          "path": "/data/tmp/video-frames-mvp/{requestId}/item-000/frame_00001.jpg"
        }
      ],
      "error": null
    }
  ]
}
```

### Resposta parcial com falhas por item (MVP)

- O endpoint deve tentar processar todos os itens.
- Se alguns itens falharem, responder `200 OK` com `status = PARTIAL`.
- Se todos falharem, responder `422` ou `500` (definir e documentar no README; preferencia: `200` com `FAILED` para manter JSON consolidado).
- Em falha por incompatibilidade, incluir `videoProbe.compatible=false` e `videoProbe.reason`.

## Processamento com FFmpeg (MVP)

- Executar FFmpeg via `ProcessBuilder` com lista de argumentos.
- Executar `ffprobe` via `ProcessBuilder` com lista de argumentos.
- Nunca usar `bash -c`.
- Trabalhar direto com URL remota.
- Aplicar timeout por processo (ex: 60s).
- Capturar `stderr` limitado para debug.
- Se `startFrame` for informado, calcular `resolvedStartSeconds` usando `sourceFps` detectado.
- Se `startFrame` e `startSeconds` vierem juntos, usar `startFrame`.
- `durationSeconds` controla a extração de frames (imagens).
- `snapshotDurationSeconds` controla a duração do `snapshot.mp4` (se omitido, usar `durationSeconds`).
- Preservar `subject` no fluxo (mesmo sem persistência neste MVP), para validar o contrato que será usado na fase assíncrona.
- `drawtext` (quando habilitado) deve renderizar `subject.id` + todos os atributos.
- Aplicar overlay em frames e também no `snapshot.mp4`.

### Comando base JPG

```bash
ffmpeg -hide_banner -loglevel error \
  -ss {start} -t {duration} \
  -i {url} \
  -vf "fps={fps},scale={maxWidth}:-2" \
  -q:v {quality} \
  {outDir}/frame_%05d.jpg
```

### Comando base PNG

```bash
ffmpeg -hide_banner -loglevel error \
  -ss {start} -t {duration} \
  -i {url} \
  -vf "fps={fps},scale={maxWidth}:-2" \
  {outDir}/frame_%05d.png
```

### Comando base snapshot em vídeo (MP4)

```bash
ffmpeg -hide_banner -loglevel error \
  -ss {resolvedStart} -t {snapshotDuration} \
  -i {url} \
  -vf "scale='min({maxWidth},iw)':-2" \
  -c:v libx264 -preset veryfast -crf 23 \
  -an \
  {outDir}/snapshot.mp4
```

## Estrutura de Diretórios Temporários

Padrao sugerido:

```text
{tmpBase}/
  {requestId}/
    item-000/
      snapshot.mp4
      frame_00001.jpg
      frame_00002.jpg
    item-001/
      ...
```

### Regras

- `tmpBase` configurável (default: `/data/tmp/video-frames-mvp`)
- Criar `requestDir` por chamada
- Criar `item-XXX` por item do array
- Manter arquivos no disco por padrao para inspecao manual
- Opcional: endpoint/flag futura para limpeza

## Configuração (application.yml) - MVP

```yaml
app:
  mvp:
    tmpBase: /data/tmp/video-frames-mvp
    maxBatchItems: 10
    maxSubjectAttributes: 100
    maxDurationSeconds: 5
    maxFps: 10
    maxWidth: 1280
    acceptedContainers: [mp4, mov, mkv, webm]
    ffmpeg:
      path: ffmpeg
      timeoutSeconds: 60
    ffprobe:
      path: ffprobe
      timeoutSeconds: 30
```

## Entregáveis do MVP

1. Projeto Spring Boot 3.x (Java 17)
2. `MvpBatchController` com endpoint `POST /v1/video-frames/mvp/process`
3. DTOs de request/response
4. `MvpVideoFrameService` (validação + processamento síncrono)
5. `VideoProbeService` (ffprobe + compatibilidade + FPS de origem)
6. `FfmpegService` (montagem segura de args e execução; frames + snapshot)
7. `TempStorageService` (criação de pastas e listagem de arquivos gerados)
8. `README.md` com exemplo `curl` e pré-requisitos (`ffmpeg` instalado)

## Testes Mínimos (MVP)

- Validação de request (campos obrigatórios, limites e precedência `startFrame` > `startSeconds`)
- Validação de `subject` (id obrigatório + atributos tipados)
- Validação de `snapshotDurationSeconds` e fallback para `durationSeconds`
- Teste de montagem segura do comando FFmpeg (lista de args; sem shell)
- Teste de montagem de `drawtext` com texto derivado de `subject`
- Teste de montagem segura do comando `ffprobe`
- Teste de compatibilidade (sem stream de vídeo / erro de probe)
- Teste de conversão `startFrame -> resolvedStartSeconds`
- Teste de criação de diretório temporário por request/item
- (Opcional) teste de integração com vídeo real, se ambiente permitir

## Critérios de Aceite do MVP

- API recebe JSON array no formato esperado.
- API aceita `subject` genérico (`id` + atributos tipados) no payload.
- Processa pelo menos 1 vídeo válido e gera frames + `snapshot.mp4` em diretório temporário.
- Retorna JSON consolidado com `frameCount`, `snapshotVideo`, `videoProbe` e paths locais.
- Retorna/propaga o `subject` recebido em cada item para validar o contrato genérico.
- Quando `overlay.enabled=true`, aplica `drawtext` em frames e snapshot com dados do `subject`.
- Permite tempos diferentes para frames (`durationSeconds`) e snapshot (`snapshotDurationSeconds`).
- Se `startFrame` e `startSeconds` forem enviados juntos, usa `startFrame`.
- Em vídeo incompatível, retorna feedback claro com motivo (`videoProbe.reason`) e propriedades detectadas quando possível.
- Não usa banco de dados nem S3.
- FFmpeg/ffprobe são executados com `ProcessBuilder` e timeout.

## Instrução Final para Codex (MVP)

Gerar a implementacao completa do MVP (sem pseudo-codigo), focada apenas no fluxo local:
- receber JSON,
- detectar propriedades/compatibilidade do vídeo,
- extrair frames e gerar snapshot em vídeo,
- salvar em diretório temporário,
- retornar JSON consolidado com os arquivos gerados e feedback de compatibilidade.

Nao implementar banco, worker assíncrono ou S3 nesta etapa.
