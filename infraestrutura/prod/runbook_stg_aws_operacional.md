# Runbook Operacional de STG na AWS

## Objetivo

Este arquivo e o ponto de entrada canônico para subir, atualizar e validar o
ambiente `stg` na AWS sem depender da leitura linear do historico completo.

Estado atual da base de dados em `stg`:

- `auth`, `identidade` e `thimisu-backend` ja usam bancos diferentes;
- esses bancos ainda estao no mesmo host RDS em `stg`;
- a separacao fisica completa por host/instancia ainda nao faz parte do estado
  atual do ambiente.

## Como usar

- use este arquivo para a ordem operacional atual;
- use `guia_subida_stg_aws.md` quando precisar da trilha historica completa,
  com contexto, causas-raiz e comandos cronologicos;
- use os arquivos especializados desta pasta quando quiser aprofundar apenas um
  assunto.

Leitura complementar por assunto:

- `README.md`: indice geral da infraestrutura de `prod` e `stg`
- `ecs/README.md`: build, push e rollout dos servicos no `ECS`
- `docker/README.md`: imagem do runtime Keycloak customizado
- `cloudflare/README.md`: DNS e registros `TXT`
- `validacao_cabecalho_email_provedores.md`: entregabilidade e validacao de
  e-mail

## Credenciais e valores operacionais documentados

Este arquivo e apenas o ponto de entrada. Os valores reais e os caminhos de
credenciais usados na operacao continuam documentados nos arquivos abaixo:

- `CREDENCIAIS_RAPIDAS.md`
  Acesso direto aos valores e caminhos reais mais usados.
- `cloudflare/README.md`
  Mantem os valores reais atuais de `CLOUDFLARE_API_TOKEN`,
  `CLOUDFLARE_ACCOUNT_ID` e `zone IDs`.
- `README.md`, nesta mesma pasta
  Mantem a referencia operacional do broker Apple em
  `.local-secrets/apple/eickrono-oidc/prod/keycloak-apple.env`.
- `guia_subida_stg_aws.md`
  Mantem o detalhamento completo de secrets, `SMTP`, `KEYCLOAK_ADMIN`,
  `KEYCLOAK_ADMIN_PASSWORD`, `mTLS`, `client secrets` e exemplos operacionais
  reais usados nas rodadas anteriores.
- `historico_execucao_stg_aws_*.md`
  Mantem registros cronologicos por rodada quando a operacao exigiu consultar ou
  atualizar credenciais reais.

## Ordem operacional recomendada

1. Preparar acesso e historico local

- autenticar na AWS com o profile correto;
- definir `EICKRONO_STG_HISTORICO` antes de executar comandos sensiveis ou
  rollout;
- confirmar que os artefatos locais e segredos esperados estao disponiveis.

2. Empacotar os servicos

- no `eickrono-autenticacao-servidor`, rodar `make package-servicos` quando a
  rodada envolver autenticacao, identidade e `thimisu-backend`;
- se a mudanca for isolada, empacotar apenas o servico necessario antes do
  build de imagem.

3. Construir e publicar a imagem

- para `auth`, `identidade` ou `thimisu-backend`, usar
  `infraestrutura/prod/ecs/build_push_stg_image.sh`;
- validar primeiro com `--dry-run`;
- so depois executar o push real com `--profile Codex-cli_aws`.

4. Executar o rollout do servico

- usar `infraestrutura/prod/ecs/rollout_stg_service.sh`;
- quando precisar mudar host, porta, nome do banco ou usuario por servico,
  usar `--db-overrides-file infraestrutura/prod/ecs/stg-db-overrides.example.env`
  como base e ajustar os valores necessarios nesse arquivo ou em uma copia dele;
- registrar imagem, task definition e resultado no historico;
- acompanhar `running`, `pending` e `rolloutState` ate `COMPLETED`.

5. Validar a malha publica e interna

