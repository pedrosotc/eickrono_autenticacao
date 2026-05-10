ALTER TABLE cadastros_conta
    ADD COLUMN IF NOT EXISTS pessoa_id_perfil BIGINT;

CREATE INDEX IF NOT EXISTS idx_cadastros_conta_pessoa_id_perfil
    ON cadastros_conta (pessoa_id_perfil);

ALTER TABLE registro_dispositivo
    ADD COLUMN IF NOT EXISTS pessoa_id_perfil BIGINT;

CREATE INDEX IF NOT EXISTS idx_registro_dispositivo_pessoa_id_perfil
    ON registro_dispositivo (pessoa_id_perfil);

ALTER TABLE dispositivos_identidade
    ADD COLUMN IF NOT EXISTS usuario_sub VARCHAR(255),
    ADD COLUMN IF NOT EXISTS pessoa_id_perfil BIGINT;

UPDATE dispositivos_identidade dispositivo
SET usuario_sub = pessoa.sub
FROM pessoas_identidade pessoa
WHERE dispositivo.pessoa_id = pessoa.id
  AND dispositivo.usuario_sub IS NULL;

ALTER TABLE dispositivos_identidade
    ALTER COLUMN pessoa_id DROP NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_dispositivos_identidade_usuario_fingerprint
    ON dispositivos_identidade (usuario_sub, fingerprint);

CREATE INDEX IF NOT EXISTS idx_dispositivos_identidade_usuario_sub
    ON dispositivos_identidade (usuario_sub);

CREATE INDEX IF NOT EXISTS idx_dispositivos_identidade_pessoa_id_perfil
    ON dispositivos_identidade (pessoa_id_perfil);

ALTER TABLE atestacoes_app_desafios
    ADD COLUMN IF NOT EXISTS usuario_sub VARCHAR(255),
    ADD COLUMN IF NOT EXISTS pessoa_id_perfil BIGINT,
    ADD COLUMN IF NOT EXISTS cadastro_id UUID,
    ADD COLUMN IF NOT EXISTS registro_dispositivo_id UUID;

CREATE INDEX IF NOT EXISTS idx_atestacoes_app_desafios_usuario_sub
    ON atestacoes_app_desafios (usuario_sub);

CREATE INDEX IF NOT EXISTS idx_atestacoes_app_desafios_pessoa_id_perfil
    ON atestacoes_app_desafios (pessoa_id_perfil);
