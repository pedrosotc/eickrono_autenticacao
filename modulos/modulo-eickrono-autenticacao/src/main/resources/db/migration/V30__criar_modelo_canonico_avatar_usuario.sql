CREATE SCHEMA IF NOT EXISTS identidade;

CREATE TABLE IF NOT EXISTS identidade.avatar_origens (
    id BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(64) NOT NULL UNIQUE,
    nome VARCHAR(128) NOT NULL,
    tipo VARCHAR(32) NOT NULL,
    cliente_ecossistema_id BIGINT REFERENCES catalogo.clientes_ecossistema (id),
    permite_vinculo_social BOOLEAN NOT NULL DEFAULT FALSE,
    permite_upload_usuario BOOLEAN NOT NULL DEFAULT FALSE,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS identidade.avatar_usuario (
    id UUID PRIMARY KEY,
    usuario_cliente_id UUID NOT NULL REFERENCES autenticacao.usuarios_clientes_ecossistema (id) ON DELETE CASCADE,
    origem_id BIGINT NOT NULL REFERENCES identidade.avatar_origens (id),
    forma_acesso_id UUID REFERENCES autenticacao.usuarios_formas_acesso (id),
    nome_exibicao_externo VARCHAR(255),
    url_avatar VARCHAR(2048) NOT NULL,
    storage_key VARCHAR(1024),
    content_type VARCHAR(128),
    tamanho_bytes BIGINT,
    hash_conteudo VARCHAR(128),
    versao VARCHAR(128),
    preferido BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    removido_em TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_avatar_usuario_preferido_ativo
    ON identidade.avatar_usuario (usuario_cliente_id)
    WHERE preferido IS TRUE
      AND removido_em IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_avatar_usuario_forma_origem
    ON identidade.avatar_usuario (usuario_cliente_id, origem_id, forma_acesso_id)
    WHERE forma_acesso_id IS NOT NULL
      AND removido_em IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_avatar_usuario_storage_origem
    ON identidade.avatar_usuario (usuario_cliente_id, origem_id, storage_key)
    WHERE storage_key IS NOT NULL
      AND removido_em IS NULL;

CREATE TABLE IF NOT EXISTS autenticacao.cadastros_conta_avatares (
    id UUID PRIMARY KEY,
    cadastro_id UUID NOT NULL REFERENCES autenticacao.cadastros_conta (id) ON DELETE CASCADE,
    origem_id BIGINT NOT NULL REFERENCES identidade.avatar_origens (id),
    url_avatar VARCHAR(2048) NOT NULL,
    storage_key VARCHAR(1024),
    content_type VARCHAR(128),
    tamanho_bytes BIGINT,
    hash_conteudo VARCHAR(128),
    versao VARCHAR(128),
    preferido BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_cadastros_conta_avatares_preferido
    ON autenticacao.cadastros_conta_avatares (cadastro_id)
    WHERE preferido IS TRUE;

WITH cliente_thimisu AS (
    SELECT id
    FROM catalogo.clientes_ecossistema
    WHERE codigo = 'eickrono-thimisu-app'
    LIMIT 1
)
INSERT INTO identidade.avatar_origens (
    codigo,
    nome,
    tipo,
    cliente_ecossistema_id,
    permite_vinculo_social,
    permite_upload_usuario,
    ativo,
    criado_em,
    atualizado_em
)
VALUES
    ('GOOGLE', 'Google', 'PROVEDOR_SOCIAL', NULL, TRUE, FALSE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('APPLE', 'Apple', 'PROVEDOR_SOCIAL', NULL, TRUE, FALSE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('FACEBOOK', 'Facebook', 'PROVEDOR_SOCIAL', NULL, TRUE, FALSE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('LINKEDIN', 'LinkedIn', 'PROVEDOR_SOCIAL', NULL, TRUE, FALSE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('INSTAGRAM', 'Instagram', 'PROVEDOR_SOCIAL', NULL, TRUE, FALSE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('X', 'X', 'PROVEDOR_SOCIAL', NULL, TRUE, FALSE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (
        'THIMISU',
        'Thimisu',
        'PRODUTO_EICKRONO',
        (SELECT id FROM cliente_thimisu),
        FALSE,
        TRUE,
        TRUE,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    )
ON CONFLICT (codigo) DO UPDATE
SET nome = EXCLUDED.nome,
    tipo = EXCLUDED.tipo,
    cliente_ecossistema_id = EXCLUDED.cliente_ecossistema_id,
    permite_vinculo_social = EXCLUDED.permite_vinculo_social,
    permite_upload_usuario = EXCLUDED.permite_upload_usuario,
    ativo = EXCLUDED.ativo,
    atualizado_em = CURRENT_TIMESTAMP;

WITH avatares_sociais AS (
    SELECT (
               SUBSTRING(MD5('identidade.avatar_usuario:social:' || forma.id::TEXT) FROM 1 FOR 8) || '-' ||
               SUBSTRING(MD5('identidade.avatar_usuario:social:' || forma.id::TEXT) FROM 9 FOR 4) || '-' ||
               SUBSTRING(MD5('identidade.avatar_usuario:social:' || forma.id::TEXT) FROM 13 FOR 4) || '-' ||
               SUBSTRING(MD5('identidade.avatar_usuario:social:' || forma.id::TEXT) FROM 17 FOR 4) || '-' ||
               SUBSTRING(MD5('identidade.avatar_usuario:social:' || forma.id::TEXT) FROM 21 FOR 12)
           )::UUID AS id,
           usuario_cliente.id AS usuario_cliente_id,
           origem.id AS origem_id,
           forma.id AS forma_acesso_id,
           forma.nome_exibicao_externo,
           BTRIM(forma.url_avatar_externo) AS url_avatar,
           MD5(BTRIM(forma.url_avatar_externo)) AS hash_conteudo,
           MD5(origem.codigo || ':' || BTRIM(forma.url_avatar_externo)) AS versao,
           usuario_cliente.avatar_preferido_origem = 'SOCIAL'
               AND usuario_cliente.avatar_preferido_forma_acesso_id = forma.id AS preferido,
           COALESCE(forma.avatar_externo_atualizado_em, usuario_cliente.atualizado_em, CURRENT_TIMESTAMP) AS atualizado_em
    FROM autenticacao.usuarios_clientes_ecossistema usuario_cliente
    JOIN autenticacao.usuarios_formas_acesso forma
      ON forma.usuario_id = usuario_cliente.usuario_id
     AND forma.tipo = 'SOCIAL'
     AND forma.desvinculado_em IS NULL
     AND forma.url_avatar_externo IS NOT NULL
     AND BTRIM(forma.url_avatar_externo) <> ''
    JOIN identidade.avatar_origens origem
      ON origem.codigo = UPPER(forma.provedor)
     AND origem.ativo IS TRUE
)
INSERT INTO identidade.avatar_usuario (
    id,
    usuario_cliente_id,
    origem_id,
    forma_acesso_id,
    nome_exibicao_externo,
    url_avatar,
    hash_conteudo,
    versao,
    preferido,
    criado_em,
    atualizado_em,
    removido_em
)
SELECT id,
       usuario_cliente_id,
       origem_id,
       forma_acesso_id,
       nome_exibicao_externo,
       url_avatar,
       hash_conteudo,
       versao,
       preferido,
       atualizado_em,
       atualizado_em,
       NULL
FROM avatares_sociais
ON CONFLICT (id) DO UPDATE
SET usuario_cliente_id = EXCLUDED.usuario_cliente_id,
    origem_id = EXCLUDED.origem_id,
    forma_acesso_id = EXCLUDED.forma_acesso_id,
    nome_exibicao_externo = COALESCE(EXCLUDED.nome_exibicao_externo, identidade.avatar_usuario.nome_exibicao_externo),
    url_avatar = EXCLUDED.url_avatar,
    hash_conteudo = EXCLUDED.hash_conteudo,
    versao = EXCLUDED.versao,
    preferido = EXCLUDED.preferido,
    atualizado_em = EXCLUDED.atualizado_em,
    removido_em = NULL;

WITH avatares_thimisu AS (
    SELECT (
               SUBSTRING(MD5('identidade.avatar_usuario:url:' || usuario_cliente.id::TEXT || ':' ||
                             BTRIM(usuario_cliente.avatar_preferido_url)) FROM 1 FOR 8) || '-' ||
               SUBSTRING(MD5('identidade.avatar_usuario:url:' || usuario_cliente.id::TEXT || ':' ||
                             BTRIM(usuario_cliente.avatar_preferido_url)) FROM 9 FOR 4) || '-' ||
               SUBSTRING(MD5('identidade.avatar_usuario:url:' || usuario_cliente.id::TEXT || ':' ||
                             BTRIM(usuario_cliente.avatar_preferido_url)) FROM 13 FOR 4) || '-' ||
               SUBSTRING(MD5('identidade.avatar_usuario:url:' || usuario_cliente.id::TEXT || ':' ||
                             BTRIM(usuario_cliente.avatar_preferido_url)) FROM 17 FOR 4) || '-' ||
               SUBSTRING(MD5('identidade.avatar_usuario:url:' || usuario_cliente.id::TEXT || ':' ||
                             BTRIM(usuario_cliente.avatar_preferido_url)) FROM 21 FOR 12)
           )::UUID AS id,
           usuario_cliente.id AS usuario_cliente_id,
           origem.id AS origem_id,
           BTRIM(usuario_cliente.avatar_preferido_url) AS url_avatar,
           MD5(BTRIM(usuario_cliente.avatar_preferido_url)) AS hash_conteudo,
           MD5(origem.codigo || ':' || BTRIM(usuario_cliente.avatar_preferido_url)) AS versao,
           COALESCE(usuario_cliente.avatar_preferido_atualizado_em, usuario_cliente.atualizado_em, CURRENT_TIMESTAMP)
               AS atualizado_em
    FROM autenticacao.usuarios_clientes_ecossistema usuario_cliente
    JOIN identidade.avatar_origens origem
      ON origem.codigo = 'THIMISU'
     AND origem.ativo IS TRUE
    WHERE usuario_cliente.avatar_preferido_origem IN ('URL_EXTERNA', 'UPLOAD_USUARIO')
      AND usuario_cliente.avatar_preferido_url IS NOT NULL
      AND BTRIM(usuario_cliente.avatar_preferido_url) <> ''
)
INSERT INTO identidade.avatar_usuario (
    id,
    usuario_cliente_id,
    origem_id,
    forma_acesso_id,
    nome_exibicao_externo,
    url_avatar,
    hash_conteudo,
    versao,
    preferido,
    criado_em,
    atualizado_em,
    removido_em
)
SELECT id,
       usuario_cliente_id,
       origem_id,
       NULL,
       NULL,
       url_avatar,
       hash_conteudo,
       versao,
       TRUE,
       atualizado_em,
       atualizado_em,
       NULL
FROM avatares_thimisu
ON CONFLICT (id) DO UPDATE
SET usuario_cliente_id = EXCLUDED.usuario_cliente_id,
    origem_id = EXCLUDED.origem_id,
    url_avatar = EXCLUDED.url_avatar,
    hash_conteudo = EXCLUDED.hash_conteudo,
    versao = EXCLUDED.versao,
    preferido = TRUE,
    atualizado_em = EXCLUDED.atualizado_em,
    removido_em = NULL;
