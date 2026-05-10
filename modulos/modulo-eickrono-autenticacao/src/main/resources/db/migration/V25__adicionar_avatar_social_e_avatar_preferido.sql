ALTER TABLE vinculos_sociais
    ADD COLUMN IF NOT EXISTS nome_exibicao_externo VARCHAR(255),
    ADD COLUMN IF NOT EXISTS url_avatar_externo VARCHAR(2048),
    ADD COLUMN IF NOT EXISTS avatar_externo_atualizado_em TIMESTAMP WITH TIME ZONE;

ALTER TABLE pessoas_formas_acesso
    ADD COLUMN IF NOT EXISTS nome_exibicao_externo VARCHAR(255),
    ADD COLUMN IF NOT EXISTS url_avatar_externo VARCHAR(2048),
    ADD COLUMN IF NOT EXISTS avatar_externo_atualizado_em TIMESTAMP WITH TIME ZONE;

ALTER TABLE autenticacao.usuarios_formas_acesso
    ADD COLUMN IF NOT EXISTS nome_exibicao_externo VARCHAR(255),
    ADD COLUMN IF NOT EXISTS url_avatar_externo VARCHAR(2048),
    ADD COLUMN IF NOT EXISTS avatar_externo_atualizado_em TIMESTAMP WITH TIME ZONE;

ALTER TABLE autenticacao.usuarios_clientes_ecossistema
    ADD COLUMN IF NOT EXISTS avatar_preferido_origem VARCHAR(32) NOT NULL DEFAULT 'NENHUM',
    ADD COLUMN IF NOT EXISTS avatar_preferido_forma_acesso_id UUID REFERENCES autenticacao.usuarios_formas_acesso (id),
    ADD COLUMN IF NOT EXISTS avatar_preferido_url VARCHAR(2048),
    ADD COLUMN IF NOT EXISTS avatar_preferido_atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS avatar_preferido_arquivo_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_usuarios_clientes_ecossistema_avatar_origem'
    ) THEN
        ALTER TABLE autenticacao.usuarios_clientes_ecossistema
            ADD CONSTRAINT chk_usuarios_clientes_ecossistema_avatar_origem
            CHECK (avatar_preferido_origem IN ('SOCIAL', 'UPLOAD_USUARIO', 'URL_EXTERNA', 'NENHUM'));
    END IF;
END $$;
