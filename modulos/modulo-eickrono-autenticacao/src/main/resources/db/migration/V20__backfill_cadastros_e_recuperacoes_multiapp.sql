WITH cliente_thimisu AS (
    SELECT cliente.id AS cliente_ecossistema_id
    FROM catalogo.clientes_ecossistema cliente
    WHERE cliente.codigo = 'eickrono-thimisu-app'
),
cadastros_base AS (
    SELECT cadastro.cadastro_id AS id,
           COALESCE(NULLIF(BTRIM(cadastro.subject_remoto), ''), pessoa.sub) AS sub_resolvido,
           cadastro.email_principal,
           cadastro.telefone_principal,
           cadastro.status,
           cadastro.codigo_email_hash,
           cadastro.codigo_email_gerado_em,
           cadastro.codigo_email_expira_em,
           cadastro.tentativas_confirmacao_email,
           cadastro.reenvios_email,
           cadastro.email_confirmado_em,
           cadastro.ip_solicitante,
           cadastro.user_agent_solicitante,
           cadastro.criado_em,
           cadastro.atualizado_em,
           sistema.id AS sistema_origem_id,
           cliente.cliente_ecossistema_id
    FROM cadastros_conta cadastro
    LEFT JOIN pessoas_identidade pessoa
      ON pessoa.id = cadastro.pessoa_id_perfil
    LEFT JOIN catalogo.sistemas_origem sistema
      ON sistema.identificador_sistema = BTRIM(cadastro.sistema_solicitante)
    CROSS JOIN cliente_thimisu cliente
),
cadastros_resolvidos AS (
    SELECT base.id,
           (
               SUBSTRING(MD5('identidade.pessoa:' || base.sub_resolvido) FROM 1 FOR 8) || '-' ||
               SUBSTRING(MD5('identidade.pessoa:' || base.sub_resolvido) FROM 9 FOR 4) || '-' ||
               SUBSTRING(MD5('identidade.pessoa:' || base.sub_resolvido) FROM 13 FOR 4) || '-' ||
               SUBSTRING(MD5('identidade.pessoa:' || base.sub_resolvido) FROM 17 FOR 4) || '-' ||
               SUBSTRING(MD5('identidade.pessoa:' || base.sub_resolvido) FROM 21 FOR 12)
           )::UUID AS pessoa_id,
           usuario.id AS usuario_id,
           base.cliente_ecossistema_id,
           base.sistema_origem_id,
           (
               SUBSTRING(MD5('identidade.email:' || LOWER(BTRIM(base.email_principal))) FROM 1 FOR 8) || '-' ||
               SUBSTRING(MD5('identidade.email:' || LOWER(BTRIM(base.email_principal))) FROM 9 FOR 4) || '-' ||
               SUBSTRING(MD5('identidade.email:' || LOWER(BTRIM(base.email_principal))) FROM 13 FOR 4) || '-' ||
               SUBSTRING(MD5('identidade.email:' || LOWER(BTRIM(base.email_principal))) FROM 17 FOR 4) || '-' ||
               SUBSTRING(MD5('identidade.email:' || LOWER(BTRIM(base.email_principal))) FROM 21 FOR 12)
           )::UUID AS email_id,
           CASE
               WHEN base.telefone_principal IS NULL OR BTRIM(base.telefone_principal) = '' THEN NULL
               ELSE (
                   SUBSTRING(MD5('identidade.telefone:' || BTRIM(base.telefone_principal)) FROM 1 FOR 8) || '-' ||
                   SUBSTRING(MD5('identidade.telefone:' || BTRIM(base.telefone_principal)) FROM 9 FOR 4) || '-' ||
                   SUBSTRING(MD5('identidade.telefone:' || BTRIM(base.telefone_principal)) FROM 13 FOR 4) || '-' ||
                   SUBSTRING(MD5('identidade.telefone:' || BTRIM(base.telefone_principal)) FROM 17 FOR 4) || '-' ||
                   SUBSTRING(MD5('identidade.telefone:' || BTRIM(base.telefone_principal)) FROM 21 FOR 12)
               )::UUID
           END AS telefone_id,
           CASE
               WHEN base.status = 'PENDENTE_EMAIL' THEN 'ABERTO'
               WHEN base.status = 'EMAIL_CONFIRMADO' THEN 'CONCLUIDO'
               ELSE base.status
           END AS status_processo,
           base.codigo_email_hash,
           base.codigo_email_gerado_em,
           base.codigo_email_expira_em,
           base.tentativas_confirmacao_email,
           base.reenvios_email,
           base.email_confirmado_em,
           CASE
               WHEN base.status = 'EMAIL_CONFIRMADO' THEN COALESCE(base.email_confirmado_em, base.atualizado_em)
               ELSE NULL
           END AS concluido_em,
           base.ip_solicitante,
           base.user_agent_solicitante,
           COALESCE(base.criado_em, CURRENT_TIMESTAMP) AS criado_em,
           COALESCE(base.atualizado_em, CURRENT_TIMESTAMP) AS atualizado_em
    FROM cadastros_base base
    LEFT JOIN autenticacao.usuarios usuario
      ON usuario.sub_remoto = base.sub_resolvido
    WHERE base.sub_resolvido IS NOT NULL
)
INSERT INTO autenticacao.cadastros_conta (
    id,
    pessoa_id,
    usuario_id,
    cliente_ecossistema_id,
    sistema_origem_id,
    email_id,
    telefone_id,
    status_processo,
    codigo_email_hash,
    codigo_email_gerado_em,
    codigo_email_expira_em,
    tentativas_confirmacao_email,
    reenvios_email,
    email_confirmado_em,
    concluido_em,
    ip_solicitante,
    user_agent_solicitante,
    criado_em,
    atualizado_em
)
SELECT base.id,
       base.pessoa_id,
       base.usuario_id,
       base.cliente_ecossistema_id,
       base.sistema_origem_id,
       base.email_id,
       base.telefone_id,
       base.status_processo,
       base.codigo_email_hash,
       base.codigo_email_gerado_em,
       base.codigo_email_expira_em,
       base.tentativas_confirmacao_email,
       base.reenvios_email,
       base.email_confirmado_em,
       base.concluido_em,
       base.ip_solicitante,
       base.user_agent_solicitante,
       base.criado_em,
       base.atualizado_em
