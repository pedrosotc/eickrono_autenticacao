WITH cliente_thimisu AS (
    SELECT cliente.id AS cliente_ecossistema_id
    FROM catalogo.clientes_ecossistema cliente
    WHERE cliente.codigo = 'eickrono-thimisu-app'
),
registros_base AS (
    SELECT registro.id,
           COALESCE(NULLIF(BTRIM(registro.usuario_sub), ''), pessoa.sub) AS sub_resolvido,
           registro.email,
           registro.telefone,
           registro.fingerprint,
           registro.plataforma,
           registro.versao_app,
           registro.chave_publica,
           registro.status,
           registro.criado_em,
           registro.expira_em,
           registro.confirmado_em,
           registro.cancelado_em,
           registro.reenvios,
           cliente.cliente_ecossistema_id
    FROM registro_dispositivo registro
    LEFT JOIN pessoas_identidade pessoa
      ON pessoa.id = registro.pessoa_id_perfil
    CROSS JOIN cliente_thimisu cliente
),
registros_resolvidos AS (
    SELECT base.id,
           usuario.id AS usuario_id,
           CASE
               WHEN base.sub_resolvido IS NULL THEN NULL
               ELSE (
                   SUBSTRING(MD5('identidade.pessoa:' || base.sub_resolvido) FROM 1 FOR 8) || '-' ||
                   SUBSTRING(MD5('identidade.pessoa:' || base.sub_resolvido) FROM 9 FOR 4) || '-' ||
                   SUBSTRING(MD5('identidade.pessoa:' || base.sub_resolvido) FROM 13 FOR 4) || '-' ||
                   SUBSTRING(MD5('identidade.pessoa:' || base.sub_resolvido) FROM 17 FOR 4) || '-' ||
                   SUBSTRING(MD5('identidade.pessoa:' || base.sub_resolvido) FROM 21 FOR 12)
               )::UUID
           END AS pessoa_id,
           base.cliente_ecossistema_id,
           (
               SUBSTRING(MD5('identidade.email:' || LOWER(BTRIM(base.email))) FROM 1 FOR 8) || '-' ||
               SUBSTRING(MD5('identidade.email:' || LOWER(BTRIM(base.email))) FROM 9 FOR 4) || '-' ||
               SUBSTRING(MD5('identidade.email:' || LOWER(BTRIM(base.email))) FROM 13 FOR 4) || '-' ||
               SUBSTRING(MD5('identidade.email:' || LOWER(BTRIM(base.email))) FROM 17 FOR 4) || '-' ||
               SUBSTRING(MD5('identidade.email:' || LOWER(BTRIM(base.email))) FROM 21 FOR 12)
           )::UUID AS email_id,
           CASE
               WHEN base.telefone IS NULL OR BTRIM(base.telefone) = '' THEN NULL
               ELSE (
                   SUBSTRING(MD5('identidade.telefone:' || BTRIM(base.telefone)) FROM 1 FOR 8) || '-' ||
                   SUBSTRING(MD5('identidade.telefone:' || BTRIM(base.telefone)) FROM 9 FOR 4) || '-' ||
                   SUBSTRING(MD5('identidade.telefone:' || BTRIM(base.telefone)) FROM 13 FOR 4) || '-' ||
                   SUBSTRING(MD5('identidade.telefone:' || BTRIM(base.telefone)) FROM 17 FOR 4) || '-' ||
                   SUBSTRING(MD5('identidade.telefone:' || BTRIM(base.telefone)) FROM 21 FOR 12)
               )::UUID
           END AS telefone_id,
           base.fingerprint,
           base.plataforma,
           base.versao_app,
           base.chave_publica,
           base.status,
           COALESCE(base.criado_em, CURRENT_TIMESTAMP) AS criado_em,
           base.expira_em,
           base.confirmado_em,
           base.cancelado_em AS encerrado_em,
           base.reenvios
    FROM registros_base base
    LEFT JOIN autenticacao.usuarios usuario
      ON usuario.sub_remoto = base.sub_resolvido
)
INSERT INTO dispositivos.registros_dispositivo (
    id,
    usuario_id,
    pessoa_id,
    cliente_ecossistema_id,
    sistema_origem_id,
    cadastro_id,
    email_id,
    telefone_id,
    fingerprint,
    plataforma,
    versao_app,
    chave_publica,
    status,
    criado_em,
    expira_em,
    confirmado_em,
    encerrado_em,
    reenvios
)
SELECT base.id,
       base.usuario_id,
       base.pessoa_id,
       base.cliente_ecossistema_id,
       NULL,
       NULL,
       base.email_id,
       base.telefone_id,
       base.fingerprint,
       base.plataforma,
       base.versao_app,
       base.chave_publica,
       base.status,
       base.criado_em,
       base.expira_em,
       base.confirmado_em,
       base.encerrado_em,
       base.reenvios
