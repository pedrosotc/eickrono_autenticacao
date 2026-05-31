# Plano de Migrations V30 a V36 para DB-01, DB-02 e DB-03

Este documento transforma a especificacao de schema dos pacotes `DB-01`,
`DB-02` e `DB-03` em uma sequencia concreta de migrations.

Escopo:

- definir nomes e ordem sugerida das migrations `V30+`;
- separar o que pode ser aplicado cedo em `local`;
- separar o que depende de mudanca de contrato e runtime;
- deixar claro o que **nao** deve ir para `hml` ainda.

Este plano **nao executa** migrations.

## Premissas

- as migrations continuam morando em
  `eickrono-identidade-servidor/src/main/resources/db/migration`;
- a maior migration atual e `V29`;
- as proximas devem ser aditivas primeiro e restritivas depois;
- `hml` continua bloqueado nesta etapa;
- primeiro fecha-se `local`, contrato e testes, depois se fala em rollout.

## Sequencia proposta

### V30 - Estender catalogo de projetos do ecossistema

Arquivo sugerido:

- `V30__estender_catalogo_clientes_ecossistema_fluxos_publicos.sql`

Objetivo:

- materializar `DB-01` em `catalogo.clientes_ecossistema`

Mudancas:

```sql
ALTER TABLE catalogo.clientes_ecossistema
    ADD COLUMN IF NOT EXISTS tipo_produto_exibicao VARCHAR(32),
    ADD COLUMN IF NOT EXISTS produto_exibicao VARCHAR(128),
    ADD COLUMN IF NOT EXISTS canal_exibicao VARCHAR(64),
    ADD COLUMN IF NOT EXISTS exige_validacao_telefone BOOLEAN NOT NULL DEFAULT FALSE;
```

Pode entrar agora em `local`?

- `sim`

Depende de codigo novo?

- `nao`

## V31 - Seed e backfill inicial do catalogo dos projetos

Arquivo sugerido:

- `V31__seed_politica_clientes_ecossistema_fluxos_publicos.sql`

Objetivo:

- preencher os projetos ja existentes com os campos novos de `DB-01`

Minimo esperado:

- atualizar `eickrono-thimisu-app`
- preparar inserts/updates idempotentes para os outros projetos conhecidos,
  quando ja mapeados

Exemplo minimo:

```sql
UPDATE catalogo.clientes_ecossistema
SET tipo_produto_exibicao = 'app',
    produto_exibicao = 'Thimisu',
    canal_exibicao = 'mobile',
    exige_validacao_telefone = FALSE,
    atualizado_em = CURRENT_TIMESTAMP
WHERE codigo = 'eickrono-thimisu-app';
```

Pode entrar agora em `local`?

- `sim`

Depende de codigo novo?

- `nao`

## V32 - Adicionar snapshot de projeto/politica nas tabelas legadas

Arquivo sugerido:

- `V32__adicionar_snapshot_politica_fluxos_publicos_legacy.sql`

Objetivo:

- cobrir o runtime atual, que ainda grava nas tabelas legadas

Tabelas alvo:

- `cadastros_conta`
- `recuperacoes_senha`

Mudancas minimas:

```sql
ALTER TABLE cadastros_conta
    ADD COLUMN IF NOT EXISTS cliente_ecossistema_id BIGINT,
    ADD COLUMN IF NOT EXISTS exige_validacao_telefone_snapshot BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE recuperacoes_senha
    ADD COLUMN IF NOT EXISTS cliente_ecossistema_id BIGINT,
    ADD COLUMN IF NOT EXISTS exige_validacao_telefone_snapshot BOOLEAN NOT NULL DEFAULT FALSE;
```

Indices recomendados:

```sql
CREATE INDEX IF NOT EXISTS idx_cadastros_conta_cliente_ecossistema_id
    ON cadastros_conta (cliente_ecossistema_id);

CREATE INDEX IF NOT EXISTS idx_recuperacoes_senha_cliente_ecossistema_id
    ON recuperacoes_senha (cliente_ecossistema_id);
```

Pode entrar agora em `local`?

- `sim`

Depende de codigo novo?

- `nao`

Observacao:

- esta migration e aditiva;
- ainda nao endurece `NOT NULL`;
- ainda nao faz FK no legado.

## V33 - Adicionar snapshot de projeto/politica no alvo multiapp

Arquivo sugerido:

- `V33__adicionar_snapshot_politica_fluxos_publicos_multiapp.sql`

Objetivo:

- cobrir `autenticacao.cadastros_conta` e `autenticacao.recuperacoes_senha`
  com os campos de `DB-02`

Tabelas alvo:

- `autenticacao.cadastros_conta`
- `autenticacao.recuperacoes_senha`

Mudancas minimas:

