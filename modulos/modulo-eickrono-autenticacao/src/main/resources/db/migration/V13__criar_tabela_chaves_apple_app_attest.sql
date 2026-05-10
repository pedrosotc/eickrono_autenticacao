CREATE TABLE IF NOT EXISTS apple_app_attest_chaves (
    id BIGSERIAL PRIMARY KEY,
    chave_id VARCHAR(128) NOT NULL UNIQUE,
    objeto_atestacao_base64 TEXT NOT NULL,
    contador_assinatura BIGINT NOT NULL,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_apple_app_attest_chaves_chave_id
    ON apple_app_attest_chaves (chave_id);
