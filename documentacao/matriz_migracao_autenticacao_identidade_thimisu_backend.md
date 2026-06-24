# Matriz de Migracao entre Autenticacao, Identidade e Thimisu-Backend

> Status deste documento: **transicao assistida**.
>
> Esta matriz ajuda a coordenar nomes, aliases de runtime e passos de cutover.
> Ela nao substitui o documento canônico de responsabilidades.
>
> Para decisoes de ownership entre `autenticacao`, `identidade` e backend de
> produto, a fonte principal passa a ser:
>
> - `consolidado_migracao_autenticacao_identidade_thimisu.md`

Este documento separa o que ja pode ser tratado como nomenclatura canônica
do que ainda continua preso a alias legados de wire, runtime ou infraestrutura.

O objetivo aqui nao e "renomear tudo de uma vez". O objetivo e evitar
quebrar contrato OIDC, `mTLS`, `docker-compose` e provisionamento interno
enquanto o ecossistema termina o cutover para `thimisu-backend`.

## Convencao canônica aprovada

### Superficies e hosts

No desenho atual, existem dois planos de leitura:

- alvo canônico final em `eickrono.com`;
- estado transitorio efetivamente publicado por ambiente, que ainda pode usar
  `eickrono.online` em `dev` e `eickrono.store` em `stg`.

- superficie do produto:
  - `thimisu-dev.eickrono.com`
  - `thimisu-stg.eickrono.com`
  - `thimisu.eickrono.com`
- backend de dominio consumido pelo app e pelo ecossistema:
  - `thimisu-backend-dev.eickrono.com`
  - `thimisu-backend-stg.eickrono.com`
  - `thimisu-backend.eickrono.com`
- borda publica de autenticacao/identidade:
  - `id-dev.eickrono.com`
  - `id-stg.eickrono.com`
  - `id.eickrono.com`
- servidor OIDC:
  - `oidc-dev.eickrono.com`
  - `oidc-stg.eickrono.com`
  - `oidc.eickrono.com`

### Identificadores canônicos de sistema

- nome logico do backend de dominio no ecossistema: `thimisu-backend`
- client id canônico de backchannel JWT: `thimisu-backend`

## Distincao importante

Nem todo nome legado precisa ser trocado na mesma rodada.

Hoje existem quatro classes diferentes de identificador:

1. nome funcional do sistema no dominio interno
2. client id OIDC usado no `client_credentials`
3. audiencia/resource client usada na validacao do JWT
4. alias tecnico de modulo, artefato, container e certificado

Misturar essas quatro coisas na mesma migracao aumenta risco sem gerar ganho
imediato. Por isso a matriz abaixo classifica o estado de cada uma.

## Estado consolidado

| Superficie | Valor atual | Alvo canônico | Estado desta rodada | Proximo passo |
| --- | --- | --- | --- | --- |
| Nome logico do sistema chamador no fluxo interno | `identidade-servidor` em parte do legado | `thimisu-backend` | Concluido no codigo principal. A `autenticacao` ja usa `thimisu-backend` como nome logico de sistema, mantendo apenas compatibilidades residuais onde necessario. | Remover alias legado restante quando nao houver mais consumer antigo. |
| Host publico do backend de dominio | host transitorio depende do ambiente | `thimisu-backend-*` | Convenção canônica continua aprovada, mas `stg` ainda publica host real em `eickrono.store`. | Fechar a migracao final de DNS quando a camada publica consolidar em `eickrono.com`. |
| Realm OIDC em `stg` e `prd` | legado parcialmente divergente | `eickrono` | Concluido no runtime principal dos servicos e nos templates atuais. | Apenas vigiar pontos externos ainda nao reexportados. |
| Client id de backchannel JWT | `identidade-servidor` em parte do legado | `thimisu-backend` | Concluido no desenho principal e aceito nos contratos internos atuais. | Revisar apenas secrets e cadastros antigos de ambiente ainda nao rotacionados. |
| Audience/resource client do backend de dominio | `thimisu-backend` | `thimisu-backend` | Concluido no runtime. | Manter. |
| Certificado `mTLS` do backend de dominio | `thimisu-backend.p12` | `thimisu-backend.p12` | Concluido no runtime. | Manter. |
| Repo, modulo e artifactId | `eickrono-thimisu-backend` / `thimisu-backend` | `thimisu-backend` | Concluido no codigo e na infraestrutura principal. | Manter apenas sincronismo documental e operacional. |

