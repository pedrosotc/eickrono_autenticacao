CREATE TABLE IF NOT EXISTS contas (
    id SERIAL PRIMARY KEY,
    numero VARCHAR(30) NOT NULL UNIQUE,
    cliente_id VARCHAR(100) NOT NULL,
    saldo NUMERIC(18,2) NOT NULL,
    criada_em TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizada_em TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS transacoes (
    id SERIAL PRIMARY KEY,
    conta_id BIGINT NOT NULL REFERENCES contas(id) ON DELETE CASCADE,
    tipo VARCHAR(20) NOT NULL,
    valor NUMERIC(18,2) NOT NULL,
    efetivada_em TIMESTAMP WITH TIME ZONE NOT NULL,
    descricao TEXT
);

CREATE INDEX IF NOT EXISTS idx_transacoes_conta_data
    ON transacoes (conta_id, efetivada_em DESC);

CREATE TABLE IF NOT EXISTS auditoria_eventos_contas (
    id SERIAL PRIMARY KEY,
    tipo_evento VARCHAR(100) NOT NULL,
    sujeito VARCHAR(255) NOT NULL,
    registrado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    detalhes TEXT
);

CREATE TABLE IF NOT EXISTS auditoria_acessos_contas (
    id SERIAL PRIMARY KEY,
    sujeito VARCHAR(255) NOT NULL,
    endpoint VARCHAR(255) NOT NULL,
    registrado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    detalhes TEXT
);
