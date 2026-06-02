# Guia de Operação em Produção

Este guia descreve processos para operar o ecossistema **Eickrono Autenticação** em produção na AWS, protegido por Cloudflare.

## Arquitetura em produção

- **EKS/ECS:** hospeda os contêineres das APIs e do Keycloak (cluster gerenciado).  
- **ALB/NLB:** balanceamento de carga com suporte a mTLS e roteamento por host/path.  
- **Cloudflare:** WAF, Rate Limiting, mTLS Origin Pull e caching seletivo de recursos estáticos.  
- **RDS PostgreSQL:** instâncias multi-AZ com backups automáticos, replicação e monitoramento de performance.  
- **Secrets Manager / Parameter Store:** armazenamento de segredos, certificados e configurações sensíveis.  
- **ACM / KMS / HSM:** gestão de certificados TLS e chaves de assinatura (JWKs).

## Pipeline CI/CD

1. **Build e testes:** `mvn verify` + cobertura de testes + validações de estilo.  
2. **SCA/SAST:** varredura de dependências e código (ex.: OWASP Dependency Check, SonarQube).  
3. **Scan de imagens:** análise de vulnerabilidades nos contêineres gerados.  
4. **Deploy automatizado:** aplicação de manifests/Helm charts em EKS/ECS e execução de Flyway.  
5. **Smoke tests:** validação de saúde, fluxo Authorization Code + PKCE e endpoints críticos.  
6. **Publicação OpenAPI:** upload automático dos artefatos JSON/YAML para armazenamento versionado (S3, artefatos de pipeline).

## Operações rotineiras

- **Rotação de segredos:** programar rotação de certificados TLS, JWKs e segredos de banco a cada 90 dias ou menos.  
- **Exportação de realms:** agendar exportação dos realms Keycloak e armazenar em S3 com versionamento.  
- **Monitoramento:** utilizar Prometheus/Grafana e OpenTelemetry Collector para métricas e traces; configurar alertas críticos.  
- **Auditoria:** revisar tabelas `auditoria_eventos` e `auditoria_acessos` das APIs periodicamente; arquivar registros em storage seguro.  
- **Gestão de incidentes:** seguir runbooks, acionar comunicação e registrar lições aprendidas no pós-incidente.

## Acesso AWS SSO para operação assistida

Quando for necessário pedir autorização interativa para operar a AWS pelo
profile local `Codex-cli_aws`, o comando padrão deve usar o fluxo de device
code, porque ele gera o link com o código já preenchido:

```bash
aws sso login \
  --profile Codex-cli_aws \
  --no-browser \
  --use-device-code
```

Não usar apenas `--no-browser` para esse caso operacional, porque o AWS CLI
pode cair no fluxo PKCE e gerar um link longo sem o código visível.

## Storage S3 de avatares do identidade-servidor

### Regra operacional

Avatares controlados pela Eickrono devem usar bucket S3 dedicado por ambiente.
O app não deve conhecer bucket nem `storage_key`; ele recebe somente a URL
pública/controlada entregue pelo `eickrono-identidade-servidor`.

Buckets definidos:

- HML: `eickrono-avatares-hml`
- Produção: `eickrono-avatares-prod`

Configuração obrigatória dos buckets:

- bloqueio de acesso público direto habilitado;
- criptografia padrão `AES256`;
- versionamento habilitado.

Em HML, a leitura pública/controlada continua pela rota:

```text
https://id-hml.eickrono.store/identidade/avatares/publicos/**
```

### Variáveis obrigatórias no `identidade-hml`

```text
IDENTIDADE_AVATAR_STORAGE_TIPO=s3
IDENTIDADE_AVATAR_STORAGE_BUCKET=eickrono-avatares-hml
IDENTIDADE_AVATAR_STORAGE_REGION=sa-east-1
IDENTIDADE_AVATAR_STORAGE_PUBLIC_URL_BASE=https://id-hml.eickrono.store/identidade/avatares/publicos
IDENTIDADE_AVATAR_STORAGE_MAX_BYTES=5242880
```

### Permissões IAM obrigatórias

A role ECS do `identidade-hml` precisa desta policy mínima:

- `s3:ListBucket` em `arn:aws:s3:::eickrono-avatares-hml`;
- `s3:GetObject` e `s3:PutObject` em
  `arn:aws:s3:::eickrono-avatares-hml/*`.

Motivo do `ListBucket`:

- sem ele, um `GetObject` de arquivo inexistente pode voltar `AccessDenied`;
- nesse caso o `eickrono-identidade-servidor` interpreta como falha de storage
  e responde HTTP 502;
- com `ListBucket`, objeto inexistente volta como ausência real e a rota pública
  responde HTTP 404.

