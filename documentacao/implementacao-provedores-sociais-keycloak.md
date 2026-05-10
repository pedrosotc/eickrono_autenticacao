# Implementação dos Provedores Sociais no Keycloak

Este documento explica em detalhe o que foi implementado no repositório para suportar provedores sociais no Keycloak do ecossistema Eickrono, por que cada mudança foi feita, quais arquivos foram alterados, o que já está pronto e o que ainda depende de credenciais externas reais.

## Contexto

A decisão funcional já estava fechada:

- o `eickrono-autenticacao-servidor` é a API canônica de vínculos sociais;
- o Keycloak é a fonte de verdade do vínculo;
- o fluxo de vínculo nasce no broker OIDC do Keycloak;
- a arquitetura precisa nascer genérica para `Google`, `Apple`, `Facebook`, `LinkedIn`, `Instagram` e `X`.

Com isso definido, a parte seguinte era preparar o servidor de autorização para reconhecer esses provedores de identidade de forma versionada nos realm exports.

O objetivo desta etapa não era concluir o login social do app Flutter.
O objetivo era deixar o Keycloak preparado para:

- autenticar com rede social usando `kc_idp_hint`;
- vincular conta social usando `kc_action=idp_link:<alias>`;
- manter os brokers versionados dentro dos exports de realm por ambiente;
- permitir que a infraestrutura futura injete as credenciais reais sem precisar editar JSON manualmente a cada ambiente.

## O que foi alterado

Os principais arquivos modificados nesta etapa foram:

- `/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/autorizacao/realms/desenvolvimento-realm.json`
- `/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/autorizacao/realms/homologacao-realm.json`
- `/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/autorizacao/realms/producao-realm.json`
- `/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/autorizacao/realms/render-realms.sh`
- `/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/infraestrutura/dev/docker-compose.yml`
- `/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/infraestrutura/hml/docker-compose.yml`
- `/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/infraestrutura/dev/.env`
- `/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/infraestrutura/hml/.env`
- `/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/README.md`
- `/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/infraestrutura/prod/README.md`

## Alteração 1: inclusão dos brokers nos realm exports

Foi adicionada a seção `identityProviders` em cada um dos três realm exports.

Isso foi feito em:

- `desenvolvimento-realm.json`
- `homologacao-realm.json`
- `producao-realm.json`

Cada realm agora carrega os seis provedores abaixo:

- `google`
- `apple`
- `facebook`
- `linkedin`
- `instagram`
- `x`

Esses aliases foram escolhidos para casar com a modelagem já discutida para o app e para o backend de vínculos sociais.

Para o caso do X, a regra final do ecossistema ficou assim:

- alias funcional usado por app, cliente e servidor de identidade: `x`;
- detalhe técnico interno do broker do Keycloak: `providerId = twitter`.

### Provider IDs usados

Os `providerId` configurados ficaram assim:

- `google` para Google
- `oidc` para Apple
- `facebook` para Facebook
- `linkedin-openid-connect` para LinkedIn
- `instagram` para Instagram
- `twitter` para o broker técnico do X

Esses `providerId` não foram escolhidos por suposição.
Eles foram confirmados a partir da própria distribuição do Keycloak 26.5.5 e testados no Admin API local.

Isso não muda o alias funcional do ecossistema:

- `alias = x`
- `providerId = twitter`

Ou seja, `twitter` não deve aparecer como alias público do app, do cliente compartilhado ou da API de identidade.

## Alteração complementar: descoberta de provedores sociais em runtime

Além do versionamento dos realm exports, o servidor de autorização passou a expor um endpoint público para o app descobrir quais brokers sociais estão realmente disponíveis no ambiente:

```text
/realms/<realm>/eickrono-runtime/provedores-sociais
```

Esse endpoint:

- lê os identity providers do realm em execução;
- normaliza a resposta para os aliases canônicos do ecossistema:
  - `google`
  - `apple`
  - `facebook`
  - `linkedin`
  - `instagram`
  - `x`
- só marca um provedor como habilitado quando ele está:
  - presente no realm;
  - com `enabled = true`;
  - sem `hideOnLogin`;
  - e com credenciais reais, não placeholders.

