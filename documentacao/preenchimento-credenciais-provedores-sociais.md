# Guia Operacional para Obter e Preencher as Credenciais dos Provedores Sociais

Este documento explica, de ponta a ponta, o que são as variáveis `KEYCLOAK_IDP_<APP>_*`, por que elas são necessárias, onde obtê-las nos portais de cada provedor, quais menus clicar, quais URLs de callback registrar no Keycloak e qual é o impacto de custo em cada caso.

O foco aqui é operacional.
O código do projeto já foi preparado para:

- carregar os brokers sociais nos realms do Keycloak;
- iniciar `kc_idp_hint` para login social;
- iniciar `kc_action=idp_link:<alias>` para vínculo social;
- sincronizar os vínculos reais via `eickrono-autenticacao-servidor`.

O que ainda depende de ação manual externa é obter as credenciais reais dos provedores.

## Convenção de nomes adotada

Daqui em diante, a documentação usa o padrão:

```text
KEYCLOAK_IDP_<APP>_<PROVEDOR>_<CREDENCIAL>
```

Neste guia, o slug de app usado nos exemplos é:

- `THIMISU`

Regras práticas:

- `FACEBOOK` e `INSTAGRAM` usam `APP_ID` e `APP_SECRET`, porque essa é a terminologia mais próxima dos portais da Meta;
- `GOOGLE`, `APPLE` e `LINKEDIN` continuam com `CLIENT_ID` e `CLIENT_SECRET`, porque essa é a terminologia mais comum nesses provedores;
- o objetivo dessa convenção é deixar claro que a credencial pertence a um app específico, mesmo quando o consumo técnico acontece no broker do Keycloak.

## O que falta preencher

As variáveis abaixo ainda estão com placeholder nos arquivos de ambiente:

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

Hoje elas ficam principalmente em:

- `/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/infraestrutura/dev/.env`
- `/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/infraestrutura/stg/.env`

## O que cada variável representa

| Variável | Origem | O que é |
| --- | --- | --- |
| `KEYCLOAK_IDP_THIMISU_GOOGLE_CLIENT_ID` | Google Cloud Console | OAuth Client ID do app web usado pelo broker Google |
| `KEYCLOAK_IDP_THIMISU_GOOGLE_CLIENT_SECRET` | Google Cloud Console | Client secret desse mesmo OAuth Client |
| `KEYCLOAK_IDP_THIMISU_APPLE_CLIENT_ID` | Apple Developer | Identificador do Services ID usado no Sign in with Apple |
| `KEYCLOAK_IDP_THIMISU_APPLE_CLIENT_SECRET_JWT` | Apple Developer + chave `.p8` | JWT assinado exigido pela Apple no lugar de um secret estático |
| `KEYCLOAK_IDP_THIMISU_FACEBOOK_APP_ID` | Meta for Developers | App ID do app Meta usado para Facebook Login |
| `KEYCLOAK_IDP_THIMISU_FACEBOOK_APP_SECRET` | Meta for Developers | App Secret desse app |
| `KEYCLOAK_IDP_THIMISU_LINKEDIN_CLIENT_ID` | LinkedIn Developer Portal | Client ID do app LinkedIn |
| `KEYCLOAK_IDP_THIMISU_LINKEDIN_CLIENT_SECRET` | LinkedIn Developer Portal | Client Secret do app LinkedIn |
| `KEYCLOAK_IDP_THIMISU_INSTAGRAM_APP_ID` | Meta for Developers | Identificador OAuth do app do Instagram usado pelo broker |
| `KEYCLOAK_IDP_THIMISU_INSTAGRAM_APP_SECRET` | Meta for Developers | Secret desse app |
| `KEYCLOAK_IDP_THIMISU_X_CLIENT_ID` | X Developer Portal | API Key / Client ID do app X usado pelo broker |
| `KEYCLOAK_IDP_THIMISU_X_CLIENT_SECRET` | X Developer Portal | API Key Secret / Client Secret do app X |

## O que e obrigatorio no desenho atual

O app Flutter atual nao faz login social via SDK nativo do provedor.
Ele abre o broker social do servidor de autorizacao e delega o fluxo ao Keycloak.

Em outras palavras:

- login social usa `kc_idp_hint=<alias>`;
- vinculacao social usa `kc_action=idp_link:<alias>`;
- a credencial realmente obrigatoria hoje e a do broker configurado no Keycloak.

Isso ja esta alinhado com o fluxo documentado no app:

- `/Users/thiago/Desenvolvedor/flutter/eickrono-thimisu/eickrono-thimisu-app/docs/guia_acoes.md`

### Matriz rapida

| Provedor | Credencial no Keycloak e obrigatoria hoje? | Credencial nativa Android/iOS e obrigatoria hoje? | Observacao |
| --- | --- | --- | --- |
| Google | Sim | Nao | O login atual passa pelo broker web do Keycloak. Credenciais Android/iOS so entram se o app passar a usar Google Sign-In nativo. |
| Apple | Sim | Nao | O login social atual usa broker OIDC do Keycloak com `Services ID` e JWT. Isso nao se confunde com App Attest, que e seguranca de dispositivo. |
| Facebook | Sim | Nao | O app atual nao usa SDK nativo do Facebook Login. O broker `facebook` do Keycloak usa callback web e escopo default `email`. |
| LinkedIn | Sim | Nao | O fluxo atual depende do broker `linkedin-openid-connect` no Keycloak. |
| Instagram | Sim | Nao | O alias `instagram` atual depende do broker deprecated `instagram-broker`, usa `user_profile` e nao equivale ao Meta Business Login / Graph API. |
| X | Sim | Nao | O alias funcional do ecossistema e `x`. Internamente, o Keycloak usa o broker tecnico `twitter`, e o fluxo depende das credenciais do portal do X. |

### Resposta curta para evitar confusao

- para todas as redes sociais, o Keycloak precisa de uma credencial propria do provedor;
- no desenho atual do app, isso e obrigatorio para `Google`, `Apple`, `Facebook`, `LinkedIn` e `Instagram`;
- credencial nativa de app mobile so e necessaria se o login deixar de ser brokerado e passar a usar o SDK do provedor diretamente no Flutter.
- Meta Business Login, publicacao e mensageria do Instagram ainda nao estao implementados nesse fluxo social brokerado.

### Sobre os arquivos locais de Google Android e iOS

As referencias locais de Google Android e iOS documentadas neste arquivo existem como material operacional do workspace.
Elas nao desbloqueiam o broker do Keycloak por si so.

Entao, no caso do Google, podem coexistir tres credenciais diferentes:

1. credencial web do broker Google no Keycloak;
2. credencial Android nativa;
3. credencial iOS nativa.

Hoje, para o login social brokerado que o app realmente executa, a obrigatoria e a `credencial web do broker`.

No caso do X, a regra final e:

- alias funcional usado por app, cliente e API: `x`;
- `twitter` aparece apenas como detalhe tecnico do `providerId` dentro do Keycloak.

## Convencao de dominios dos ambientes

Para reduzir ambiguidade nos proximos passos operacionais, a convencao documentada neste repositório passa a ser:

### Padrao canonico final

- `dev`:
  - API de identidade: `id-dev.eickrono.com`
  - servidor OIDC: `oidc-dev.eickrono.com`
- `stg`:
  - API de identidade: `id-stg.eickrono.com`
  - servidor OIDC: `oidc-stg.eickrono.com`
- `prod`:
  - API de identidade: `id.eickrono.com`
  - servidor OIDC: `oidc.eickrono.com`

### Padrao transitorio de implantacao

- `dev`:
  - API de identidade: `id-dev.eickrono.online`
  - servidor OIDC: `oidc-dev.eickrono.online`
- `stg`:
  - API pública de autenticação: `auth-stg.eickrono.store`
  - servidor OIDC: `oidc-stg.eickrono.store`

Os dominios auxiliares existem para permitir configuracao inicial de brokers e tunnels com menor risco no dominio principal.
Eles nao substituem o alvo canonico final em `eickrono.com`.

### Observacao sobre o nome curto

Os prefixos recomendados passam a ser:

- `id` para a API de identidade;
- `oidc` para o servidor de autorizacao.

Motivos:

- `id` resume bem `eickrono-thimisu-backend`;
- `oidc` descreve melhor a superficie publica do servidor de autorizacao do que `oauth`;
- `keycloak` ficaria preso a tecnologia interna.

### Sobre o path `/realms/<realm>`

Mesmo com um dominio por ambiente, o path do realm continua obrigatorio no desenho do Keycloak.

Exemplos corretos:

- `https://oidc-dev.eickrono.online/realms/eickrono/...`
- `https://oidc-stg.eickrono.store/realms/eickrono/...`

O dominio separa o ambiente na internet.
O realm separa a configuracao OIDC dentro do servidor de autorizacao.

No runtime atual, o realm padronizado ja e `eickrono` em `dev`, `stg` e `prod`.

## Callbacks que cada portal precisa aceitar

O padrão do Keycloak é:

```text
<base-publica-keycloak>/realms/<realm>/broker/<alias>/endpoint
```

Os aliases configurados no projeto são:

- `google`
- `apple`
- `facebook`
- `linkedin`
- `instagram`
- `x`

Para evitar ambiguidade:

- os callbacks e o `kc_idp_hint` do ecossistema usam `x`;
- o nome `twitter` nao deve ser usado como alias funcional fora do Keycloak.

### Desenvolvimento

Com o compose atual de `dev`, o Keycloak sobe localmente em:

```text
http://localhost:8080
```

Para testes locais no navegador do proprio Mac, as callbacks ficam:

- `http://localhost:8080/realms/eickrono/broker/google/endpoint`
- `http://localhost:8080/realms/eickrono/broker/apple/endpoint`
- `http://localhost:8080/realms/eickrono/broker/facebook/endpoint`
- `http://localhost:8080/realms/eickrono/broker/linkedin/endpoint`
- `http://localhost:8080/realms/eickrono/broker/instagram/endpoint`
- `http://localhost:8080/realms/eickrono/broker/x/endpoint`

Observacao importante para cadastro nos provedores:

