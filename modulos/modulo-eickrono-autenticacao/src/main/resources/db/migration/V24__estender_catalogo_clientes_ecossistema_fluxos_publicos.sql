ALTER TABLE catalogo.clientes_ecossistema
    ADD COLUMN IF NOT EXISTS tipo_produto_exibicao VARCHAR(32),
    ADD COLUMN IF NOT EXISTS produto_exibicao VARCHAR(128),
    ADD COLUMN IF NOT EXISTS canal_exibicao VARCHAR(64),
    ADD COLUMN IF NOT EXISTS exige_validacao_telefone BOOLEAN NOT NULL DEFAULT FALSE;