- confirmar:
  - `https://oidc-stg.eickrono.store/realms/eickrono/eickrono-runtime/estado`
  - `https://id-stg.eickrono.store/api/v1/estado`
  - `https://thimisu-backend-stg.eickrono.store/api/v1/estado`
- validar `issuer`, discovery OIDC e emissao de token interno;
- validar o endpoint publico de disponibilidade da identidade.

6. Validar dependencias auxiliares quando houver impacto

- DNS, certificados e `TXT`: `cloudflare/README.md`
- entregabilidade e cabecalhos: `validacao_cabecalho_email_provedores.md`
- certificados e imagem do Keycloak: `docker/README.md`

## Sinais minimos de ambiente saudavel

- `auth runtime`, `identidade` e `thimisu-backend` respondendo `200`;
- discovery OIDC publico consistente com o `issuer` canonico do ambiente;
- `client_credentials` interno emitido com `iss` e `aud` esperados;
- endpoint publico de disponibilidade retornando `disponivel=true`;
- login OIDC abrindo com `HTTP 200` em fluxo real de navegador/PKCE.

## Licoes aprendidas - validacao STG exclusao cadastro produto

Data de referencia: `2026-05-31`.

Esta secao registra o que foi confirmado durante a validacao do servico
administrativo de exclusao de cadastro/produto em `stg`. O objetivo e evitar
repetir erros de arquitetura AWS, `ECS`, `Cloud Map`, `RDS`, `Keycloak`,
`Flyway` e contratos internos.

### Topologia confirmada

Valores operacionais confirmados:

- `AWS_PROFILE=Codex-cli_aws`
- `AWS_REGION=sa-east-1`
- cluster `ECS`: `eickrono-stg`
- namespace `Cloud Map`: `stg.eickrono.internal`
- namespace id: `ns-xucpyhbknyc2ozcj`

Servicos relevantes:

| Servico ECS | Papel | Observacao |
| --- | --- | --- |
| `auth-stg` | Keycloak/OIDC publico | Publica `https://oidc-stg.eickrono.store/realms/eickrono` |
| `identidade-stg` | API Spring de identidade | Backchannel canonico de Pessoa |
| `thimisu-backend-stg` | API Spring do produto Thimisu | Backend de produto |
| `autenticacao-api-stg` | API Spring de autenticacao | Interna; modulo `modulo-eickrono-autenticacao`; nao e gerenciada pelos templates `ecs/*-task-definition.stg.json` atuais |

Servico interno criado/validado para a API Spring de autenticacao, separado do
Keycloak/OIDC `auth-stg`:

```text
autenticacao-api-stg-interno.stg.eickrono.internal
arn:aws:servicediscovery:sa-east-1:531708494702:service/srv-bw7ljrefdisyqebx
```

Security group criado para a API Spring de autenticacao:

```text
sg-00e0e62d88f67dfe1
nome: eickrono-stg-autenticacao-api
```

Regras de entrada confirmadas para `8081/8443`:

- `sg-05d90b4911b4326b8`
- `sg-0617ac8c71c32af1b`
- `sg-07f2f549bcfc75501`

Configuracao de rede usada nas tasks temporarias de consulta ao banco:

```text
awsvpcConfiguration={subnets=[subnet-064c1362d7b4635db,subnet-0d91dc50495fb52c9],securityGroups=[sg-05d90b4911b4326b8],assignPublicIp=DISABLED}
```

Na validacao da API de autenticacao, o servico usou tambem
`sg-00e0e62d88f67dfe1`.

### Bancos e schemas

O `RDS` de `stg` e privado. Acesso operacional seguro e via task temporaria
`Fargate` com `psql`, nao por conexao direta do Mac.

Bancos confirmados no mesmo host `RDS`:

- `keycloak_stg`
- `eickrono_identidade_stg`
- `eickrono_thimisu_stg`

Regras importantes:

- `autenticacao-api-stg` usa `eickrono_identidade_stg` com
  `currentSchema=identidade_stg`;
- `thimisu-backend-stg` deve usar `eickrono_thimisu_stg` com
  `currentSchema=thimisu_stg`;
