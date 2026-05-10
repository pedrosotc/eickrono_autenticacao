# Backlog Cross-Service de Autenticacao, OIDC e Dispositivo

> Status deste documento: **backlog operacional de transicao**.
>
> Este arquivo existe para priorizacao de trabalho entre servicos.
> Ele nao e a fonte principal para decidir ownership de `Pessoa`, conta
> central, `usuario + sistema` ou papel do backend de produto.
>
> Para essas decisoes, usar primeiro:
>
> - `consolidado_migracao_autenticacao_identidade_thimisu.md`

## Objetivo

Este documento consolida o backlog que ainda atravessa mais de um projeto do
ecossistema:

- `eickrono-thimisu-app`
- `eickrono-autenticacao-servidor`
- `modulo-eickrono-keycloak` (Keycloak)
- `eickrono-thimisu-backend`

Ele existe para evitar que as pendencias fiquem espalhadas entre `TODO.md`,
README e notas de arquitetura.

A decisão canônica de nomes dos serviços que hoje estão dentro do monorepo de
autenticação ficou registrada separadamente em
`decisao_nomenclatura_repositorios_servicos.md`.

## Escopo

Entram aqui apenas itens que dependem de coordenacao entre servicos,
contratos, ambientes ou infraestrutura.

Nao entram aqui:

- detalhes isolados de UI do app;
- tarefas exclusivamente locais de um modulo;
- itens ja fechados na rodada atual, como o fallback legado do banco unico do
  app.

## O que ja esta fechado

- o app ja trabalha com `CatalogoLocalContas`, `ContaLocalDispositivo` e banco
  por conta;
- o fallback legado do banco unico `thimisu.sqlite` ja foi removido do app;
- o contrato publico de `aplicacaoId` e sinais locais de seguranca do app ja
  entrou no `servidor de autenticacao`;
- o checklist de [TODO_seguranca_app_movel.md](TODO_seguranca_app_movel.md) ja
  esta implementado no recorte original.

## Prioridade Canonica

### P0. Padronizacao OIDC Entre Ambientes

**Problema**

O ecossistema ainda corre risco de divergencia entre `issuer`, realm,
callbacks, exports de realm, configuracoes Spring e configuracoes do app.

**Servicos impactados**

- `modulo-eickrono-keycloak`
- `api-identidade-eickrono`
- `eickrono-thimisu-app`

**O que falta**

- padronizar o realm em `eickrono` para `dev`, `hml` e `prod`;
- separar claramente `id-*` para APIs e `oidc-*` para o servidor de
  autorizacao;
- consolidar `thimisu-*` para a superficie do produto e
  `thimisu-backend-*` para o backend de dominio consumido pelo app e pelos
  demais projetos do ecossistema;
- alinhar `issuer`, redirect URIs, `postLogoutRedirectUri`, callbacks dos
  brokers sociais e exports do realm;
- tratar a mudanca como alteracao de contrato OIDC, nao como renome simples.

**Definicao de pronto**

- o app aponta para `issuer` canonico por ambiente;
- os brokers sociais funcionam com os callbacks finais;
- Keycloak, backend e app deixam de depender de nomes divergentes por ambiente.

**Estado desta rodada**

- primeiro corte aplicado no runtime:
  - `hml` dos backends autenticados passou para `https://oidc-hml.eickrono.store/realms/eickrono`;
  - `prd` do `identidade-servidor` passou para `https://oidc.eickrono.com/realms/eickrono`;
  - o app em `prod` passou a usar `https://id.eickrono.com/` como host operacional da borda canonica de autenticacao;
- o app agora aceita sobrescritas independentes por build para:
  - `CONFIG_AUTENTICACAO_BASE_URL`
  - `CONFIG_THIMISU_BASE_URL`
  - `CONFIG_OIDC_ISSUER`
- a convencao de nomes do dominio Thimisu ficou aprovada:
  - `thimisu-*` para a superficie do produto
  - `thimisu-backend-*` para o backend de dominio
- a leitura do que ja pode mudar e do que ainda depende de migracao
  coordenada ficou centralizada em
  `matriz_migracao_autenticacao_identidade_thimisu_backend.md`
- ainda falta publicar DNS/runtime correspondente para fechar `hml`
  do app como ambiente publico completo.

### P0. Governanca De Sessao E Dispositivo

**Problema**

Ainda faltam capacidades operacionais centrais de sessao e `X-Device-Token`.

**Servicos impactados**

- `eickrono-autenticacao-servidor`
- `modulo-eickrono-keycloak`
- `eickrono-thimisu-app`

**O que falta**

- revogacao remota de sessoes e de `X-Device-Token`;
- encerramento de todas as sessoes ativas de um usuario;
- lista de dispositivos confiaveis com historico e ultimo acesso;
- reautenticacao para operacoes sensiveis;
- bloqueio por risco e expiracao por inatividade com politica unificada.

**Definicao de pronto**

- logout local e global ficam coerentes;
- redefinicao de senha invalida sessao e dispositivo de forma audivel;
- o app consegue listar e revogar dispositivos confiaveis.

### P0. Fechar O Modelo De Cadastro Pendente Com Telefone

**Problema**

O fluxo ja avancou para validacao dupla, mas a politica de estados e expurgo
do lado do servidor ainda esta centrada em `PENDENTE_EMAIL`.

**Servicos impactados**

- `api-identidade-eickrono`
- `eickrono-thimisu-backend`
- `eickrono-thimisu-app`

