# Howto — Metodologia de Planos e Prompts (docs-as-code)

## O que é esta metodologia

Organização de documentação viva dentro do próprio repositório, em `prompts/`,
separando produto, técnico, execução e decisões arquiteturais.
Serve como memória permanente do projeto para humanos e para IAs (Claude, Codex).

---

## Estrutura padrão de arquivos

```
prompts/
  README.md                        # governança: mapa, regras, workflows, exemplos
  masters/
    CONTEXT.md                     # ponto de entrada da sessão — leia primeiro
    CONTEXT-CONFIG.md              # regras de formato de resposta da IA
    master-tecnico.md              # arquitetura, stack, processamento, infra
    master-produto-<slug>.md       # domínio, entidades, regras de negócio, API conceitual
    master-roadmap.md              # sequenciamento de entregas, slices, critérios de aceite
    master-adrs.md                 # governança de ADRs: critérios, template, índice
    master-monetizacao.md          # (opcional) estratégia comercial, GTM, pricing
  adrs/
    README.md                      # índice dos ADRs
    0000-template.md               # template padrão
    000N-<slug-da-decisao>.md      # ADRs reais
  templates/
    howto-metodologia.md           # este arquivo
    template-app.md                # prompt de bootstrap para novos projetos
  estudos/                         # (opcional) experimentos, integrações, estudos pontuais
```

---

## Como iniciar um novo projeto

### Passo 1 — Criar estrutura de diretórios

```bash
mkdir -p prompts/masters prompts/adrs prompts/templates prompts/estudos
```

### Passo 2 — Criar CONTEXT-CONFIG.md (conteúdo fixo — copiar sempre igual)

```
prompts/masters/CONTEXT-CONFIG.md
```

Conteúdo:
```
Utilize essas configurações de contexto:
- gatilho preferencial para economia de tokens: `modo silencioso`
- `modo silencioso` = todos os itens abaixo (prioridade máxima de formato)
- sem updates intermediários (incluindo andamento/progresso), salvo bloqueio crítico
- só resposta final em 1 linha (esta regra tem prioridade sobre qualquer outra de formatação)
- só o resultado, sem explicação
- sem bullets/listas (exceto se eu pedir explicitamente)
- não mostre alterações/exclusões/adições de código, texto ou arquivos
- não mostre comando nem passo a passo
- não mostre output "explored"
- não mostre logs, stacktrace, stdout/stderr, output de testes ou output de ferramentas
- não repita trechos de arquivos lidos; use apenas o resumo final
- se precisar, pergunte antes de detalhar
- quando terminar um tópico, só apresente o resultado final da entrega
- em caso de erro, responda apenas o bloqueio em 1 linha (sem logs)
- você tem autorização para rodar comandos
- você tem acesso ao código-fonte do projeto
- você tem acesso a arquivos de configuração e documentação
- você tem acesso a arquivos de planejamento e estratégia
- você tem acesso ao diretório raiz do projeto, incluindo subdiretórios
```

### Passo 3 — Criar CONTEXT.md

Preencher com:
- nome e objetivo do projeto
- stack (usar defaults abaixo se aplicável)
- estado das entregas (inicialmente vazio)
- próximas prioridades
- arquivos de referência (tabela)
- governança dos planos

> Usar o template em `prompts/templates/template-app.md` → seção "Saída esperada" como guia.

### Passo 4 — Criar master-tecnico.md

Seções mínimas:
- Stack (linguagem, framework, banco, storage, deploy)
- Arquitetura (componentes e responsabilidades)
- Processamento (fluxo principal de dados)
- Persistência (modelo de banco, migrations)
- Integrações externas
- Segurança (autenticação, validações, SSRF, etc.)
- Observabilidade (logs, health check, métricas)
- Limites operacionais (memória, CPU, tamanho de arquivo, timeout)

**Defaults para o stack padrão:**
```
Stack:
- Java 17 + Spring Boot 3.x
- PostgreSQL + Flyway (migrations versionadas)
- Linode Object Storage — S3-compatível (AWS SDK v2)
- Flutter web (cliente)
- Deploy: serviço Linux via systemd (sem Docker na fase inicial)
- Servidor: Linode (2GB RAM referência)

Processamento:
- ProcessBuilder (nunca bash -c)
- SSRF bloqueado em URLs externas

Observabilidade:
- spring-boot-starter-actuator (/actuator/health, /actuator/metrics)
- X-Request-Id nos logs (MDC)

Deploy (Linux service):
- build: mvn package -q -DskipTests
- artefato: target/<app>-<version>.jar
- serviço: /etc/systemd/system/<app>.service
- reinício automático: Restart=on-failure
- variáveis de ambiente: /etc/default/<app> ou EnvironmentFile= no unit
```

### Passo 5 — Criar master-produto-<slug>.md

