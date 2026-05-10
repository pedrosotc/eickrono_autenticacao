CREATE TABLE IF NOT EXISTS dispositivos.registros_dispositivo (
    id UUID PRIMARY KEY,
    usuario_id UUID REFERENCES autenticacao.usuarios (id),
    pessoa_id UUID,
    cliente_ecossistema_id BIGINT NOT NULL REFERENCES catalogo.clientes_ecossistema (id),
    sistema_origem_id BIGINT REFERENCES catalogo.sistemas_origem (id),
    cadastro_id UUID REFERENCES autenticacao.cadastros_conta (id),
    email_id UUID NOT NULL,
    telefone_id UUID,
    fingerprint VARCHAR(255) NOT NULL,
    plataforma VARCHAR(100) NOT NULL,
    versao_app VARCHAR(32),
    chave_publica TEXT,
    status VARCHAR(32) NOT NULL,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    expira_em TIMESTAMP WITH TIME ZONE NOT NULL,
    confirmado_em TIMESTAMP WITH TIME ZONE,
    encerrado_em TIMESTAMP WITH TIME ZONE,
    reenvios INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_registros_dispositivo_multiapp_status
    ON dispositivos.registros_dispositivo (status);

CREATE INDEX IF NOT EXISTS idx_registros_dispositivo_multiapp_cliente_status
    ON dispositivos.registros_dispositivo (cliente_ecossistema_id, status);

CREATE INDEX IF NOT EXISTS idx_registros_dispositivo_multiapp_usuario
    ON dispositivos.registros_dispositivo (usuario_id);

CREATE TABLE IF NOT EXISTS dispositivos.codigos_verificacao_dispositivo (
    id UUID PRIMARY KEY,
    registro_dispositivo_id UUID NOT NULL REFERENCES dispositivos.registros_dispositivo (id) ON DELETE CASCADE,
    canal VARCHAR(16) NOT NULL,
    email_id UUID,
    telefone_id UUID,
    codigo_hash VARCHAR(128) NOT NULL,
    tentativas INTEGER NOT NULL DEFAULT 0,
    tentativas_maximas INTEGER NOT NULL,
    reenvios INTEGER NOT NULL DEFAULT 0,
    reenvios_maximos INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    enviado_em TIMESTAMP WITH TIME ZONE,
    confirmado_em TIMESTAMP WITH TIME ZONE,
    expira_em TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_codigos_verificacao_dispositivo_registro_canal
    ON dispositivos.codigos_verificacao_dispositivo (registro_dispositivo_id, canal);

CREATE TABLE IF NOT EXISTS dispositivos.dispositivos_confiaveis (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL REFERENCES autenticacao.usuarios (id),
    cliente_ecossistema_id BIGINT NOT NULL REFERENCES catalogo.clientes_ecossistema (id),
    fingerprint VARCHAR(255) NOT NULL,
    plataforma VARCHAR(100) NOT NULL,
    versao_app_atual VARCHAR(100),
    chave_publica_atual TEXT,
    status VARCHAR(50) NOT NULL,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    ultimo_token_emitido_em TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_dispositivos_confiaveis_usuario_cliente_fingerprint
    ON dispositivos.dispositivos_confiaveis (usuario_id, cliente_ecossistema_id, fingerprint);

CREATE INDEX IF NOT EXISTS idx_dispositivos_confiaveis_cliente_status
    ON dispositivos.dispositivos_confiaveis (cliente_ecossistema_id, status);

CREATE TABLE IF NOT EXISTS dispositivos.tokens_dispositivo (
    id UUID PRIMARY KEY,
    dispositivo_id UUID NOT NULL REFERENCES dispositivos.dispositivos_confiaveis (id),
    registro_dispositivo_id UUID NOT NULL REFERENCES dispositivos.registros_dispositivo (id),
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    emitido_em TIMESTAMP WITH TIME ZONE NOT NULL,
    expira_em TIMESTAMP WITH TIME ZONE NOT NULL,
    revogado_em TIMESTAMP WITH TIME ZONE,
    motivo_revogacao VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS idx_tokens_dispositivo_registro_status
    ON dispositivos.tokens_dispositivo (registro_dispositivo_id, status);

CREATE INDEX IF NOT EXISTS idx_tokens_dispositivo_dispositivo_status
    ON dispositivos.tokens_dispositivo (dispositivo_id, status);
