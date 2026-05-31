# Especificacao de Schema dos Pacotes DB-01, DB-02 e DB-03

> Status deste documento: **canônico no seu escopo**.
>
> Este documento fecha o desenho de banco dos pacotes `DB-01`, `DB-02` e
> `DB-03` para os fluxos publicos.
>
> Quando aparecerem nomes historicos de seed, tabela ou coluna, leia-os como
> retrato do schema ou da migration, nao como decisao de ownership superior ao
> consolidado de migracao.

Este documento fecha a especificacao de dados necessaria para sustentar a
regra funcional alvo dos fluxos publicos.

Escopo:

- `DB-01`: politica e exibicao por projeto/cliente do ecossistema;
- `DB-02`: copia congelada (`snapshot`) do projeto e da politica aplicada nos
  fluxos publicos;
- `DB-03`: contexto social pendente para vinculacao assistida no login.

Este documento **nao aplica migration**.

Ele existe para responder, antes de qualquer rollout:

- o que precisa mudar no banco;
- o que ja existe e deve ser reaproveitado;
- o que ainda e gap de contrato HTTP ou de runtime.

Para a ordem concreta das migrations `V30+`, consultar:

- [plano_migrations_v30_v36_db01_db02_db03_local_primeiro.md](plano_migrations_v30_v36_db01_db02_db03_local_primeiro.md)

## Estado atual reaproveitavel

### O que ja existe no modelo multiapp

No desenho multiapp e nas migrations do `identidade-servidor`, ja existem:

- `catalogo.clientes_ecossistema`
  - criada em
    [V15__criar_catalogo_e_usuarios_multiapp.sql](/Users/thiago/Desenvolvedor/flutter/eickrono-identidade-servidor/src/main/resources/db/migration/V15__criar_catalogo_e_usuarios_multiapp.sql:1)
- `autenticacao.cadastros_conta`
  - com `cliente_ecossistema_id`
  - criada em
    [V15__criar_catalogo_e_usuarios_multiapp.sql](/Users/thiago/Desenvolvedor/flutter/eickrono-identidade-servidor/src/main/resources/db/migration/V15__criar_catalogo_e_usuarios_multiapp.sql:73)
- `autenticacao.recuperacoes_senha`
  - com `cliente_ecossistema_id`, hoje ainda anulavel
  - criada em
    [V15__criar_catalogo_e_usuarios_multiapp.sql](/Users/thiago/Desenvolvedor/flutter/eickrono-identidade-servidor/src/main/resources/db/migration/V15__criar_catalogo_e_usuarios_multiapp.sql:100)
- seed inicial do catalogo
  - hoje com `eickrono-thimisu-app`
  - em
    [V18__seed_catalogo_multiapp_inicial.sql](/Users/thiago/Desenvolvedor/flutter/eickrono-identidade-servidor/src/main/resources/db/migration/V18__seed_catalogo_multiapp_inicial.sql:1)

### O que ja existe no runtime legado atual

O runtime atual ainda grava principalmente nas tabelas legadas:

- `cadastros_conta`
- `recuperacoes_senha`

Nelas ja existem partes do contexto que serao reaproveitadas:

- contexto de exibicao e locale/timezone
  - em
    [V29__adicionar_contexto_fluxos_publicos.sql](/Users/thiago/Desenvolvedor/flutter/eickrono-identidade-servidor/src/main/resources/db/migration/V29__adicionar_contexto_fluxos_publicos.sql:1)
- vinculo social pendente acoplado ao cadastro
  - em
    [V22__adicionar_vinculo_social_pendente_ao_cadastro_conta.sql](/Users/thiago/Desenvolvedor/flutter/eickrono-identidade-servidor/src/main/resources/db/migration/V22__adicionar_vinculo_social_pendente_ao_cadastro_conta.sql:1)
- codigo de telefone no cadastro
  - em
    [V23__adicionar_codigo_telefone_ao_cadastro_conta.sql](/Users/thiago/Desenvolvedor/flutter/eickrono-identidade-servidor/src/main/resources/db/migration/V23__adicionar_codigo_telefone_ao_cadastro_conta.sql:1)

