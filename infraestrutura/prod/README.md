# Estrutura de Produção

Esta pasta deve conter os artefatos de infraestrutura usados em produção:

- `terraform/`: módulos e configurações IaC para AWS.  
- `cloudflare/`: scripts e templates de configuração Cloudflare (WAF, mTLS Origin Pull, Rate Limits, DNS TXT como DMARC).
- `dns/`: scripts de validacao de SPF/DKIM/DMARC e entregabilidade de e-mail.
- `pipeline/`: definições de pipelines CI/CD (build, segurança, deploy e smoke tests).

O desenho canônico do pipeline agora está documentado em `pipeline/README.md`,
consumindo explicitamente:

- `eickrono-identidade-servidor`
- `eickrono-contas-servidor`
- `eickrono-autenticacao-servidor`

## Leitura operacional recomendada

- `CREDENCIAIS_RAPIDAS.md`
  Acesso direto a tokens, senhas, usuários e caminhos reais mais usados.
- `runbook_stg_aws_operacional.md`
  Caminho canônico e resumido para subir, atualizar e validar `stg`.
- `guia_subida_stg_aws.md`
  Trilha completa da subida de `stg`, incluindo contexto, causas-raiz e
  historico cronologico consolidado.
- `ecs/README.md`
  Caminho focado para build, push e rollout dos servicos no `ECS`.
- `cloudflare/README.md`
  Operacao de DNS e registros `TXT` via `Cloudflare`.
- `validacao_cabecalho_email_provedores.md`
  Validacao de entregabilidade e leitura de cabecalhos reais.

O ponto de entrada canônico agora passa a ser
`runbook_stg_aws_operacional.md`.

O arquivo `guia_subida_stg_aws.md` continua importante, mas deve ser tratado
como trilha historica ampliada. Para execucao focada por assunto, prefira o
runbook operacional novo e os arquivos especializados acima.

Para o rollout dos brokers sociais no Keycloak 26.5.5, a infraestrutura de produção também precisa contemplar:

- materialização das credenciais `${KEYCLOAK_IDP_<APP>_*}` usadas nos exports dos realms;
- `--features=instagram-broker` no processo de inicialização do Keycloak, caso o broker `instagram` permaneça habilitado.

## Materialização atual do broker Apple em produção

Para o `Sign in with Apple` brokerado pelo Keycloak do app `Thimisu`, a infraestrutura oficial de produção precisa materializar estes dois valores:

- `KEYCLOAK_IDP_THIMISU_APPLE_CLIENT_ID=com.eickrono.thimisu.oidc.prd`
- `KEYCLOAK_IDP_THIMISU_APPLE_CLIENT_SECRET_JWT=<jwt_gerado_com_a_key_principal>`

Origem operacional local desses valores:

- [prod keycloak-apple.env](/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/.local-secrets/apple/eickrono-oidc/prod/keycloak-apple.env:1)

Destino esperado em produção:

- `AWS Secrets Manager`, `SSM Parameter Store` ou mecanismo equivalente usado pelo deploy do Keycloak;
- nunca versionar esse `JWT` em `terraform/`, `pipeline/` ou `cloudflare/`.

Validação operacional esperada depois da materialização:

- o realm canônico deve ser `eickrono`;
- o broker `apple` deve existir e estar `enabled = true`;
- `config.clientId` deve ser `com.eickrono.thimisu.oidc.prd`;
- `config.clientSecret` deve aparecer apenas mascarado nas consultas administrativas.

Checklist mínimo pós-deploy no runtime do Keycloak:

1. entrar no container ou pod do Keycloak de produção;
2. autenticar o `kcadm.sh` no `realm master`;
3. confirmar que o realm `eickrono` existe;
4. consultar o broker `apple`;
5. verificar que `enabled = true` e `config.clientId = com.eickrono.thimisu.oidc.prd`.

Exemplo de comandos a executar já dentro do runtime do Keycloak:

```bash
/opt/keycloak/bin/kcadm.sh config credentials \
  --config /tmp/kcadm-prod.config \
  --server http://localhost:8080 \
  --realm master \
  --user "$KEYCLOAK_ADMIN" \
  --password "$KEYCLOAK_ADMIN_PASSWORD"

/opt/keycloak/bin/kcadm.sh get realms \
  --config /tmp/kcadm-prod.config

/opt/keycloak/bin/kcadm.sh get identity-provider/instances/apple \
  -r eickrono \
  --config /tmp/kcadm-prod.config
```

Sinais de sucesso:

- a lista de realms inclui `eickrono`;
- a resposta do broker mostra `alias = apple`;
- a resposta do broker mostra `enabled = true`;
- a resposta do broker mostra `config.clientId = com.eickrono.thimisu.oidc.prd`;
- `config.clientSecret` aparece mascarado.

> Os arquivos sensíveis (segredos, estados de Terraform etc.) **não** devem ser versionados neste repositório.
