# Guia de mTLS

Este guia consolida como o `mTLS` funciona no repositório
`eickrono-autenticacao-servidor`, quais módulos realmente o usam hoje, quais
certificados cada um consome e como gerar os artefatos locais de `dev` e
`stg`.

## Nota de nomenclatura operacional

Os nomes `eickrono-autenticacao`, `identidade-servidor`,
`api-contas-eickrono` e arquivos como `api-identidade-eickrono.p12` aparecem
aqui como **nomes operacionais atuais** de serviços, containers, endpoints e
certificados.

Eles não significam que:

- `eickrono-autenticacao-servidor` deva permanecer monorepo por domínio;
- esses nomes sejam a fronteira arquitetural final aprovada do ecossistema.

Regra complementar:

- `modulo-eickrono-autenticacao` e `modulo-eickrono-keycloak` são os nomes
  físicos aprovados dos módulos internos deste repositório;
- `eickrono-autenticacao` e `eickrono-keycloak` continuam sendo nomes de
  runtime/`docker compose`.

## Visão geral

No estado atual do código, o `mTLS` é usado para proteger o `backchannel` entre serviços internos. Ele não substitui o JWT interno nem o header `X-Eickrono-Internal-Secret`.

Para a arquitetura canônica do app móvel, a direção mais importante desse `backchannel` passa a ser:

- `autenticação -> identidade` para criar ou atualizar a `Pessoa` canônica;
- `autenticação -> backend do produto` para criar ou atualizar o perfil daquele sistema.

Camadas reais do desenho:

- `mTLS`: autentica o par cliente-servidor no transporte.
- `JWT interno`: autentica a chamada na camada de aplicação.
- `X-Eickrono-Internal-Secret`: barreira adicional para rotas internas.

Serviços com participação em `mTLS` no ecossistema atual:

- `eickrono-autenticacao`: servidor e cliente `mTLS`.
- `identidade-servidor`: servidor e cliente `mTLS`.
- `modulo-eickrono-keycloak`: cliente `mTLS` no fluxo de refresh por `device token`.
- `api-contas-eickrono`: suporte de servidor `mTLS`, mas sem uso ativo no `docker-compose` atual e sem cliente `mTLS`.

## modulo-eickrono-autenticacao / serviço eickrono-autenticacao

### Responsabilidade no mTLS

- expõe rotas internas que podem ser consumidas por outros serviços via `backchannel`;
- aceita certificado do cliente quando a porta `mTLS` está habilitada;
- também atua como cliente `mTLS` ao chamar `identidade-servidor` e, quando
  necessário, o backend do produto por HTTPS interno.

### Configuração

As propriedades são carregadas por `seguranca.mtls`:

- `habilitado`
- `porta-interna`
- `keystore-arquivo`
- `keystore-senha`
- `truststore-arquivo`
- `truststore-senha`

Comportamento por ambiente:

- `application.yml`: nasce desligado por padrão.
- `application-stg.yml`: marcado como ligado.
- `application-prod.yml`: marcado como ligado.

No runtime via `docker-compose`, `dev` e `stg` sobrescrevem os caminhos para `file:/certificados/...`, que é o que realmente vale no container.

### Como o servidor sobe

Quando `seguranca.mtls.habilitado=false`, a API sobe apenas com o conector HTTP normal.

Quando `seguranca.mtls.habilitado=true`:

- a configuração valida `keystore` e `truststore`;
- se `porta-interna > 0`, o Tomcat cria um conector HTTPS adicional para o canal interno;
- esse conector exige certificado do cliente com `clientAuth=true`;
- a porta pública HTTP continua existindo para tráfego normal e Swagger;
- se `porta-interna == 0`, o servidor inteiro passa a exigir `mTLS`.

Na prática atual da estrutura física do repositório:

- em `dev`, a porta pública da autenticação continua em `8081`;
- a porta interna `mTLS` sobe em `8443` dentro do container e é publicada como `18481` no host;
- em `stg`, a mesma lógica vale, mas publicada como `19481`.

### Como o cliente HTTP usa mTLS

O helper `ConfiguradorRestTemplateBackchannelMtls` só ativa `mTLS` quando a `urlBase` da integração usa `https`.

Fluxo do helper:

- recebe `urlBase` e `timeout`;
- se a URL for `http`, devolve um `RestTemplateBuilder` normal;
- se a URL for `https`, exige `seguranca.mtls.habilitado=true`;
- carrega `keystore` e `truststore`;
- monta um `SSLContext` com `KeyManagerFactory` e `TrustManagerFactory`;
- injeta esse `SSLContext` no `HttpClient` usado pelo `RestTemplate`.

