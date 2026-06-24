# Especificacao - Rotacao RDS com redeploy automatico e fallback por erro

## Objetivo

Definir o comportamento padrao para lidar com rotacao de senha do RDS em
servicos ECS da Eickrono.

Siglas usadas neste documento:

- ECS: Elastic Container Service, servico da AWS que executa as tasks.
- RDS: Relational Database Service, banco PostgreSQL gerenciado pela AWS.
- ALB: Application Load Balancer, balanceador HTTP que distribui trafego para
  as tasks.
- HA: alta disponibilidade, capacidade de continuar atendendo mesmo quando uma
  instancia falha ou reinicia.

O alvo e evitar que uma task ECS continue usando uma senha antiga depois que o
Secrets Manager marcar uma nova senha como `AWSCURRENT`.

O desenho aprovado e a opcao hibrida:

1. `Secrets Manager` conclui a rotacao do segredo RDS.
2. `EventBridge` recebe o evento `RotationSucceeded`.
3. Uma Lambda valida se o segredo rotacionado e o segredo RDS monitorado.
4. A Lambda executa `ecs update-service --force-new-deployment` nos servicos
   afetados.
5. Como protecao adicional, um fallback observa logs com
   `password authentication failed for user "eickrono_admin"`.
6. Se o fallback confirmar que o segredo atual conecta no RDS, ele executa o
   mesmo redeploy controlado.
7. Se o segredo atual tambem nao conectar, o fallback nao redeploya e gera
   alerta operacional.

## Regra de ambiente

STG e PRD devem usar a mesma arquitetura.

A diferenca entre ambientes deve ser apenas configuracao:

- cluster ECS;
- nomes dos servicos;
- ARN do segredo RDS;
- quantidade de tasks;
- janela/frequencia de rotacao;
- destinos de alerta;
- limites de cooldown.

Nao deve existir uma estrategia para STG e outra estrategia diferente para PRD,
porque STG precisa validar o comportamento que sera usado em producao.

## Problema tecnico

Os servicos ECS consomem a senha do PostgreSQL por secret/variavel injetada no
start da task.

Quando o segredo do RDS rotaciona:

- o Secrets Manager atualiza o valor `AWSCURRENT`;
- a task ECS ja em execucao nao reread o secret automaticamente;
- o pool de conexoes pode continuar usando conexoes antigas ja abertas;
- novas conexoes abertas pelo pool podem falhar com:

```text
password authentication failed for user "eickrono_admin"
```

Esse erro nao significa necessariamente que o segredo atual esta errado. Pode
significar que a task antiga ainda esta com a senha anterior em memoria.

## Por que nao trocar a senha dentro da aplicacao

Nao e o padrao recomendado para o desenho atual.

Motivos:

- a aplicacao recebe a senha no start da task;
- Spring/Hikari nao troca automaticamente a senha do `DataSource` em runtime;
- recriar pool de conexoes em runtime exige controle de concorrencia;
- cada servico Java teria que implementar o mesmo mecanismo;
- bugs nesse ponto afetam todo acesso ao banco;
- a aplicacao nao deve ser responsavel por corrigir rotacao de infraestrutura;
- se o banco estiver indisponivel, a aplicacao pode estar justamente sem
  condicao confiavel de executar logica de recuperacao.

O mecanismo correto para a arquitetura atual e externo:

- Secrets Manager;
- EventBridge;
- Lambda;
- ECS rolling deployment;
- CloudWatch Alarm como fallback.

## Arquitetura principal

### Componentes

| Componente | Responsabilidade |
| --- | --- |
| Secrets Manager | Mantem o segredo RDS e executa a rotacao. |
| EventBridge | Observa eventos `RotationSucceeded`. |
| Lambda de rotacao | Valida o segredo do evento e forca novo deployment ECS. |
| ECS | Substitui tasks antigas por tasks novas com o secret atual. |
| CloudWatch Logs | Registra falhas de senha antiga em servicos. |
| CloudWatch Alarm/Filtro | Detecta erro de autenticacao do banco como fallback. |
| Lambda de fallback | Valida o secret atual, aplica cooldown e redeploya se fizer sentido. |

### Servicos afetados

Todo servico ECS que consome o segredo RDS deve estar listado na automacao.

No STG atual, os nomes conhecidos sao:

- `autenticacao-api-stg`;
- `auth-stg`;
- `identidade-stg`;
- `thimisu-backend-stg`.

Em PRD, a lista deve usar os nomes equivalentes de producao.

