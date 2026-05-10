CREATE TABLE IF NOT EXISTS autenticacao.pendencias_integracao_produto (
    id uuid PRIMARY KEY,
    cliente_ecossistema_id bigint NOT NULL
        REFERENCES catalogo.clientes_ecossistema (id),
    tipo_operacao varchar(64) NOT NULL,
    uri_endpoint varchar(512) NOT NULL,
    metodo_http varchar(16) NOT NULL,
    payload_json jsonb NOT NULL,
    idempotency_key varchar(255) NOT NULL,
    versao_contrato varchar(32) NOT NULL,
    cadastro_id uuid,
    pessoa_id_central bigint,
    perfil_sistema_id uuid,
    identificador_publico_sistema varchar(255),
    status_pendencia varchar(32) NOT NULL,
    tentativas_realizadas integer NOT NULL DEFAULT 0,
    ultima_tentativa_em timestamptz,
    proxima_tentativa_em timestamptz NOT NULL,
    codigo_ultimo_erro varchar(128),
    mensagem_ultimo_erro varchar(2000),
    processando_por_instancia varchar(255),
    processando_desde timestamptz,
    criado_em timestamptz NOT NULL,
    atualizado_em timestamptz NOT NULL,
    CONSTRAINT uq_pendencia_integracao_produto_idempotency
        UNIQUE (cliente_ecossistema_id, idempotency_key),
    CONSTRAINT ck_pendencia_integracao_produto_metodo_http
        CHECK (metodo_http IN ('POST', 'PUT', 'PATCH', 'DELETE'))
);

CREATE INDEX IF NOT EXISTS idx_pendencias_integracao_produto_status_proxima
    ON autenticacao.pendencias_integracao_produto (status_pendencia, proxima_tentativa_em);

CREATE INDEX IF NOT EXISTS idx_pendencias_integracao_produto_cliente
    ON autenticacao.pendencias_integracao_produto (cliente_ecossistema_id);

CREATE INDEX IF NOT EXISTS idx_pendencias_integracao_produto_cadastro
    ON autenticacao.pendencias_integracao_produto (cadastro_id);

CREATE TABLE IF NOT EXISTS autenticacao.parametros_scheduler_integracao_produto (
    id smallint PRIMARY KEY DEFAULT 1,
    habilitado boolean NOT NULL DEFAULT true,
    tempo_entre_tentativas_segundos integer NOT NULL,
    quantidade_maxima_tentativas integer NOT NULL,
    quantidade_maxima_itens_por_ciclo integer NOT NULL,
    timeout_sondagem_millis integer NOT NULL,
    timeout_entrega_millis integer NOT NULL,
    criado_em timestamptz NOT NULL,
    atualizado_em timestamptz NOT NULL,
    CONSTRAINT ck_parametros_scheduler_integracao_produto_singleton
        CHECK (id = 1)
);

INSERT INTO autenticacao.parametros_scheduler_integracao_produto (
    id,
    habilitado,
    tempo_entre_tentativas_segundos,
    quantidade_maxima_tentativas,
    quantidade_maxima_itens_por_ciclo,
    timeout_sondagem_millis,
    timeout_entrega_millis,
    criado_em,
    atualizado_em
) VALUES (
    1,
    true,
    300,
    10,
    50,
    3000,
    10000,
    NOW(),
    NOW()
)
ON CONFLICT (id) DO UPDATE
SET habilitado = EXCLUDED.habilitado,
    tempo_entre_tentativas_segundos = EXCLUDED.tempo_entre_tentativas_segundos,
    quantidade_maxima_tentativas = EXCLUDED.quantidade_maxima_tentativas,
    quantidade_maxima_itens_por_ciclo = EXCLUDED.quantidade_maxima_itens_por_ciclo,
    timeout_sondagem_millis = EXCLUDED.timeout_sondagem_millis,
    timeout_entrega_millis = EXCLUDED.timeout_entrega_millis,
    atualizado_em = NOW();

CREATE TABLE IF NOT EXISTS autenticacao.controles_integracao_produto (
    id bigserial PRIMARY KEY,
    cliente_ecossistema_id bigint NOT NULL
        REFERENCES catalogo.clientes_ecossistema (id),
    escritas_internas_habilitadas boolean NOT NULL DEFAULT true,
    produto_em_manutencao boolean NOT NULL DEFAULT false,
    inicio_manutencao timestamptz,
    fim_manutencao timestamptz,
    motivo_manutencao varchar(512),
    tempo_entre_tentativas_segundos_override integer,
    quantidade_maxima_tentativas_override integer,
    timeout_sondagem_millis_override integer,
    timeout_entrega_millis_override integer,
    criado_em timestamptz NOT NULL,
    atualizado_em timestamptz NOT NULL,
    CONSTRAINT uq_controles_integracao_produto_cliente
        UNIQUE (cliente_ecossistema_id)
);

INSERT INTO autenticacao.controles_integracao_produto (
    cliente_ecossistema_id,
    escritas_internas_habilitadas,
    produto_em_manutencao,
    inicio_manutencao,
    fim_manutencao,
    motivo_manutencao,
    tempo_entre_tentativas_segundos_override,
    quantidade_maxima_tentativas_override,
    timeout_sondagem_millis_override,
    timeout_entrega_millis_override,
    criado_em,
    atualizado_em
)
SELECT
    c.id,
    true,
    false,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NOW(),
    NOW()
FROM catalogo.clientes_ecossistema c
WHERE c.ativo = true
ON CONFLICT (cliente_ecossistema_id) DO NOTHING;
