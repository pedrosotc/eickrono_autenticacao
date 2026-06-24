# Consolidado de Migracao entre Autenticacao, Identidade e Thimisu

## Papel deste documento

Este e o documento canônico da migracao entre `autenticacao`,
`identidade` e backend do produto.

Ele deve ser usado como fonte principal para:

- ownership de responsabilidades;
- ordem de migracao;
- decisoes ja consolidadas;
- plano executavel em TDD;
- checklist tecnico por etapa.

Se outro documento antigo divergir deste consolidado, este consolidado
prevalece.

## Objetivo

Unificar, em um unico documento, a leitura canônica sobre:

- o papel de cada projeto no ecossistema;
- o que ja foi migrado;
- o que ainda esta em transicao;
- quais inconsistencias documentais ainda precisam de decisao.

Este documento substitui a leitura fragmentada de varios arquivos quando a
pergunta e: "quem deve ser dono de que responsabilidade entre autenticacao,
identidade e thimisu?".

## Fontes consolidadas

Este consolidado foi montado a partir de:

- `documentacao/analise_fronteiras_funcionais_autenticacao_identidade_thimisu_backend.md`
- `documentacao/matriz_migracao_autenticacao_identidade_thimisu_backend.md`
- `../eickrono-thimisu-backend/docs/proposta_cisao_identidade_thimisu.md`
- `../eickrono-thimisu-backend/README.md`
- `../eickrono-identidade-servidor/README.md`
- `README.md` deste repositório

## Decisoes canonicas ja assumidas neste consolidado

As regras abaixo ja sao tratadas aqui como decisao vigente:

- `Pessoa` canônica do ecossistema pertence a `identidade`.
- a conta central de acesso do ecossistema pertence a `autenticacao`.
- o `usuario` de produto nao e global do ecossistema; ele representa um perfil
  vinculado a um sistema.
- a unicidade do `usuario` deve ser controlada pela chave composta
  `usuario + sistema`.
- a disponibilidade de `usuario + sistema` deve ser controlada pela
  `autenticacao` para todos os sistemas do ecossistema.
- quando um conceito servir para todo o ecossistema, a nomenclatura deve evitar
  nome de produto e usar termos gerais como `cliente`, `sistema`, `vinculo` e
  `perfilSistema`.
- `thimisu-backend` nao e borda publica de login, senha, recuperacao de senha
  ou emissao de sessao.
- no estado alvo, o `eickrono-thimisu-backend` deve ficar restrito ao dominio
  de estudo do Thimisu.

## Papel de cada projeto

### `eickrono-autenticacao-servidor`

Projeto canônico de runtime de autenticacao e autorizacao do ecossistema.

Funcoes principais:

- sustentar o Keycloak customizado;
- versionar realms, themes, providers e runtime OIDC;
- manter brokers sociais e descoberta runtime dos provedores;
- sustentar infraestrutura local e operacional da stack;
- manter certificados, `mTLS`, compose e automacao de runtime;
- proteger refresh, `device token` e integracoes sensiveis por backchannel;
- ser dono da conta central de autenticacao do usuario;
- validar a disponibilidade do `usuario + sistema` antes de liberar o restante
  do fluxo;
- ser dono do vinculo do usuario com cada cliente do ecossistema.

Nao e o projeto que o app consome diretamente para os endpoints Spring da borda
publica. Ele sustenta o servidor de autorizacao e a operacao da stack.

### `eickrono-identidade-servidor`

Projeto canônico da API publica consumida pelo app para fluxos sensiveis de
conta e identidade.

Funcoes principais:

- expor cadastro, login, refresh, recuperacao de senha e confirmacoes;
- validar credenciais, codigos e politicas de sessao;
- emitir e renovar sessao;
- emitir `X-Device-Token`;
- integrar o app com Keycloak e com os servicos internos por backchannel;
- concentrar a leitura e a escrita da `Pessoa` canônica;
- ser o dono do contexto cadastral e civil compartilhado do ecossistema;
- expor vinculos sociais e vinculos organizacionais da conta autenticada.

Leitura pratica:

- e a borda publica canonica do app para autenticacao e identidade;
- e o dono funcional de `Pessoa`;
- nao deve concentrar regras do dominio pedagogico do Thimisu.

### `eickrono-thimisu-backend`

Projeto de backend do produto em transicao.

Ele deve ser lido em dois momentos:

- `estado atual em transicao`
- `estado alvo do mesmo backend`

O que existe hoje no codigo:

- `GET /api/v1/estado`
- `GET /api/interna/perfis-sistema/contexto`
- `GET /api/interna/perfis-sistema/disponibilidade`
- `POST /api/interna/perfis-sistema/provisionamentos`

Funcoes hoje encontradas:

- resolucao de contexto interno do produto;
- provisionamento idempotente de `PerfilSistema`;
- manutencao de copia local de `PessoaProdutoLocal` e `PerfilSistema`,
  sem ownership canônico de `Pessoa`.

Estado alvo do mesmo backend:

- nao ser dono de login, senha, codigo, sessao ou `device token`;
- nao ser dono de `Pessoa` canônica;
- nao ser dono da conta central do usuario;
- nao ser dono da verificacao de disponibilidade de `usuario + sistema`;
- ficar restrito a contexto e estruturas do dominio do produto;
- no estado final, o que restar aqui deve ser contrato de dominio do produto,
  nao "identidade dentro do backend do produto".

Escopo do `eickrono-thimisu-backend` no estado alvo:

- `deck`
- `card`
- `revisao`
- `progresso`
- compartilhamento
- permissoes do dominio de estudo

## Corte canonico de responsabilidades

### Deve ficar em `autenticacao`

- conta central do usuario;
- login;
- senha;
- recuperacao de senha;
- refresh;
- sessao;
- MFA;
- brokers sociais e integracao com OIDC;
- `X-Device-Token`;
- confianca de dispositivo;
- atestacao nativa;
- vinculo do usuario com apps e sites do ecossistema;
- disponibilidade de `usuario + sistema` para qualquer app, site ou software do
  ecossistema;
- status global da conta;
- status do vinculo com cada cliente do ecossistema;
- tabelas e trilhas de atestacao e operacoes sensiveis.

### Deve ficar em `identidade`

- `Pessoa` canônica do ecossistema;
- historico cadastral da pessoa;
- dados civis e cadastrais compartilhados;
- email e telefone como contatos canonicos da pessoa;
- contexto cadastral canonico usado por outros servicos;
- provisionamento e atualizacao de `Pessoa` a partir de fluxos internos;
- leitura de contexto de pessoa por `pessoaId`, `sub` ou chaves equivalentes;
- contratos de backchannel entre autenticacao e identidade para criar ou
  atualizar `Pessoa`.

### Deve ficar no backend de dominio do Thimisu

- estruturas de estudo do produto;
- contexto de dominio necessario ao uso do Thimisu;
- futuras entidades de produto como `deck`, `card`, `revisao` e `progresso`;
- qualquer metadado de produto que dependa de regra propria do Thimisu;
- consumo de `usuarioId` e `pessoaId` centrais sem recriar conta central;
- criacao de estruturas do produto depois que `autenticacao` e `identidade`
  ja tiverem concluido suas partes.

## O que ja foi saneado

### Fronteira publica

Pelo estado atual do codigo e da analise de fronteira, ja esta saneado que:

- cadastro publico do app nao entra pelo Thimisu;
- login nao entra pelo Thimisu;
- recuperacao de senha nao entra pelo Thimisu;
- confirmacoes de codigo nao entram pelo Thimisu;
- emissao de sessao nao entra pelo Thimisu.

Em outras palavras:

- o principal desvio historico de autenticacao publica no backend do produto ja
  foi removido.

### Runtime e infraestrutura

Ja esta documentado e amplamente alinhado que:

- `thimisu-backend` e o nome canônico de runtime do backend de dominio;
- `id-*` continua reservado para a borda publica de identidade;
- `oidc-*` continua reservado para o servidor OIDC;
- `thimisu-backend.p12` e nome canônico de runtime do certificado tecnico do
  backend de dominio.

## O que continua em transicao

### 1. `Pessoa` ainda aparece no lugar errado em parte do backend de dominio

Apesar da decisao canônica deste consolidado ser "Pessoa pertence a
identidade", ainda existem resquicios no backend de dominio e na propria
documentacao antiga dizendo o contrario.

### 2. `Usuario` ainda aparece acoplado ao legado do Thimisu

A proposta de cisao ja documenta que:

- a conta central fica na autenticacao;
- o controle de `usuario + sistema` fica na autenticacao;
- o backend do produto deve consumir ids centrais.

Mas o codigo e a documentacao ainda guardam restos de `Usuario` local,
`StatusUsuarioThimisu` e disponibilidade de `usuario` como se a regra fosse
estritamente do backend do produto.

### 3. O namespace legado `/api/interna/identidade/*` ja saiu do runtime

Esse namespace deixou de ser a superficie ativa do backend do produto.

O que ainda pode aparecer com esse nome hoje e:

- documentacao historica;
- planejamento antigo de migracao;
- nomenclatura residual em explicacoes de legado.

