# Odd Video Frames API

API para extracao de frames e snapshot de video com FFmpeg, com foco em um contrato generico de metadados (`subject`) para reutilizacao em diferentes dominios.

## Estado do Projeto

- `MVP` implementado (sincrono, local, sem banco/S3)
- `Master plan` documentado para evolucao assíncrona com PostgreSQL, workers e storage S3

Arquivos de plano:
- `prompts/mvp-tecnico.md`
- `prompts/master-tecnico.md`

## O Que o MVP Faz

- Recebe um JSON array com filmagens
- Faz `ffprobe` por item para validar compatibilidade e ler propriedades do video
- Extrai frames (JPG/PNG)
- Gera `snapshot.mp4`
- Salva tudo em diretorio temporario local
- Retorna JSON consolidado com:
  - `videoProbe`
  - `resolvedStartSeconds`
  - `snapshotVideo`
  - lista de `frames`
- Suporta `overlay` com `drawtext` (FFmpeg), renderizando **o maximo de informacoes do `subject`** no frame e no snapshot

## Contrato de Entrada (MVP)

Cada item da lista deve conter:

- `videoUrl`
- `dataFilmagem`
- `subject`:
  - `id` (obrigatorio)
  - `attributes[]` (opcional), com atributos tipados:
    - `type = "string"` + `stringValue`
    - `type = "number"` + `numberValue`

Tambem suporta:
- `startSeconds` ou `startFrame` (`startFrame` tem precedencia)
- `durationSeconds` (frames)
- `snapshotDurationSeconds` (clip; opcional, fallback para `durationSeconds`)
- `fps`, `maxWidth`, `format`, `quality`
- `overlay` (opcional)

## Requisitos

- Java 17 (testado: `17.0.7 LTS`)
- Maven 3.9+
- `ffmpeg` no `PATH`
- `ffprobe` no `PATH`
- Para overlay com `drawtext`:
  - FFmpeg com filtro `drawtext` habilitado
  - fonte TTF disponivel (padrao: DejaVu Sans Bold)

## Instalacao (Ambiente)

### Ubuntu/Debian (exemplo)

```bash
sudo apt update
sudo apt install -y ffmpeg fontconfig fonts-dejavu-core
```

Verificar `drawtext`:

```bash
ffmpeg -hide_banner -filters | grep drawtext
```

Se aparecer algo como `drawtext ... using libfreetype`, o overlay deve funcionar.

## Build e Testes

Rodar testes:

```bash
mvn -Dmaven.repo.local=.m2/repository test
```

O projeto possui testes unitarios para:
- validacao de controller
- montagem segura de comandos FFmpeg
- probe (`ffprobe`)
- storage temporario
- overlay `drawtext` no comando

## Como Rodar (MVP)

Subir a API:

```bash
mvn -Dmaven.repo.local=.m2/repository spring-boot:run
```

Se precisar de `fps` maior que o limite padrao:

```bash
mvn -Dmaven.repo.local=.m2/repository spring-boot:run '-Dspring-boot.run.arguments=--app.mvp.maxFps=30'
```

## Endpoint MVP

- `POST /v1/video-frames/mvp/process`

## Exemplo de Chamada (Com Overlay e Subject)

```bash
curl -sS -X POST http://127.0.0.1:8080/v1/video-frames/mvp/process \
  -H 'Content-Type: application/json' \
  -d '[
    {
      "videoUrl": "https://appimagens.br-gru-1.linodeobjects.com/57_36081920_20250220T170651_9eb0.mp4",
      "startSeconds": 12.0,
      "startFrame": 360,
      "durationSeconds": 1.0,
      "snapshotDurationSeconds": 2.0,
      "fps": 5,
      "maxWidth": 640,
      "format": "jpg",
      "quality": 10,
      "dataFilmagem": "2026-02-24T14:30:00-03:00",
      "subject": {
        "id": "animal-123",
        "attributes": [
          { "key": "brinco", "type": "string", "stringValue": "12334234534" },
          { "key": "peso", "type": "number", "numberValue": 450.0 },
          { "key": "lote", "type": "string", "stringValue": "A1" }
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
  ]'
```

## Overlay (`drawtext`) no MVP

### Como funciona

- Se `overlay.enabled=true`, o MVP aplica `drawtext`:
  - nos frames
  - no `snapshot.mp4`
- O texto exibido usa o `subject`:
  - `subject.id`
  - todos os atributos (`key: value`) na ordem recebida

### Modos suportados

- `SUBJECT` (padrao)
- `SUBJECT_AND_FRAME`
- `SUBJECT_AND_TIMESTAMP`
- `SUBJECT_AND_BOTH`

