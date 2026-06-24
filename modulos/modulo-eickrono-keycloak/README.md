# Modulo Eickrono Keycloak

Esta pasta contém customizações do Keycloak/RH-SSO utilizadas pela Eickrono:

- `temas-login-ptbr`: temas de login totalmente em português.  
- `mapeamentos-atributos`: mapeamentos adicionais de claims e atributos.  
- `configuracoes-fapi`: políticas de PAR/JAR/JARM, mTLS e client policies.  
- `realms`: exports versionados por ambiente, todos com o nome lógico de realm `eickrono`.  
- `scripts-spi`: código Java/JS para integrações via SPI.

O módulo gera um JAR com utilidades compartilhadas e serve como ponto de empacotamento para as customizações do servidor de autorização.

## Papel na arquitetura móvel canônica

No fluxo móvel atual:

- a borda pública final do app pertence ao domínio de `autenticacao`, não a
  este módulo diretamente;
- este módulo sustenta a parte de Keycloak/RH-SSO do ecossistema, com credenciais, tokens, `required actions`, políticas de cliente e refresh;
- a `identidade` deve ficar apenas em backchannel interno quando ainda for
  necessária durante a migração;
- login, recuperação de senha e demais fluxos sensíveis continuam
  centralizados na autenticação.

Observação estrutural:

- a existência atual de `modulo-eickrono-autenticacao` neste repositório
  deve ser lida como estado físico transitório do código;
- qualquer referência a `api-contas-eickrono` neste repositório deve ser lida
  como histórico de extração ou como cliente técnico do Keycloak;
- a arquitetura alvo aprovada continua sendo `eickrono-autenticacao-servidor`
  como projeto simples, central e genérico, sem módulo interno de `contas`.

## SPI de senha derivada

Este módulo também fornece providers customizados para derivar a senha efetiva usada pelo Keycloak a partir de:

- `senha informada pelo usuario`
- `pepper` lido da variável de ambiente `EICKRONO_PASSWORD_PEPPER`
- `createdTimestamp` interno do usuário no Keycloak

Fórmula aplicada:

- `senha_efetiva = senha + pepper + createdTimestamp`

Providers registrados:

- `eickrono-username-password-form`
- `eickrono-registration-password`
- `eickrono-update-password`

Observação:

- os realms deste repositório já ligam os providers aos fluxos de browser, registro e `requiredActions`;
- o alias `UPDATE_PASSWORD` do realm aponta para `eickrono-update-password`, então reset de senha e required action passam pela derivação customizada;
- o `createdTimestamp` é garantido na criação do usuário e, se necessário, é materializado pela própria SPI antes da primeira gravação da credencial;
- a derivação usa apenas o `createdTimestamp` nativo do usuário; atributos livres como `data_criacao_conta` não participam mais desse cálculo;
- o `docker-compose` monta o JAR gerado em `/opt/keycloak/providers/modulo-eickrono-keycloak.jar`, então é preciso empacotar o módulo antes de subir o ambiente.

Empacotamento:

```bash
mvn -pl modulo-eickrono-keycloak -am package -DskipITs
```

## Client policy de refresh por device token

Este módulo também publica o executor de client policy `eickrono-device-token-refresh`.

Responsabilidade:

- interceptar `grant_type=refresh_token`;
- exigir o parâmetro adicional `device_token` no refresh;
- consultar a API pública de autenticação em `/api/conta/dispositivos/token/validacao/interna`;
- bloquear o refresh quando o dispositivo perdeu confiança.

Configuração esperada no ambiente do Keycloak:

- `EICKRONO_AUTENTICACAO_API_BASE_URL`
- `EICKRONO_INTERNAL_SECRET`
- `EICKRONO_IDENTIDADE_TIMEOUT_MS`
- `EICKRONO_INTERNO_MTLS_HABILITADO`
- `EICKRONO_INTERNO_MTLS_KEYSTORE_ARQUIVO`
- `EICKRONO_INTERNO_MTLS_KEYSTORE_SENHA`
- `EICKRONO_INTERNO_MTLS_TRUSTSTORE_ARQUIVO`
- `EICKRONO_INTERNO_MTLS_TRUSTSTORE_SENHA`

Observação de `mTLS`:

- este módulo usa `mTLS` como cliente ao consultar a API de identidade no refresh por `device token`;
- ele não sobe um endpoint próprio com `mTLS`;
- o detalhamento completo do fluxo e da geração de certificados está em `../../documentacao/guia-mtls.md`.

Os realms versionados já saem com:

- `clientProfiles` contendo `eickrono-device-token-refresh-profile`;
- `clientPolicies` contendo `eickrono-device-token-refresh-policy`;
- o cliente `app-flutter-local` marcado com `eickrono.device-token-refresh=true`.

## Provedores sociais versionados

Os exports versionados por ambiente agora carregam cinco brokers de identidade com aliases estáveis:

- `google` com `providerId=google`
- `apple` com `providerId=oidc`
- `facebook` com `providerId=facebook`
- `linkedin` com `providerId=linkedin-openid-connect`
- `instagram` com `providerId=instagram`

Observações operacionais:

- A documentação do módulo passa a adotar o padrão `KEYCLOAK_IDP_<APP>_<PROVEDOR>_<CREDENCIAL>`, usando `THIMISU` como slug de app nos exemplos operacionais.
- Apple entra como broker OIDC genérico e usa `clientSecret` no formato JWT, documentado na convenção `KEYCLOAK_IDP_<APP>_APPLE_CLIENT_SECRET_JWT`.
- Facebook segue o broker social nativo do Keycloak com `defaultScope=email`, o suficiente para o fluxo atual de autenticação social.
- Instagram continua no broker `instagram`, com `defaultScope=user_profile`, e permanece deprecated no Keycloak 26.5.5. Para o import aceitar `providerId=instagram`, o servidor precisa subir com `--features=instagram-broker`.
- O setup atual de `Instagram Business Login` / Graph API da Meta não está materializado nesses exports. Publicação, mensageria e permissões como `instagram_business_*` ou `pages_*` pedem integração dedicada no servidor, não apenas ajuste de broker social.
- Por causa disso, o app Flutter esconde temporariamente o caminho visual de `Instagram` e mantém apenas `Facebook` como login Meta exposto ao usuário final.
- Os valores `${KEYCLOAK_IDP_<APP>_*}` nos realms são placeholders de credencial. Em `infraestrutura/dev` e `infraestrutura/stg`, o startup do Keycloak passa por `render-realms.sh`, que substitui apenas esses placeholders antes do `--import-realm`.
- A renderização é seletiva de propósito: placeholders nativos do próprio Keycloak como `${username}` precisam continuar literais dentro do realm export.
- Os exemplos de `.env` em `infraestrutura/dev` e `infraestrutura/stg` já listam as chaves esperadas para facilitar esse preenchimento.

Teste do módulo:

```bash
mvn -pl modulo-eickrono-keycloak -am test
```