Portanto, a ambiguidade funcional principal ja foi cortada do runtime. O que
resta aqui e limpeza residual de documentacao e rastros historicos.
  migracao termina".

Como `Pessoa` passa a ser canônica em `identidade`, esse namespace tende a
ficar semanticamente errado se continuar no backend de dominio sem corte claro.

## Checklist de nomes atuais e nomes destino

Esta tabela consolida nomes legados ou centrados em produto que devem ser
renomeados no desenho alvo.

Legenda de `status`:

- `Pendente`: ainda nao migrado
- `Concluido`: migracao ja aplicada no codigo
- `Parcial`: parte da migracao ja existe, mas o legado ainda aparece
- `Documental`: impacto principal em nome e documentacao

| Nome atual | Nome destino | Projeto atual | Projeto destino | Status | Impacta banco | Impacta contrato | Ordem recomendada |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `/api/interna/identidade/provisionamentos` | quebrar em `/identidade/pessoas/interna/confirmacoes-email` e `/api/interna/perfis-sistema/provisionamentos` | legado do `eickrono-thimisu-backend` | `eickrono-identidade-servidor` + `eickrono-thimisu-backend (estado alvo)` | `Concluido` | `Sim` | `Sim` | `1` |
| `ProvisionamentoCadastroInternoServico` | `ProvisionamentoPerfilSistemaServico` | legado do `eickrono-thimisu-backend` | `eickrono-thimisu-backend (estado alvo)` | `Concluido` | `Sim` | `Sim` | `2` |
| `IdentidadeInternaController` | `PerfisSistemaInternoController` | legado do `eickrono-thimisu-backend` | `eickrono-thimisu-backend (estado alvo)` | `Concluido` | `Nao` | `Sim` | `3` |
| `/api/interna/identidade/usuarios/disponibilidade` | `/api/interna/perfis-sistema/disponibilidade` | legado do `eickrono-thimisu-backend` | `eickrono-autenticacao-servidor` + `eickrono-thimisu-backend (estado alvo)` | `Concluido` | `Sim` | `Sim` | `4` |
| `Usuario.nomeExibicao` | `identificador_publico_cliente` | `eickrono-thimisu-backend` | `eickrono-autenticacao-servidor` | `Pendente` | `Sim` | `Sim` | `5` |
| `StatusUsuarioThimisu` | `StatusPerfilSistema` | legado do `eickrono-thimisu-backend` | `eickrono-thimisu-backend (estado alvo)` | `Concluido` | `Sim` | `Sim` | `6` |
| `/api/interna/identidade/contexto` | `/api/interna/perfis-sistema/contexto` | legado do `eickrono-thimisu-backend` | `eickrono-thimisu-backend (estado alvo)` | `Concluido` | `Nao` | `Sim` | `7` |
| `UsuarioThimisu` | `PerfilSistema` | legado documental do `eickrono-thimisu-backend` | `eickrono-thimisu-backend (estado alvo)` | `Parcial` | `Nao` | `Sim` | `8` |
| `eickrono-thimisu-servidor` | `eickrono-thimisu-backend (estado alvo do mesmo backend)` | documentação legada | documentação consolidada | `Parcial` | `Nao` | `Nao` | `9` |

## Itens que devem migrar ou ser redesenhados

### Migrar para `identidade`

Pela proposta de cisao, estes itens sao claramente de `Pessoa`/cadastro:

- `HistoricoIdentidadeServico.java`
- `Pessoa.java`
- `PessoaHistorico.java`
- `TipoPessoa.java`
- `SexoPessoa.java`
- `CanalValidacaoTelefone.java`
- `PessoaRepositorio.java`
- `PessoaHistoricoRepositorio.java`
- parte de `IdentidadeInternaController.java` relacionada a operacoes de pessoa
- parte de `ProvisionamentoCadastroInternoServico.java` relacionada a criacao e
  atualizacao de pessoa
- parte das migrations ligadas a `pessoas` e `pessoas_historico`

### Redesenhar na `autenticacao`

Pela proposta de cisao, estes itens nao devem ser copiados literalmente para
`identidade`, e sim redesenhados na autenticacao:

- `Usuario.java`
- `UsuarioHistorico.java`
- `StatusUsuarioThimisu.java`
- `UsuarioRepositorio.java`
- `UsuarioHistoricoRepositorio.java`
- a parte de disponibilidade de `usuario + sistema`;
- a parte de criacao automatica de usuario acoplada ao fluxo de identidade.

### Permanecer no backend do produto

No estado final, o backend do produto deve manter apenas:

- consumo de `usuarioId` e `pessoaId` centrais;
- estruturas de produto;
- contexto de uso do produto;
- metadados especificos do dominio;
- nenhuma conta central duplicada.

## Contratos recomendados entre os servicos

### `autenticacao -> identidade`

Contrato interno para:

- criar ou atualizar `Pessoa` depois da etapa sensivel;
- usar `cadastroId` como chave idempotente;
- nunca enviar senha, hash, token, codigo ou estado transitorio de login.

Payload base esperado:

- `cadastroId`
- `sub`
- `nomeCompleto`
- `tipoPessoa`
- `nomeFantasia`
- `sexo`
- `paisNascimento`
- `dataNascimento`
- `emailPrincipal`
- `telefonePrincipal`
- `canalValidacaoTelefone`

Resposta base esperada:

- `pessoaId`
- `sub`
- `statusProvisionamento`

### `autenticacao -> backend do produto`

Contrato interno para:

- provisionar apenas o necessario ao produto;
- referenciar a conta central, nao criar conta local duplicada.

Payload base esperado:

- `usuarioId`
- `pessoaId`
- `clienteEcossistema`
- claims ou metadados minimos necessarios ao produto

### `backend do produto -> identidade`

Continua valido para:

- leitura de contexto canônico de pessoa;
- enriquecimento cadastral quando o dominio precisar consultar dados centrais.

## Decisoes consolidadas em linguagem de produto

### 1. Quem cuida de que parte do cadastro

O cadastro completo agora fica dividido em tres responsabilidades:

- `autenticacao` cuida da conta central, das validacoes sensiveis e da
  autorizacao para os outros sistemas seguirem;
- `identidade` cuida da `Pessoa` canônica do ecossistema;
- o sistema de produto cuida apenas do perfil e das estruturas necessarias para
  aquele produto funcionar.

Em termos de fluxo:

1. a `autenticacao` valida e libera;
2. a `identidade` cria ou atualiza a `Pessoa`;
3. o backend do produto cria o perfil e o contexto daquele sistema.

### 2. O que significa `usuario` no ecossistema

`usuario` nao deve mais ser lido como a conta central do ecossistema.

Neste consolidado, `usuario` significa:

- um identificador de perfil vinculado a um sistema;
- algo que pode variar entre sistemas;
- algo cuja unicidade e validada pela combinacao `usuario + sistema`.

Isso permite, por exemplo:

- a mesma pessoa ter perfis em sistemas diferentes;
- a mesma pessoa ter mais de um perfil no mesmo sistema, desde que cada perfil
  tenha um `usuario` diferente.

### 3. Quem valida se um `usuario` pode ser usado

A validacao de disponibilidade de `usuario + sistema` deve ficar na
`autenticacao`.

Motivo de produto:

- a autenticacao participa antes dos outros sistemas no fluxo de cadastro;
- ela e o ponto correto para decidir se aquele perfil pode ou nao seguir;
- os outros sistemas nao precisam receber o app sem que essa validacao ja tenha
  sido resolvida.

### 4. O que deve sair do backend do produto

Tudo o que for de `Pessoa` canônica e de identidade deve sair do backend de
produto.

Tudo o que for de conta central, liberacao de acesso e disponibilidade de
`usuario + sistema` deve sair do backend do produto e ficar na
`autenticacao`.

No backend do produto deve sobrar apenas:

- contexto e estruturas do produto;
- criacao do perfil do produto depois que `autenticacao` e `identidade`
  terminarem suas partes;
- regras que sejam realmente de dominio do produto.

### 5. O namespace `/api/interna/identidade/*` nao deve sobreviver como nome final no backend do produto

Como `Pessoa` canônica pertence a `identidade`, manter rotas chamadas
`/api/interna/identidade/*` dentro do backend do produto so prolonga uma
confusao de responsabilidade.

Direcao consolidada:

- o que for de `Pessoa` deve ir para `identidade`;
- o que sobrar no backend do produto deve ganhar nome de contexto ou
  provisionamento de produto, e nao de identidade.

### 6. O documento antigo de cisao tem trechos que precisam ser tratados como desatualizados

Algumas partes antigas ainda dizem que `Pessoa` ficaria no
`eickrono-thimisu-backend`.

Isso nao deve mais ser usado como regra de decisao.

A leitura consolidada correta passa a ser:

- `Pessoa` canônica e da `identidade`;
- conta central e disponibilidade de `usuario + sistema` sao da
  `autenticacao`;
- backend do produto fica so com o que e de produto.

## Proximos passos priorizados em linguagem de produto

### 1. Prioridade imediata

Primeiro deve ser concluida a migracao de `Pessoa` para a `identidade`.

Isso inclui:

- mover regras, modelos e historicos de `Pessoa`;
- ajustar contratos internos;
- remover a ideia de que `Pessoa` canônica mora no backend do produto.