Com isso, o app pode mostrar no login social apenas o que realmente funciona no Keycloak do ambiente atual.

### Por que Apple usa `oidc`

Apple não aparece no Keycloak 26.5.5 como um broker social pronto no mesmo formato de Google e Facebook.

Por isso a configuração correta é:

- alias do provedor: `apple`
- `providerId`: `oidc`
- endpoints configurados manualmente
- `clientSecret` em formato JWT assinado, como a Apple exige

Na prática, Apple entra como um broker OIDC genérico especializado, não como um provider social embutido com `providerId=apple`.

### Por que LinkedIn usa `linkedin-openid-connect`

O LinkedIn suportado no Keycloak atual não é o provider antigo de OAuth puro.
No 26.5.5 o broker relevante é o OpenID Connect do LinkedIn.

Por isso o `providerId` correto ficou:

```text
linkedin-openid-connect
```

### Por que Instagram ficou configurado mesmo sendo especial

Instagram foi mantido porque a arquitetura aprovada precisa nascer cobrindo os cinco provedores.

Mas ele tem uma ressalva importante no Keycloak 26.5.5:

- o broker de Instagram está marcado como deprecated para remoção;
- ele não sobe por padrão;
- o servidor precisa iniciar com `--features=instagram-broker`.

Sem essa flag, o Admin API rejeita o provider com erro de `Invalid identity provider id [instagram]`.

## Alteração 2: placeholders de credenciais nos realms

Na convenção documental atual, esses placeholders passam a seguir o padrão
`KEYCLOAK_IDP_<APP>_<PROVEDOR>_<CREDENCIAL>`, usando `THIMISU` como slug de
app nos exemplos deste documento.

Os realm exports não receberam segredo real.
Em vez disso, cada broker foi versionado com placeholders como:

```text
${KEYCLOAK_IDP_THIMISU_GOOGLE_CLIENT_ID}
${KEYCLOAK_IDP_THIMISU_GOOGLE_CLIENT_SECRET}
${KEYCLOAK_IDP_THIMISU_APPLE_CLIENT_ID}
${KEYCLOAK_IDP_THIMISU_APPLE_CLIENT_SECRET_JWT}
```

Isso foi feito para resolver três problemas ao mesmo tempo:

- evitar vazar segredo no repositório;
- permitir ambientes diferentes com credenciais diferentes;
- manter o realm export versionado e legível.

As variáveis esperadas ficaram assim:

- `KEYCLOAK_IDP_THIMISU_GOOGLE_CLIENT_ID`
- `KEYCLOAK_IDP_THIMISU_GOOGLE_CLIENT_SECRET`
- `KEYCLOAK_IDP_THIMISU_APPLE_CLIENT_ID`
- `KEYCLOAK_IDP_THIMISU_APPLE_CLIENT_SECRET_JWT`
- `KEYCLOAK_IDP_THIMISU_FACEBOOK_APP_ID`
- `KEYCLOAK_IDP_THIMISU_FACEBOOK_APP_SECRET`
- `KEYCLOAK_IDP_THIMISU_LINKEDIN_CLIENT_ID`
- `KEYCLOAK_IDP_THIMISU_LINKEDIN_CLIENT_SECRET`
- `KEYCLOAK_IDP_THIMISU_INSTAGRAM_APP_ID`
- `KEYCLOAK_IDP_THIMISU_INSTAGRAM_APP_SECRET`
- `KEYCLOAK_IDP_THIMISU_X_CLIENT_ID`
- `KEYCLOAK_IDP_THIMISU_X_CLIENT_SECRET`

## Alteração 3: criação do wrapper `render-realms.sh`

A parte mais sensível desta implementação foi a materialização dos placeholders.

Não bastava colocar `${KEYCLOAK_IDP_<APP>_*}` no JSON e esperar que o Keycloak resolvesse isso automaticamente com segurança.
Além disso, existia um risco importante:

- os realm exports já contêm placeholders que pertencem ao próprio Keycloak, como `${username}`, `${email}`, `${firstName}` e `${lastName}`;
- uma substituição genérica de variáveis de ambiente poderia destruir esses placeholders internos e corromper o realm.

