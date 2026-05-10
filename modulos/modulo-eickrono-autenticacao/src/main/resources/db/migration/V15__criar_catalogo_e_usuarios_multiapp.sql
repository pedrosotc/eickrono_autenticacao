CREATE TABLE IF NOT EXISTS catalogo.clientes_ecossistema (
    id BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(64) NOT NULL UNIQUE,
    nome VARCHAR(255) NOT NULL,
    tipo VARCHAR(32) NOT NULL,
    client_id_oidc VARCHAR(255) UNIQUE,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS catalogo.sistemas_origem (
    id BIGSERIAL PRIMARY KEY,
    identificador_sistema VARCHAR(64) NOT NULL UNIQUE,
    descricao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS autenticacao.usuarios (
    id UUID PRIMARY KEY,
    pessoa_id UUID NOT NULL,
    sub_remoto VARCHAR(255) UNIQUE,
    status_global VARCHAR(32) NOT NULL,
    credencial_local_habilitada BOOLEAN NOT NULL DEFAULT TRUE,
    ultimo_login_em TIMESTAMP WITH TIME ZONE,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_autenticacao_usuarios_pessoa_id
    ON autenticacao.usuarios (pessoa_id);

CREATE TABLE IF NOT EXISTS autenticacao.usuarios_formas_acesso (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL REFERENCES autenticacao.usuarios (id),
    email_id UUID,
    tipo VARCHAR(32) NOT NULL,
    provedor VARCHAR(64) NOT NULL,
    identificador_externo VARCHAR(255) NOT NULL,
    principal BOOLEAN NOT NULL DEFAULT FALSE,
    verificado_em TIMESTAMP WITH TIME ZONE,
    vinculado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    desvinculado_em TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_usuarios_formas_acesso_identificador
    ON autenticacao.usuarios_formas_acesso (tipo, provedor, identificador_externo);

CREATE INDEX IF NOT EXISTS idx_usuarios_formas_acesso_usuario_principal
    ON autenticacao.usuarios_formas_acesso (usuario_id, principal);

CREATE TABLE IF NOT EXISTS autenticacao.usuarios_clientes_ecossistema (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL REFERENCES autenticacao.usuarios (id),
    cliente_ecossistema_id BIGINT NOT NULL REFERENCES catalogo.clientes_ecossistema (id),
    status_vinculo VARCHAR(32) NOT NULL,
    identificador_publico_cliente VARCHAR(255),
    ultimo_acesso_em TIMESTAMP WITH TIME ZONE,
    vinculado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    revogado_em TIMESTAMP WITH TIME ZONE,
    motivo_revogacao VARCHAR(64)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_usuarios_clientes_ecossistema_usuario_cliente
    ON autenticacao.usuarios_clientes_ecossistema (usuario_id, cliente_ecossistema_id);

CREATE INDEX IF NOT EXISTS idx_usuarios_clientes_ecossistema_cliente_status
    ON autenticacao.usuarios_clientes_ecossistema (cliente_ecossistema_id, status_vinculo);

CREATE TABLE IF NOT EXISTS autenticacao.cadastros_conta (
    id UUID PRIMARY KEY,
    pessoa_id UUID NOT NULL,
    usuario_id UUID REFERENCES autenticacao.usuarios (id),
    cliente_ecossistema_id BIGINT NOT NULL REFERENCES catalogo.clientes_ecossistema (id),
    sistema_origem_id BIGINT REFERENCES catalogo.sistemas_origem (id),
    email_id UUID NOT NULL,
    telefone_id UUID,
    status_processo VARCHAR(32) NOT NULL,
    codigo_email_hash VARCHAR(64) NOT NULL,
    codigo_email_gerado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    codigo_email_expira_em TIMESTAMP WITH TIME ZONE NOT NULL,
    tentativas_confirmacao_email INTEGER NOT NULL DEFAULT 0,
    reenvios_email INTEGER NOT NULL DEFAULT 0,
    email_confirmado_em TIMESTAMP WITH TIME ZONE,
    concluido_em TIMESTAMP WITH TIME ZONE,
    ip_solicitante VARCHAR(64),
    user_agent_solicitante VARCHAR(512),
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_cadastros_conta_multiapp_cliente_status
    ON autenticacao.cadastros_conta (cliente_ecossistema_id, status_processo);

CREATE INDEX IF NOT EXISTS idx_cadastros_conta_multiapp_usuario
    ON autenticacao.cadastros_conta (usuario_id);

CREATE TABLE IF NOT EXISTS autenticacao.recuperacoes_senha (
    id UUID PRIMARY KEY,
    usuario_id UUID REFERENCES autenticacao.usuarios (id),
    cliente_ecossistema_id BIGINT REFERENCES catalogo.clientes_ecossistema (id),
    email_id UUID NOT NULL,
    status_processo VARCHAR(32) NOT NULL,
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

CREATE INDEX IF NOT EXISTS idx_recuperacoes_senha_multiapp_usuario
    ON autenticacao.recuperacoes_senha (usuario_id);

CREATE INDEX IF NOT EXISTS idx_recuperacoes_senha_multiapp_cliente_status
    ON autenticacao.recuperacoes_senha (cliente_ecossistema_id, status_processo);
