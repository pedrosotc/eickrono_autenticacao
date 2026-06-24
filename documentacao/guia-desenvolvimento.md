# Guia de Desenvolvimento

Este guia orienta a preparação do ambiente local e o fluxo de trabalho diário para contribuir com a stack **Eickrono Autenticação**.

## Requisitos locais

- **Sistema operacional:** macOS, Linux ou Windows 11 com WSL2.
- **JDK:** Temurin/Adoptium Java 21.
- **Maven:** 3.9 ou superior.
- **Docker + Docker Compose:** para execução dos ambientes dev/stg.
- **Node.js (opcional):** apenas se for necessário personalizar o tema do Keycloak com toolchain front-end.
- **Ferramentas auxiliares:** `make`, `openssl` para geração de certificados, `psql` para interações com PostgreSQL.

## Primeiros passos

1. Execute `mvn -version` para validar a instalação do Maven e do JDK 21.
2. Rode `mvn verify` na raiz para baixar dependências e validar qualidade.
3. Revise `infraestrutura/dev/.env` e personalize os valores locais com segurança.
4. Execute `docker compose up` em `infraestrutura/dev` para subir Keycloak, PostgreSQL e as APIs.
5. Acesse `http://127.0.0.1:8081/actuator/health` e `http://127.0.0.1:8082/actuator/health` para verificar se as APIs estão saudáveis.

Estrutura prática do repositório:

- `modulos/modulo-eickrono-autenticacao/`: código Java da API pública/autenticada de autenticação
- `modulos/modulo-eickrono-keycloak/`: provider, SPI e runtime customizado do Keycloak
- `autorizacao/`: realms, tema e artefatos externos montados no container
- `infraestrutura/`: `docker compose`, certificados e variáveis por ambiente
- `documentacao/`: guias operacionais e arquiteturais

Mapa rápido de nomenclatura:

- módulo Maven da API pública/autenticada: `modulo-eickrono-autenticacao`
- módulo Maven do provider Keycloak: `modulo-eickrono-keycloak`
- serviço Docker da API pública: `eickrono-autenticacao`
- serviço Docker do Keycloak: `eickrono-keycloak`

Atalho operacional canônico:

- `make package-servicos`
- `make compose-config`
- `make up-dev`

### Regra operacional importante do `docker compose`

No ambiente local, os serviços Java rodam a partir de artefatos já empacotados:

- `eickrono-identidade-servidor` fornece o `jar` da API de identidade;
- `eickrono-contas-servidor` fornece o `jar` da API de contas;
- `eickrono-autenticacao-servidor` fornece:
  - o `jar` de `modulo-eickrono-autenticacao` para a API pública;
  - o `jar` de `modulo-eickrono-keycloak` montado dentro do Keycloak.

Por isso, alteração de código sem novo `package` e sem recriar o serviço deixa o container executando a versão antiga.

Comandos canônicos:

- empacotar os três projetos da stack:
  - `make package-servicos`
- testar a bateria representativa dos três projetos:
  - `make test-servicos`
- testar a suíte completa dos três serviços:
  - `make test-servicos-completo`
  - exige `Docker` acessível, porque a identidade sobe infraestrutura via `Testcontainers`
- validar os `docker compose` locais:
  - `make compose-config`
- subir a stack `dev`:
  - `make up-dev`

Se precisar agir isoladamente em um serviço:

- API identidade:
  - `cd ../eickrono-identidade-servidor && mvn -q package -DskipTests`
- API contas:
  - `cd ../eickrono-contas-servidor && mvn -q package -DskipTests`
- autenticação, só API pública:
  - `mvn -q -pl modulos/modulo-eickrono-autenticacao -am package -DskipTests`
- autenticação, só provider Keycloak:
  - `mvn -q -pl modulos/modulo-eickrono-keycloak -am package -DskipTests`
- autenticação, conjunto interno completo:
  - `mvn -q package -DskipTests`

Se um comportamento visto no app não bater com o código atual, valide primeiro se o container local foi realmente recriado.

Observações importantes do fluxo canônico:

- a borda pública final do app deve convergir para `autenticacao` para
  cadastro, confirmação de e-mail, login, refresh e recuperação de senha;
- referências a contratos públicos ainda localizados em
  `modulo-eickrono-autenticacao` devem ser lidas como estado físico atual do
  código;
