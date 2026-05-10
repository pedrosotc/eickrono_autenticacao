CREATE TABLE IF NOT EXISTS seguranca.atestacoes_app_desafios (
    id UUID PRIMARY KEY,
    cliente_ecossistema_id BIGINT NOT NULL REFERENCES catalogo.clientes_ecossistema (id),
    sistema_origem_id BIGINT REFERENCES catalogo.sistemas_origem (id),
    usuario_id UUID REFERENCES autenticacao.usuarios (id),
    pessoa_id UUID,
    vinculo_cliente_id UUID REFERENCES autenticacao.usuarios_clientes_ecossistema (id),
    cadastro_id UUID REFERENCES autenticacao.cadastros_conta (id),
    registro_dispositivo_id UUID REFERENCES dispositivos.registros_dispositivo (id),
    dispositivo_id UUID REFERENCES dispositivos.dispositivos_confiaveis (id),
    operacao VARCHAR(50) NOT NULL,
    plataforma VARCHAR(20) NOT NULL,
    provedor_esperado VARCHAR(50) NOT NULL,
    desafio_base64 VARCHAR(128) NOT NULL UNIQUE,
    ip_solicitante VARCHAR(64),
    user_agent_solicitante VARCHAR(512),
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    expira_em TIMESTAMP WITH TIME ZONE NOT NULL,
    consumido_em TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_atestacoes_app_desafios_multiapp_operacao_expira
    ON seguranca.atestacoes_app_desafios (cliente_ecossistema_id, operacao, expira_em);

CREATE INDEX IF NOT EXISTS idx_atestacoes_app_desafios_multiapp_vinculo
    ON seguranca.atestacoes_app_desafios (vinculo_cliente_id);

CREATE INDEX IF NOT EXISTS idx_atestacoes_app_desafios_multiapp_registro
    ON seguranca.atestacoes_app_desafios (registro_dispositivo_id);

CREATE INDEX IF NOT EXISTS idx_atestacoes_app_desafios_multiapp_dispositivo
    ON seguranca.atestacoes_app_desafios (dispositivo_id);

CREATE TABLE IF NOT EXISTS seguranca.credenciais_atestacao_dispositivo (
    id UUID PRIMARY KEY,
    dispositivo_id UUID NOT NULL REFERENCES dispositivos.dispositivos_confiaveis (id),
    registro_dispositivo_id UUID REFERENCES dispositivos.registros_dispositivo (id),
    cliente_ecossistema_id BIGINT NOT NULL REFERENCES catalogo.clientes_ecossistema (id),
    usuario_id UUID NOT NULL REFERENCES autenticacao.usuarios (id),
    pessoa_id UUID,
    plataforma VARCHAR(20) NOT NULL,
    provedor VARCHAR(50) NOT NULL,
    identificador_credencial VARCHAR(128) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    criada_em TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizada_em TIMESTAMP WITH TIME ZONE NOT NULL,
    ultimo_uso_em TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_credenciais_atestacao_dispositivo_dispositivo_provedor
    ON seguranca.credenciais_atestacao_dispositivo (dispositivo_id, provedor);

CREATE INDEX IF NOT EXISTS idx_credenciais_atestacao_dispositivo_cliente_provedor_status
    ON seguranca.credenciais_atestacao_dispositivo (cliente_ecossistema_id, provedor, status);

CREATE TABLE IF NOT EXISTS seguranca.apple_app_attest_chaves (
    credencial_id UUID PRIMARY KEY REFERENCES seguranca.credenciais_atestacao_dispositivo (id) ON DELETE CASCADE,
    chave_id VARCHAR(128) NOT NULL UNIQUE,
    objeto_atestacao_base64 TEXT NOT NULL,
    contador_assinatura BIGINT NOT NULL,
    receipt_base64 TEXT,
    atestacao_inicial_em TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_apple_app_attest_chaves_chave_id_multiapp
    ON seguranca.apple_app_attest_chaves (chave_id);

CREATE TABLE IF NOT EXISTS auditoria.operacoes_atestadas (
    id UUID PRIMARY KEY,
    desafio_id UUID NOT NULL REFERENCES seguranca.atestacoes_app_desafios (id),
    cliente_ecossistema_id BIGINT NOT NULL REFERENCES catalogo.clientes_ecossistema (id),
    sistema_origem_id BIGINT REFERENCES catalogo.sistemas_origem (id),
    usuario_id UUID REFERENCES autenticacao.usuarios (id),
    pessoa_id UUID,
    vinculo_cliente_id UUID REFERENCES autenticacao.usuarios_clientes_ecossistema (id),
    cadastro_id UUID REFERENCES autenticacao.cadastros_conta (id),
    registro_dispositivo_id UUID REFERENCES dispositivos.registros_dispositivo (id),
    dispositivo_id UUID REFERENCES dispositivos.dispositivos_confiaveis (id),
    token_dispositivo_id UUID REFERENCES dispositivos.tokens_dispositivo (id),
    credencial_atestacao_id UUID REFERENCES seguranca.credenciais_atestacao_dispositivo (id),
    operacao VARCHAR(50) NOT NULL,
    tipo_evidencia VARCHAR(32) NOT NULL,
    provedor_utilizado VARCHAR(50),
    status_validacao VARCHAR(32) NOT NULL,
    status_operacao VARCHAR(32) NOT NULL,
    identificador_principal VARCHAR(255),
    resumo_resultado VARCHAR(512),
    ip_solicitante VARCHAR(64),
    user_agent_solicitante VARCHAR(512),
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_operacoes_atestadas_cliente_operacao_criado
    ON auditoria.operacoes_atestadas (cliente_ecossistema_id, operacao, criado_em);

CREATE INDEX IF NOT EXISTS idx_operacoes_atestadas_usuario_criado
    ON auditoria.operacoes_atestadas (usuario_id, criado_em);

CREATE INDEX IF NOT EXISTS idx_operacoes_atestadas_registro_criado
    ON auditoria.operacoes_atestadas (registro_dispositivo_id, criado_em);

CREATE INDEX IF NOT EXISTS idx_operacoes_atestadas_dispositivo_criado
    ON auditoria.operacoes_atestadas (dispositivo_id, criado_em);

CREATE TABLE IF NOT EXISTS auditoria.google_play_integrity_veredictos (
    operacao_atestada_id UUID PRIMARY KEY REFERENCES auditoria.operacoes_atestadas (id) ON DELETE CASCADE,
    request_hash VARCHAR(255),
    nonce_recebido VARCHAR(512),
    request_package_name VARCHAR(255),
    app_recognition_verdict VARCHAR(64),
    device_recognition_verdicts JSONB,
    app_licensing_verdict VARCHAR(64),
    account_activity_level VARCHAR(64),
    recent_device_activity_level VARCHAR(64),
    device_recall JSONB,
    token_emitido_em TIMESTAMP WITH TIME ZONE,
    avaliado_em TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS auditoria.usuarios_historico (
    id BIGSERIAL PRIMARY KEY,
    usuario_id UUID NOT NULL REFERENCES autenticacao.usuarios (id),
    pessoa_id UUID NOT NULL,
    acao VARCHAR(32) NOT NULL,
    sub_remoto VARCHAR(255),
    status_global VARCHAR(32) NOT NULL,
    origem_alteracao VARCHAR(64) NOT NULL,
    alterado_em TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_usuarios_historico_usuario_alterado
    ON auditoria.usuarios_historico (usuario_id, alterado_em DESC);

CREATE TABLE IF NOT EXISTS auditoria.usuarios_clientes_ecossistema_historico (
    id BIGSERIAL PRIMARY KEY,
    vinculo_id UUID NOT NULL REFERENCES autenticacao.usuarios_clientes_ecossistema (id),
    usuario_id UUID NOT NULL REFERENCES autenticacao.usuarios (id),
    cliente_ecossistema_id BIGINT NOT NULL REFERENCES catalogo.clientes_ecossistema (id),
    acao VARCHAR(32) NOT NULL,
    status_vinculo VARCHAR(32) NOT NULL,
    identificador_publico_cliente VARCHAR(255),
    origem_alteracao VARCHAR(64) NOT NULL,
    alterado_em TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_usuarios_clientes_ecossistema_historico_vinculo_alterado
    ON auditoria.usuarios_clientes_ecossistema_historico (vinculo_id, alterado_em DESC);