Sempre que um novo servico passar a usar o mesmo segredo RDS, ele deve ser
incluido na configuracao da Lambda.

## Fluxo principal - rotacao concluida

1. Secrets Manager conclui a rotacao.
2. AWS emite evento `RotationSucceeded`.
3. EventBridge aciona a Lambda de rotacao.
4. Lambda extrai os possiveis ARNs do evento.
5. Lambda compara os ARNs com `TARGET_SECRET_ARN`.
6. Se nao for o segredo monitorado, ignora o evento.
7. Se for o segredo monitorado, executa `ecs update-service` com
   `forceNewDeployment=true` no primeiro servico configurado.
8. Lambda aguarda `services_stable` desse servico.
9. So depois repete o processo para o proximo servico.
10. ECS inicia novas tasks.
11. Novas tasks leem o secret atual no start.
12. Health check confirma que as novas tasks estao saudaveis.
13. ECS encerra as tasks antigas.

## Fluxo fallback - erro de senha antiga

Esse fluxo nao substitui o fluxo principal. Ele existe para cobrir falha da
automacao principal ou evento nao processado.

1. Um servico registra:

```text
password authentication failed for user "eickrono_admin"
```

2. CloudWatch Logs gera metrica ou alarme.
3. O alarme aciona a Lambda de fallback.
4. Lambda verifica cooldown para evitar loop.
5. Lambda busca o valor atual do segredo RDS no Secrets Manager.
6. Lambda tenta abrir conexao simples com o RDS usando o secret atual.
7. Se a conexao com o secret atual falhar:
   - nao faz redeploy;
   - registra erro;
   - envia alerta operacional.
8. Se a conexao com o secret atual funcionar:
   - conclui que o secret atual esta valido;
   - conclui que as tasks antigas podem estar com senha antiga;
   - executa redeploy controlado e sequencial dos servicos afetados.
9. Lambda registra quais servicos foram redeployados.
10. CloudWatch deve permitir confirmar se o erro parou.

## Regra de redeploy sequencial

A Lambda de rotacao e a Lambda de fallback devem redeployar os servicos
sequencialmente.

Regra obrigatoria:

1. iniciar redeploy de um unico servico;
2. aguardar `services_stable`;
3. registrar sucesso desse servico;
4. iniciar o proximo servico.

Motivo:

- em `stg`, um teste sintetico do fallback em `2026-06-02` confirmou que
  redeploy simultaneo de todos os servicos pode duplicar temporariamente o
  numero de tasks Java;
- esse pico abriu conexoes demais no RDS pequeno de STG;
- os servicos falharam com
  `remaining connection slots are reserved for non-replication superuser and rds_reserved connections`;
- o problema nao era senha invalida, era excesso de conexoes causado pelo
  paralelismo do proprio mecanismo de recuperacao.

Parametros de runtime:

- `SERVICE_STABLE_WAITER_DELAY_SECONDS`: intervalo entre checagens de
  estabilidade;
- `SERVICE_STABLE_WAITER_MAX_ATTEMPTS`: quantidade maxima de checagens por
  servico;
- `TIMEOUT` da Lambda deve cobrir a soma dos waits sequenciais.

Default operacional atual:

- delay: `15` segundos;
- tentativas: `40` por servico;
- timeout da Lambda: `900` segundos.

## Regra de decisao do fallback

| Condicao | Acao |
| --- | --- |
| Erro apareceu nos logs e secret atual conecta no RDS | Forcar rolling redeploy. |
| Erro apareceu nos logs e secret atual nao conecta no RDS | Nao redeployar; alertar. |
| Erro apareceu durante cooldown | Nao redeployar; registrar ignorado por cooldown. |
| Evento nao e erro de senha do usuario RDS monitorado | Ignorar. |

## Cooldown e idempotencia

O fallback precisa impedir loops.

Regras minimas:

- ter uma janela de cooldown por cluster/secret, por exemplo 10 a 15 minutos;
- nao disparar mais de um redeploy simultaneo para o mesmo cluster/secret;
- registrar `correlacaoId` por execucao;
- registrar motivo da execucao: `ROTATION_SUCCEEDED` ou
  `PASSWORD_AUTH_FAILURE_FALLBACK`;
- registrar se a execucao foi aplicada, ignorada ou bloqueada.

Opcao tecnica para controlar cooldown:

- DynamoDB com chave `ambiente + secretArn`;
- Parameter Store com timestamp da ultima execucao;
- tag/metadata operacional se a frequencia for baixa.

Recomendacao: DynamoDB ou Parameter Store. Nao usar somente memoria da Lambda,
porque a Lambda pode iniciar em outro container.