## O que ja foi refatorado com seguranca

### `eickrono-autenticacao-servidor`

- `CadastroInternoController` agora usa `thimisu-backend` como
  `sistemaSolicitante` padrao;
- `IntegracaoInternaProperties` da API de identidade aceita:
  - `thimisu-backend`
  - `identidade-servidor`
  - `eickrono-keycloak`
- `application.yml` da API de identidade passou a carregar a lista de clientes
  internos com o canônico primeiro e o alias legado ainda permitido;
- `V18__seed_catalogo_multiapp_inicial.sql` agora semeia:
  - `thimisu-backend`
  - `identidade-servidor` como alias legado documentado
- os testes de cadastro interno, envio SMTP e atestacao interna ja foram
  alinhados ao nome canônico onde isso nao quebra contrato de wire.

### `eickrono-thimisu-backend`

- a documentacao do projeto ja registra `thimisu-backend-*` como convenio
  canônico para o host publico do backend de dominio;
- a execução do profile `stg` via Docker passou a usar `issuer`/`jwk-set-uri` coerentes com
  `https://oidc-stg.eickrono.store/realms/eickrono`;
- o `docker compose` de `stg` passou a assumir `realm=eickrono` para o
  client interno de backchannel.
- o backend do produto ja expõe:
  - `GET /api/interna/perfis-sistema/contexto`
  - `GET /api/interna/perfis-sistema/disponibilidade`
  - `POST /api/interna/perfis-sistema/provisionamentos`
- o namespace final `/api/interna/identidade/*` ja foi cortado do runtime do
  produto.

### `eickrono-thimisu-app`

- o bootstrap ja aceita sobrescritas independentes de:
  - `CONFIG_AUTENTICACAO_BASE_URL`
  - `CONFIG_THIMISU_BASE_URL`
  - `CONFIG_OIDC_ISSUER`
- isso permite validar ambientes hibridos sem fixar cedo demais um host
  publico errado do backend de dominio.

## O que ainda nao deve ser trocado cegamente

### 1. aliases de ambiente e secrets externos

Ainda aparece em:

- alguns cadastros antigos de ambiente;
- secrets e `client_credentials` ainda nao rotacionados em todos os ambientes;
- documentos historicos que ainda registram a fase de transicao.

Trocar isso sem coordenacao ainda pode quebrar emissao de JWT interno ou
integrações antigas.

### 2. `thimisu-backend`

Ja aparece de forma canonica em:

- `spring.application.name`
- `management.metrics.tags.application`
- `fapi.seguranca.audiencia-esperada`
- exports do Keycloak
- audience mappers
- testes

O nome canônico ja vale tanto para runtime quanto para o modulo principal.

### 3. `thimisu-backend.p12`

Ja aparece de forma canonica em:

- scripts de geracao de certificados `dev` e `stg`
- `.env` do backend de dominio
- guias de `mTLS`
- SAN e alias dos certificados locais

Esse corte ja foi coordenado com os consumers do backchannel local.

## Ordem recomendada da migracao restante

1. manter `thimisu-backend` como nome logico canônico de sistema;
2. concluir a rotacao de secrets e `client_credentials` onde ainda houver
   alias externo antigo;
3. fechar a migracao final de DNS publico para `eickrono.com`, quando a camada
   publica estiver pronta;
4. manter apenas limpeza residual de documentacao e observabilidade.

## Leitura em conjunto

- [Plano de Padronizacao para Realm Unico](plano-padronizacao-realm-unico.md)
- [Backlog Cross-Service de Autenticacao, OIDC e Dispositivo](backlog_cross_service_autenticacao_oidc_dispositivo.md)
- `../eickrono-thimisu-backend/README.md`
