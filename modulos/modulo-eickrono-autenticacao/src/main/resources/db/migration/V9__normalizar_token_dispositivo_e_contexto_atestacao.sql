UPDATE token_dispositivo token
SET dispositivo_id = dispositivo.id
FROM registro_dispositivo registro,
     dispositivos_identidade dispositivo
WHERE token.registro_id = registro.id
  AND dispositivo.fingerprint = registro.fingerprint
  AND dispositivo.usuario_sub = COALESCE(token.usuario_sub, registro.usuario_sub)
  AND token.dispositivo_id IS NULL;

ALTER TABLE dispositivos_identidade
    DROP CONSTRAINT IF EXISTS uk_dispositivos_identidade_pessoa_fingerprint,
    DROP CONSTRAINT IF EXISTS dispositivos_identidade_pessoa_id_fkey;

DROP INDEX IF EXISTS idx_dispositivos_identidade_pessoa;

ALTER TABLE token_dispositivo
    DROP COLUMN IF EXISTS usuario_sub,
    DROP COLUMN IF EXISTS fingerprint,
    DROP COLUMN IF EXISTS plataforma,
    DROP COLUMN IF EXISTS versao_app;

DROP INDEX IF EXISTS idx_token_dispositivo_usuario_status;

CREATE INDEX IF NOT EXISTS idx_token_dispositivo_registro_status
    ON token_dispositivo (registro_id, status);
