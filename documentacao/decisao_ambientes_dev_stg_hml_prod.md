# Decisao de Ambientes: dev, stg, hml e prod

## Decisao vigente

Em 2026-06-24, o ambiente compartilhado que estava nomeado como `hml` na AWS
foi renomeado para `stg`.

Essa renomeacao corrige a ambiguidade anterior: o ambiente existente nao era a
homologacao final. Ele representa staging.

Nao existia um ambiente local de homologacao. O que existia era `hml` na AWS; a
execucao via Docker no computador do desenvolvedor e apenas uma forma de rodar
componentes para teste.

## Nomes canonicos

| Nome | Uso |
| --- | --- |
| `dev` | desenvolvimento |
| `stg` | staging |
| `hml` | homologacao futura na AWS, ainda nao criada |
| `prod`/`prd` | producao |

## Regra operacional

Nao existe ambiente chamado `hml-local`, `stg-local`, "homologacao local" ou
"staging local".

Docker, Kubernetes local, LocalStack, emuladores ou qualquer ferramenta parecida
sao apenas formas de executar componentes no computador do desenvolvedor. Eles
nao criam um ambiente novo.

Quando o compose usa a pasta `infraestrutura/stg`, a leitura correta e:

- ambiente: `stg`;
- forma de execucao: Docker no computador do desenvolvedor;
- bancos: bancos separados com sufixo `_stg`;
- objetivo: permitir testes do runtime de `stg` sem depender da AWS.

## Regra para pastas e arquivos

- caminho de staging: `infraestrutura/stg`;
- profile Spring de staging: `stg`;
- arquivo de configuracao do app: `app_config.stg.json`;
- realm de staging: `staging-realm.json`;
- scripts e task definitions de staging devem usar `stg`;
- `hml` fica reservado para a homologacao futura e nao deve ser reaproveitado
  para o staging atual.

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
