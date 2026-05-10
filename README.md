# Eickrono Autenticação

Projeto do ecossistema de autenticação da Eickrono que concentra:

- o runtime do servidor de autorização baseado em Keycloak customizado;
- os artefatos de runtime do Keycloak organizados em `autorizacao/`;
- a orquestração local via `docker compose`;
- certificados, scripts e documentação operacional da stack.

A decisão canônica de nomes dos serviços está em
`documentacao/decisao_nomenclatura_repositorios_servicos.md`.

Na arquitetura do ecossistema, a divisão correta é:

- `eickrono-autenticacao-servidor`: servidor de autorização Keycloak customizado + infraestrutura operacional da stack;
- `eickrono-identidade-servidor`: serviço de identidade e contexto canônico, ainda usado em partes da migração e do backchannel;
- `eickrono-contas-servidor`: API de contas.

## Decisão estrutural aprovada

O alvo arquitetural aprovado para `eickrono-autenticacao-servidor` é este:

- convergir para um **projeto simples**, não para um monorepo por domínio;
- permanecer como **servidor central/singleton** de autenticação do ecossistema;
- servir de forma **genérica** a qualquer app, site ou projeto cadastrado na
  base, sem módulo por produto;
- concentrar apenas responsabilidades de autenticação/autorização, sessão,
  refresh, brokers sociais, política de dispositivo e integrações centrais
  relacionadas a isso;
- **não** embutir `contas` como módulo interno de domínio;
- **não** criar acoplamento interno de runtime entre autenticação e `contas`.

Isso significa, na prática:

- `contas` permanece um domínio separado, com sua própria borda pública para
  app/site/frontend;
- este repositório não deve evoluir para ter “um módulo por projeto” nem “uma
  API interna de contas” como parte da arquitetura final;
- quando a autenticação precisar ser flexível para vários produtos, isso deve
  ser resolvido por dados como `cliente`, `sistema`, `aplicacaoId` e regras
  canônicas, e não por multiplicação de módulos específicos.

## Estado físico atual do código

Hoje o repositório está organizado fisicamente com:

- `modulos/modulo-eickrono-autenticacao`
- `modulos/modulo-eickrono-keycloak`
- a raiz agregadora com infraestrutura, documentação, `docker compose`,
  certificados e scripts operacionais

O corte físico de desmontagem do monorepo já foi aplicado:

- o build Maven padrão da raiz agora cuida apenas de autenticação;
- `api-contas-eickrono` já foi extraído para o repositório standalone
  `eickrono-contas-servidor`;
- os dois módulos de autenticação voltam a ficar agrupados em `modulos/`,
  deixando a raiz apenas com material agregador e operacional;
- o `pom.xml` da raiz fica apenas como um agregador fino transitório, sem
  dependências nem plugins compartilhados entre os componentes;
- a stack local sobe autenticação por padrão e só inclui `contas` quando houver opt-in operacional.

Essa estrutura física atual deve ser lida como **estado transitório do código e
da operação local**, não como a arquitetura final aprovada.

Leitura correta:

- referências a `modulos/modulo-eickrono-autenticacao` e
  `modulos/modulo-eickrono-keycloak` em documentação significam o layout físico
  vigente dos módulos;
- referências antigas a `api-identidade-eickrono`,
  `api-autenticacao-eickrono` e `servidor-autorizacao-eickrono` devem ser
  lidas como nomes físicos anteriores desses módulos;
- a convergência desejada continua sendo separar a autenticação como projeto
  simples e central.

### Mapa de nomenclatura

| Conceito | Nome aprovado |
| --- | --- |
| repositório | `eickrono-autenticacao-servidor` |
| módulo físico da API | `modulo-eickrono-autenticacao` |
| módulo físico do Keycloak | `modulo-eickrono-keycloak` |
| serviço Docker da API | `eickrono-autenticacao` |
| serviço Docker do Keycloak | `eickrono-keycloak` |