## Requisitos de rolling deployment

Para que o redeploy nao vire indisponibilidade, cada servico critico precisa
estar configurado para rolling deployment saudavel.

O comportamento esperado e:

```text
Antes da rotacao:
Task A antiga atende usuarios com a senha antiga.
Task B antiga atende usuarios com a senha antiga.

Depois da rotacao:
Secrets Manager passa a ter a senha nova.
Task A e Task B continuam com a senha antiga em memoria.

Durante o redeploy:
ECS cria Task C nova com a senha nova.
ECS cria Task D nova com a senha nova.
Task A e Task B continuam atendendo enquanto C e D sobem.

Quando C e D passam no health check:
o balanceador/ECS passa a enviar trafego para C e D.
ECS remove Task A e Task B.
```

Esse comportamento nao e um delay. A task nova e uma nova instancia real do
servico, criada para nascer com o secret atual. A task antiga nao recebe a senha
nova em runtime.

Requisitos:

- health check funcional;
- pelo menos 2 tasks para servicos que nao podem parar;
- `minimumHealthyPercent` adequado para manter task antiga enquanto a nova sobe;
- `maximumPercent` adequado para permitir task extra temporaria;
- timeout de health check coerente com tempo real de startup;
- logs de startup mostrando conexao com banco e readiness.

Se um servico tiver apenas 1 task, o redeploy ainda pode ser correto, mas pode
gerar indisponibilidade curta. Nesse caso, o risco deve estar documentado.

## Logs obrigatorios

### Lambda de rotacao

Deve registrar:

- `evento_recebido`;
- `secret_arn_extraido`;
- `secret_monitorado`;
- `evento_ignorado` quando nao for o segredo alvo;
- `redeploy_iniciado`;
- `servico`;
- `cluster`;
- `deployment_id` quando disponivel;
- `redeploy_concluido` ou `erro_redeploy`.

### Lambda de fallback

Deve registrar:

- `fallback_password_auth_failure_recebido`;
- `log_group`;
- `log_stream`;
- `servico_detectado`, se possivel;
- `secret_validacao_iniciada`;
- `secret_validacao_sucesso`;
- `secret_validacao_falha`;
- `cooldown_ativo`;
- `redeploy_iniciado`;
- `redeploy_ignorado`;
- `alerta_emitido`.

### Servicos Java

Os servicos devem manter logs suficientes para identificar:

- startup do pool;
- falha de autenticacao no banco;
- nome do servico;
- ambiente;
- correlacao operacional quando existir.

## Testes obrigatorios

## Etapas de implementacao

### Etapa 1 - Levantar configuracao atual

Objetivo:

- confirmar quais servicos ECS usam o segredo RDS;
- confirmar se cada servico critico tem redundancia suficiente para rolling
  deployment sem parada.

Validacoes:

- `desiredCount`;
- `minimumHealthyPercent`;
- `maximumPercent`;
- health check;
- target group;
- log group;
- task definition atual;
- secrets injetados na task;
- ARN do segredo RDS usado por cada servico.

Criterio de aceite:

- existe uma lista fechada de servicos afetados;
- cada servico critico tem decisao documentada sobre `desiredCount >= 2`;
- nenhum servico consumidor do segredo RDS ficou fora da lista.

### Etapa 2 - Ajustar rolling deployment

Objetivo:

- garantir que o ECS consiga criar tasks novas antes de desligar as antigas.

Regras:

- servicos criticos devem usar `desiredCount >= 2`;
- `minimumHealthyPercent` deve manter capacidade durante a troca;
- `maximumPercent` deve permitir tasks extras temporarias;
- health check precisa validar que a task nova realmente esta pronta.

Criterio de aceite:

- durante um redeploy manual, o servico continua respondendo;
- as tasks antigas so saem depois que as novas ficam saudaveis.

### Etapa 3 - Revisar Lambda principal de rotacao

Objetivo:

- garantir que a automacao existente cobre a rotacao bem-sucedida do
  Secrets Manager.

Validacoes:

- regra EventBridge esta ativa;
- target aponta para a Lambda correta;
- `TARGET_SECRET_ARN` e o segredo RDS correto;
- `ECS_CLUSTER` aponta para o cluster correto;
- `ECS_SERVICES` contem todos os servicos afetados;
- Lambda ignora rotacao de outro segredo.

Criterio de aceite:

- evento `RotationSucceeded` do segredo monitorado dispara redeploy;
- evento de outro segredo nao dispara redeploy.