Por isso foi criado:

- `/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/autorizacao/realms/render-realms.sh`

### O que esse script faz

Antes de o Keycloak subir, o script:

1. lê os realm exports originais em `/opt/keycloak/import-source`;
2. copia os arquivos para `/opt/keycloak/data/import`;
3. substitui somente os placeholders `KEYCLOAK_IDP_<APP>_*`;
4. preserva qualquer outro placeholder do JSON;
5. executa o `kc.sh ... --import-realm`.

### Por que a substituição é seletiva

A substituição seletiva foi uma decisão deliberada.

Se fosse usado um processo amplo como:

- `envsubst` em todo o arquivo;
- replace global de qualquer `${VAR}`;
- pré-processamento indiscriminado dos realm exports;

o sistema poderia trocar coisas como:

```text
${username}
${email}
${firstName}
${lastName}
```

Esses placeholders fazem parte de blocos do próprio realm export e não devem virar variáveis de sistema operacional.

Logo, o script foi desenhado para substituir só os nomes conhecidos de provedores sociais.

### Comportamento de falha

O script também falha se uma variável obrigatória de provedor social estiver ausente.

Isso foi feito para evitar um boot enganoso do Keycloak com import parcialmente inválido.

Ou seja:

- se faltar uma variável crítica de broker social;
- o startup deve falhar cedo;
- em vez de subir um Keycloak aparentemente saudável, mas com configuração social quebrada.

## Alteração 4: ajuste do Docker Compose de `dev` e `hml`

Os `docker-compose.yml` de desenvolvimento e homologação foram adaptados para usar o wrapper.

Antes:

- o diretório `realms` era montado diretamente em `/opt/keycloak/data/import`;
- o container iniciava o `kc.sh start-dev --import-realm` ou `kc.sh start --import-realm`.

Agora:

- o diretório `realms` é montado em `/opt/keycloak/import-source`;
- o `entrypoint` do container passa a ser `sh /opt/keycloak/import-source/render-realms.sh`;
- o `command` volta a carregar apenas os argumentos do `kc.sh`;
- o script renderiza os arquivos para `/opt/keycloak/data/import`;
- depois o próprio script executa o `kc.sh` com `--import-realm`.

Essa mudança aconteceu em:

- `/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/infraestrutura/dev/docker-compose.yml`
- `/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/infraestrutura/hml/docker-compose.yml`

## Alteração 5: ativação da feature de Instagram em `dev` e `hml`

Como Instagram não é aceito por padrão no Keycloak 26.5.5, foi adicionada a variável:

```text
KEYCLOAK_FEATURES=instagram-broker
```

nos `.env` locais de:

- `infraestrutura/dev/.env`
- `infraestrutura/hml/.env`

e os `docker-compose.yml` passaram a iniciar o Keycloak com:

```text
--features=${KEYCLOAK_FEATURES:-instagram-broker}
```

Isso garante que:

- em `dev` e `hml`, o broker `instagram` possa ser importado;
- o mesmo padrão possa ser transportado para produção pela infraestrutura futura.

## Alteração 6: documentação operacional

Também foi atualizada a documentação do módulo e da infraestrutura para registrar o comportamento novo.

### No README do módulo do servidor de autorização

Foi documentado:

- quais brokers estão versionados;
- quais aliases e `providerId` são usados;
- que Apple usa `oidc`;
- que Instagram exige feature flag;
- que os placeholders dos brokers são materializados pelo `render-realms.sh`.

Arquivo:

- `/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/README.md`

### No README da pasta de produção

Foi acrescentado que a infraestrutura de produção ainda precisa:

- materializar as credenciais reais `KEYCLOAK_IDP_<APP>_*`;
- subir o Keycloak com `instagram-broker`, se o provedor continuar habilitado em produção.

Arquivo:

- `/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/infraestrutura/prod/README.md`

## Como cada provedor ficou configurado

## Google

Google ficou como broker social nativo do Keycloak:

- alias: `google`
- `providerId`: `google`
- escopo default: `openid profile email`