Use os nomes da coluna certa conforme o contexto. A maior parte da confusão
histórica vinha justamente de tratar nome de serviço como se fosse nome de
módulo.

### Mapa de namespaces de segredos

Para `AWS Secrets Manager`, o padrão canônico aprovado é:

```text
/eickrono/<ambiente>/<dominio>/<categoria>/<identificador>/<tipo>
```

Regra prática:

- segredos de cliente do Keycloak:
  `/eickrono/<ambiente>/keycloak/clientes/<client-id>/secret`
- senha de admin do Keycloak:
  `/eickrono/<ambiente>/keycloak/admin/password`
- SMTP da identidade:
  `/eickrono/<ambiente>/identidade/smtp/primario/username`
  e `/password`

Namespaces canônicos atualmente usados em `hml`:

| Namespace | Client ID | Finalidade | Consumidores |
| --- | --- | --- | --- |
| `/eickrono/hml/keycloak/clientes/autenticacao-servidor/secret` | `autenticacao-servidor` | segredo do cliente interno usado para token JWT/backchannel entre autenticação e identidade | `identidade-hml`, `auth-hml` |
| `/eickrono/hml/keycloak/clientes/eickrono-keycloak/secret` | `eickrono-keycloak` | segredo do cliente interno do próprio módulo Keycloak/customizações | `auth-hml` |
| `/eickrono/hml/keycloak/clientes/thimisu-backend/secret` | `thimisu-backend` | segredo do cliente interno usado pelo backend do Thimisu no backchannel JWT | `thimisu-backend-hml`, `auth-hml` |

Fonte de verdade: `documentacao/decisao_nomenclatura_repositorios_servicos.md`.

## Diretriz de nomenclatura

Na autenticação, modelos, tabelas, contratos, enums e documentação devem
evitar nomes específicos de produto quando o conceito for compartilhado pelo
ecossistema.

Regra prática:

- usar nomes gerais como `cliente`, `sistema`, `vinculo`, `perfilSistema` e
  equivalentes;
- evitar nomes como `Thimisu` quando a regra vale para vários apps, sites ou
  softwares;
- só usar o nome de um produto quando a regra realmente for exclusiva dele.

## Documentação canônica

A documentação principal permanece em `documentacao/`, e este `README.md`
resume a estrutura física e a direção vigente do repositório.

### Diretriz vigente para o app móvel

- cadastro, confirmação de e-mail, login e recuperação de senha devem
  convergir para a borda pública final de `autenticacao`;
- a autenticação continua dona da conta central, das credenciais e das liberações internas;
- a identidade continua dona da `Pessoa` canônica;
- o `thimisu` recebe apenas o provisionamento do perfil daquele sistema depois que conta central e `Pessoa` já tiverem sido resolvidas;
- o `X-Device-Token` canônico nasce no próprio login público da autenticação;
- qualquer explicação antiga centrada em navegador, `OIDC` interativo no app ou autenticação pública via `thimisu` deve ser considerada legada.

### Guias principais