FROM registros_resolvidos base
ON CONFLICT (id) DO NOTHING;

WITH codigos_base AS (
    SELECT codigo.id,
           codigo.registro_id,
           codigo.canal,
           codigo.destino,
           codigo.codigo_hash,
           codigo.tentativas,
           codigo.tentativas_maximas,
           codigo.reenvios,
           codigo.reenvios_maximos,
           codigo.status,
           codigo.enviado_em,
           codigo.confirmado_em,
           codigo.expira_em
    FROM codigo_verificacao codigo
)
INSERT INTO dispositivos.codigos_verificacao_dispositivo (
    id,
    registro_dispositivo_id,
    canal,
    email_id,
    telefone_id,
    codigo_hash,
    tentativas,
    tentativas_maximas,
    reenvios,
    reenvios_maximos,
    status,
    enviado_em,
    confirmado_em,
    expira_em
)
SELECT base.id,
       base.registro_id,
       base.canal,
       CASE
           WHEN base.canal = 'EMAIL' AND base.destino IS NOT NULL AND BTRIM(base.destino) <> '' THEN (
               SUBSTRING(MD5('identidade.email:' || LOWER(BTRIM(base.destino))) FROM 1 FOR 8) || '-' ||
               SUBSTRING(MD5('identidade.email:' || LOWER(BTRIM(base.destino))) FROM 9 FOR 4) || '-' ||
               SUBSTRING(MD5('identidade.email:' || LOWER(BTRIM(base.destino))) FROM 13 FOR 4) || '-' ||
               SUBSTRING(MD5('identidade.email:' || LOWER(BTRIM(base.destino))) FROM 17 FOR 4) || '-' ||
               SUBSTRING(MD5('identidade.email:' || LOWER(BTRIM(base.destino))) FROM 21 FOR 12)
           )::UUID
           ELSE NULL
       END AS email_id,
       CASE
           WHEN base.canal = 'SMS' AND base.destino IS NOT NULL AND BTRIM(base.destino) <> '' THEN (
               SUBSTRING(MD5('identidade.telefone:' || BTRIM(base.destino)) FROM 1 FOR 8) || '-' ||
               SUBSTRING(MD5('identidade.telefone:' || BTRIM(base.destino)) FROM 9 FOR 4) || '-' ||
               SUBSTRING(MD5('identidade.telefone:' || BTRIM(base.destino)) FROM 13 FOR 4) || '-' ||
               SUBSTRING(MD5('identidade.telefone:' || BTRIM(base.destino)) FROM 17 FOR 4) || '-' ||
               SUBSTRING(MD5('identidade.telefone:' || BTRIM(base.destino)) FROM 21 FOR 12)
           )::UUID
           ELSE NULL
       END AS telefone_id,
       base.codigo_hash,
       base.tentativas,
       base.tentativas_maximas,
       base.reenvios,
       base.reenvios_maximos,
       base.status,
       base.enviado_em,
       base.confirmado_em,
       base.expira_em
FROM codigos_base base
ON CONFLICT (id) DO NOTHING;