```sql
ALTER TABLE autenticacao.cadastros_conta
    ADD COLUMN IF NOT EXISTS locale_solicitante VARCHAR(16),
    ADD COLUMN IF NOT EXISTS time_zone_solicitante VARCHAR(64),
    ADD COLUMN IF NOT EXISTS tipo_produto_exibicao VARCHAR(32),
    ADD COLUMN IF NOT EXISTS produto_exibicao VARCHAR(128),
    ADD COLUMN IF NOT EXISTS canal_exibicao VARCHAR(64),
    ADD COLUMN IF NOT EXISTS empresa_exibicao VARCHAR(128),
    ADD COLUMN IF NOT EXISTS ambiente_exibicao VARCHAR(32),
    ADD COLUMN IF NOT EXISTS exige_validacao_telefone_snapshot BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE autenticacao.recuperacoes_senha
    ADD COLUMN IF NOT EXISTS locale_solicitante VARCHAR(16),
    ADD COLUMN IF NOT EXISTS time_zone_solicitante VARCHAR(64),
    ADD COLUMN IF NOT EXISTS tipo_produto_exibicao VARCHAR(32),
    ADD COLUMN IF NOT EXISTS produto_exibicao VARCHAR(128),
    ADD COLUMN IF NOT EXISTS canal_exibicao VARCHAR(64),
    ADD COLUMN IF NOT EXISTS empresa_exibicao VARCHAR(128),
    ADD COLUMN IF NOT EXISTS ambiente_exibicao VARCHAR(32),
    ADD COLUMN IF NOT EXISTS exige_validacao_telefone_snapshot BOOLEAN NOT NULL DEFAULT FALSE;
```

Pode entrar agora em `local`?

- `sim`

Depende de codigo novo?

- `nao`

Observacao:

- continua sendo aditiva;
- ainda nao endurece `cliente_ecossistema_id` em `autenticacao.recuperacoes_senha`.

## V34 - Contexto social pendente persistido

Status:

- `cancelada como migration alvo`

Motivo:

- o fluxo aprovado nao persiste contexto social pendente no backend;
- dados sociais antes de cadastro/vinculo definitivo ficam temporariamente no
  app;
- o contrato publico usa listas confirmadas:
  - `vinculosSociaisConfirmados`;
  - `avataresCadastroConfirmados`.

Regra operacional:

- nao criar novos testes que dependam de tabela de pendencia social;
- se algum ambiente ja tiver artefato legado equivalente, a remocao fisica deve
  ser planejada em migration futura de drop, com verificacao previa de runtime.

## V35 - Backfill minimo transitorio de cliente e snapshot

Arquivo sugerido:

- `V35__backfill_cliente_ecossistema_e_snapshot_fluxos_publicos.sql`

Objetivo:

- preencher o legado e o alvo multiapp com o projeto default hoje conhecido;
- copiar o valor de `exige_validacao_telefone` para o snapshot em linhas ja
  existentes

Estratégia transitoria:

1. resolver o `id` do projeto `eickrono-thimisu-app`
2. preencher `cliente_ecossistema_id` nulo nas tabelas legadas e multiapp
3. preencher `exige_validacao_telefone_snapshot` a partir do catalogo

Esqueleto:

```sql
WITH cliente_thimisu AS (
    SELECT id, exige_validacao_telefone
    FROM catalogo.clientes_ecossistema
    WHERE codigo = 'eickrono-thimisu-app'
)
UPDATE cadastros_conta
SET cliente_ecossistema_id = cliente.id,
    exige_validacao_telefone_snapshot = cliente.exige_validacao_telefone
FROM cliente_thimisu cliente
WHERE cadastros_conta.cliente_ecossistema_id IS NULL;
```

E repetir a mesma ideia para:

- `recuperacoes_senha`
- `autenticacao.recuperacoes_senha`, quando `cliente_ecossistema_id` ainda
  estiver nulo

Pode entrar agora em `local`?

- `sim`

Depende de codigo novo?

- `nao`

Observacao:

- este backfill e aceitavel enquanto o unico projeto materializado for o
  `thimisu`;
- quando houver multiplos projetos reais em uso, o backfill por default deixa
  de ser suficiente.

## V36 - Endurecimento depois da mudanca de contrato

Arquivo sugerido:

- `V36__endurecer_recuperacao_por_projeto_e_contexto.sql`

Objetivo:

- aplicar os `NOT NULL` e restricoes que so fazem sentido depois que o runtime
  e o contrato novo estiverem em producao/local validada

Precondicoes obrigatorias:

1. `IniciarRecuperacaoSenhaApiRequest` ja recebe `aplicacaoId`
2. o backend ja precisa resolver e persistir `cliente_ecossistema_id` pela
   entrada do request, nao por heuristica de historico
3. os TDDs correspondentes da recuperacao precisam estar verdes

Mudancas aprovadas para esta `V36`:

