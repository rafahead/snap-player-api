-- Seeds the phase-1 default tenant and its default subject template.
-- `where not exists` keeps migrations idempotent across local resets/test runs.

insert into assinatura (codigo, nome, api_token, status, created_at)
select 'default', 'Default', 'dev-default-token', 'ACTIVE', current_timestamp
where not exists (select 1 from assinatura where codigo = 'default');

-- Seed the default template expected by Entrega 1 and ADR-0004 fallback rules.
insert into subject_template (
    assinatura_id,
    nome,
    slug,
    ativo,
    schema_json,
    is_default,
    created_at,
    updated_at
)
select
    a.id,
    'Template Default MVP',
    'default',
    true,
    '{
      "fields": [
        { "key": "id", "type": "string", "required": false },
        { "key": "observacoes", "type": "string", "required": false }
      ]
    }',
    true,
    current_timestamp,
    current_timestamp
from assinatura a
where a.codigo = 'default'
  and not exists (
      select 1
      from subject_template t
      where t.assinatura_id = a.id
        and t.slug = 'default'
  );
