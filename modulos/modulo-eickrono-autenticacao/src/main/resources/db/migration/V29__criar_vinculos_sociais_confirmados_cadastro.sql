CREATE TABLE IF NOT EXISTS autenticacao.cadastros_conta_vinculos_sociais_confirmados (
    id UUID PRIMARY KEY,
    cadastro_id UUID NOT NULL REFERENCES autenticacao.cadastros_conta (id) ON DELETE CASCADE,
    provedor VARCHAR(64) NOT NULL,
    identificador_externo VARCHAR(255) NOT NULL,
    nome_usuario_externo VARCHAR(255),
    email_social VARCHAR(255),
    nome_exibicao_externo VARCHAR(255),
    url_avatar_externo VARCHAR(2048),
    avatar_preferido BOOLEAN NOT NULL DEFAULT FALSE,
    consumido_em TIMESTAMP WITH TIME ZONE,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_cadastros_conta_vinculos_sociais_ativos
    ON autenticacao.cadastros_conta_vinculos_sociais_confirmados (
        cadastro_id,
        provedor,
        identificador_externo
    )
    WHERE consumido_em IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_cadastros_conta_vinculos_sociais_avatar_preferido
    ON autenticacao.cadastros_conta_vinculos_sociais_confirmados (cadastro_id)
    WHERE avatar_preferido IS TRUE
      AND consumido_em IS NULL;