Aliases legados (aceitos por compatibilidade):
- `FRAME_NUMBER` -> `SUBJECT_AND_FRAME`
- `TIMESTAMP` -> `SUBJECT_AND_TIMESTAMP`
- `BOTH` -> `SUBJECT_AND_BOTH`

### Posicao suportada no MVP

- `TOP_RIGHT` (apenas)

## Regras Importantes do Recorte

- Envie `startSeconds` ou `startFrame`
- Se enviar os dois, o MVP usa `startFrame`
- O retorno informa `resolvedStartSeconds`
- `durationSeconds` controla os frames
- `snapshotDurationSeconds` controla o `snapshot.mp4`

## Compatibilidade do Video (Probe)

Antes da extracao, o MVP roda `ffprobe` e retorna `videoProbe` com:

- `containerFormat`
- `codecName`
- `width`
- `height`
- `durationSeconds`
- `sourceFps`
- `pixelFormat`
- `compatible`
- `reason` (em erro/incompatibilidade)

Contêineres aceitos por padrao:
- `mp4`
- `mov`
- `mkv`
- `webm`

Observacao: a compatibilidade real depende do FFmpeg instalado no ambiente.

## Estrutura de Saida Local (MVP)

Exemplo:

```text
/tmp/video-frames-mvp/
  {requestId}/
    item-000/
      snapshot.mp4
      frame_00001.jpg
      frame_00002.jpg
      ...
```

## Configuracao (`application.yml`)

Arquivo: `src/main/resources/application.yml`

Principais chaves:
- `app.mvp.tmpBase`
- `app.mvp.maxBatchItems`
- `app.mvp.maxSubjectAttributes`
- `app.mvp.maxDurationSeconds`
- `app.mvp.maxFps`
- `app.mvp.maxWidth`
- `app.mvp.acceptedContainers`
- `app.mvp.ffmpeg.path`
- `app.mvp.ffmpeg.timeoutSeconds`
- `app.mvp.ffmpeg.fontFile`
- `app.mvp.ffprobe.path`
- `app.mvp.ffprobe.timeoutSeconds`

## Troubleshooting

### 1. Overlay falha com `drawtext filter is not available`

Verifique:

```bash
ffmpeg -hide_banner -filters | grep drawtext
```

Se nao aparecer `drawtext`, seu FFmpeg foi compilado sem esse filtro.

### 2. Fonte nao encontrada

Verifique o arquivo configurado:

```bash
ls -l /usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf
```

Trocar fonte no startup:

```bash
mvn -Dmaven.repo.local=.m2/repository spring-boot:run \
  '-Dspring-boot.run.arguments=--app.mvp.ffmpeg.fontFile=/caminho/fonte.ttf'
```

### 3. Video incompatível

Veja no JSON de resposta:
- `filmagens[i].videoProbe.reason`
- `filmagens[i].error`

## Estrutura do Projeto (Resumo)

- `src/main/java/com/oddplayerapi/mvp/controller` - endpoint MVP
- `src/main/java/com/oddplayerapi/mvp/service` - probe, ffmpeg, fluxo MVP, temp storage
- `src/main/java/com/oddplayerapi/mvp/dto` - contratos JSON
- `src/main/resources/application.yml` - configuracao
- `src/test/java` - testes unitarios
- `prompts/mvp-tecnico.md` - plano MVP
- `prompts/master-tecnico.md` - plano assíncrono completo

## Roadmap (Master)

Planejado em `prompts/master-tecnico.md`:
- processamento assíncrono por batch
- PostgreSQL + Flyway
- worker com `SKIP LOCKED`
- upload S3/Linode Object Storage
- consulta por `subject.id`
- busca por atributos do `subject` (igualdade e faixa numérica)

## Contribuicao

- Defina primeiro o escopo no plano correspondente:
  - `prompts/mvp-tecnico.md` para evolucoes locais/sincronas
  - `prompts/master-tecnico.md` para arquitetura assíncrona/persistente
- Rode testes antes de enviar alteracoes:
  - `mvn -Dmaven.repo.local=.m2/repository test`
- Para mudancas no contrato JSON, atualize:
  - DTOs
  - validacoes
  - testes
  - README/plano correspondente

## Versionamento / Releases

- MVP atual: processamento síncrono local (sem PostgreSQL/S3)
- Evolucoes futuras previstas no `master` devem introduzir versionamento de contrato/endpoints quando houver quebra de compatibilidade

## Licenca / Uso

Definir conforme sua necessidade de distribuicao (interno, privado, OSS, etc.).