### 2. Em seguida

Depois disso, deve ser concluido o desenho de conta central e de
`usuario + sistema` dentro da `autenticacao`.

### 3. Depois

So entao o backend do produto deve ser limpo de vez, para ficar com nome,
contratos e responsabilidades puramente de produto.

## Plano executavel por etapas com TDD

Este bloco traduz a migracao em fases menores, com ordem de execucao,
pre-requisitos, testes que devem falhar primeiro e criterio de conclusao.

Diretriz de execucao:

- cada etapa deve seguir `TDD` (`test-driven development`, ou seja: escrever o
  teste primeiro, ver falhar, implementar o minimo, ver passar e so entao
  refatorar);
- sempre que a etapa atravessar mais de um servico, os testes de contrato e de
  integracao devem vir antes da troca efetiva do consumidor;
- nenhuma etapa deve remover o legado antes de existir teste cobrindo o
  comportamento alvo.
- todas as etapas abaixo seguem a mesma ordem de leitura:
  - objetivo;
  - projetos principais;
  - pre-requisitos;
  - `TDD obrigatorio`;
  - implementacao minima;
  - definicao de pronto;
  - checklist tecnico detalhado;
  - sequencia segura de implementacao;
  - criterio objetivo de encerramento.

Observacao importante sobre os checklists detalhados:

- eles registram o ponto de partida historico usado para planejar cada etapa;
- por isso, alguns itens abaixo ainda usam expressoes como `hoje` ou citam
  classes antigas;
- quando isso acontecer, ler essas citacoes como fotografia do inicio da
  etapa, e nao como descricao do runtime atual depois das mudancas ja
  executadas.

### Etapa 1. Fazer a identidade virar dona real da `Pessoa`

Objetivo:

- tirar do backend do produto a responsabilidade canônica por `Pessoa`;
- fazer `identidade` concentrar criacao, atualizacao e consulta da `Pessoa`
  canonica.

Projetos principais:

- `eickrono-identidade-servidor`
- `eickrono-thimisu-backend`
- `eickrono-autenticacao-servidor`

Pre-requisitos:

- consolidado e proposta de cisao alinhados;
- contrato interno minimo de provisionamento de `Pessoa` definido.

TDD obrigatorio:

1. criar testes que falhem no `eickrono-identidade-servidor` para:
   - criar `Pessoa` por contrato interno;
   - atualizar `Pessoa` sem duplicar registro;
   - repetir a mesma chamada com o mesmo `cadastroId` sem criar duplicidade.
2. criar testes que falhem no `eickrono-thimisu-backend` para provar que:
   - o backend do produto nao precisa mais criar `Pessoa` canonica;
   - ele passa a aceitar apenas `pessoaId` ja resolvido.
3. criar teste de contrato entre `autenticacao` e `identidade` cobrindo o
   payload interno.

Implementacao minima:

- criar ou ajustar o endpoint interno da `identidade` para provisionar
  `Pessoa`;
- mover o servico de criacao/atualizacao de `Pessoa` para a `identidade`;
- adaptar a chamada interna da `autenticacao` para usar esse contrato;
- manter o legado do `thimisu-backend` apenas o suficiente para compatibilidade
  temporaria.

Definicao de pronto:

- `Pessoa` nova nasce pela `identidade`;
- repeticao com mesmo `cadastroId` continua devolvendo o mesmo resultado
  logico;
- o backend do produto deixa de ser a fonte canônica de `Pessoa`.

#### Checklist tecnico detalhado da Etapa 1

Arquivos e classes que hoje ainda prendem `Pessoa` ao backend do produto:

- `eickrono-thimisu-backend/modulos/thimisu-backend/src/main/java/com/eickrono/thimisu/backend/apresentacao/web/IdentidadeInternaController.java`
  - hoje ainda expoe contratos internos com nome de `identidade`;
- `eickrono-thimisu-backend/modulos/thimisu-backend/src/main/java/com/eickrono/thimisu/backend/aplicacao/servico/ProvisionamentoCadastroInternoServico.java`
  - hoje ainda cria ou atualiza `Pessoa` canônica dentro do backend do
    produto;
- `eickrono-thimisu-backend/modulos/thimisu-backend/src/main/java/com/eickrono/thimisu/backend/dominio/modelo/Pessoa.java`
- `eickrono-thimisu-backend/modulos/thimisu-backend/src/main/java/com/eickrono/thimisu/backend/dominio/modelo/PessoaHistorico.java`
- `eickrono-thimisu-backend/modulos/thimisu-backend/src/main/java/com/eickrono/thimisu/backend/dominio/modelo/TipoPessoa.java`
- `eickrono-thimisu-backend/modulos/thimisu-backend/src/main/java/com/eickrono/thimisu/backend/dominio/modelo/SexoPessoa.java`
- `eickrono-thimisu-backend/modulos/thimisu-backend/src/main/java/com/eickrono/thimisu/backend/dominio/modelo/CanalValidacaoTelefone.java`
- `eickrono-thimisu-backend/modulos/thimisu-backend/src/main/java/com/eickrono/thimisu/backend/dominio/repositorio/PessoaRepositorio.java`
- `eickrono-thimisu-backend/modulos/thimisu-backend/src/main/java/com/eickrono/thimisu/backend/dominio/repositorio/PessoaHistoricoRepositorio.java`

Arquivos e classes que devem consolidar a posse canonica em `identidade`:

- `eickrono-identidade-servidor/src/main/java/com/eickrono/api/identidade/aplicacao/servico/ProvisionamentoIdentidadeService.java`
  - ponto natural para consolidar a criacao, atualizacao e idempotencia
    (`repetir sem duplicar efeito`) de `Pessoa`;
- `eickrono-identidade-servidor/src/main/java/com/eickrono/api/identidade/apresentacao/api/CadastroInternoController.java`
  - candidato natural para expor o contrato interno minimo desta etapa;
- `eickrono-identidade-servidor/src/main/java/com/eickrono/api/identidade/dominio/modelo/Pessoa.java`
- `eickrono-identidade-servidor/src/main/java/com/eickrono/api/identidade/dominio/repositorio/PessoaRepositorio.java`

Arquivos que ainda dependem do contrato legado do backend do produto e
precisarao mudar no decorrer desta etapa:

- `eickrono-identidade-servidor/src/main/java/com/eickrono/api/identidade/infraestrutura/integracao/ProvisionadorPerfilDominioHttp.java`
  - hoje aponta para `/api/interna/identidade/provisionamentos`;
- `eickrono-identidade-servidor/src/main/java/com/eickrono/api/identidade/infraestrutura/integracao/ClienteContextoPessoaPerfilHttp.java`
  - hoje aponta para `/api/interna/identidade/contexto`.

Tabelas e migracoes que devem ser tratadas como legado no backend do produto:

- `pessoas`
  - criada em
    `eickrono-thimisu-backend/modulos/thimisu-backend/src/main/resources/db/migration/V2__criar_tabelas_identidade_acesso.sql`;
- `pessoas_historico`
  - criada em
    `eickrono-thimisu-backend/modulos/thimisu-backend/src/main/resources/db/migration/V7__criar_tabelas_historico_identidade.sql`;
- expansoes de cadastro local em
  `eickrono-thimisu-backend/modulos/thimisu-backend/src/main/resources/db/migration/V8__expandir_dados_cadastro_publico.sql`;
- regra de idempotencia local em
  `eickrono-thimisu-backend/modulos/thimisu-backend/src/main/resources/db/migration/V9__idempotencia_provisionamento_identidade.sql`.

Nesta etapa, a diretriz nao e apagar essas tabelas de imediato. A diretriz e
parar de aumentar responsabilidade sobre elas e comecar a trocar o ownership
canônico para `identidade`.

Testes que devem falhar primeiro:

1. `eickrono-identidade-servidor`
   - criar ou expandir
     `src/test/java/com/eickrono/api/identidade/aplicacao/servico/ProvisionamentoIdentidadeServiceTest.java`
     para cobrir:
     - criacao de `Pessoa` por contrato interno;
     - atualizacao de `Pessoa` existente;
     - repeticao com mesmo `cadastroId` devolvendo o mesmo resultado logico;
     - atualizacao sem duplicar `Pessoa`.
2. `eickrono-identidade-servidor`
   - criar teste de controller ou integracao para o contrato interno novo em
     `CadastroInternoController`;
   - o teste deve provar payload minimo, resposta minima e sem duplicidade.
3. `eickrono-thimisu-backend`
   - expandir
     `src/test/java/com/eickrono/thimisu/backend/apresentacao/web/IdentidadeInternaControllerTest.java`
     para cobrir o comportamento alvo:
     - backend do produto recebe `pessoaId` pronto;
     - backend do produto deixa de criar `Pessoa` canônica;
     - backend do produto trabalha so com o perfil do sistema.
4. `eickrono-autenticacao-servidor`
   - criar teste de contrato interno para o payload enviado a `identidade`,
     antes de trocar o consumidor real.

Sequencia segura de implementacao:

1. escrever os testes que falham em `identidade`;
2. ajustar `ProvisionamentoIdentidadeService` para virar a implementacao
   canônica de `Pessoa`;
