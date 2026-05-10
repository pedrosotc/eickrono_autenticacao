# Eickrono Autenticação

Esta pasta reúne a documentação canônica do ecossistema de identidade da Eickrono.

## Decisão estrutural aprovada

Para este repositório, a decisão estrutural aprovada é:

- `eickrono-autenticacao-servidor` deve convergir para um **projeto simples**,
  não para um monorepo por domínio;
- ele deve funcionar como **servidor central/singleton** de autenticação do
  ecossistema;
- ele deve atender vários apps, sites e projetos por configuração central, e
  não por “um módulo para cada produto”;
- ele não deve incorporar `contas` como módulo interno de domínio;
- ele não deve pressupor comunicação interna de runtime entre autenticação e
  `contas`.

Estado físico atual:

- o layout físico vigente agrupa os módulos internos em `modulos/`;
- `modulos/modulo-eickrono-autenticacao` concentra a API pública/autenticada;
- `modulos/modulo-eickrono-keycloak` concentra o provider, as SPI e o
  empacotamento customizado do Keycloak;
- ainda existem referências históricas a `api-identidade-eickrono` e nomes
  legados de runtime neste repositório;
- isso deve ser lido como **estado transitório do código e da operação local**,
  não como a arquitetura final desejada.

## Mapa canônico de nomenclatura

| Contexto | Nome |
| --- | --- |
| repositório | `eickrono-autenticacao-servidor` |
| módulo físico da API pública/autenticada | `modulo-eickrono-autenticacao` |
| módulo físico do provider Keycloak | `modulo-eickrono-keycloak` |
| serviço/container da API pública | `eickrono-autenticacao` |
| serviço/container do Keycloak | `eickrono-keycloak` |

Regra prática:

- use `modulo-eickrono-*` para caminho, `pom.xml`, IDE e `artifactId`;
- use `eickrono-autenticacao` e `eickrono-keycloak` apenas quando o
  assunto for runtime, `docker compose`, logs ou operação.

## Mapa canônico de namespaces de segredos

Para `AWS Secrets Manager`, a convenção documental aprovada não deve misturar
papel de cliente com papel de servidor no mesmo nome.

Padrão preferencial:

```text
/eickrono/<ambiente>/<dominio>/<categoria>/<identificador>/<tipo>
```

Atalho prático para o caso mais sensível deste repositório:

- segredos de clientes do Keycloak devem seguir:
  `/eickrono/<ambiente>/keycloak/clientes/<client-id>/secret`

Aplicação vigente em `hml`:

| Namespace | Client ID | Para que serve |
| --- | --- | --- |
| `/eickrono/hml/keycloak/clientes/autenticacao-servidor/secret` | `autenticacao-servidor` | autenticação interna entre `identidade` e o domínio de autenticação |
| `/eickrono/hml/keycloak/clientes/eickrono-keycloak/secret` | `eickrono-keycloak` | autenticação interna do módulo Keycloak/customizações |
| `/eickrono/hml/keycloak/clientes/thimisu-backend/secret` | `thimisu-backend` | autenticação interna do `thimisu-backend` |

Fonte de verdade:

- `decisao_nomenclatura_repositorios_servicos.md`

## Diretriz vigente

Para o app móvel:

- cadastro, confirmação de e-mail, login, refresh e recuperação de senha devem
  convergir para a borda pública final de `autenticacao`;
- a autenticação continua dona da conta central, das credenciais e das liberações internas;
- a identidade continua dona da `Pessoa` canônica;
- o thimisu recebe apenas o provisionamento do perfil daquele sistema depois que conta central e `Pessoa` já tiverem sido resolvidas;
- o `X-Device-Token` canônico nasce no próprio login público da autenticação;
- qualquer explicação antiga centrada em navegador, OIDC interativo no app ou autenticação pública via thimisu deve ser considerada legada.

## Diretriz de nomenclatura

Nesta documentação, quando o conceito servir para todo o ecossistema, devem
ser usados nomes gerais e não nomes de produto.

Regra prática:

- preferir termos como `cliente`, `sistema`, `vinculo`, `perfilSistema` e
  equivalentes;
- evitar termos centrados em um produto específico quando a regra vale para
  vários apps, sites ou softwares;
- manter nome de produto apenas quando o comportamento for realmente exclusivo
  daquele produto.

## Guias principais

- `guia-arquitetura.md`: papel de cada serviço, contratos canônicos e segurança do fluxo
- `guia_fluxos_login_autenticacao_app.md`: guia detalhado dos fluxos reais de cadastro, login por senha, login social, registro silencioso, recuperacao de senha e divergencias abertas entre runtime atual e alvo arquitetural
- `consolidado_migracao_autenticacao_identidade_thimisu.md`: visão única das responsabilidades, migrações e inconsistências abertas entre autenticação, identidade e thimisu
- `classificacao_documentacao_ecossistema.md`: mapa de quais `.md` dos repositorios centrais sao canonicos, historicos ou ainda precisam alinhar
- `especificacao_scheduler_pendencias_integracao_produto.md`: especificacao funcional e tecnica da fila persistida e do scheduler de novas tentativas para entregas ao backend do produto
- `runbook_teste_integrado_dev_produto_indisponivel.md`: passo a passo validado em `dev` para cadastro confirmado com produto fora do ar, drenagem da fila ao religar o produto e login central sem dependencia do backend do produto
- `guia-seguranca-app-movel.md`: sinais locais do app, integração com atestação oficial e decisão de risco no backend
- `guia-desenvolvimento.md`: ambiente local, `MailHog`, Docker e rotina de desenvolvimento
- `guia-mtls.md`: malha mTLS do backchannel e geração de certificados
- `guia-operacao-producao.md`: runtime, operação e observabilidade
- `guia-cloudflare-tunnel-google-keycloak-dev.md`: exposição pública temporária do Keycloak local para Google OAuth brokerado no iPhone físico
- `plano-padronizacao-realm-unico.md`: alvo arquitetural para padronizar o realm OIDC em `eickrono` entre `dev`, `hml` e `prod`
- `runbook_migracao_multiapp_schemas.md`: ordem prática da migração do legado em `public` para o modelo novo por schemas

## Estrutura

- `/modulos/modulo-eickrono-autenticacao`: runtime público/autenticado de
  autenticação ainda em convergência arquitetural
- `/modulos/modulo-eickrono-autenticacao/README.md`: papel do módulo e
  distinção entre nome físico e nome operacional
- `/modulos/modulo-eickrono-keycloak`: provider, SPI e empacotamento do
  Keycloak customizado
- `/infraestrutura`: `docker compose`, variáveis e material de runtime por ambiente
- `/documentacao`: guias arquiteturais, operacionais e diagramas

## Leitura recomendada

1. `guia-arquitetura.md`
2. `guia_fluxos_login_autenticacao_app.md`
3. `guia-desenvolvimento.md`
4. `guia-mtls.md`
5. `checklist-seguranca-fapi.md`
