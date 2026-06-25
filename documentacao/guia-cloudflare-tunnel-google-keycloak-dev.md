# Guia Cloudflare Tunnel para Google OAuth Local no Keycloak

Este guia registra o procedimento adotado para viabilizar o login Google brokerado pelo Keycloak no ambiente local de desenvolvimento, inclusive em iPhone fisico.

## Convencao de dominios de ambiente

Os dominios auxiliares hoje reservados para implantacao inicial sao:

- `eickrono.online` para `dev`
- `eickrono.store` para `stg`

O padrão canonico futuro continua concentrado em `eickrono.com`:

- API de identidade:
  - `id-dev.eickrono.com`
  - `id-stg.eickrono.com`
  - `id.eickrono.com`
- servidor de autorizacao OIDC:
  - `oidc-dev.eickrono.com`
  - `oidc-stg.eickrono.com`
  - `oidc.eickrono.com`

Para a implantacao transitória segura, enquanto `dev` e `stg` ainda nao estiverem publicados no dominio principal, este guia usa:

- API de identidade:
  - `id-dev.eickrono.online`
  - `auth-stg.eickrono.store`
- servidor de autorizacao OIDC:
  - `oidc-dev.eickrono.online`
  - `oidc-stg.eickrono.store`

### O que cada hostname representa

- `id-*`: borda HTTP da API de identidade/autenticacao do ecossistema;
- `oidc-*`: superficie publica do servidor de autorizacao OIDC atualmente implementado com Keycloak.

### Por que `oidc` e nao `oauth` ou `keycloak`

- `oidc` descreve melhor o papel publico do servidor de autorizacao;
- `oauth` e mais amplo e impreciso;
- `keycloak` acopla o hostname a tecnologia interna.

Assim, a convencao recomendada neste repositório passa a ser:

- `id-*` para a API;
- `oidc-*` para o servidor de autorizacao.

## Estado atual x estado alvo

Este guia mistura dois planos diferentes e eles nao devem ser confundidos:

- estado atual do runtime local: todos os ambientes ja usam o realm unico `eickrono`;
- estado alvo de DNS/hostnames: publicacao canonica sob `eickrono.com`;
- estado transitorio de implantacao: `dev` e `stg` podem usar `eickrono.online` e `eickrono.store` ate a publicacao equivalente em `eickrono.com`.

Neste documento:

- exemplos de `localhost` representam o estado atual local puro;
- exemplos com dominios publicos `oidc-dev.eickrono.online` e `oidc-stg.eickrono.store` representam a fase transitoria;
- o alvo canonico final correspondente e:
  - `oidc-dev.eickrono.com`
  - `oidc-stg.eickrono.com`
  - `oidc.eickrono.com`

## Por que este guia existe

No ambiente local, o Google aceitou `localhost` como `redirect URI` do OAuth web, mas rejeitou:

- IP privado da rede local, como `http://192.168.0.49:8080/...`;
- hosts sem dominio publico valido.

Na pratica:

- `localhost` serve para testes no navegador do proprio Mac;
- `localhost` nao serve para o iPhone fisico;
- para o iPhone fisico, o broker do Keycloak precisa ser exposto por um dominio publico valido.

Como este ambiente local nao depende de IP publico fixo, a estrategia escolhida foi `Cloudflare Tunnel`.

## Estado atual

Em `2026-04-11`, o binario `cloudflared` foi instalado nesta maquina.

- versao instalada: `2026.3.0`
- arquitetura: `darwin arm64`
- caminho instalado: `/opt/homebrew/bin/cloudflared`
- origem da release: `https://github.com/cloudflare/cloudflared/releases/tag/2026.3.0`

## O que foi feito nesta maquina

### 1. Confirmacao do ambiente local

Foi confirmado:

- macOS em `arm64`;
- `Homebrew` disponivel;
- `cloudflared` ainda nao estava instalado;
- nao existia `~/.cloudflared`.

### 2. Confirmacao da release oficial mais recente

A release oficial consultada foi:

```text
2026.3.0
https://github.com/cloudflare/cloudflared/releases/tag/2026.3.0
```

### 3. Download e instalacao do binario

Os comandos executados foram:

```bash
TMPDIR=$(mktemp -d)
cd "$TMPDIR"
curl -fL \
  "https://github.com/cloudflare/cloudflared/releases/download/2026.3.0/cloudflared-darwin-arm64.tgz" \
  -o cloudflared-darwin-arm64.tgz
tar -xzf cloudflared-darwin-arm64.tgz
install -m 0755 cloudflared /opt/homebrew/bin/cloudflared
/opt/homebrew/bin/cloudflared --version
rm -rf "$TMPDIR"
```

### 4. Validacao da instalacao

O retorno observado foi:

```text
cloudflared version 2026.3.0 (built 2026-03-09-14:09 UTC)
```

## O que voce precisa fazer manualmente

O passo manual preferido para este projeto e:

```bash
cloudflared tunnel login
```

Esse comando:

- abre o navegador;
- pede autenticacao na sua conta Cloudflare;
- pede para escolher a zona/dominio que o tunnel podera usar;
- grava a credencial local em `~/.cloudflared/`.

### Comando de ajuda confirmado

```bash
cloudflared tunnel login --help
```

Sintese do que foi validado:

- o comando existe e esta funcional na instalacao atual;
- o login padrao usa o fluxo web da propria Cloudflare.

### Comportamento observado nesta maquina

No fluxo executado em `2026-04-11`, houve um detalhe importante:

- a autorizacao no navegador concluiu normalmente;
- o `cloudflared` nao conseguiu gravar `~/.cloudflared/cert.pem` automaticamente;
- o navegador baixou o arquivo manualmente em `~/Downloads/cert.pem`;
- foi necessario copiar esse arquivo para `~/.cloudflared/cert.pem`.

Comando efetivamente usado:

```bash
install -m 600 ~/Downloads/cert.pem ~/.cloudflared/cert.pem
```

Isso deixou a maquina apta a executar `cloudflared tunnel list`, `create`, `route dns` e `run`.

## Sequencia completa depois do login

Depois que o `cloudflared tunnel login` for concluido, a sequencia recomendada e esta.

### 1. Criar o tunnel

Exemplo:

```bash
cloudflared tunnel create oidc-dev
```

Esse comando devolve:

- o `UUID` do tunnel;
- um arquivo JSON de credenciais em `~/.cloudflared/<UUID>.json`.

### 2. Criar o DNS publico do tunnel

Exemplo:

```bash
cloudflared tunnel route dns oidc-dev oidc-dev.eickrono.online
```

Para staging, a mesma ideia ficaria:

```bash
cloudflared tunnel route dns oidc-stg oidc-stg.eickrono.store
```

### 3. Criar a configuracao local do tunnel

Arquivo esperado:

```text
~/.cloudflared/config.yml
```

Exemplo:

```yaml
tunnel: <UUID_DO_TUNNEL>
credentials-file: /Users/thiago/.cloudflared/<UUID_DO_TUNNEL>.json

ingress:
  - hostname: oidc-dev.eickrono.online
    service: http://localhost:8080
  - service: http_status:404
```

### 4. Subir o tunnel

```bash
cloudflared tunnel run oidc-dev
```

Ou, se preferir, rodar usando o arquivo de configuracao:

```bash
cloudflared tunnel run
```

Observacao operacional:

- esse processo precisa permanecer ativo enquanto o login social em `dev` estiver sendo testado;
- hoje isso e pre-requisito direto para `Facebook Login`, porque a Meta redireciona para `https://oidc-dev.eickrono.online/realms/eickrono/broker/facebook/endpoint`;
- se o tunnel cair, o hostname publico passa a falhar e o fluxo do broker deixa de funcionar.

### 5. Validar o Keycloak pelo dominio publico

Exemplo:

```bash
curl -s https://oidc-dev.eickrono.online/realms/eickrono/.well-known/openid-configuration
```

O resultado esperado e o `issuer` apontando para o dominio publico do tunnel.

No estado transitorio deste guia, isso significa `oidc-dev.eickrono.online`.
No alvo canonico final, a mesma validacao deve ocorrer com `oidc-dev.eickrono.com`.

Sinais de falha comuns:

- se esse `curl` responder `530`, o hostname publico ainda existe na Cloudflare, mas o `cloudflared tunnel run oidc-dev` nao esta conectado a uma origem valida naquele momento;
- nesse caso, confirme que o Keycloak local responde em `http://localhost:8080/realms/eickrono/.well-known/openid-configuration`;
- se o Keycloak local estiver saudavel e o host publico seguir em `530`, suba novamente o tunnel com `cloudflared tunnel run oidc-dev` e repita o `curl` ate voltar `200`.

Para o fluxo `facebook` atual, uma validacao adicional util e:

```bash
curl -I 'https://oidc-dev.eickrono.online/realms/eickrono/protocol/openid-connect/auth?client_id=app-flutter-local&redirect_uri=com.eickrono.thimisu%3A%2Foauth2redirect&response_type=code&scope=openid%20identidade%3Aler%20vinculos%3Aler%20vinculos%3Aescrever%20offline_access&state=diag&nonce=diag&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256&kc_idp_hint=facebook'
```

O comportamento esperado e um `302` ou `303` para `/broker/facebook/login`.

Se o app Flutter for executado com `CONFIG_OIDC_ISSUER=https://oidc-dev.eickrono.online/realms/eickrono`, esse `530` aparece no simulador ou no aparelho como falha de discovery OIDC antes mesmo de abrir o login social. Nesse cenario, o problema nao esta no app: ele esta no hostname publico/tunnel.

## O que precisa mudar no Google OAuth

Depois que o tunnel e o dominio publico estiverem prontos, o cliente `Web application` do Google deve usar esta callback:

