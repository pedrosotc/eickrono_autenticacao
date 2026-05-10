-- Backfill inicial do modelo multiapp:
-- - usuario_id e pessoa_id sao UUIDs transitorios, mas deterministicas;
-- - a fonte principal e o sub legado em pessoas_identidade;
-- - o mesmo algoritmo deve ser reutilizado quando o schema identidade for materializado.

WITH pessoas_base AS (
    SELECT pessoa.id AS legacy_pessoa_id,
           pessoa.sub AS sub_remoto,
           pessoa.email AS email_principal,
           pessoa.atualizado_em,
           (
               SUBSTRING(MD5('autenticacao.usuario:' || pessoa.sub) FROM 1 FOR 8) || '-' ||
               SUBSTRING(MD5('autenticacao.usuario:' || pessoa.sub) FROM 9 FOR 4) || '-' ||
               SUBSTRING(MD5('autenticacao.usuario:' || pessoa.sub) FROM 13 FOR 4) || '-' ||
               SUBSTRING(MD5('autenticacao.usuario:' || pessoa.sub) FROM 17 FOR 4) || '-' ||
               SUBSTRING(MD5('autenticacao.usuario:' || pessoa.sub) FROM 21 FOR 12)
           )::UUID AS usuario_id,
           (
               SUBSTRING(MD5('identidade.pessoa:' || pessoa.sub) FROM 1 FOR 8) || '-' ||
               SUBSTRING(MD5('identidade.pessoa:' || pessoa.sub) FROM 9 FOR 4) || '-' ||
               SUBSTRING(MD5('identidade.pessoa:' || pessoa.sub) FROM 13 FOR 4) || '-' ||
               SUBSTRING(MD5('identidade.pessoa:' || pessoa.sub) FROM 17 FOR 4) || '-' ||
               SUBSTRING(MD5('identidade.pessoa:' || pessoa.sub) FROM 21 FOR 12)
           )::UUID AS pessoa_id
    FROM pessoas_identidade pessoa
    WHERE pessoa.sub IS NOT NULL
      AND BTRIM(pessoa.sub) <> ''
)
INSERT INTO autenticacao.usuarios (
    id,
    pessoa_id,
    sub_remoto,
    status_global,
    credencial_local_habilitada,
    ultimo_login_em,
    criado_em,
    atualizado_em
)
SELECT base.usuario_id,
       base.pessoa_id,
       base.sub_remoto,
       'ATIVO',
       TRUE,
       NULL,
       COALESCE(base.atualizado_em, CURRENT_TIMESTAMP),
       COALESCE(base.atualizado_em, CURRENT_TIMESTAMP)
FROM pessoas_base base
ON CONFLICT (sub_remoto) DO NOTHING;

WITH pessoas_base AS (
    SELECT pessoa.id AS legacy_pessoa_id,
           (
               SUBSTRING(MD5('autenticacao.usuario:' || pessoa.sub) FROM 1 FOR 8) || '-' ||
               SUBSTRING(MD5('autenticacao.usuario:' || pessoa.sub) FROM 9 FOR 4) || '-' ||
               SUBSTRING(MD5('autenticacao.usuario:' || pessoa.sub) FROM 13 FOR 4) || '-' ||
               SUBSTRING(MD5('autenticacao.usuario:' || pessoa.sub) FROM 17 FOR 4) || '-' ||
               SUBSTRING(MD5('autenticacao.usuario:' || pessoa.sub) FROM 21 FOR 12)
           )::UUID AS usuario_id
    FROM pessoas_identidade pessoa
    WHERE pessoa.sub IS NOT NULL
      AND BTRIM(pessoa.sub) <> ''
),
formas_base AS (
    SELECT (
               SUBSTRING(MD5('autenticacao.forma_acesso:' || forma.id::TEXT) FROM 1 FOR 8) || '-' ||
               SUBSTRING(MD5('autenticacao.forma_acesso:' || forma.id::TEXT) FROM 9 FOR 4) || '-' ||
               SUBSTRING(MD5('autenticacao.forma_acesso:' || forma.id::TEXT) FROM 13 FOR 4) || '-' ||
               SUBSTRING(MD5('autenticacao.forma_acesso:' || forma.id::TEXT) FROM 17 FOR 4) || '-' ||
               SUBSTRING(MD5('autenticacao.forma_acesso:' || forma.id::TEXT) FROM 21 FOR 12)
           )::UUID AS forma_acesso_id,
           pessoa.usuario_id,
           forma.tipo,
           forma.provedor,
           forma.identificador,
           forma.principal,
           forma.verificado_em,
           forma.criado_em
    FROM pessoas_formas_acesso forma
    JOIN pessoas_base pessoa
      ON pessoa.legacy_pessoa_id = forma.pessoa_id
)
INSERT INTO autenticacao.usuarios_formas_acesso (
    id,
    usuario_id,
    email_id,
    tipo,
    provedor,
    identificador_externo,
    principal,
    verificado_em,
    vinculado_em,
    desvinculado_em
)
SELECT base.forma_acesso_id,
       base.usuario_id,
       NULL,
       base.tipo,
       base.provedor,
       base.identificador,
       base.principal,
       base.verificado_em,
       COALESCE(base.criado_em, CURRENT_TIMESTAMP),
       NULL
