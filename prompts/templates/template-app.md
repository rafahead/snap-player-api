# Template de Inicialização de App (Prompt Padrão)

## Papel deste arquivo

Prompt padrão para iniciar **qualquer novo produto/app** usando a mesma metodologia aplicada neste projeto:
- documentação viva (`docs-as-code`)
- separação entre produto, técnico e execução
- ADRs para decisões estruturais
- governança clara de atualização dos planos

Use este arquivo **antes** de discutir funcionalidades específicas do produto.

## Objetivo

Criar a base de planejamento do projeto em `prompts/`, com estrutura, conceitos e regras de manutenção que evitem:
- mistura de domínio com implementação
- retrabalho por decisão não registrada
- plano de entregas divergente dos masters
- crescimento caótico da documentação

## Resultado esperado (saída do bootstrap)

Ao usar este prompt, o Codex/assistente deve produzir (ou propor) a estrutura inicial:

- `prompts/README.md`
  - governança dos planos
  - mapa dos arquivos
  - regra de atualização
  - convenções de nomenclatura

- `prompts/master-produto-<slug>.md`
  - domínio
  - regras de negócio
  - fluxos do produto
  - API conceitual (se aplicável)

- `prompts/masters/master-tecnico.md`
  - arquitetura
  - componentes
  - decisões técnicas-base
  - contratos técnicos e restrições

- `prompts/entregas-<slug>-v1.md`
  - plano tático por entregas
  - recortes de escopo
  - critérios de aceite
  - ordem de implementação

- `prompts/adrs/0000-template.md`
  - template padrão de ADR

- `prompts/adrs/0001-<decisao-inicial>.md` (opcional)
  - primeira(s) decisão(ões) estruturais já estabilizadas

Arquivos opcionais (quando fizer sentido):
- `prompts/estudos/mvp-tecnico.md`
- `prompts/<integracao-cliente>.md`
- `prompts/<estudo-ou-experimento>.md`

## Conceitos obrigatórios da organização (metodologia)

### 1. Fontes de verdade (Master Plans)

Separar explicitamente:
- **Master Produto**: domínio, entidades, regras, fluxos, política de visibilidade/permissão, API conceitual
- **Master Técnico**: arquitetura, processamento, persistência, integração, segurança, limites operacionais

Regra:
- mudanças conceituais devem entrar primeiro nos masters

### 2. Plano de Entregas (execução tática)

O plano de entregas **não define o produto**; ele sequencia a implementação.

Deve conter:
- escopo por entrega
- simplificações temporárias
- critérios de aceite
- riscos e dependências

### 3. ADRs (decisões estruturais)

Usar ADR quando a decisão:
- muda a direção do projeto
- impacta vários módulos
- precisa ser lembrada no futuro

Evitar usar ADR para:
- detalhes operacionais de curto prazo
- tarefas de entrega

### 4. Docs-as-code

Os prompts/planos são parte do projeto e devem ser:
- versionados
- revisáveis
- atualizados junto com mudanças relevantes
- coerentes com o código

### 5. Governança de atualização (ordem)

Quando surgir mudança:

1. atualizar `master-produto` e/ou `master-tecnico`
2. atualizar `entregas-*`
3. registrar ADR (se estrutural)
4. só então implementar código

## Estrutura padrão recomendada (`prompts/`)

```text
prompts/
  README.md                         # governança da documentação/plano
  master-tecnico.md                 # fonte de verdade técnica
  master-produto-<slug>.md          # fonte de verdade de produto/domínio
  entregas-<slug>-v1.md             # execução por entregas (fase atual)
  mvp-tecnico.md                    # (opcional) validação técnica inicial
  <integracao-cliente>.md           # (opcional) plano de integração
  <estudo-ou-experimento>.md        # (opcional) estudo separado
  adrs/
    README.md                       # índice/guia dos ADRs (opcional mas recomendado)
    0000-template.md                # template de ADR
    0001-<decisao>.md               # ADRs reais
```

## Convenções de nomenclatura (recomendadas)

- `master-tecnico.md`
- `master-produto-<slug>.md`
- `entregas-<slug>-vN.md`
- `mvp-tecnico.md`
- `adrs/000X-<slug-da-decisao>.md`

Onde `<slug>`:
- curto
- descritivo
- estável (evitar renomear)

## Sequência de bootstrap (plano de inicialização)

### Etapa 1. Contexto e limites

Levantar o mínimo necessário:
- problema
- público/usuário
- fluxo principal
- plataforma(s)
- restrições (prazo, time, infra, compliance)

### Etapa 2. Organização dos prompts

Criar/atualizar `prompts/README.md` com:
- mapa dos arquivos
- papéis de cada arquivo
- regra de atualização
- matriz de responsabilidade

