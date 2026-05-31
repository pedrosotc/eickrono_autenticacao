CREATE TABLE IF NOT EXISTS auditoria.exclusoes_cadastro_produto (
    id UUID PRIMARY KEY,
    correlacao_id UUID NOT NULL UNIQUE,
    produto VARCHAR(64) NOT NULL,
    usuario_publico_produto VARCHAR(255),
    perfil_produto_id UUID,
    dry_run BOOLEAN NOT NULL,
    status VARCHAR(32) NOT NULL,
    solicitante VARCHAR(255),
    motivo TEXT NOT NULL,
    plano_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    resultado_json JSONB,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    iniciado_em TIMESTAMP WITH TIME ZONE,
    concluido_em TIMESTAMP WITH TIME ZONE,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE auditoria.exclusoes_cadastro_produto IS
    'Registro principal da simulacao ou execucao administrativa de exclusao de cadastro/produto.';
COMMENT ON COLUMN auditoria.exclusoes_cadastro_produto.correlacao_id IS
    'Identificador publico de rastreamento usado nos logs, respostas administrativas e etapas.';
COMMENT ON COLUMN auditoria.exclusoes_cadastro_produto.produto IS
    'Produto Eickrono alvo da operacao, por exemplo THIMISU.';
COMMENT ON COLUMN auditoria.exclusoes_cadastro_produto.usuario_publico_produto IS
    'Usuario publico no produto usado para resolver o perfil alvo quando informado.';
COMMENT ON COLUMN auditoria.exclusoes_cadastro_produto.perfil_produto_id IS
    'Identificador do vinculo usuario/produto quando a operacao for aberta por ID tecnico.';
COMMENT ON COLUMN auditoria.exclusoes_cadastro_produto.dry_run IS
    'Indica se o registro corresponde a uma simulacao sem alteracao de dados.';
COMMENT ON COLUMN auditoria.exclusoes_cadastro_produto.status IS
    'Estado global da simulacao/execucao: PLANEJADA, BLOQUEADA, EM_EXECUCAO, CONCLUIDA ou FALHOU.';
COMMENT ON COLUMN auditoria.exclusoes_cadastro_produto.solicitante IS
    'Identidade administrativa ou processo interno que solicitou a operacao.';
COMMENT ON COLUMN auditoria.exclusoes_cadastro_produto.motivo IS
    'Justificativa operacional obrigatoria para auditoria.';
COMMENT ON COLUMN auditoria.exclusoes_cadastro_produto.plano_json IS
    'Snapshot do plano gerado pelo dryRun para conferencia e reexecucao segura.';
COMMENT ON COLUMN auditoria.exclusoes_cadastro_produto.resultado_json IS
    'Snapshot do resultado final ou parcial da execucao.';

CREATE INDEX IF NOT EXISTS idx_exclusoes_cadastro_produto_produto_usuario
    ON auditoria.exclusoes_cadastro_produto (produto, usuario_publico_produto);

CREATE INDEX IF NOT EXISTS idx_exclusoes_cadastro_produto_status
    ON auditoria.exclusoes_cadastro_produto (status);

CREATE TABLE IF NOT EXISTS auditoria.exclusoes_cadastro_produto_etapas (
    id UUID PRIMARY KEY,
    exclusao_id UUID NOT NULL REFERENCES auditoria.exclusoes_cadastro_produto (id) ON DELETE CASCADE,
    ordem INTEGER NOT NULL,
    sistema VARCHAR(128) NOT NULL,
    tipo VARCHAR(64) NOT NULL,
    recurso VARCHAR(255) NOT NULL,
    quantidade_planejada BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    tentativas INTEGER NOT NULL DEFAULT 0,
    erro_codigo VARCHAR(128),
    erro_mensagem TEXT,
    resultado_json JSONB,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    iniciado_em TIMESTAMP WITH TIME ZONE,
    concluido_em TIMESTAMP WITH TIME ZONE,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_exclusoes_cadastro_produto_etapas_ordem UNIQUE (exclusao_id, ordem)
);

COMMENT ON TABLE auditoria.exclusoes_cadastro_produto_etapas IS
    'Etapas consultaveis e reexecutaveis da exclusao de cadastro/produto por sistema e recurso.';
COMMENT ON COLUMN auditoria.exclusoes_cadastro_produto_etapas.exclusao_id IS
    'Referencia ao registro principal da operacao administrativa.';
COMMENT ON COLUMN auditoria.exclusoes_cadastro_produto_etapas.ordem IS
    'Ordem planejada de execucao da etapa dentro da operacao.';
COMMENT ON COLUMN auditoria.exclusoes_cadastro_produto_etapas.sistema IS
    'Sistema participante da etapa, como KEYCLOAK, EICKRONO_AUTENTICACAO_SERVIDOR ou EICKRONO_THIMISU_BACKEND.';
COMMENT ON COLUMN auditoria.exclusoes_cadastro_produto_etapas.tipo IS
    'Acao planejada: APAGAR, ANONIMIZAR, PRESERVAR, NAO_TOCAR, MATERIALIZAR_PENDENCIA ou BLOQUEAR.';
COMMENT ON COLUMN auditoria.exclusoes_cadastro_produto_etapas.recurso IS
    'Tabela, endpoint, objeto de storage ou recurso administrativo afetado pela etapa.';
COMMENT ON COLUMN auditoria.exclusoes_cadastro_produto_etapas.quantidade_planejada IS
    'Quantidade calculada no dryRun para o recurso da etapa.';
COMMENT ON COLUMN auditoria.exclusoes_cadastro_produto_etapas.status IS
    'Estado da etapa: PLANEJADA, BLOQUEADA, EM_EXECUCAO, CONCLUIDA, FALHOU ou IGNORADA.';
COMMENT ON COLUMN auditoria.exclusoes_cadastro_produto_etapas.tentativas IS
    'Numero de tentativas de execucao da etapa para controle idempotente.';
COMMENT ON COLUMN auditoria.exclusoes_cadastro_produto_etapas.resultado_json IS
    'Resultado tecnico da etapa, incluindo IDs afetados ou resposta do sistema participante.';

CREATE INDEX IF NOT EXISTS idx_exclusoes_cadastro_produto_etapas_exclusao
    ON auditoria.exclusoes_cadastro_produto_etapas (exclusao_id, ordem);

CREATE INDEX IF NOT EXISTS idx_exclusoes_cadastro_produto_etapas_status
    ON auditoria.exclusoes_cadastro_produto_etapas (status);
