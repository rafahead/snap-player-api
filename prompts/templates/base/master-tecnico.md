# Master Técnico — {{NOME_PROJETO}}

## Papel deste arquivo

Fonte de verdade para arquitetura e decisões técnicas.

Use junto com:
- `CONTEXT.md` (estado atual e próximas prioridades)
- `prompts/masters/master-produto.md` (domínio e regras de produto)
- `prompts/masters/master-roadmap.md` (sequenciamento de entregas)
- `prompts/masters/master-adrs.md` (decisões arquiteturais)

---

## Stack

- Java 17 + Spring Boot 3.x
- PostgreSQL + Flyway (migrations versionadas)
- Linode Object Storage — S3-compatível (AWS SDK v2)
- Flutter web (cliente)
- Deploy: serviço Linux via systemd (sem Docker na fase inicial)
- Servidor: Linode

---

## Arquitetura

### Componentes principais

| Componente | Responsabilidade |
|---|---|
| API REST (Spring Boot) | (a definir) |
| Banco de dados (PostgreSQL) | (a definir) |
| Storage (Linode/S3) | (a definir) |

### Fluxo principal

```
[cliente] → [API] → [serviço] → [banco]
                              ↘ [storage]
```

---

## Processamento

### Modo de operação

- Fase 1: síncrono (simples, sem fila)
- Fase 2 (planejada): assíncrono com fila em banco (FOR UPDATE SKIP LOCKED)

### Fluxo (a detalhar por entrega)

1. ...

---

## Persistência

- PostgreSQL em produção; H2 em memória nos testes
- Migrations via Flyway (`src/main/resources/db/migration/`)
- Convenção: `V{N}__{descricao}.sql`
- Entidades JPA com `@Column(columnDefinition="text")` para campos JSON/texto longo (evitar `@Lob`)

---

## Integrações externas

| Serviço | Finalidade | SDK/Lib |
|---|---|---|
| Linode Object Storage | Storage de artefatos | AWS SDK v2 |

---

## Segurança

- SSRF: URLs externas validadas antes de qualquer requisição de rede
- Tokens: comparação timing-safe (MessageDigest.isEqual)
- Segredos: variáveis de ambiente (nunca hardcoded no código ou application.yml)
- Autenticação: (a definir)

---

## Observabilidade

- `spring-boot-starter-actuator` — `/actuator/health`, `/actuator/metrics`
- `X-Request-Id` nos logs via MDC (truncado a 64 chars)
- `GlobalExceptionHandler`: 5xx retorna mensagem genérica + `log.error` com stack

---

## Deploy (Linux service)

```bash
# build
mvn package -q -DskipTests

# instalar
sudo cp target/{{SLUG}}-*.jar /opt/{{SLUG}}/app.jar
sudo systemctl enable {{SLUG}}
sudo systemctl start {{SLUG}}
```

Unit file: `/etc/systemd/system/{{SLUG}}.service`
Variáveis de ambiente: `/etc/default/{{SLUG}}` (via `EnvironmentFile=` no unit)

---

## Limites operacionais (Linode 2GB)

- Heap JVM: `-Xmx512m` (ajustar conforme uso real)
- Timeout de requisição: (a definir)
- Tamanho máximo de arquivo: (a definir)

*Atualizado: {{MES_ANO}}.*
