CREATE TABLE IF NOT EXISTS recuperacoes_senha (
    id BIGSERIAL PRIMARY KEY,
    fluxo_id UUID NOT NULL UNIQUE,
    subject_remoto VARCHAR(255),
    email_principal VARCHAR(255) NOT NULL,
    codigo_email_hash VARCHAR(64) NOT NULL,
    codigo_email_gerado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    codigo_email_expira_em TIMESTAMP WITH TIME ZONE NOT NULL,
    tentativas_confirmacao_email INTEGER NOT NULL DEFAULT 0,
    reenvios_email INTEGER NOT NULL DEFAULT 0,
    codigo_confirmado_em TIMESTAMP WITH TIME ZONE,
    senha_redefinida_em TIMESTAMP WITH TIME ZONE,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_recuperacoes_senha_email_principal
    ON recuperacoes_senha (email_principal);