3. expor ou ajustar o endpoint interno minimo em `CadastroInternoController`;
4. criar o teste de contrato no servico chamador antes de trocar a chamada
   real;
5. adaptar o fluxo chamador para usar `identidade` como fonte de `Pessoa`;
6. adaptar o backend do produto para receber `pessoaId` resolvido e parar de
   decidir a `Pessoa` canônica;
7. manter o legado so durante o tempo necessario para o corte seguro;
8. so depois marcar as tabelas, classes e contratos antigos como candidatos a
   remocao.

Criterio objetivo de encerramento desta etapa:

- existe uma chamada interna clara para criar ou atualizar `Pessoa` em
  `identidade`;
- repetir a mesma solicitacao com o mesmo `cadastroId` nao cria duplicidade;
- o backend do produto deixa de ser ponto de decisao da `Pessoa` canônica;
- os testes novos de `identidade`, contrato interno e backend do produto
  ficam verdes antes de qualquer corte do legado.

### Etapa 2. Mover a disponibilidade de `usuario + sistema` para a autenticacao

Objetivo:

- fazer a `autenticacao` responder pela regra de disponibilidade do perfil
  publico por sistema.

Projetos principais:

- `eickrono-autenticacao-servidor`
- `eickrono-thimisu-backend`

Pre-requisitos:

- decisao de negocio consolidada de que a unicidade e `usuario + sistema`.

TDD obrigatorio:

1. criar testes que falhem na `autenticacao` para:
   - consultar disponibilidade de `usuario + sistema`;
   - bloquear duplicidade no mesmo sistema;
   - permitir o mesmo `usuario` em sistemas diferentes.
2. criar testes que falhem no backend do produto para:
   - provar que ele deixa de ser dono dessa verificacao;
   - aceitar o identificador publico ja validado fora dele.
3. criar teste de contrato do endpoint interno de disponibilidade.

Implementacao minima:

- criar tabela/servico de disponibilidade na `autenticacao`;
- criar endpoint interno de consulta;
- migrar o consumidor do `thimisu-backend` para a `autenticacao`;
- manter alias e compatibilidade temporaria enquanto o cutover terminar.

Definicao de pronto:

- a disponibilidade deixa de ser decidida no backend do produto;
- o cadastro consulta a `autenticacao` para saber se `usuario + sistema`
  pode seguir.

#### Checklist tecnico detalhado da Etapa 2

Arquivos e classes que hoje ainda mantem essa verificacao dentro do backend do
produto:

- `eickrono-thimisu-backend/modulos/thimisu-backend/src/main/java/com/eickrono/thimisu/backend/apresentacao/web/IdentidadeInternaController.java`
  - hoje ainda expoe `GET /api/interna/identidade/usuarios/disponibilidade`;
- `eickrono-thimisu-backend/modulos/thimisu-backend/src/main/java/com/eickrono/thimisu/backend/apresentacao/web/dto/interna/DisponibilidadeUsuarioInternaResposta.java`
  - hoje ainda define o payload de resposta dessa consulta;
- `eickrono-thimisu-backend/modulos/thimisu-backend/src/main/java/com/eickrono/thimisu/backend/aplicacao/servico/ProvisionamentoCadastroInternoServico.java`
  - hoje ainda contem a regra usada para decidir se o `usuario` esta livre;
- `eickrono-thimisu-backend/modulos/thimisu-backend/src/main/java/com/eickrono/thimisu/backend/dominio/repositorio/UsuarioRepositorio.java`
  - hoje ainda expoe `existsByNomeExibicaoIgnoreCase` e
    `findFirstByNomeExibicaoIgnoreCase`, que sustentam a verificacao local.

Testes atuais do backend do produto que ajudam a localizar o comportamento
legado:

- `eickrono-thimisu-backend/modulos/thimisu-backend/src/test/java/com/eickrono/thimisu/backend/apresentacao/web/IdentidadeInternaControllerTest.java`
  - ja cobre a rota atual de disponibilidade e devera ser atualizado para
    refletir o corte de ownership.

Estrutura nova que precisa nascer na `autenticacao`:

Hoje nao existe nada equivalente no codigo do `eickrono-autenticacao-servidor`.
Esta etapa vai exigir criacao explicita de estrutura nova. O padrao mais
coerente com o projeto atual e seguir o mesmo estilo de `RealmResourceProvider`
que ja existe em
`modulos/modulo-eickrono-keycloak/src/main/java/com/eickrono/servidor/autorizacao/infraestrutura/versao/EstadoRuntimeRealmResourceProvider.java`.

Nomes sugeridos para essa estrutura nova:

- pacote novo:
  `modulos/modulo-eickrono-keycloak/src/main/java/com/eickrono/servidor/autorizacao/infraestrutura/disponibilidade/`
- recurso interno novo:
  `DisponibilidadeUsuarioSistemaRealmResourceProvider.java`
- factory do recurso:
  `DisponibilidadeUsuarioSistemaRealmResourceProviderFactory.java`
- servico ou leitor da regra:
  `DisponibilidadeUsuarioSistemaService.java`
- request interno:
  `ConsultaDisponibilidadeUsuarioSistemaRequest.java`
- response interna:
  `DisponibilidadeUsuarioSistemaResposta.java`

Esses nomes ainda nao existem hoje. Eles entram aqui como sugestao de destino,
para a implementacao nao nascer improvisada.

Persistencia que precisa nascer na `autenticacao`:

Hoje o repositorio de `autenticacao` nao tem nenhuma tabela, classe ou contrato
pronto para guardar a unicidade de `usuario + sistema`. Portanto, esta etapa
tambem precisa criar uma estrutura persistida nova.

Diretriz funcional dessa persistencia:

- um mesmo `usuario` pode existir em sistemas diferentes;
- o mesmo `usuario` nao pode ser reaproveitado no mesmo sistema quando a
  politica daquele sistema exigir unicidade;
- a regra deve responder por `usuario + sistema`, e nao mais por
  `nomeExibicao` solto do backend do produto.

O nome exato da tabela ainda pode ser decidido na implementacao, mas ela deve
representar claramente a reserva, consulta ou ocupacao de `usuario + sistema`
na `autenticacao`.

Testes que devem falhar primeiro:

1. `eickrono-autenticacao-servidor`
   - criar testes novos para provar:
     - consulta de disponibilidade de `usuario + sistema`;
     - bloqueio de duplicidade no mesmo sistema;
     - permissao do mesmo `usuario` em sistemas diferentes;
     - normalizacao do valor antes da consulta, se essa for a regra final.
2. `eickrono-autenticacao-servidor`
   - criar teste de contrato para o endpoint interno novo:
     - request minima;
     - response minima;
     - erro claro quando faltar `usuario` ou `sistema`.
3. `eickrono-thimisu-backend`
   - ajustar ou expandir `IdentidadeInternaControllerTest` para provar que:
     - o backend do produto deixa de ser o dono final dessa decisao;
     - o produto passa a consumir uma resposta externa de disponibilidade.

Sequencia segura de implementacao:

1. escrever os testes que falham na `autenticacao` para a regra
   `usuario + sistema`;
2. criar a persistencia minima dessa regra na `autenticacao`;
3. criar o endpoint interno novo seguindo o padrao de `RealmResourceProvider`;
4. escrever o teste de contrato desse endpoint antes de plugar qualquer
   consumidor;
5. adaptar o consumidor do backend do produto para consultar a `autenticacao`;
6. manter a verificacao local antiga so durante o periodo minimo de transicao;
7. so depois remover a responsabilidade local do backend do produto.

Criterio objetivo de encerramento desta etapa:

- a consulta de disponibilidade passa a existir de forma clara na
  `autenticacao`;
- a regra central passa a considerar `usuario + sistema`;
- o backend do produto deixa de decidir sozinho se o `usuario` esta livre;
- os testes novos da `autenticacao`, do contrato interno e do backend do
  produto ficam verdes antes de qualquer remocao do legado local.

### Etapa 3. Quebrar o provisionamento final em duas chamadas internas

Objetivo:

- separar claramente:
  - criacao/atualizacao de `Pessoa` na `identidade`;
  - criacao/atualizacao do perfil do produto no backend do produto.

Projetos principais:

- `eickrono-autenticacao-servidor`
- `eickrono-identidade-servidor`
- `eickrono-thimisu-backend`

Pre-requisitos:

- Etapa 1 concluida;
- Etapa 2 pelo menos com contrato interno pronto.

TDD obrigatorio:

1. criar teste de orquestracao que falhe na `autenticacao` para o fluxo:
   - autenticacao conclui conta central;
   - autenticacao chama identidade;
   - autenticacao chama backend do produto.
2. criar teste que falhe para garantir a ordem:
   - backend do produto so pode ser chamado depois de existir `pessoaId`.
3. criar teste que falhe para idempotencia do fluxo inteiro por `cadastroId`.

Implementacao minima:

- substituir o provisionamento unico antigo por duas chamadas internas;
- fazer a `autenticacao` carregar `pessoaId` da resposta da `identidade`;
- usar `pessoaId` para provisionar o perfil no produto.

Definicao de pronto:

- a `autenticacao` nao depende mais de um endpoint unico que mistura
  `Pessoa` e perfil de produto;