### Etapa 4 - Criar fallback por erro de senha

Objetivo:

- cobrir o caso em que a automacao principal nao executou, falhou ou nao
  recebeu o evento.

Regra:

- CloudWatch deve detectar:

```text
password authentication failed for user "eickrono_admin"
```

- o fallback deve ser externo ao app;
- o fallback nao deve trocar senha dentro da aplicacao;
- o fallback deve validar o segredo atual antes de redeployar.

Criterio de aceite:

- erro de senha antiga aciona a Lambda de fallback;
- erro irrelevante nao aciona redeploy.

### Etapa 5 - Validar o secret atual no fallback

Objetivo:

- diferenciar task antiga com senha velha de segredo RDS quebrado.

Regra:

- se o secret atual conecta no RDS, redeployar;
- se o secret atual nao conecta no RDS, nao redeployar e alertar.

Criterio de aceite:

- fallback com secret valido executa redeploy;
- fallback com secret invalido gera alerta sem redeploy.

### Etapa 6 - Implementar cooldown e idempotencia

Objetivo:

- impedir loop de redeploy.

Regras:

- uma execucao por cluster/secret dentro da janela de cooldown;
- registrar ultima execucao;
- registrar execucoes ignoradas por cooldown;
- nao depender apenas da memoria da Lambda.

Criterio de aceite:

- duas falhas seguidas nao geram dois redeploys dentro da janela configurada.

### Etapa 7 - Adicionar logs operacionais

Objetivo:

- conseguir explicar toda decisao tomada pela automacao.

Logs obrigatorios:

- evento recebido;
- secret identificado;
- validacao do secret iniciada;
- validacao do secret concluida;
- cooldown ativo;
- redeploy iniciado;
- redeploy concluido;
- redeploy ignorado;
- alerta emitido.

Criterio de aceite:

- pelos logs e possivel saber por que houve ou nao houve redeploy.

### Etapa 8 - Aplicar em todos os ambientes

Objetivo:

- manter STG e PRD com a mesma arquitetura.

Regras:

- a arquitetura deve ser a mesma;
- nomes, ARNs, escala e destinos de alerta podem mudar por ambiente;
- STG deve validar o comportamento que sera usado em PRD.

Criterio de aceite:

- STG e PRD usam o mesmo desenho operacional;
- diferem apenas em configuracao.

## Testes obrigatorios

### Teste unitario da Lambda de rotacao

Cenarios:

- evento `RotationSucceeded` com `resources` contendo o secret alvo;
- evento `RotationSucceeded` com `detail.responseElements.arn`;
- evento `RotationSucceeded` com `detail.additionalEventData.SecretId`;
- evento de outro secret deve ser ignorado;
- evento que nao e `RotationSucceeded` deve ser ignorado;
- lista de servicos vazia deve falhar de forma explicita.

### Teste unitario da Lambda de fallback

Cenarios:

- erro de senha detectado e secret atual conecta;
- erro de senha detectado e secret atual nao conecta;
- erro detectado durante cooldown;
- erro de outro usuario de banco deve ser ignorado;
- erro sem secret configurado deve falhar de forma explicita;
- falha no `ecs update-service` deve gerar erro/alerta.

### Teste integrado em STG

O mesmo procedimento deve ser repetivel em PRD em janela controlada.

Cenarios:

1. Confirmar que a regra EventBridge esta `ENABLED`.
2. Confirmar que a Lambda tem `TARGET_SECRET_ARN` correto.
3. Confirmar que todos os servicos consumidores estao em `ECS_SERVICES`.
4. Simular evento `RotationSucceeded` contra a Lambda.
5. Confirmar que ECS criou novo deployment nos servicos esperados.
6. Confirmar que as novas tasks ficaram saudaveis.
7. Simular evento de outro secret e confirmar que nada foi redeployado.
8. Simular fallback com secret valido e confirmar redeploy.
9. Simular fallback dentro do cooldown e confirmar que nao redeploya.
10. Simular fallback com secret invalido e confirmar alerta sem redeploy.

## Validacao operacional

Comandos de verificacao esperados:

```bash
aws events describe-rule \
  --region <regiao> \
  --name <regra>
```

```bash
aws events list-targets-by-rule \
  --region <regiao> \
  --rule <regra>
```

```bash
aws lambda get-function-configuration \
  --region <regiao> \
  --function-name <lambda>
```

```bash
aws ecs describe-services \
  --region <regiao> \
  --cluster <cluster> \
  --services <servico>
```

## Criterios de aceite