FROM formas_base base
ON CONFLICT (tipo, provedor, identificador_externo) DO NOTHING;

WITH pessoas_base AS (
    SELECT pessoa.sub AS sub_remoto,
           pessoa.atualizado_em,
           (
               SUBSTRING(MD5('autenticacao.usuario:' || pessoa.sub) FROM 1 FOR 8) || '-' ||
               SUBSTRING(MD5('autenticacao.usuario:' || pessoa.sub) FROM 9 FOR 4) || '-' ||
               SUBSTRING(MD5('autenticacao.usuario:' || pessoa.sub) FROM 13 FOR 4) || '-' ||
               SUBSTRING(MD5('autenticacao.usuario:' || pessoa.sub) FROM 17 FOR 4) || '-' ||
               SUBSTRING(MD5('autenticacao.usuario:' || pessoa.sub) FROM 21 FOR 12)
           )::UUID AS usuario_id
    FROM pessoas_identidade pessoa
    WHERE pessoa.sub IS NOT NULL
      AND BTRIM(pessoa.sub) <> ''
),
cliente_thimisu AS (
    SELECT cliente.id AS cliente_ecossistema_id
    FROM catalogo.clientes_ecossistema cliente
    WHERE cliente.codigo = 'eickrono-thimisu-app'
),
vinculos_base AS (
    SELECT (
               SUBSTRING(MD5('autenticacao.vinculo_cliente:' || pessoa.sub_remoto || ':eickrono-thimisu-app') FROM 1 FOR 8) || '-' ||
               SUBSTRING(MD5('autenticacao.vinculo_cliente:' || pessoa.sub_remoto || ':eickrono-thimisu-app') FROM 9 FOR 4) || '-' ||
               SUBSTRING(MD5('autenticacao.vinculo_cliente:' || pessoa.sub_remoto || ':eickrono-thimisu-app') FROM 13 FOR 4) || '-' ||
               SUBSTRING(MD5('autenticacao.vinculo_cliente:' || pessoa.sub_remoto || ':eickrono-thimisu-app') FROM 17 FOR 4) || '-' ||
               SUBSTRING(MD5('autenticacao.vinculo_cliente:' || pessoa.sub_remoto || ':eickrono-thimisu-app') FROM 21 FOR 12)
           )::UUID AS vinculo_id,
           pessoa.usuario_id,
           cliente.cliente_ecossistema_id,
           pessoa.atualizado_em
    FROM pessoas_base pessoa
    CROSS JOIN cliente_thimisu cliente
)
INSERT INTO autenticacao.usuarios_clientes_ecossistema (
    id,
    usuario_id,
    cliente_ecossistema_id,
    status_vinculo,
    identificador_publico_cliente,
    ultimo_acesso_em,
    vinculado_em,
    atualizado_em,
    revogado_em,
    motivo_revogacao
)
SELECT base.vinculo_id,
       base.usuario_id,
       base.cliente_ecossistema_id,
       'ATIVO',
       NULL,
       NULL,
       COALESCE(base.atualizado_em, CURRENT_TIMESTAMP),
       COALESCE(base.atualizado_em, CURRENT_TIMESTAMP),
       NULL,
       NULL
FROM vinculos_base base
ON CONFLICT (usuario_id, cliente_ecossistema_id) DO NOTHING;