WITH cliente_thimisu AS (
    SELECT cliente.id AS cliente_ecossistema_id
    FROM catalogo.clientes_ecossistema cliente
    WHERE cliente.codigo = 'eickrono-thimisu-app'
),
dispositivos_base AS (
    SELECT dispositivo.id AS legacy_dispositivo_id,
           COALESCE(NULLIF(BTRIM(dispositivo.usuario_sub), ''), pessoa.sub) AS sub_resolvido,
           dispositivo.fingerprint,
           dispositivo.plataforma,
           dispositivo.versao_app,
           dispositivo.chave_publica,
           dispositivo.status,
           dispositivo.criado_em,
           dispositivo.atualizado_em,
           dispositivo.ultimo_token_emitido_em,
           cliente.cliente_ecossistema_id
    FROM dispositivos_identidade dispositivo
    LEFT JOIN pessoas_identidade pessoa
      ON pessoa.id = dispositivo.pessoa_id_perfil
    CROSS JOIN cliente_thimisu cliente
),
dispositivos_resolvidos AS (
    SELECT (
               SUBSTRING(MD5('dispositivos.confiavel:' || base.legacy_dispositivo_id::TEXT) FROM 1 FOR 8) || '-' ||
               SUBSTRING(MD5('dispositivos.confiavel:' || base.legacy_dispositivo_id::TEXT) FROM 9 FOR 4) || '-' ||
               SUBSTRING(MD5('dispositivos.confiavel:' || base.legacy_dispositivo_id::TEXT) FROM 13 FOR 4) || '-' ||
               SUBSTRING(MD5('dispositivos.confiavel:' || base.legacy_dispositivo_id::TEXT) FROM 17 FOR 4) || '-' ||
               SUBSTRING(MD5('dispositivos.confiavel:' || base.legacy_dispositivo_id::TEXT) FROM 21 FOR 12)
           )::UUID AS dispositivo_id,
           base.legacy_dispositivo_id,
           usuario.id AS usuario_id,
           base.sub_resolvido,
           base.cliente_ecossistema_id,
           base.fingerprint,
           base.plataforma,
           base.versao_app,
           base.chave_publica,
           base.status,
           COALESCE(base.criado_em, CURRENT_TIMESTAMP) AS criado_em,
           COALESCE(base.atualizado_em, CURRENT_TIMESTAMP) AS atualizado_em,
           base.ultimo_token_emitido_em
    FROM dispositivos_base base
    LEFT JOIN autenticacao.usuarios usuario
      ON usuario.sub_remoto = base.sub_resolvido
    WHERE base.sub_resolvido IS NOT NULL
)
INSERT INTO dispositivos.dispositivos_confiaveis (
    id,
    usuario_id,
    cliente_ecossistema_id,
    fingerprint,
    plataforma,
    versao_app_atual,
    chave_publica_atual,
    status,
    criado_em,
    atualizado_em,
    ultimo_token_emitido_em
)
SELECT base.dispositivo_id,
       base.usuario_id,
       base.cliente_ecossistema_id,
       base.fingerprint,
       base.plataforma,
       base.versao_app,
       base.chave_publica,
       base.status,
       base.criado_em,
       base.atualizado_em,
       base.ultimo_token_emitido_em
FROM dispositivos_resolvidos base
WHERE base.usuario_id IS NOT NULL
ON CONFLICT (usuario_id, cliente_ecossistema_id, fingerprint) DO NOTHING;