- o fluxo fica explicitamente dividido em duas etapas internas.

#### Checklist tecnico detalhado da Etapa 3

Onde a mistura ainda acontece hoje:

- `eickrono-identidade-servidor/src/main/java/com/eickrono/api/identidade/aplicacao/servico/CadastroContaInternaServico.java`
  - hoje ainda faz a orquestracao final do cadastro publico;
  - no metodo `finalizarCadastroPublico`, a `identidade`:
    - confirma o fluxo central;
    - chama o provisionamento do produto;
    - grava `pessoaId` e `usuarioId` de perfil no proprio `CadastroConta`;
    - monta a resposta final do cadastro.
- `eickrono-identidade-servidor/src/main/java/com/eickrono/api/identidade/aplicacao/servico/ProvisionadorPerfilDominioServico.java`
  - hoje ainda mistura:
    - consulta de disponibilidade de `usuario`;
    - provisionamento do perfil do produto.
- `eickrono-identidade-servidor/src/main/java/com/eickrono/api/identidade/infraestrutura/integracao/ProvisionadorPerfilDominioHttp.java`
  - hoje ainda aponta para:
    - `/api/interna/identidade/usuarios/disponibilidade`
    - `/api/interna/identidade/provisionamentos`
  - portanto, ainda trata o backend do produto como dono final do perfil e de
    parte da decisao do cadastro.
- `eickrono-identidade-servidor/src/main/java/com/eickrono/api/identidade/aplicacao/modelo/ProvisionamentoPerfilRealizado.java`
  - hoje ainda devolve junto:
    - `pessoaId`
    - `usuarioId`
    - `statusUsuario`
  - isso e sinal claro de que uma mesma chamada ainda mistura resultado de
    `Pessoa` com resultado do perfil do sistema.

Testes atuais que ancoram o comportamento legado:

- `eickrono-identidade-servidor/src/test/java/com/eickrono/api/identidade/aplicacao/servico/CadastroContaInternaServicoTest.java`
  - hoje ja cobre cadastro publico, disponibilidade de `usuario` e
    provisionamento do produto;
- `eickrono-identidade-servidor/src/test/java/com/eickrono/api/identidade/apresentacao/api/RegistroDispositivoControllerIT.java`
  - hoje ainda simula o retorno do provisionamento do produto durante a
    confirmacao do cadastro.

Estrutura nova que precisa nascer para o fluxo dividido:

Hoje o projeto de `autenticacao` ainda nao tem uma estrutura explicita para
orquestrar esse fechamento em duas chamadas internas. Esta etapa precisa criar
isso de forma clara.

Nomes sugeridos para nascer na `autenticacao`:

- pacote novo:
  `modulos/modulo-eickrono-keycloak/src/main/java/com/eickrono/servidor/autorizacao/infraestrutura/provisionamento/`
- orquestrador novo:
  `OrquestradorProvisionamentoCadastroService.java`
- cliente interno para `identidade`:
  `ClienteProvisionamentoPessoaIdentidade.java`
- cliente interno para o backend do produto:
  `ClienteProvisionamentoPerfilSistema.java`
- request da chamada para `identidade`:
  `ProvisionarPessoaRequest.java`
- response da chamada para `identidade`:
  `ProvisionarPessoaResposta.java`
- request da chamada para o produto:
  `ProvisionarPerfilSistemaRequest.java`
- response da chamada para o produto:
  `ProvisionarPerfilSistemaResposta.java`

Esses nomes ainda nao existem no codigo atual. Eles entram aqui como destino
sugerido para a implementacao nao nascer improvisada.

Contratos que precisam ser separados no alvo:

1. chamada central para `identidade`
   - responsabilidade:
     - criar ou atualizar `Pessoa`;
     - devolver `pessoaId`;
   - essa chamada nao deve decidir `usuarioId` do produto.
2. chamada central para o backend do produto
   - responsabilidade:
     - criar ou atualizar o perfil daquele sistema;
     - usar `pessoaId` ja resolvido;
     - devolver `usuarioId` do sistema e `statusPerfilSistema`.

Em outras palavras:

- `Pessoa` deve nascer ou ser atualizada primeiro;
- so depois o perfil do sistema pode ser criado ou atualizado.

Testes que devem falhar primeiro:

1. `eickrono-autenticacao-servidor`
   - criar teste de orquestracao para provar:
     - a chamada para `identidade` acontece antes;
     - a chamada para o produto so acontece se `pessoaId` existir;
     - repetir o mesmo `cadastroId` nao duplica o efeito final.
2. `eickrono-autenticacao-servidor`
   - criar testes de contrato separados:
     - um para a chamada `autenticacao -> identidade`;
     - outro para a chamada `autenticacao -> produto`.
3. `eickrono-identidade-servidor`
   - ajustar `CadastroContaInternaServicoTest` para parar de assumir que a
     propria `identidade` fecha o provisionamento do produto;
   - o teste deve passar a aceitar que a parte de perfil do sistema vira
     responsabilidade de outro passo interno.
4. `eickrono-thimisu-backend`
   - criar ou ajustar teste para provar que o backend do produto recebe
     `pessoaId` pronto e responde apenas com os dados do perfil do sistema.

Sequencia segura de implementacao:

1. escrever os testes que falham para a orquestracao em duas chamadas;
2. separar conceitualmente o contrato atual em:
   - contrato de `Pessoa`;
   - contrato de perfil do sistema;
3. criar o cliente interno da `autenticacao` para `identidade`;
4. criar o cliente interno da `autenticacao` para o backend do produto;
5. fazer a orquestracao nova carregar `pessoaId` da resposta da
   `identidade`;
6. usar esse `pessoaId` para provisionar o perfil do sistema;
7. ajustar `CadastroContaInternaServico` para deixar de ser o dono final
   dessa orquestracao;
8. manter compatibilidade temporaria so pelo tempo necessario para o corte
   seguro;
9. so depois remover o contrato unico legado.

Criterio objetivo de encerramento desta etapa:

- existem duas chamadas internas explicitas:
  - uma para `Pessoa` em `identidade`;
  - outra para perfil do sistema no backend do produto;
- a ordem fica protegida por teste:
  - primeiro `Pessoa`;
  - depois perfil do sistema;
- repetir a mesma operacao por `cadastroId` nao duplica o efeito logico;
- a `identidade` deixa de carregar sozinha a responsabilidade de fechar o
  provisionamento completo do produto.

### Etapa 4. Limpar o backend do produto do legado de identidade

Objetivo:

- deixar o backend do produto com nomes, contratos e entidades puramente de
  produto.

Projetos principais:

- `eickrono-thimisu-backend`

Pre-requisitos:

- Etapas 1, 2 e 3 concluidas.

TDD obrigatorio:

1. criar testes que falhem para provar que:
   - o backend do produto opera com `pessoaId` e `usuarioId` centrais;
   - ele nao precisa decidir disponibilidade de `usuario + sistema`;
   - ele nao precisa criar `Pessoa` canonica.
2. criar testes de contrato cobrindo os novos nomes internos do backend de
   produto.

Implementacao minima:

- remover ou desativar contratos internos legados de `identidade`;
- renomear estruturas legadas para nomes de produto ou de perfil de sistema;
- manter apenas copias locais necessarias para o produto funcionar.

Definicao de pronto:

- o backend do produto nao expoe mais namespace final de `identidade`;
- o codigo deixa de sugerir ownership de `Pessoa` ou de conta central.

#### Checklist tecnico detalhado da Etapa 4

O que ainda carrega semantica de `identidade` dentro do backend do produto:

- controller e rotas:
  - `eickrono-thimisu-backend/modulos/thimisu-backend/src/main/java/com/eickrono/thimisu/backend/apresentacao/web/IdentidadeInternaController.java`
  - hoje ainda usa `@RequestMapping("/api/interna/identidade")`
- DTOs internos do controller:
  - `ContextoPessoaInternaResposta.java`
  - `DisponibilidadeUsuarioInternaResposta.java`
  - `ProvisionamentoCadastroInternoRequest.java`
  - `ProvisionamentoCadastroInternoResposta.java`
- servicos legados:
  - `ProvisionamentoCadastroInternoServico.java`
  - `HistoricoIdentidadeServico.java`
- repositorios legados:
  - `PessoaRepositorio.java`
  - `PessoaHistoricoRepositorio.java`
  - `UsuarioHistoricoRepositorio.java`
- modelos legados:
  - `Pessoa.java`
  - `PessoaHistorico.java`
  - `UsuarioHistorico.java`
  - `StatusUsuarioThimisu.java`
- configuracao de seguranca ainda apontando para o namespace legado:
  - `eickrono-thimisu-backend/modulos/thimisu-backend/src/main/java/com/eickrono/thimisu/backend/infraestrutura/configuracao/SegurancaConfiguracao.java`
  - hoje ainda libera explicitamente:
    - `GET /api/interna/identidade/contexto`
    - `POST /api/interna/identidade/provisionamentos`

Tabelas e migracoes legadas que precisam ser reclassificadas ou removidas ao
final da limpeza:

- `pessoas`
  - hoje criada em `V2__criar_tabelas_identidade_acesso.sql`