- esses endpoints locais existem no Keycloak local e podem ser uteis para diagnostico no navegador do Mac;
- para `Google Web`, `localhost` pode ser cadastrado como callback adicional no client do broker;
- para `Apple Web`, esse `localhost` nao deve ser cadastrado no portal Apple; para Apple use apenas host publico HTTPS.

Para testes publicos com tunnel e iPhone fisico, a referencia operacional atual de `dev` passa a ser:

- `https://oidc-dev.eickrono.online/realms/eickrono/broker/google/endpoint`
- `https://oidc-dev.eickrono.online/realms/eickrono/broker/apple/endpoint`
- `https://oidc-dev.eickrono.online/realms/eickrono/broker/facebook/endpoint`
- `https://oidc-dev.eickrono.online/realms/eickrono/broker/linkedin/endpoint`
- `https://oidc-dev.eickrono.online/realms/eickrono/broker/instagram/endpoint`
- `https://oidc-dev.eickrono.online/realms/eickrono/broker/x/endpoint`

Observacao:

- o runtime atual de `dev` ja responde em `/realms/eickrono`;
- o mesmo padrao deve ser mantido em `stg` e `prod`.

### Staging

Com o compose atual de `stg`, o Keycloak é publicado no host em:

```text
http://localhost:18080
```

Para execução via Docker no computador do desenvolvedor, as callbacks ficam:

- `http://localhost:18080/realms/eickrono/broker/google/endpoint`
- `http://localhost:18080/realms/eickrono/broker/apple/endpoint`
- `http://localhost:18080/realms/eickrono/broker/facebook/endpoint`
- `http://localhost:18080/realms/eickrono/broker/linkedin/endpoint`
- `http://localhost:18080/realms/eickrono/broker/instagram/endpoint`

Observacao importante para cadastro nos provedores:

- esses endpoints locais existem no Keycloak local e podem ser uteis para diagnostico controlado;
- para `Google Web`, `localhost` pode continuar como callback adicional do client `Web application`;
- para `Apple Web`, `localhost` nao deve entrar em `Domains and Subdomains` nem em `Return URLs`; para Apple use apenas host publico HTTPS.

Para a convencao publica recomendada do ambiente `stg`, a referencia passa a ser:

- `https://oidc-stg.eickrono.store/realms/eickrono/broker/google/endpoint`
- `https://oidc-stg.eickrono.store/realms/eickrono/broker/apple/endpoint`
- `https://oidc-stg.eickrono.store/realms/eickrono/broker/facebook/endpoint`
- `https://oidc-stg.eickrono.store/realms/eickrono/broker/linkedin/endpoint`
- `https://oidc-stg.eickrono.store/realms/eickrono/broker/instagram/endpoint`

## Resumo operacional de `stg` para brokers sociais

Esta seção deixa explícito o conjunto mínimo de valores operacionais do `stg` no mesmo modelo já usado para Google e Apple.

### Google

- OAuth Web Client atual:
  - `<google-web-client-id>.apps.googleusercontent.com`
- Redirect URI pública de `stg`:
  - `https://oidc-stg.eickrono.store/realms/eickrono/broker/google/endpoint`
- Variáveis do Keycloak:
  - `KEYCLOAK_IDP_THIMISU_GOOGLE_CLIENT_ID`
  - `KEYCLOAK_IDP_THIMISU_GOOGLE_CLIENT_SECRET`

### Apple

- `Services ID` de `stg`:
  - `com.eickrono.thimisu.oidc.stg`
- `Team ID`:
  - `M863Q6N87G`
- `Key ID`:
  - `SD47UPB393`
- `Domain/Subdomain`:
  - `oidc-stg.eickrono.store`
- `Return URL`:
  - `https://oidc-stg.eickrono.store/realms/eickrono/broker/apple/endpoint`
- Variáveis do Keycloak:
  - `KEYCLOAK_IDP_THIMISU_APPLE_CLIENT_ID`
  - `KEYCLOAK_IDP_THIMISU_APPLE_CLIENT_SECRET_JWT`

### Facebook

- broker alias:
  - `facebook`
- `App Domains` em `stg`:
  - `oidc-stg.eickrono.store`
- `Valid OAuth Redirect URI` em `stg`:
  - `https://oidc-stg.eickrono.store/realms/eickrono/broker/facebook/endpoint`
- Variáveis do Keycloak:
  - `KEYCLOAK_IDP_THIMISU_FACEBOOK_APP_ID`
  - `KEYCLOAK_IDP_THIMISU_FACEBOOK_APP_SECRET`
- valor de desenvolvimento já catalogado para `App ID` de `stg`:
  - `1615481193012133`

Observações:

- o `App Secret` não deve ser repetido neste guia; ele deve continuar em material sigiloso e no secret store do ambiente;
- no `stg` atual da AWS, o broker `facebook` só deve ser religado depois que o secret materializado no runtime deixar de ser placeholder e o redirect público acima estiver confirmado na Meta.

### LinkedIn

- broker alias:
  - `linkedin`
- `Authorized redirect URL` em `stg`:
  - `https://oidc-stg.eickrono.store/realms/eickrono/broker/linkedin/endpoint`
- Variáveis do Keycloak:
  - `KEYCLOAK_IDP_THIMISU_LINKEDIN_CLIENT_ID`
  - `KEYCLOAK_IDP_THIMISU_LINKEDIN_CLIENT_SECRET`

Observações:

- até esta revisão, `client_id` e `client_secret` reais de `LinkedIn` ainda não estavam catalogados em `SEGREDOS_LOCAIS.md`;
- quando forem obtidos, devem seguir exatamente o mesmo modelo operacional já usado para Google, Apple e Facebook:
  - captura no portal externo;
  - armazenamento sigiloso local;
  - materialização no secret store do ambiente;
  - aplicação no broker ativo do Keycloak.

### Produção

Em produção, o formato esperado é:

```text
https://<dominio-publico-oidc>/realms/eickrono/broker/<alias>/endpoint
```

## Ordem recomendada de execução

Para evitar retrabalho, o melhor fluxo é:

1. decidir em qual ambiente vai testar primeiro;
2. abrir o portal do provedor;
3. criar o app ou reaproveitar um app existente;
4. registrar a redirect URI do Keycloak;
5. copiar o `client_id` e o `client_secret`;
6. preencher o `.env`;
7. no caso da Apple, gerar o JWT do secret;
8. reiniciar o Keycloak;
9. validar o broker no Admin Console;
10. testar login e vínculo pelo app.

## Preciso pagar?

Resumo direto:

- Apple: você já paga o item mais importante. Para este fluxo, em princípio não há outro custo Apple separado além da sua assinatura ativa do Apple Developer Program.
- Google: normalmente não há taxa separada para criar um OAuth Client para login. Algumas APIs Google podem pedir billing, mas isso não é o mesmo que pagar pelo login em si.
- Meta Facebook/Instagram: normalmente não há taxa separada para criar o app e obter App ID/App Secret. O que costuma existir é exigência de conta, uso case correto, conta profissional do Instagram e eventualmente revisão/verificação.
- LinkedIn: normalmente não há taxa separada para criar o app básico de login, mas é comum precisar associar o app a uma LinkedIn Page.

Observação importante:
nas linhas acima, Apple é o caso mais explícito em documentação de conta/programa. Google, Meta e LinkedIn tendem a tratar isso mais como requisito de conta/projeto/app do que como uma página formal de preço do login. Então, onde eu disser “normalmente não há taxa separada”, isso é uma inferência operacional a partir das documentações oficiais de criação/configuração, não uma tabela oficial de pricing do provedor.

## Google

Para o caso específico de `Google OAuth web + Keycloak local + iPhone físico`, leia também:

- `/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/documentacao/guia-cloudflare-tunnel-google-keycloak-dev.md`

### O que você vai obter

- `KEYCLOAK_IDP_THIMISU_GOOGLE_CLIENT_ID`
- `KEYCLOAK_IDP_THIMISU_GOOGLE_CLIENT_SECRET`

### Sites oficiais

- Console do Google Auth Platform: `https://console.developers.google.com/`
- Documentação OAuth para web apps: `https://developers.google.com/identity/protocols/oauth2/web-server`
- Ajuda do Google Auth Platform Clients: `https://support.google.com/cloud/answer/15549257`

### Passo a passo detalhado

1. Acesse `https://console.developers.google.com/`.
2. Entre com a conta Google que será dona do projeto.
3. Se o console pedir um projeto:
   - clique no seletor de projeto no topo;
   - clique em `New Project` ou `Create Project`;
   - crie o projeto.
4. Vá para a página de clients do Google Auth Platform:
   - URL direta: `https://console.developers.google.com/auth/clients`
   - a ajuda oficial chama essa tela de `Google Auth Platform Clients`.
5. Se o console disser que você ainda precisa registrar o app para usar Google Auth:
   - siga o fluxo de branding/audience;
   - preencha nome do app, email de suporte e demais dados básicos.
6. Clique em `Create Client`.
7. Em `Application type`, escolha `Web application`.
8. Dê um nome claro para o client, por exemplo:
   - `eickrono-keycloak-dev`
   - `eickrono-keycloak-stg`
9. Em `Authorized redirect URIs`, adicione a callback do Keycloak correspondente ao ambiente.
   Exemplo em `dev` local puro:

```text
http://localhost:8080/realms/eickrono/broker/google/endpoint
```

   Exemplo em `dev` com iPhone fisico e Cloudflare Tunnel:

```text
https://oidc-dev.eickrono.online/realms/eickrono/broker/google/endpoint
```

   Tabela resumida para copiar:

| Ambiente | Nome sugerido do client | Authorized redirect URIs |
| --- | --- | --- |
| `dev` | `eickrono-keycloak-dev` | `https://oidc-dev.eickrono.online/realms/eickrono/broker/google/endpoint` |
| `dev` | `eickrono-keycloak-dev` | `https://oidc-dev.eickrono.com/realms/eickrono/broker/google/endpoint` |
| `dev` | `eickrono-keycloak-dev` | `http://localhost:8080/realms/eickrono/broker/google/endpoint` |
| `stg` | `eickrono-keycloak-stg` | `https://oidc-stg.eickrono.store/realms/eickrono/broker/google/endpoint` |
| `stg` | `eickrono-keycloak-stg` | `https://oidc-stg.eickrono.com/realms/eickrono/broker/google/endpoint` |
| `stg` | `eickrono-keycloak-stg` | `http://localhost:18080/realms/eickrono/broker/google/endpoint` |
| `prod` | `eickrono-keycloak-prod` | `https://oidc.eickrono.com/realms/eickrono/broker/google/endpoint` |

   Observacoes:

   - em `dev` e `stg`, mantenha apenas as URIs que aquele ambiente realmente usa;
   - `localhost` pode entrar como callback adicional no `Google Web`;
   - em `prod`, a configuracao esperada hoje usa apenas o host canonico publico;
   - `Authorized JavaScript origins` podem ficar vazios neste fluxo brokerado, porque o ponto critico aqui e a callback web do Keycloak.

10. Clique em `Create`.
11. Copie:
   - `Client ID`
   - `Client Secret`

### Observações importantes do Google

- A documentação oficial informa que apps web devem registrar `authorized redirect URIs`.
- A documentação oficial também informa que o `client secret` só é exibido por completo no momento da criação.
- Se você perder o secret, o caminho correto é gerar um novo, não tentar “revelar” o antigo.
- Em alguns cenários, o Google pode pedir configuração de consent screen, audience e eventualmente verificação, dependendo dos escopos usados.
- Para login simples brokerizado pelo Keycloak, o fluxo costuma ser mais leve do que integrações com APIs sensíveis.
- Para `dev` e `stg`, o client `Web application` do Google pode acumular callbacks publicas e um `localhost` adicional, desde que cada URI autorizada seja exata.

### Credencial Google web atual do Keycloak em `dev`

No ambiente local atual, a credencial web usada pelo broker Google do Keycloak foi atualizada.

O JSON anterior foi removido antes da substituicao.
O arquivo atual foi copiado para um diretório local ignorado pelo Git:

```text
/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/.local-secrets/google/eickrono-oidc/web-dev/client_secret_<google-web-client-id>.json
```

O arquivo de origem usado nesta substituicao foi:

```text
/Users/thiago/Downloads/client_secret_<google-web-client-id>.json
```

Metadados uteis desse arquivo:

- tipo principal no JSON: `web`
- `project_id`: `eickrono-autenticacao-servidor`
- `client_id`: `<google-web-client-id>`
- `redirect_uri` principal: `https://oidc-dev.eickrono.online/realms/eickrono/broker/google/endpoint`
- `redirect_uri` local auxiliar: `http://localhost:8080/realms/eickrono/broker/google/endpoint`

### Como esse arquivo deve ser entendido

Esse JSON e a credencial OAuth web do Google usada pelo Keycloak no `dev`.

Ele corresponde diretamente a:

- `KEYCLOAK_IDP_THIMISU_GOOGLE_CLIENT_ID`
- `KEYCLOAK_IDP_THIMISU_GOOGLE_CLIENT_SECRET`

Entao, diferente dos arquivos Android e iOS locais:

- esse JSON representa a credencial efetivamente usada pelo broker Google do servidor OIDC;
- ele e parte operacional direta do login social atual do ecossistema em `dev`.

### Regra de seguranca para esse arquivo

- nao deve ser commitado;
- deve permanecer apenas em `.local-secrets/`;
- o `client_secret` nao deve ser repetido em documentacao versionada.

### SHA-1 do Android: quando entra e quando não entra

Esse ponto costuma gerar confusão, então vale separar bem:

- se você estiver usando o Google como broker web no Keycloak, o ponto central é a `Authorized redirect URI` do Keycloak;
- nesse desenho, o `client_id` principal do Google fica no app web criado para o Keycloak;
- o `SHA-1` normalmente entra quando você cria credencial Android nativa no ecossistema Google, por exemplo em cenários de Firebase Auth, Google Sign-In nativo ou APIs Android que validam assinatura do app.

Ou seja:

- para o broker Google do Keycloak, o item crítico é a callback web;
- para credencial Android nativa do seu app, o item crítico costuma ser `package name + SHA-1`.

### Comandos prontos para obter o SHA-1 local por ambiente Android

No app Android atual, o projeto fica em:

```text
/Users/thiago/Desenvolvedor/flutter/eickrono-thimisu/eickrono-thimisu-app/android
```

O padrão operacional local deixou de usar uma única `~/.android/debug.keystore`.
Agora cada flavor Android usa sua própria keystore local, referenciada em:

```text
/Users/thiago/Desenvolvedor/flutter/eickrono-thimisu/eickrono-thimisu-app/android/key.properties
```

Os arquivos locais atuais são:

```text
/Users/thiago/Desenvolvedor/flutter/eickrono-thimisu/eickrono-thimisu-app/android/.signing/dev-local.jks
/Users/thiago/Desenvolvedor/flutter/eickrono-thimisu/eickrono-thimisu-app/android/.signing/stg-signing.jks
/Users/thiago/Desenvolvedor/flutter/eickrono-thimisu/eickrono-thimisu-app/android/.signing/prd-local.jks
```

Os comandos de consulta passam a ser:

```bash
cd /Users/thiago/Desenvolvedor/flutter/eickrono-thimisu/eickrono-thimisu-app/android
keytool -list -v -alias thimisu-dev -keystore .signing/dev-local.jks -storepass <devStorePassword> -keypass <devKeyPassword>
keytool -list -v -alias thimisu-stg -keystore .signing/stg-signing.jks -storepass <stgStorePassword> -keypass <stgKeyPassword>
keytool -list -v -alias thimisu-prd -keystore .signing/prd-local.jks -storepass <prdStorePassword> -keypass <prdKeyPassword>
```

Os valores `<...Password>` devem ser lidos do `android/key.properties`, que fica fora do versionamento.

### Pacotes Android e SHA-1 locais atuais

Com a separação por flavor, a identidade Android local passa a ser esta:

- `dev`: package `com.eickrono.thimisu.dev`
- `dev`: `SHA-1` `3D:C7:CC:F9:93:5D:CF:CB:B2:15:D8:80:59:A7:23:43:DD:20:4D:A3`
- `stg`: package `com.eickrono.thimisu.stg`
- `stg`: `SHA-1` `3E:27:D2:98:8B:55:54:32:62:5C:66:65:53:D3:E9:9B:CA:98:94:78`
- `prd`: package `com.eickrono.thimisu`
- `prd`: `SHA-1` `E4:0B:10:42:67:87:45:A4:8D:DB:BE:21:23:CA:A3:08:0E:2A:3E:DA`

### Observações importantes sobre o novo padrão

- o Google identifica Android OAuth por `package name + SHA-1`;
- `dev`, `stg` e `prd` agora são três identidades Android locais diferentes;
- isso permite cadastrar três clientes OAuth Android separados, um por ambiente;
- o `android/key.properties` e o diretório `android/.signing/` devem continuar fora do versionamento;
- se uma dessas keystores for apagada e recriada, o `SHA-1` daquele ambiente muda.

### Como guardar os arquivos JSON locais do Google para Android

Depois de criar cada cliente Android no Google, salve o JSON baixado apenas em diretório local ignorado pelo Git, por exemplo:

```text
/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/.local-secrets/google/thimisu/android-dev/client_secret_<google-android-dev-client-id>.json
/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/.local-secrets/google/thimisu/android-stg/client_secret_<google-android-stg-client-id>.json
/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/.local-secrets/google/thimisu/android-prd/client_secret_<google-android-prd-client-id>.json
```

Esses arquivos não substituem as variáveis `KEYCLOAK_IDP_THIMISU_GOOGLE_*`.
Eles representam credenciais OAuth nativas do app Android, enquanto o Keycloak usa a credencial web do broker Google no servidor de autorização.

### Credencial Google local atual para iOS `dev/testes`

Também existe uma credencial OAuth local do Google para iOS `dev/testes`.

O `plist` anterior foi removido antes da substituição.
O arquivo atual foi copiado para um diretório local ignorado pelo Git:

```text
/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/.local-secrets/google/thimisu/ios-dev/client_<google-ios-client-id>.plist
```

O arquivo de origem usado nesta substituição foi:

```text
/Users/thiago/Downloads/client_<google-ios-client-id>.plist
```

### Metadados úteis do `plist`

Os dados úteis já identificados desse arquivo são:

- `CLIENT_ID`: `<google-ios-client-id>`
- `REVERSED_CLIENT_ID`: `<google-ios-reversed-client-id>`
- `BUNDLE_ID`: `com.eickrono.thimisu`
- `PLIST_VERSION`: `1`

Observacao:

- `com.eickrono.thimisu` continua sendo o bundle id tecnico atual do app;
- a renomeacao do workspace e do pacote Dart nao alterou ainda esse identificador nas plataformas moveis.

### Como esse arquivo deve ser entendido no iOS

Esse `plist` representa a credencial OAuth local atual do Google para o app iOS de desenvolvimento/testes.

Na prática, ele serve para relembrar rapidamente:

- qual `CLIENT_ID` iOS foi emitido;
- qual `REVERSED_CLIENT_ID` precisa existir no app iOS quando o fluxo Google Sign-In exigir o callback nativo;
- qual `BUNDLE_ID` essa credencial espera.

### O que é importante no iOS

No iOS, para Google Sign-In e fluxos semelhantes, normalmente os pontos críticos são:

- `BUNDLE_ID` do app;
- `CLIENT_ID` emitido para iOS;
- `REVERSED_CLIENT_ID`, usado no esquema reverso de callback do app.

Ou seja:

- no Android, a dupla crítica costuma ser `package name + SHA-1`;
- no iOS, a dupla crítica costuma ser `bundle id + reversed client id`.

### Relação com o Keycloak

Assim como no caso do Android, esse arquivo iOS não substitui as variáveis do broker web do Keycloak:

- `KEYCLOAK_IDP_THIMISU_GOOGLE_CLIENT_ID`
- `KEYCLOAK_IDP_THIMISU_GOOGLE_CLIENT_SECRET`

Essas variáveis continuam sendo a credencial web do Google usada pelo Keycloak.

O `plist` acima é apenas a referência local de iOS `dev/testes`.