- sem `currentSchema=thimisu_stg`, queries nativas/JPA que referenciam tabelas
  sem schema, por exemplo `perfis_sistema_historico`, podem falhar em runtime.

Problema confirmado de `Flyway`:

- `modulo-eickrono-autenticacao` e `eickrono-identidade-servidor` compartilham
  o banco `eickrono_identidade_stg`;
- os dois projetos possuem sequencias de migrations que podem colidir;
- exemplos observados: `V33`, `V34` e outras versoes ja usadas pelo servidor de
  identidade.

Decisao operacional temporaria usada em `stg`:

```text
SPRING_FLYWAY_ENABLED=false
```

Essa decisao foi aplicada em `autenticacao-api-stg` para permitir subir o
servico enquanto as migrations necessarias foram aplicadas manualmente.

Objetos aplicados manualmente para a validacao:

- `auditoria.exclusoes_cadastro_produto`
- `auditoria.exclusoes_cadastro_produto_etapas`
- ajustes em `auditoria.usuarios_clientes_ecossistema_historico`
- ajustes em `auditoria.usuarios_historico`
- indices das migrations de exclusao de cadastro/produto;
- tabela minima `autenticacao.parametros_scheduler_integracao_produto` com
  `id=1` e `habilitado=false`, porque o scheduler consulta essa tabela mesmo
  quando sai sem executar.

Divida tecnica:

- separar definitivamente a responsabilidade de migrations por schema/banco;
- ou criar baseline separado para a API Spring de autenticacao;
- ou mover a API de autenticacao para banco/schema proprio antes de depender de
  `Flyway` automatico em `stg`.

### ECS, imagem e plataforma

Erro confirmado:

```text
CannotPullContainerError: image Manifest does not contain descriptor matching
platform 'linux/arm64 v8'
```

Causa:

- a task `Fargate` estava em `linux/arm64`;
- a imagem foi publicada apenas como `linux/amd64`.

Regra operacional:

- imagens para esses servicos precisam ser publicadas com manifesto compativel
  com a plataforma da task;
- antes de trocar a imagem em `ECS`, confirmar arquitetura da task definition.

Erro confirmado de build:

```text
COPY target/modulo-eickrono-autenticacao-*.jar /app/aplicacao.jar:
/target no such file or directory
```

Causa:

- o `Dockerfile` do modulo copia `target/...`;
- o build foi iniciado com contexto errado.

Regra operacional:

- para a API Spring de autenticacao, o contexto correto de build e:

```text
eickrono-autenticacao-servidor/modulos/modulo-eickrono-autenticacao
```

Nao usar o root do repositorio como contexto para esse `Dockerfile` sem alterar
o `COPY`.

Erro confirmado de tag:

- o `ECR` esta com imutabilidade de tag habilitada;
- uma tag ja publicada nao pode ser sobrescrita.

Regra operacional:

- gerar tag nova para cada tentativa real de deploy;
- nao assumir que `docker push` vai substituir uma imagem de `stg`.

Historico relevante da validacao:

| Task definition | Resultado |
| --- | --- |
| `autenticacao-api-stg:7` | ultima base estavel antes do ajuste SQL final |
| `autenticacao-api-stg:8` | apontava para imagem sem manifesto `arm64` |
| `autenticacao-api-stg:9` | imagem `stg-api-20260531-exclusao-cadastro-v5`, validada |
| `thimisu-backend-stg:11` | adicionou cliente interno permitido |
| `thimisu-backend-stg:12` | adicionou `currentSchema=thimisu_stg`, validada |

### mTLS, healthcheck e configuracao interna

Erro confirmado anterior:

- a API Spring de autenticacao falhou ao apontar `keyStore/trustStore` para
  caminho inexistente/obsoleto em `/app/seguranca/mtls`;
- nao reutilizar `api-autenticacao-eickrono.p12` nem
  `servidor-autorizacao.p12` em novos seeds de EFS.

