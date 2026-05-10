CREATE TABLE IF NOT EXISTS dispositivos_identidade (
    id BIGSERIAL PRIMARY KEY,
    pessoa_id BIGINT NOT NULL REFERENCES pessoas_identidade(id) ON DELETE CASCADE,
    fingerprint VARCHAR(255) NOT NULL,
    plataforma VARCHAR(100) NOT NULL,
    versao_app VARCHAR(100),
    chave_publica TEXT,
    status VARCHAR(50) NOT NULL,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    ultimo_token_emitido_em TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_dispositivos_identidade_pessoa_fingerprint UNIQUE (pessoa_id, fingerprint)
);

CREATE INDEX IF NOT EXISTS idx_dispositivos_identidade_pessoa
    ON dispositivos_identidade (pessoa_id);

ALTER TABLE token_dispositivo
    ADD COLUMN IF NOT EXISTS dispositivo_id BIGINT;

ALTER TABLE token_dispositivo
    ADD CONSTRAINT fk_token_dispositivo_dispositivo
        FOREIGN KEY (dispositivo_id)
        REFERENCES dispositivos_identidade(id);

CREATE INDEX IF NOT EXISTS idx_token_dispositivo_dispositivo
    ON token_dispositivo (dispositivo_id);

INSERT INTO dispositivos_identidade (
    pessoa_id,
    fingerprint,
    plataforma,
    versao_app,
    chave_publica,
    status,
    criado_em,
    atualizado_em,
    ultimo_token_emitido_em
)
SELECT
    pessoa.id,
    registro.fingerprint,
    registro.plataforma,
    registro.versao_app,
    registro.chave_publica,
    'ATIVO',
    COALESCE(registro.confirmado_em, registro.criado_em),
    COALESCE(MAX(token.emitido_em), registro.confirmado_em, registro.criado_em),
    MAX(token.emitido_em)
FROM registro_dispositivo registro
JOIN pessoas_identidade pessoa
    ON pessoa.sub = registro.usuario_sub
LEFT JOIN token_dispositivo token
    ON token.registro_id = registro.id
WHERE registro.usuario_sub IS NOT NULL
GROUP BY pessoa.id, registro.fingerprint, registro.plataforma, registro.versao_app, registro.chave_publica,
         registro.confirmado_em, registro.criado_em
ON CONFLICT (pessoa_id, fingerprint) DO NOTHING;

UPDATE token_dispositivo token
SET dispositivo_id = dispositivo.id
FROM registro_dispositivo registro
JOIN pessoas_identidade pessoa
    ON pessoa.sub = registro.usuario_sub
JOIN dispositivos_identidade dispositivo
    ON dispositivo.pessoa_id = pessoa.id
   AND dispositivo.fingerprint = registro.fingerprint
WHERE token.registro_id = registro.id
  AND token.dispositivo_id IS NULL;

CREATE TABLE IF NOT EXISTS eventos_offline_dispositivo (
    id UUID PRIMARY KEY,
    dispositivo_id BIGINT NOT NULL REFERENCES dispositivos_identidade(id) ON DELETE CASCADE,
    token_id UUID REFERENCES token_dispositivo(id) ON DELETE SET NULL,
    tipo_evento VARCHAR(80) NOT NULL,
    detalhes TEXT,
    ocorrido_em TIMESTAMP WITH TIME ZONE NOT NULL,
    registrado_em TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_eventos_offline_dispositivo_dispositivo
    ON eventos_offline_dispositivo (dispositivo_id, ocorrido_em DESC);