### Gaps atuais que impedem a regra alvo

Os gaps mais relevantes hoje sao:

1. a politica `exigeValidacaoTelefone` ainda nao mora no catalogo de projeto;
2. o cadastro e a recuperacao ainda nao persistem explicitamente a copia
   congelada (`snapshot`) da
   politica de telefone;
3. a recuperacao de senha ja recebe `aplicacaoId` e o runtime legado ja resolve
   e persiste `cliente_ecossistema_id`, mas o endurecimento final ainda nao foi
   aplicado no schema;
4. o contexto social pendente do login assistido ainda nao tem tabela propria;
5. o contexto social pendente hoje acoplado a `cadastros_conta` so cobre o
   ramo em que o usuario de fato abriu o cadastro, mas nao cobre bem o ramo de
   `Entrar e vincular` no proprio login.

## DB-01 - Politica e exibicao por projeto/cliente

### Decisao estrutural

`DB-01` **nao cria nova tabela**.

A politica por projeto deve reaproveitar a tabela ja existente
`catalogo.clientes_ecossistema`.

Isso respeita a decisao funcional ja fechada:

- um registro por projeto/cliente;
- nao uma tabela separada para cada projeto.

### Campos ja existentes que devem ser reaproveitados

- `id`
  - identificador interno ja existente do cliente do ecossistema
- `codigo`
  - codigo estavel do projeto/cliente
- `nome`
  - deve continuar servindo como `nomeProjeto`
- `ativo`
  - ja atende a regra de projeto ativo/inativo

### Campos novos obrigatorios em `catalogo.clientes_ecossistema`

```sql
ALTER TABLE catalogo.clientes_ecossistema
    ADD COLUMN IF NOT EXISTS tipo_produto_exibicao VARCHAR(32),
    ADD COLUMN IF NOT EXISTS produto_exibicao VARCHAR(128),
    ADD COLUMN IF NOT EXISTS canal_exibicao VARCHAR(64),
    ADD COLUMN IF NOT EXISTS exige_validacao_telefone BOOLEAN NOT NULL DEFAULT FALSE;
```

Semantica dos campos:

- `tipo_produto_exibicao`
  - texto curto exibivel, por exemplo `app`, `portal`, `site`, `sistema`
- `produto_exibicao`
  - nome do produto exibido ao usuario, por exemplo `Thimisu`
- `canal_exibicao`
  - canal funcional do acesso, por exemplo `mobile`, `web`, `admin`
- `exige_validacao_telefone`
  - `true` quando o projeto exige validacao de telefone
  - `false` quando o telefone segue obrigatorio no preenchimento, mas nao na
    validacao

### O que nao entra em DB-01 nesta fase

`empresa_exibicao` **nao entra** neste pacote.

Motivo:

- ela nao foi escolhida pelo usuario como campo minimo do catalogo;
- hoje ela pode continuar vindo do contexto do request e da copia congelada
  (`snapshot`) do fluxo;
- se no futuro ela precisar virar politica fixa do projeto, isso entra em uma
  fase posterior.

### Estrategia de backfill recomendada

Para nao inventar valores globais errados:

1. adicionar as colunas;
2. preencher explicitamente cada projeto existente;
3. so depois endurecer `NOT NULL` para os campos de exibicao.

Exemplo para o seed atual:

```sql
UPDATE catalogo.clientes_ecossistema
SET tipo_produto_exibicao = 'app',
    produto_exibicao = 'Thimisu',
    canal_exibicao = 'mobile',
    exige_validacao_telefone = FALSE,
    atualizado_em = CURRENT_TIMESTAMP
WHERE codigo = 'eickrono-thimisu-app';
```

### Restricao final recomendada para DB-01

Depois de preencher todos os projetos existentes:

```sql
ALTER TABLE catalogo.clientes_ecossistema
    ALTER COLUMN tipo_produto_exibicao SET NOT NULL,
    ALTER COLUMN produto_exibicao SET NOT NULL,
    ALTER COLUMN canal_exibicao SET NOT NULL;
```

Observacao pratica:

- em `2026-04-29`, as migrations versionadas do repositório seedavam
  explicitamente apenas `eickrono-thimisu-app`;
- qualquer outro projeto ativo do catalogo alem de
  `eickrono-thimisu-app`, quando presente no ambiente, ainda dependia de
  seed complementar para preencher esses campos;
- por isso o endurecimento do catalogo foi separado do endurecimento da
  recuperacao e nao entra na `V36`.

## DB-02 - Snapshot do projeto e da politica no fluxo publico

### Decisao estrutural

`DB-02` nao cria tabela nova.

Ele estende:

- `autenticacao.cadastros_conta`
- `autenticacao.recuperacoes_senha`

E, no runtime transitorio atual, tambem exige colunas equivalentes nas tabelas
legadas:

- `cadastros_conta`
- `recuperacoes_senha`

### Motivacao

O fluxo precisa congelar no inicio da jornada:

- qual projeto foi resolvido;
- se esse projeto exigia ou nao validacao de telefone;
- qual contexto de exibicao estava valendo naquele momento.

Sem copia congelada (`snapshot`), mudar a configuracao do projeto no meio da
jornada pode mudar a regra de um cadastro/recuperacao ja em andamento.

### Campos novos obrigatorios no alvo multiapp

Nas tabelas `autenticacao.cadastros_conta` e
`autenticacao.recuperacoes_senha`, adicionar:

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

### Observacao importante sobre `cliente_ecossistema_id`

No alvo multiapp:

- `autenticacao.cadastros_conta.cliente_ecossistema_id` ja e `NOT NULL`
- `autenticacao.recuperacoes_senha.cliente_ecossistema_id` ainda e anulavel

Para a regra funcional fechada, a recomendacao e:

```sql
ALTER TABLE autenticacao.recuperacoes_senha
    ALTER COLUMN cliente_ecossistema_id SET NOT NULL;
```

Mas isso so pode acontecer **depois** de fechar o gap de contrato abaixo.

### Contrato ja fechado; runtime ainda precisa endurecer a recuperacao

Hoje `IniciarRecuperacaoSenhaApiRequest` ja recebe:

- `aplicacaoId`
- `emailPrincipal`
- contexto de exibicao/locale

Referencias:

- [IniciarRecuperacaoSenhaApiRequest.java](/Users/thiago/Desenvolvedor/flutter/eickrono-identidade-servidor/src/main/java/com/eickrono/api/identidade/apresentacao/dto/fluxo/IniciarRecuperacaoSenhaApiRequest.java:1)
- [RecuperacaoSenhaService.java](/Users/thiago/Desenvolvedor/flutter/eickrono-identidade-servidor/src/main/java/com/eickrono/api/identidade/aplicacao/servico/RecuperacaoSenhaService.java:123)

O que ainda falta antes de endurecer `cliente_ecossistema_id` e a copia
congelada (`snapshot`) da
politica na recuperacao e apenas:

- promover esse comportamento para a etapa de endurecimento de schema
  (`NOT NULL` e validacoes finais);
- validar o runtime completo em banco local antes de escrever a `V36`.

### Runtime transitorio atual

Como o runtime atual ainda grava nas tabelas legadas, o pacote transitorio de
schema deve refletir a mesma intencao nessas tabelas.

As colunas de contexto abaixo **ja existem** no legado por `V29`:

- `locale_solicitante`
- `time_zone_solicitante`
- `tipo_produto_exibicao`
- `produto_exibicao`
- `canal_exibicao`
- `empresa_exibicao`
- `ambiente_exibicao`

Falta adicionar no legado:

```sql
ALTER TABLE cadastros_conta
    ADD COLUMN IF NOT EXISTS cliente_ecossistema_id BIGINT,
    ADD COLUMN IF NOT EXISTS exige_validacao_telefone_snapshot BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE recuperacoes_senha
    ADD COLUMN IF NOT EXISTS cliente_ecossistema_id BIGINT,
    ADD COLUMN IF NOT EXISTS exige_validacao_telefone_snapshot BOOLEAN NOT NULL DEFAULT FALSE;
```

Backfill minimo transitorio:

- enquanto existir so o projeto `eickrono-thimisu-app`, preencher
  `cliente_ecossistema_id` com o `id` correspondente no catalogo;
- ao abrir recuperacao com contrato novo, preencher explicitamente pelo
  `aplicacaoId` recebido;
- depois do backfill e da mudanca contratual, tornar `cliente_ecossistema_id`
  `NOT NULL` tambem no legado.

### O que DB-02 nao tenta resolver

`DB-02` nao define aqui:

- modelo final de timezone IANA vs `UTC±offset`;
- traducao de idioma do e-mail;
- politica de fallback por ultimo contexto conhecido.

Esses pontos continuam de regra de aplicacao e nao de schema puro.

## DB-03 - Contexto social pendente persistido

### Decisao estrutural atualizada

`DB-03` esta **cancelado como alvo ativo**.

O fluxo aprovado nao deve criar tabela nem contrato persistido para contexto
social pendente. A autenticacao social feita antes de cadastro ou antes do login
local fica temporariamente no app. Quando o usuario concluir o cadastro ou a
vinculacao, o app envia dados confirmados em listas:

- `vinculosSociaisConfirmados`;
- `avataresCadastroConfirmados`.

### Regra funcional

- o backend valida a credencial social recebida;
- se a rede ainda nao pode ser vinculada definitivamente, o backend devolve
  estado funcional para o app, sem persistir pendencia social;
- o app guarda o estado temporario somente em memoria/estado local de fluxo;
- a persistencia definitiva acontece apenas junto do cadastro finalizado ou do
  login local confirmado para `Entrar e vincular`.

### Relacao com artefatos legados

Se alguma instalacao ja tiver tabela, migration ou campos com nome de contexto
social pendente, isso deve ser tratado como legado. Fluxos novos nao devem
depender desses artefatos.

Remocao fisica, quando for decidida, deve ocorrer em migration propria de drop,
separada deste documento, para evitar quebra em ambientes que ja aplicaram a
estrutura antiga.

## Ordem recomendada de migrations

### Fase 1 - Catalogo por projeto

1. aplicar `DB-01`
2. popular explicitamente os projetos existentes
3. validar que todo `aplicacaoId` conhecido resolve para um cliente ativo

### Fase 2 - Snapshot do fluxo

1. aplicar `DB-02` no alvo multiapp
2. aplicar a variante transitoria no legado atual
3. ajustar contrato da recuperacao para receber `aplicacaoId`
4. so depois endurecer `cliente_ecossistema_id` em recuperacao

### Fase 3 - Cadastro social confirmado

1. nao criar `DB-03` de contexto social pendente persistido
2. aceitar somente listas confirmadas no contrato publico de cadastro/vinculo
3. gravar vinculos sociais e avatares apenas quando o cadastro ou o vínculo
   local estiver confirmado

## Preparacao de local e hml

### Local

- aplicar migrations primeiro em `local`
- criar ou ajustar registros reais no catalogo para os projetos em teste
- validar `cadastro`, `login`, `recuperacao` e `login social` contra o
  catalogo novo

### HML

- so aplicar migrations depois que:
  - `DB-01` e `DB-02` estiverem aprovados nesta especificacao
  - o contrato de cadastro social confirmado estiver fechado
  - o contrato da recuperacao com `aplicacaoId` estiver fechado
  - os TDDs correspondentes estiverem implementados pelo menos em camada
    minima

Conclusao operacional:

- `sim`, local e `hml` vao precisar de atualizacao de banco para fechar o alvo
  funcional;
- `nao`, ainda nao e momento de aplicar isso em `hml`;
- esta especificacao existe justamente para que a migration seja desenhada
  antes de qualquer rollout.