A implementacao esta correta quando:

- STG e PRD usam o mesmo desenho;
- rotacao bem-sucedida dispara redeploy automatico;
- fallback por erro de senha existe;
- fallback valida o secret atual antes de redeployar;
- fallback nao redeploya quando o secret atual nao conecta;
- fallback tem cooldown;
- todos os servicos que usam o segredo estao na lista de redeploy;
- rolling deployment nao derruba servicos criticos;
- logs permitem explicar cada decisao tomada;
- testes unitarios e integrados cobrem os cenarios acima.

## Estado atual conhecido

Ja existe automacao principal documentada e implementada para STG:

- script operacional:
  `infraestrutura/prod/ecs/configure_stg_rds_rotation_redeploy.sh`;
- Lambda:
  `infraestrutura/prod/ecs/lambda/rds_rotation_ecs_redeploy/handler.py`;
- regra EventBridge:
  `eickrono-stg-rds-rotation-succeeded`;
- Lambda:
  `eickrono-stg-rds-rotation-ecs-redeploy`.

O fallback por log ja possui implementacao local, testes locais e instalacao em
STG. A validacao segura da task Fargate de `psql` tambem foi executada com
sucesso.

Em `2026-06-02`, `auth-stg` tambem foi validado e corrigido para operar com
duas tasks permanentes. O teste sintetico completo do fallback ainda deve ser
executado em janela segura, porque ele pode forcar redeploy sequencial dos
quatro servicos, mas ele ja nao depende mais de um Keycloak com task unica.

Estado operacional aplicado em STG em `2026-06-02`:

- `autenticacao-api-stg` foi incluido em `ECS_SERVICES`;
- policy IAM da Lambda passou a permitir redeploy de `autenticacao-api-stg`;
- `autenticacao-api-stg`, `identidade-stg` e `thimisu-backend-stg` foram
  ajustados para `desiredCount=2`;
- `thimisu-backend-stg` passou a usar `healthCheckGracePeriodSeconds=180`;
- `auth-stg` foi ajustado para `desiredCount=2` permanente;
- `auth-stg` passou a usar `deploymentConfiguration` com
  `minimumHealthyPercent=100` e `maximumPercent=150`;
- target group `eickrono-stg-auth` passou a usar stickiness por cookie do ALB
  com duracao de 86400 segundos;
- o uso de stickiness foi escolhido porque a task definition atual do Keycloak
  nao mostrou configuracao explicita de cache/cluster;
- `auth-stg` foi validado com 2 targets `healthy`;
- endpoint publico do Keycloak foi validado em
  `https://oidc-stg.eickrono.store/realms/eickrono/eickrono-runtime/estado`;
- issuer publico foi validado em
  `https://oidc-stg.eickrono.store/realms/eickrono/.well-known/openid-configuration`;
- conexoes visiveis em `pg_stat_activity` apos a mudanca:
  `keycloak_stg=4`, total observado `73/81`;
- task orfa `identidade-stg:50` fora do service `identidade-stg` foi parada por
  manter senha RDS antiga em memoria.
- Lambda de fallback `eickrono-stg-rds-password-auth-failure-fallback` foi
  instalada;
- subscription filter
  `eickrono-stg-rds-password-auth-failure-fallback` foi instalado em:
  `/ecs/stg/autenticacao`, `/ecs/stg/auth`, `/ecs/stg/identidade` e
  `/ecs/stg/thimisu-backend`;
- task Fargate manual de validacao
  `86180380ca7840e6809493716429210d` executou `SELECT 1` em
  `eickrono_identidade_stg` e terminou com `exitCode=0`.

## Arquivos e artefatos envolvidos

### Documentacao

| Arquivo | Papel |
| --- | --- |
| `documentacao/especificacao_rotacao_rds_redeploy_fallback.md` | Especificacao central deste processo: arquitetura, etapas, testes, levantamento atual e pendencias. |
| `documentacao/guia-operacao-producao.md` | Guia operacional geral. Contem a regra de redeploy obrigatorio apos rotacao do segredo RDS. |
| `infraestrutura/prod/ecs/README.md` | Guia operacional especifico da automacao ECS/Lambda/EventBridge para rotacao RDS. |

### Automacao principal de rotacao

| Arquivo | Papel |
| --- | --- |
| `infraestrutura/prod/ecs/configure_stg_rds_rotation_redeploy.sh` | Script oficial para criar/atualizar Lambda, role IAM, policy, regra EventBridge e target da automacao de rotacao em STG. |
| `infraestrutura/prod/ecs/lambda/rds_rotation_ecs_redeploy/handler.py` | Codigo da Lambda que recebe `RotationSucceeded`, valida o segredo monitorado e executa `ecs update-service --force-new-deployment`. |