WITH cliente_thimisu AS (
    SELECT cliente.id AS cliente_ecossistema_id
    FROM catalogo.clientes_ecossistema cliente
    WHERE cliente.codigo = 'eickrono-thimisu-app'
),
dispositivos_mapeados AS (
    SELECT dispositivo.id AS legacy_dispositivo_id,
           dispositivo_novo.id AS dispositivo_id,
           COALESCE(NULLIF(BTRIM(dispositivo.usuario_sub), ''), pessoa.sub) AS sub_resolvido,
           dispositivo.fingerprint
    FROM dispositivos_identidade dispositivo
    LEFT JOIN pessoas_identidade pessoa
      ON pessoa.id = dispositivo.pessoa_id_perfil
    LEFT JOIN autenticacao.usuarios usuario
      ON usuario.sub_remoto = COALESCE(NULLIF(BTRIM(dispositivo.usuario_sub), ''), pessoa.sub)
    CROSS JOIN cliente_thimisu cliente
    LEFT JOIN dispositivos.dispositivos_confiaveis dispositivo_novo
      ON dispositivo_novo.usuario_id = usuario.id
     AND dispositivo_novo.cliente_ecossistema_id = cliente.cliente_ecossistema_id
     AND dispositivo_novo.fingerprint = dispositivo.fingerprint
),
registros_vinculados AS (
    SELECT registro.id AS registro_id,
           COALESCE(NULLIF(BTRIM(registro.usuario_sub), ''), pessoa.sub) AS sub_resolvido,
           registro.fingerprint
    FROM registro_dispositivo registro
    LEFT JOIN pessoas_identidade pessoa
      ON pessoa.id = registro.pessoa_id_perfil
),
tokens_resolvidos AS (
    SELECT token.id,
           COALESCE(dispositivo_por_id.dispositivo_id, dispositivo_por_match.dispositivo_id) AS dispositivo_id,
           token.registro_id AS registro_dispositivo_id,
           token.token_hash,
           token.status,
           token.emitido_em,
           token.expira_em,
           token.revogado_em,
           token.motivo_revogacao
    FROM token_dispositivo token
    LEFT JOIN dispositivos_mapeados dispositivo_por_id
      ON dispositivo_por_id.legacy_dispositivo_id = token.dispositivo_id
    LEFT JOIN registros_vinculados registro
      ON registro.registro_id = token.registro_id
    LEFT JOIN dispositivos_mapeados dispositivo_por_match
      ON dispositivo_por_match.sub_resolvido = registro.sub_resolvido
     AND dispositivo_por_match.fingerprint = registro.fingerprint
)
INSERT INTO dispositivos.tokens_dispositivo (
    id,
    dispositivo_id,
    registro_dispositivo_id,
    token_hash,
    status,
    emitido_em,
    expira_em,
    revogado_em,
    motivo_revogacao
)
SELECT base.id,
       base.dispositivo_id,
       base.registro_dispositivo_id,
       base.token_hash,
       base.status,
       base.emitido_em,
       base.expira_em,
       base.revogado_em,
       base.motivo_revogacao
FROM tokens_resolvidos base
WHERE base.dispositivo_id IS NOT NULL
ON CONFLICT (id) DO NOTHING;

