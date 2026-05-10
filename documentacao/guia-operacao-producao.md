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

## Automação obrigatória para rotação do segredo RDS em ECS

### Regra operacional

Sempre que o segredo gerenciado do RDS for rotacionado com sucesso, os serviços
ECS que consomem essa senha por variável de ambiente precisam receber
`forceNewDeployment`.

No ecossistema `hml`, isso é obrigatório para:

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
4. A Lambda executa `ecs update-service --force-new-deployment` para os três serviços.

### Padrão de evento adotado

Baseado na documentação oficial da AWS para eventos de rotação do
`Secrets Manager`, o padrão operacional usado é:

```json
{
  "source": ["aws.secretsmanager"],
  "detail-type": ["AWS Service Event via CloudTrail"],
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
- `ECS_SERVICES=auth-hml,identidade-hml,thimisu-backend-hml`

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
  - `auth-hml`
  - `identidade-hml`
  - `thimisu-backend-hml`

Comandos úteis:

```bash
aws ecs describe-services \
  --cluster eickrono-hml \
  --services auth-hml identidade-hml thimisu-backend-hml \
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