### Fallback por erro de senha antiga

| Arquivo | Papel |
| --- | --- |
| `infraestrutura/prod/ecs/configure_stg_rds_password_auth_failure_fallback.sh` | Script para criar/atualizar Lambda, role IAM, policy, permissao de CloudWatch Logs e subscription filter nos log groups monitorados. |
| `infraestrutura/prod/ecs/lambda/rds_password_auth_failure_fallback/handler.py` | Codigo da Lambda que recebe evento de log, detecta `password authentication failed`, respeita cooldown, valida o segredo atual por task ECS/Fargate de `psql` e redeploya os servicos se a validacao passar. |

### Testes locais

| Arquivo | Papel |
| --- | --- |
| `infraestrutura/prod/tests/configure_stg_rds_rotation_redeploy_test.sh` | Testa o plano gerado pelo script de configuracao em `--dry-run`. |
| `infraestrutura/prod/tests/rds_secret_rotation_ecs_redeploy_lambda_test.py` | Testa a Lambda de rotacao: eventos aceitos, eventos ignorados e redeploy dos servicos configurados. |
| `infraestrutura/prod/tests/configure_stg_rds_password_auth_failure_fallback_test.sh` | Testa o plano gerado pelo script de configuracao do fallback em `--dry-run`. |
| `infraestrutura/prod/tests/rds_password_auth_failure_fallback_lambda_test.py` | Testa a Lambda de fallback: evento irrelevante, secret validado, secret nao validado e cooldown. |

### Artefatos AWS em STG

| Artefato | Papel |
| --- | --- |
| EventBridge rule `eickrono-stg-rds-rotation-succeeded` | Observa evento `RotationSucceeded` do Secrets Manager. |
| Lambda `eickrono-stg-rds-rotation-ecs-redeploy` | Executa redeploy dos servicos ECS configurados. |
| IAM role `eickrono-stg-rds-rotation-ecs-redeploy-role` | Permite que a Lambda escreva logs e chame `ecs:UpdateService` nos servicos permitidos. |
| Secrets Manager secret `rds!db-7df15f56-c831-40b7-be42-ebd935108b06` | Segredo RDS monitorado. |
| ECS service `autenticacao-api-stg` | Consome `SPRING_DATASOURCE_PASSWORD` do segredo RDS. |
| ECS service `auth-stg` | Consome `KC_DB_PASSWORD` do segredo RDS. |
| ECS service `identidade-stg` | Consome `SPRING_DATASOURCE_PASSWORD` do segredo RDS. |
| ECS service `thimisu-backend-stg` | Consome `SPRING_DATASOURCE_PASSWORD` do segredo RDS. |

### Estado atual do fallback em STG

| Artefato | Estado |
| --- | --- |
| Lambda `eickrono-stg-rds-password-auth-failure-fallback` | Instalada e `Active`. |
| IAM role `eickrono-stg-rds-password-auth-failure-fallback-role` | Instalada com permissao restrita para redeploy dos servicos monitorados, execucao da task de validacao e parametro de cooldown. |
| Subscription filters CloudWatch Logs | Instalados nos quatro log groups monitorados. |
| Estado de cooldown | Parameter Store em `/eickrono/stg/rds-password-auth-failure-fallback/last-run`. |
| Task Fargate de validacao `eickrono-stg-db-query-codex:1` | Validada manualmente com `exitCode=0`. |

## Levantamento atual em STG - 2026-06-02

Comandos executados:

- `aws ecs list-clusters`;
- `aws ecs list-services`;
- `aws ecs describe-services`;
- `aws ecs describe-task-definition`;
- `aws elbv2 describe-target-groups`;
- `aws elbv2 describe-target-health`;
- `aws events describe-rule`;
- `aws lambda get-function-configuration`.

### Cluster encontrado

| Item | Valor |
| --- | --- |
| Cluster ECS | `eickrono-stg` |
| Servicos ativos | 4 |
| Tasks Fargate rodando | 7 |
| Container Insights | `enabled` |

### Segredo RDS monitorado

| Item | Valor |
| --- | --- |
| Secret | `rds!db-7df15f56-c831-40b7-be42-ebd935108b06` |
| ARN | `arn:aws:secretsmanager:sa-east-1:531708494702:secret:rds!db-7df15f56-c831-40b7-be42-ebd935108b06-22Dwvf` |
| Rotacao habilitada | `true` |
| Ultima rotacao observada | `2026-05-30T21:09:55-03:00` |

