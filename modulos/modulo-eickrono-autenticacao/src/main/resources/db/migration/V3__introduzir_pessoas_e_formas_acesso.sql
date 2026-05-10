CREATE TABLE IF NOT EXISTS pessoas_identidade (
    id SERIAL PRIMARY KEY,
    sub VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    nome VARCHAR(255) NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS pessoas_identidade_perfis (
    pessoa_id BIGINT NOT NULL REFERENCES pessoas_identidade(id) ON DELETE CASCADE,
    perfil VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS pessoas_identidade_papeis (
    pessoa_id BIGINT NOT NULL REFERENCES pessoas_identidade(id) ON DELETE CASCADE,
    papel VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS pessoas_formas_acesso (
    id SERIAL PRIMARY KEY,
    pessoa_id BIGINT NOT NULL REFERENCES pessoas_identidade(id) ON DELETE CASCADE,
    tipo VARCHAR(50) NOT NULL,
    provedor VARCHAR(100) NOT NULL,
    identificador VARCHAR(255) NOT NULL,
    principal BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    verificado_em TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_pessoas_formas_acesso UNIQUE (tipo, provedor, identificador)
);

CREATE INDEX IF NOT EXISTS idx_pessoas_formas_acesso_pessoa
    ON pessoas_formas_acesso (pessoa_id);

INSERT INTO pessoas_identidade (sub, email, nome, atualizado_em)
SELECT perfil.sub, perfil.email, perfil.nome, perfil.atualizado_em
FROM perfis_identidade perfil
WHERE NOT EXISTS (
    SELECT 1
    FROM pessoas_identidade pessoa
    WHERE pessoa.sub = perfil.sub
);

INSERT INTO pessoas_identidade_perfis (pessoa_id, perfil)
SELECT pessoa.id, perfil_legacy.perfil
FROM perfis_identidade perfil
JOIN pessoas_identidade pessoa ON pessoa.sub = perfil.sub
JOIN perfis_identidade_perfis perfil_legacy ON perfil_legacy.perfil_id = perfil.id
WHERE NOT EXISTS (
    SELECT 1
    FROM pessoas_identidade_perfis atual
    WHERE atual.pessoa_id = pessoa.id
      AND atual.perfil = perfil_legacy.perfil
);

INSERT INTO pessoas_identidade_papeis (pessoa_id, papel)
SELECT pessoa.id, papel_legacy.papel
FROM perfis_identidade perfil
JOIN pessoas_identidade pessoa ON pessoa.sub = perfil.sub
JOIN perfis_identidade_papeis papel_legacy ON papel_legacy.perfil_id = perfil.id
WHERE NOT EXISTS (
    SELECT 1
    FROM pessoas_identidade_papeis atual
    WHERE atual.pessoa_id = pessoa.id
      AND atual.papel = papel_legacy.papel
);

INSERT INTO pessoas_formas_acesso (pessoa_id, tipo, provedor, identificador, principal, criado_em, verificado_em)
SELECT pessoa.id, 'EMAIL_SENHA', 'EMAIL', pessoa.email, TRUE, pessoa.atualizado_em, pessoa.atualizado_em
FROM pessoas_identidade pessoa
WHERE NOT EXISTS (
    SELECT 1
    FROM pessoas_formas_acesso forma
    WHERE forma.tipo = 'EMAIL_SENHA'
      AND forma.provedor = 'EMAIL'
      AND forma.identificador = pessoa.email
);

INSERT INTO pessoas_formas_acesso (pessoa_id, tipo, provedor, identificador, principal, criado_em, verificado_em)
SELECT pessoa.id, 'SOCIAL', UPPER(vinculo.provedor), vinculo.identificador, FALSE, vinculo.vinculado_em, vinculo.vinculado_em
FROM vinculos_sociais vinculo
JOIN perfis_identidade perfil ON perfil.id = vinculo.perfil_id
JOIN pessoas_identidade pessoa ON pessoa.sub = perfil.sub
WHERE NOT EXISTS (
    SELECT 1
    FROM pessoas_formas_acesso forma
    WHERE forma.tipo = 'SOCIAL'
      AND forma.provedor = UPPER(vinculo.provedor)
      AND forma.identificador = vinculo.identificador
);
