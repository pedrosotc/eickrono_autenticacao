# Decisão de Nomenclatura dos Repositórios de Serviços

## Objetivo

Fechar a nomenclatura canônica dos serviços do ecossistema de autenticação.

Esta decisão separa claramente:

- nome do repositório
- `artifactId` raiz do projeto
- nomes físicos dos módulos internos
- `groupId` Maven
- pacote Java raiz

## Regra base

O grupo organizacional e técnico compartilhado continua sendo:

- `groupId`: `com.eickrono`
- pacote Java raiz por serviço: `com.eickrono.*`

Ou seja: os projetos podem ser separados em repositórios independentes sem
perder a identidade comum do grupo `com.eickrono`.

## Nomes canônicos aprovados dos repositórios e runtimes

| Serviço | Repositório canônico | `artifactId` raiz canônico | Pacote Java raiz canônico |
| --- | --- | --- | --- |
| Servidor de identidade/autenticação | `eickrono-identidade-servidor` | `eickrono-identidade-servidor` | `com.eickrono.identidade` |
| Servidor de contas | `eickrono-contas-servidor` | `eickrono-contas-servidor` | `com.eickrono.contas` |
| Servidor de autorização / Keycloak customizado | `eickrono-autenticacao-servidor` | `eickrono-autenticacao-servidor` | `com.eickrono.autorizacao` |

## Nomes físicos aprovados dos módulos internos deste repositório

Enquanto `eickrono-autenticacao-servidor` ainda concentrar dois componentes
Java separados no mesmo workspace, os nomes físicos aprovados passam a ser:

| Papel | Diretório físico | `artifactId` do módulo |
| --- | --- | --- |
| API pública/autenticada de autenticação | `modulos/modulo-eickrono-autenticacao` | `modulo-eickrono-autenticacao` |
| Provider, SPI e empacotamento customizado do Keycloak | `modulos/modulo-eickrono-keycloak` | `modulo-eickrono-keycloak` |

Regra de leitura:

- `modulo-eickrono-autenticacao` e `modulo-eickrono-keycloak` são nomes
  físicos de módulo e de `artifactId`;
- `eickrono-autenticacao` e `eickrono-keycloak` continuam sendo nomes
  operacionais de serviço/container onde o runtime ainda usa essa nomenclatura;
- o nome do repositório não muda por causa dessa organização interna.

## O que permanece igual

- `groupId` compartilhado: `com.eickrono`
- a ideia de que os serviços pertencem ao mesmo ecossistema
- a comunicação entre serviços por HTTP, backchannel, JWT de serviço e `mTLS`

## Nomenclatura canônica dos namespaces de Secrets Manager

Para segredos operacionais da infraestrutura AWS, a leitura correta passa a ser:

- nome de cliente OAuth2/OIDC e nome de serviço não devem ser fundidos no
  mesmo token nominal do segredo;
- o namespace deve deixar explícito se o segredo pertence a:
  - um cliente do Keycloak;
  - um usuário administrativo do Keycloak;
  - um serviço de runtime;
  - um segredo compartilhado do ambiente.

Padrão canônico aprovado para novos segredos:

```text
/eickrono/<ambiente>/<dominio>/<categoria>/<identificador>/<tipo>
```

Onde:

- `<ambiente>`: `dev`, `hml`, `prod`
- `<dominio>`: `keycloak`, `auth`, `identidade`, `shared`, `thimisu-backend`
- `<categoria>`: `clientes`, `admin`, `smtp`, `mtls`, `jwt-interno`,
  `device-token`, `codigo-token`
- `<identificador>`: nome semântico do recurso, sem misturar papéis
- `<tipo>`: `secret`, `password`, `username`, `keystore-password`,
  `truststore-password`

Exemplos canônicos:

- cliente interno do domínio de autenticação:
  `/eickrono/hml/keycloak/clientes/autenticacao-servidor/secret`
- cliente interno do módulo Keycloak:
  `/eickrono/hml/keycloak/clientes/eickrono-keycloak/secret`
- cliente interno do backend do Thimisu:
  `/eickrono/hml/keycloak/clientes/thimisu-backend/secret`
- cliente móvel do app Flutter:
  `/eickrono/hml/keycloak/clientes/app-flutter-hml/secret`
- usuário administrador do Keycloak:
  `/eickrono/hml/keycloak/admin/password`
- credencial SMTP da identidade:
  `/eickrono/hml/identidade/smtp/primario/username`
  `/eickrono/hml/identidade/smtp/primario/password`
- segredo compartilhado interno:
  `/eickrono/hml/shared/jwt-interno/autenticacao/secret`

Regra prática:

- `clientes/<identificador>/secret` é o formato preferencial para segredos de
  cliente do Keycloak;
- o `<identificador>` deve refletir o `client_id` real usado no realm;
- nomes misturando `client` com `servidor` fora do `client_id` ficam proibidos
  como namespace canônico.

### Namespaces materializados em `hml`

| Namespace | Client ID | Para que serve | Consumidores |
| --- | --- | --- | --- |
| `/eickrono/hml/keycloak/clientes/autenticacao-servidor/secret` | `autenticacao-servidor` | token/backchannel interno entre autenticação e identidade | `identidade-hml`, `auth-hml` |
| `/eickrono/hml/keycloak/clientes/eickrono-keycloak/secret` | `eickrono-keycloak` | operações internas do módulo Keycloak/customizações | `auth-hml` |
| `/eickrono/hml/keycloak/clientes/thimisu-backend/secret` | `thimisu-backend` | backchannel JWT interno do `thimisu-backend` | `thimisu-backend-hml`, `auth-hml` |

## O que deve deixar de existir

- ambiguidade entre nome de módulo e nome de serviço;
- documentação que trate `eickrono-autenticacao` como nome físico do
  módulo Maven;
- documentação que trate `modulo-eickrono-keycloak` como nome de serviço
  Docker.

## Regra de dependência entre projetos

Depois da extração:

- os serviços não devem conversar entre si como módulos Maven irmãos;
- os serviços não devem depender do `jar` uns dos outros como regra normal;
- a integração canônica entre eles continua sendo por contrato de rede e
  autenticação interna;
- só vale criar dependência Maven compartilhada se surgir uma biblioteca
  realmente reutilizável e independente de serviço.

## Mapeamento do estado atual para o alvo

| Estado atual | Alvo |
| --- | --- |
| repositório `eickrono-autenticacao-servidor` | repositório central de autenticação/autorização com infraestrutura operacional |
| `pom.xml` da raiz | agregador fino transitório dos módulos internos de autenticação |
| `modulos/modulo-eickrono-autenticacao` | borda pública/autenticada enquanto a convergência arquitetural não termina |
| `modulos/modulo-eickrono-keycloak` | provider e empacotamento do Keycloak customizado |
| `eickrono-identidade-servidor` e `eickrono-contas-servidor` | repositórios externos e independentes |

## Próximas etapas esperadas

1. Extrair `api-identidade-eickrono` para `eickrono-identidade-servidor` — concluído
2. Extrair `api-contas-eickrono` para `eickrono-contas-servidor` — concluído
3. Padronizar os módulos internos remanescentes em `modulos/` — concluído
4. Renomear os módulos físicos para `modulo-eickrono-autenticacao` e `modulo-eickrono-keycloak` — concluído
5. Reajustar `docker-compose`, scripts, caminhos absolutos, CI e documentação — concluído nesta passada