Isso significa que o serviço:

- apresenta o próprio certificado ao outro lado;
- valida o certificado apresentado pelo outro lado;
- falha cedo se pedirem HTTPS sem configuração válida de `mTLS`.

### Onde ele é usado hoje

Na autenticação, o uso real do cliente `mTLS` hoje está no `backchannel` para
resolver contexto de pessoa em `identidade-servidor`.

Na arquitetura canônica, esse mesmo mecanismo também é a base para o
provisionamento interno que a autenticação faz em duas etapas:

1. para a identidade, ao criar ou atualizar a `Pessoa` canônica;
2. para o backend do produto, ao criar ou atualizar o perfil daquele sistema.

A chamada segue este desenho:

1. monta `RestTemplate` com `SSLContext` se a URL do perfil for HTTPS;
2. obtém um JWT interno por `client_credentials`;
3. envia `Authorization: Bearer ...`;
4. envia `X-Eickrono-Internal-Secret`;
5. envia a requisição HTTPS já autenticada por certificado.

Observação importante:

- a obtenção do JWT interno no Keycloak não usa o helper de `mTLS`; o `mTLS` aqui protege o `backchannel` entre serviços, não toda chamada feita pelo módulo.

## modulo-eickrono-keycloak

### Responsabilidade no mTLS

Este módulo não sobe um servidor HTTP Spring para receber `mTLS`. Ele roda
dentro do Keycloak e usa `mTLS` apenas como cliente quando valida `device
token` na API pública de autenticação durante o refresh.

### Configuração

A SPI lê estas variáveis:

- `EICKRONO_INTERNO_MTLS_HABILITADO`
- `EICKRONO_INTERNO_MTLS_KEYSTORE_ARQUIVO`
- `EICKRONO_INTERNO_MTLS_KEYSTORE_SENHA`
- `EICKRONO_INTERNO_MTLS_TRUSTSTORE_ARQUIVO`
- `EICKRONO_INTERNO_MTLS_TRUSTSTORE_SENHA`

Além disso, ela depende de:

- `EICKRONO_AUTENTICACAO_API_BASE_URL`
- `EICKRONO_KEYCLOAK_URL_BASE`
- `EICKRONO_AUTENTICACAO_CLIENT_ID_INTERNO`
- `EICKRONO_AUTENTICACAO_CLIENT_SECRET_INTERNO`
- `EICKRONO_INTERNAL_SECRET`

Por compatibilidade de rollout, o provider ainda aceita os nomes legados
`EICKRONO_IDENTIDADE_*` como fallback.

### Como o fluxo funciona

No refresh com `device token`, a SPI:

1. detecta se a URL da autenticação ou do Keycloak está em HTTPS;
2. se alguma estiver em HTTPS, exige `EICKRONO_INTERNO_MTLS_HABILITADO=true`;
3. carrega `keystore` e `truststore`;
4. monta um `HttpClient` Java com `SSLContext`;
5. obtém um JWT interno por `client_credentials`;
6. chama a API pública de autenticação com `Bearer`,
   `X-Eickrono-Internal-Secret`, `X-Device-Token` e `X-Usuario-Sub`.

No `docker-compose` atual:

- a URL do Keycloak interno continua em HTTP;
- a URL da API pública de autenticação interna está em HTTPS;
- então o certificado do `eickrono-keycloak.p12` é usado para o canal até a autenticação.

## api-contas-eickrono

### Responsabilidade no mTLS

O módulo tem suporte de servidor para `mTLS`, mas seu desenho ainda é mais simples do que o da identidade:

- não existe `porta-interna`;
- se o `mTLS` for habilitado, a porta inteira da aplicação passa a exigir certificado de cliente;
- não existe, hoje, helper de cliente HTTP com `mTLS` equivalente ao dos outros módulos.

### Estado atual

No código:

- `application.yml` nasce com `seguranca.mtls.habilitado=false`;
- `application-stg.yml` e `application-prod.yml` marcam `mTLS` como ligado.

No runtime dos ambientes locais:

- `dev`: o `docker-compose` não ativa `mTLS`;
- `stg`: o `docker-compose` ativa explicitamente `SEGURANCA_MTLS_HABILITADO=false`.

Na prática, hoje o `api-contas-eickrono` ainda não participa do
`backchannel mTLS` ativo da stack local padrão.

### Limitação atual

O validador remoto de `device token` usa `RestTemplate` simples. Então:

- ele pode chamar a API de identidade;
- mas essa chamada não usa cliente `mTLS`;
- se o serviço passar a falar com um endpoint interno somente `mTLS`, será
  preciso evoluir esse módulo para seguir o mesmo padrão do
  `eickrono-autenticacao`.

## Geração de certificados

Os scripts oficiais ficam em:

- `infraestrutura/dev/certificados/gerar_certificados.sh`
- `infraestrutura/stg/certificados/gerar_certificados.sh`

Eles geram:

- uma CA interna autoassinada;
- `eickrono-autenticacao.p12`;
- `api-identidade-eickrono.p12`;
- `thimisu-backend.p12`;
- `eickrono-keycloak.p12`;
- `backchannel-truststore.p12`.

### Artefatos gerados

Arquivos principais:

- `backchannel-ca.key`
- `backchannel-ca.crt`
- `backchannel-truststore.p12`
- `eickrono-autenticacao.p12`
- `api-identidade-eickrono.p12`
- `thimisu-backend.p12`
- `eickrono-keycloak.p12`

### Execução em dev

```bash
cd /Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/infraestrutura/dev/certificados
MTLS_KEYSTORE_SENHA=senhaBackchannelDev \
MTLS_TRUSTSTORE_SENHA=senhaBackchannelDev \
./gerar_certificados.sh
```

### Execução em stg

```bash
cd /Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/infraestrutura/stg/certificados
MTLS_KEYSTORE_SENHA=senhaBackchannelStg \
MTLS_TRUSTSTORE_SENHA=senhaBackchannelStg \
./gerar_certificados.sh
```

### O que o script faz

1. remove os artefatos antigos;
2. cria uma CA RSA interna;
3. gera chave privada e CSR por serviço;
4. assina cada CSR com a CA;
5. exporta o material em `PKCS12`;
6. importa a CA no `backchannel-truststore.p12`.

### Recarregar os containers depois da geracao

Se os certificados forem regenerados com a stack ja em execucao, os servicos que
ja estavam no ar continuam com o `keystore` e o `truststore` antigos em
memoria. Isso pode aparecer como falso `PKIX`, mesmo com os arquivos novos ja
gravados em `/certificados`.

Depois de rodar `./gerar_certificados.sh`, recrie os containers que usam `mTLS`:

```bash
cd /Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/infraestrutura/dev
docker compose up -d --force-recreate eickrono-autenticacao identidade-servidor eickrono-keycloak

cd /Users/thiago/Desenvolvedor/flutter/eickrono-thimisu-backend/infraestrutura/dev
docker compose up -d --force-recreate thimisu-backend
```

Em `stg`, use os `docker compose` equivalentes do diretório `infraestrutura/stg`.

### SAN e uso estendido

O script gera:

- `eickrono-autenticacao` com `serverAuth,clientAuth`;
- `api-identidade-eickrono` com `serverAuth,clientAuth`;
- `thimisu-backend` com `serverAuth,clientAuth`;
- `eickrono-keycloak` com `clientAuth`.

Os SANs incluem nomes úteis para container e host local, como:

- `api-identidade-eickrono`
- `eickrono-autenticacao`
- `thimisu-backend`
- `eickrono-keycloak`
- `host.docker.internal`
- `localhost`
- `127.0.0.1`

## Como os certificados entram nos containers

Em `dev` e `stg`, o `docker-compose` monta a pasta `certificados` em `/certificados`.

Exemplos de uso real:

- a autenticação lê `file:/certificados/modulos/modulo-eickrono-autenticacao.p12`;
- a identidade lê `file:/certificados/api-identidade-eickrono.p12`;
- o thimisu lê `file:/certificados/thimisu-backend.p12`;
- a SPI do Keycloak lê `/certificados/eickrono-keycloak.p12`;
- todos confiam na CA via `/certificados/backchannel-truststore.p12`.

## Limitações e observações importantes

- os caminhos `classpath:certificados/...` dos `application-*.yml` não são a fonte real usada no `docker-compose` local; os containers atuais dependem dos arquivos montados em `/certificados`;
- hoje a malha `eickrono-autenticacao <-> identidade-servidor`,
  `identidade-servidor <-> thimisu` e
  `eickrono-keycloak -> eickrono-autenticacao` está realmente usando
  `mTLS` no fluxo local;
- o `api-contas-eickrono` ainda precisa de evolução se for entrar no mesmo padrão;
- as variáveis `SERVIDOR_AUTORIZACAO_MTLS_CERTIFICADO` e `SERVIDOR_AUTORIZACAO_MTLS_SENHA` aparecem nos `docker-compose`, mas não são consumidas pelo código Java atual.