### Regra de armazenamento adotada

Para evitar perda do arquivo e também evitar commit acidental:

- o arquivo foi guardado em `.local-secrets/`;
- a pasta `.local-secrets/` foi adicionada ao `.gitignore`;
- a cópia local foi criada com permissão restrita.

Isso significa que:

- o arquivo continua disponível localmente;
- ele não entra no versionamento do repositório;
- o caminho documentado acima pode ser usado como referência operacional futura.

### Como descobrir o SHA-1 de produção

Se o app tiver uma keystore própria de release, o comando tem o mesmo formato, mudando apenas o caminho, alias e senhas:

```bash
keytool -list -v -alias <seu_alias_release> -keystore /caminho/da/sua-release.jks
```

Se o seu projeto usar `key.properties`, normalmente você encontra nele:

- `storeFile`
- `storePassword`
- `keyAlias`
- `keyPassword`

Com isso, o comando fica:

```bash
keytool -list -v -alias <keyAlias> -keystore <storeFile>
```

Se quiser evitar prompt interativo:

```bash
keytool -list -v -alias <keyAlias> -keystore <storeFile> -storepass <storePassword> -keypass <keyPassword>
```

### Situação atual deste workspace

No workspace atual, o app Android do `Thimísu` está em:

```text
/Users/thiago/Desenvolvedor/flutter/eickrono-thimisu/eickrono-thimisu-app/android
```

Também não encontrei `key.properties` Android versionado.

Também encontrei que o app Flutter localizado em:

- `/Users/thiago/Desenvolvedor/flutter/eickrono-thimisu/eickrono-thimisu-app/android/app/build.gradle.kts`

está usando a `signingConfig` de `debug` inclusive no bloco `release`.

Na prática, isso significa que, enquanto esse projeto não ganhar uma keystore própria de produção, o fingerprint de debug acima é o que representa a assinatura Android usada localmente.

### O que preencher no projeto

```dotenv
KEYCLOAK_IDP_THIMISU_GOOGLE_CLIENT_ID=<cole_aqui>
KEYCLOAK_IDP_THIMISU_GOOGLE_CLIENT_SECRET=<cole_aqui>
```

### Custo

Não encontrei cobrança separada, nas fontes oficiais consultadas, para simplesmente criar o OAuth client de login.
O que pode acontecer é o console pedir billing para alguma API específica habilitada no projeto. Isso é diferente de uma taxa para o login OAuth em si.

## Apple

### O que você vai obter

- `KEYCLOAK_IDP_THIMISU_APPLE_CLIENT_ID`
- `KEYCLOAK_IDP_THIMISU_APPLE_CLIENT_SECRET_JWT`

### Leia isto antes de começar

Para este fluxo de `Sign in with Apple` web brokerado pelo Keycloak, a ordem correta comeca em `Identifiers`, nao em `Certificates` e nao em `Keys`.

### Qual menu do portal Apple entra neste fluxo

No `developer.apple.com/account`, a navegacao correta para este caso fica assim:

| Menu do portal Apple | Entra neste fluxo? | O que fazer nele | Observacao operacional |
| --- | --- | --- | --- |
| `Certificates` | Nao | Nada para o broker Apple do Keycloak. | Essa area e para assinatura do app, APNs, Apple Pay e certificados relacionados. |
| `Identifiers` | Sim | Revisar o `App ID` principal `com.eickrono.thimisu`; criar ou revisar os `Services IDs` por ambiente; configurar `Domains and Subdomains` e `Return URLs` dentro de cada `Services ID`. | E a area principal deste fluxo. `App ID` e `Services ID` moram aqui. |
| `Devices` | Nao | Nada para este fluxo. | Isso e para cadastro de aparelhos usados em desenvolvimento/ad hoc. |
| `Profiles` | Nao | Nada para este fluxo. | Isso e para provisionamento e assinatura do app, nao para o broker web do Keycloak. |
| `Keys` | Sim | Criar a chave `.p8` de `Sign in with Apple` usada para assinar o JWT que entra no Keycloak. | A `Key` vem depois de `Identifiers`. |
| `Services` | Nao | Nao usar esse menu top-level para este caso. | O que interessa aqui e `Services ID`, que fica dentro de `Identifiers`, nao dentro de `Services`. |

Traducao pratica:

1. entrar em `Identifiers`;
2. revisar o `App ID` principal do iOS;
3. criar ou revisar o `Services ID` do ambiente;
4. ainda dentro do `Services ID`, configurar dominio e callback web;
5. so depois entrar em `Keys` para gerar a `.p8`.

Ponto importante:

- `App ID` e `Services ID` nao sao duas coisas fora de `Identifiers`;
- os dois ficam dentro da area `Identifiers` do portal Apple;
- mas eles sao **dois tipos diferentes de identifier**;
- por isso voce cria um primeiro e o outro depois;
- o portal usa `radio button` porque, em cada criacao, voce escolhe **um tipo de identifier por vez**.

Traducao direta para o nosso caso:

- `App ID`
  - e o identificador do app iPhone;
  - no projeto atual, ele usa o `Bundle ID`:
    - `com.eickrono.thimisu`
- `Services ID`
  - e o identificador web usado pelo broker Apple do Keycloak;
  - ele nao representa o app iPhone;
  - ele representa a integracao web/OIDC do servidor de autorizacao.
- `Key .p8`
  - e a chave privada usada para assinar o JWT enviado pelo Keycloak para a Apple;
  - ela nao substitui nem o `App ID` nem o `Services ID`.

Em resumo:

1. primeiro voce cria ou revisa o `App ID` do app iOS;
2. depois cria ou revisa o `Services ID` do Keycloak/web;
3. por ultimo cria a `Key .p8`.

Leia assim:

1. `Identifiers`:
   - criar ou revisar primeiro o `App ID` principal do app iOS;
   - depois criar ou revisar o `Services ID`;
   - depois abrir o `Services ID` e configurar dominio e callback web.
2. `Keys`:
   - criar a chave `.p8` usada para assinar o JWT.
3. `Certificates`:
   - ignorar para este fluxo especifico;
   - essa area e para assinatura do app, APNs, Apple Pay e outros certificados, nao para o broker web do Keycloak.

Se voce estiver numa tela como `Create a New Certificate`, esta na tela errada para o login Apple web do Keycloak.

### Como interpretar o banner `Finish Setting up Sign in with Apple`

Depois de criar ou abrir um `Services ID`, a Apple pode mostrar um banner com este checklist:

1. `Enable App ID`
2. `Create Service ID for Web Authentication`
3. `Create Key`
4. `Register Email Sources for Communication`

Para o fluxo brokerado atual do projeto, interprete assim:

- `Enable App ID`
  - obrigatorio;
  - significa que o `App ID` principal `com.eickrono.thimisu` precisa existir e estar com `Sign in with Apple` habilitado.
- `Create Service ID for Web Authentication`
  - obrigatorio;
  - e o `Services ID` web por ambiente, por exemplo `com.eickrono.thimisu.oidc.dev`.
- `Create Key`
  - obrigatorio;
  - e a chave `.p8` usada para assinar o JWT que vira o `client secret` do Keycloak;
  - ela nao precisa existir uma por ambiente; ela e associada ao `Primary App ID`.
- `Register Email Sources for Communication`
  - nao e bloqueante para o fluxo atual;
  - isso fica relacionado a cenarios de comunicacao via `Private Email Relay`, nao ao broker Apple basico do Keycloak.

Se esse banner aparecer, isso nao significa erro.
Na pratica, ele quer apenas dizer que o setup ainda nao esta completo.

Conclusao operacional:

1. criar ou revisar o `Services ID`;
2. configurar `Domains and Subdomains` e `Return URLs`;
3. clicar em `Save`;
4. ir para `Keys`;
5. criar a chave `.p8`;
6. gerar o JWT;
7. aplicar `client_id` e `client_secret` no Keycloak.

Se a tela mostrar algo como `Website URLs` associado ao `Primary App ID`, isso tambem nao e erro.
E apenas o resumo da associacao entre o `Services ID` web e o app principal.

### O que isso significa no nosso desenho

No caso da Apple:

- a credencial obrigatoria hoje e a do broker do Keycloak;
- isso significa `Services ID` + JWT assinado para o broker OIDC `apple`;
- isso significa que o fluxo comeca em `Identifiers`, porque e ali que ficam `App ID` e `Services ID`;
- isso significa que a criacao da `Key` vem depois;
- isso significa que a area de `Certificates` comum nao entra nesse processo;
- isso nao significa que o app Flutter esteja usando `Sign in with Apple` nativo;
- e tambem nao tem relacao com `App Attest`, que e um mecanismo separado de seguranca do dispositivo.

- o `client_id` será o identificador do `Services ID`;
- o “secret” não é um texto estático copiado do portal;
- o “secret” que entra no Keycloak é um JWT assinado com sua chave privada `.p8`.

### Padrao correto das private keys Apple neste projeto

Para `Sign in with Apple`, a Apple associa a private key ao `Primary App ID`, nao ao ambiente.

Isso significa:

- o `Primary App ID` continua sendo `com.eickrono.thimisu`;
- os `Services IDs` continuam separados por ambiente:
  - `com.eickrono.thimisu.oidc.dev`
  - `com.eickrono.thimisu.oidc.stg`
  - `com.eickrono.thimisu.oidc.prd`
- as private keys `.p8` nao precisam existir uma por ambiente;
- a Apple permite no maximo **duas** private keys por `Primary App ID`;
- a forma correta de operar isso no projeto e tratar essas duas chaves por papel:
  - `Principal`
  - `Rotacao`

Convencao operacional adotada:

- `Thimisu Apple Principal`
- `Thimisu Apple Rotacao OIDC`

Conclusao:

- separe por ambiente no `Services ID`;
- compartilhe a key `Principal` entre `dev`, `stg` e `prod`;
- mantenha a key `Rotacao` reservada para troca segura sem downtime;
- nao tente manter uma terceira key exclusiva de `prd`, porque isso foge do limite operacional da Apple para este `Primary App ID`.

### Sites oficiais

