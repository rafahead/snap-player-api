# ADR 0003 - Single Domain com Isolamento Lógico por Assinatura

## Status

Aceito

## Contexto

O sistema será operado em um único domínio/serviço, mas precisa suportar múltiplas empresas (assinaturas) sem misturar dados.

Uma mesma URL de vídeo pode ser usada em empresas diferentes.
Se a identidade do vídeo fosse global apenas por URL, haveria risco de mistura de snaps e metadados entre empresas.

## Decisão

Adotar arquitetura **single domain** com **isolamento lógico por `assinatura_id`** em toda modelagem e consulta.

Regras principais:
- contexto de assinatura explícito em toda query
- vídeos identificados/reutilizados por escopo de assinatura (ex.: `assinatura_id + url_hash`)
- mesma URL em assinaturas diferentes gera registros distintos

## Consequências

### Positivas

- Suporta multi-tenant sem complexidade de subdomínios.
- Evita mistura de dados entre empresas.
- Mantém possibilidade de evolução para autenticação/token por assinatura.

### Trade-offs / Custos

- Exige disciplina em filtros (`WHERE assinatura_id = ...`) em todas as queries.
- Índices e constraints precisam sempre considerar `assinatura_id`.

## Relação com planos

- Domínio/produto em `prompts/masters/master-produto-snap.md`
- Sequenciamento em `prompts/entregas/entregas-api-snap-v2.md`