- `documentacao/guia-arquitetura.md`: papel de cada serviço, contratos canônicos e segurança do fluxo
- `documentacao/consolidado_migracao_autenticacao_identidade_thimisu.md`: consolidado único das responsabilidades, migrações e perguntas abertas entre autenticação, identidade e thimisu
- `documentacao/guia-seguranca-app-movel.md`: sinais locais do app, atestação e decisão de risco no backend
- `documentacao/guia-desenvolvimento.md`: ambiente local, `MailHog`, Docker e rotina de desenvolvimento
- `documentacao/guia-mtls.md`: malha `mTLS` do backchannel e geração de certificados
- `documentacao/guia-operacao-producao.md`: runtime, operação e observabilidade
- `documentacao/padrao-codigos-erro-correlacao-observabilidade.md`: padrão canônico de `error_code`, `flow_id`, logs mascarados, traces e auditoria
- `documentacao/guia-cloudflare-tunnel-google-keycloak-dev.md`: exposição temporária do Keycloak local para Google OAuth brokerado
- `documentacao/plano-padronizacao-realm-unico.md`: alvo arquitetural para padronizar o realm `OIDC`
- `documentacao/matriz_migracao_autenticacao_identidade_thimisu_backend.md`: transição consolidada entre autenticação, identidade e `thimisu-backend`
- `documentacao/analise_fronteiras_funcionais_autenticacao_identidade_thimisu_backend.md`: verificação objetiva das fronteiras funcionais
- `documentacao/runbook_migracao_multiapp_schemas.md`: ordem prática da migração do legado em `public` para o modelo por schemas
- `documentacao/backlog_cross_service_autenticacao_oidc_dispositivo.md`: backlog priorizado da coordenação entre app, autenticação, Keycloak e identidade-servidor

## Orquestração canônica

Os comandos operacionais de build, teste e subida da stack agora ficam
centralizados neste repositório:

- `make package-servicos`
- `make test-servicos`
- `make test-servicos-completo` (`Docker` acessível, porque a identidade usa `Testcontainers`)
- `make compose-config`
- `make up-dev`
- `make up-hml`

Observações desta etapa:

- `make package` e `make test` agora exercitam apenas o build canônico de autenticação;
- para subir `contas` junto na stack local, use `INCLUIR_API_CONTAS=true make up-dev`
  ou `INCLUIR_API_CONTAS=true make up-hml`.

## Consulta de versão em runtime

Para conferência operacional do que está rodando:

- servidor de autorização/Keycloak customizado:
  - `GET /realms/{realm}/eickrono-runtime/estado`
  - resposta com `servico`, `status`, `versao` e `buildTime`

Esse endpoint é atendido pelo provider customizado deste projeto e devolve a
versão do artefato Java empacotado no runtime do Keycloak.

## Arquitetura canônica

- o app deve falar diretamente com a borda pública final de `autenticacao` para cadastro, login, refresh e recuperação de senha;
- a `identidade` deve ficar apenas em backchannel interno quando ainda for necessária durante a migração;
- a autenticação continua sendo a autoridade central de credencial, sessão e vínculo por sistema;
- o backend do produto recebe apenas provisionamento interno de perfil e contexto já autorizados;
- a confirmação de e-mail acontece na autenticação antes de qualquer provisionamento no domínio do produto;
- o app não abre uma tela dedicada de registro de dispositivo;
- se a autenticação exigir validação adicional de contato, o app reutiliza a tela de verificação já existente;
- a recuperação de senha sempre responde ao app com mensagem genérica, sem revelar se o e-mail existe.

## Responsabilidades deste repositório

- empacotar o provider/JAR do servidor de autorização;
- versionar `autorizacao/realms`, `autorizacao/temas` e `autorizacao/providers`;
- sustentar o refresh com validação de `device token` por backchannel;
- fornecer a infraestrutura local de `docker compose`, `MailHog` e certificados;
- documentar a operação da stack de autenticação.

No estado alvo aprovado, o contrato público final usado pelo app pertence ao
domínio de autenticação. Enquanto a convergência ainda não termina, parte do
código público permanece no módulo físico
`modulos/modulo-eickrono-autenticacao`. Essa localização atual não deve ser lida
como aprovação permanente de monorepo ou de múltiplas APIs de domínio dentro
deste repositório.

## Papel na arquitetura canônica

No fluxo móvel atual:

- a borda pública final do app deve ficar em `autenticacao`;
- este repositório sustenta a parte de Keycloak/RH-SSO do ecossistema;
- login, recuperação de senha e demais fluxos sensíveis devem convergir para a API pública final de autenticação;
- a autenticação continua como dona da conta central, de `usuario + sistema`, do refresh e das políticas de segurança;
- o provider daqui consulta a identidade por `mTLS` no refresh protegido por `device token`.