WITH cliente_thimisu AS (
    SELECT cliente.id AS cliente_ecossistema_id
    FROM catalogo.clientes_ecossistema cliente
    WHERE cliente.codigo = 'eickrono-thimisu-app'
),
desafios_base AS (
    SELECT desafio.identificador_desafio,
           desafio.desafio_base64,
           desafio.operacao,
           desafio.plataforma,
           desafio.provedor_esperado,
           COALESCE(NULLIF(BTRIM(desafio.usuario_sub), ''), pessoa.sub) AS sub_resolvido,
           desafio.cadastro_id,
           desafio.registro_dispositivo_id,
           desafio.ip_solicitante,
           desafio.user_agent_solicitante,
           desafio.criado_em,
           desafio.expira_em,
           desafio.consumido_em,
           cliente.cliente_ecossistema_id
    FROM atestacoes_app_desafios desafio
    LEFT JOIN pessoas_identidade pessoa
      ON pessoa.id = desafio.pessoa_id_perfil
    CROSS JOIN cliente_thimisu cliente
),
desafios_resolvidos AS (
    SELECT (
               SUBSTRING(MD5('seguranca.desafio:' || base.identificador_desafio) FROM 1 FOR 8) || '-' ||
               SUBSTRING(MD5('seguranca.desafio:' || base.identificador_desafio) FROM 9 FOR 4) || '-' ||
               SUBSTRING(MD5('seguranca.desafio:' || base.identificador_desafio) FROM 13 FOR 4) || '-' ||
               SUBSTRING(MD5('seguranca.desafio:' || base.identificador_desafio) FROM 17 FOR 4) || '-' ||
               SUBSTRING(MD5('seguranca.desafio:' || base.identificador_desafio) FROM 21 FOR 12)
           )::UUID AS id,
           base.cliente_ecossistema_id,
           cadastro_novo.sistema_origem_id,
           COALESCE(usuario_sub.id, cadastro_novo.usuario_id, registro_novo.usuario_id) AS usuario_id,
           COALESCE(
               CASE
                   WHEN base.sub_resolvido IS NULL THEN NULL
                   ELSE (
                       SUBSTRING(MD5('identidade.pessoa:' || base.sub_resolvido) FROM 1 FOR 8) || '-' ||
                       SUBSTRING(MD5('identidade.pessoa:' || base.sub_resolvido) FROM 9 FOR 4) || '-' ||
                       SUBSTRING(MD5('identidade.pessoa:' || base.sub_resolvido) FROM 13 FOR 4) || '-' ||
                       SUBSTRING(MD5('identidade.pessoa:' || base.sub_resolvido) FROM 17 FOR 4) || '-' ||
                       SUBSTRING(MD5('identidade.pessoa:' || base.sub_resolvido) FROM 21 FOR 12)
                   )::UUID
               END,
               cadastro_novo.pessoa_id,
               registro_novo.pessoa_id
           ) AS pessoa_id,
           vinculo.id AS vinculo_cliente_id,
           cadastro_novo.id AS cadastro_id,
           registro_novo.id AS registro_dispositivo_id,
           dispositivo_novo.id AS dispositivo_id,
           base.operacao,
           base.plataforma,
           base.provedor_esperado,
           base.desafio_base64,
           base.ip_solicitante,
           base.user_agent_solicitante,
           base.criado_em,
           base.expira_em,
           base.consumido_em
    FROM desafios_base base
    LEFT JOIN autenticacao.usuarios usuario_sub
      ON usuario_sub.sub_remoto = base.sub_resolvido
    LEFT JOIN autenticacao.cadastros_conta cadastro_novo
      ON cadastro_novo.id = base.cadastro_id
    LEFT JOIN dispositivos.registros_dispositivo registro_novo
      ON registro_novo.id = base.registro_dispositivo_id
    LEFT JOIN autenticacao.usuarios_clientes_ecossistema vinculo
      ON vinculo.usuario_id = COALESCE(usuario_sub.id, cadastro_novo.usuario_id, registro_novo.usuario_id)
     AND vinculo.cliente_ecossistema_id = base.cliente_ecossistema_id
    LEFT JOIN dispositivos.dispositivos_confiaveis dispositivo_novo
      ON dispositivo_novo.usuario_id = COALESCE(usuario_sub.id, cadastro_novo.usuario_id, registro_novo.usuario_id)
     AND dispositivo_novo.cliente_ecossistema_id = base.cliente_ecossistema_id
     AND dispositivo_novo.fingerprint = registro_novo.fingerprint
)
INSERT INTO seguranca.atestacoes_app_desafios (
    id,
    cliente_ecossistema_id,
    sistema_origem_id,
    usuario_id,
    pessoa_id,
    vinculo_cliente_id,
    cadastro_id,
    registro_dispositivo_id,
    dispositivo_id,
    operacao,
    plataforma,
    provedor_esperado,
    desafio_base64,
    ip_solicitante,
    user_agent_solicitante,
    criado_em,
    expira_em,
    consumido_em
)
SELECT base.id,
       base.cliente_ecossistema_id,
       base.sistema_origem_id,
       base.usuario_id,
       base.pessoa_id,
       base.vinculo_cliente_id,
       base.cadastro_id,
       base.registro_dispositivo_id,
       base.dispositivo_id,
       base.operacao,
       base.plataforma,
       base.provedor_esperado,
       base.desafio_base64,
       base.ip_solicitante,
       base.user_agent_solicitante,
       base.criado_em,
       base.expira_em,
       base.consumido_em
FROM desafios_resolvidos base
ON CONFLICT (id) DO NOTHING;
