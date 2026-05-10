CREATE TABLE IF NOT EXISTS cadastros_conta (
    id BIGSERIAL PRIMARY KEY,
    cadastro_id UUID NOT NULL UNIQUE,
    subject_remoto VARCHAR(255) NOT NULL UNIQUE,
    nome_completo VARCHAR(255) NOT NULL,
    email_principal VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    codigo_email_hash VARCHAR(64) NOT NULL,
    codigo_email_gerado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    codigo_email_expira_em TIMESTAMP WITH TIME ZONE NOT NULL,
    tentativas_confirmacao_email INTEGER NOT NULL DEFAULT 0,
    reenvios_email INTEGER NOT NULL DEFAULT 0,
    email_confirmado_em TIMESTAMP WITH TIME ZONE,
    sistema_solicitante VARCHAR(64) NOT NULL,
    ip_solicitante VARCHAR(64),
    user_agent_solicitante VARCHAR(512),
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_cadastros_conta_email_principal
    ON cadastros_conta (email_principal);

CREATE INDEX IF NOT EXISTS idx_cadastros_conta_subject_remoto
    ON cadastros_conta (subject_remoto);