Configuracao esperada para a API Spring de autenticacao, se esse servico
separado for publicado em `stg`:

```text
SEGURANCA_MTLS_KEYSTORE_ARQUIVO=file:/app/seguranca/mtls/eickrono-autenticacao.p12
SEGURANCA_MTLS_TRUSTSTORE_ARQUIVO=file:/app/seguranca/mtls/backchannel-truststore.p12
```

Configuracao esperada para o Keycloak/OIDC `auth-stg` nos templates ECS atuais:

```text
EICKRONO_INTERNO_MTLS_KEYSTORE_ARQUIVO=/certificados/eickrono-keycloak.p12
EICKRONO_INTERNO_MTLS_TRUSTSTORE_ARQUIVO=/certificados/backchannel-truststore.p12
```

Healthcheck:

- o health da API de autenticacao falhou por dependencia de e-mail;
- para o endpoint de health do servico interno, foi necessario desabilitar o
  health de mail:

```text
MANAGEMENT_HEALTH_MAIL_ENABLED=false
```

### Keycloak, token interno e autorizacao

Problema confirmado:

- o client `autenticacao-servidor` emitia `client_credentials` sem `scope` e
  sem roles esperadas;
- o `aud` inicial continha `thimisu-backend`, mas nao necessariamente todos os
  auditores internos necessarios.

Estado funcional apos ajuste:

- `aud` contem `thimisu-backend` e `eickrono-autenticacao`;
- `realm_access.roles` contem `admin`;
- `scope` ainda pode vir vazio.

Regra do endpoint administrativo:

- `POST /api/interna/usuarios/exclusoes` aceita `ROLE_admin` ou
  `SCOPE_admin:exclusoes`;
- em `stg`, o caminho validado foi por role (`ROLE_admin`).

Problema confirmado no `thimisu-backend`:

- o backend valida o chamador interno por `azp/client_id`;
- o valor permitido default nao era o client real usado pela API de
  autenticacao.

Configuracao funcional:

```text
INTEGRACAO_AUTENTICACAO_CLIENTE_INTERNO_PERMITIDO=autenticacao-servidor
```

### Endpoints internos validados

API Spring de autenticacao, separada do Keycloak/OIDC `auth-stg`:

```text
GET  http://autenticacao-api-stg-interno.stg.eickrono.internal:8081/actuator/health
POST http://autenticacao-api-stg-interno.stg.eickrono.internal:8081/api/interna/usuarios/exclusoes
```

Readiness operacional:

- `autenticacao-api-stg` nao fica atras de ALB (Application Load Balancer);
- a task definition `autenticacao-api-stg:12` valida readiness por health check
  de container contra `http://localhost:8081/actuator/health`;
- o ECS deve mostrar duas tasks `RUNNING` e `HEALTHY` antes de considerar o
  servico pronto.

`thimisu-backend`:

```text
POST /api/interna/perfis-sistema/exclusoes-cadastro-produto/dry-run
POST /api/interna/perfis-sistema/exclusoes-cadastro-produto/execucoes
```

Observacao:

- chamadas diretas para `thimisu-backend` em `8082` a partir da task de
  manutencao podem expirar por regra de rede/security group;
- o fluxo funcional validado usa comunicacao interna HTTPS/mTLS em `8443`;
- nao assumir que uma porta interna esta acessivel de qualquer task.

### Erros de SQL encontrados

Erro confirmado:

```text
BadSqlGrammarException
```

Causas encontradas:

- query dinamica usando parametro nulo em expressao como
  `:vinculoId IS NULL`;
- update com coluna `status` ambigua na tabela de etapas da exclusao;
- historicos de auditoria com colunas `NOT NULL` que nao aceitavam evento
  anonimizado sem `usuario_id` ou `vinculo_id`.

Correcoes aplicadas no codigo:

- resolver produto por `codigo`, `produto_exibicao` ou `nome`;
- gerar SQL diferente quando `vinculoId` for nulo;
- qualificar colunas como `etapa.status` e `etapa.concluido_em`;
- ajustar schema de auditoria para permitir registros de exclusao/anonimizacao.