Esse é o caminho mais direto e compatível com o Keycloak atual.

## Apple

Apple ficou como broker OIDC genérico:

- alias: `apple`
- `providerId`: `oidc`
- `issuer`: `https://appleid.apple.com`
- `authorizationUrl`: `https://appleid.apple.com/auth/authorize?response_mode=form_post`
- `tokenUrl`: `https://appleid.apple.com/auth/token`
- `jwksUrl`: `https://appleid.apple.com/auth/keys`
- `disableUserInfo=true`
- `useJwksUrl=true`
- `validateSignature=true`
- `clientAuthMethod=client_secret_post`

O `clientSecret` da Apple aqui não é um segredo simples de painel como em outros provedores.
Ele precisa ser um JWT assinado conforme as regras da Apple.

Observacao importante:

- como o broker Apple atual pede `scope=openid email name`, a Apple exige `response_mode=form_post` no authorize;
- no Keycloak deste projeto, a forma operacional adotada e persistir esse parametro diretamente no `authorizationUrl` do broker `apple`;
- sem isso, a Apple responde com `invalid_request` informando que `response_mode must be form_post when name or email scope is requested`.

## Facebook

Facebook ficou como broker social nativo:

- alias: `facebook`
- `providerId`: `facebook`
- escopo default: `email`

Sem necessidade de configuração extensa no export além de `clientId`, `clientSecret` e do escopo default explicitado no realm para evitar ambiguidade entre ambientes.

## LinkedIn

LinkedIn ficou como broker OIDC específico do Keycloak:

- alias: `linkedin`
- `providerId`: `linkedin-openid-connect`
- escopo default: `openid profile email`

## Instagram

Instagram ficou assim:

- alias: `instagram`
- `providerId`: `instagram`
- escopo default: `user_profile`

Mas depende de:

- `--features=instagram-broker`

Sem isso, o Keycloak rejeita o provider.

Ponto importante: o broker `instagram` do Keycloak 26.5.5 continua baseado no fluxo legado do próprio Keycloak e está deprecated para remoção. A documentação oficial do Keycloak manda preferir o broker `facebook` quando a necessidade real é integração com o ecossistema atual da Meta.

Na prática, isso significa que este alias `instagram` ainda serve apenas para autenticação/vinculação social no desenho atual. Ele não implementa, por si só:

- `Instagram Business Login`;
- publicação de conteúdo via Graph API;
- mensageria via Instagram API;
- escopos de negócio como `instagram_business_*`, `pages_show_list`, `pages_read_engagement` ou `business_management`.

## O que foi validado

Esta etapa não foi deixada apenas “no papel”.
Foram feitas validações práticas.

### 1. Validação dos `providerId` e do formato de configuração

Foi inspecionada a própria distribuição do Keycloak 26.5.5 para confirmar:

- quais factories existem;
- quais `providerId` cada uma usa;
- quais configurações fazem sentido para cada broker.

Isso foi importante para não escrever JSON com `providerId` incorreto.

### 2. Validação no Admin API do Keycloak local

Foi testado o cadastro de brokers diretamente no Admin API do Keycloak local em execução.

Resultado:

- Google foi aceito;
- Apple foi aceito;
- Facebook foi aceito;
- LinkedIn foi aceito;
- Instagram foi rejeitado sem feature flag.

Esse resultado confirmou duas coisas:

- os quatro primeiros brokers estavam modelados corretamente;
- Instagram realmente exige ativação de feature.

### 3. Validação de sintaxe dos realm exports

Os três JSONs foram validados com `jq`.

Isso confirmou que:

- a estrutura final continuou parseável;
- a nova seção `identityProviders` não quebrou os exports.

### 4. Validação do compose

Foi usado `docker compose config` em `dev` e `hml` para confirmar:

- que o wrapper `render-realms.sh` está configurado como `entrypoint` do serviço;
- que o mount foi alterado para `import-source`;
- que a feature `instagram-broker` aparece na expansão final.

### 5. Validação da renderização seletiva

O `render-realms.sh` foi executado de forma controlada com os `.env` locais para confirmar:

- que placeholders `KEYCLOAK_IDP_<APP>_*` são substituídos;
- que placeholders do realm como `${username}` permanecem literais;
- que o script é sintaticamente válido em `sh`.

## O que ainda não foi possível concluir

Apesar de a estrutura estar pronta, os brokers ainda não estão funcionalmente ativos com credenciais verdadeiras.

O motivo é simples:

- não existem no workspace;
- não existem no ambiente do shell;
- não existem no container atual;

as credenciais reais dos provedores externos.

Ou seja, não foi possível preencher de verdade:

- Google
- Apple
- Facebook
- LinkedIn
- Instagram

com seus `client_id` e `client_secret` reais.

## Por que o Keycloak atual não foi reiniciado

O ambiente em execução não foi reiniciado nesta etapa porque isso causaria um import com valores placeholder como:

- `trocar-google-client-id`
- `trocar-google-client-secret`
- `trocar-apple-client-secret-jwt`

Isso deixaria o Keycloak “configurado”, mas não realmente funcional para login social.

Então a decisão prudente foi:

- preparar toda a estrutura;
- validar a automação;
- não reiniciar a instância existente sem credencial real.

## O que falta para ficar funcional de verdade

Para terminar esta etapa operacionalmente, ainda faltam as credenciais reais de cada provedor:

- `KEYCLOAK_IDP_THIMISU_GOOGLE_CLIENT_ID`
- `KEYCLOAK_IDP_THIMISU_GOOGLE_CLIENT_SECRET`
- `KEYCLOAK_IDP_THIMISU_APPLE_CLIENT_ID`
- `KEYCLOAK_IDP_THIMISU_APPLE_CLIENT_SECRET_JWT`
- `KEYCLOAK_IDP_THIMISU_FACEBOOK_APP_ID`
- `KEYCLOAK_IDP_THIMISU_FACEBOOK_APP_SECRET`
- `KEYCLOAK_IDP_THIMISU_LINKEDIN_CLIENT_ID`
- `KEYCLOAK_IDP_THIMISU_LINKEDIN_CLIENT_SECRET`
- `KEYCLOAK_IDP_THIMISU_INSTAGRAM_APP_ID`
- `KEYCLOAK_IDP_THIMISU_INSTAGRAM_APP_SECRET`

Depois disso, o passo operacional é:

1. preencher os `.env` do ambiente;
2. reiniciar o serviço `eickrono-keycloak`;
3. deixar o `render-realms.sh` materializar os secrets no import;
4. confirmar no Admin Console do Keycloak que os brokers subiram corretamente;
5. testar `kc_idp_hint` e `idp_link` com o app.

## Resumo final

O que foi concluído nesta etapa:

- os cinco brokers foram versionados nos três realms;
- Apple foi configurado corretamente como `oidc`;
- LinkedIn foi configurado corretamente como `linkedin-openid-connect`;
- Instagram foi mantido com suporte condicionado à feature flag;
- a infraestrutura de `dev` e `hml` foi preparada para materializar credenciais em tempo de startup;
- a substituição foi feita de forma seletiva para não corromper placeholders internos do realm;
- a documentação operacional foi atualizada;
- a estrutura ficou pronta para receber credenciais reais sem novo retrabalho estrutural.

O que continua pendente:

- inserir credenciais reais dos provedores;
- reiniciar/importar o Keycloak com essas credenciais;
- testar o fluxo real de autenticação e vínculo social ponta a ponta.

## Material complementar criado depois desta etapa

Para destravar o preenchimento operacional das credenciais, foram adicionados dois artefatos complementares:

- `/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/documentacao/preenchimento-credenciais-provedores-sociais.md`
- `/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/autorizacao/realms/gerar-apple-client-secret-jwt.sh`

O primeiro explica o que cada `KEYCLOAK_IDP_<APP>_*` representa, quais callbacks o Keycloak espera por ambiente e por que esses valores não podem ser descobertos só pelo repositório.

O segundo automatiza a geração de `KEYCLOAK_IDP_THIMISU_APPLE_CLIENT_SECRET_JWT`, que é o campo mais trabalhoso porque a Apple exige um JWT assinado em vez de um segredo estático simples.