Comando de validação da policy:

```bash
aws iam get-role-policy \
  --role-name eickrono-hml-ecs-task-role \
  --policy-name eickrono-hml-identidade-avatares-s3 \
  --profile Codex-cli_aws
```

### Validação pós-deploy

```bash
curl https://id-hml.eickrono.store/actuator/health
```

Resposta esperada:

```json
{"status":"UP","groups":["liveness","readiness"]}
```

Validação de rota pública para arquivo inexistente:

```bash
curl -i https://id-hml.eickrono.store/identidade/avatares/publicos/avatares/thimisu/arquivo-inexistente.png
```

Resposta esperada:

- HTTP 404;
- não deve retornar HTTP 502.

Validação de leitura S3 pela rota pública:

1. Enviar um PNG temporário para
   `s3://eickrono-avatares-hml/avatares/thimisu/validacao-s3-hml.png`.
2. Ler pela URL
   `https://id-hml.eickrono.store/identidade/avatares/publicos/avatares/thimisu/validacao-s3-hml.png`.
3. Confirmar HTTP 200, `content-type: image/png` e
   `cache-control: public, max-age=86400`.
4. Remover o objeto de teste do bucket.

## Automação obrigatória para rotação do segredo RDS em ECS

### Regra operacional

Sempre que o segredo gerenciado do RDS for rotacionado com sucesso, os serviços
ECS que consomem essa senha por variável de ambiente precisam receber
`forceNewDeployment`.

No ecossistema `hml`, isso é obrigatório para:

- `autenticacao-api-hml`
- `auth-hml`
- `identidade-hml`
- `thimisu-backend-hml`

Motivo técnico:

- o `Secrets Manager` atualiza `AWSCURRENT`;
- a task ECS não reinjeta segredos em tempo de execução;
- a senha antiga continua em memória até o restart da task;
- novas conexões do pool podem falhar com
  `password authentication failed for user "eickrono_admin"`.

### Automação canônica

Arquivo operacional:

- [configure_hml_rds_rotation_redeploy.sh](/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/infraestrutura/prod/ecs/configure_hml_rds_rotation_redeploy.sh)

Artefato de runtime:

- [handler.py](/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/infraestrutura/prod/ecs/lambda/rds_rotation_ecs_redeploy/handler.py)

O mecanismo padrão é:

1. `EventBridge` observa `RotationSucceeded` do `Secrets Manager`.
2. A regra aciona a Lambda `eickrono-hml-rds-rotation-ecs-redeploy`.
3. A Lambda confirma que o segredo do evento é o segredo RDS monitorado.
4. A Lambda executa `ecs update-service --force-new-deployment` para os quatro serviços, um por vez.
5. Depois de cada serviço, a Lambda aguarda `services_stable` antes de iniciar o próximo.

Essa espera sequencial é obrigatória. Em HML, redeploy paralelo dos quatro
serviços gerou pico de tasks e erro de limite de conexões no RDS.

### Fallback por erro de senha antiga

Arquivo operacional:

- [configure_hml_rds_password_auth_failure_fallback.sh](/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/infraestrutura/prod/ecs/configure_hml_rds_password_auth_failure_fallback.sh)

Artefato de runtime:

- [handler.py](/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/infraestrutura/prod/ecs/lambda/rds_password_auth_failure_fallback/handler.py)

O fallback observa os log groups dos serviços. Quando encontra
`password authentication failed for user "eickrono_admin"`, ele:

1. verifica cooldown no Parameter Store;
2. executa uma task Fargate de validação com `psql`;
3. confirma que o segredo atual conecta no RDS;
4. se conectar, executa `forceNewDeployment` nos serviços configurados;
5. aguarda `services_stable` de cada serviço antes de redeployar o próximo;
6. se não conectar, não faz redeploy e registra erro operacional.

Instalação/atualização em `hml`:

```bash
bash ./infraestrutura/prod/ecs/configure_hml_rds_password_auth_failure_fallback.sh \
  --profile Codex-cli_aws
```

### Padrão de evento adotado

Baseado na documentação oficial da AWS para eventos de rotação do
`Secrets Manager`, o padrão operacional usado é:

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

Além desse filtro, a Lambda valida internamente:

- `resources`
- `detail.responseElements.arn`
- `detail.responseElements.aRN`
- `detail.secretId`
- `detail.additionalEventData.SecretId`

Isso elimina dependência do formato exato do payload do CloudTrail.

### Procedimento reprodutível

#### 1. Instalar ou atualizar a automação

```bash
bash ./infraestrutura/prod/ecs/configure_hml_rds_rotation_redeploy.sh \
  --profile Codex-cli_aws
```

