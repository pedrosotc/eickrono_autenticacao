CREATE TABLE IF NOT EXISTS atestacoes_app_desafios (
    id BIGSERIAL PRIMARY KEY,
    identificador_desafio VARCHAR(36) NOT NULL UNIQUE,
    desafio_base64 VARCHAR(128) NOT NULL UNIQUE,
    operacao VARCHAR(50) NOT NULL,
    plataforma VARCHAR(20) NOT NULL,
    provedor_esperado VARCHAR(50) NOT NULL,
    ip_solicitante VARCHAR(64),
    user_agent_solicitante VARCHAR(512),
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    expira_em TIMESTAMP WITH TIME ZONE NOT NULL,
    consumido_em TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_atestacoes_app_desafios_operacao
    ON atestacoes_app_desafios (operacao);

CREATE INDEX IF NOT EXISTS idx_atestacoes_app_desafios_expira_em
    ON atestacoes_app_desafios (expira_em);
