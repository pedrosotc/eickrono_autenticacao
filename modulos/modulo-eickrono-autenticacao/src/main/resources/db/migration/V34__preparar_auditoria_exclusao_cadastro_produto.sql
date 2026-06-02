ALTER TABLE auditoria.usuarios_clientes_ecossistema_historico
    ALTER COLUMN vinculo_id DROP NOT NULL,
    ALTER COLUMN usuario_id DROP NOT NULL;

ALTER TABLE auditoria.usuarios_clientes_ecossistema_historico
    ADD COLUMN IF NOT EXISTS anonimizado_em TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS correlacao_exclusao_cadastro_produto UUID;

COMMENT ON COLUMN auditoria.usuarios_clientes_ecossistema_historico.anonimizado_em IS
    'Momento em que os identificadores pessoais diretos foram minimizados por exclusao de cadastro/produto.';
COMMENT ON COLUMN auditoria.usuarios_clientes_ecossistema_historico.correlacao_exclusao_cadastro_produto IS
    'Correlacao da operacao administrativa que minimizou o historico preservado.';

CREATE INDEX IF NOT EXISTS idx_usuarios_clientes_ecossistema_historico_exclusao
    ON auditoria.usuarios_clientes_ecossistema_historico (correlacao_exclusao_cadastro_produto);

ALTER TABLE auditoria.usuarios_historico
    ALTER COLUMN usuario_id DROP NOT NULL;

ALTER TABLE auditoria.usuarios_historico
    ADD COLUMN IF NOT EXISTS anonimizado_em TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS correlacao_exclusao_cadastro_produto UUID;

COMMENT ON COLUMN auditoria.usuarios_historico.anonimizado_em IS
    'Momento em que a referencia direta ao usuario de autenticacao foi minimizada por exclusao de cadastro/produto.';
COMMENT ON COLUMN auditoria.usuarios_historico.correlacao_exclusao_cadastro_produto IS
    'Correlacao da operacao administrativa que minimizou o historico preservado.';

CREATE INDEX IF NOT EXISTS idx_usuarios_historico_exclusao_cadastro_produto
    ON auditoria.usuarios_historico (correlacao_exclusao_cadastro_produto);