- referências a `api-identidade-eickrono` devem ser lidas como nomenclatura
  legada/operacional, não como arquitetura final aprovada;
- o servidor de autorização continua sendo a autoridade de credencial, sessão, refresh e políticas de segurança;
- depois da confirmação de e-mail, a autenticação conclui a conta central e aciona a identidade por comunicação interna entre servidores para criar ou atualizar a `Pessoa` canônica;
- depois disso, a autenticação aciona o backend do produto para criar ou atualizar o perfil daquele sistema;
- esses provisionamentos internos precisam ser idempotentes por `cadastroId`;
- o login público já emite o `X-Device-Token` quando o backend aprova o aparelho;
- o app não usa mais tela dedicada de registro de dispositivo no fluxo principal;
- se o backend exigir nova validação de contato, o app deve reutilizar a tela já existente de verificação;
- em `docker compose`, a própria API de identidade precisa apontar o Keycloak interno para `http://eickrono-keycloak:8080`, não para `localhost`;
- a derivação da senha efetiva no servidor de autorização usa apenas `pepper + createdTimestamp` do usuário no Keycloak, e não mais `data_nascimento`.
- em `dev` e `stg`, o `docker compose` já inclui um SMTP de teste (`MailHog`) publicado no host local, mas o `dev` pode ser sobrescrito para SMTP real via `.env`.
- quando `IDENTIDADE_CADASTRO_EMAIL_FORNECEDOR=smtp`, o envio passa a usar `JavaMailSender` com as propriedades `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` e derivados.
- os `docker compose` executados no computador do desenvolvedor sobem `MailHog` para capturar esses e-mails:
  - `dev`: UI em `http://localhost:8025`
  - `stg`: UI em `http://localhost:18025`

## Como ver códigos de e-mail no MailHog

Quando cadastro ou recuperação de senha disparam envio de e-mail, o `eickrono-autenticacao-servidor` pode entregar a mensagem para um SMTP fake local (`MailHog`) ou para um SMTP real, dependendo da composição usada no `dev`.

Fluxo prático em `dev`:

1. suba o ambiente local do auth com o override fake:
   - `cd infraestrutura/dev`
   - `docker compose -f docker-compose.yml -f docker-compose.email-fake.yml up -d`
2. confirme que o `MailHog` está ativo:
   - `docker ps | rg eickrono-mailhog-dev`
3. execute o fluxo do app até a tela de verificação do código;
4. abra a UI do `MailHog` em `http://localhost:8025`;
5. na lista de mensagens, procure o e-mail cujo destinatário é o endereço usado no fluxo;
6. abra a mensagem;
7. leia o corpo do e-mail e copie o valor da linha `Código de confirmação: XXXXXX`;
8. volte para o app e digite esse código na tela de verificação.

Fluxo prático com o compose de `stg`:

1. suba o compose de `stg`:
   - `cd infraestrutura/stg`
   - `docker compose up -d`
2. abra a UI do `MailHog` em `http://localhost:18025`;
3. localize o e-mail correspondente e copie o código da mesma forma.

Observações importantes:

- o código enviado por e-mail é numérico e hoje possui 6 dígitos;
- o link `Reenviar código` do app é funcional para e-mail;

## Como usar um SMTP real em vez do MailHog

O backend já suporta SMTP real. O que prendia o ambiente local ao MailHog era apenas o `docker compose` de `dev`/`stg`.

Agora:

- se você não sobrescrever nada e o `.env` não trouxer SMTP real, o ambiente usa o MailHog local;
- se você preencher `SPRING_MAIL_*` e `IDENTIDADE_CADASTRO_EMAIL_*` no `.env`, a API passa a usar esse SMTP real sem depender do MailHog;
- se o `.env` já estiver com SMTP real, você ainda pode forçar o MailHog temporariamente com `docker compose -f docker-compose.yml -f docker-compose.email-fake.yml ...`.

Exemplo local:

1. abra [`infraestrutura/dev/.env.email-real.exemplo`](/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/infraestrutura/dev/.env.email-real.exemplo);
2. copie as variáveis desejadas para [`infraestrutura/dev/.env`](/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/infraestrutura/dev/.env);
3. ajuste host, porta, usuário e credencial do seu provedor;
4. reinicie o ambiente:
   - `cd infraestrutura/dev`
   - `docker compose down`
   - `docker compose up -d`

## Como forcar MailHog no dev sem mexer no `.env` real