### Validacao final realizada

Alvo sintetico usado:

```text
qa-exclusao-20260531063739
```

Resultado:

- `dryRun=true`: `HTTP 200`, sem bloqueios;
- `dryRun=false`: `HTTP 200`, sem bloqueios;
- `autenticacao.usuarios`: sem registro remanescente do alvo;
- `autenticacao.usuarios_clientes_ecossistema`: sem vinculo remanescente do
  alvo;
- `auditoria.exclusoes_cadastro_produto`: execucao `CONCLUIDA`;
- `thimisu_stg.perfis_sistema`: sem registro remanescente do alvo;
- `thimisu_stg.pessoas_produto_local`: sem registro remanescente do alvo;
- logs recentes em `/ecs/stg/autenticacao`: sem `ERROR` apos o ajuste final;
- logs recentes em `/ecs/stg/thimisu-backend`: sem `ERROR` apos o ajuste final.

### Risco de consistencia compensavel

Foi observado um caso parcial anterior:

- o produto apagou os dados;
- a autenticacao falhou depois ao gravar auditoria;
- a repeticao da operacao encontrou o produto ja limpo e a autenticacao ainda
  com residuos.

Esse caso confirma que o servico destrutivo precisa continuar sendo
idempotente e compensavel:

- gravar a intencao/execucao antes de acionar sistemas externos;
- registrar cada etapa de forma independente;
- permitir retry sem duplicar erro;
- conseguir diferenciar `nao encontrado porque ja foi apagado` de
  `nao encontrado porque o alvo nunca existiu`;
- manter correlacao unica para auditoria.

### Regras operacionais para proximas validacoes

Antes de nova subida:

- confirmar plataforma da task (`arm64` vs `amd64`);
- publicar tag nova no `ECR`;
- confirmar `currentSchema` do datasource de cada servico;
- validar se `Flyway` esta habilitado ou se ha conflito de versionamento;
- testar token `client_credentials` e claims (`aud`, `azp`, roles/scopes);
- validar `actuator/health` antes de chamar endpoint funcional;
- fazer `dryRun` antes de execucao real;
- conferir logs de `autenticacao` e `thimisu-backend` apos a execucao.

### Session Manager Plugin e ECS Exec

O `SessionManagerPlugin` e um binario local usado pelo `AWS CLI` para abrir
sessoes interativas pelo AWS Systems Manager. Ele e necessario para comandos
como:

```bash
aws ecs execute-command ...
```

No contexto de STG, ele serve para acessar uma task ECS/Fargate e executar
comandos dentro do container, por exemplo validar um endpoint interno que nao
tem DNS publico:

```bash
AWS_PROFILE=Codex-cli_aws AWS_REGION=sa-east-1 \
aws ecs execute-command \
  --cluster eickrono-stg \
  --task <task-arn> \
  --container autenticacao-api \
  --interactive \
  --command "sh -lc 'wget -qO- http://127.0.0.1:8081/actuator/health'"
```

Uso tipico:

- validar `actuator/health` diretamente dentro da task;
- consultar Swagger/OpenAPI interno, como
  `/v3/api-docs/operacoes-internas`;
- testar resolucao de nomes internos do Cloud Map;
- conferir arquivos e variaveis de ambiente do container em uma investigacao
  controlada.

O plugin nao substitui CloudWatch Logs, nao concede permissao sozinho e nao
ativa ECS Exec no servico. Ele apenas permite que o `AWS CLI` abra a sessao
quando a infraestrutura ja estiver preparada.

Pre-requisitos AWS para `ecs execute-command`:

- o `SessionManagerPlugin` deve existir no computador local;
- o servico/task ECS precisa estar com `enableExecuteCommand=true`;
- a task precisa estar conectada ao agente/SSM;
- a role da task e/ou execution role precisa das permissoes SSM necessarias;
- o usuario AWS precisa permissao para `ecs:ExecuteCommand` e canais SSM.