## Observação sobre `contas`

`contas` continua sendo um domínio separado.

Neste desenho aprovado:

- `contas` não faz parte da fronteira interna do servidor de autenticação;
- `contas` não é backchannel obrigatório da autenticação;
- `contas` conversa com app/site/frontend pela sua própria borda pública;
- qualquer referência a `contas` neste `README` fora do contexto de orquestração
  local de ambiente não deve ser interpretada como acoplamento arquitetural
  desejado.

## Comunicação interna entre servidores

No fluxo canônico de cadastro, a autenticação coordena duas etapas internas:

1. a autenticação aciona a identidade para criar ou atualizar a `Pessoa` canônica;
2. depois disso, a autenticação aciona o backend do produto para criar ou atualizar o perfil daquele sistema.

Essas comunicações internas devem ser:

- autenticado por JWT de serviço;
- restrito por allowlist de `client_id`;
- protegido por `mTLS`;
- idempotentes por `cadastroId`, para que retries não dupliquem `Pessoa` ou perfil de sistema.

## Sessão e recuperação de senha

Fluxos públicos canônicos da autenticação:

- `POST /api/publica/cadastros`
- `POST /api/publica/cadastros/{cadastroId}/confirmacoes/email`
- `POST /api/publica/cadastros/{cadastroId}/confirmacoes/email/reenvio`
- `POST /api/publica/sessoes`
- `POST /api/publica/recuperacoes-senha`
- `POST /api/publica/recuperacoes-senha/{fluxoId}/confirmacoes/email`
- `POST /api/publica/recuperacoes-senha/{fluxoId}/confirmacoes/email/reenvio`
- `POST /api/publica/recuperacoes-senha/{fluxoId}/senha`

Qualquer rota pública de autenticação existente em servidor de produto deve ser tratada como legada e não deve ser usada por novos clientes.

## Sessão, dispositivo e `X-Device-Token`

No desenho canônico atual:

- o login público já valida credenciais, atestação nativa e metadados do aparelho;
- o backend decide se o aparelho pode ser aceito silenciosamente;
- quando o contexto estiver válido, a própria autenticação emite o `X-Device-Token` na resposta de `POST /api/publica/sessoes`;
- o app apenas persiste esse token e o envia depois nas chamadas protegidas;
- o app não calcula localmente um estado de "onboarding de dispositivo";
- uma tela separada de registro de dispositivo não faz mais parte do fluxo principal.

## Derivação de senha

- a credencial efetiva não usa mais `data_nascimento` como insumo auxiliar;
- a SPI do Keycloak deriva a senha com `pepper + createdTimestamp`, usando apenas o campo nativo do usuário no Keycloak;
- o mesmo mecanismo precisa ser respeitado em reset de senha e required actions.

## Atualização local obrigatória do `docker compose`

Os containers Java locais não recompilam código automaticamente. A imagem da API de identidade copia o `jar` já empacotado em `target/`, então alteração em Java sem novo `package` deixa o ambiente rodando código antigo.

Quando mudar qualquer um dos três projetos da stack:

1. `make package-servicos`
2. `make up-dev`

Se precisar agir isoladamente em um projeto:

- identidade: `cd ../eickrono-identidade-servidor && mvn -q package -DskipTests`
- contas: `cd ../eickrono-contas-servidor && mvn -q package -DskipTests`
- autenticação/autorização: `mvn -q package -DskipTests`

Se o problema observado no app divergir do código-fonte atual, a primeira hipótese operacional deve ser container desatualizado.

Observação:

- as referências operacionais a `identidade` e `contas` nesta parte do
  `README` existem porque este repositório ainda centraliza a orquestração
  local da stack;
- isso não altera a decisão arquitetural aprovada de que
  `eickrono-autenticacao-servidor` deve convergir para um projeto simples,
  central e sem módulo interno de `contas`.

## Ambientes locais

