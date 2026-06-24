# ECS STG

Esta pasta guarda os `task definitions` base do ambiente `stg` na AWS.

Arquivos:

- `auth-task-definition.stg.json`
- `identidade-task-definition.stg.json`
- `thimisu-backend-task-definition.stg.json`

Leitura correta dos papéis:

- `auth-task-definition.stg.json`: Keycloak + provider de autorização +
  integração interna com a API pública `eickrono-autenticacao`.
- `identidade-task-definition.stg.json`: serviço `identidade-servidor`, usado
  como backchannel canônico de `Pessoa`.

Nota de nomenclatura:

- `eickrono-autenticacao` acima é o nome operacional do serviço em runtime;
- o módulo físico que gera essa API, dentro deste repositório, é
  `modulos/modulo-eickrono-autenticacao`;
- o provider montado no Keycloak vem de
  `modulos/modulo-eickrono-keycloak`.

Regras:

- os placeholders `__...__` sao resolvidos pelo script `rollout_stg_service.sh`
- os arquivos assumem `ECS + Fargate`
- os certificados de `mTLS` estao modelados como volume `EFS`
- a explicacao operacional resumida fica em `../runbook_stg_aws_operacional.md`
- o historico ampliado continua em `../guia_subida_stg_aws.md`
- o caminho operacional preferencial de rollout agora e `rollout_stg_service.sh`

Nota operacional:

- as licoes da validacao STG de `2026-05-31` para
  `autenticacao-api-stg`, `thimisu-backend-stg`, `Cloud Map`, `RDS`,
  `Keycloak`, `Flyway`, plataforma `arm64` e erros reais de deploy estao em
  `../runbook_stg_aws_operacional.md`, na secao
  `Licoes aprendidas - validacao STG exclusao cadastro produto`.

## Regra canônica para namespaces de segredos

Para leitura humana e para novos segredos no `Secrets Manager`, a convenção
canônica aprovada é:

```text
/eickrono/<ambiente>/<dominio>/<categoria>/<identificador>/<tipo>
```

Aplicação prática:

- segredos de clientes do Keycloak:
  `/eickrono/<ambiente>/keycloak/clientes/<client-id>/secret`
- senha de admin do Keycloak:
  `/eickrono/<ambiente>/keycloak/admin/password`
- SMTP da identidade:
  `/eickrono/<ambiente>/identidade/smtp/primario/username`
  `/eickrono/<ambiente>/identidade/smtp/primario/password`
- segredo interno compartilhado:
  `/eickrono/<ambiente>/shared/jwt-interno/autenticacao/secret`

Namespaces materializados em `stg` para clientes do Keycloak:

| Namespace | Client ID | Consumidores |
| --- | --- | --- |
| `/eickrono/stg/keycloak/clientes/autenticacao-servidor/secret` | `autenticacao-servidor` | `identidade-stg`, `auth-stg` |
| `/eickrono/stg/keycloak/clientes/eickrono-keycloak/secret` | `eickrono-keycloak` | `auth-stg` |
| `/eickrono/stg/keycloak/clientes/thimisu-backend/secret` | `thimisu-backend` | `thimisu-backend-stg`, `auth-stg` |

## Estado atual da separacao de banco em STG

Hoje os `task definitions` de `stg` ja estao separados por servico no nivel de
nome de banco:

- `auth` usa `keycloak_stg`
- `identidade` usa `eickrono_identidade_stg`
- `thimisu-backend` usa `eickrono_thimisu_stg`

Mas essa separacao ainda acontece no mesmo host RDS:

- `eickrono-stg-postgres.cdu8yi4qkl16.sa-east-1.rds.amazonaws.com`

Ou seja:

- a separacao atual de `stg` na AWS ja evita mistura por banco;
- a separacao fisica completa por host/instancia ainda continua como etapa
  futura da migracao.

Para preparar essa evolucao sem novo refactor estrutural, o script de rollout
ja aceita overrides por servico:

- `AUTH_KC_DB_HOST`
- `AUTH_KC_DB_PORT`
- `AUTH_KC_DB_NAME`
- `AUTH_KC_DB_USERNAME`
- `AUTH_KC_DB_PASSWORD_SECRET_ARN`
- `IDENTIDADE_DB_HOST`
- `IDENTIDADE_DB_PORT`
- `IDENTIDADE_DB_NAME`
- `IDENTIDADE_DB_USERNAME`
- `IDENTIDADE_DB_PASSWORD_SECRET_ARN`
- `THIMISU_DB_HOST`
- `THIMISU_DB_PORT`
- `THIMISU_DB_NAME`
- `THIMISU_DB_USERNAME`
- `THIMISU_DB_PASSWORD_SECRET_ARN`

Se nada for informado, o script usa o host compartilhado atual de `stg` como
default.

Arquivo exemplo:

- `stg-db-overrides.example.env`

Uso rapido:

```bash
bash infraestrutura/prod/ecs/rollout_stg_service.sh \
  --service identidade \
  --db-overrides-file infraestrutura/prod/ecs/stg-db-overrides.example.env \
  --image 531708494702.dkr.ecr.sa-east-1.amazonaws.com/eickrono-identidade-servidor:stg-20260429-001 \
  --dry-run
```

## Script oficial de rollout

Arquivo:

- `build_push_stg_image.sh`
- `rollout_stg_service.sh`
- `configure_stg_rds_rotation_redeploy.sh`
- `summarize_stg_db_config.sh`
- `validate_stg_task_templates.sh`

## Redeploy obrigatorio apos rotacao do segredo RDS

### Problema que esta automacao resolve

Os servicos `autenticacao-api-stg`, `auth-stg`, `identidade-stg` e
`thimisu-backend-stg` consomem a senha do PostgreSQL via `Secrets Manager`,
injetada como variavel de ambiente na task ECS no momento do start.

Quando o segredo gerenciado do RDS rotaciona:

- o valor em `AWSCURRENT` muda imediatamente no `Secrets Manager`;
- a task ECS **nao** reread o segredo sozinha;
- a task continua com a senha antiga em memoria;
- a proxima abertura de conexao nova com o banco pode falhar com
  `password authentication failed`.

Por isso, neste ambiente, a rotacao do segredo do banco exige
**redeploy forcado obrigatorio** dos quatro servicos consumidores:

- `auth-stg`
- `autenticacao-api-stg`
- `identidade-stg`
- `thimisu-backend-stg`

### Automacao padrao

Arquivo:

- `configure_stg_rds_rotation_redeploy.sh`

Comportamento:

1. cria ou atualiza uma funcao Lambda;
2. cria ou atualiza uma regra EventBridge;
3. escuta o evento `RotationSucceeded` do `Secrets Manager`;
4. valida se o segredo do evento e o segredo RDS monitorado;
5. executa `ecs update-service --force-new-deployment` sequencialmente para:
   - `autenticacao-api-stg`
   - `auth-stg`
   - `identidade-stg`
   - `thimisu-backend-stg`
6. aguarda `services_stable` de cada servico antes de iniciar o proximo.

A espera sequencial evita que todos os servicos dupliquem tasks ao mesmo tempo
e estourem o limite de conexoes do RDS durante a recuperacao.

Codigo da Lambda:

- `lambda/rds_rotation_ecs_redeploy/handler.py`

### Regra EventBridge utilizada

A regra instalada por este script observa exatamente os eventos de rotacao
bem-sucedida do `Secrets Manager`, usando o tipo documentado pela AWS para
rotacao:

```json
{
  "source": ["aws.secretsmanager"],
  "detail-type": [
    "AWS Service Event via CloudTrail",
    "AWS API Call via CloudTrail"
  ],
  "detail": {
    "eventSource": ["secretsmanager.amazonaws.com"],
    "eventName": ["RotationSucceeded"]
  }
}
```

Observacao importante:

- o filtro final por segredo monitorado acontece tambem dentro da Lambda;
- isso evita dependencia do formato exato de `arn` vs `aRN` no payload;
- a Lambda tambem aceita `detail.additionalEventData.SecretId`, que foi o
  formato observado na rotacao gerenciada do RDS em `2026-05-24`;
- e protege contra eventos de rotacao de outros segredos da conta.

### Comando oficial para instalar ou atualizar a automacao em `stg`

Validacao segura:

```bash
bash ./infraestrutura/prod/ecs/configure_stg_rds_rotation_redeploy.sh \
  --dry-run
```

Execucao real:

```bash
bash ./infraestrutura/prod/ecs/configure_stg_rds_rotation_redeploy.sh \
  --profile Codex-cli_aws
```

Defaults canonicos embutidos no script:

- `cluster = eickrono-stg`
- `services = autenticacao-api-stg,auth-stg,identidade-stg,thimisu-backend-stg`
- `secret-arn = arn:aws:secretsmanager:sa-east-1:531708494702:secret:rds!db-7df15f56-c831-40b7-be42-ebd935108b06-22Dwvf`
- `function-name = eickrono-stg-rds-rotation-ecs-redeploy`
- `rule-name = eickrono-stg-rds-rotation-succeeded`
- `role-name = eickrono-stg-rds-rotation-ecs-redeploy-role`
- `timeout = 900`
- `service-stable-waiter-delay-seconds = 15`
- `service-stable-waiter-max-attempts = 40`

### O que validar depois da instalacao

1. Regra EventBridge existente:

```bash
aws events describe-rule \
  --name eickrono-stg-rds-rotation-succeeded \
  --profile Codex-cli_aws \
  --region sa-east-1
```

2. Target da regra apontando para a Lambda:

```bash
aws events list-targets-by-rule \
  --rule eickrono-stg-rds-rotation-succeeded \
  --profile Codex-cli_aws \
  --region sa-east-1
```

3. Lambda instalada com o segredo e cluster corretos:

```bash
aws lambda get-function-configuration \
  --function-name eickrono-stg-rds-rotation-ecs-redeploy \
  --profile Codex-cli_aws \
  --region sa-east-1
```

Validar:

- `TARGET_SECRET_ARN` = segredo gerenciado do RDS de `stg`
- `ECS_CLUSTER` = `eickrono-stg`
- `ECS_SERVICES` = `autenticacao-api-stg,auth-stg,identidade-stg,thimisu-backend-stg`

4. Permissao da Lambda para chamar ECS:

```bash
aws iam get-role-policy \
  --role-name eickrono-stg-rds-rotation-ecs-redeploy-role \
  --policy-name eickrono-stg-rds-rotation-ecs-redeploy-role-ecs-redeploy \
  --profile Codex-cli_aws \
  --region sa-east-1
```

### Testes locais da automacao

Arquivos:

- `../tests/configure_stg_rds_rotation_redeploy_test.sh`
- `../tests/rds_secret_rotation_ecs_redeploy_lambda_test.py`

Execucao:

```bash
bash infraestrutura/prod/tests/configure_stg_rds_rotation_redeploy_test.sh
PYTHONPATH=infraestrutura/prod python3 -m unittest \
  infraestrutura/prod/tests/rds_secret_rotation_ecs_redeploy_lambda_test.py
```

## Fallback por erro de senha antiga do RDS

### Problema que o fallback cobre

A automacao principal depende do evento `RotationSucceeded`. Se esse evento nao
for processado, ou se existir task antiga/orfa ainda rodando com senha anterior,
os logs podem registrar:

```text
password authentication failed for user "eickrono_admin"
```

O fallback observa esse erro nos log groups dos servicos e so executa redeploy
depois de validar que o segredo atual ainda conecta no RDS.

### Como o fallback valida o segredo atual

A Lambda nao carrega driver PostgreSQL. Ela inicia uma task Fargate de
validacao com `psql`, usando a task definition configurada em
`VALIDATION_TASK_DEFINITION`.

Fluxo:

1. CloudWatch Logs envia para a Lambda um evento que bate no filtro.
2. Lambda verifica cooldown no Parameter Store.
3. Lambda executa task Fargate de validacao.
4. A task executa `SELECT 1` no banco configurado.
5. Se a task termina com exit code `0`, a Lambda forca redeploy dos servicos.
6. A Lambda aguarda `services_stable` de cada servico antes de redeployar o proximo.
7. Se a task falha, a Lambda nao redeploya e registra erro operacional.

### Script oficial para instalar ou atualizar o fallback em `stg`

Validacao segura:

```bash
bash ./infraestrutura/prod/ecs/configure_stg_rds_password_auth_failure_fallback.sh \
  --dry-run
```

Execucao real:

```bash
bash ./infraestrutura/prod/ecs/configure_stg_rds_password_auth_failure_fallback.sh \
  --profile Codex-cli_aws
```

Defaults canonicos embutidos no script:

- `cluster = eickrono-stg`
- `services = autenticacao-api-stg,auth-stg,identidade-stg,thimisu-backend-stg`
- `log-groups = /ecs/stg/autenticacao,/ecs/stg/auth,/ecs/stg/identidade,/ecs/stg/thimisu-backend`
- `function-name = eickrono-stg-rds-password-auth-failure-fallback`
- `role-name = eickrono-stg-rds-password-auth-failure-fallback-role`
- `filter-name = eickrono-stg-rds-password-auth-failure-fallback`
- `validation-task-definition = eickrono-stg-db-query-codex:1`
- `validation-container-name = psql`
- `validation-database = eickrono_identidade_stg`
- `cooldown-parameter-name = /eickrono/stg/rds-password-auth-failure-fallback/last-run`
- `cooldown-seconds = 900`
- `timeout = 900`
- `service-stable-waiter-delay-seconds = 15`
- `service-stable-waiter-max-attempts = 40`

### Testes locais do fallback

Arquivos:

- `../tests/configure_stg_rds_password_auth_failure_fallback_test.sh`
- `../tests/rds_password_auth_failure_fallback_lambda_test.py`

Execucao:

```bash
bash infraestrutura/prod/tests/configure_stg_rds_password_auth_failure_fallback_test.sh
PYTHONPATH=infraestrutura/prod python3 -m unittest \
  infraestrutura/prod/tests/rds_password_auth_failure_fallback_lambda_test.py
```

### Build e push da imagem

Exemplo de validacao segura:

```bash
bash ./infraestrutura/prod/ecs/build_push_stg_image.sh \
  --service identidade \
  --tag stg-20260429-001 \
  --dry-run
```

Exemplo de execucao real:

```bash
export EICKRONO_STG_HISTORICO="/caminho/para/historico.md"

bash ./infraestrutura/prod/ecs/build_push_stg_image.sh \
  --service identidade \
  --tag stg-20260429-001 \
  --profile Codex-cli_aws
```

Exemplo de validacao segura:

```bash
bash ./infraestrutura/prod/ecs/rollout_stg_service.sh \
  --service identidade \
  --image 531708494702.dkr.ecr.sa-east-1.amazonaws.com/eickrono-identidade-servidor:stg-20260429-001 \
  --dry-run
```

Exemplo de execucao real:

```bash
export EICKRONO_STG_HISTORICO="/caminho/para/historico.md"

bash ./infraestrutura/prod/ecs/rollout_stg_service.sh \
  --service identidade \
  --image 531708494702.dkr.ecr.sa-east-1.amazonaws.com/eickrono-identidade-servidor:stg-20260429-001
```

## Teste local do script

Arquivo:

- `../tests/build_push_stg_image_test.sh`
- `../tests/rollout_stg_service_test.sh`

Execucao:

```bash
bash infraestrutura/prod/tests/build_push_stg_image_test.sh
bash infraestrutura/prod/tests/rollout_stg_service_test.sh
bash infraestrutura/prod/tests/summarize_stg_db_config_test.sh
bash infraestrutura/prod/tests/validate_stg_task_templates_test.sh
```