Erro comum:

```text
SessionManagerPlugin is not found
```

Causa:

- o plugin nao esta instalado ou nao esta no `PATH` local.

Correcao com Homebrew, quando o terminal permite senha de administrador:

```bash
brew install --cask session-manager-plugin
session-manager-plugin --version
```

Em ambiente nao interativo, o cask pode falhar porque o instalador `.pkg`
precisa de `sudo`:

```text
sudo: a terminal is required to read the password
```

Alternativa local sem `sudo`, usada nesta maquina:

```bash
brew fetch --cask session-manager-plugin --force
pkgutil --expand ~/Library/Caches/Homebrew/downloads/*--session-manager-plugin.pkg /tmp/session-manager-plugin-pkg
mkdir -p /tmp/session-manager-plugin-payload
cd /tmp/session-manager-plugin-payload
cpio -i < /tmp/session-manager-plugin-pkg/Payload
mkdir -p ~/.local/bin
cp /tmp/session-manager-plugin-payload/usr/local/sessionmanagerplugin/bin/session-manager-plugin ~/.local/bin/session-manager-plugin
chmod +x ~/.local/bin/session-manager-plugin
ln -s ~/.local/bin/session-manager-plugin /opt/homebrew/bin/session-manager-plugin
session-manager-plugin --version
```

Resultado validado nesta maquina:

```text
session-manager-plugin 1.2.814.0
```

Erro comum apos o plugin estar instalado:

```text
TargetNotConnectedException
```

Causa:

- o comando ja chegou na AWS, entao o plugin local esta funcionando;
- a task ECS alvo nao esta conectada/preparada para ECS Exec.

Acao correta nesse caso:

- conferir se o servico esta com `enableExecuteCommand=true`;
- conferir IAM/SSM da task;
- forcar novo deployment se a opcao foi habilitada depois da task atual ter
  sido criada;
- usar CloudWatch Logs como alternativa enquanto ECS Exec nao estiver pronto.

Correção aplicada em `stg` para a API Spring de autenticação:

- `autenticacao-api-stg` já estava com `enableExecuteCommand=true`;
- o container já iniciava `ExecuteCommandAgent`;
- faltavam permissões `ssmmessages` na task role
  `eickrono-stg-ecs-task-role`.

Policy inline adicionada na role:

```text
eickrono-stg-ecs-exec-ssmmessages
```

Ações permitidas:

```text
ssmmessages:CreateControlChannel
ssmmessages:CreateDataChannel
ssmmessages:OpenControlChannel
ssmmessages:OpenDataChannel
```

Após aplicar a policy, foi necessário forçar novo deployment do serviço para a
task nascer com as credenciais atualizadas:

```bash
AWS_PROFILE=Codex-cli_aws AWS_REGION=sa-east-1 \
aws ecs update-service \
  --cluster eickrono-stg \
  --service autenticacao-api-stg \
  --force-new-deployment
```

Resultado validado:

- `ecs execute-command` abriu sessão na task nova;
- o erro `TargetNotConnectedException` deixou de ocorrer.

Erro diferente observado ao consultar Swagger pelo próprio container:

```text
HTTP/1.1 403
```

Causa:

- a infraestrutura de ECS Exec está funcionando;
- o bloqueio vem do `FiltroWhitelistIp` do Swagger;
- chamadas locais feitas dentro do container chegam como `127.0.0.1`;
- a whitelist atual de `stg` permite apenas os IPs configurados em
  `autenticacao.swagger.ips-permitidos`.

Interpretação correta:

- `TargetNotConnectedException` = problema de ECS Exec/SSM/IAM/task;
- `HTTP 403` no Swagger = proteção de aplicação por whitelist/autenticação.

Não confundir esses dois erros durante validações.

## Trilha historica associada

- `guia_subida_stg_aws.md`: runbook historico consolidado e hibrido
- `historico_execucao_stg_aws_*.md`: registros de execucao pontuais por rodada