Em `dev` e `hml`, o `docker compose` inclui `MailHog` para testes locais de e-mail:

- `dev`: SMTP `localhost:1025`, UI `http://localhost:8025`
- `hml`: SMTP `localhost:11025`, UI `http://localhost:18025`

No `dev`, se o `.env` ja estiver apontando para um SMTP real, ainda e possivel
forcar o uso do MailHog sem alterar essas credenciais:

1. `cd infraestrutura/dev`
2. `docker compose -f docker-compose.yml -f docker-compose.email-fake.yml up -d --build smtp-teste eickrono-autenticacao identidade-servidor`
3. abrir `http://localhost:8025`

O `docker compose` local usa PostgreSQL já existente no ambiente local, com bancos separados por serviço:

- `dev` Keycloak/autorização: `jdbc:postgresql://localhost:5432/eickrono_autorizacao`
- `dev` identidade: `jdbc:postgresql://localhost:5432/eickrono_identidade`
- `dev` contas: `jdbc:postgresql://localhost:5432/eickrono_contas`
- `hml` Keycloak: `jdbc:postgresql://localhost:5432/keycloak_hml`
- `hml` identidade: `jdbc:postgresql://localhost:5432/eickrono_identidade_hml`
- `hml` contas: `jdbc:postgresql://localhost:5432/eickrono_contas_hml`

## Swagger

- API autenticacao `dev`: `http://127.0.0.1:8081/swagger-ui/index.html`
- API autenticacao `dev` OpenAPI: `http://127.0.0.1:8081/v3/api-docs`
- API identidade `hml`: `http://localhost:18081/swagger-ui/index.html`
- API identidade `hml` OpenAPI: `http://localhost:18081/v3/api-docs`
- API contas `dev`: `http://localhost:8082/swagger-ui/index.html`
- API contas `dev` OpenAPI: `http://localhost:8082/v3/api-docs`
- API contas `hml`: `http://localhost:18082/swagger-ui/index.html`
- API contas `hml` OpenAPI: `http://localhost:18082/v3/api-docs`

Proteção:

- `dev`: uso local liberado;
- `hml`: `Basic Auth` + whitelist de IP;
- credenciais padrão de `hml`: usuário `swagger`, senha `swagger-hml`.

## Leitura recomendada

- `documentacao/guia-arquitetura.md`
- `documentacao/fluxogramas_fluxos_publicos_estado_atual.md`
- `documentacao/fluxogramas_fluxos_publicos_regra_funcional_em_fechamento.md`
- `documentacao/especificacao_schema_db01_db02_db03_fluxos_publicos.md`
- `documentacao/especificacao_avatar_social_e_avatar_preferido_multiapp.md`
- `documentacao/padrao-codigos-erro-correlacao-observabilidade.md`
- `documentacao/matriz_migracao_autenticacao_identidade_thimisu_backend.md`
- `documentacao/analise_fronteiras_funcionais_autenticacao_identidade_thimisu_backend.md`
- `documentacao/plano_migrations_v30_v36_db01_db02_db03_local_primeiro.md`
- `documentacao/mapeamento_tdd_componentes_migracoes_fluxos_publicos.md`
- `documentacao/runbook_migracao_multiapp_schemas.md`
- `documentacao/backlog_cross_service_autenticacao_oidc_dispositivo.md`
- `documentacao/guia-seguranca-app-movel.md`
- `documentacao/guia-desenvolvimento.md`
- `documentacao/guia-mtls.md`
- `documentacao/guia-operacao-producao.md`
- `documentacao/checklist-seguranca-fapi.md`
- `documentacao/guia-cloudflare-tunnel-google-keycloak-dev.md`
- `documentacao/plano-padronizacao-realm-unico.md`
- `infraestrutura/prod/pipeline/README.md`

> Toda a documentação, comentários e identificadores permanecem em português do Brasil, conforme diretriz organizacional.