- `pessoas_historico`
  - hoje criada em `V7__criar_tabelas_historico_identidade.sql`
- `usuarios_historico`
  - hoje ligado ao mesmo conjunto legado de historico
- `V8__expandir_dados_cadastro_publico.sql`
  - hoje ainda amplia a responsabilidade local dessas tabelas
- `V9__idempotencia_provisionamento_identidade.sql`
  - hoje ainda reforca a ideia de idempotencia (`repetir sem duplicar efeito`)
    do provisionamento de `Pessoa` no backend do produto

Nesta etapa, a regra nao e apagar tudo de uma vez. A regra e:

- primeiro tirar o ownership funcional;
- depois renomear o que sobrar para semantica de produto;
- so no final remover o que nao fizer mais sentido.

Testes atuais que ancoram o legado e precisarao ser atualizados:

- `eickrono-thimisu-backend/modulos/thimisu-backend/src/test/java/com/eickrono/thimisu/backend/apresentacao/web/IdentidadeInternaControllerTest.java`
  - hoje ancora o namespace e os contratos de `identidade`;
- `eickrono-thimisu-backend/modulos/thimisu-backend/src/test/java/com/eickrono/thimisu/backend/dominio/repositorio/RepositoriosIdentidadeTest.java`
  - hoje ancora repositorios e modelos com nome e semantica de `identidade`.

Nomes de destino sugeridos para o que realmente precisa continuar existindo no
backend do produto:

- `IdentidadeInternaController`
  - destino sugerido:
    `PerfilSistemaInternoController`
- `/api/interna/identidade/contexto`
  - destino sugerido:
    `/api/interna/perfis-sistema/contexto`
- `/api/interna/identidade/provisionamentos`
  - destino sugerido:
    `/api/interna/perfis-sistema/provisionamentos`
- `ProvisionamentoCadastroInternoServico`
  - destino sugerido:
    `ProvisionamentoPerfilSistemaServico`
- `HistoricoIdentidadeServico`
  - destino sugerido:
    `HistoricoPerfilSistemaServico`
- `StatusUsuarioThimisu`
  - destino sugerido:
    `StatusPerfilSistema`

Esses nomes de destino so fazem sentido depois que as Etapas 1, 2 e 3 ja
tirarem do backend do produto a responsabilidade canônica por `Pessoa`.

Testes que devem falhar primeiro:

1. `eickrono-thimisu-backend`
   - criar ou ajustar testes para provar:
     - o namespace interno novo nao fala mais em `identidade`;
     - os contratos restantes respondem apenas por perfil do sistema;
     - o backend do produto nao cria `Pessoa` canônica;
     - o backend do produto nao decide disponibilidade de `usuario + sistema`.
2. `eickrono-thimisu-backend`
   - criar testes de regressao para garantir que o produto continua operando
     com:
     - `pessoaId` central;
     - `usuarioId` do sistema;
     - `statusPerfilSistema`.
3. documentacao e contrato
   - criar ou atualizar testes de contrato para os endpoints internos novos,
     antes de remover os endpoints legados.

Sequencia segura de implementacao:

1. concluir as Etapas 1, 2 e 3 antes de qualquer limpeza agressiva;
2. escrever os testes que falham para o namespace e os contratos novos;
3. criar aliases temporarios entre rotas antigas e rotas novas, se isso
   reduzir risco de corte;
4. renomear controller, DTOs e servicos internos para linguagem de produto ou
   de perfil do sistema;
5. ajustar `SegurancaConfiguracao` para proteger o namespace novo;
6. parar de usar `PessoaRepositorio` e `PessoaHistoricoRepositorio` como
   centros do fluxo do produto;
7. remover classes, repositorios e tabelas legadas somente quando nao houver
   mais consumidor real;
8. por ultimo, atualizar testes e documentacao final para refletir apenas o
   desenho limpo.

Criterio objetivo de encerramento desta etapa:

- o backend do produto deixa de expor rotas internas com nome de
  `identidade`;
- o backend do produto passa a falar apenas em perfil do sistema, contexto do
  sistema e status do sistema;
- classes, DTOs e servicos remanescentes deixam de usar nomes centrados em
  `identidade` ou em produto quando o conceito e do ecossistema;
- tabelas e migracoes legadas ficam marcadas para remocao ou ja removidas,
  conforme o corte executado.

### Etapa 5. Separar fisicamente o banco do produto

Objetivo:

- isolar o banco do produto Thimisu dos bancos centrais.

Projetos principais:

- `eickrono-thimisu-backend`
- `eickrono-identidade-servidor`
- `eickrono-autenticacao-servidor`
- infraestrutura

Pre-requisitos:

- Etapas 1 a 4 concluidas;
- contratos internos e copias locais estaveis.

TDD obrigatorio:

1. criar testes que falhem para validar:
   - o produto continua subindo e operando com banco proprio;
   - os contratos internos continuam resolvendo `pessoaId`, `usuarioId` e
     perfil sem leitura cruzada direta do banco central.
2. criar testes de migracao de dados ou checks automatizados para:
   - quantidade de registros copiados;
   - integridade de chaves externas logicas;
   - ausencia de dependencia ativa do schema antigo.

Implementacao minima:

- criar novo banco do produto;
- migrar apenas o que continuar sendo do produto;
- ajustar `docker compose`, infraestrutura e secrets;
- cortar a dependencia fisica do banco central.

Definicao de pronto:

- o produto sobe com banco proprio;
- `identidade` e `autenticacao` deixam de precisar compartilhar a base do
  produto;
- qualquer copia local restante passa a vir por comunicacao entre servidores,
  nao por leitura cruzada de banco.

#### Checklist tecnico detalhado da Etapa 5

Ponto importante sobre o estado atual:

- esta etapa nao parte do zero;
- o `eickrono-thimisu-backend` ja tem perfis de aplicacao apontando para banco
  e schema proprios:
  - `src/main/resources/application-dev.yml`
    - `jdbc:postgresql://localhost:5432/eickrono_thimisu`
    - schema `thimisu`
  - `src/main/resources/application-stg.yml`
    - `jdbc:postgresql://banco-postgres:5432/eickrono_thimisu_stg`
    - schema `thimisu_stg`
  - `src/main/resources/application-prd.yml`
    - `jdbc:postgresql://rds-thimisu.eickrono.internal:5432/eickrono_thimisu`
    - schema `thimisu`
- o `eickrono-identidade-servidor` tambem ja tem perfis apontando para banco e
  schema proprios:
  - `src/main/resources/application-dev.yml`
    - `jdbc:postgresql://localhost:5432/eickrono_identidade`
    - schema `identidade`
  - `src/main/resources/application-stg.yml`
    - `jdbc:postgresql://banco-postgres:5432/eickrono_identidade_stg`
    - schema `identidade_stg`
  - `src/main/resources/application-prod.yml`
    - `jdbc:postgresql://rds-identidade.eickrono.internal:5432/eickrono_identidade`
    - schema `identidade`

Isso significa que a aplicacao ja fala a linguagem correta de separacao. O que
ainda falta fechar nesta etapa e:

- garantir que a infraestrutura executada em cada ambiente respeite de fato a
  separacao fisica;
- migrar apenas os dados que continuarem pertencendo ao produto;
- validar que o backend do produto nao depende mais de leitura cruzada do banco
  central.

Arquivos de infraestrutura e configuracao que precisam entrar na revisao desta
etapa:

- `eickrono-thimisu-backend/infraestrutura/dev/docker-compose.yml`
- `eickrono-thimisu-backend/infraestrutura/stg/docker-compose.yml`
- `eickrono-thimisu-backend/modulos/thimisu-backend/src/main/resources/application-dev.yml`
- `eickrono-thimisu-backend/modulos/thimisu-backend/src/main/resources/application-stg.yml`
- `eickrono-thimisu-backend/modulos/thimisu-backend/src/main/resources/application-prd.yml`
- `eickrono-identidade-servidor/src/main/resources/application-dev.yml`
- `eickrono-identidade-servidor/src/main/resources/application-stg.yml`
- `eickrono-identidade-servidor/src/main/resources/application-prod.yml`
- `eickrono-autenticacao-servidor/infraestrutura/dev/docker-compose.yml`
- `eickrono-autenticacao-servidor/infraestrutura/stg/docker-compose.yml`

O que precisa ser verificado nesses arquivos:

- se cada servico aponta para o banco esperado;
- se cada servico aponta para o schema esperado;
- se o ambiente ainda esta reutilizando o mesmo host ou a mesma instancia de
  Postgres so por conveniencia operacional;
- se a separacao real do produto esta sendo feita por banco, por schema ou
  apenas por convencao documental.

Dados que precisam ser tratados nesta migracao:

1. dados que permanecem no banco do produto
   - perfil do sistema;
   - historico do perfil do sistema;
   - dados de dominio do produto;
   - copias locais necessarias para interface e operacao do produto.
2. dados que nao devem mais permanecer como fonte canônica no banco do produto
   - `Pessoa`;
   - historico canônico de `Pessoa`;
   - qualquer forma de acesso central;
   - qualquer dado que faca o produto parecer dono da conta central.

Testes que devem falhar primeiro:

1. `eickrono-thimisu-backend`
   - criar teste de inicializacao ou integracao para provar que o produto sobe
     usando apenas o banco dele;
   - o teste deve falhar se houver dependencia obrigatoria de schema central.
2. `eickrono-thimisu-backend`
   - criar teste de regressao para provar que o produto continua operando com:
     - `pessoaId` central;
     - `usuarioId` do sistema;
     - dados locais do produto;
     - sem consulta direta ao banco de `identidade`.
3. infraestrutura
   - criar validacao automatizada para conferir:
     - JDBC URL esperada por ambiente;
     - schema esperado por ambiente;
     - ausencia de leitura cruzada de tabelas centrais.
4. migracao de dados
   - criar script ou check automatizado para conferir:
     - quantidade de registros migrados;
     - consistencia entre `pessoaId`, `usuarioId` e perfil local;
     - ausencia de dado central que deveria ter sido removido do produto.

Sequencia segura de implementacao:

1. concluir as Etapas 1, 2, 3 e 4 antes do corte fisico do banco;
2. listar exatamente quais tabelas do banco atual ainda pertencem ao produto;
3. listar exatamente quais tabelas legadas ainda estao no produto so por
   heranca da cisao incompleta;
4. preparar script de migracao dos dados que continuarem sendo do produto;
5. preparar validacoes automaticas de contagem e consistencia;
6. ajustar compose, variaveis de ambiente, secrets e perfis para o banco final
   do produto;
7. executar a copia ou migracao dos dados;
8. subir o produto apontando apenas para o banco dele;
9. validar que toda leitura restante de identidade ou autenticacao acontece por
   comunicacao entre servidores, e nao por banco compartilhado;
10. so depois desativar o acesso antigo.

Criterio objetivo de encerramento desta etapa:

- o `eickrono-thimisu-backend` sobe e opera usando apenas o banco do produto;
- os dados centrais continuam nos bancos de `autenticacao` e `identidade`;
- o produto usa apenas os dados locais necessarios e os identificadores
  centrais recebidos por comunicacao entre servidores;
- nao existe mais dependencia operacional de leitura cruzada do banco central
  para o fluxo principal do produto.

## Criterios gerais de conclusao por etapa

- os testes novos devem existir antes do corte do legado;
- testes unitarios, de integracao e de contrato da etapa devem ficar verdes;
- o comportamento legado a ser removido precisa estar explicitamente coberto por
  pelo menos um teste de regressao;
- toda etapa encerrada deve atualizar:
  - consolidado;
  - README ou guia principal afetado;
  - TODO local se ainda sobrar desdobramento posterior.

## Desenho recomendado de bancos por projeto

### Direcao recomendada

Direcao alvo:

- `eickrono-autenticacao-servidor` com banco proprio;
- `eickrono-identidade-servidor` com banco proprio;
- `eickrono-thimisu-backend` no estado alvo, com banco proprio.

Direcao transitoria aceita:

- `autenticacao` e `identidade` ainda podem dividir a mesma base fisica por um
  tempo, desde que a separacao por schema e ownership seja rigida;
- o banco do produto Thimisu deve ser separado dos bancos centrais o quanto
  antes, para evitar que o dominio do produto continue acoplado ao ecossistema
  central.

### O que fica em cada banco

| Projeto | Banco alvo | Ownership principal | Exemplos de tabelas ou conjuntos de dados |
| --- | --- | --- | --- |
| `eickrono-autenticacao-servidor` | banco de autenticacao | conta central, sessao, vinculo por sistema, seguranca | conta central, credenciais, sessao, refresh, dispositivos, atestacao, brokers sociais, disponibilidade de `usuario + sistema`, perfil por sistema |
| `eickrono-identidade-servidor` | banco de identidade | `Pessoa` canônica e dados cadastrais compartilhados | pessoa, pessoa_historico, contatos canonicos, documentos e demais dados de identidade compartilhada |
| `eickrono-thimisu-backend` no estado alvo | banco do produto | dominio de estudo e leitura local do que o produto precisa | deck, card, revisao, progresso, configuracoes de produto, copias locais minimas de pessoa/perfil usadas pela interface do produto |

### O que vale copiar para o banco do produto

O banco do produto nao deve virar nova fonte de verdade de identidade ou de
autenticacao. O recomendado e manter uma copia local apenas do necessario para
o produto funcionar com boa performance e com menor acoplamento.

Nesta leitura consolidada:

- `clienteEcossistema` identifica qual app, site ou software do ecossistema
  esta sendo provisionado;
- `identificadorPublicoSistema` e o nome publico do perfil dentro daquele
  sistema, por exemplo o usuario exibido ao usuario final.

Como cada backend do produto normalmente ja representa um unico
`clienteEcossistema`, esse dado nao precisa necessariamente ser persistido na
base do produto. Ele pode ficar resolvido por configuracao, roteamento ou
mapeamento do proprio sistema, desde que o contrato interno saiba para qual
cliente esta operando.

| Dado no banco do produto | Fonte de verdade | Motivo da copia local | Atualizacao recomendada |
| --- | --- | --- | --- |
| `pessoaId` | `identidade` | correlacao canônica com a pessoa | no provisionamento inicial e em reconciliacao |
| `usuarioId` | `autenticacao` | correlacao com a conta/perfil do ecossistema | no provisionamento inicial e em reconciliacao |
| `identificadorPublicoSistema` | `autenticacao` | exibicao e referencia do perfil no produto | no provisionamento inicial e quando houver troca |
| `nomeCompletoAtual` | `identidade` | leitura rapida pela interface do produto | no provisionamento inicial e quando o produto decidir atualizar a copia |
| `dataNascimentoAtual` | `identidade` | uso do produto sem depender de leitura central a cada acesso | no provisionamento inicial e quando o produto decidir atualizar a copia |
| `emailsAtuais` | `identidade` | exibicao e uso local do produto | no provisionamento inicial e quando o produto decidir atualizar a copia |
| `telefonesAtuais` | `identidade` | exibicao e uso local do produto | no provisionamento inicial e quando o produto decidir atualizar a copia |
| `avatarAtual` | `identidade` | leitura rapida pela interface do produto | no provisionamento inicial e quando o produto decidir atualizar a copia |
| `statusPerfilSistema` | `autenticacao` | bloquear uso local quando o vinculo estiver suspenso | sob evento de mudanca e validacao no login |

### O que nao deve ser copiado como dado autoritativo no produto

- senha;
- hash de senha;
- refresh token;
- codigo de recuperacao;
- segredos de broker social;
- estado interno de dispositivo confiavel;
- trilhas sensiveis de autenticacao;
- qualquer regra que faca do banco do produto uma segunda fonte de verdade da
  conta central.

## Conversa recomendada por backchannel entre os projetos

Neste documento, `backchannel` significa comunicacao interna entre servidores,
sem passar pelo app do usuario.

### 1. `autenticacao -> identidade`

Objetivo:

- criar ou atualizar a `Pessoa` canonica depois que a parte sensivel do fluxo
  for aprovada.

Quando chamar:

- no fim do cadastro;
- em ajustes cadastrais relevantes;
- em reconciliacao quando o ecossistema detectar divergencia.

Payload base:

- `cadastroId`
- `sub`
- `nomeCompleto`
- `tipoPessoa`
- `nomeFantasia`
- `sexo`
- `paisNascimento`
- `dataNascimento`
- `emailPrincipal`
- `telefonePrincipal`
- `canalValidacaoTelefone`

Resposta base:

- `pessoaId`
- `statusProvisionamento`
- `versaoContexto`

### 2. `autenticacao -> eickrono-thimisu-backend (estado alvo)`

Objetivo:

- criar ou atualizar o perfil daquele sistema sem recriar a conta central nem a
  `Pessoa`.

Quando chamar:

- depois que `identidade` devolver `pessoaId`;
- quando houver troca relevante de `identificadorPublicoSistema`,
  `statusPerfilSistema` ou metadados necessarios ao produto.
- antes de qualquer criacao, edicao ou remocao no produto, o servico chamador
  deve verificar se o produto responde a uma sondagem operacional simples;
- mesmo depois da sondagem positiva, a operacao real ainda deve ser executada
  com `timeout` curto (tempo maximo de espera) e tratamento de falha, porque o
  produto pode cair entre a sondagem e a chamada final.

Payload base:

- `usuarioId`
- `pessoaId`
- `identificadorPublicoSistema`
- `statusPerfilSistema`
- `nomeCompletoAtual`
- `dataNascimentoAtual`
- `emailsAtuais`
- `telefonesAtuais`
- `avatarAtual`

Resposta base:

- `perfilSistemaId`
- `statusProvisionamento`
- `versaoPerfil`

### 3. `eickrono-thimisu-backend (estado alvo) -> identidade`

Objetivo:

- consultar contexto canonico de `Pessoa` quando o produto realmente precisar.

Uso esperado:

- leitura;
- enriquecimento pontual;
- jamais para virar fonte de verdade local da identidade.

### 4. `eickrono-thimisu-backend (estado alvo) -> autenticacao`

Objetivo:

- consultar situacao operacional do perfil por sistema quando isso influenciar o
  uso do produto.

Uso esperado:

