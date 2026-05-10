INSERT INTO catalogo.clientes_ecossistema (
    codigo,
    nome,
    tipo,
    client_id_oidc,
    ativo,
    criado_em,
    atualizado_em
)
VALUES (
    'eickrono-thimisu-app',
    'Eickrono Thimisu App',
    'APP_MOVEL',
    NULL,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO catalogo.sistemas_origem (
    identificador_sistema,
    descricao,
    ativo,
    criado_em,
    atualizado_em
)
SELECT origem.identificador_sistema,
       origem.descricao,
       TRUE,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM (
    SELECT 'identidade-servidor' AS identificador_sistema,
           'Backend legado do produto thimisu que abre fluxos internos por backchannel' AS descricao
    UNION
    SELECT 'servidor-autorizacao',
           'Servidor de autorizacao do ecossistema para fluxos internos de autenticacao'
    UNION
    SELECT DISTINCT BTRIM(cadastro.sistema_solicitante) AS identificador_sistema,
           CASE
               WHEN BTRIM(cadastro.sistema_solicitante) = 'app-flutter-publico'
                   THEN 'Origem publica legada observada no fluxo de cadastro do app movel'
               WHEN BTRIM(cadastro.sistema_solicitante) = 'identidade-servidor'
                   THEN 'Backend legado do produto thimisu que abre fluxos internos por backchannel'
               ELSE 'Origem legada observada durante o seed inicial do catalogo'
           END AS descricao
    FROM cadastros_conta cadastro
    WHERE cadastro.sistema_solicitante IS NOT NULL
      AND BTRIM(cadastro.sistema_solicitante) <> ''
) origem
ON CONFLICT (identificador_sistema) DO NOTHING;
