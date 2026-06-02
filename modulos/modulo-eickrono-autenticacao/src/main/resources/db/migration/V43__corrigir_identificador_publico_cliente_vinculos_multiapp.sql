WITH candidatos AS (
    SELECT DISTINCT ON (cadastro.usuario_id, cadastro.cliente_ecossistema_id)
           cadastro.usuario_id,
           cadastro.cliente_ecossistema_id,
           LOWER(BTRIM(cadastro_legacy.usuario)) AS identificador_publico_cliente,
           cadastro.atualizado_em
    FROM autenticacao.cadastros_conta cadastro
    JOIN cadastros_conta cadastro_legacy
      ON cadastro_legacy.cadastro_id = cadastro.id
    WHERE cadastro.usuario_id IS NOT NULL
      AND cadastro.cliente_ecossistema_id IS NOT NULL
      AND cadastro_legacy.usuario IS NOT NULL
      AND BTRIM(cadastro_legacy.usuario) <> ''
    ORDER BY cadastro.usuario_id,
             cadastro.cliente_ecossistema_id,
             cadastro.atualizado_em DESC
)
UPDATE autenticacao.usuarios_clientes_ecossistema vinculo
SET identificador_publico_cliente = candidatos.identificador_publico_cliente,
    atualizado_em = GREATEST(vinculo.atualizado_em, candidatos.atualizado_em)
FROM candidatos
WHERE vinculo.usuario_id = candidatos.usuario_id
  AND vinculo.cliente_ecossistema_id = candidatos.cliente_ecossistema_id
  AND (
      vinculo.identificador_publico_cliente IS NULL
      OR BTRIM(vinculo.identificador_publico_cliente) = ''
  );