### Regra EventBridge atual

| Item | Valor |
| --- | --- |
| Regra | `eickrono-stg-rds-rotation-succeeded` |
| Estado | `ENABLED` |
| Target | `arn:aws:lambda:sa-east-1:531708494702:function:eickrono-stg-rds-rotation-ecs-redeploy` |

### Lambda atual

| Item | Valor |
| --- | --- |
| Lambda | `eickrono-stg-rds-rotation-ecs-redeploy` |
| Runtime | `python3.12` |
| Estado | `Active` |
| `ECS_CLUSTER` | `eickrono-stg` |
| `TARGET_SECRET_ARN` | `arn:aws:secretsmanager:sa-east-1:531708494702:secret:rds!db-7df15f56-c831-40b7-be42-ebd935108b06-22Dwvf` |
| `ECS_SERVICES` | `autenticacao-api-stg,auth-stg,identidade-stg,thimisu-backend-stg` |

Ultima atualizacao operacional conhecida:

- `2026-06-02T07:40:58Z`: Lambda atualizada com os 4 servicos.

### Servicos ECS encontrados

| Servico | Usa segredo RDS | Esta na Lambda atual | `desiredCount` | `runningCount` | Readiness | Deployment |
| --- | --- | --- | --- | --- | --- | --- |
| `autenticacao-api-stg` | Sim, `SPRING_DATASOURCE_PASSWORD` | Sim | 2 | 2 | Health check de container | `ROLLING`, `minimumHealthyPercent=100`, `maximumPercent=150` |
| `auth-stg` | Sim, `KC_DB_PASSWORD` | Sim | 2 | 2 | Target group ALB | `ROLLING`, `minimumHealthyPercent=100`, `maximumPercent=150` |
| `identidade-stg` | Sim, `SPRING_DATASOURCE_PASSWORD` | Sim | 2 | 2 | Target group ALB | `ROLLING`, `minimumHealthyPercent=100`, `maximumPercent=150` |
| `thimisu-backend-stg` | Sim, `SPRING_DATASOURCE_PASSWORD` | Sim | 2 | 2 | Target group ALB | `ROLLING`, `minimumHealthyPercent=100`, `maximumPercent=150` |

Achados:

- `autenticacao-api-stg`, `identidade-stg` e `thimisu-backend-stg` ja estao com
  redundancia minima de 2 tasks;
- `auth-stg` agora tambem esta com redundancia minima de 2 tasks;
- `auth-stg` usa stickiness no ALB porque a task definition atual nao mostrou
  configuracao explicita de cache/cluster do Keycloak;
- antes de replicar em PRD, validar se a estrategia definitiva sera manter
  stickiness ou configurar cluster/cache explicito no Keycloak;
- `autenticacao-api-stg` nao possui load balancer; sua validacao de readiness
  foi resolvida por health check de container contra o actuator local;
- foi encontrada task standalone `f250f20fed8941dab9e93f4af0b62090`,
  `group=family:identidade-stg`, `taskDefinition=identidade-stg:50`, iniciada em
  `2026-05-26`, fora do service `identidade-stg`;
- essa task standalone gerava `password authentication failed for user
  "eickrono_admin"` e foi parada em `2026-06-02T08:02:18Z`;
- apos a parada da task orfa, nao houve novo erro de senha no log de identidade
  no intervalo verificado.
- fallback por erro de senha foi instalado em STG com subscription filters nos
  quatro log groups monitorados;
- a task Fargate usada pelo fallback para validar o segredo atual foi testada
  manualmente em `2026-06-02` e terminou com `exitCode=0`.
- apos a instalacao do fallback, uma busca nos quatro log groups monitorados
  pelos ultimos 30 minutos nao encontrou novo `password authentication failed`
  para `eickrono_admin`.

### Health checks conhecidos

| Servico | Tipo | Alvo | Intervalo | Healthy threshold / retries | Grace/start period |
| --- | --- | --- | --- | --- | --- |
| `autenticacao-api-stg` | Container health check | `wget -q -O - http://localhost:8081/actuator/health | grep '"status":"UP"'` | 30s | 3 retries | 120s |
| `auth-stg` | Target group ALB | `/realms/eickrono/eickrono-runtime/estado` | 30s | 5 | 180s |
| `identidade-stg` | Target group ALB | `/api/v1/estado` | 30s | 5 | 240s |
| `thimisu-backend-stg` | Target group ALB | `/api/v1/estado` | 30s | 5 | 180s |