Quando o `infraestrutura/dev/.env` ja estiver apontando para um SMTP real, use o override
[`docker-compose.email-fake.yml`](/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/infraestrutura/dev/docker-compose.email-fake.yml)
para redirecionar apenas a API pública de autenticação ao `smtp-teste`.

Comandos canonicos:

1. subir ou recriar a API com SMTP fake:
   - `cd infraestrutura/dev`
   - `docker compose -f docker-compose.yml -f docker-compose.email-fake.yml up -d --build smtp-teste eickrono-autenticacao identidade-servidor`
2. validar a configuracao efetiva:
   - `docker compose -f docker-compose.yml -f docker-compose.email-fake.yml config | rg 'SPRING_MAIL_HOST|SPRING_MAIL_PORT|SPRING_MAIL_USERNAME|IDENTIDADE_CADASTRO_EMAIL_REMETENTE'`
3. abrir a UI do MailHog:
   - `http://localhost:8025`
4. quando quiser voltar ao SMTP real definido no `.env`:
   - `docker compose up -d --build eickrono-autenticacao identidade-servidor`

Observacoes:

- esse override nao altera nem apaga as credenciais reais do `.env`;
- ele sobrescreve apenas o container `eickrono-autenticacao`;
- `smtp-teste` continua sendo o mesmo MailHog ja previsto no `docker-compose.yml`;
- se quiser personalizar remetente fake, use variaveis opcionais como `IDENTIDADE_CADASTRO_EMAIL_FAKE_REMETENTE` no shell antes do `docker compose`.

Observações:

- não extraímos credenciais automaticamente de apps locais como Outlook, Gmail ou Mail do macOS;
- para segurança e previsibilidade, o servidor deve receber essas credenciais explicitamente por variável de ambiente;
- para Gmail pessoal, o fluxo mais comum é usar `smtp.gmail.com:587` com `STARTTLS` e uma senha de app;
- para Outlook/Microsoft, o host costuma ser `smtp-mail.outlook.com:587` com autenticação e `STARTTLS`.
- para iCloud Mail, o host oficial é `smtp.mail.me.com`, porta `587`, autenticação obrigatória, `STARTTLS/TLS`, usuário como endereço completo e senha de app da Apple Account.
- se você usar um domínio customizado no iCloud+ como `info@eickrono.com`, o remetente pode continuar sendo o endereço customizado, mas o login SMTP deve usar o endereço primário `@icloud.com` da conta Apple que hospeda esse domínio.
- no caso de `eickrono.com`, o DNS atual confirma esse cenário: MX em `mx01.mail.icloud.com` e `mx02.mail.icloud.com`, SPF com `include:icloud.com` e DKIM delegada para `icloudmailadmin.com`.
- ao reenviar, o sistema gera um código novo e invalida o anterior;
- portanto, sempre use o código do e-mail mais recente no `MailHog`;
- se nenhum e-mail aparecer, verifique primeiro se a API de identidade e o `MailHog` estão rodando no `docker compose`.

Referências do comportamento no código:

- envio SMTP do código: `../eickrono-identidade-servidor/src/main/java/com/eickrono/api/identidade/aplicacao/servico/CanalEnvioCodigoCadastroEmailSmtp.java`
- geração e reenvio do código: `../eickrono-identidade-servidor/src/main/java/com/eickrono/api/identidade/aplicacao/servico/CadastroContaInternaServico.java`

## PostgreSQL compartilhado em dev

O ambiente `dev` usa um PostgreSQL externo já existente no Docker local, compartilhado pelos projetos.

Credenciais de acesso manual:

- **Host:** `localhost`
- **Porta:** `5432`
- **Usuário:** `adm`
- **Senha:** `AdmDev2026!`
- **JDBC URL do autorização:** `jdbc:postgresql://localhost:5432/eickrono_autorizacao`
- **JDBC URL da identidade:** `jdbc:postgresql://localhost:5432/eickrono_identidade`
- **JDBC URL de contas:** `jdbc:postgresql://localhost:5432/eickrono_contas`
- **JDBC URL do thimisu:** `jdbc:postgresql://localhost:5432/eickrono_thimisu`

Observações:

- essas credenciais servem apenas para desenvolvimento local;
- o usuário `adm` foi criado como `SUPERUSER` para facilitar inspeção e administração do banco;
- as aplicações continuam usando seus próprios usuários técnicos configurados em `infraestrutura/dev/.env`;
- no `docker compose` local, a separação por serviço usa:
  - `KEYCLOAK_POSTGRES_*`
  - `IDENTIDADE_POSTGRES_*`
  - `CONTAS_POSTGRES_*`

## Execução do perfil stg no mesmo PostgreSQL

O compose de `stg` usa o mesmo servidor PostgreSQL em `localhost:5432`, mas com
bancos separados para evitar mistura com o `dev`. Isso não cria um ambiente
novo; o ambiente é `stg` e a forma de execução é Docker no computador do
desenvolvedor.

Bancos usados pelo compose de `stg`:

- `keycloak_stg`
- `eickrono_identidade_stg`
- `eickrono_contas_stg`

Portas publicadas pelo compose de `stg`:

- Keycloak: `18080`
- API identidade: `18081`
- API contas: `18082`

Credenciais de acesso manual ao mesmo PostgreSQL compartilhado:

- **Host:** `localhost`
- **Porta:** `5432`
- **Usuário:** `adm`
- **Senha:** `AdmDev2026!`
- **JDBC URL do Keycloak em stg:** `jdbc:postgresql://localhost:5432/keycloak_stg`
- **JDBC URL da identidade em stg:** `jdbc:postgresql://localhost:5432/eickrono_identidade_stg`
- **JDBC URL de contas em stg:** `jdbc:postgresql://localhost:5432/eickrono_contas_stg`

Observações da execução do profile `stg` via Docker:

- a malha interna `api-identidade <-> thimisu` e `eickrono-keycloak -> api-identidade` já usa `mTLS`;
- o `api-contas-eickrono` continua fora dessa malha no `docker-compose` atual;
- localmente, as APIs de identidade e contas usam `ddl-auto=update` para complementar tabelas ainda não cobertas pelas migrations atuais;
- o objetivo dessa execução é testar o fluxo real de login sem dividir estado com o `dev`.
- no `docker compose` de `stg`, a separação por serviço usa:
  - `KEYCLOAK_POSTGRES_*`
  - `IDENTIDADE_POSTGRES_*`
  - `CONTAS_POSTGRES_*`
- detalhes de portas, stores e geração de certificados estão em `guia-mtls.md`.

## Swagger

Endereços por API:

- API autenticacao `dev`: `http://127.0.0.1:8081/swagger-ui/index.html`
- API autenticacao `dev` OpenAPI: `http://127.0.0.1:8081/v3/api-docs`
- API identidade `stg`: `http://localhost:18081/swagger-ui/index.html`
- API identidade `stg` OpenAPI: `http://localhost:18081/v3/api-docs`
- API contas `dev`: `http://localhost:8082/swagger-ui/index.html`
- API contas `dev` OpenAPI: `http://localhost:8082/v3/api-docs`
- API contas `stg`: `http://localhost:18082/swagger-ui/index.html`
- API contas `stg` OpenAPI: `http://localhost:18082/v3/api-docs`

Proteção por ambiente:

- `dev`: acesso liberado para uso local;
- `stg`: `Basic Auth` + whitelist de IP;
- credenciais padrão de `stg`: usuário `swagger`, senha `swagger-stg`.

## Fluxo Git recomendado

- Branch principal: `main`.
- Branches de feature: `feature/<descricao-curta>`.
- Commits pequenos e em português.
- Pull request acompanhado do `checklist-seguranca-fapi.md` preenchido.
- Configure previamente as credenciais Git (PAT ou SSH). Um `git push --set-upstream origin main` falhará com `could not read Username` se o ambiente não puder autenticar no GitHub.

## Testes e qualidade

- `mvn verify`: executa testes, Checkstyle, SpotBugs e validações do Spring Boot.
- `cd ../eickrono-identidade-servidor && mvn spring-boot:run`: inicia apenas a API de identidade.
- `cd ../eickrono-contas-servidor && mvn spring-boot:run`: inicia a API de contas.
- Testcontainers é utilizado para testes de integração com PostgreSQL real; não é necessário subir um PostgreSQL manualmente para a suíte de testes, mas o Docker local precisa estar saudável.

### PostgreSQL real nos testes