- Portal principal: `https://developer.apple.com/account/`
- Services IDs: `https://developer.apple.com/account/resources/identifiers/list/serviceId`
- Keys: `https://developer.apple.com/account/resources/authkeys/list`
- Registrar Services ID: `https://developer.apple.com/help/account/identifiers/register-a-services-id`
- Configurar Sign in with Apple for the web: `https://developer.apple.com/help/account/capabilities/configure-sign-in-with-apple-for-the-web`
- Criar private key para Sign in with Apple: `https://developer.apple.com/help/account/capabilities/create-a-sign-in-with-apple-private-key`

### Pré-requisitos e ordem obrigatória

Antes de configurar o site, a Apple documenta que você precisa:

- ter um `primary App ID` já habilitado para `Sign in with Apple`;
- registrar um `Services ID`;
- associar o website a esse app;
- criar uma private key para assinar developer tokens.

Traduzindo isso para a navegacao do portal Apple:

1. `Identifiers > + > App IDs > App`
2. `Identifiers > + > Services IDs`
3. configuracao web dentro do `Services ID`
4. `Keys > Sign in with Apple`

No banner atual da Apple, isso costuma aparecer com estes nomes:

1. `Enable App ID`
2. `Create Service ID for Web Authentication`
3. `Create Key`
4. `Register Email Sources for Communication`

No nosso caso, o item 4 nao e bloqueante para o broker Apple do Keycloak agora.

Nao comece por:

- `Certificates > Create a New Certificate`
- `Keys` antes de existir `App ID` + `Services ID`

### Passo a passo detalhado

#### Ordem correta

Na Apple, a ordem certa para esse fluxo e esta:

1. garantir que o **App ID principal do app iOS** exista e tenha `Sign in with Apple`;
2. criar ou revisar o **Services ID** web;
3. associar o **Services ID** ao **App ID principal**;
4. configurar `Domains and Subdomains` e `Return URLs`;
5. criar a **private key** `.p8` de `Sign in with Apple`.

Se voce tentar começar pela `Key` antes disso, a Apple tende a mostrar:

- `No App ID is available`
- `There are no identifiers available that can be associated with the key`

porque a associacao previa ainda nao existe.

#### Parte 1: garantir o App ID principal

1. Acesse `https://developer.apple.com/account/`.
2. Entre com a conta do Apple Developer Program.
3. Vá em `Certificates, Identifiers & Profiles`.
4. No menu lateral, clique em `Identifiers`.
5. Procure o App ID do app iOS atual.

No projeto atual, os valores reais sao:

- `Bundle ID`: `com.eickrono.thimisu`
- `Team ID`: `M863Q6N87G`

6. Se o App ID `com.eickrono.thimisu` nao existir:
   - clique em `+`;
   - escolha `App IDs`;
   - escolha `App`;
   - avance e crie esse identificador.
7. Abra o App ID `com.eickrono.thimisu`.
8. Clique em `Edit`.
9. Habilite `Sign in with Apple`.
10. Salve.

Observacao:

- esse App ID e o **app principal** que o `Services ID` web vai usar como referencia;
- sem ele, a configuracao web nao fecha corretamente.

#### Parte 2: criar ou revisar o Services ID

1. Ainda em `Certificates, Identifiers & Profiles`, continue em `Identifiers`.
2. Clique no botão `+`.
3. Selecione `Services IDs`.
4. Clique em `Continue`.
5. Preencha os campos.

Como preencher:

- `Description`
  - e so o nome amigavel mostrado no portal.
  - sugestao adotada para `dev`:
    - `Thimisu DEV`
- `Identifier`
  - e o identificador tecnico estavel do `Services ID`.
  - ele vira o `client_id` da Apple no Keycloak.
  - deve ser unico na conta Apple.
  - use formato reverse-DNS.
  - sugestao adotada para `dev`:
    - `com.eickrono.thimisu.oidc.dev`

Importante:

- `Thimisu DEV` / `com.eickrono.thimisu.oidc.dev` servem apenas para o ambiente `dev`;
- esse `Services ID` nao deve ser reutilizado em `stg` nem em `prod`;
- para cada ambiente adicional, crie um `Services ID` separado.

Convencao recomendada para este projeto:

- o `Services ID` da Apple deve ser por app/familia de app, nao global por empresa;
- para o app `Thimisu`, o identificador deve incluir `thimisu` no namespace;
- isso evita ambiguidade futura se surgirem outros apps no mesmo ecossistema e no mesmo Apple Developer Team.

Se quiser separar por ambiente:

- `dev`: `com.eickrono.thimisu.oidc.dev`
- `stg`: `com.eickrono.thimisu.oidc.stg`
- `prod`: `com.eickrono.thimisu.oidc.prd`

No desenho atual, isso significa:

- `dev`
  - `Description`: `Thimisu DEV`
  - `Identifier`: `com.eickrono.thimisu.oidc.dev`
- `stg`
  - criar outro `Services ID`
  - sugestao:
    - `Description`: `Thimisu STG`
    - `Identifier`: `com.eickrono.thimisu.oidc.stg`
- `prod`
  - criar outro `Services ID`
  - sugestao:
    - `Description`: `Thimisu`
    - `Identifier`: `com.eickrono.thimisu.oidc.prd`

Observacao operacional:

- o texto mostrado para o usuario na tela da Apple vem do `Description`/nome amigavel do `Services ID`;
- trocar apenas esse nome amigavel nao exige regenerar JWT nem atualizar o Keycloak;
- trocar o `Identifier` exige regenerar o `JWT` e atualizar o `KEYCLOAK_IDP_THIMISU_APPLE_CLIENT_ID`, porque esse valor vira o `client_id` tecnico do broker.

6. Clique em `Continue`.
7. Revise.
8. Clique em `Register`.
9. Abra o `Services ID` recem-criado na lista.

#### Parte 3: associar o website ao app

1. Dentro do `Services ID`, localize `Sign in with Apple`.
2. Clique em `Configure`.
3. Em `Primary App ID`, selecione o App ID principal do app:
   - `com.eickrono.thimisu`
4. Em `Domains and Subdomains`, informe o host publico usado pelo broker Apple do Keycloak.

No `dev` atual, use:

```text
oidc-dev.eickrono.online
```

5. Em `Return URLs`, informe a callback exata do broker `apple` do Keycloak.

No `dev` atual, use:

```text
https://oidc-dev.eickrono.online/realms/eickrono/broker/apple/endpoint
```

6. Clique em `Done`.
7. Clique em `Continue`.
8. Revise a configuracao.
9. Clique em `Save`.

#### Parte 4: como interpretar cada campo

`Primary App ID`

- e o app nativo principal associado ao website;
- no nosso caso, hoje ele deve ser:
  - `com.eickrono.thimisu`

`Description`

- nome amigavel no portal Apple;
- pode mudar depois sem quebrar integracao;
- nao entra no Keycloak.

`Identifier`

- identificador tecnico permanente do `Services ID`;
- e o que depois entra em:
  - `KEYCLOAK_IDP_THIMISU_APPLE_CLIENT_ID`
- esse valor deve ser pensado com cuidado, porque costuma virar referencia estavel do ambiente.

`Domains and Subdomains`

- lista dos hosts autorizados para o `Sign in with Apple` web;
- aqui entra so o host, sem `https://` e sem path;
- para `dev` atual:
  - `oidc-dev.eickrono.online`

`Return URLs`

- lista das callbacks absolutas que a Apple pode usar ao finalizar o login;
- aqui entra a URL completa, com `https`, host e path;
- para `dev` atual:
  - `https://oidc-dev.eickrono.online/realms/eickrono/broker/apple/endpoint`

#### Parte 5: o que fazer se a Apple bloquear a tela

Se a Apple mostrar algo como:

- `No App ID is available`
- `No App ID is available for web authentication configuration`
- `There are no identifiers available that can be associated with the key`

verifique nesta ordem:

1. o App ID `com.eickrono.thimisu` existe;
2. ele esta no time correto;
3. `Sign in with Apple` foi habilitado nesse App ID;
4. o `Services ID` foi salvo antes de tentar criar a key;
5. voce esta configurando o `Services ID`, nao a `Key`, na etapa web.

### O que entra como domain/return URL

Cada `Services ID` da Apple aceita listas em `Domains and Subdomains` e `Return URLs`.
Entao, durante transicao de dominio, cadastre todos os hosts publicos realmente usados por aquele ambiente.

Tabela resumida para copiar:

| Ambiente | Services ID | Domains and Subdomains | Return URLs |
| --- | --- | --- | --- |
| `dev` | `com.eickrono.thimisu.oidc.dev` | `oidc-dev.eickrono.online` | `https://oidc-dev.eickrono.online/realms/eickrono/broker/apple/endpoint` |
| `dev` | `com.eickrono.thimisu.oidc.dev` | `oidc-dev.eickrono.com` | `https://oidc-dev.eickrono.com/realms/eickrono/broker/apple/endpoint` |
| `stg` | `com.eickrono.thimisu.oidc.stg` | `oidc-stg.eickrono.store` | `https://oidc-stg.eickrono.store/realms/eickrono/broker/apple/endpoint` |
| `stg` | `com.eickrono.thimisu.oidc.stg` | `oidc-stg.eickrono.com` | `https://oidc-stg.eickrono.com/realms/eickrono/broker/apple/endpoint` |
| `prod` | `com.eickrono.thimisu.oidc.prd` | `oidc.eickrono.com` | `https://oidc.eickrono.com/realms/eickrono/broker/apple/endpoint` |

Observacoes:

- `Primary App ID` continua unico para os tres ambientes: `com.eickrono.thimisu`;
- em `dev` e `stg`, mantenha apenas as linhas dos hosts publicos realmente usados naquele momento;
- `localhost` nao deve entrar aqui para Apple Web.

#### `dev`

Se o ambiente `dev` ainda usa o tunnel atual:

```text
Domain/Subdomain: oidc-dev.eickrono.online
Return URL: https://oidc-dev.eickrono.online/realms/eickrono/broker/apple/endpoint
```

Se o ambiente `dev` tambem ja estiver atendendo pelo dominio canonico, adicione tambem:

```text
Domain/Subdomain: oidc-dev.eickrono.com
Return URL: https://oidc-dev.eickrono.com/realms/eickrono/broker/apple/endpoint
```