```text
https://oidc-dev.eickrono.online/realms/eickrono/broker/google/endpoint
```

Para `stg`, a mesma regra ficaria:

```text
https://oidc-stg.eickrono.store/realms/eickrono/broker/google/endpoint
```

Observacoes:

- `localhost` pode continuar cadastrado como callback adicional para testes no navegador do Mac;
- IP privado, como `192.168.0.49`, nao deve ser usado no Google OAuth web.
- quando `dev` e `stg` migrarem para o dominio principal, as callbacks devem ser regravadas para `oidc-dev.eickrono.com` e `oidc-stg.eickrono.com`.

## O que precisa mudar no app

Quando o tunnel estiver pronto, o `issuer` do app Flutter nao deve mais apontar para `127.0.0.1` ou `192.168.x.x` para o fluxo social.

O valor esperado hoje em `dev` passa a ser:

```text
https://oidc-dev.eickrono.online/realms/eickrono
```

Isso precisa ficar coerente com:

- o host publico que o Google vai redirecionar;
- o host pelo qual o iPhone acessa o Keycloak.

Se a API de identidade tambem precisar de hostname publico proprio no ambiente, a convencao paralela fica:

```text
https://id-dev.eickrono.online/
```

## Por que o `/realms/<realm>` continua necessario

Mesmo com um dominio separado por ambiente, como `oidc-dev.eickrono.online` e `oidc-stg.eickrono.store`, o path do realm continua obrigatorio.

No Keycloak, o realm faz parte estrutural do `issuer` e dos endpoints OIDC:

- `https://oidc-dev.eickrono.online/realms/eickrono`
- `https://oidc-stg.eickrono.store/realms/eickrono`

O dominio separa o ambiente na camada de rede.
O realm separa a configuracao OIDC dentro do servidor de autorizacao.

Na pratica, hoje o realm ainda concentra:

- usuarios;
- clientes;
- scopes;
- roles;
- brokers sociais;
- flows e politicas.

Entao, neste projeto, trocar o dominio nao elimina o realm.

Para remover o realm do caminho seria necessario redesenhar a topologia do servidor de autorizacao, por exemplo com outra camada de proxy/rewrite ou com outra estrategia de distribuicao do Keycloak. Isso nao faz parte do escopo atual.

Observacao:

- o runtime atual ja responde com `realm=eickrono`;
- enquanto `dev` e `stg` ainda estiverem na fase transitoria, os hosts publicos podem usar `eickrono.online` e `eickrono.store`;
- o destino final recomendado continua sendo `eickrono.com` para todos os ambientes.

## O que ja foi executado neste guia

No `dev`, ja foi executado:

- download da release oficial;
- instalacao do binario;
- validacao da versao;
- `cloudflared tunnel login`;
- copia manual do `cert.pem` baixado pelo navegador;
- `cloudflared tunnel create oidc-dev`;
- `cloudflared tunnel route dns oidc-dev oidc-dev.eickrono.online`;
- criacao de `~/.cloudflared/config.yml`;
- subida do tunnel `oidc-dev`;
- ajuste do Keycloak de `dev` para emitir `https://oidc-dev.eickrono.online/realms/eickrono`;
- validacao do discovery publico.

Ainda nao foi executado neste guia:

- migracao de `dev` para `oidc-dev.eickrono.com`;
- configuracao equivalente de `stg`;
- endurecimento operacional do tunnel como servico persistente.

## Execucao do app Flutter

Para simulador ou execucao local pura:

```bash
flutter run
```

No `app_config.dev.json` padrao, `dev` usa `http://127.0.0.1:8080/realms/eickrono`. Para validar este guia de tunnel, sobrescreva explicitamente o issuer com `--dart-define=CONFIG_OIDC_ISSUER=https://oidc-dev.eickrono.online/realms/eickrono`.
Se voce quiser voltar temporariamente ao Keycloak local em `http://127.0.0.1:8080/realms/eickrono`, sobrescreva:

```bash
flutter run --dart-define=CONFIG_OIDC_ISSUER=http://127.0.0.1:8080/realms/eickrono
```

Para iPhone fisico com APIs locais no Docker e OIDC publico no tunnel:

```bash
flutter run -d <device-id> \
  --dart-define=CONFIG_HOST_LOCAL=192.168.0.49
```

Detalhes:

- `CONFIG_HOST_LOCAL` troca apenas os hosts locais das APIs para o IP do Mac;
- `CONFIG_OIDC_ISSUER` continua disponivel para override manual quando necessario;
- por padrao, o `dev` ja usa o issuer OIDC publico do tunnel sem mexer nas URLs HTTP das APIs.

## Proximo passo pratico

Com o tunnel operacional, o passo seguinte passa a ser:

- manter o `cloudflared tunnel run` ativo;
- preencher as credenciais reais do provedor social desejado;
- validar login social no iPhone fisico.