- os projetos `eickrono-identidade-servidor` e `eickrono-contas-servidor` não usam mais H2 nos perfis de teste;
- os testes Spring Boot/integração sobem PostgreSQL real com Testcontainers;
- a falha histórica de `permission denied ... docker.sock` e `Could not find a valid Docker environment` não era problema de schema, mas de compatibilidade entre a stack antiga de Testcontainers e a API atual do Docker Desktop local;
- os testes **não** reutilizam host/porta do `docker compose`; o container de teste continua sendo criado pelo Testcontainers.
- o `docker compose` de `dev/stg` agora usa variáveis separadas por serviço:
  - `KEYCLOAK_POSTGRES_*`
  - `IDENTIDADE_POSTGRES_*`
  - `CONTAS_POSTGRES_*`
- os testes de integração podem reaproveitar defaults genéricos do ambiente quando eles existirem fora do `docker compose`, mas não dependem mais dessas chaves da stack local.
- também existem overrides específicos para os testes:
  - `EICKRONO_TEST_POSTGRES_IMAGE`
  - `EICKRONO_TEST_POSTGRES_DB_IDENTIDADE`
  - `EICKRONO_TEST_POSTGRES_DB_CONTAS`

### Diferença entre `.env` da stack local e Testcontainers

As variáveis de [`infraestrutura/dev/.env`](/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/infraestrutura/dev/.env) e [`infraestrutura/stg/.env`](/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/infraestrutura/stg/.env) descrevem o ambiente da aplicação.

Para a abertura de sessão interna por `backchannel`, mantenha o `client_id` do app alinhado ao realm de cada ambiente:
- `dev`: `app-flutter-local`
- `stg`: `app-flutter-stg`
- `prod`: `app-flutter-prod`

Para o provisionamento interno do cadastro nativo, mantenha tambem configurados na API de identidade:
- `identidade.cadastro.interna.keycloak.client-id`
- `identidade.cadastro.interna.keycloak.client-secret`
- `identidade.cadastro.interna.keycloak.realm`
- `identidade.cadastro.interna.keycloak.url-base`
- `identidade.cadastro.interna.keycloak.username-admin`
- `identidade.cadastro.interna.keycloak.password-admin`
- `identidade.cadastro.interna.keycloak.password-pepper`

Para o canal SMTP do cadastro nativo, as propriedades relevantes sao:
- `IDENTIDADE_CADASTRO_EMAIL_FORNECEDOR`
- `IDENTIDADE_CADASTRO_EMAIL_REMETENTE`
- `IDENTIDADE_CADASTRO_EMAIL_RESPONDER_PARA`
- `IDENTIDADE_CADASTRO_EMAIL_ASSUNTO`
- `IDENTIDADE_CADASTRO_EMAIL_NOME_APLICACAO`
- `SPRING_MAIL_HOST`
- `SPRING_MAIL_PORT`
- `SPRING_MAIL_USERNAME`
- `SPRING_MAIL_PASSWORD`
- `SPRING_MAIL_SMTP_AUTH`
- `SPRING_MAIL_SMTP_STARTTLS_ENABLE`

Já os testes de integração fazem outra coisa:

- criam um PostgreSQL efêmero por execução;
- escolhem porta dinâmica;
- isolam o banco do estado do `dev`/`stg`;
- podem reaproveitar nome de banco, usuário e senha via variáveis de ambiente para manter alinhamento sem acoplar os testes ao banco do compose.

Em outras palavras:

- as portas de banco definidas no `docker compose` **não** são usadas pelos testes;
- usuário, senha e nome de banco do ambiente local **podem** ser reaproveitados como defaults pelos containers de teste, quando fornecidos fora do `docker compose`;
- o OIDC dos testes da identidade continua simulado em memória e não depende do `OIDC_ISSUER_URI` do ambiente `dev/stg`.

### Diagnóstico reproduzível do Docker/Testcontainers

Durante esta etapa foi corrigido um sintoma enganoso:

- `permission denied while trying to connect to the docker API at unix:///Users/thiago/.docker/run/docker.sock`
- ou `Could not find a valid Docker environment`

O problema real não era ausência de rede e nem necessidade de apontar os testes para o PostgreSQL do `docker compose`. O problema era a combinação entre:

- Docker Desktop local saudável;
- socket Unix local acessível;
- stack de Testcontainers/docker-java antiga demais para conversar corretamente com a API atual do Docker.

#### Comandos executados no diagnóstico

1. Descobrir qual contexto do Docker estava ativo:

```bash
docker context show
```

Saída observada:

```text
desktop-linux
```

Interpretação:

- o CLI estava apontando para o daemon local do Docker Desktop;
- isso eliminou a hipótese de contexto remoto incorreto.

2. Confirmar que o socket local do Docker realmente respondia:

```bash
curl --silent --show-error --unix-socket "$HOME/.docker/run/docker.sock" http://localhost/version
```

Saída observada:

```json
{"Platform":{"Name":"Docker Desktop 4.64.0 (221278)"},"Version":"29.2.1","ApiVersion":"1.53","MinAPIVersion":"1.44","Os":"linux","Arch":"arm64", ...}
```

Interpretação:

- o daemon local estava vivo;
- o socket Unix local funcionava;
- a API efetiva do Docker Desktop era `1.53`, com mínimo `1.44`.

3. Comparar uma rota antiga com uma rota suportada pela API mínima do daemon:

```bash
curl --silent --show-error --unix-socket "$HOME/.docker/run/docker.sock" http://localhost/v1.41/info
curl --silent --show-error --unix-socket "$HOME/.docker/run/docker.sock" http://localhost/v1.44/info
```

Comportamento observado:

- `v1.41/info` respondeu com JSON degradado, com campos essenciais vazios;
- `v1.44/info` respondeu com dados consistentes do daemon.

Interpretação:

- o Docker Desktop atual ainda atende rotas mais antigas, mas de forma insuficiente para a descoberta automática esperada por clientes antigos;
- isso explicava por que uma stack velha de Testcontainers podia acusar "permission denied" ou "no valid Docker environment", mesmo com Docker local funcionando.

4. Confirmar a versão final da stack de testes:

```bash
rg -n "testcontainers.version|<artifactId>testcontainers|postgresql</artifactId>|h2</artifactId>" \
  ../eickrono-identidade-servidor/pom.xml \
  ../eickrono-contas-servidor/pom.xml
```

Estado final esperado:

- `pom.xml` raiz com `testcontainers.version` em `1.21.4`;
- dependência `org.testcontainers:postgresql` nos módulos de identidade e contas;
- ausência de `h2` nesses módulos.

#### Correção aplicada

A correção escolhida foi:

- manter Testcontainers;
- manter PostgreSQL real nos testes;
- atualizar Testcontainers para `1.21.4`;
- remover H2 dos testes Spring Boot de `api-identidade-eickrono` e `api-contas-eickrono`;
- manter o reaproveitamento apenas de `imagem`, `database`, `user` e `password` via variáveis de ambiente, sem reutilizar `host` e `porta` do ambiente `dev/stg`.

Arquivos centrais dessa decisão:

- [`pom.xml`](/Users/thiago/Desenvolvedor/flutter/eickrono-identidade-servidor/pom.xml)
- [`pom.xml`](/Users/thiago/Desenvolvedor/flutter/eickrono-contas-servidor/pom.xml)
- [`InfraestruturaTesteIdentidade.java`](/Users/thiago/Desenvolvedor/flutter/eickrono-identidade-servidor/src/test/java/com/eickrono/api/identidade/support/InfraestruturaTesteIdentidade.java)
- [`InfraestruturaTesteContas.java`](/Users/thiago/Desenvolvedor/flutter/eickrono-contas-servidor/src/test/java/com/eickrono/api/contas/support/InfraestruturaTesteContas.java)

#### Como repetir a validação final

1. Compilar os testes da identidade:

```bash
cd ../eickrono-identidade-servidor && mvn -U test-compile -DskipITs
```

2. Compilar os testes de contas:

```bash
cd ../eickrono-contas-servidor && mvn -U test-compile -DskipITs
```

3. Rodar os testes relevantes da identidade com PostgreSQL real:

```bash
cd ../eickrono-identidade-servidor && mvn -U \
  -Dtest=AplicacaoApiIdentidadeTest,RegistroDispositivoControllerIT,RegistroDispositivoServiceTest,CanalEnvioCodigoSmsTest \
  test
```

4. Rodar os testes relevantes de contas com PostgreSQL real:

```bash
cd ../eickrono-contas-servidor && mvn -U \
  -Dtest=AplicacaoApiContasTest,ApiContasDeviceTokenContractTest \
  test
```

5. Rodar os testes relevantes da política offline da identidade:

```bash
cd ../eickrono-identidade-servidor && mvn -U \
  -Dtest=AplicacaoApiIdentidadeTest,RegistroDispositivoControllerIT,RegistroDispositivoServiceTest,OfflineDispositivoServiceTest \
  test
```

6. Rodar os testes do servidor de autorização que bloqueiam refresh por device token:

```bash
cd .. && mvn -U test
```

#### Sinais esperados de sucesso

Durante a execução bem-sucedida, os logs devem conter linhas semelhantes a:

```text
Testcontainers version: 1.21.4
Found Docker environment with local Unix socket (unix:///var/run/docker.sock)
Connected to docker:
  Server Version: 29.2.1
  API Version: 1.53
Container postgres:15.5 started
BUILD SUCCESS
```

Quando a etapa de política offline estiver correta, também é esperado encontrar nos logs linhas semelhantes a:

```text
Mapped to com.eickrono.api.identidade.apresentacao.api.RegistroDispositivoController#obterPoliticaOffline()
Mapped to com.eickrono.api.identidade.apresentacao.api.RegistroDispositivoController#registrarEventosOffline(Jwt, String, RegistrarEventosOfflineRequest)
insert into eventos_offline_dispositivo
BUILD SUCCESS
```

Quando a etapa de refresh vinculado ao dispositivo estiver correta, também é esperado encontrar nos logs linhas semelhantes a:

```text
ClientPolicyEvent.TOKEN_REFRESH
GET /api/conta/dispositivos/token/validacao/interna
DEVICE_TOKEN_REVOKED
BUILD SUCCESS
```

### Refresh token vinculado ao device token

O fluxo final ficou assim:

1. O app recebe uma sessão remota emitida pela autenticação já com `device_token`.
2. O `device_token` opaco passa a ser persistido junto da sessão local.
3. Ao pedir refresh, o cliente envia o parâmetro adicional `device_token`.
4. O Keycloak aplica o executor `eickrono-device-token-refresh`.
5. O executor consulta a API pública de autenticação em `/api/conta/dispositivos/token/validacao/interna`.
6. Se a API responder que o token está revogado, expirado, inválido ou ausente, o refresh falha com `invalid_grant`.

Variáveis de ambiente relevantes para reproduzir esse fluxo:

- `EICKRONO_AUTENTICACAO_API_BASE_URL`
- `EICKRONO_INTERNAL_SECRET`
- `EICKRONO_AUTENTICACAO_TIMEOUT_MS`

Essas variáveis precisam existir no container do Keycloak e na API pública de autenticação. O `docker compose` de `dev` e `stg` já foi alinhado com isso. Os nomes `EICKRONO_IDENTIDADE_*` continuam aceitos apenas como fallback transitório.

#### O que esta correção não faz

- não usa o PostgreSQL já criado pelo `docker compose` de `dev`/`stg`;
- não reaproveita porta fixa do `docker compose`;
- não elimina a necessidade de Docker local acessível;
- não substitui Testcontainers por conexão em banco compartilhado.

Ela apenas torna os testes de integração compatíveis com o Docker Desktop atual e com PostgreSQL real de forma isolada.

## Alertas do Java LS/JDT em testes

Em alguns testes, especialmente com `Mockito`, `Spring Data` e `ArgumentCaptor`, o Java Language Server do VS Code pode exibir alertas como:

- `Null type safety: The expression of type ... needs unchecked conversion ...`
- `Missing non-null annotation ...`
- imports aparentemente não usados após refatorações

Esses alertas nem sempre indicam erro real de compilação. O caso mais comum na stack local é o analisador de nulidade do JDT não conseguir inferir corretamente contratos de:

- `captor.capture()`
- `captor.getValue()`
- `invocation.getArgument(...)`
- `save(any(...))`
- repositórios Spring Data/JPA usados como mocks
- serialização Jackson, por exemplo `objectMapper.writeValueAsString(...)`

### Como corrigir do jeito preferido no projeto

1. **Prefira fake/proxy em vez de Mockito** quando o teste só precisa observar `save(...)` ou manter estado em memória.
   Isso reduz ruído de nulidade e deixa o teste mais explícito.

2. **Use `Objects.requireNonNull(...)` em valores lidos**, não em matchers do Mockito.
   Exemplos bons:
   - `Objects.requireNonNull(captor.getValue())`
   - `Objects.requireNonNull(invocation.getArgument(0, MeuTipo.class))`
   - `Objects.requireNonNull(parametro, "mensagem")`

