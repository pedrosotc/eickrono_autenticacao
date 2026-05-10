CREATE TABLE IF NOT EXISTS vinculos_organizacionais (
    id BIGSERIAL PRIMARY KEY,
    cadastro_id UUID NOT NULL UNIQUE,
    pessoa_id_perfil BIGINT NOT NULL,
    usuario_id_perfil VARCHAR(64) NOT NULL,
    organizacao_id VARCHAR(128) NOT NULL,
    nome_organizacao VARCHAR(255) NOT NULL,
    convite_codigo VARCHAR(128) NOT NULL,
    email_convidado VARCHAR(255),
    exige_conta_separada BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_vinculos_organizacionais_org_usuario
    ON vinculos_organizacionais (organizacao_id, usuario_id_perfil);