Diagnostico rapido para `invalid_client` no Apple `dev`:

- se o navegador ja abriu `https://appleid.apple.com/auth/authorize`, o app e o Keycloak ja conseguiram chegar ao passo web correto;
- nesse ponto, confirme que o `Services ID` salvo na Apple e exatamente `com.eickrono.thimisu.oidc.dev`;
- confirme que o `Primary App ID` associado continua `com.eickrono.thimisu`;
- confirme que `oidc-dev.eickrono.online` esta listado em `Domains and Subdomains`;
- confirme que `https://oidc-dev.eickrono.online/realms/eickrono/broker/apple/endpoint` esta listado em `Return URLs`;
- se o dominio canonico `oidc-dev.eickrono.com` tambem estiver em uso, ele precisa entrar como linha adicional propria, junto com a `Return URL` equivalente;
- se o Keycloak estiver redirecionando a Apple com `client_id=com.eickrono.thimisu.oidc.dev` e `redirect_uri=https://oidc-dev.eickrono.online/realms/eickrono/broker/apple/endpoint`, mas a Apple ainda retornar `invalid_client`, a pendencia esta no cadastro ou na propagacao do `Services ID` no portal da Apple, nao no Flutter.

#### `stg`

Se o ambiente `stg` ainda usa o host publico atual:

```text
Domain/Subdomain: oidc-stg.eickrono.store
Return URL: https://oidc-stg.eickrono.store/realms/eickrono/broker/apple/endpoint
```

Se o ambiente `stg` tambem ja estiver atendendo pelo dominio canonico, adicione tambem:

```text
Domain/Subdomain: oidc-stg.eickrono.com
Return URL: https://oidc-stg.eickrono.com/realms/eickrono/broker/apple/endpoint
```

#### `prod`

Para `prod`, o valor canonico atual e:

```text
Domain/Subdomain: oidc.eickrono.com
Return URL: https://oidc.eickrono.com/realms/eickrono/broker/apple/endpoint
```

Resumo operacional:

- `dev`: hoje pode precisar de `oidc-dev.eickrono.online` e `oidc-dev.eickrono.com`;
- `stg`: hoje pode precisar de `oidc-stg.eickrono.store` e `oidc-stg.eickrono.com`;
- `prod`: hoje usa apenas `oidc.eickrono.com`.

### Restrição prática importante da Apple

Embora o projeto hoje tenha callbacks locais para `dev` e `stg`, a Apple é o provedor mais sensível para teste local.

Inferência operacional importante:

- para testes reais, você provavelmente vai preferir usar um domínio público HTTPS, ou um túnel estável com domínio válido;
- `localhost` puro pode ser insuficiente ou inconveniente para fechar o fluxo web da Apple em cenários reais.
- para o cadastro do `Services ID`, trate `localhost` como valor invalido operacionalmente: use apenas `Domains and Subdomains` publicos e `Return URLs` HTTPS publicas.

Ou seja:

- Google, Facebook e LinkedIn tendem a ser mais tolerantes ao setup executado no computador do desenvolvedor;
- Apple merece ser validada cedo em ambiente com URL pública.
- no resumo operacional atual deste projeto: `Google Web` pode manter `localhost` como callback adicional em `dev` e `stg`, mas `Apple Web` nao deve cadastrar `localhost`.

#### Parte 3: criar a private key

1. Acesse `https://developer.apple.com/account/resources/authkeys/list`.
2. Entre em `Keys`.
3. Clique no botão `+`.
4. Em `Key Name`, use um nome operacional claro.

Exemplo:

- `Thimisu Apple Principal`

5. Marque `Sign in with Apple`.
6. Clique em `Configure`.
7. Associe a chave ao `primary App ID` correto:
   - `com.eickrono.thimisu`
8. Salve a configuracao da chave.
9. Clique em `Continue`.
10. Clique em `Register`.
11. Baixe o arquivo `.p8`.

Guarde três informações:

- `Team ID`
- `Key ID`
- arquivo `.p8`

Além disso, anote o identificador do `Services ID`, porque ele vira o `client_id`.

Registro operacional atual das chaves Apple do app `Thimisu`:

- key `Principal`
  - `Nome`: `Thimisu Apple Principal`
  - `Key ID`: `SD47UPB393`
  - `Servicos`: `Sign in with Apple`
- key `Rotacao`
  - `Nome`: `Thimisu Apple Rotacao OIDC`
  - `Key ID`: `273LPMGRY8`
  - `Servicos`: `DeviceCheck`, `Sign in with Apple`

Leitura correta dessas duas chaves:

- a key `Principal` e a candidata padrao para assinar os JWTs de `dev`, `stg` e `prod`;
- a key `Rotacao` fica reservada para rotacao/contingencia;
- a presenca de `DeviceCheck` na key de rotacao nao substitui a configuracao do `Services ID` nem o JWT do broker Apple;
- o aviso do portal sobre `Services` significa apenas que o `Services ID` precisa continuar com a configuracao web concluida para a equipe.

#### Parte 4: guardar o arquivo `.p8` no local correto

No workspace atual, o caminho operacional adotado para a key `Principal` e:

```text
/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/.local-secrets/apple/eickrono-oidc/principal/AuthKey_SD47UPB393.p8
```

Valores operacionais da key `Principal`:

- `Team ID`: `M863Q6N87G`
- `Key ID`: `SD47UPB393`
- `uso esperado`: `dev`, `stg` e `prod`

Se o navegador baixar a key `Principal` para `Downloads`, mova-o para o diretório local de segredos com:

```bash
mkdir -p /Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/.local-secrets/apple/eickrono-oidc/principal

mv /Users/thiago/Downloads/AuthKey_SD47UPB393.p8 \
  /Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/.local-secrets/apple/eickrono-oidc/principal/AuthKey_SD47UPB393.p8

chmod 600 /Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/.local-secrets/apple/eickrono-oidc/principal/AuthKey_SD47UPB393.p8
```

Observacoes:

- esse arquivo nao deve ser versionado no Git;
- a permissao `600` reduz exposicao desnecessaria do segredo;
- a mesma key `Principal` pode assinar os JWTs de `dev`, `stg` e `prod`;
- a key `Rotacao` deve ficar guardada separadamente para troca futura;
- a Apple permite no maximo duas private keys por `Primary App ID`, entao o desenho correto e `Principal` + `Rotacao`, nao uma key por ambiente.

No workspace atual, o caminho operacional adotado para a key `Rotacao` e:

```text
/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/.local-secrets/apple/eickrono-oidc/rotacao/AuthKey_273LPMGRY8.p8
```

Valores operacionais da key `Rotacao`:

- `Team ID`: `M863Q6N87G`
- `Key ID`: `273LPMGRY8`
- `uso esperado`: rotacao/contingencia

### Como transformar isso no secret que o Keycloak espera

O projeto já tem o script:

- `/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/autorizacao/realms/gerar-apple-client-secret-jwt.sh`

Exemplo com a key `Principal`:

```bash
cd /Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor

export APPLE_TEAM_ID="M863Q6N87G"
export APPLE_KEY_ID="SD47UPB393"
export APPLE_PRIVATE_KEY_P8="/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/.local-secrets/apple/eickrono-oidc/principal/AuthKey_SD47UPB393.p8"
export APPLE_CLIENT_ID="com.eickrono.thimisu.oidc.dev"
export OUTPUT_ENV_LINE=true

sh autorizacao/realms/gerar-apple-client-secret-jwt.sh
```

O valor gerado é o que entra em:

```dotenv
KEYCLOAK_IDP_THIMISU_APPLE_CLIENT_ID=<services_id_identifier>
KEYCLOAK_IDP_THIMISU_APPLE_CLIENT_SECRET_JWT=<jwt_gerado>
```

Exemplo equivalente para gerar o JWT de `stg` com a mesma key `Principal`:

```bash
cd /Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor

export APPLE_TEAM_ID="M863Q6N87G"
export APPLE_KEY_ID="SD47UPB393"
export APPLE_PRIVATE_KEY_P8="/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/.local-secrets/apple/eickrono-oidc/principal/AuthKey_SD47UPB393.p8"
export APPLE_CLIENT_ID="com.eickrono.thimisu.oidc.stg"
export OUTPUT_ENV_LINE=true

sh autorizacao/realms/gerar-apple-client-secret-jwt.sh
```

Exemplo equivalente para gerar o JWT de `prod` com a mesma key `Principal`:

```bash
cd /Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor

export APPLE_TEAM_ID="M863Q6N87G"
export APPLE_KEY_ID="SD47UPB393"
export APPLE_PRIVATE_KEY_P8="/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/.local-secrets/apple/eickrono-oidc/principal/AuthKey_SD47UPB393.p8"
export APPLE_CLIENT_ID="com.eickrono.thimisu.oidc.prd"
export OUTPUT_ENV_LINE=true

sh autorizacao/realms/gerar-apple-client-secret-jwt.sh
```

Materializacoes locais recomendadas para o JWT gerado:

- `dev`: `/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/.local-secrets/apple/eickrono-oidc/dev/keycloak-apple.env`
- `stg`: `/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/.local-secrets/apple/eickrono-oidc/stg/keycloak-apple.env`
- `prod`: `/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/.local-secrets/apple/eickrono-oidc/prod/keycloak-apple.env`

#### Parte 5: aplicar nos ambientes do Keycloak

Com a convencao por app ja adotada, os valores por ambiente ficam assim:

- `dev`: `com.eickrono.thimisu.oidc.dev`
- `stg`: `com.eickrono.thimisu.oidc.stg`
- `prod`: `com.eickrono.thimisu.oidc.prd`

Aplicacao real no workspace atual:

- `dev`: atualizar `infraestrutura/dev/.env`
- `stg`: atualizar `infraestrutura/stg/.env`
- `prod`: materializar o segredo fora do Git, por exemplo em `.local-secrets/apple/eickrono-oidc/prod/keycloak-apple.env`

Depois de gerar o JWT, aplique-o no arquivo:

- `/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/infraestrutura/dev/.env`

No `dev`, os valores ficam assim:

```dotenv
KEYCLOAK_IDP_THIMISU_APPLE_CLIENT_ID=com.eickrono.thimisu.oidc.dev
KEYCLOAK_IDP_THIMISU_APPLE_CLIENT_SECRET_JWT=<jwt_gerado_pelo_script>
```

Observacao importante:

- se o ambiente ainda estiver com o `Services ID` antigo, atualize primeiro o portal Apple e gere um novo JWT antes de trocar esse valor no `.env`;
- se o realm `eickrono` ja existir no Keycloak em execucao, apenas recriar o container nao reimporta automaticamente as configuracoes do broker;
- nesse caso, alem de atualizar o `.env`, aplique a mudanca no broker `apple` em runtime.

Passo a passo real do `dev` atual:

1. gerar o JWT com o script acima;
2. copiar o valor para `infraestrutura/dev/.env`;
3. reautenticar o `kcadm` dentro do container do Keycloak;
4. atualizar o broker `apple` no realm `eickrono`.

Exemplo:

```bash
docker exec eickrono-keycloak-dev /bin/sh -lc '
  /opt/keycloak/bin/kcadm.sh config credentials \
    --server http://localhost:8080 \
    --realm master \
    --user admin \
    --password admin123 >/dev/null &&
  /opt/keycloak/bin/kcadm.sh update identity-provider/instances/apple \
    -r eickrono \
    -s config.authorizationUrl=https://appleid.apple.com/auth/authorize?response_mode=form_post \
    -s config.clientId=com.eickrono.thimisu.oidc.dev \
    -s "config.clientSecret=<jwt_gerado_pelo_script>" >/dev/null &&
  /opt/keycloak/bin/kcadm.sh get identity-provider/instances/apple -r eickrono
'
```

Validacao esperada:

- `alias = apple`
- `enabled = true`
- `config.authorizationUrl = https://appleid.apple.com/auth/authorize?response_mode=form_post`
- `config.clientId = com.eickrono.thimisu.oidc.dev`
- `config.clientSecret` mascarado pelo Keycloak

Observacao importante:

- como o broker Apple atual usa `defaultScope = openid email name`, a Apple exige `response_mode=form_post` no authorize;
- sem esse ajuste, a tela da Apple retorna `invalid_request` com a mensagem `response_mode must be form_post when name or email scope is requested`;
- a convencao deste projeto e persistir isso diretamente no `authorizationUrl` do broker `apple`.

### Custo

No seu caso, a resposta prática é:

- você já paga o item principal, que é a licença do Apple Developer Program;
- para esse fluxo, em princípio, não há outra cobrança Apple separada só para ativar o Sign in with Apple web.

## Facebook

### O que você vai obter

- `KEYCLOAK_IDP_THIMISU_FACEBOOK_APP_ID`
- `KEYCLOAK_IDP_THIMISU_FACEBOOK_APP_SECRET`

### Sites oficiais

- Portal Meta for Developers: `https://developers.facebook.com/`
- Guia oficial de criação de app: `https://developers.facebook.com/docs/development/create-an-app`
- Tela operacional para iniciar a criação do app no portal: `https://developers.facebook.com/apps/creation/`
- Facebook Login docs: `https://developers.facebook.com/documentation/facebook-login/web`

### Pré-requisitos

A documentação da Meta deixa explícito que, antes de criar o app, você precisa:

- se registrar como developer;
- estar autenticado na sua conta de developer.

### Passo a passo detalhado

1. Acesse `https://developers.facebook.com/`.
2. Faça login.
3. Se ainda não for developer cadastrado:
   - conclua o registro em `Meta for Developers`.
4. Vá para:
   - `https://developers.facebook.com/apps/creation/`
5. Preencha:
   - nome do app;
   - email de contato.
6. Clique em `Next`.
7. Escolha o `use case`.

Para o nosso cenário, a Meta pode exibir opções diferentes conforme o estado da conta e o catálogo atual.
O caminho mais útil costuma ser o fluxo ligado a autenticação com Facebook Login.

Na documentação da Meta, também existe a observação de que alguns produtos podem ser adicionados automaticamente conforme o use case.

Na interface atual do portal, a opcao correta para este projeto e:

- `Autenticar e solicitar dados de usuários com o Login do Facebook`

Nao selecione, para este caso:

- Marketing API
- anuncios de app
- Threads
- Instant Game
- WhatsApp

8. Se o `Facebook Login` não for adicionado automaticamente:
   - entre no `App Dashboard`;
   - procure a seção de produtos;
   - adicione `Facebook Login`.

9. No painel lateral do app, entre em:
   - `Casos de uso`
   - `Autenticar e solicitar dados de usuários com o Login do Facebook`

10. Dentro desse caso de uso, procure a área de configuração do `Facebook Login`.
   O nome da tela pode variar um pouco, mas em geral você precisa chegar em algo como:
   - `Configurações`
   - `Settings`
   - `Valid OAuth Redirect URIs`

11. Abra as configurações básicas do app.
   Em geral, no App Dashboard atual, isso costuma ficar em:
   - `App settings`
   - `Basic`

Se a UI mudar, use a busca interna do dashboard por `Basic settings`.

12. Copie:
   - `App ID`
   - `App Secret`

13. Em `App settings` > `Basic`, preencha `App Domains` com os hosts publicos usados pelo broker `facebook` do Keycloak.
   Para o `dev`, use:

```text
oidc-dev.eickrono.online
```

   Se o dominio canonico do `dev` tambem estiver em uso, adicione tambem:

```text
oidc-dev.eickrono.com
```

   Para `stg`:

```text
oidc-stg.eickrono.store
```

   Se o dominio canonico do `stg` tambem estiver em uso, adicione tambem:

```text
oidc-stg.eickrono.com
```

   Para `prod`:

```text
oidc.eickrono.com
```

14. Adicione a redirect URI do Keycloak em `Valid OAuth Redirect URIs`.
   Exemplo em `dev`:

```text
https://oidc-dev.eickrono.online/realms/eickrono/broker/facebook/endpoint
```

Opcionalmente, para teste local no navegador do proprio Mac:

```text
http://localhost:8080/realms/eickrono/broker/facebook/endpoint
```

15. Se a Meta retornar erro `OAuthException` com `code: 191` dizendo que o dominio da URL nao esta incluido nos dominios do app, a pendencia esta em `App Domains`, nao no Flutter nem no Keycloak. O host publico usado no `redirect_uri` precisa aparecer tambem em `App Domains`.

### O que preencher no projeto

```dotenv
KEYCLOAK_IDP_THIMISU_FACEBOOK_APP_ID=<app_id>
KEYCLOAK_IDP_THIMISU_FACEBOOK_APP_SECRET=<app_secret>
```

### Escopo atual no Keycloak

Nos realms versionados deste projeto, o broker `facebook` usa:

- `defaultScope=email`
- `storeToken=false`

Isso e suficiente para o fluxo atual de autenticacao social brokerada.

Se o mesmo app Meta tambem estiver sendo preparado para Graph API, publicacao no Instagram, mensageria ou permissoes como `instagram_business_*`, `pages_show_list`, `pages_read_engagement` e `business_management`, trate isso como uma integracao separada do login social. Esses grants nao estao sendo solicitados pelo broker `facebook` atual do projeto.

### Custo

Não encontrei, nas fontes oficiais consultadas, uma cobrança separada para criar o app e obter `App ID` e `App Secret`.
O que costuma aparecer na Meta é requisito de conta, compliance, app review, business verification e regras de uso, não um pagamento direto para esse passo.

## LinkedIn

### O que você vai obter

- `KEYCLOAK_IDP_THIMISU_LINKEDIN_CLIENT_ID`
- `KEYCLOAK_IDP_THIMISU_LINKEDIN_CLIENT_SECRET`

### Sites oficiais

- Portal principal: `https://developer.linkedin.com/`
- My apps: `https://www.linkedin.com/developers/apps`
- Create app: `https://www.linkedin.com/developers/apps/new`
- Passo oficial com menus detalhados: `https://learn.microsoft.com/en-us/power-pages/security/authentication/oauth2-linkedin`
- Ajuda oficial sobre associação com LinkedIn Page: `https://www.linkedin.com/help/linkedin/answer/a548360`

### Pré-requisito importante

O LinkedIn exige associação do app com uma `LinkedIn Page`.
A ajuda oficial indica que, para determinados produtos, você precisa selecionar uma página padrão já no processo de criação.

### Passo a passo detalhado

1. Acesse `https://developer.linkedin.com/`.
2. Clique em `Create app`.
   Alternativamente, vá direto para:
   - `https://www.linkedin.com/developers/apps/new`
3. Preencha:
   - nome do app;
   - URL da LinkedIn Page da organização associada ao app;
   - logo do app.
4. Marque que leu e concorda com os termos.
5. Clique em `Create app`.
6. Na página do app, clique na aba `Auth`.
7. Em `Authentication keys`, copie:
   - `Client ID`
   - `Client Secret`
8. Ainda na aba `Auth`, localize `OAuth 2.0 settings`.
9. Clique no ícone de lápis.
10. Clique em `+ Add redirect URL`.
11. Cole a callback do Keycloak.
   Exemplo em `dev`:

```text
http://localhost:8080/realms/eickrono/broker/linkedin/endpoint
```

12. Clique em `Update`.

### O que preencher no projeto

```dotenv
KEYCLOAK_IDP_THIMISU_LINKEDIN_CLIENT_ID=<client_id>
KEYCLOAK_IDP_THIMISU_LINKEDIN_CLIENT_SECRET=<client_secret>
```

### Custo

Não encontrei, nas fontes oficiais consultadas, cobrança separada para criar o app básico e gerar `Client ID` e `Client Secret`.
O pré-requisito mais visível é a associação com uma LinkedIn Page. Dependendo do produto, também pode haver aprovação de acesso.

## Instagram

### O que você vai obter

- `KEYCLOAK_IDP_THIMISU_INSTAGRAM_APP_ID`
- `KEYCLOAK_IDP_THIMISU_INSTAGRAM_APP_SECRET`

### Sites oficiais