Estado atual dos health checks:

- `autenticacao-api-stg`: 2 tasks `HEALTHY` na revisao
  `autenticacao-api-stg:12`;
- `auth-stg`: 2 targets `healthy`;
- `identidade-stg`: 2 targets `healthy`;
- `thimisu-backend-stg`: 2 targets `healthy`.

`autenticacao-api-stg` nao possui load balancer. Ele usa Cloud Map/service
discovery interno. Para esse servico, a validacao de readiness deve ser feita
pelo health check de container. Chamada interna por Cloud Map/log continua sendo
diagnostico complementar, nao a fonte primaria de prontidao do ECS.

### Divergencias contra o desenho alvo

| Divergencia | Risco | Acao recomendada |
| --- | --- | --- |
| Possibilidade de task standalone antiga | Task fora do service pode continuar rodando com configuracao antiga e gerar erro operacional. | Monitorar tasks com `group=family:*` para familias de servicos e parar tasks orfas. |

## Proxima etapa recomendada

1. Testar o fallback em STG usando evento sintetico somente em janela segura,
   porque ele pode forcar redeploy sequencial dos quatro servicos.
2. Criar monitoramento para tasks standalone/orfas das familias de servicos.
3. Replicar a mesma arquitetura em PRD com configuracoes proprias quando PRD
   existir.

### Correcao de readiness do `autenticacao-api-stg`

Executado em `2026-06-02`:

1. Copiada a task definition ativa `autenticacao-api-stg:11`.
2. Registrada a task definition `autenticacao-api-stg:12` mantendo imagem,
   secrets, roles, CPU/memoria e portas existentes.
3. Adicionado health check de container ao container `autenticacao-api`:
   - comando:
     `wget -q -O - http://localhost:8081/actuator/health | grep '"status":"UP"' || exit 1`;
   - `interval=30`;
   - `timeout=5`;
   - `retries=3`;
   - `startPeriod=120`.
4. Atualizado o service `autenticacao-api-stg` para a revisao 12 com rollout:
   - `desiredCount=2`;
   - `minimumHealthyPercent=100`;
   - `maximumPercent=150`.
5. Confirmado:
   - ECS `desired=2`, `running=2`, `pending=0`;
   - rollout `COMPLETED`;
   - duas tasks `RUNNING` e `HEALTHY`;
   - actuator interno retornando
     `{"status":"UP","groups":["liveness","readiness"]}`;
   - sem `ERROR` recente no log group `/ecs/stg/autenticacao` no intervalo
     verificado.

### Correcao de `auth-stg` para 2 tasks

Executado em `2026-06-02`:

1. Levantada a task definition `auth-stg:21`.
2. Confirmado que `auth-stg` usa o banco `keycloak_stg` no RDS compartilhado.
3. Confirmado que a task definition nao possui configuracao explicita de
   cache/cluster do Keycloak.
4. Habilitada stickiness no target group `eickrono-stg-auth`:
   - `stickiness.enabled=true`;
   - `stickiness.type=lb_cookie`;
   - `stickiness.lb_cookie.duration_seconds=86400`.
5. Atualizado o service `auth-stg`:
   - `desiredCount=2`;
   - `minimumHealthyPercent=100`;
   - `maximumPercent=150`.
6. Confirmado:
   - ECS `desired=2`, `running=2`, `pending=0`;
   - dois targets `healthy` no target group `eickrono-stg-auth`;
   - endpoint `/realms/eickrono/eickrono-runtime/estado` retornando `status=ok`;
   - issuer publico retornando
     `https://oidc-stg.eickrono.store/realms/eickrono`;
   - sem eventos recentes de `ERROR`, `password authentication failed` ou
     `remaining connection slots` no log group `/ecs/stg/auth`.

Consumo de conexoes apos a correcao:

| Banco | Usuario | Estado | Conexoes |
| --- | --- | --- | --- |
| `eickrono_identidade_stg` | `eickrono_admin` | `idle` | 40 |
| `eickrono_thimisu_stg` | `eickrono_admin` | `idle` | 20 |
| `keycloak_stg` | `eickrono_admin` | `idle` | 4 |
| `postgres` | `eickrono_admin` | `active` | 1 |
| `rdsadmin` | `rdsadmin` | `idle` | 2 |
| reservado/sem banco | `rdsadmin` | n/a | 1 |
| reservado/sem usuario | n/a | n/a | 5 |

`max_connections=81`; total observado em `pg_stat_activity=73`.
