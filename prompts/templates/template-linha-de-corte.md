# Template — Linha de Corte (projeto existente, sem auditoria)

## Papel deste arquivo

Para projetos existentes onde **não vale a pena documentar o passado**.
O código anterior é reconhecido mas não catalogado.
A metodologia passa a valer a partir do momento em que este template é aplicado.

Diferença dos outros templates:

| Template | Quando usar |
|---|---|
| `template-projeto-novo.md` | Projeto novo, sem código |
| `template-projeto-existente.md` | Projeto existente — quero documentar o que já existe |
| **este arquivo** | Projeto existente — quero ignorar o passado e aplicar a metodologia daqui em diante |

---

## Prompt de linha de corte (copiar e usar)

```md
# Linha de Corte — Adoção da Metodologia em Projeto Existente

Este projeto já tem código. Não quero documentar o que foi feito antes.
Quero apenas criar a estrutura `prompts/` mínima para aplicar a metodologia daqui em diante.

## Regras de trabalho

- Não auditar nem catalogar o código existente.
- Registrar o estado atual de forma mínima (1-2 linhas por item).
- Foco total nas próximas prioridades.
- Usar modo silencioso: só resultado final, sem updates intermediários.

## Stack padrão (confirmar ou ajustar)

- Backend: Java 17 + Spring Boot 3.x
- Banco: PostgreSQL + Flyway
- Storage: Linode Object Storage (S3-compatível, AWS SDK v2)
- Frontend: Flutter web
- Deploy: serviço Linux via systemd (sem Docker na fase inicial)
- Servidor: Linode

## Entradas (preencher)

### 1. Identidade
- Nome do projeto:
- Slug:

### 2. Estado atual (resumo livre — não precisa ser exato)
- O que o sistema faz hoje em 1-2 linhas:
- Está em produção? (sim/não):
- Há algo quebrado ou crítico agora?:

### 3. Próximas prioridades (o que importa daqui em diante)
- Prioridade 1:
- Prioridade 2:
- Prioridade 3:

### 4. Decisões que preciso registrar (opcional)
Se houver alguma decisão recente ou futura que vale documentar como ADR:
- Decisão:

## Arquivos base a copiar antes de iniciar

```bash
cp prompts/templates/base/AGENTS.md            ./
cp prompts/templates/base/CLAUDE.md            ./
cp prompts/templates/base/
cp prompts/templates/base/CONTEXT.md           prompts/           # preencher {{VARIÁVEIS}}
cp prompts/templates/base/master-tecnico.md    prompts/masters/   # preencher stack
cp prompts/templates/base/master-produto.md prompts/masters/master-produto.md
cp prompts/templates/base/master-roadmap.md    prompts/masters/   # só próximas prioridades
cp prompts/templates/base/master-adrs.md       prompts/masters/   # substituir {{SLUG}} e {{DATA}}
cp prompts/templates/base/adrs/                prompts/adrs/
cp prompts/templates/base/prompts-README.md    prompts/README.md
cp prompts/templates/base/README.md            ./README.md        # preencher {{VARIÁVEIS}}
```

## Saída esperada do assistente

Com base nas entradas preenchidas acima e nos arquivos base copiados, preencher apenas:

1. `prompts/CONTEXT.md` — estado atual mínimo + próximas prioridades
2. `prompts/masters/master-tecnico.md` — stack e arquitetura em tópicos curtos
3. `prompts/masters/master-produto.md` — objetivo e entidades principais
4. `prompts/masters/master-roadmap.md` — só próximas entregas (sem histórico)
5. ADRs informados nas entradas (se houver)

Não fazer: auditoria do código existente, ADRs retroativos não solicitados, análise de dívidas.
```

---

## Nota sobre o código legado

O código anterior continua existindo e funcionando.
A metodologia não exige documentar o passado para funcionar.
À medida que partes do código legado forem tocadas em novas entregas,
documentar naturalmente no master relevante ou em ADR se for decisão estrutural.
