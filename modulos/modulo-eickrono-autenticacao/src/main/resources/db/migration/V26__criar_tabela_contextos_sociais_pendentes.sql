CREATE TABLE IF NOT EXISTS autenticacao.contextos_sociais_pendentes (
    id UUID PRIMARY KEY,
    cliente_ecossistema_id BIGINT NOT NULL REFERENCES catalogo.clientes_ecossistema (id),
    provedor VARCHAR(32) NOT NULL,
    identificador_externo VARCHAR(255) NOT NULL,
    email_social_normalizado VARCHAR(255) NOT NULL,
    nome_usuario_externo VARCHAR(255),
    nome_exibicao_externo VARCHAR(255),
    url_avatar_externo VARCHAR(2048),
    usuario_id_sugerido UUID REFERENCES autenticacao.usuarios (id),
    login_sugerido VARCHAR(255),
    modo_pendente VARCHAR(32) NOT NULL,
    tentativas_falhas INTEGER NOT NULL DEFAULT 0,
    tentativas_maximas INTEGER NOT NULL DEFAULT 3,
    expira_em TIMESTAMP WITH TIME ZONE NOT NULL,
    cancelado_em TIMESTAMP WITH TIME ZONE,
    consumido_em TIMESTAMP WITH TIME ZONE,
    motivo_cancelamento VARCHAR(64),
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_contextos_sociais_pendentes_modo
        CHECK (modo_pendente IN ('ABRIR_CADASTRO', 'ENTRAR_E_VINCULAR')),
    CONSTRAINT ck_contextos_sociais_pendentes_tentativas_falhas
        CHECK (tentativas_falhas >= 0),
    CONSTRAINT ck_contextos_sociais_pendentes_tentativas_maximas
        CHECK (tentativas_maximas > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_contextos_sociais_pendentes_ativos_projeto_provedor
    ON autenticacao.contextos_sociais_pendentes (cliente_ecossistema_id, provedor, identificador_externo)
    WHERE cancelado_em IS NULL
      AND consumido_em IS NULL;

CREATE INDEX IF NOT EXISTS idx_contextos_sociais_pendentes_email_projeto
    ON autenticacao.contextos_sociais_pendentes (cliente_ecossistema_id, email_social_normalizado);

CREATE INDEX IF NOT EXISTS idx_contextos_sociais_pendentes_usuario_sugerido
    ON autenticacao.contextos_sociais_pendentes (usuario_id_sugerido, cliente_ecossistema_id);
