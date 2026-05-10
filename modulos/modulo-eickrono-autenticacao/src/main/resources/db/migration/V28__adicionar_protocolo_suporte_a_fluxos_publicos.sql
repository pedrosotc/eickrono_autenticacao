ALTER TABLE cadastros_conta
    ADD COLUMN IF NOT EXISTS protocolo_suporte VARCHAR(40);

UPDATE cadastros_conta
SET protocolo_suporte = 'CAD-' || UPPER(
        SUBSTRING(MD5(cadastro_id::TEXT), 1, 8) || '-' ||
        SUBSTRING(MD5(cadastro_id::TEXT), 9, 4) || '-' ||
        SUBSTRING(MD5(cadastro_id::TEXT), 13, 4) || '-' ||
        SUBSTRING(MD5(cadastro_id::TEXT), 17, 4) || '-' ||
        SUBSTRING(MD5(cadastro_id::TEXT), 21, 12)
    )
WHERE protocolo_suporte IS NULL;

ALTER TABLE cadastros_conta
    ALTER COLUMN protocolo_suporte SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_cadastros_conta_protocolo_suporte
    ON cadastros_conta (protocolo_suporte);

ALTER TABLE recuperacoes_senha
    ADD COLUMN IF NOT EXISTS protocolo_suporte VARCHAR(40);

UPDATE recuperacoes_senha
SET protocolo_suporte = 'REC-' || UPPER(
        SUBSTRING(MD5(fluxo_id::TEXT), 1, 8) || '-' ||
        SUBSTRING(MD5(fluxo_id::TEXT), 9, 4) || '-' ||
        SUBSTRING(MD5(fluxo_id::TEXT), 13, 4) || '-' ||
        SUBSTRING(MD5(fluxo_id::TEXT), 17, 4) || '-' ||
        SUBSTRING(MD5(fluxo_id::TEXT), 21, 12)
    )
WHERE protocolo_suporte IS NULL;

ALTER TABLE recuperacoes_senha
    ALTER COLUMN protocolo_suporte SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_recuperacoes_senha_protocolo_suporte
    ON recuperacoes_senha (protocolo_suporte);
