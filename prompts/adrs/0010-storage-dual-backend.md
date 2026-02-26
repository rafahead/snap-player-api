# ADR 0010 — Storage Dual-Backend: Local (dev) / S3 Compatível (prod)

## Status

Aceito

## Contexto

Na Entrega 5, o projeto precisava substituir o armazenamento em diretório
temporário (`tmp`) por armazenamento persistente dos artefatos (frames e clip).

Requisitos conflitantes:
- Em desenvolvimento: rodar sem credenciais cloud, artefatos acessíveis localmente
- Em produção (Linode): artefatos duráveis e acessíveis por URL pública, fora do filesystem efêmero do container

Alternativas consideradas:
1. Sempre usar S3 (exige configuração em dev)
2. Sempre usar filesystem local (não funciona em produção cloud)
3. Dual-backend com feature flag: local em dev, S3 em produção → **escolhida**

## Decisão

`StorageService` com dois backends selecionados por configuração:

- `app.storage.local.enabled=true` → persiste em `basePath` no filesystem
- `app.storage.s3.enabled=true` → persiste via AWS SDK v2 no bucket configurado

Apenas um backend ativo por vez. S3 tem precedência quando ambos estão `true`.

Chaves de artefatos são estáveis e baseadas no `snapId`:
- frames: `{prefix}/frames/{snapId}/frame_NNNNN.jpg`
- clip: `{prefix}/snapshots/{snapId}/snapshot.mp4`

`clientRequestId = snapId` no fluxo `v2` (sync e worker) garante que
reprocessamentos usem as mesmas chaves, tornando o upload idempotente.

Cleanup do diretório temporário (`finally`) permanece independente do
backend de storage — sempre ocorre após upload bem-sucedido ou em falha.

## Consequências

### Positivas

- Dev funciona sem credenciais cloud (`local.enabled=true` é o default)
- Prod usa S3 compatível (Linode Object Storage, AWS S3, MinIO) sem mudança de código
- Keys estáveis por `snapId` permitem reprocessamento sem acúmulo de artefatos órfãos
- Temp folder não cresce indefinidamente (cleanup em `finally`)

### Trade-offs / Custos

- Dois backends para manter (surface de teste maior)
- Sem testes de integração com S3 real/LocalStack ainda — validação em produção
- `publicBaseUrl` precisa ser configurado manualmente para URLs públicas no S3

### Configuração de produção

```yaml
app.storage.s3.enabled: true
app.storage.s3.endpoint: ${STORAGE_ENDPOINT}
app.storage.s3.bucket: ${STORAGE_BUCKET}
app.storage.s3.accessKey: ${STORAGE_ACCESS_KEY}
app.storage.s3.secretKey: ${STORAGE_SECRET_KEY}
app.storage.s3.publicBaseUrl: ${STORAGE_PUBLIC_BASE_URL}
```

## Relação com planos

- `prompts/masters/master-tecnico.md`
- `prompts/masters/master-roadmap.md` (Entrega 5)