#### 2. Validar a regra EventBridge

```bash
aws events describe-rule \
  --name eickrono-hml-rds-rotation-succeeded \
  --profile Codex-cli_aws \
  --region sa-east-1
```

#### 3. Validar o target da regra

```bash
aws events list-targets-by-rule \
  --rule eickrono-hml-rds-rotation-succeeded \
  --profile Codex-cli_aws \
  --region sa-east-1
```

#### 4. Validar a configuração da Lambda

```bash
aws lambda get-function-configuration \
  --function-name eickrono-hml-rds-rotation-ecs-redeploy \
  --profile Codex-cli_aws \
  --region sa-east-1
```

Campos obrigatórios:

- `TARGET_SECRET_ARN`
- `ECS_CLUSTER=eickrono-hml`
- `ECS_SERVICES=autenticacao-api-hml,auth-hml,identidade-hml,thimisu-backend-hml`
- `SERVICE_STABLE_WAITER_DELAY_SECONDS=15`
- `SERVICE_STABLE_WAITER_MAX_ATTEMPTS=40`

#### 5. Validar permissão IAM da Lambda

```bash
aws iam get-role-policy \
  --role-name eickrono-hml-rds-rotation-ecs-redeploy-role \
  --policy-name eickrono-hml-rds-rotation-ecs-redeploy-role-ecs-redeploy \
  --profile Codex-cli_aws
```

#### 6. Validar após a próxima rotação real

Após uma rotação real do segredo RDS, confirmar:

- evento `RotationSucceeded` no CloudTrail/EventBridge;
- invocação da Lambda;
- novos deployments em:
  - `autenticacao-api-hml`
  - `auth-hml`
  - `identidade-hml`
  - `thimisu-backend-hml`

Comandos úteis:

```bash
aws ecs describe-services \
  --cluster eickrono-hml \
  --services autenticacao-api-hml auth-hml identidade-hml thimisu-backend-hml \
  --profile Codex-cli_aws \
  --region sa-east-1
```

```bash
aws logs tail /aws/lambda/eickrono-hml-rds-rotation-ecs-redeploy \
  --follow \
  --profile Codex-cli_aws \
  --region sa-east-1
```

## Procedimentos de emergência

- **Disaster Recovery:** gatilho para restauração em região secundária (backup de RDS + reimplantação de Keycloak).  
- **Comprometimento de chave:** revogar certificado no ACM/KMS, gerar novo par e atualizar a configuração nos serviços e na Cloudflare.  
- **Falha de autenticação:** verificar integridade do JWK endpoint (`/.well-known/jwks.json`), sincronização de relógios (NTP) e logs de auditoria.  
- **Rejeição FAPI:** validar configuração de PAR/JAR/JARM e certificados mTLS dos clientes confidenciais.

## Segurança operacional

- **Princípio do menor privilégio:** usuários e papéis IAM restritos à necessidade.  
- **Políticas de acesso Cloudflare:** whitelist de IPs e autenticação de operadores.  
- **Logging mascarado:** garantir que dados sensíveis permaneçam protegidos; mascaramento implementado nas APIs e no Keycloak.  
- **Reviews periódicos:** executar o `checklist-seguranca-fapi.md` durante janelas de manutenção e antes de releases.

## Handoff atual do Apple broker

O fluxo atual de `Sign in with Apple` do app `Thimisu` em produção depende da materialização destes segredos no runtime do Keycloak:

- `KEYCLOAK_IDP_THIMISU_APPLE_CLIENT_ID=com.eickrono.thimisu.oidc.prd`
- `KEYCLOAK_IDP_THIMISU_APPLE_CLIENT_SECRET_JWT=<jwt_gerado_localmente_com_a_key_principal>`

Fonte operacional local:

- [prod keycloak-apple.env](/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/.local-secrets/apple/eickrono-oidc/prod/keycloak-apple.env:1)

Pontos de controle depois do deploy:

- o Keycloak de produção precisa receber esses valores antes do `render-realms.sh` materializar o realm;
- o realm esperado é `eickrono`;
- o broker `apple` deve responder com `config.clientId = com.eickrono.thimisu.oidc.prd`;
- qualquer rotação futura do JWT deve reaproveitar a mesma key `Principal`, mudando apenas o token materializado na infraestrutura.

### Validação administrativa pós-deploy

Depois de o segredo ser materializado e o Keycloak ser reiniciado, execute dentro do runtime do Keycloak:

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

Validar manualmente:

- `eickrono` aparece na lista de realms;
- o broker retornado tem `alias = apple`;
- `enabled = true`;
- `config.clientId = com.eickrono.thimisu.oidc.prd`;
- `config.clientSecret` está mascarado.