Seções mínimas:
- Objetivo do produto
- Entidades principais (domínio)
- Regras de negócio
- Fluxos principais
- API conceitual (endpoints e comportamento esperado)
- Permissões e visibilidade
- Multi-tenant (se aplicável)

### Passo 6 — Criar master-roadmap.md

Seções mínimas:
- Estado das entregas (inicialmente: "Entrega 1 — EM ANDAMENTO")
- Entrega 1 com slices e critérios de aceite
- Próximas prioridades

Formato de slice:
```markdown
### Slice N — <descrição>
- B1: <correção bloqueante ou item obrigatório>
- B2: ...
- I1: <melhoria ou hardening>
- I2: ...
```

### Passo 7 — Criar master-adrs.md

Copiar de `snap-player-api/prompts/masters/master-adrs.md` — o conteúdo de governança
(critérios, template, ciclo de vida, fluxo) é reaproveitável em qualquer projeto.
Zerar o índice (manter só o cabeçalho da tabela).

### Passo 8 — Criar adrs/0000-template.md e adrs/README.md

Copiar de `snap-player-api/prompts/adrs/`.

### Passo 9 — Criar prompts/README.md

Copiar de `snap-player-api/prompts/README.md` e adaptar:
- atualizar "Mapa dos arquivos"
- atualizar "Estado consolidado"
- manter workflows e exemplos (são genéricos)

### Passo 10 — Registrar ADRs iniciais

Decisões já claras antes de escrever código → registrar imediatamente.
Exemplos típicos para o stack padrão:
- entidade principal do domínio
- arquitetura síncrona ou assíncrona na fase 1
- isolamento multi-tenant (se aplicável)
- estratégia de storage (local na fase 1 → S3 na fase 2)

---

## Como adotar em um projeto existente

### Passo 1 — Auditar o que existe

Verificar se há algum `README`, `docs/`, `wiki` ou comentários com decisões de arquitetura.

### Passo 2 — Criar a estrutura `prompts/`

Seguir passos 1-9 acima, mas preenchendo os masters com base no código existente
(não inventar — refletir o que já está implementado).

### Passo 3 — Registrar decisões já tomadas como ADRs

Olhar o código e identificar decisões estruturais que já existem:
- por que esta entidade é a principal?
- por que este banco / esta estratégia de fila / este modelo de tenancy?
- marcar como `Aceito` (decisão já executada)

### Passo 4 — Definir estado atual em master-roadmap.md

- marcar entregas já concluídas
- mapear o que está em andamento
- listar próximas prioridades

### Passo 5 — Manter daqui em diante

A partir deste ponto, seguir o workflow normal (seção abaixo).

---

## Uso no dia a dia

### Início de sessão com IA

1. Abrir o projeto
2. Enviar ao Claude/Codex: `carregar prompts/masters/CONTEXT.md`
3. A partir daí, o modo silencioso e o contexto do projeto estão ativos

### Quando surgir qualquer mudança

Consultar o workflow em `prompts/README.md` → seção "Workflow recomendado por mudança".

Regra rápida:

| Situação | Ação |
|---|---|
| Bug pontual óbvio | Corrige direto no código |
| Bug complexo (múltiplos arquivos, risco regressão) | Adiciona slice em `master-roadmap.md` |
| Bug que revela decisão estrutural | master relevante → ADR → slice → código |
| Nova feature | master-produto → master-roadmap → (ADR se estrutural) → código |
| Mudança de infra/processamento | master-tecnico → master-roadmap → (ADR se estrutural) → código |
| Mudança de prazo/escopo | Só `master-roadmap.md` |

### Onde adicionar o slice (para bugs e features com plano)

- Se há entrega em andamento → adiciona slice nela
- Se não há entrega aberta → cria nova entrega:

```markdown
## Entrega N — <tema> (ex.: Correções pós-produção)

### Slice 1 — <descrição>
- B1: ...
- B2: ...
```

### Quando criar ADR

Criar quando satisfizer **2 ou mais** dos critérios:
1. Há alternativa real descartada
2. Impacta schema, contrato de API ou infraestrutura
3. Um dev novo reverteria sem entender o porquê
4. Tem trade-offs relevantes que devem ser explícitos

Não criar ADR para: typos, correções óbvias, detalhes operacionais pontuais.

---

## Checklist de qualidade (novo projeto ou revisão periódica)

- [ ] `CONTEXT.md` reflete o estado atual real?
- [ ] `master-roadmap.md` tem as próximas prioridades definidas?
- [ ] Decisões estruturais recentes viraram ADR?
- [ ] Referências entre arquivos estão corretas (sem paths quebrados)?
- [ ] `http/*.http` está atualizado com novos endpoints?
- [ ] `CONTEXT-CONFIG.md` está presente e copiado corretamente?

---

*Metodologia aplicada em: snap-player-api (fevereiro 2026).*
