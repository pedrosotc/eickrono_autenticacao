CREATE TABLE registro_dispositivo (
    id UUID PRIMARY KEY,
    usuario_sub VARCHAR(255),
    email VARCHAR(255) NOT NULL,
    telefone VARCHAR(32) NOT NULL,
    fingerprint VARCHAR(255) NOT NULL,
    plataforma VARCHAR(100) NOT NULL,
    versao_app VARCHAR(32),
    chave_publica TEXT,
    status VARCHAR(32) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL,
    expira_em TIMESTAMPTZ NOT NULL,
    confirmado_em TIMESTAMPTZ,
    cancelado_em TIMESTAMPTZ,
    reenvios INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_registro_dispositivo_status ON registro_dispositivo (status);
CREATE INDEX idx_registro_dispositivo_expira_em ON registro_dispositivo (expira_em);
CREATE INDEX idx_registro_dispositivo_usuario_sub ON registro_dispositivo (usuario_sub);

CREATE TABLE codigo_verificacao (
    id UUID PRIMARY KEY,
    registro_id UUID NOT NULL REFERENCES registro_dispositivo (id) ON DELETE CASCADE,
    canal VARCHAR(16) NOT NULL,
    destino VARCHAR(255) NOT NULL,
    codigo_hash VARCHAR(128) NOT NULL,
    tentativas INT NOT NULL,
    tentativas_maximas INT NOT NULL,
    reenvios INT NOT NULL,
    reenvios_maximos INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    enviado_em TIMESTAMPTZ,
    confirmado_em TIMESTAMPTZ,
    expira_em TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX idx_codigo_verificacao_registro_canal ON codigo_verificacao (registro_id, canal);
CREATE INDEX idx_codigo_verificacao_status ON codigo_verificacao (status);

CREATE TABLE token_dispositivo (
    id UUID PRIMARY KEY,
    registro_id UUID NOT NULL REFERENCES registro_dispositivo (id) ON DELETE CASCADE,
    usuario_sub VARCHAR(255) NOT NULL,
    fingerprint VARCHAR(255) NOT NULL,
    plataforma VARCHAR(100) NOT NULL,
    versao_app VARCHAR(32),
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    emitido_em TIMESTAMPTZ NOT NULL,
    expira_em TIMESTAMPTZ NOT NULL,
    revogado_em TIMESTAMPTZ,
    motivo_revogacao VARCHAR(64)
);

CREATE INDEX idx_token_dispositivo_usuario_status ON token_dispositivo (usuario_sub, status);
