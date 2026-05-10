ALTER TABLE autenticacao.cadastros_conta
    ADD COLUMN IF NOT EXISTS identificador_publico_cliente VARCHAR(255);

UPDATE autenticacao.cadastros_conta destino
SET identificador_publico_cliente = LOWER(BTRIM(origem.usuario))
FROM cadastros_conta origem
WHERE destino.id = origem.cadastro_id
  AND origem.usuario IS NOT NULL
  AND BTRIM(origem.usuario) <> ''
  AND (
      destino.identificador_publico_cliente IS NULL
      OR BTRIM(destino.identificador_publico_cliente) = ''
  );

CREATE INDEX IF NOT EXISTS idx_cadastros_conta_multiapp_cliente_identificador
    ON autenticacao.cadastros_conta (cliente_ecossistema_id, identificador_publico_cliente);