**O que falta**

- revisar o modelo de estados de `cadastros_conta`;
- definir o que significa `email confirmado, telefone pendente`;
- revisar expurgo automatico e limpeza segura;
- garantir que provisionamento para o servidor de identidade so ocorra no ponto
  canonico certo.

**Definicao de pronto**

- o cadastro tem maquina de estados coerente para email + telefone;
- expurgo nao remove estados validos intermediarios;
- app e backend compartilham a mesma semantica de liberacao.

### P1. Completar O Cutover Do Identidade-Servidor Para Backchannel Only

**Problema**

O `identidade-servidor` ja esta documentado como fora da borda publica de
autenticacao, mas ainda precisa consolidar a transicao e eliminar restos de
fluxo publico onde existirem.

**Servicos impactados**

- `eickrono-thimisu-backend`
- `eickrono-autenticacao-servidor`

**O que falta**

- localizar e remover ou blindar quaisquer rotas publicas de autenticacao
  restantes no `identidade-servidor`;
- manter o provisionamento somente por backchannel;
- reforcar idempotencia por `cadastroId` no fluxo de provisionamento.

**Definicao de pronto**

- o `identidade-servidor` nao recebe mais senha, codigo ou tentativa de login
  do app;
- toda autenticacao publica converge para a borda final de `autenticacao`,
  ainda que parte do runtime atual permaneça fisicamente localizada em
  `api-identidade-eickrono` durante a migracao.

### P1. Hardening Real Da Seguranca Do App Movel

**Problema**

O contrato basico de risco ja existe, mas o endurecimento do runtime movel
continua pendente.

**Servicos impactados**

- `eickrono-autenticacao-servidor`
- `eickrono-thimisu-app`

**O que falta**

- deteccao de root/jailbreak alem da atestacao nativa;
- sinais de Frida/hooking e adulteracao do app;
- revisao de screenshot, clipboard, cache e armazenamento local;
- alinhar politica por `aplicacaoId`, `packageName`, `bundleIdentifier` e
  `teamIdentifier`.

**Definicao de pronto**

- existe politica clara por ambiente e por app;
- os sinais locais relevantes chegam ao backend e entram na decisao de risco;
- o app aplica as protecoes de UX/armazenamento aprovadas.

### P1. Monitoramento E Antifraude Operacional

**Problema**

Ainda falta sair de rate limit basico para uma camada real de deteccao,
quarentena e alerta.

**Servicos impactados**

- `eickrono-autenticacao-servidor`
- `modulo-eickrono-keycloak`
- `eickrono-thimisu-app`

**O que falta**

- mascaramento adicional de dados sensiveis em logs;
- envio de eventos criticos para monitoramento e alerta;
- bloqueio progressivo, quarentena e resposta operacional;
- backlog separado para politica antiusuarios fake do produto.

**Definicao de pronto**

- eventos criticos de autenticacao chegam a observabilidade;
- existe estrategia operacional alem de rate limit;
- o backlog anti-fake do produto deixa de ser apenas nota isolada.

### P1. Cutover Multiapp Por Schemas E Remocao Do Legado Compartilhado

**Problema**

O runbook de schemas ja existe, mas o legado ainda aparece como transicao
documentada e operacional.

**Servicos impactados**

- `eickrono-autenticacao-servidor`
- `eickrono-thimisu-backend`
- banco PostgreSQL compartilhado

**O que falta**

- concluir a transicao do modelo legado em `public` para o modelo alvo por
  schemas;
- desligar escrita no legado depois da validacao operacional;
- remover tabelas e projecoes que ficarem apenas como compatibilidade.

**Definicao de pronto**

- schemas novos sustentam a operacao;
- `public` deixa de ser dependencia de transicao;
- o runbook de migracao passa de plano para historico de execucao.

### P2. Governanca Final Dos Brokers Sociais

**Problema**

Ainda existe diferenca entre “suportado no modelo” e “habilitado de fato no
ambiente”, especialmente com provedores Meta e com a politica de
`first broker login`.

**Servicos impactados**

- `modulo-eickrono-keycloak`
- `api-identidade-eickrono`
- `eickrono-thimisu-app`

**O que falta**

- versionar explicitamente os flows de `first broker login` e
  `post broker login`;
- consolidar a politica de provedores habilitados por ambiente;
- manter coerencia entre `trustEmail`, UX do app e resolucao local.

**Definicao de pronto**

- o app nao precisa mais esconder provedores por comentario local;
- os brokers sociais ficam governados por politica e ambiente;
- Apple, Google e Meta seguem contrato operacional previsivel.

## Ordem Recomendada De Execucao

1. Padronizacao OIDC entre ambientes.
2. Governanca de sessao e dispositivo.
3. Fechamento do modelo de cadastro pendente com telefone.
4. Cutover do `identidade-servidor` para backchannel only.
5. Hardening real da seguranca do app movel.
6. Monitoramento e antifraude operacional.
7. Cutover multiapp por schemas e remocao do legado compartilhado.
8. Governanca final dos brokers sociais.

## Relacao Com Os TODOs Locais

- o `TODO.md` do `eickrono-autenticacao-servidor` continua valido como fonte
  local de detalhes;
- este backlog passa a ser a visao canonica priorizada do que ainda depende de
  coordenacao entre projetos;
- o `eickrono-thimisu-backend` deve ser lido em conjunto com a diretriz de
  que ele nao e mais a borda publica de autenticacao.
