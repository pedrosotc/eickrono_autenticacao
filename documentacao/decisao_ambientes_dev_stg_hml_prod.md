# Decisao de Ambientes: dev, stg, hml e prod

## Decisao vigente

Em 2026-06-24, o ambiente compartilhado que estava nomeado como `hml` na AWS
foi renomeado para `stg`.

Essa renomeacao corrige a ambiguidade anterior: o ambiente existente nao era a
homologacao final. Ele representa staging.

Decisao complementar: a partir desta revisao, `hml` volta a existir, mas com
outro significado. `hml` agora e o ambiente de homologacao local/simulado,
executado no computador do desenvolvedor e isolado do `stg` da AWS.

O `hml` novo nao e a AWS antiga. Ele copia o que for util do desenho de `stg`,
mas troca dependencias externas por recursos locais ou simulados.

## Nomes canonicos

| Nome | Uso |
| --- | --- |
| `dev` | desenvolvimento |
| `hml` | homologacao local/simulada |
| `stg` | staging na AWS |
| `prod`/`prd` | producao |

## Regra operacional

Nao crie nomes como `hml-local` ou `stg-local`.

O nome canonico do ambiente local de homologacao e `hml`.

Docker, Kubernetes local, LocalStack, emuladores ou qualquer ferramenta parecida
sao formas de execucao local. No caso de homologacao local, a forma de execucao
local pertence ao ambiente `hml`.

Quando o compose usa a pasta `infraestrutura/stg`, a leitura correta e:

- ambiente: `stg`;
- alvo: staging na AWS ou runtime equivalente de staging;
- bancos: bancos separados com sufixo `_stg`;
- objetivo: validar o runtime de staging.

Quando o compose usa a pasta `infraestrutura/hml`, a leitura correta e:

- ambiente: `hml`;
- forma de execucao: Docker no computador do desenvolvedor;
- bancos: bancos separados com sufixo `_hml`;
- objetivo: homologacao local com mocks/simulacoes para o que nao existir fora
  da AWS.

Mocks e simulacoes atuais do `hml`:

- e-mail via MailHog;
- PostgreSQL local compartilhado, mas em bancos `_hml` separados por servico:
  `keycloak_hml`, `eickrono_autenticacao_hml`,
  `eickrono_identidade_hml`, `eickrono_contas_hml` e
  `eickrono_thimisu_hml`;
- Keycloak local com `hml-realm.json`;
- avatar/storage em modo local, com volume Docker `identidade_avatar_hml` e
  endpoint publico local `http://localhost:19084/identidade/avatares/publicos`;
- validacao oficial de atestacao desligada, com validacao local permitida;
- provedores sociais com credenciais placeholder quando nao houver credencial
  real local.

## Regra para pastas e arquivos

- caminho de staging: `infraestrutura/stg`;
- caminho de homologacao local: `infraestrutura/hml`;
- profile Spring de staging: `stg`;
- profile Spring de homologacao local: `hml`;
- arquivo de configuracao do app: `app_config.stg.json`;
- arquivo de configuracao do app HML local: `app_config.hml.json`;
- realm de staging: `staging-realm.json`;
- realm HML local: `hml-realm.json`;
- scripts e task definitions de staging devem usar `stg`;
- scripts locais de homologacao devem usar `hml`;
- artefatos AWS continuam usando `stg` para staging e `prod`/`prd` para
  producao.

Arquivos de assinatura Android guardados no computador do desenvolvedor devem
ser lidos apenas como material de assinatura da variante correspondente. Eles
nao definem um ambiente novo.

## Historico e ponto de retorno

Antes da renomeacao textual desta passada, os HEADs de referencia eram:

| Repositorio | Branch | HEAD |
| --- | --- | --- |
| `eickrono-autenticacao-servidor` | `push-main-clean-v2` | `fdb1c7867d39` |
| `eickrono-identidade-servidor` | `main` | `56de4957a61b` |
| `eickrono-thimisu-app` | `main` | `1940119686bb` |
| `eickrono-thimisu-backend` | `main` | `1b4674d13fb6a0ddac38a0dda3fd8820fb2a8f9b` |
| `eickrono-contas-servidor` | `main` | `6519a59c4a1ae839892e0c502ebbe5fb0ebfe715` |
| `eickrono-autenticacao-cliente` | `main` | `ac37fab79fb4e7a016f9aa9a4e572d24399a4f59` |
| `eickrono-postgres-dev` | `main` | `3626c0a738017b2260685cfbf48213c919f59dc0` |

Os worktrees ja tinham alteracoes locais antes desta renomeacao. Portanto, para
investigar regressao, use o HEAD acima junto com o diff local existente antes da
renomeacao, registrado no contexto de continuidade da conversa.