- validacao de acesso;
- consulta de disponibilidade ou bloqueio do perfil;
- nunca para assumir a autoria da conta central.

## Contratos internos que precisariam mudar

| Contrato atual | Problema atual | Contrato alvo |
| --- | --- | --- |
| `GET /api/interna/identidade/usuarios/disponibilidade` no `thimisu-backend` | disponibilidade de `usuario + sistema` esta no projeto errado | endpoint interno na `autenticacao` |
| `POST /api/interna/identidade/provisionamentos` no `thimisu-backend` | mistura criacao de `Pessoa` com criacao de perfil de sistema | dois contratos: `autenticacao -> identidade` e `autenticacao -> backend do produto` |
| `GET /api/interna/identidade/contexto` no `thimisu-backend` | contexto canônico de pessoa esta exposto pelo projeto errado | leitura interna no `eickrono-identidade-servidor` |

## Ordem de migracao sem quebrar o que ja existe

1. manter o fluxo atual funcional, mas adicionar os novos contratos de
   `backchannel` (comunicacao interna entre servidores) em paralelo;
2. fazer `autenticacao` chamar `identidade` para criar/atualizar `Pessoa`;
3. fazer `autenticacao` chamar o backend do produto para criar/atualizar o
   perfil daquele sistema;
4. fazer o produto consumir os ids centrais e as copias locais necessarias em
   vez de manter `Pessoa` canônica local;
5. criar a trilha persistida de pendencias de integracao com o produto e o
   `scheduler` (rotina agendada) de `retries` (novas tentativas automaticas);
6. migrar a verificacao de disponibilidade de `usuario + sistema` para a
   `autenticacao`;
7. remover do backend do produto os contratos e tabelas que sobraram do legado
   de identidade;
8. por fim, separar fisicamente o banco do produto Thimisu dos bancos centrais;
9. depois da separacao fisica, limpar nomes legados, schemas legados e
   documentacao antiga.

## Decisoes consolidadas sobre copia local de dados no produto

### 1. Regra geral

No momento do cadastro, a fonte de verdade dos dados cadastrais compartilhados
continua sendo a `identidade`.

O produto recebe uma copia local desses dados para operar com independencia.

Depois disso, `identidade` e produto podem evoluir de forma independente, e uma
alteracao em um lado nao precisa repercutir automaticamente no outro.

### 2. Isso nao significa replicacao automatica entre bases

Nao existe, nesta direcao consolidada, a ideia de replicacao automatica entre
bancos.

O que existe e:

- copia inicial por `backchannel` (comunicacao interna entre servidores);
- novas comunicacoes entre os servidores quando o negocio exigir;
- reconciliacao apenas quando houver necessidade definida pelo fluxo.

### 3. Consequencia pratica

O backend do produto passa a ter autonomia local de leitura e de evolucao do
seu proprio contexto, sem transformar sua copia local em nova fonte de verdade
do ecossistema.

## Respostas ja consolidadas sobre consistencia eventual

As respostas abaixo ja ficam tratadas como decisao corrente deste documento.

### 1. Nome, avatar, data de nascimento, emails e telefones no produto

- no cadastro inicial, a fonte de verdade vem da `identidade`;
- o produto recebe uma copia local;
- depois disso, produto e `identidade` podem divergir, porque sao projetos
  diferentes.

### 2. Momento de atualizacao da copia local do produto

- a regra base fica como `2A`: atualizar no provisionamento inicial.

### 3. Reconciliacao periodica

- a regra base fica como `3C`: reconciliacao sob demanda, quando houver erro,
  divergencia ou necessidade real de negocio.

### 4. Vencedor em caso de divergencia

- a regra base fica como `4C`: depende do campo; a decisao precisa ser por tipo
  de dado.

### 5. Metadados proprios do produto

- a regra base fica como `7C`: depende do tipo de dado; precisa de
  classificacao.

### 6. Nao usar a ideia de copia local como se fosse sincronizacao automatica

- a leitura correta aqui passa a ser `copia local` feita por comunicacao entre
  servidores, e nao replicacao automatica entre bancos.

## Decisao consolidada sobre indisponibilidade do produto

Quando um servico central precisar criar, editar ou apagar algo no backend do
produto, o fluxo recomendado passa a ser:

1. fazer uma sondagem operacional simples no produto;
2. se o produto nao responder, decidir entre:
   - parar o processo com erro claro de comunicacao, quando aquele fluxo
     realmente depender do produto naquele instante;
   - ou registrar pendencia e seguir com a parte central, quando o produto
     puder ser concluido depois;
3. se o produto responder, executar a operacao real;
4. se a operacao real falhar, registrar a pendencia para `retry`
   (nova tentativa automatica) controlado.

### O que a sondagem resolve

- evita iniciar um processo quando o produto ja esta claramente fora do ar;
- melhora a mensagem devolvida para operacao ou para o usuario;
- ajuda a distinguir "produto indisponivel" de "erro de regra de negocio".

### O que a sondagem nao resolve sozinha

- ela nao garante que o produto continuara de pe ate o fim da operacao;
- por isso a chamada real continua precisando de `timeout` (tempo maximo de
  espera), `idempotencia` (repetir a mesma operacao sem duplicar efeito) e
  tratamento de falha.

### Comportamento consolidado para o cadastro

Se a conta central e a `Pessoa` ja tiverem sido criadas, mas o produto nao
conseguir concluir sua parte, a direcao recomendada fica:

- o cadastro central continua valido;
- o problema do produto vira pendencia operacional;
- o login central continua podendo acontecer;
- o erro so precisa aparecer quando algum app realmente tentar usar o backend
  do produto e ele ainda nao tiver concluido sua parte;
- aviso especial ao usuario nao e regra padrao; ele so passa a fazer sentido
  quando a pendencia durar tempo demais ou quando houver fila acumulada fora do
  normal;
- o sistema agenda nova tentativa automatica.

## Registro minimo de pendencias para retry (nova tentativa automatica)

Um boolean isolado de `concluido` ajuda, mas sozinho costuma ser pouco.

O minimo recomendado e registrar algo como:

- `uriEndpoint`
- `metodoHttp`
- `payloadJson`
- `concluido`
- `statusPendencia`
- `produtoAlvo`
- `idempotencyKey` (chave para impedir duplicidade)
- `versaoContrato`
- `ultimaTentativaEm`
- `proximaTentativaEm`
- `tentativasRealizadas`
- `codigoUltimoErro`
- `mensagemUltimoErro`

### Leitura em linguagem simples

- `uriEndpoint`
  diz para qual endpoint do produto a tentativa precisa ser refeita;
- `metodoHttp`
  diz se a chamada sera `POST`, `PUT`, `PATCH` ou `DELETE`;
- `payloadJson`
  guarda exatamente o corpo JSON que precisa ser reenviado;
- `concluido = true`
  significa que o produto terminou a parte dele com sucesso;
- `concluido = false`
  significa que ainda ha algo pendente;
- `statusPendencia`
  explica se esta pendente, tentando de novo, concluido ou falhou de vez;
- `idempotencyKey`
  evita que o produto crie ou altere algo em duplicidade quando a nova
  tentativa automatica repetir a mesma entrega;
- `versaoContrato`
  ajuda a saber com qual formato de payload aquela entrega foi criada;
- os campos de tentativa ajudam o `scheduler` (rotina agendada) a saber quando
  deve tentar de novo.

### Scheduler recomendado

Recomendacao:

- um `scheduler` periodico (rotina agendada) consulta as pendencias com
  `concluido = false`;
- ele tenta novamente apenas as que estiverem prontas para `retry` (nova
  tentativa automatica);
- quando o produto concluir sua parte, a pendencia passa para `concluido = true`;
- se atingir limite de tentativas, a pendencia fica marcada para intervencao
  operacional.

### Melhor desenho pratico neste momento

- usar `uriEndpoint + metodoHttp + payloadJson` como base da entrega que o
  `scheduler` (rotina agendada) precisa refazer;
- usar `concluido` como coluna simples de leitura;
- junto dela, manter `statusPendencia`, `idempotencyKey`, tentativas e erro da
  ultima tentativa;
- nao depender apenas de um boolean solto.

## Regras ja consolidadas para a fila de pendencias

- a fila de pendencias cobre todas as operacoes que precisem criar, alterar ou
  apagar algo no backend do produto;
- o tempo entre tentativas da rotina agendada sera parametrizavel no banco da
  `autenticacao`;
- a quantidade maxima de tentativas antes de escalar para operacao tambem sera
  parametrizavel no banco da `autenticacao`.

## Pontos que ainda podem precisar de detalhamento futuro

Os pontos que ainda podem ser refinados depois tendem a ser:

- politica exata de novas tentativas automaticas por produto;
- valores iniciais padrao para tempo entre tentativas da rotina agendada;
- valores iniciais padrao para limite de tentativas antes de escalar para
  operacao.


## Leitura recomendada em seguida

1. `analise_fronteiras_funcionais_autenticacao_identidade_thimisu_backend.md`
2. `matriz_migracao_autenticacao_identidade_thimisu_backend.md`
3. `../eickrono-thimisu-backend/docs/proposta_cisao_identidade_thimisu.md`
