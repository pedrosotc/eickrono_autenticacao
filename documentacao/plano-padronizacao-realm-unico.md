# Plano de Padronizacao para Realm Unico

Este documento registra a padronizacao do nome do realm do servidor de autorizacao para um valor unico em todos os ambientes.

## Objetivo

Usar o realm unico:

- `eickrono`

em `dev`, `stg` e `prod`.

## Motivacao

Hoje o ambiente muda em dois eixos ao mesmo tempo:

- no host;
- no nome do realm.

Exemplos correntes:

- `http://localhost:8080/realms/eickrono/...`
- `http://localhost:18080/realms/eickrono/...`
- `https://oidc-stg.eickrono.store/realms/eickrono/...`
- `https://oidc.eickrono.com/realms/eickrono/...`

O alvo desejado e simplificar o desenho para que o host separe o ambiente e o realm permaneça estavel:

- `https://oidc-dev.eickrono.com/realms/eickrono/...`
- `https://oidc-stg.eickrono.com/realms/eickrono/...`
- `https://oidc.eickrono.com/realms/eickrono/...`

Observacao importante:

- no `stg` ativo hoje na AWS, o host publico aplicado continua `https://oidc-stg.eickrono.store/realms/eickrono/...`;
- `https://oidc-stg.eickrono.com/realms/eickrono/...` permanece como alvo futuro de consolidacao.

## O que e `issuer`

`issuer` nao e um termo inventado localmente.
Ele vem do OpenID Connect.

Na pratica, o `issuer` e a URL canonica da autoridade que emite os tokens.

Ele aparece em dois pontos centrais:

- no documento de descoberta OIDC, normalmente em `/.well-known/openid-configuration`;
- na claim `iss` dentro dos tokens emitidos.

O cliente OIDC e os resource servers usam esse valor para:

- descobrir endpoints oficiais do servidor de autorizacao;
- validar se o token veio da autoridade esperada;
- ancorar a configuracao de login, token, logout e validacao de assinatura.

Exemplo conceitual:

- `issuer`: `https://oidc-dev.eickrono.com/realms/eickrono`

Se o token trouxer outro `iss`, a validacao tende a falhar.

## Estado aplicado

O runtime ja foi padronizado para o realm unico `eickrono`.

Os exports continuam separados por ambiente apenas no nome do arquivo:

- `../autorizacao/realms/desenvolvimento-realm.json`
- `../autorizacao/realms/staging-realm.json`
- `../autorizacao/realms/producao-realm.json`

Cada ambiente importa apenas o seu arquivo de export, mas todos com o mesmo nome logico de realm.

## Estado alvo

### Hosts

- superficie do produto Thimisu:
  - `thimisu-dev.eickrono.com`
  - `thimisu-stg.eickrono.com`
  - `thimisu.eickrono.com`
- backend de dominio do Thimisu:
  - `thimisu-backend-dev.eickrono.com`
  - `thimisu-backend-stg.eickrono.com`
  - `thimisu-backend.eickrono.com`
- API identidade:
  - `id-dev.eickrono.com`
  - `id-stg.eickrono.com`
  - `id.eickrono.com`
- servidor OIDC:
  - `oidc-dev.eickrono.com`
  - `oidc-stg.eickrono.com`
  - `oidc.eickrono.com`

### Implantacao transitoria segura

Enquanto o dominio principal ainda nao estiver pronto para receber `dev` e `stg`, a implantacao inicial pode usar dominios auxiliares ja disponiveis:

- `dev`:
  - `id-dev.eickrono.online`
  - `oidc-dev.eickrono.online`
- `stg`:
  - `auth-stg.eickrono.store`
  - `oidc-stg.eickrono.store`

Para o backend de dominio do Thimisu, a convencao canonica ja esta aprovada,
mas a publicacao DNS transitoria ainda pode ficar em aberto por ambiente.
Enquanto isso, o app usa `CONFIG_THIMISU_BASE_URL` para apontar o host correto
sem cristalizar um dominio errado nos assets.

Esses hosts transitorios existem para reduzir risco operacional durante Cloudflare Tunnel, DNS e configuracao inicial dos brokers.
Eles nao mudam o alvo canônico final, que continua concentrado em `eickrono.com`.

### Realms

- `dev`: `eickrono`
- `stg`: `eickrono`
- `prod`: `eickrono`

### Issuers alvo

- `https://oidc-dev.eickrono.com/realms/eickrono`
- `https://oidc-stg.eickrono.com/realms/eickrono`
- `https://oidc.eickrono.com/realms/eickrono`

Observacao:

- `issuer` OIDC nao deve reutilizar o host da API de identidade;
- no desenho atual, `prod` usa `https://oidc.eickrono.com/realms/eickrono` como autoridade OIDC canonica;
- no `stg` ativo hoje, o `issuer` efetivo e `https://oidc-stg.eickrono.store/realms/eickrono`;
- a API de identidade pode continuar exposta em outro host sem alterar o `iss` dos tokens.

### Redirect URIs alvo dos brokers

- `https://oidc-dev.eickrono.com/realms/eickrono/broker/google/endpoint`
- `https://oidc-stg.eickrono.com/realms/eickrono/broker/google/endpoint`
- `https://oidc.eickrono.com/realms/eickrono/broker/google/endpoint`

O mesmo padrao vale para `apple`, `facebook`, `linkedin` e `instagram`.

Enquanto o `stg` ainda estiver em `eickrono.store`, os callbacks publicos reais que precisam existir nos consoles externos sao:

- `https://oidc-stg.eickrono.store/realms/eickrono/broker/google/endpoint`
- `https://oidc-stg.eickrono.store/realms/eickrono/broker/apple/endpoint`
- `https://oidc-stg.eickrono.store/realms/eickrono/broker/facebook/endpoint`
- `https://oidc-stg.eickrono.store/realms/eickrono/broker/linkedin/endpoint`

## Impacto tecnico

### 1. Realm exports

Precisam ser padronizados os nomes internos dos realms exportados.

Arquivos impactados:

- `../autorizacao/realms/desenvolvimento-realm.json`
- `../autorizacao/realms/staging-realm.json`
- `../autorizacao/realms/producao-realm.json`

Pontos de atencao:

- `realm`
- `id`
- referencias internas a issuer/base URL
- eventuais clientes, links e callbacks com host/path antigos

### 2. Configuracoes do app

Arquivos impactados:

- `eickrono-thimisu/eickrono-thimisu-app/assets/config/app_config.dev.json`
- `eickrono-thimisu/eickrono-thimisu-app/assets/config/app_config.stg.json`
- `eickrono-thimisu/eickrono-thimisu-app/assets/config/app_config.prod.json`

Pontos de atencao:

- `auth.oidc.issuer`
- `servicos.autenticacao.baseUrl`
- `servicos.thimisu.baseUrl`
- qualquer referencia antiga a host/realm divergente

### 3. APIs Spring Boot

Pontos que precisam ser revisados:

- `application-*.yml`
- `OIDC_ISSUER_URI`
- `OIDC_JWKS_URI`
- clientes internos que dependem de `realm`
- filtros/resource servers que fazem validacao por issuer

### 4. Google OAuth e demais brokers

Todos os provedores sociais configurados no Keycloak passam a exigir callbacks com o novo realm unico:

- `.../autorizacao/realms/eickrono/broker/<alias>/endpoint`

### 5. Scripts e documentacao operacional

Precisam ser atualizados:

- guias de JWT
- guias de debug
- docs de Cloudflare Tunnel
- docs de credenciais de provedores sociais
- exemplos de curl

## Ordem recomendada

1. fechar o hostname por ambiente;
2. padronizar o realm alvo para `eickrono`;
3. ajustar os realm exports;
4. ajustar `.env`, docker compose e configs Spring;
5. ajustar configs do app;
6. atualizar callbacks dos brokers externos;
7. recriar ou reimportar os realms;
8. validar discovery OIDC, login por senha e login social.

## Risco principal

O risco principal e confundir:

- mudanca de host;
- mudanca de realm;
- mudanca de callbacks externos.

Por isso esta padronizacao nao deve ser feita como ajuste isolado de string.
Ela precisa ser tratada como mudanca coordenada de contrato OIDC.

## Status

Status atual desta tarefa:

- aprovada conceitualmente;
- primeiro corte de runtime ja aplicado:
  - `api-identidade-eickrono` e `api-contas-eickrono` em `stg` agora usam `https://oidc-stg.eickrono.store/realms/eickrono`;
  - o `thimisu-backend` em `stg` e `prd` agora usa `realm=eickrono` com `issuer` OIDC separado do host da API;
  - o app em `prod` ja usa `servicos.autenticacao.baseUrl` como chave
    canonica;
- o app agora aceita sobrescritas independentes de bootstrap para `CONFIG_AUTENTICACAO_BASE_URL`, `CONFIG_THIMISU_BASE_URL` e `CONFIG_OIDC_ISSUER`;
- a convencao canonica do backend publico do dominio ficou aprovada como `thimisu-backend-*`;
- a leitura do que ja pode migrar e do que ainda continua alias legado ficou consolidada em `matriz_migracao_autenticacao_identidade_thimisu_backend.md`;
- o app continua apontando para endpoints publicados no host do desenvolvedor ate o DNS/runtime de `stg` desse backend ser publicado no ambiente;
- documentada como alvo arquitetural.