### Etapa 3. Master Produto

Registrar:
- objetivo do produto
- conceitos de domínio (entidades principais)
- regras de negócio
- fluxos principais
- permissões/visibilidade
- contratos conceituais

### Etapa 4. Master Técnico

Registrar:
- arquitetura alvo
- componentes e responsabilidades
- persistência
- integrações
- segurança
- escalabilidade
- observabilidade
- limites operacionais

### Etapa 5. Plano de Entregas v1

Definir:
- baseline da fase atual (simplificações)
- entregas priorizadas
- escopo técnico/funcional por entrega
- critérios de aceite
- riscos e próximos passos

### Etapa 6. ADRs iniciais

Registrar decisões já claras e estruturais, por exemplo:
- arquitetura síncrona vs assíncrona na fase inicial
- isolamento multi-tenant
- entidade principal do domínio
- estratégia de identidade/autenticação inicial

### Etapa 7. Pendências abertas

Listar:
- dúvidas de produto
- decisões técnicas pendentes
- hipóteses assumidas
- validações necessárias (MVP/prova de conceito)

## Checklist de qualidade do bootstrap

- Existe separação explícita entre produto e técnico?
- O plano de entregas referencia os masters como fonte de verdade?
- As simplificações temporárias estão registradas?
- As decisões estruturais já estáveis viraram ADR?
- O `prompts/README.md` explica como manter a organização?
- Há hipóteses e dúvidas abertas claramente listadas?

## Prompt padrão (copiar e usar)

> Use este bloco no início de um novo projeto/app. Preencha os campos e envie ao Codex/assistente.

```md
# Bootstrap de Novo App/Produto (metodologia de prompts + docs-as-code)

Quero iniciar um novo projeto seguindo a mesma metodologia de organização de prompts/planos:
- `prompts/README.md` como governança
- `master-produto` (domínio/regras)
- `master-tecnico` (arquitetura)
- `entregas` (execução tática)
- `adrs/` (decisões estruturais)

Sua tarefa é criar o plano de inicialização e a estrutura de documentação em `prompts/`, antes de discutir features detalhadas.

## Regras de trabalho

- Não misturar regras de produto com tarefas de entrega.
- Tratar os arquivos em `prompts/` como documentação viva (`docs-as-code`).
- Registrar hipóteses quando faltar informação.
- Se faltar contexto crítico, listar perguntas objetivas (mínimas) antes de avançar.
- Priorizar clareza de conceitos e organização.

## Entradas do projeto (preencher)

### 1. Identidade
- Nome do app/produto:
- Slug do projeto (para nomes de arquivo):
- Tipo (API, web app, mobile app, backend, integração, plataforma):

### 2. Problema e objetivo
- Problema que resolve:
- Objetivo principal da v1:
- Resultado de negócio esperado:

### 3. Usuários e contexto
- Usuário(s) principais:
- Quem opera/administra:
- Contexto de uso (interno, B2B, B2C, campo, escritório, etc.):

### 4. Fluxos principais
- Fluxo principal #1:
- Fluxo principal #2:
- Fluxo principal #3 (opcional):

### 5. Regras de negócio iniciais (se já existirem)
- Regra:
- Regra:

### 6. Plataformas / interfaces
- API:
- Web:
- Mobile:
- Integrações externas:

### 7. Restrições
- Prazo:
- Tamanho do time:
- Infra disponível:
- Restrições legais/compliance:
- Restrições operacionais:

### 8. Direção técnica inicial (se já existir)
- Stack preferida:
- Banco:
- Hospedagem:
- Arquitetura desejada (monolito/modular/microserviços):
- Processamento síncrono/assíncrono:

### 9. Escopo inicial e não-escopo
- Deve entrar na v1:
- Pode ficar para fase 2:
- Fora de escopo por agora:

### 10. Dúvidas abertas
- Dúvida:
- Dúvida:

## Saída esperada do assistente

1. Propor a estrutura de `prompts/` (com nomes de arquivos).
2. Criar/rascunhar `prompts/README.md` com governança e conceitos de organização.
3. Criar `master-produto-<slug>.md` (rascunho inicial).
4. Criar `master-tecnico.md` (rascunho inicial).
5. Criar `entregas-<slug>-v1.md` com entregas e critérios de aceite.
6. Criar `adrs/0000-template.md`.
7. Sugerir ADRs iniciais (se aplicável).
8. Listar hipóteses e perguntas pendentes.
```

## Observação de uso

Este é um **prompt de partida**, não um contrato rígido. Ajuste a estrutura quando o tipo de projeto exigir (ex.: app puramente frontend, integração simples, PoC curta), mas preserve a separação entre:
- conceito de produto
- conceito técnico
- execução por entregas
- decisões estruturais