FROM cadastros_resolvidos base
ON CONFLICT (id) DO NOTHING;

WITH cliente_thimisu AS (
    SELECT cliente.id AS cliente_ecossistema_id
    FROM catalogo.clientes_ecossistema cliente
    WHERE cliente.codigo = 'eickrono-thimisu-app'
),
recuperacoes_base AS (
    SELECT recuperacao.fluxo_id AS id,
           NULLIF(BTRIM(recuperacao.subject_remoto), '') AS sub_resolvido,
           recuperacao.email_principal,
           recuperacao.codigo_email_hash,
           recuperacao.codigo_email_gerado_em,
           recuperacao.codigo_email_expira_em,
           recuperacao.tentativas_confirmacao_email,
           recuperacao.reenvios_email,
           recuperacao.codigo_confirmado_em,
           recuperacao.senha_redefinida_em,
           recuperacao.criado_em,
           recuperacao.atualizado_em,
           cliente.cliente_ecossistema_id
    FROM recuperacoes_senha recuperacao
    CROSS JOIN cliente_thimisu cliente
),
usuarios_por_email AS (
    SELECT forma.usuario_id,
           LOWER(BTRIM(forma.identificador_externo)) AS email_normalizado
    FROM autenticacao.usuarios_formas_acesso forma
    WHERE forma.tipo = 'EMAIL_SENHA'
      AND forma.provedor = 'EMAIL'
      AND forma.identificador_externo IS NOT NULL
),
recuperacoes_resolvidas AS (
    SELECT base.id,
           COALESCE(usuario_sub.id, usuario_email.usuario_id) AS usuario_id,
           base.cliente_ecossistema_id,
           (
               SUBSTRING(MD5('identidade.email:' || LOWER(BTRIM(base.email_principal))) FROM 1 FOR 8) || '-' ||
               SUBSTRING(MD5('identidade.email:' || LOWER(BTRIM(base.email_principal))) FROM 9 FOR 4) || '-' ||
               SUBSTRING(MD5('identidade.email:' || LOWER(BTRIM(base.email_principal))) FROM 13 FOR 4) || '-' ||
               SUBSTRING(MD5('identidade.email:' || LOWER(BTRIM(base.email_principal))) FROM 17 FOR 4) || '-' ||
               SUBSTRING(MD5('identidade.email:' || LOWER(BTRIM(base.email_principal))) FROM 21 FOR 12)
           )::UUID AS email_id,
           CASE
               WHEN base.senha_redefinida_em IS NOT NULL THEN 'SENHA_REDEFINIDA'
               WHEN base.codigo_confirmado_em IS NOT NULL THEN 'EMAIL_CONFIRMADO'
               WHEN base.codigo_email_expira_em < CURRENT_TIMESTAMP THEN 'EXPIRADO'
               ELSE 'ABERTO'
           END AS status_processo,
           base.codigo_email_hash,
           base.codigo_email_gerado_em,
           base.codigo_email_expira_em,
           base.tentativas_confirmacao_email,
           base.reenvios_email,
           base.codigo_confirmado_em,
           base.senha_redefinida_em,
           COALESCE(base.criado_em, CURRENT_TIMESTAMP) AS criado_em,
           COALESCE(base.atualizado_em, CURRENT_TIMESTAMP) AS atualizado_em
    FROM recuperacoes_base base
    LEFT JOIN autenticacao.usuarios usuario_sub
      ON usuario_sub.sub_remoto = base.sub_resolvido
    LEFT JOIN usuarios_por_email usuario_email
      ON usuario_email.email_normalizado = LOWER(BTRIM(base.email_principal))
)
INSERT INTO autenticacao.recuperacoes_senha (
    id,
    usuario_id,
    cliente_ecossistema_id,
    email_id,
    status_processo,
    codigo_email_hash,
    codigo_email_gerado_em,
    codigo_email_expira_em,
    tentativas_confirmacao_email,
    reenvios_email,
    codigo_confirmado_em,
    senha_redefinida_em,
    criado_em,
    atualizado_em
)
SELECT base.id,
       base.usuario_id,
       base.cliente_ecossistema_id,
       base.email_id,
       base.status_processo,
       base.codigo_email_hash,
       base.codigo_email_gerado_em,
       base.codigo_email_expira_em,
       base.tentativas_confirmacao_email,
       base.reenvios_email,
       base.codigo_confirmado_em,
       base.senha_redefinida_em,
       base.criado_em,
       base.atualizado_em
FROM recuperacoes_resolvidas base
ON CONFLICT (id) DO NOTHING;