```sql
ALTER TABLE autenticacao.recuperacoes_senha
    ALTER COLUMN cliente_ecossistema_id SET NOT NULL;

ALTER TABLE recuperacoes_senha
    ALTER COLUMN cliente_ecossistema_id SET NOT NULL;
```

Mudanca adiada para migration posterior, quando todo o catalogo estiver completo:

```sql
ALTER TABLE catalogo.clientes_ecossistema
    ALTER COLUMN tipo_produto_exibicao SET NOT NULL,
    ALTER COLUMN produto_exibicao SET NOT NULL,
    ALTER COLUMN canal_exibicao SET NOT NULL;
```

Observacao do estado local em `2026-04-29`:

- as migrations versionadas seedam explicitamente apenas
  `eickrono-thimisu-app`;
- qualquer outro projeto ativo do catalogo alem de
  `eickrono-thimisu-app`, quando presente no ambiente, ainda depende de seed
  complementar para preencher esses campos de exibicao;
- por isso o endurecimento do catalogo nao entra nesta `V36`;
- ele deve ficar para uma migration posterior, depois de fechar os valores reais
  do projeto.

Template recomendado para seed complementar por projeto:

```sql
INSERT INTO catalogo.clientes_ecossistema (
    codigo,
    nome,
    tipo,
    client_id_oidc,
    ativo,
    criado_em,
    atualizado_em
)
VALUES (
    '#codigo-projeto',
    '#nome-projeto',
    '#tipo-cliente',
    #client-id-oidc-ou-null,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (codigo) DO NOTHING;

UPDATE catalogo.clientes_ecossistema
SET tipo_produto_exibicao = '#tipo-produto-exibicao',
    produto_exibicao = '#produto-exibicao',
    canal_exibicao = '#canal-exibicao',
    exige_validacao_telefone = #true-ou-false,
    atualizado_em = CURRENT_TIMESTAMP
WHERE codigo = '#codigo-projeto';
```

Pode entrar agora em `local`?

- `nao`, nao antes da mudanca de contrato e do runtime

Pode ir para `hml` agora?

- `nao`

## Ordem de aplicacao recomendada em local

### Primeira rodada segura

Aplicar em `local`, sem esperar codigo novo:

1. `V30`
2. `V31`
3. `V32`
4. `V33`
5. `V34`
6. `V35`

Resultado esperado:

- banco preparado para implementar a nova regra;
- sem quebrar o runtime atual;
- sem depender ainda da recuperacao com `aplicacaoId`.

### Segunda rodada, depois do contrato e do runtime

Aplicar em `local` so depois da implementacao:

7. `V36`

## Checklists de validacao local

### Depois de V30 e V31

- `catalogo.clientes_ecossistema` possui os campos novos
- `eickrono-thimisu-app` esta preenchido com:
  - `tipo_produto_exibicao`
  - `produto_exibicao`
  - `canal_exibicao`
  - `exige_validacao_telefone`

### Depois de V32 e V33

- tabelas legadas e multiapp aceitam snapshot sem erro
- nenhuma query do runtime quebra por coluna faltante
- `cadastros_conta` e `recuperacoes_senha` seguem operacionais em `local`

### Depois de V34

- nenhuma tabela nova de contexto social pendente deve existir por causa deste
  plano;
- cadastro/vinculo social deve depender apenas do contrato confirmado em lista;
- qualquer tabela antiga de pendencia social deve ser tratada como legado ate
  uma migration futura de remocao.

### Depois de V35

- `cliente_ecossistema_id` legado deixa de ficar nulo nas linhas antigas
- `exige_validacao_telefone_snapshot` passa a refletir a política default do
  projeto

### Antes de V36

- recuperação já recebe `aplicacaoId`
- `cliente_ecossistema_id` novo já é resolvido e persistido pelo `aplicacaoId`
- TDD de recuperação por projeto está verde

## O que ainda nao deve ser feito

- nao aplicar nenhuma dessas migrations em `hml`
- nao endurecer `NOT NULL` na recuperacao antes da mudanca de contrato
- nao apagar os campos legados de contexto social em `cadastros_conta`
- nao remover heuristicas antigas antes de a nova persistencia estar validada

## Relacao com os outros documentos

- schema alvo e justificativa:
  [especificacao_schema_db01_db02_db03_fluxos_publicos.md](especificacao_schema_db01_db02_db03_fluxos_publicos.md)
- ownership e dependencia por TDD:
  [mapeamento_tdd_componentes_migracoes_fluxos_publicos.md](mapeamento_tdd_componentes_migracoes_fluxos_publicos.md)
- regra funcional alvo:
  [fluxogramas_fluxos_publicos_regra_funcional_em_fechamento.md](fluxogramas_fluxos_publicos_regra_funcional_em_fechamento.md)