3. **Evite envolver diretamente** estes trechos com `Objects.requireNonNull(...)`:
   - `captor.capture()`
   - `any(...)`
   - `save(any(...))`

   Isso costuma agradar o compilador Maven, mas ainda pode deixar o JDT reclamando.

4. **Quando o alerta estiver em código de produção**, aplique `Objects.requireNonNull(...)` no argumento obrigatório ou no retorno que realmente não pode ser nulo.
   Exemplo válido:
   ```java
   return Objects.requireNonNull(
       repositorio.save(Objects.requireNonNull(entidade, "entidade é obrigatória")),
       "entidade salva é obrigatória");
   ```

5. **Quando a origem do valor for biblioteca externa sem anotação de nulidade confiável**, normalize o retorno no ponto de leitura.
   Exemplo típico no projeto:
   ```java
   String payload = Objects.requireNonNull(
       objectMapper.writeValueAsString(Map.of("chave", "valor")));
   ```
   Isso vale para casos como `MockMvc.content(...)`, em que a API de destino exige `@NonNull String` e o JDT não consegue provar isso a partir do Jackson.

6. **Se o teste depender só de auditoria/captura simples**, substitua `ArgumentCaptor` por uma lista/variável em memória dentro de um fake.

### Ordem prática de tratamento

Quando surgir esse tipo de alerta, siga esta ordem:

1. verificar se o código realmente compila com:
   - `mvn -pl <modulo> -am test-compile -DskipITs`
2. se o alerta vier de `Mockito`/`ArgumentCaptor`, tentar simplificar o teste com fake/proxy
3. usar `Objects.requireNonNull(...)` só nos pontos de leitura/contrato real
4. limpar o cache do editor:
   - `Java: Clean Java Language Server Workspace`
   - `Developer: Reload Window`

### Regra desta stack

- não usar `@SuppressWarnings("null")` como solução padrão
- não espalhar `@NonNull` artificialmente só para satisfazer a IDE
- preferir testes mais explícitos e menos dependentes de inferência do JDT

## Alertas de "never used" em testes Spring/JUnit

Além dos alertas de nulidade, o Java LS/JDT também pode marcar como "never used" elementos que são usados apenas por reflexão, por exemplo:

- classes com `@TestConfiguration`
- métodos `@Bean`
- classes auxiliares importadas por `@Import(...)`
- callbacks e estruturas que o Spring/JUnit instanciam indiretamente

Esses casos não são, por si só, erro de compilação. O problema é o analisador estático não enxergar o uso indireto feito por annotations.

### Como corrigir do jeito preferido no projeto

1. **Prefira configuração de teste top-level**, em arquivo próprio, em vez de `@TestConfiguration` interna dentro do teste.
2. **Referencie explicitamente a configuração** no teste com `@SpringBootTest(classes = ...)` e/ou `@Import(...)`.
3. **Evite depender de uso implícito por varredura** quando o objetivo é apenas fornecer beans fake de teste.
4. **Não usar `@SuppressWarnings("unused")` como padrão** para esse caso. Primeiro tente tornar a relação explícita no código.

### Exemplo aplicado no projeto

No teste de onboarding do dispositivo, a configuração interna foi extraída para uma classe própria:

- [`RegistroDispositivoControllerIT.java`](/Users/thiago/Desenvolvedor/flutter/eickrono-identidade-servidor/src/test/java/com/eickrono/api/identidade/apresentacao/api/RegistroDispositivoControllerIT.java)
- [`RegistroDispositivoControllerITConfiguration.java`](/Users/thiago/Desenvolvedor/flutter/eickrono-identidade-servidor/src/test/java/com/eickrono/api/identidade/apresentacao/api/RegistroDispositivoControllerITConfiguration.java)

Esse padrão é o preferido quando a IDE acusa que `@TestConfiguration` ou métodos `@Bean` "nunca são usados".

## Dicas adicionais

- Utilize perfis `application-dev.yml`, `application-stg.yml` e `application-prod.yml` para configurações específicas por ambiente.
- O Swagger (springdoc) fica acessível apenas em dev/stg, protegido por Basic Auth e whitelist em staging.
- Certificados mTLS autoassinados podem ser regenerados com o script `infraestrutura/dev/certificados/gerar_certificados.sh`.
- Para Keycloak, utilize os realms exportados em `autorizacao/realms/`.
