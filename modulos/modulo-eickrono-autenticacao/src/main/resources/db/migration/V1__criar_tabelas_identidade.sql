CREATE TABLE IF NOT EXISTS perfis_identidade (
    id SERIAL PRIMARY KEY,
    sub VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    nome VARCHAR(255) NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS perfis_identidade_perfis (
    perfil_id BIGINT NOT NULL REFERENCES perfis_identidade(id) ON DELETE CASCADE,
    perfil VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS perfis_identidade_papeis (
    perfil_id BIGINT NOT NULL REFERENCES perfis_identidade(id) ON DELETE CASCADE,
    papel VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS vinculos_sociais (
    id SERIAL PRIMARY KEY,
    perfil_id BIGINT NOT NULL REFERENCES perfis_identidade(id) ON DELETE CASCADE,
    provedor VARCHAR(100) NOT NULL,
    identificador VARCHAR(255) NOT NULL,
    vinculado_em TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_vinculo_perfil_provedor
    ON vinculos_sociais (perfil_id, provedor);

CREATE TABLE IF NOT EXISTS auditoria_eventos_identidade (
    id SERIAL PRIMARY KEY,
    tipo_evento VARCHAR(100) NOT NULL,
    sujeito VARCHAR(255) NOT NULL,
    registrado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    detalhes TEXT
);
