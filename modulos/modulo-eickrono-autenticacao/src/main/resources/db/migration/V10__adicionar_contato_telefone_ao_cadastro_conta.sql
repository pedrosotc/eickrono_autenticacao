ALTER TABLE cadastros_conta
    ADD COLUMN IF NOT EXISTS telefone_principal VARCHAR(32),
    ADD COLUMN IF NOT EXISTS canal_validacao_telefone VARCHAR(16);
