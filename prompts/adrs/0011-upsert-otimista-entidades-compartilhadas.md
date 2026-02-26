# ADR 0011 — Upsert Otimista para Entidades Compartilhadas (Video, Usuario)

## Status

Aceito

## Contexto

`Video` e `Usuario` são entidades compartilhadas entre snaps de um mesmo tenant.
O fluxo de criação de snap sempre resolve/cria essas entidades antes de persistir o snap.

Problema de concorrência: com múltiplos requests paralelos do mesmo operador
(mesmo email ou mesma URL de vídeo), a sequência `findBy → save` tem janela de
race condition. O índice único no banco previne duplicata, mas a exceção
resultante (`DataIntegrityViolationException`) não estava tratada — escapava como 500.

Alternativas consideradas:
1. `SELECT FOR UPDATE` na busca (lock pessimista) — overhead para o caso comum
2. Mutex/synchronized no service — não escala para múltiplas instâncias
3. Upsert otimista: tentar salvar; em conflito, re-buscar o registro existente → **escolhida**

## Decisão

Padrão "upsert otimista" para `resolveOrCreateVideo` e `resolveUsuario`:

```
try {
    return repository.save(newEntity);
} catch (DataIntegrityViolationException e) {
    return repository.findByUniqueKey(...).orElseThrow();
}
```

Para `Usuario`, a decisão adicional é **"última escrita vence"** para o campo
`nickname`: campo de exibição sem implicação de segurança; vencer a race condition
é preferível a travar o sistema. Documentado em JavaDoc da classe.

## Consequências

### Positivas

- O caso comum (sem concorrência) tem custo zero de lock
- 500 inesperado eliminado — race condition resulta em 200/201 normal
- Comportamento determinístico e auditável

### Trade-offs / Custos

- Nickname pode ser sobrescrito por request concorrente com email igual
  (aceito — `usuario` é identidade de display, sem implicação de auth)
- Exige índice único nas colunas de deduplicação (`uk_video_ass_urlhash`, `uk_usuario_email`)
- `DataIntegrityViolationException` deve ser capturada apenas no nível do upsert,
  não globalmente, para não mascarar outros erros de integridade

## Relação com planos

- `prompts/masters/master-tecnico.md`
- `prompts/masters/master-produto-snap.md`
- `prompts/masters/master-roadmap.md` (Entrega 4, Slice 5)