- Keycloak Server Administration Guide: `https://www.keycloak.org/docs/latest/server_admin/`
- Criar app de Instagram: `https://developers.facebook.com/docs/instagram-platform/create-an-instagram-app`
- Instagram API with Instagram Login: `https://developers.facebook.com/docs/instagram-platform/instagram-api-with-instagram-login/`
- Portal de apps Meta: `https://developers.facebook.com/apps/creation/`

### Observação importante sobre o nosso projeto

O broker `instagram` do Keycloak 26.5.5 está marcado como deprecated e depende da feature:

```text
instagram-broker
```

Isso significa que Instagram é o provedor mais frágil da nossa lista do ponto de vista de compatibilidade futura do Keycloak.

Em outras palavras:

- o projeto está preparado para ele;
- mas ele merece ser testado cedo, porque é o candidato mais provável a exigir ajuste extra.
- a própria documentação oficial do Keycloak manda preferir o broker `facebook` quando a necessidade real estiver no ecossistema atual da Meta.

### O que este broker cobre hoje

No estado atual do projeto, o alias `instagram` cobre apenas autenticacao/vinculacao social no Keycloak.

Ele nao cobre, por si so:

- `Instagram Business Login`;
- publicacao via Graph API;
- mensageria do Instagram;
- escopos como `instagram_business_basic`, `instagram_business_content_publish`, `instagram_business_manage_messages`, `pages_show_list`, `pages_read_engagement` e `business_management`.

Se o seu app Meta foi criado no use case `Gerenciar mensagens e conteudo no Instagram` ou esta configurado com `API setup with Instagram login` / `API setup with Facebook login`, esse setup nao equivale automaticamente ao broker `instagram` que o realm atual exporta.

### Pré-requisitos do lado Meta/Instagram para o broker atual

Para o broker `instagram` que o Keycloak 26.5.5 implementa hoje, a referência mais confiável é a documentação oficial do próprio Keycloak. O procedimento operacional esperado por esse broker é:

- criar o app na Meta em `Other`;
- selecionar o tipo `Consumer`;
- adicionar a plataforma `Website`;
- configurar `Instagram Basic Display`;
- registrar no app Meta a callback do Keycloak em:
  - `Valid OAuth Redirect URIs`
  - `Deauthorize Callback URL`
  - `Data Deletion Request URL`

### Passo a passo detalhado

1. Acesse:
   - `https://developers.facebook.com/`
2. Abra `My Apps`.
3. Clique em `Create App`.
4. Selecione:
   - `Other`
5. Clique em `Next`.
6. Selecione:
   - `Consumer`
7. Preencha os campos obrigatorios e conclua a criacao do app.
8. No dashboard do app, abra:
   - `App settings`
   - `Basic`
9. Clique em `+ Add Platform`.
10. Escolha:
   - `Website`
11. Preencha a URL do site.
12. Volte ao `Dashboard`.
13. Na caixa `Instagram Basic Display`, clique em `Set Up`.
14. Clique em `Create New App`.
15. Na tela do app do Instagram, preencha o `Display name`.
16. Cole a callback do Keycloak em:
   - `Valid OAuth Redirect URIs`
   - `Deauthorize Callback URL`
   - `Data Deletion Request URL`
17. Clique em `Show` no campo `Instagram App Secret`.
18. Anote:
   - `Instagram App ID`
   - `Instagram App Secret`
19. Siga para:
   - `App Review`
   - `Requests`
20. Continue o fluxo de revisão conforme a Meta solicitar.

### Onde obter o identificador e o secret

No fluxo esperado pelo broker atual do Keycloak, o identificador e o secret vêm do app criado para `Instagram Basic Display`:

- `Instagram App ID`
- `Instagram App Secret`

### Onde registrar a callback

Registre a callback do Keycloak nesses tres campos do app Meta:

- `Valid OAuth Redirect URIs`
- `Deauthorize Callback URL`
- `Data Deletion Request URL`

Exemplo em `dev`:

```text
http://localhost:8080/realms/eickrono/broker/instagram/endpoint
```

Exemplo em `stg`:

```text
https://oidc-stg.eickrono.store/realms/eickrono/broker/instagram/endpoint
```

Exemplo em `prod`:

```text
https://oidc.eickrono.com/realms/eickrono/broker/instagram/endpoint
```

### O que preencher no projeto

```dotenv
KEYCLOAK_IDP_THIMISU_INSTAGRAM_APP_ID=<instagram_app_id>
KEYCLOAK_IDP_THIMISU_INSTAGRAM_APP_SECRET=<instagram_app_secret>
```

### Quando esse setup nao e o certo

Se o objetivo for:

- publicar no Instagram;
- operar mensagens;
- pedir grants `instagram_business_*` ou `pages_*`;
- usar o fluxo novo de `Instagram Business Login`;

entao o broker `instagram` atual deste projeto nao e a peca certa. Nesse caso, voce precisa desenhar uma integracao dedicada para Graph API no servidor e nao apenas preencher o export de broker social.

### Custo

Não encontrei, nas fontes oficiais consultadas, cobrança separada para criar o app Meta/Instagram e chegar ao ponto de obter as credenciais.
Os pontos mais relevantes aqui são:

- tipo correto do app;
- conta profissional do Instagram;
- compatibilidade do broker do Keycloak.

## Como aplicar as credenciais no projeto

Depois de obter as credenciais, preencha os arquivos de ambiente.

### Desenvolvimento

Arquivo:

- `/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/infraestrutura/dev/.env`

Troque:

```dotenv
KEYCLOAK_IDP_THIMISU_GOOGLE_CLIENT_ID=trocar-google-client-id
KEYCLOAK_IDP_THIMISU_GOOGLE_CLIENT_SECRET=trocar-google-client-secret
KEYCLOAK_IDP_THIMISU_APPLE_CLIENT_ID=trocar-apple-client-id
KEYCLOAK_IDP_THIMISU_APPLE_CLIENT_SECRET_JWT=trocar-apple-client-secret-jwt
KEYCLOAK_IDP_THIMISU_FACEBOOK_APP_ID=trocar-facebook-app-id
KEYCLOAK_IDP_THIMISU_FACEBOOK_APP_SECRET=trocar-facebook-app-secret
KEYCLOAK_IDP_THIMISU_LINKEDIN_CLIENT_ID=trocar-linkedin-client-id
KEYCLOAK_IDP_THIMISU_LINKEDIN_CLIENT_SECRET=trocar-linkedin-client-secret
KEYCLOAK_IDP_THIMISU_INSTAGRAM_APP_ID=trocar-instagram-app-id
KEYCLOAK_IDP_THIMISU_INSTAGRAM_APP_SECRET=trocar-instagram-app-secret
```

pelos valores reais.

### Staging

Arquivo:

- `/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/infraestrutura/stg/.env`

Faça a mesma substituição.

## Como reiniciar o Keycloak depois de preencher

### Desenvolvimento

```bash
cd /Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/infraestrutura/dev
docker compose up -d --force-recreate eickrono-keycloak
```

### Staging

```bash
cd /Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/infraestrutura/stg
docker compose up -d --force-recreate eickrono-keycloak
```

## Como validar depois do restart

Validação mínima:

1. abrir o Admin Console do Keycloak;
2. entrar no realm correto;
3. abrir `Identity Providers`;
4. confirmar que existem:
   - `google`
   - `apple`
   - `facebook`
   - `linkedin`
   - `instagram`
5. abrir cada um e conferir se o `client id` não ficou mais com placeholder `trocar-*`.

Validação funcional:

1. abrir o app Flutter;
2. testar login social na tela de login;
3. autenticar com uma rede;
4. entrar na tela de contas vinculadas;
5. testar o fluxo de `vincular`;
6. testar o fluxo de `desvincular`.

## O que eu consigo fazer depois que você tiver os dados

Depois que você tiver as credenciais reais, eu consigo fazer a parte restante sem depender de portal externo:

1. preencher os `.env`;
2. gerar o JWT da Apple, se você me passar:
   - `Team ID`
   - `Key ID`
   - caminho do `.p8`
   - `Services ID`
3. reiniciar o Keycloak;
4. validar os brokers;
5. testar o app com os fluxos sociais já implementados.

## Resumo prático

Se o objetivo é terminar isso com menos atrito, a ordem mais pragmática é:

1. Google
2. Facebook
3. LinkedIn
4. Apple
5. Instagram

Essa ordem não é por prioridade de negócio.
É por menor risco operacional.

Motivo:

- Google costuma ser o mais direto;
- Facebook e LinkedIn tendem a ser administráveis;
- Apple é mais sensível por causa de domínio, Services ID e JWT;
- Instagram é o mais instável por causa do broker deprecated do Keycloak.

## Fontes oficiais consultadas

- Google OAuth para web apps: `https://developers.google.com/identity/protocols/oauth2/web-server`
- Google Auth Platform Clients Help: `https://support.google.com/cloud/answer/15549257`
- Google app verification/help: `https://support.google.com/cloud/answer/13461325`
- Apple Configure Sign in with Apple for the web: `https://developer.apple.com/help/account/capabilities/configure-sign-in-with-apple-for-the-web`
- Apple Create a Sign in with Apple private key: `https://developer.apple.com/help/account/capabilities/create-a-sign-in-with-apple-private-key`
- Apple Services IDs portal: `https://developer.apple.com/account/resources/identifiers/list/serviceId`
- Apple Keys portal: `https://developer.apple.com/account/resources/authkeys/list`
- Meta Create an App with Meta: `https://developers.facebook.com/docs/development/create-an-app`
- Meta Facebook Login docs: `https://developers.facebook.com/documentation/facebook-login/web`
- Meta Instagram Create an App: `https://developers.facebook.com/docs/instagram-platform/create-an-instagram-app`
- Meta Instagram API with Instagram Login: `https://developers.facebook.com/docs/instagram-platform/instagram-api-with-instagram-login/`
- LinkedIn Developer home: `https://developer.linkedin.com/`
- LinkedIn create app: `https://www.linkedin.com/developers/apps/new`
- LinkedIn official help on app/page association: `https://www.linkedin.com/help/linkedin/answer/a548360`
- Microsoft Learn com passos oficiais do portal LinkedIn: `https://learn.microsoft.com/en-us/power-pages/security/authentication/oauth2-linkedin`
