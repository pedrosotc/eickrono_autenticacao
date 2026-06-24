# Especificacao - Servico administrativo de reset/exclusao de acesso e conta de produto

## Objetivo

Definir o servico administrativo que executa o caminho inverso do cadastro de
usuario para permitir:

- reutilizar e-mail, usuario e vinculos sociais em testes controlados;
- atender solicitacoes futuras de exclusao do usuario de autenticacao/acesso e
  do perfil em um produto especifico;
- remover dados pessoais do backend do produto especifico quando aplicavel;
- preservar a pessoa canonica do servidor de identidade, salvo em servico
  especifico futuro de exclusao de pessoa.

Este documento define o comportamento alvo, os limites do escopo, a matriz de
dados e o estado de implementacao do servico para evitar remocoes indevidas.

## Fontes usadas para esta especificacao

Esta especificacao deve ser lida junto com:

| Documento | Uso nesta especificacao |
| --- | --- |
| `eickrono-thimisu/eickrono-thimisu-app/docs/politica_exclusao_dados_thimisu.md` | Regra juridica/funcional para apagar dados exclusivos do perfil do Thimisu e preservar dados compartilhados, financeiros, auditoria e obrigacoes legais. |
| `eickrono-thimisu-backend/docs/fluxo_cadastro_login_nativo.md` | Define que cadastro/login entram pela borda de identidade/autenticacao e que o `thimisu-backend` recebe provisionamento depois da conta central/pessoa estarem prontas. |
| `eickrono-thimisu-backend/docs/proposta_cisao_identidade_thimisu.md` | Separa pessoa canonica, usuario de autenticacao e usuario/perfil do produto. |
| `eickrono-identidade-servidor/documentacao/roteiro_qa_avatar_social_autenticacao_thimisu.md` | Lista cenarios de cadastro/login/social/avatar que precisam ser repetiveis apos exclusao de cadastro/produto. |

Regra de precedencia:

1. Para exclusao de cadastro/produto de um usuario, este documento prevalece
   sobre scripts manuais de limpeza ampla de banco.
2. Runbooks operacionais podem existir para manutencao de ambiente, mas nao
   definem a regra funcional deste servico.
3. Politicas juridicas, privacidade, financeiro, auditoria, seguranca e dados
   compartilhados prevalecem sobre conveniencia operacional.

## Resumo tecnico fechado

| Area | Decisao |
| --- | --- |
| Orquestrador | `eickrono-autenticacao-servidor`. |
| Endpoint administrativo | `POST /api/interna/usuarios/exclusoes`. |
| Entrada principal | `produto + usuarioPublicoProduto` ou `produto + perfilProdutoId`. |
| Chaves auxiliares | `email`, `sub` e `provedoresSociais`, apenas para suporte/diagnostico e validacao de conflito. |
| Simulacao obrigatoria | `dryRun=true` deve resolver todos os alvos e listar acoes antes de qualquer execucao real. |
| Execucao real | `dryRun=false` so pode executar um plano consistente, com motivo, permissao e auditoria. |
| Pessoa canonica | Preservada. Exclusao da pessoa canonica sera outro servico. |
| Perfil do produto | Removido ou anonimizado no produto informado, sem afetar outros produtos. |
| Usuario de autenticacao/acesso | Removido junto com credenciais, sessoes, dispositivos, formas de acesso e vinculos sociais do alvo. |
| Rede social do usuario | O vinculo social do usuario e apagado; a configuracao global Google/Apple nunca e apagada. |
| Avatar | Avatar controlado pela Eickrono deve materializar pendencia com `bucket + storageKey` antes da limpeza logica. URL externa social nao e apagada. |
| Legado social | Fluxos/tabelas de contexto social pendente nao fazem parte do contrato novo e nao devem reaparecer no `dryRun` nem na execucao. |
| Estruturas historicas pessoa/perfil | Nao sao fluxo funcional novo. Podem existir apenas como compatibilidade tecnica ate migracao final e devem bloquear, nao consolidar automaticamente, se houver conflito. |
| Rastreamento de versao | Nao criar versionamento especial por rota. Rastrear por documento, contrato revisado, commit e tag Git/release da entrega. |

## Nao objetivo

Este servico nao deve apagar a pessoa canonica do ecossistema no servidor de
identidade, nem seus dados pessoais canonicos. A pessoa e seus dados civis
continuam existindo no servidor de identidade para preservar a fonte canonica
central.

Exclusao de pessoa, dados civis, dados cadastrais canonicos compartilhados ou
historico central de identidade deve ser outro servico, mais raro, com regra
juridica e operacional propria.

Este servico tambem nao e uma exclusao global em todos os produtos da Eickrono.
Ele atua sobre:

- o usuario de autenticacao/acesso usado nos servidores de autorizacao,
  autenticacao e identidade;
- as formas de acesso, credenciais, sessoes, dispositivos, avatares e vinculos
  sociais ligados a esse usuario de acesso;
- o perfil pessoal no backend do produto especifico informado na solicitacao.

Este servico tambem nao deve apagar configuracoes globais, como:

- realm `master` do Keycloak;
- usuario `admin` do realm `master`;
- clients do Keycloak;
- identity providers Google, Apple ou outros;
- secrets, chaves, certificados e configuracoes de provedores sociais;
- migrations;
- catalogos globais;
- configuracoes de aplicacao;
- service accounts.

## Escopo funcional

O servico deve remover ou anonimizar tudo que impede um novo cadastro usando os
mesmos dados de acesso, sem apagar a pessoa canonica:

- e-mail usado como credencial;
- usuario publico do produto;
- usuario de autenticacao;
- credenciais;
- sessoes;
- refresh tokens;
- dispositivos;
- atestacoes vinculadas ao usuario;
- formas de acesso;
- vinculos sociais do usuario;
- avatar do usuario;
- perfil pessoal no backend do produto especifico.

No backend do produto, dados pessoais do perfil podem ser apagados. Dados que
outros usuarios, dados compartilhados, financeiro, auditoria ou historico
operacional precisam manter devem continuar sem identificacao pessoal direta.

No servidor de identidade, os dados pessoais canonicos da pessoa nao entram
nesta limpeza. O que pode ser removido/desassociado nesse servidor sao somente
os elementos de acesso daquele usuario: e-mail/usuario usados como credencial,
formas de acesso, vinculos sociais, avatar e arquivos de avatar ligados ao
usuario de acesso.

## Glossario operacional

| Termo | Significado neste documento | Regra pratica |
| --- | --- | --- |
| `dryRun` | Simulacao obrigatoria da operacao. O servico resolve o alvo e lista exatamente o que apagaria, anonimizaria, preservaria, ignoraria ou bloquearia, mas nao altera nenhum dado. | Toda execucao real deve ser precedida por `dryRun=true` com resultado consistente. |
| Execucao real | Operacao com `dryRun=false`, depois que o resultado do `dryRun` foi revisado/aprovado conforme permissao aplicavel. | Pode alterar dados; deve gerar auditoria e validacao pos-condicao. |
| Alvo de acesso | Usuario de autenticacao/acesso, formas de acesso, sessoes, dispositivos, credenciais e vinculos sociais que permitem entrar no sistema. | Pode ser removido/desassociado quando pertence ao usuario/produto resolvido. |
| Alvo de produto | Perfil pessoal no backend do produto especifico informado na solicitacao. | Deve ser resolvido por `produto + usuarioPublicoProduto` ou identificador equivalente do produto. |
| Vínculo usuário/produto | Registro que liga o usuário de autenticação ao produto Eickrono, como `autenticacao.usuarios_clientes_ecossistema`. | Deve carregar o mesmo identificador público usado pelo produto alvo; se esse identificador estiver ausente ou divergente, a execução real deve bloquear. |
| Pessoa canonica | Registro central da pessoa no servidor de identidade. | Preservada por este servico; exclusao de pessoa e outro servico. |
| Compatibilidade historica | Estruturas antigas de pessoa/perfil ainda existentes durante a migracao `Long`/`UUID`, mas que nao fazem parte do legado social removido. | Podem bloquear a execucao se impedirem resolver o alvo com seguranca; nao aparecem como categoria funcional propria. |
| Pendencia de remocao de avatar | Registro imutavel com `bucket`, `storageKey`, origem, produto e dono resolvido antes de apagar/desassociar avatar. | Garante que a remocao fisica do arquivo continue possivel mesmo depois da limpeza logica. |

Categorias obrigatorias do `dryRun`:

| Categoria | Quando usar |
| --- | --- |
| `APAGAR` | Registro ou recurso pode ser removido fisicamente pelo escopo deste servico. |
| `ANONIMIZAR` | Registro deve permanecer por regra de negocio, auditoria, financeiro, seguranca ou dado compartilhado, mas sem identificacao pessoal direta. |
| `PRESERVAR` | Registro deve permanecer sem alteracao, normalmente por ser canonico, compartilhado ou obrigatorio. |
| `NAO_TOCAR` | Recurso encontrado mas fora do escopo, como configuracao global, client, provider social, migration ou service account. |
| `BLOQUEAR` | O servico nao tem dados suficientes ou encontrou risco de remocao indevida; execucao real nao deve prosseguir. |

## Cenarios de uso

Este documento especifica um unico servico. A regra central e sempre a mesma:
resolver o alvo a partir do produto e do usuario/perfil informado, remover o
acesso, limpar ou anonimizar o perfil do produto especifico e preservar o que
precisa ser preservado.

### Regra unica do servico

Finalidade:

- liberar e-mail, usuario e rede social para novo cadastro quando os dados que
  bloqueavam o cadastro forem removidos;
- limpar dados de autenticacao e produto relacionados ao usuario do produto
  informado;
- remover dados pessoais do perfil do produto;
- invalidar usuario de autenticacao, sessoes, formas de acesso e vinculos
  sociais ligados ao usuario de acesso;
- preservar configuracoes globais, provedores sociais, clients, service accounts
  e dados canonicos da pessoa no servidor de identidade;
- preservar registros financeiros, auditoria, seguranca e dados compartilhados
  quando a lei ou a regra de negocio exigir, usando anonimizacao/minimizacao.

Restricoes:

- nao deve apagar dados canonicos da pessoa no servidor de identidade;
- deve exigir permissao administrativa interna adequada ao tipo de solicitacao;
- deve gerar relatorio de `dryRun` e relatorio de execucao;
- deve registrar log detalhado com solicitante, motivo e dados mascarados quando
  o log nao precisar dos identificadores integrais;
- nao deve prometer reversao de dados fisicamente apagados;
- deve preservar somente o que a lei, auditoria, financeiro, seguranca ou dados
  compartilhados exigirem.
- deve exigir consistência entre o backend do produto e o vínculo de
  autenticação: o perfil do produto e `autenticacao.usuarios_clientes_ecossistema`
  precisam apontar para o mesmo usuário/produto por `sub`, produto e
  identificador público do produto.
- se o produto possuir `identificador_publico_sistema`, mas a autenticação
  estiver com `identificador_publico_cliente` vazio ou divergente, o serviço deve
  bloquear a execução real e reportar inconsistência. Não é permitido apagar só
  um lado para "destravar" o cadastro.

### Solicitacoes atendidas pelo servico

O mesmo servico atende tanto a reutilizacao controlada de cadastro quanto a
exclusao de conta de produto solicitada por usuario/suporte. Em todos os casos,
o objetivo e remover o acesso e os dados pessoais do produto especifico, sem
apagar a pessoa canonica e sem apagar registros que precisam ser preservados por
obrigacao legal, financeira, auditoria, seguranca ou dados compartilhados.

Regras adicionais:

- deve exigir permissao administrativa mais restritiva e motivo formal;
- deve preservar trilha de auditoria da solicitacao e da execucao;
- deve retornar bloqueios claros quando alguma informacao nao puder ser apagada
  por retencao obrigatoria;
- deve permitir novo cadastro com os mesmos identificadores quando a politica de
  retencao nao exigir bloqueio ou preservacao impeditiva.

## Endpoint alvo

Preferir `POST`, nao `DELETE`, porque a operacao precisa receber motivo,
`dryRun` e possiveis chaves alternativas.

Endpoint proposto:

```http
POST /api/interna/usuarios/exclusoes
```

Payload proposto:

```json
{
  "produto": "THIMISU",
  "usuarioPublicoProduto": "usuario_publico",
  "dryRun": true,
  "motivo": "Solicitacao de exclusao de cadastro/produto"
}
```

Payload com chaves alternativas para suporte/diagnostico:

```json
{
  "produto": "THIMISU",
  "usuarioPublicoProduto": "usuario_publico",
  "perfilProdutoId": null,
  "email": "usuario@exemplo.com",
  "sub": null,
  "provedoresSociais": ["GOOGLE", "APPLE"],
  "dryRun": true,
  "motivo": "Solicitacao de exclusao de cadastro/produto"
}
```

Regras:

- `produto` deve ser sempre informado;
- o caminho principal deve usar `produto + usuarioPublicoProduto`;
- `perfilProdutoId`, `email`, `sub` e `provedoresSociais` sao chaves
  alternativas para suporte, diagnostico ou casos em que o usuario publico nao
  esteja disponivel/confiavel;
- `produto` faz parte da chave logica do perfil do produto;
- `usuarioPublicoProduto` nao e global entre todos os produtos Eickrono: dois
  produtos podem permitir o mesmo identificador publico sem conflito;
- remover ou anonimizar um perfil de `THIMISU` nao pode remover nem alterar
  perfil de outro produto Eickrono;
- usuario de autenticacao e `usuarioPublicoProduto` sao campos diferentes, mesmo
  quando tiverem o mesmo texto;
- o servico deve resolver e-mail, sub, usuario de autenticacao, vinculos
  sociais, Keycloak e avatar a partir do alvo de produto identificado e das
  chaves auxiliares informadas;
- `dryRun=true` deve ser o primeiro passo operacional;
- `dryRun=false` deve informar o `correlacaoId` retornado pelo `dryRun=true`;
- antes de qualquer efeito destrutivo, a execucao deve carregar o `dryRun`
  registrado, confirmar que ele esta `PLANEJADA`, recalcular o plano atual e
  bloquear a operacao se alvo, acoes, preservacoes ou bloqueios divergirem;
- o servico deve retornar tudo que encontrou e tudo que pretende apagar,
  anonimizar, preservar ou ignorar.

Resposta proposta:

```json
{
  "correlacaoId": "uuid",
  "dryRun": true,
  "alvosResolvidos": {
    "acesso": {
      "email": "usuario@exemplo.com",
      "usuarioAutenticacao": "usuario_autenticacao",
      "subs": ["sub-real"]
    },
    "produto": {
      "produto": "THIMISU",
      "usuarioPublicoProduto": "usuario_publico",
      "perfilProdutoId": "uuid-perfil-produto"
    }
  },
  "acoes": [
    {
      "sistema": "KEYCLOAK",
      "tipo": "APAGAR",
      "recurso": "usuario realm eickrono",
      "quantidade": 1
    },
    {
      "sistema": "EICKRONO_THIMISU_BACKEND",
      "tipo": "ANONIMIZAR",
      "recurso": "historico pessoal preservado por regra de auditoria",
      "quantidade": 2
    },
    {
      "sistema": "EICKRONO_IDENTIDADE_SERVIDOR",
      "tipo": "APAGAR",
      "recurso": "formas de acesso sociais do alvo no modelo atual",
      "quantidade": 1
    }
  ],
  "preservados": [
    {
      "sistema": "EICKRONO_THIMISU_BACKEND",
      "tipo": "PRESERVAR",
      "recurso": "perfis de outros produtos",
      "quantidade": 0
    },
    {
      "sistema": "KEYCLOAK",
      "tipo": "NAO_TOCAR",
      "recurso": "clients, service accounts e identity providers",
      "quantidade": 6
    }
  ],
  "bloqueios": []
}
```

Regra de exposicao de dados na resposta:

- a resposta administrativa do endpoint deve retornar identificadores reais
  necessarios para conferencia da operacao;
- mascaramento nao deve ser aplicado ao contrato principal de resposta, porque o
  operador precisa conferir exatamente qual alvo sera apagado, anonimizado,
  preservado ou bloqueado;
- mascaramento deve ser aplicado em logs de baixa exposicao, mensagens de UI ou
  relatorios compartilhaveis que nao precisem dos identificadores integrais;
- auditoria tecnica pode guardar identificadores reais, hashes ou snapshots
  minimos conforme a politica de seguranca e retencao;
- depois da exclusao, qualquer trilha que precise continuar existindo deve ser
  suficiente para auditoria, mas nao deve depender de consultar dados que foram
  fisicamente apagados.

Resposta de execucao real:

Request minimo de execucao real:

```json
{
  "produto": "THIMISU",
  "usuarioPublicoProduto": "usuario_publico",
  "perfilProdutoId": null,
  "dryRun": false,
  "motivo": "Solicitacao de exclusao de cadastro/produto",
  "correlacaoId": "uuid-retornado-pelo-dry-run"
}
```

Regras especificas:

- `correlacaoId` e obrigatorio para `dryRun=false`;
- o `correlacaoId` deve apontar para um `dryRun=true` previamente registrado
  com status `PLANEJADA`;
- se o plano recalculado divergir do plano aprovado, a execucao deve falhar e
  o operador deve executar um novo `dryRun`;
- a execucao usa a mesma correlacao aprovada, mudando a operacao auditada de
  `PLANEJADA` para `EM_EXECUCAO`, depois `CONCLUIDA` ou `FALHOU`;
- um `dryRun` com bloqueios nao pode ser executado.

```json
{
  "correlacaoId": "uuid",
  "dryRun": false,
  "alvosResolvidos": {
    "acesso": {
      "email": "usuario@exemplo.com",
      "usuarioAutenticacao": "usuario_autenticacao",
      "subs": ["sub-real"]
    },
    "produto": {
      "produto": "THIMISU",
      "usuarioPublicoProduto": "usuario_publico",
      "perfilProdutoId": "uuid-perfil-produto"
    }
  },
  "acoesExecutadas": [
    {
      "sistema": "KEYCLOAK",
      "tipo": "APAGAR",
      "recurso": "usuario realm eickrono",
      "quantidade": 1,
      "resultado": "CONCLUIDO"
    }
  ],
  "acoesPendentes": [
    {
      "sistema": "STORAGE_AVATAR",
      "tipo": "APAGAR",
      "recurso": "objeto S3 de avatar controlado pela Eickrono",
      "quantidade": 1,
      "resultado": "PENDENTE_REMOCAO_FISICA"
    }
  ],
  "posCondicoes": [
    {
      "codigo": "EMAIL_LIBERADO",
      "resultado": "CONCLUIDO"
    },
    {
      "codigo": "USUARIO_PRODUTO_LIBERADO",
      "resultado": "CONCLUIDO"
    },
    {
      "codigo": "VINCULO_SOCIAL_LIBERADO",
      "resultado": "CONCLUIDO"
    }
  ],
  "bloqueios": []
}
```

Codigos padrao de bloqueio/erro:

| Codigo | Quando retornar | Efeito |
| --- | --- | --- |
| `ALVO_NAO_RESOLVIDO` | Nenhum perfil/acesso foi encontrado para as chaves informadas. | Bloqueia execucao real. |
| `CONFLITO_IDENTIFICADORES` | `produto`, `usuarioPublicoProduto`, `email`, `sub` ou rede social apontam para alvos diferentes. | Bloqueia execucao real. |
| `VINCULO_PRODUTO_INCONSISTENTE` | O produto encontrou o perfil, mas `autenticacao.usuarios_clientes_ecossistema` nao possui o mesmo identificador publico do produto, ou possui identificador vazio/divergente. | Bloqueia execucao real; exige correcao do cadastro/provisionamento ou rotina controlada de reconciliacao antes da exclusao. |
| `USUARIO_CENTRAL_COMPARTILHADO` | O plano tentaria apagar usuario central, formas de acesso ou Keycloak enquanto o usuario de autenticacao ainda possui vinculo ativo com outro produto. | Bloqueia somente a acao central indevida; a exclusao do perfil do produto alvo pode continuar preservando o usuario central. |
| `HISTORICO_PRODUTO_DEPENDENTE_DO_ALVO` | O backend do produto ainda possui historico ligado por FK ao perfil/pessoa alvo. | Bloqueia execucao destrutiva ate anonimizar/minimizar historico ou ajustar schema para preservar auditoria sem PII. |
| `RECURSO_PROTEGIDO` | A resolucao encontrou admin master, service account, client, provider global, secret, migration ou catalogo. | Bloqueia se o plano tentaria tocar nesse recurso. |
| `PESSOA_CANONICA_FORA_ESCOPO` | A acao exigiria apagar dados canonicos da pessoa no servidor de identidade. | Bloqueia e orienta usar servico futuro especifico. |
| `DADO_COMPARTILHADO_PRESERVADO` | Dado do produto nao pode ser apagado por regra financeira, auditoria, seguranca ou compartilhamento. | Nao bloqueia se puder anonimizar/preservar corretamente. |
| `STORAGE_KEY_AUSENTE` | Avatar controlado pela Eickrono nao tem `storageKey` suficiente para remocao fisica segura. | Bloqueia apagar o arquivo fisico; permite desassociacao apenas se a regra de negocio aceitar. |
| `STORAGE_PREFIXO_INVALIDO` | `storageKey` fora dos prefixos permitidos para avatar. | Bloqueia remocao fisica. |
| `POS_CONDICAO_FALHOU` | Apos execucao, e-mail, usuario, rede social ou sessao ainda bloqueiam novo cadastro. | Retorna falha operacional e deve registrar ocorrencia de correcao. |

## Matriz de tratamento por sistema

## Achado operacional - inconsistência entre Thimisu e autenticação

Data de referência: `2026-06-02`.

Durante a validação do cenário de QA `C01`, o serviço administrativo foi
executado somente em `dryRun=true`. Nenhum dado foi apagado porque o próprio
serviço retornou bloqueio.

Dados encontrados:

| Sistema | Tabela/campo | Valor |
| --- | --- | --- |
| Thimisu | `thimisu_stg.perfis_sistema.identificador_publico_sistema` | `cenario01` |
| Thimisu | `thimisu_stg.pessoas_produto_local.sub` | `4771695b-6bed-4f82-b58d-737b2d5fbf3e` |
| Autenticação | `autenticacao.usuarios.sub_remoto` | `4771695b-6bed-4f82-b58d-737b2d5fbf3e` |
| Autenticação | `autenticacao.usuarios_clientes_ecossistema.identificador_publico_cliente` | vazio |

Resultado dos `dryRun`:

| Entrada | Resultado |
| --- | --- |
| `produto=THIMISU`, `usuarioPublicoProduto=cenario01` | Thimisu resolve o perfil, mas autenticação resolve `usuarios=0` e `vinculos=0`; bloqueado. |
| `produto=THIMISU`, `perfilProdutoId=<id do perfil Thimisu>` | Thimisu resolve o perfil, mas autenticação resolve `usuarios=0` e `vinculos=0`; bloqueado. |
| `produto=THIMISU`, `perfilProdutoId=<id do vínculo de autenticação>` | Autenticação resolve `usuarios=1` e `vinculos=1`, mas Thimisu não encontra perfil com esse ID; bloqueado. |

Interpretação:

- o serviço de exclusão agiu corretamente ao bloquear a execução real;
- a inconsistência nasceu antes, no cadastro/provisionamento, porque o vínculo
  da autenticação com o produto foi gravado sem o identificador público do
  produto;
- não é seguro executar exclusão real enquanto o produto e a autenticação não
  conseguirem provar que apontam para o mesmo alvo;
- a correção funcional deve garantir que todo cadastro/provisionamento grave
  `autenticacao.usuarios_clientes_ecossistema.identificador_publico_cliente`
  com o mesmo valor do identificador público usado no backend do produto, ou
  criar uma rotina controlada de reconciliação antes da exclusão.

Teste obrigatório derivado:

- criar cadastro real pelo app;
- validar no banco que `thimisu_stg.perfis_sistema.identificador_publico_sistema`
  e `autenticacao.usuarios_clientes_ecossistema.identificador_publico_cliente`
  possuem o mesmo valor;
- executar `dryRun=true` e exigir `usuariosAutenticacaoIds` e
  `vinculosProdutoIds` não vazios;
- somente depois executar `dryRun=false`.

### Keycloak

| Dado | Acao | Observacao |
| --- | --- | --- |
| Usuario real do realm da aplicacao | Apagar | Deve localizar por e-mail, username e/ou sub. |
| Credenciais do usuario | Apagar | Removidas junto com o usuario. |
| Identidades federadas do usuario | Apagar | Google, Apple e outras vinculadas ao usuario. |
| Sessoes e tokens do usuario | Invalidar/apagar | Evita reutilizacao de sessao antiga. |
| Usuario `admin` do realm `master` | Nao tocar | Protecao obrigatoria. |
| Clients, service accounts e identity providers | Nao tocar | Configuracao global, nao dado do usuario. |

### eickrono-autenticacao-servidor

| Dado | Acao | Observacao |
| --- | --- | --- |
| Cadastro pendente/finalizado ligado ao alvo | Apagar | Necessario para permitir novo cadastro com os mesmos dados de acesso/produto. |
| Usuario de autenticacao/acesso | Apagar | Remove a conta de acesso, nao a pessoa canonica. |
| Formas de acesso do usuario | Apagar | Inclui senha/e-mail e social. |
| Vinculos sociais confirmados do usuario | Apagar | Libera provedor social para novo fluxo. |
| Sessoes, refresh tokens e tokens de dispositivo | Apagar/invalidar | Evita login por sessao antiga. |
| Dispositivos e atestacoes do usuario | Apagar | Dados tecnicos ligados ao usuario removido. |
| Recuperacoes de senha do usuario | Apagar | Fluxos pendentes deixam de valer. |
| Pendencias de integracao de produto do usuario | Apagar/cancelar | Nao deve reenfileirar provisionamento antigo. |
| Catalogos, configuracoes, provedores sociais globais | Nao tocar | Nao sao dados do usuario. |

#### Tabelas iniciais mapeadas

Base logica: banco do servidor de autenticacao.

| Tabela | Tratamento no servico por usuario/produto | Observacao |
| --- | --- | --- |
| `autenticacao.cadastros_conta` | Apagar registros do alvo | Libera e-mail/usuario em cadastros pendentes ou finalizados. |
| `autenticacao.cadastros_conta_avatares` | Apagar registros dos cadastros do alvo | Filhos de cadastro; apagar antes do cadastro. |
| `autenticacao.cadastros_conta_vinculos_sociais_confirmados` | Apagar registros dos cadastros do alvo | Libera tentativa social associada ao cadastro. |
| `autenticacao.codigos_validacao_cadastro` | Apagar codigos do cadastro alvo | Evita validacao de codigo antigo. |
| `autenticacao.usuarios` | Apagar usuario de autenticacao do alvo | Nao e pessoa canonica. |
| `autenticacao.usuarios_formas_acesso` | Apagar formas de acesso do usuario alvo | Senha, e-mail, Google, Apple etc. |
| `autenticacao.usuarios_clientes_ecossistema` | Apagar vinculo do usuario com o produto alvo | Libera somente a chave do produto alvo. `usuarioPublicoProduto` pode repetir em outro produto e nao deve ser tratado como identificador global. |
| `autenticacao.recuperacoes_senha` | Apagar fluxos do usuario alvo | Nao deve permitir redefinicao de senha para usuario removido. |
| `autenticacao.pendencias_integracao_produto` | Cancelar/apagar pendencias do alvo | Evita provisionamento atrasado recriar perfil do produto. |
| `autenticacao.parametros_scheduler_integracao_produto` | Nao tocar | Configuracao global do scheduler. |
| `autenticacao.controles_integracao_produto` | Nao tocar | Controle/configuracao de execucao, nao dado do usuario. |

### eickrono-identidade-servidor

| Dado | Acao | Observacao |
| --- | --- | --- |
| Pessoa canonica | Preservar | Este servico nao apaga pessoa. |
| Dados pessoais canonicos da pessoa | Preservar | Nome civil, documentos, nascimento, genero, telefone canonico e outros dados pessoais da pessoa nao sao removidos por este servico. |
| E-mail/usuario usados como credencial ou forma de acesso | Apagar/desassociar | Remove identificadores de acesso do usuario, sem apagar o dado pessoal canonico da pessoa quando ele existir em tabela canonica. |
| Formas de acesso do usuario | Apagar | Inclui e-mail/senha e social, sem apagar a pessoa. |
| Vinculos sociais do usuario | Apagar | Remove associacao social definitiva do usuario de acesso. |
| Avatar do usuario | Apagar/desassociar | Remove opcoes de avatar ligadas ao usuario de acesso. |
| Arquivos de avatar enviados pelo usuario | Apagar no storage quando aplicavel | Apenas objetos vinculados ao usuario alvo. |
| Vínculos organizacionais historicos obrigatorios | Preservar/anonimizar conforme regra | Nao deve quebrar dados de terceiros. |

#### Tabelas iniciais mapeadas

Base logica: banco do servidor de identidade.

| Tabela | Tratamento no servico por usuario/produto | Observacao |
| --- | --- | --- |
| `identidade.pessoas` | Preservar | Este servico nao apaga pessoa canonica. |
| `identidade.contatos_email` | Preservar contato canonico; desassociar apenas se for modelado como forma de acesso | E-mail canonico da pessoa nao deve ser removido por exclusao de conta de produto. |
| `identidade.contatos_telefone` | Preservar | Telefone canonico e dado pessoal da pessoa. |
| `identidade.avatar_usuario` | Apagar/desassociar somente avatares do usuario de acesso/produto alvo | Se avatar for canonico da pessoa, precisa regra fina por origem e produto. |
| `identidade.avatar_origens` | Nao tocar | Catalogo/configuracao de origem/provedor. |
| Estruturas historicas de identidade | Consultar somente se ainda existir compatibilidade estrutural documentada | Nao fazem parte do modelo alvo e nao devem influenciar a regra funcional nova. |
| `vinculos_organizacionais` / historicos equivalentes | Preservar ou anonimizar conforme regra juridica | Pode afetar terceiros/organizacoes. |

#### Estado atual do modelo apos remocao do legado social

O servico de exclusao deve operar pelo modelo atual. O legado de vinculo social
em tabela propria e o contexto social pendente em servidor ja foram removidos do
runtime, com migrations de remocao para bases que ainda possuam essas estruturas.
Portanto, o servico novo nao deve consultar, listar, limpar, recriar ou tratar
essas estruturas como fonte de compatibilidade.

Essa regra evita reintroduzir o comportamento antigo: a rede social confirmada
no cadastro deve estar no payload/lista de vinculos confirmados e, depois da
confirmacao, no modelo atual de formas de acesso.

Modelo atual para acesso/cadastro/avatar:

| Area | Tabelas consideradas modelo atual |
| --- | --- |
| Catalogo | `catalogo.clientes_ecossistema`, `catalogo.sistemas_origem` |
| Usuario/acesso | `autenticacao.usuarios`, `autenticacao.usuarios_formas_acesso`, `autenticacao.usuarios_clientes_ecossistema` |
| Cadastro em andamento/finalizado | `autenticacao.cadastros_conta`, `autenticacao.codigos_validacao_cadastro`, `autenticacao.cadastros_conta_vinculos_sociais_confirmados`, `autenticacao.cadastros_conta_avatares` |
| Avatar | `identidade.avatar_origens`, `identidade.avatar_usuario` |
| Pessoa canonica relacionada | `identidade.pessoas`, `identidade.contatos_email`, `identidade.contatos_telefone` |

Legado social ja removido do desenho novo:

| Estrutura | Status esperado | Regra |
| --- | --- | --- |
| `autenticacao.contextos_sociais_pendentes` | Removida por migration nova nos servidores. | Nao deve ser recriada, consultada, exposta em contrato nem usada em teste novo. |
| `vinculos_sociais` | Removida do runtime; existe migration de drop para ambientes que ainda tenham a tabela. | Nao deve ser recriada, consultada, exposta em contrato nem usada em teste novo. |
| Campo antigo de contexto social no contrato publico | Campo de contrato removido do fluxo novo. | Nao deve voltar ao payload do app, autenticacao ou identidade. |
| Payload antigo de vinculo social no cadastro | Payload substituido por vinculos sociais confirmados em lista. | Nao deve ser aceito em cadastro novo. |

Compatibilidades estruturais ainda existentes:

| Estrutura real | Dependencia atual conhecida | Consequencia |
| --- | --- | --- |
| `cadastros_conta` sem schema | As entidades JPA `CadastroConta` ainda usam `@Table(name = "cadastros_conta")`. | Novas consultas do servico de exclusao devem usar schema explicito para nao depender de `search_path`. |
| `pessoas_identidade`, `perfis_identidade`, `pessoas_formas_acesso` | Ainda existem em partes do modelo de pessoa/perfil e na transicao `Long`/`UUID`. | Nao sao a fonte do novo vinculo social, mas tambem nao devem ser dropadas dentro do pacote de exclusao de cadastro/produto. |

Regra atual:

1. O servico novo deve usar `autenticacao.usuarios`,
   `autenticacao.usuarios_formas_acesso`, `autenticacao.cadastros_conta`,
   `autenticacao.cadastros_conta_vinculos_sociais_confirmados`,
   `identidade.avatar_usuario` e as tabelas de produto.
2. `vinculos_sociais` e `contextos_sociais_pendentes` nao entram mais no
   `dryRun` nem na execucao.
3. Migrations historicas que citam essas tabelas permanecem apenas como
   historico de evolucao de schema.

Bloqueio tecnico atual para remocao completa:

| Contrato/classe | Problema | Acao antes de remover legado |
| --- | --- | --- |
| `ContextoPessoaPerfilSistema.pessoaId` | O contrato ainda usa `Long`, herdado de `pessoas_identidade.id`. O modelo novo usa `UUID` em `identidade.pessoas.id`. | Adicionar `pessoaCanonicaId UUID` em paralelo antes de trocar resolvedores de contexto para `identidade.pessoas`; nao substituir `pessoaId` diretamente na primeira etapa. |
| `ClienteContextoPessoaPerfilSistemaLocal` | Ainda resolve contexto por `CadastroContaRepositorio` e depende de dados legados completos. | Migrar para consultas no modelo atual somente depois de resolver o tipo de identificador da pessoa no contrato. |
| `ResolvedorContextoFluxoPublico` | Runtime migrado para `autenticacao.cadastros_conta`, `autenticacao.recuperacoes_senha` e `identidade.contatos_email`. Permanece construtor de compatibilidade para testes/construtores legados. | Remover o construtor compatível quando os construtores antigos de cadastro/recuperacao forem eliminados. |
| `ProvisionamentoIdentidadeService` | Ainda sincroniza `Pessoa`, `FormaAcesso` e `PerfilIdentidade` legados. | Migrar writes para `identidade.pessoas`, `identidade.contatos_email`, `autenticacao.usuarios` e `autenticacao.usuarios_formas_acesso`. |
| `VinculoSocialService` | Migrado para nao usar `vinculos_sociais`. | Manter testes garantindo que a fonte de vinculo social continue sendo o modelo atual de formas de acesso/avatar. |

Risco especifico de `cadastros_conta` sem schema:

As entidades JPA `CadastroConta` dos servidores de autenticacao e identidade
ainda declaram `@Table(name = "cadastros_conta")` sem schema. Portanto, ao
olhar apenas o codigo Java nao fica claro se a consulta vai atingir a tabela
legada no schema default ou a tabela canonica `autenticacao.cadastros_conta`.
O servico de exclusao e qualquer nova consulta devem preferir SQL com schema
explicito quando a intencao for o modelo atual:

- `autenticacao.cadastros_conta`;
- `autenticacao.codigos_validacao_cadastro`;
- `autenticacao.cadastros_conta_vinculos_sociais_confirmados`;
- `autenticacao.cadastros_conta_avatares`.

Enquanto existir entidade sem schema, a remocao fisica da tabela antiga
`cadastros_conta` sem schema deve ser tratada como migration separada, depois
de confirmar o `search_path` e todos os repositórios JPA afetados.

Detalhamento do bloqueio `Long`/`UUID`:

| Ponto de contrato/persistencia | Tipo atual | Origem atual | Motivo para nao migrar parcialmente |
| --- | --- | --- | --- |
| `ContextoPessoaPerfilSistema.pessoaId` nos servidores de autenticacao e identidade | `Long` | `pessoas_identidade.id` | Login, dispositivo, convite, vinculo organizacional e provisionamento consomem este identificador como numero. |
| `PessoaCanonicaConfirmada.pessoaId` no `eickrono-autenticacao-servidor` | `Long` | Resposta interna de confirmacao de pessoa no `eickrono-identidade-servidor` | Cadastro confirmado grava este valor em `cadastros_conta.pessoa_id_perfil` e usa o mesmo valor para provisionar o produto. |
| `ConfirmacaoPessoaCadastroInternoApiResposta.pessoaId` no `eickrono-identidade-servidor` | `Long` | Entidade legada `Pessoa` | O contrato HTTP interno ainda devolve ID numerico; trocar para `UUID` quebra o cliente se nao houver migracao coordenada. |
| `ProvisionamentoPerfilSistemaProdutoRequestPayload.pessoaIdCentral` | `Long` | Valor recebido da identidade durante cadastro | Backends de produto esperam o ID central numerico; migrar apenas identidade/autenticacao quebraria provisionamento. |
| `ClienteContextoPessoaPerfilSistemaHttp` | `Long pessoaIdCentral` | Endpoint interno de contexto de pessoa/perfil | O cliente consulta por `pessoaIdCentral` numerico e converte a resposta para `ContextoPessoaPerfilSistema`. |
| `cadastros_conta.pessoa_id_perfil`, `registro_dispositivo.pessoa_id_perfil`, `dispositivos_identidade.pessoa_id_perfil`, `desafios_atestacao_app.pessoa_id_perfil` | `Long` | Modelo legado de pessoa/perfil | Esses registros fazem parte de restauracao de sessao, dispositivo e atestacao. Trocar uma tabela isolada cria divergencia entre sessao, dispositivo e cadastro. |

Regra de migracao desse bloqueio:

1. Definir contrato alvo antes do codigo: adicionar um novo campo
   `pessoaCanonicaId` `UUID`, mantendo `pessoaIdCentral` numerico apenas como
   compatibilidade temporaria.
2. Atualizar os DTOs internos dos dois servidores em conjunto.
3. Atualizar as tabelas que hoje guardam `pessoa_id_perfil` para apontarem para
   `identidade.pessoas.id` ou para uma coluna nova canonica.
4. Atualizar provisionamento de produto para receber o identificador canonico.
5. Somente depois remover consultas e entidades de `pessoas_identidade`,
   `perfis_identidade` e `pessoas_formas_acesso`.

Estado atual do corte seguro ja feito:

| Ponto | Estado atual | Observacao |
| --- | --- | --- |
| Disponibilidade de usuario no `eickrono-autenticacao-servidor` | Ja consulta `autenticacao.usuarios_clientes_ecossistema` e `autenticacao.cadastros_conta` com schema explicito. | Nao depende mais de `identidade.cadastros_conta.usuario` para decidir disponibilidade no fluxo novo. |
| Resolucao de contexto publico no runtime Spring do `eickrono-identidade-servidor` | Ja consulta `autenticacao.cadastros_conta`, `autenticacao.recuperacoes_senha` e `identidade.contatos_email` com schema explicito. | Ainda existe construtor de compatibilidade para testes/construtores antigos. |
| `CadastroContaInternaServico` no `eickrono-identidade-servidor` | Ainda possui construtores antigos que instanciam `ResolvedorContextoFluxoPublico(cadastroContaRepositorio, recuperacaoSenhaRepositorio)`. | Esses construtores devem ser removidos apenas quando os caminhos antigos de teste/compatibilidade forem eliminados. |
| `RegistroDispositivoService` e `CadastroContaInternaServico` no `eickrono-identidade-servidor` | Ainda possuem `ClienteContextoPessoaPerfilSistemaLegado`. | Esse fallback ainda usa modelo legado de pessoa/perfil e nao deve ser apagado antes da migracao `Long`/`UUID`. |
| `CadastroConta` JPA no `eickrono-identidade-servidor` | Ainda usa `@Table(name = "cadastros_conta")` sem schema. | Qualquer limpeza fisica da tabela legada deve aguardar confirmacao de `search_path` e remocao dos repositorios JPA afetados. |

Proximo corte seguro recomendado:

1. Manter as migrations de drop da tabela de pendencia social em servidor,
   porque o contrato novo usa apenas dados sociais temporarios no app e listas
   confirmadas no cadastro.
2. Nao dropar `pessoas_identidade`, `perfis_identidade`,
   `pessoas_formas_acesso` nem `cadastros_conta` sem schema nesta etapa.
3. Para o contrato de pessoa canonica, usar `pessoaCanonicaId UUID` em paralelo
   com `pessoaIdCentral Long`.
4. Nao trocar `pessoaId` diretamente para `UUID` na primeira entrega. Migrar
   consumidores por etapas e remover o `Long` somente no fim reduz risco de
   quebrar login, dispositivo, convite e provisionamento de produto.

Roteiro detalhado para migrar `pessoaIdCentral Long` para pessoa canonica:

Objetivo:

usar `identidade.pessoas.id` como identificador canonico da pessoa, sem quebrar
os fluxos que ainda dependem do ID numerico legado.

Decisao recomendada:

adicionar `pessoaCanonicaId UUID` em paralelo aos campos atuais. Nao trocar
`pessoaId` diretamente para `UUID` na primeira etapa, porque isso muda varios
contratos ao mesmo tempo.

Etapa 1 - contrato interno em paralelo:

| Projeto | Alteracao | Testes obrigatorios |
| --- | --- | --- |
| `eickrono-identidade-servidor` | Adicionar `pessoaCanonicaId` em `ContextoPessoaPerfilSistema` e `ConfirmacaoPessoaCadastroInternoApiResposta`, mantendo `pessoaId`/`pessoaIdCentral` numerico. | Testes de controller interno, contexto por e-mail/sub/usuario e confirmacao de cadastro. |
| `eickrono-autenticacao-servidor` | Ler `pessoaCanonicaId` das respostas internas, mantendo fallback para `pessoaIdCentral`. | Testes de cadastro, login, registro de dispositivo e provisionamento. |
| Backends de produto | Aceitar `pessoaCanonicaId` no payload de provisionamento, mantendo `pessoaIdCentral` durante transicao. | Testes de provisionamento e idempotencia. |

Etapa 2 - persistencia em paralelo:

| Tabela/area | Alteracao | Regra |
| --- | --- | --- |
| `cadastros_conta` / `autenticacao.cadastros_conta` | Criar coluna `pessoa_canonica_id UUID` em paralelo a `pessoa_id_perfil BIGINT`. | Novos cadastros gravam as duas quando possivel. |
| `registro_dispositivo` | Criar coluna `pessoa_canonica_id UUID`. | Registro novo de dispositivo deve preferir UUID; `BIGINT` fica para compatibilidade. |
| `dispositivos_identidade` | Criar coluna `pessoa_canonica_id UUID`. | Restauracao de sessao deve validar UUID quando existir. |
| `atestacoes_app_desafios` | Criar coluna `pessoa_canonica_id UUID`. | App Attest deve manter correlacao com pessoa canonica. |
| `vinculos_organizacionais` | Criar coluna `pessoa_canonica_id UUID`. | Convites e vinculos nao devem depender apenas do ID numerico. |
| Pendencias de integracao de produto | Criar campo `pessoa_canonica_id UUID` no payload e/ou tabela. | Reprocessamento deve conseguir provisionar sem consultar legado. |

Etapa 3 - leitura preferencial pelo modelo canonico:

| Leitura | Nova regra |
| --- | --- |
| Contexto por e-mail | Consultar `identidade.contatos_email` + `identidade.pessoas` + vinculo de cliente/produto. |
| Contexto por sub | Consultar forma de acesso canonica do usuario/pessoa, nao `pessoas_identidade`. |
| Contexto por usuario publico do produto | Consultar vinculo do usuario com `catalogo.clientes_ecossistema`. |
| Login social | Resolver forma social em `autenticacao.usuarios_formas_acesso` e avatar em `identidade.avatar_usuario`. |
| Registro de dispositivo | Ativar sessao/dispositivo usando `pessoaCanonicaId` quando presente. |

Etapa 4 - limpeza de compatibilidade:

Remover apenas depois de todos os testes de etapa 3 passarem:

- construtores que instanciam `ResolvedorContextoFluxoPublico` com
  `CadastroContaRepositorio` e `RecuperacaoSenhaRepositorio`;
- `ClienteContextoPessoaPerfilSistemaLegado`;
- leituras de `pessoas_identidade`, `perfis_identidade`,
  `pessoas_formas_acesso`;
- dependencias funcionais de `pessoa_id_perfil`.

Etapa 5 - migrations de remocao:

Somente apos etapa 4:

1. criar migration para garantir que nenhum registro ativo dependa apenas de
   `pessoa_id_perfil`;
2. criar relatorio de contagem por tabela;
3. dropar ou arquivar tabelas legadas;
4. remover colunas `pessoa_id_perfil` quando nao houver consumidores;
5. remover campos `pessoaIdCentral` dos contratos.

Testes de regressao obrigatorios da migracao:

| Cenario | Cobertura minima |
| --- | --- |
| Cadastro por senha | Cria pessoa canonica, usuario de autenticacao, perfil de produto e grava `pessoaCanonicaId`. |
| Confirmacao de e-mail | Retorna `pessoaCanonicaId` e ainda retorna `pessoaIdCentral` durante transicao. |
| Login por senha | Resolve contexto sem consultar `vinculos_sociais` nem `contextos_sociais_pendentes`. |
| Login social | Resolve forma social canonica, avatar preferido e perfil de produto. |
| Registro silencioso de dispositivo | Usa `pessoaCanonicaId` quando existir e nao quebra sessoes antigas. |
| Biometria | Valida sessao da conta correta usando identificador canonico. |
| Recuperacao de senha | Resolve contexto de exibicao pelo modelo multiapp. |
| Exclusao de cadastro/produto | `dryRun` lista o modelo atual; execucao libera e-mail, usuario e redes sociais do produto alvo sem consultar legado social removido. |
| Compatibilidade temporaria | Registro antigo com apenas `pessoaIdCentral` ainda funciona ate a remocao planejada. |

#### Estruturas historicas e compatibilidade de pessoa/perfil

Nao existe mais legado social ativo para o servico de exclusao. As tabelas
`vinculos_sociais` e `autenticacao.contextos_sociais_pendentes` nao devem
aparecer no `dryRun`, nao devem ser consultadas e nao devem receber limpeza por
alvo.

Ainda existem estruturas historicas de pessoa/perfil em partes do sistema. Elas
nao sao fonte do novo fluxo social e nao devem ser tratadas como autorizacao
para apagar dados pessoais canonicos. Quando o servico de exclusao precisar
lidar com elas, deve seguir estas regras:

- usar schema explicito nas consultas;
- nunca decidir vinculo social por `vinculos_sociais`, pois a tabela foi
  removida;
- nao apagar pessoa canonica do servidor de identidade neste servico;
- nao apagar `pessoas_identidade`, `perfis_identidade` ou
  `pessoas_formas_acesso` por migration dentro deste pacote;
- listar apenas impactos reais do modelo atual no `dryRun`;
- se alguma compatibilidade estrutural impedir a execucao, retornar bloqueio
  explicito em vez de reintroduzir uma etapa funcional de legado social.

| Estrutura | Papel atual | Regra para este servico |
| --- | --- | --- |
| `cadastros_conta` sem schema | Compatibilidade JPA/search path. | Novas consultas devem usar `autenticacao.cadastros_conta`; nao consultar tabela sem schema. |
| `pessoas_identidade` | Compatibilidade de contratos numericos `Long`. | Preservar; nao apagar como parte da exclusao de cadastro/produto. |
| `perfis_identidade` | Compatibilidade de perfil/produto em contratos antigos. | Preservar ate pacote especifico de migracao `pessoaCanonicaId UUID`. |
| `pessoas_formas_acesso` | Compatibilidade de formas antigas em pontos ainda dependentes de `Pessoa`/`PerfilIdentidade`. | Nao usar como fonte de vinculo social novo; qualquer bloqueio deve ser explicitado e tratado em pacote proprio. |

Invariante:

O resultado do servico deve ser determinado pelo modelo atual. Estruturas de
compatibilidade podem explicar bloqueios tecnicos, mas nao podem criar uma
segunda fonte de verdade nem reintroduzir limpeza de legado social.

### eickrono-thimisu-backend

| Dado | Acao | Observacao |
| --- | --- | --- |
| Perfil pessoal do produto especifico | Apagar ou anonimizar | Dados pessoais podem ser removidos no backend do produto alvo. |
| Nome, e-mail, telefone, avatar e preferencias pessoais do perfil | Apagar/anonimizar | Conforme politica de exclusao do produto. |
| Usuario publico do produto | Liberar | Necessario para novo cadastro quando a politica permitir reutilizacao do identificador. |
| Dados privados exclusivos do usuario | Apagar | Ex.: preferencias, cache, configuracoes pessoais. |
| Dados compartilhados com outros usuarios | Preservar/anonimizar | Nao remover conteudo necessario a terceiros. |
| Dados financeiros, auditoria, contratos e historico obrigatorio | Preservar com minimizacao | Substituir identificacao pessoal por marcador neutro quando aplicavel. |

#### Tabelas iniciais mapeadas

Base logica: banco do produto Thimisu.

| Tabela | Tratamento no servico por usuario/produto | Observacao |
| --- | --- | --- |
| `thimisu.perfis_sistema` | Apagar ou anonimizar perfil do alvo | Contem o perfil do usuario no Thimisu e libera `identificador_publico_sistema`. |
| `thimisu.pessoas_produto_local` | Apagar ou anonimizar dados pessoais locais do alvo | Dados pessoais locais do produto; nao e pessoa canonica do identity. |
| `thimisu.perfis_sistema_historico` | Preservar com minimizacao/anonimizacao, salvo quando regra aprovada permitir apagar | Politica do Thimisu permite preservar historico operacional sem identificacao direta. |
| `thimisu.pessoas_produto_local_historico` | Preservar com minimizacao/anonimizacao, salvo quando regra aprovada permitir apagar | Historico pode ser necessario para auditoria/suporte; remover PII direta. |
| `thimisu.documentos_historico` | Preservar ou anonimizar conforme tipo do documento | Precisa classificacao por dado financeiro/auditoria/terceiros antes de apagar. |
| `thimisu.flyway_schema_history` | Nao tocar | Versionamento de schema. |

### Storage de avatares

| Dado | Acao | Observacao |
| --- | --- | --- |
| Avatar enviado pelo usuario | Apagar | Ex.: origem `THIMISU`. |
| Cache/derivados do avatar enviado | Apagar | Se existir storage derivado. |
| URL de rede social | Desassociar | Nao apagar arquivo externo de Google/Apple. |
| Bucket/configuracao S3/CloudFront | Nao tocar | Infraestrutura global. |

### Logs e auditoria

| Dado | Acao | Observacao |
| --- | --- | --- |
| Logs operacionais | Preservar por retencao | Nao apagar diretamente em producao. |
| Logs de teste/manutencao | Limpar somente por runbook separado | Nao misturar com exclusao funcional. |
| Auditoria da exclusao | Criar | Deve registrar solicitante, motivo, tipo de solicitacao, alvo real quando necessario para rastreabilidade, representacao mascarada para visualizacoes de baixa exposicao e resultado. |

### Keycloak

Base logica: banco/realm Keycloak da aplicacao.

| Tabela/recurso | Tratamento no servico por usuario/produto | Observacao |
| --- | --- | --- |
| `public.user_entity` do realm da aplicacao | Apagar somente usuario real alvo | Nunca apagar realm `master`, admin, service accounts ou usuarios de configuracao. |
| `public.federated_identity` | Apagar vinculos do usuario alvo | Libera Google/Apple daquele usuario. |
| `public.credential` | Apagar credenciais do usuario alvo | Senha/credenciais locais do Keycloak. |
| `public.user_attribute`, `public.user_role_mapping`, `public.user_required_action`, `public.user_consent`, `public.user_group_membership` | Apagar filhos do usuario alvo | Filhos antes de `user_entity`. |
| `public.client` | Nao tocar | Configuracao OIDC. |
| `public.identity_provider` e `public.identity_provider_config` | Nao tocar | Configuracao Google/Apple/Facebook etc. |
| `public.realm` | Nao tocar | Nunca apagar `master` nem realm da aplicacao. |
| `public.jgroups_ping` | Nao tocar com Keycloak rodando | Nao e dado de usuario. |

## Ordem de execucao recomendada

1. Resolver o alvo principal por `produto + usuarioPublicoProduto` ou
   `produto + perfilProdutoId`. Usar `email`, `sub` e provedores sociais apenas
   como chaves alternativas de suporte/diagnostico.
2. Executar `dryRun` consultando Keycloak, autenticacao, identidade e backend do
   produto especifico.
3. Validar bloqueios:
   - alvo pertence ao realm correto;
   - alvo nao e admin, service account ou configuracao;
   - permissao e tipo de solicitacao sao permitidos;
   - registros financeiros/terceiros serao preservados.
4. Invalidar sessoes e tokens.
5. Remover usuario e identidades federadas do Keycloak.
6. Remover dados de autenticacao.
7. Remover/desassociar formas de acesso, vinculos sociais e avatar do usuario
   no servidor de identidade, preservando pessoa canonica e dados pessoais
   canonicos.
8. Apagar ou anonimizar perfil e dados pessoais no backend do produto
   especifico.
9. Apagar objetos de avatar controlados pela Eickrono quando aplicavel.
10. Registrar auditoria.
11. Executar validacao pos-condicao.

## Pos-condicoes obrigatorias

Depois de uma execucao que remova acesso e dados impeditivos de cadastro, deve
ser possivel:

- cadastrar novamente com o mesmo e-mail;
- cadastrar novamente com o mesmo usuario;
- autenticar novamente com a mesma rede social;
- concluir cadastro sem receber `email_indisponivel`,
  `usuario_indisponivel` ou erro de social ja vinculado por resíduo anterior,
  salvo quando houver politica explicita de retencao/bloqueio.

Depois de uma exclusao de conta de produto, deve ser verdadeiro:

- usuario nao consegue mais autenticar com credenciais antigas;
- perfil pessoal do produto nao exibe dados pessoais;
- dados financeiros/auditoria compartilhados continuam integros;
- pessoa canonica e dados pessoais canonicos permanecem no servidor de
  identidade.

## Conflitos e decisoes registradas

### 1. Runbook amplo de manutencao pode apagar pessoa canonica, este servico nao

Conflito:

- runbooks amplos de manutencao podem apagar `identidade.pessoas`,
  `identidade.contatos_email` e `identidade.contatos_telefone`;
- este servico preserva pessoa canonica e dados pessoais canonicos.

Decisao:

- manter os dois comportamentos separados;
- tratar runbook amplo como manutencao de banco, nao como regra funcional;
- o novo endpoint de usuario/produto nao deve usar script amplo de manutencao
  como implementacao direta.

### 2. E-mail aparece como credencial e como dado canonico

Conflito:

- para reutilizar cadastro, a forma de acesso por e-mail precisa ser apagada;
- o contato de e-mail canonico da pessoa no servidor de identidade deve ser
  preservado por este servico.

Decisao:

- confirmar no schema atual quais tabelas representam "forma de acesso" e quais
  representam "contato canonico" durante a montagem das queries do `dryRun`;
- o `dryRun` deve mostrar os dois papéis separadamente para evitar apagar contato
  canonico por engano;
- se contato canonico preservado bloquear novo cadastro, o erro deve ser
  corrigido na regra de disponibilidade, nao apagando a pessoa canonica.

### 3. Avatar pode ser canonico, social ou do produto

Conflito:

- avatar enviado pelo app Thimisu deve poder ser apagado;
- URL de avatar social externa nao deve apagar arquivo externo;
- avatar canonico da pessoa pode ser compartilhado por outros produtos.

Decisao:

- classificar `identidade.avatar_usuario` por origem/produto antes de apagar;
- apagar objetos controlados pela Eickrono apenas quando forem do usuario/produto
  alvo;
- desassociar URLs sociais sem tentar remover arquivo externo;
- materializar pendencia de remocao com `bucket + storageKey` antes de apagar ou
  desassociar o registro logico;
- remover/minimizar a pendencia depois do prazo configurado.

### 4. Historicos do Thimisu

Conflito:

- runbook amplo de manutencao pode apagar historicos de produto;
- exclusao funcional de conta do produto deve preservar financeiro, auditoria,
  contratos e dados de terceiros, com minimizacao/anonimizacao.

Decisao:

- historicos so podem ser apagados quando a matriz de preservacao permitir;
- quando houver obrigacao de preservacao, a execucao deve
  anonimizar/minimizar historicos.

### 5. Keycloak SQL direto vs Admin API

Conflito:

- runbook manual mostra SQL direto para limpar usuarios reais do realm
  `eickrono`;
- operacao funcional deve preferir Admin API para evitar inconsistencias/cache.

Decisao:

- endpoint novo deve usar Admin API/cliente interno quando possivel;
- SQL direto fica restrito a runbook manual de manutencao.

## Perguntas de decisao antes da implementacao

As perguntas abaixo registram decisoes fechadas e pendencias antes de codificar
o servico. Cada resposta altera contrato, testes e risco operacional.

Resumo das decisoes e pendencias registradas:

| Pergunta | Decisao |
| --- | --- |
| 1 | Orquestrador no `eickrono-autenticacao-servidor`. |
| 2 | Contrato unico; permissao, motivo, aprovacao e politica controlam o uso. |
| 3 | Preservar pessoa canonica neste servico; exclusao canonica deve ser outro servico/endpoint. |
| 4 | Separar e-mail credencial de e-mail canonico no `dryRun`. |
| 5 | Se identificadores continuarem bloqueados apos execucao, a limpeza falhou ou existe regra/tabela fora da matriz. |
| 6 | Avatar deve ser tratado por origem/produto; apagar fisicamente apenas objetos controlados pela Eickrono do produto alvo. |
| 7 | Historicos do produto devem ser apagados, anonimizados ou preservados conforme matriz de retencao. |
| 8 | Dados compartilhados/financeiros/auditoria devem ser preservados ou anonimizados, nunca apagados cegamente. |
| 9 | Usuario Keycloak deve ser removido via Admin API/cliente interno, nao por SQL funcional. |
| 10 | Execucao compensavel e idempotente por etapas. |
| 11 | `dryRun` obrigatorio e completo. |
| 12 | Endpoint interno com permissao administrativa, motivo e auditoria. |
| 13 | Produto obrigatorio; usuario publico de produto nao e identificador global. |
| 14 | Pos-condicoes automaticas obrigatorias; falha gera ocorrencia operacional para correcao. |
| 15 | Apagar/liberar vinculo social do usuario; preservar configuracao global do provedor social. |
| 16 | Nao consultar legado social removido; compatibilidades historicas viram bloqueio tecnico quando necessario. |
| 17 | Conflitos entre e-mail, usuario e sub devem bloquear a execucao. |
| 18 | Resposta administrativa pode trazer dados reais; logs comuns devem mascarar; auditoria restrita guarda o minimo necessario. |
| 19 | Sem versionamento por rota neste momento; versionar por documentacao/contrato e tag/release Git quando implementado. |

### 1. Qual sera o orquestrador do servico?

Contexto:

O reset/exclusao precisa coordenar Keycloak, autenticacao, identidade, produto
Thimisu, storage e auditoria. O orquestrador e o componente responsavel por
resolver o alvo, executar `dryRun`, chamar os servicos envolvidos e consolidar
o resultado.

Exemplo:

Um operador autorizado pede exclusao do cadastro/produto de
`usuario_publico` no produto `THIMISU`. O orquestrador precisa encontrar o
perfil do Thimisu, usuario de autenticacao, formas de acesso, cadastro, usuario
Keycloak, avatares e chaves auxiliares como e-mail/sub, sem apagar a pessoa
canonica.

Opcoes:

| Opcao | Descricao | Vantagens | Riscos |
| --- | --- | --- | --- |
| A | Orquestrador no `eickrono-autenticacao-servidor`. | Fica perto de cadastro, usuario de acesso, sessoes, Keycloak e formas de acesso. Menor esforco inicial. | Pode concentrar conhecimento de produto dentro da autenticacao se o contrato com produto nao for bem definido. |
| B | Orquestrador no `eickrono-identidade-servidor`. | Fica perto da pessoa canonica e avatar. | Risco de misturar exclusao de acesso/produto com identidade canonica, que queremos preservar. |
| C | Servico administrativo separado. | Arquitetura mais limpa para operacoes cross-system e auditoria. | Maior custo inicial, novo deploy, nova seguranca, novo contrato. |

Decisao fechada:

`A`.

Recomendacao tecnica:

`A`. Comecar no `eickrono-autenticacao-servidor`, com clientes internos claros
para identidade e produto. Isso resolve o problema atual com menor escopo e
mantem a identidade canonica protegida por contrato.

### 2. Como controlar autorizacao da execucao?

Contexto:

O servico e unico. A diferenca entre solicitacoes esta em permissao, motivo,
politica de retencao e regras de anonimizacao, nao em duas logicas
independentes nem em outro contrato.

Exemplo:

Um operador autorizado pode pedir a limpeza de `THIMISU + usuario_publico` para
reutilizar o cadastro controladamente. Um titular pode pedir exclusao da conta
no Thimisu. Nos dois casos, o servico resolve o mesmo tipo de alvo e aplica a
mesma matriz de apagar, anonimizar, preservar e nao tocar.

Opcoes:

| Opcao | Descricao | Vantagens | Riscos |
| --- | --- | --- | --- |
| A | Implementar contrato unico e controlar execucao por permissao, motivo, aprovacao e politica. | Permite usar o mesmo servico para teste controlado e solicitacao real sem duplicar regra. | Exige validacao cuidadosa das permissoes para nao liberar execucao indevida. |
| B | Criar endpoints separados por tipo de solicitacao. | Parece simples no curto prazo. | Duplica regra, aumenta risco de divergencia e contradiz o objetivo de servico unico. |
| C | Implementar apenas reutilizacao de cadastro em teste controlado e redesenhar exclusao real depois. | Reduz risco inicial. | Recria a confusao de que existem dois servicos e atrasa o caso funcional de exclusao de conta do produto. |

Decisao fechada:

`A`.

Recomendacao tecnica:

`A`. O servico deve nascer com contrato unico. O que muda por tipo de
solicitacao e a politica de permissao, aprovacao, auditoria e retencao. Qualquer
bloqueio temporario deve ser uma configuracao explicita, nao uma separacao de
logica nem uma limitacao conceitual do servico.

Decisao registrada:

este documento continua especificando um unico servico de exclusao de
cadastro/conta de produto. Uma exclusao ampla da pessoa canonica do ecossistema,
quando existir, deve ser outro servico/endpoint, com especificacao propria,
permissao mais restritiva e matriz de dados diferente.

### 3. Como tratar pessoa canonica no servidor de identidade?

Contexto:

O servidor de identidade possui a pessoa canonica. O novo servico nao deve
apagar pessoa nem dados pessoais canonicos. Mas para reutilizar cadastro,
precisa apagar formas de acesso e vinculos do usuario.

Exemplo:

`usuario@exemplo.com` pode existir como contato canonico da pessoa, mas tambem
como forma de acesso por e-mail/senha. O reset deve liberar login/cadastro,
mas nao apagar a pessoa canonica.

Opcoes:

| Opcao | Descricao | Vantagens | Riscos |
| --- | --- | --- | --- |
| A | Preservar sempre `identidade.pessoas`, `identidade.contatos_email` e `identidade.contatos_telefone`; apagar somente usuario/formas de acesso/vinculos/avatar do acesso. | Alinha com a regra definida e evita perda de dados canonicos. | Pode restar contato canonico com mesmo e-mail; o fluxo de disponibilidade precisa ignorar contatos canonicos quando decide credencial disponivel. |
| B | Apagar pessoa canonica durante limpeza operacional. | Facilita base totalmente limpa. | Mistura manutencao ampla de banco com servico por usuario; aumenta risco de repetir erro de apagar dado indevido. |
| C | Anonimizar pessoa canonica no mesmo servico. | Pode atender privacidade futuramente. | Contradiz o escopo atual; precisa outro servico raro e controle juridico. |

Decisao fechada:

`A` para este servico. A exclusao da pessoa canonica pode existir futuramente,
mas deve ser outro servico/endpoint, com especificacao propria.

Recomendacao tecnica:

`A`. Pessoa canonica preservada sempre neste servico. Se a disponibilidade de
e-mail/usuario continuar bloqueando por contato canonico, o erro esta na regra
de disponibilidade e deve ser corrigido ali, nao apagando pessoa.

Decisao registrada:

este servico preserva a pessoa canonica. A pessoa canonica pode ficar sem acesso,
sem produto e sem formas de login ativas depois da execucao. Se for necessario
apagar a pessoa canonica do ecossistema, isso deve ser implementado em servico
separado, raro e mais restrito.

### 4. Como diferenciar e-mail credencial de e-mail canonico?

Contexto:

O mesmo texto de e-mail pode aparecer em tabelas diferentes com papeis
diferentes. Um e-mail em forma de acesso impede login/cadastro; um e-mail em
contato canonico descreve a pessoa.

Exemplo:

Para o alvo `THIMISU + usuario_publico`, o e-mail associado aparece em
`autenticacao.usuarios_formas_acesso` e em `identidade.contatos_email`. O reset
deve remover a forma de acesso, mas preservar o contato canonico.

Opcoes:

| Opcao | Descricao | Vantagens | Riscos |
| --- | --- | --- | --- |
| A | O `dryRun` separa explicitamente `EMAIL_CREDENCIAL` de `EMAIL_CONTATO_CANONICO`; execucao remove apenas `EMAIL_CREDENCIAL`. | Muito claro e auditavel. | Exige classificador por tabela/campo. |
| B | Remover qualquer e-mail igual ao alvo em todos os schemas. | Simples. | Errado para este escopo; apaga dado canonico indevido. |
| C | Nao remover e-mail em nenhum lugar, apenas usuario Keycloak. | Conservador. | Nao libera cadastro e mantém erro de e-mail ja cadastrado. |

Decisao fechada:

`A`.

Recomendacao tecnica:

`A`. Classificacao explicita por papel no `dryRun`.

### 5. O que fazer se a pessoa canonica preservada ainda bloquear novo cadastro?

Contexto:

Depois do reset, pode acontecer de o app tentar cadastrar o mesmo e-mail e a
validacao consultar contato canonico, retornando `email_indisponivel`, mesmo
sem usuario de autenticacao ativo.

Exemplo:

Reset remove `autenticacao.usuarios` e Keycloak, mas
`identidade.contatos_email.email = usuario@exemplo.com` permanece. Se a regra de
disponibilidade olhar `contatos_email`, o cadastro continua bloqueado.

Opcoes:

| Opcao | Descricao | Vantagens | Riscos |
| --- | --- | --- | --- |
| A | Corrigir disponibilidade para bloquear somente credenciais/usuarios ativos, nao contato canonico preservado. | Mantem identidade correta e resolve cadastro. | Precisa revisar consultas de disponibilidade. |
| B | Apagar contato canonico no reset. | Libera cadastro rapidamente. | Viola escopo de preservar dados canonicos. |
| C | Criar regra especial de teste que ignora pessoa canonica. | Ajuda teste. | Pode mascarar erro funcional e divergir da regra real. |

Decisao fechada:

`A`. Se a exclusao/reset terminar e os identificadores continuarem bloqueados,
isso significa falha funcional ou tabela/regra fora da matriz; o problema deve
ser corrigido ate liberar o cadastro conforme a politica.

Recomendacao tecnica:

`A`. A limpeza nao deve compensar regra de disponibilidade errada. Depois da
execucao, se e-mail, usuario ou rede social do alvo continuarem bloqueando novo
cadastro quando deveriam estar liberados, a execucao deve ser considerada
incompleta. O servico deve registrar a inconsistencia e continuar exigindo
correcao ate a causa real ser eliminada.

### 6. Como tratar avatares?

Contexto:

Avatar pode vir de rede social, upload do Thimisu ou outro produto. A regra de
remocao fisica depende da origem e de quem controla o arquivo.

Exemplo:

Um usuario tem avatar Google como URL externa e avatar Thimisu enviado para S3.
Ao excluir/resetar o produto Thimisu, o arquivo S3 do Thimisu pode ser apagado,
mas a URL do Google deve apenas ser desassociada.

Opcoes:

| Opcao | Descricao | Vantagens | Riscos |
| --- | --- | --- | --- |
| A | Classificar por `origem`/produto: apagar fisicamente apenas objetos controlados pela Eickrono do produto alvo; desassociar URLs externas. | Correto e seguro. | Exige campos confiaveis de origem/produto/storage. |
| B | Apagar todos os registros de avatar da pessoa. | Simples. | Pode remover avatar compartilhado/canonico indevido. |
| C | Nunca apagar arquivo fisico, apenas desassociar. | Seguro contra delecao errada. | Deixa PII em storage sem uso. |

Decisao fechada:

`A`.

Recomendacao tecnica:

`A`. Se faltar metadado para classificar, o `dryRun` deve bloquear a delecao
fisica e reportar pendencia.

#### 6.1. Como remover fisicamente objetos de avatar no S3?

Contexto:

O servico de reset/exclusao pode identificar que um avatar controlado pela
Eickrono deve ser removido fisicamente do storage. Essa remocao nao deve apagar
URL externa de rede social e nao deve depender de acesso manual ao bucket.

Exemplo:

Usuario do `THIMISU` enviou `usuarios/123/avatar/thimisu_abc.jpg` para o bucket
de avatares do produto. A exclusao do produto `THIMISU` deve remover esse
objeto e possiveis derivados/cache, mas nao deve tocar em avatar Google/Apple
nem em objetos de outro produto.

Opcoes:

| Opcao | Descricao | Vantagens | Riscos |
| --- | --- | --- | --- |
| A | O proprio `eickrono-autenticacao-servidor` chama uma API interna do `eickrono-identidade-servidor`, e a identidade remove o objeto no S3. | Menor quantidade de componentes; identidade fica dona do storage de avatar. | Requisicao pode ficar mais lenta e precisa permissao S3 no servico. |
| B | O servico registra uma solicitacao de remocao e uma Lambda processa o delete no S3 de forma assincrona. | Melhor isolamento, retry e controle de permissao minima no S3. | Mais infraestrutura, fila/evento, observabilidade e reconciliacao. |
| C | O servico apenas desassocia o avatar e um job periodico remove objetos orfaos. | Simples para endpoint e reduz risco de falha em cascata. | PII pode permanecer no storage por uma janela maior. |

Decisao fechada:

`B`.

Recomendacao tecnica:

`B`, usando evento/fila e Lambda com permissao limitada aos prefixos de avatar.
Em qualquer opcao, a remocao fisica so pode ocorrer quando o objeto tiver
metadados confiaveis: bucket, `storage_key`, origem/produto e dono resolvido.

#### 6.2. O que a Lambda de remocao de avatar precisa garantir?

Contexto:

Se a decisao for usar Lambda, ela precisa ser segura para reprocessamento e nao
pode receber apenas uma URL publica, porque URL pode mudar ou apontar para
recurso externo.

Exemplo:

Evento recebido:

```json
{
  "correlacaoId": "uuid",
  "produto": "THIMISU",
  "avatarId": "uuid",
  "bucket": "eickrono-avatares",
  "storageKey": "usuarios/123/avatar/thimisu_abc.jpg",
  "motivo": "Solicitacao de exclusao de cadastro/produto"
}
```

Regras:

- a Lambda deve apagar por `bucket + storageKey`, nao por URL publica;
- a Lambda deve ser idempotente: objeto inexistente deve ser tratado como
  sucesso com aviso;
- a Lambda deve rejeitar `storageKey` fora dos prefixos permitidos;
- a Lambda deve registrar auditoria com `correlacaoId`, produto, avatarId, acao
  e resultado;
- a Lambda nao deve apagar objetos de Google, Apple ou outro provedor externo;
- falha de remocao fisica deve aparecer no resultado do servico como
  `PENDENTE_REMOCAO_STORAGE` ou equivalente.

#### 6.3. Retencao da pendencia de remocao de avatar

Contexto:

A pendencia/auditoria de remocao guarda `bucket`, `storageKey`, origem, produto
e dono resolvido para que a Lambda consiga remover o objeto mesmo depois que o
fluxo de exclusao deixar de depender do registro original de avatar. Esse dado
nao deve permanecer indefinidamente: depois de removido ou esgotado o prazo de
tratamento, ele deve ser eliminado ou minimizado.

Regra:

- a execucao deve materializar a pendencia de remocao antes de remover ou
  desassociar o registro logico de avatar;
- a pendencia deve possuir status, tentativas, data de criacao, data de ultima
  tentativa e data limite de retencao;
- depois de sucesso, a pendencia deve ficar disponivel apenas pelo prazo
  operacional configurado;
- depois do prazo, um job/Lambda deve apagar ou minimizar os dados tecnicos que
  permitiriam identificar o objeto original, preservando apenas auditoria
  agregada quando necessaria.

Configuracao:

O prazo de retencao deve ser configuravel no `eickrono-identidade-servidor`,
porque a pendencia tecnica de storage fica no dominio de avatar da identidade.
A prioridade de resolucao da configuracao e:

1. variavel de ambiente do servidor;
2. arquivo `application*.properties` do projeto;
3. valor default seguro definido no codigo somente se as duas fontes anteriores
   estiverem ausentes.

Nome sugerido:

```properties
identidade.avatar.remocao.pendencia-retencao-dias=30
```

Variavel de ambiente sugerida:

```text
IDENTIDADE_AVATAR_REMOCAO_PENDENCIA_RETENCAO_DIAS=30
```

Regra de precedencia:

Se `IDENTIDADE_AVATAR_REMOCAO_PENDENCIA_RETENCAO_DIAS` existir e for valida,
ela vence o valor do `application*.yml`. Se a variavel estiver ausente ou nula,
usa-se `identidade.avatar.remocao.pendencia-retencao-dias`. Se ambas estiverem
ausentes, o servico deve usar o default documentado e registrar log de
configuracao no bootstrap.

Valor inicial recomendado:

`30 dias`, ajustavel por configuracao.

### 7. Como tratar historicos do Thimisu?

Contexto:

Historicos podem conter dados pessoais, mas tambem podem ser necessarios para
auditoria, suporte, financeiro ou integridade de dados de terceiros.

Exemplo:

`thimisu.pessoas_produto_local_historico` guarda nome/e-mail antigo do perfil.
Quando a regra aprovada permitir remocao, pode ser apagado. Quando houver
obrigacao de preservacao, deve ser minimizado ou anonimizado.

Opcoes:

| Opcao | Descricao | Vantagens | Riscos |
| --- | --- | --- | --- |
| A | Apagar historicos do alvo somente quando a matriz de preservacao permitir; caso contrario, anonimizar/minimizar. | Equilibra reutilizacao de cadastro e obrigacoes de preservacao. | Exige regras por tipo de dado e politica de retencao. |
| B | Sempre apagar historicos. | Simples. | Pode quebrar auditoria/financeiro/terceiros. |
| C | Sempre preservar historicos sem anonimizar. | Evita perda operacional. | Pode manter PII alem do necessario. |

Decisao fechada:

`A`.

Recomendacao tecnica:

`A`.

### 8. Como tratar dados compartilhados, financeiros e auditoria?

Contexto:

Nem tudo que referencia um usuario pertence exclusivamente a ele. Alguns dados
podem impactar outros usuarios ou obrigacoes legais.

Exemplo:

Um pagamento, contrato, turma compartilhada ou historico de compra nao deve
sumir porque o perfil do usuario foi excluido. O identificador pessoal pode ser
substituido por `Perfil excluido` ou ID anonimo.

Opcoes:

| Opcao | Descricao | Vantagens | Riscos |
| --- | --- | --- | --- |
| A | Preservar registros obrigatorios/compartilhados e anonimizar identificadores pessoais. | Alinha com a politica do Thimisu. | Exige matriz especifica por tabela do produto. |
| B | Apagar tudo que referencia o usuario. | Simples. | Pode quebrar dados de terceiros e obrigacoes legais. |
| C | Preservar tudo sem anonimizar. | Baixo risco tecnico. | Alto risco de privacidade. |

Decisao fechada:

`A`.

Recomendacao tecnica:

`A`.

### 9. Como apagar usuario Keycloak?

Contexto:

Runbooks manuais podem usar SQL direto para limpar usuarios do realm `eickrono`,
mas uma
operacao funcional deve evitar inconsistencias e preservar cache/configuracoes.

Exemplo:

Ao excluir `THIMISU + usuario_publico`, o usuario Keycloak resolvido para esse
alvo e suas identidades federadas devem ser removidos. Clients, identity
providers, service accounts e admin master nao podem ser tocados.

Opcoes:

| Opcao | Descricao | Vantagens | Riscos |
| --- | --- | --- | --- |
| A | Usar Admin API/cliente interno para remover usuario real alvo e invalidar sessoes. | Caminho correto para Keycloak. | Precisa cliente/admin token confiavel. |
| B | Usar SQL direto no endpoint. | Rapido de implementar. | Risco de cache/inconsistencia e de apagar configuracao por erro. |
| C | Nao apagar Keycloak; apenas desabilitar usuario. | Menos destrutivo. | Nao libera rede social/e-mail corretamente para novo cadastro. |

Decisao fechada:

`A`.

Recomendacao tecnica:

`A`. SQL direto fica apenas para runbook manual.

### 10. O endpoint deve executar operacao atomica ou compensavel?

Contexto:

A operacao cruza bancos e sistemas diferentes. Nao existe transacao unica entre
Keycloak, autenticacao, identidade, produto e storage.

Exemplo:

O servico apaga usuario Keycloak, mas falha ao apagar perfil Thimisu. O resultado
fica parcial e precisa ser reexecutavel com seguranca.

Opcoes:

| Opcao | Descricao | Vantagens | Riscos |
| --- | --- | --- | --- |
| A | Operacao compensavel e idempotente por etapas, com status por acao e reexecucao segura. | Realista para sistemas distribuidos. | Mais codigo de auditoria/estado. |
| B | Tentar transacao distribuida. | Conceitualmente atomico. | Complexo e inadequado para Keycloak/storage. |
| C | Executar sequencial sem compensacao, retornando erro em falha. | Simples. | Deixa estado parcial dificil de diagnosticar. |

Decisao fechada:

`A`.

Recomendacao tecnica:

`A`.

### 11. Como sera o `dryRun`?

Contexto:

O `dryRun` precisa impedir repeticao de erros de limpeza: apagar configuracao
Keycloak, admin master, provider social, migrations ou pessoa canonica.

Exemplo:

Para `THIMISU + usuario_publico`, o `dryRun` deve listar: "1 perfil Thimisu
seria apagado/anonimizado", "1 usuario Keycloak seria apagado", "2 formas de
acesso seriam apagadas", "pessoa canonica preservada", "0 clients afetados".

Opcoes:

| Opcao | Descricao | Vantagens | Riscos |
| --- | --- | --- | --- |
| A | `dryRun` obrigatorio e completo, com categorias `APAGAR`, `ANONIMIZAR`, `PRESERVAR`, `NAO_TOCAR`, `BLOQUEAR`. | Auditavel e seguro. | Maior implementacao inicial. |
| B | `dryRun` simples apenas com contadores. | Mais rapido. | Pouca clareza para validar seguranca. |
| C | Sem `dryRun`; endpoint executa direto. | Menor codigo. | Inaceitavel para operacao destrutiva. |

Decisao fechada:

`A`.

Recomendacao tecnica:

`A`.

### 12. Quem pode chamar o endpoint?

Contexto:

Este endpoint e destrutivo. Precisa autenticacao forte, autorizacao interna e
auditoria.

Exemplo:

Operador autorizado pode executar a reutilizacao controlada de cadastro. Suporte
ou processo administrativo pode executar a exclusao de conta do produto quando a
politica aplicavel permitir, sempre com permissao restrita e motivo obrigatorio.

Opcoes:

| Opcao | Descricao | Vantagens | Riscos |
| --- | --- | --- | --- |
| A | Endpoint interno, mTLS/JWT de service account, role administrativa especifica, motivo obrigatorio e auditoria. | Seguro e alinhado ao risco. | Exige configuracao de seguranca. |
| B | Endpoint interno apenas por rede/VPC. | Simples. | Rede interna nao basta para operacao destrutiva. |
| C | Endpoint publico autenticado por usuario comum. | Facil de consumir. | Inadequado e perigoso. |

Decisao fechada:

`A`.

Recomendacao tecnica:

`A`.

### 13. Como lidar com multiplos produtos?

Contexto:

Uma mesma pessoa/usuario de autenticacao pode futuramente ter vinculos com mais
de um produto Eickrono. Alem disso, dois produtos podem aceitar o mesmo
`usuarioPublicoProduto` sem conflito, porque o identificador publico do produto
nao e necessariamente global no ecossistema. O servico nao pode apagar dados de
produto nao solicitado.

Exemplo:

Pessoa usa `THIMISU` e outro produto futuro. Em ambos, o perfil publico pode se
chamar `pedrosotc`. Pedido e reset/exclusao apenas do `THIMISU`; o outro produto
deve continuar intacto, mesmo que use o mesmo texto de usuario publico.

Opcoes:

| Opcao | Descricao | Vantagens | Riscos |
| --- | --- | --- | --- |
| A | `alvoProduto.produto` obrigatorio para a parte de produto; resolver/apagar perfil usando chave do produto, como `produto + usuarioPublicoProduto` ou `produto + perfilProdutoId`. | Seguro para multi-produto e evita tratar usuario publico como global. | Precisa modelagem correta de produto nas tabelas. |
| B | Apagar todos os produtos vinculados ao usuario. | Simples. | Viola escopo e pode remover dados indevidos. |
| C | Nao apagar nenhum produto; apenas acesso central. | Conservador. | Nao atende exclusao/reset do produto. |

Decisao fechada:

`A`.

Recomendacao tecnica:

`A`.

### 14. Como validar pos-condicoes automaticamente?

Contexto:

O erro recorrente foi achar que limpou, mas sobrou registro em outro
schema/banco. O servico precisa provar que liberou o cadastro.

Exemplo:

Depois de excluir `THIMISU + usuario_publico`, as consultas de disponibilidade
devem retornar e-mail e usuario livres para o produto alvo, e a rede social nao
deve estar vinculada ao usuario antigo.

Opcoes:

| Opcao | Descricao | Vantagens | Riscos |
| --- | --- | --- | --- |
| A | O endpoint executa validacoes pos-condicao e retorna falha se algo ainda bloquear novo cadastro. | Evita falso sucesso. | Exige consultas de validacao em todos os sistemas. |
| B | Validacao fica manual no runbook. | Menor codigo. | Repete o problema atual de limpeza incompleta. |
| C | Validar apenas contagens internas da autenticacao. | Parcial. | Pode ignorar Keycloak, identidade ou produto. |

Decisao fechada:

`A`. A validacao de pos-condicoes e obrigatoria em qualquer ambiente. Em testes,
ela tambem permite validar manualmente novo cadastro com os mesmos dados; em
operacao real, se algo continuar bloqueado, deve ser registrada ocorrencia
operacional para correcao.

Recomendacao tecnica:

`A`. A validacao automatica deve provar que os identificadores do alvo foram
liberados conforme a politica. Em ambientes de teste, essa validacao pode ser
confirmada manualmente por novo cadastro com os mesmos dados. Em ambiente de
operacao real, novo cadastro imediato nao e fluxo normal do usuario, mas a
liberacao tecnica precisa continuar correta; se nao estiver, o servico deve
registrar ocorrencia operacional para correcao.

### 15. Como separar vinculo social do usuario da configuracao global do provedor?

Contexto:

Rede social do usuario e configuracao global do provedor social nao sao a mesma
coisa. O servico deve liberar o vinculo social do usuario alvo, mas nunca deve
apagar configuracoes globais de Google, Apple, Keycloak client, secrets,
identity providers ou service accounts.

Exemplo:

O usuario `pedro` possui forma de acesso social `GOOGLE/sub-123`. Ao excluir a
conta/cadastro do produto, o vinculo `GOOGLE/sub-123` do usuario deve ser
apagado/desassociado para poder ser usado em outro cadastro valido. A
configuracao global "Entrar com Google" do sistema deve permanecer intacta.

Opcoes:

| Opcao | Descricao | Vantagens | Riscos |
| --- | --- | --- | --- |
| A | Apagar/desassociar formas de acesso e vinculos sociais do usuario alvo; preservar configuracao global do provedor. | Libera Google/Apple do usuario para novo cadastro sem quebrar login social do sistema. | Exige resolvedor preciso para nao confundir vinculo do usuario com configuracao global. |
| B | Preservar tambem o vinculo social do usuario. | Conservador. | Nao libera rede social para novo cadastro e mantem conflito funcional. |
| C | Apagar vinculo do usuario e configuracao global do provedor. | Remove tudo relacionado ao provedor. | Inaceitavel; quebra Google/Apple para todos os usuarios e repete risco de apagar configuracao. |

Decisao fechada:

`A`.

Recomendacao tecnica:

`A`. O vinculo social do usuario deve ser apagado/liberado. A configuracao
global do provedor social deve ser sempre `NAO_TOCAR`.

### 16. Como tratar estruturas historicas apos remover o legado social?

Contexto:

O legado social (`vinculos_sociais` e
`autenticacao.contextos_sociais_pendentes`) foi removido do runtime e possui
migrations de remocao. Ainda existem estruturas historicas de pessoa/perfil usadas por
contratos numericos e por compatibilidade `Long`/`UUID`, mas elas nao fazem
parte da regra funcional nova de vinculo social.

Exemplo:

Cadastro novo falha por e-mail existente; o `dryRun` deve explicar qual tabela
do modelo atual bloqueia o cadastro. Se o bloqueio vier de uma estrutura
historica de pessoa/perfil, o servico deve retornar bloqueio tecnico explicito
em vez de consultar `vinculos_sociais` ou recriar limpeza de legado social.

Opcoes:

| Opcao | Descricao | Vantagens | Riscos |
| --- | --- | --- | --- |
| A | Nao consultar legado social removido; validar somente modelo atual e retornar bloqueio tecnico se uma compatibilidade estrutural impedir execucao. | Mantem o servico limpo e evita reintroduzir o modelo antigo. | Exige mensagens de bloqueio bem claras. |
| B | Ignorar qualquer estrutura historica e validar apenas tabelas novas. | Codigo mais simples. | Pode esconder bloqueios reais durante a transicao `Long`/`UUID`. |
| C | Tratar estruturas historicas como fonte alternativa de limpeza. | Pode resolver residuos antigos. | Reintroduz a ambiguidade que acabou de ser removida. |

Decisao fechada:

`A`.

Recomendacao tecnica:

`A`. O servico nao deve voltar a listar `vinculos_sociais` ou
`contextos_sociais_pendentes`. Estruturas historicas restantes entram apenas
como bloqueio tecnico ou como parte de pacotes futuros de migracao.

### 17. Como tratar conflitos entre e-mail, usuario e sub?

Contexto:

As chaves auxiliares podem apontar para mais de um alvo se houver sujeira,
bug antigo ou dado parcialmente migrado. O servico nao deve escolher um alvo
por suposicao quando os identificadores discordarem.

Exemplo:

A solicitacao informa `THIMISU + usuario_publico`, mas o e-mail auxiliar aponta
para uma pessoa e o `sub` social aponta para outra. Executar delete nesse estado
poderia apagar o acesso errado.

Opcoes:

| Opcao | Descricao | Vantagens | Riscos |
| --- | --- | --- | --- |
| A | Bloquear a execucao e retornar conflito detalhado no `dryRun`. | Evita apagar dado errado e força correcao da causa. | Exige mensagem clara e ferramenta de diagnostico. |
| B | Priorizar sempre `produto + usuarioPublicoProduto` e ignorar conflito auxiliar. | Simples. | Pode esconder corrupcao de dados e deixar acesso social/e-mail preso em outro alvo. |
| C | Tentar consolidar automaticamente os alvos divergentes. | Pode resolver alguns casos. | Risco alto de juntar pessoas/acessos incorretos. |

Decisao fechada:

`A`.

Recomendacao tecnica:

`A`. Conflito entre identificadores deve retornar `BLOQUEAR`. Consolidacao de
dados divergentes e correcao de base devem ser rotina separada, nao efeito
colateral deste servico.

### 18. Como registrar logs e auditoria?

Contexto:

A resposta administrativa precisa ser util para operar e corrigir dados. Ao
mesmo tempo, logs comuns nao devem expor PII alem do necessario.

Exemplo:

O `dryRun` retorna e-mail real, usuario real e IDs reais para o operador
autorizado conferir a exclusao. Ja o log comum deve mascarar e-mail e usuario,
mantendo `correlacaoId`, acao, sistema e resultado.

Opcoes:

| Opcao | Descricao | Vantagens | Riscos |
| --- | --- | --- | --- |
| A | Resposta administrativa com dados reais; logs comuns mascarados; auditoria restrita com identificadores reais/hashes. | Operavel e mais seguro. | Exige separacao clara entre resposta, log comum e auditoria restrita. |
| B | Mascarar tudo, inclusive resposta administrativa. | Menos exposicao. | Dificulta validar se o alvo certo sera apagado. |
| C | Logar tudo em claro. | Facilita debug. | Exposicao desnecessaria de PII. |

Decisao fechada:

`A`.

Recomendacao tecnica:

`A`. O contrato administrativo pode retornar dados reais para quem tem
permissao. Logs comuns devem usar mascara; auditoria restrita deve reter o
minimo necessario para rastreabilidade.

### 19. Como rastrear a versao do contrato sem versionamento por rota?

Contexto:

Este servico e destrutivo e deve evoluir com cuidado. O projeto nao usa
versionamento por rota para cada endpoint, entao este contrato nao deve criar
uma regra especial de URL. Ainda assim, a versao do contrato precisa ser
rastreavel por documentacao, commit e release/tag.

Exemplo:

O endpoint e criado em um commit especifico e documentado como primeira versao
do contrato de exclusao de cadastro/produto. O release/deploy pode receber uma
tag Git, como `autenticacao-exclusao-cadastro-produto-v1`, para facilitar
auditoria e rollback. Se o contrato mudar de forma incompatível futuramente,
a decisao de versionar rota ou criar novo contrato deve ser reavaliada.

Opcoes:

| Opcao | Descricao | Vantagens | Riscos |
| --- | --- | --- | --- |
| A | Nao criar versionamento por rota agora; registrar a versao do contrato na documentacao/OpenAPI e usar commit/tag Git no release. | Respeita o padrao atual do sistema e mantem rastreabilidade. | Se houver mudanca incompatível no futuro, sera preciso nova decisao de versionamento. |
| B | Criar versionamento proprio so para este endpoint. | Isola evolucao deste contrato. | Inconsistente com o padrao atual do sistema e adiciona complexidade desnecessaria agora. |
| C | Nao registrar versao de contrato nem tag/release. | Menor trabalho imediato. | Dificulta auditoria, rollback e suporte. |

Decisao fechada:

`A`.

Recomendacao tecnica:

`A`. Nao criar `/v1` especifico para este endpoint neste momento. Registrar a
primeira versao do contrato na documentacao e associar o commit/release a uma
tag Git quando a implementacao for entregue.

### 20. Como o orquestrador deve chamar identidade e produto?

Contexto:

O `eickrono-autenticacao-servidor` sera o orquestrador. A decisao tecnica
define se ele executa chamadas internas sincronas para identidade/produto, se
publica eventos em fila, ou se usa uma combinacao dos dois.

Exemplo:

Uma exclusao precisa remover acesso no Keycloak, limpar dados de autenticacao,
pedir ao servidor de identidade para desassociar avatar/formas de acesso e
pedir ao backend do produto para apagar/anonimizar perfil. Se uma chamada falha,
o orquestrador precisa saber exatamente qual etapa ficou pendente.

Opcoes:

| Opcao | Descricao | Vantagens | Riscos |
| --- | --- | --- | --- |
| A | Chamadas internas sincronas para `dryRun` e execucao, com tabela de etapas no orquestrador. | Mais simples para primeira versao e mais facil de debugar. | Operacao pode demorar e precisa timeout/retry bem definidos. |
| B | Eventos/fila para todas as etapas, inclusive `dryRun`. | Melhor desacoplamento. | Mais complexo, dificulta resposta imediata do `dryRun`. |
| C | `dryRun` sincrono; execucao real por eventos/fila. | Boa separacao entre diagnostico e execucao. | Mais infraestrutura desde a primeira versao. |

Decisao fechada:

`A`.

Recomendacao tecnica:

`A` para a primeira implementacao. O `dryRun` e a execucao ficam mais
observaveis, e a compensacao/idempotencia pode ser controlada pela tabela de
execucao do orquestrador. Eventos podem ser introduzidos depois para etapas
mais lentas.

### 21. Como modelar a auditoria e as etapas da execucao?

Contexto:

O servico precisa registrar o plano do `dryRun`, a execucao real, cada etapa,
resultado, erro, retry e pos-condicao. Isso pode ficar em uma tabela unica ou
em tabelas separadas.

Exemplo:

Uma execucao apaga o usuario Keycloak, mas falha ao limpar avatar. O suporte
precisa consultar a correlacao e ver que Keycloak terminou, avatar ficou
pendente e produto ainda nao pode ser considerado totalmente limpo.

Opcoes:

| Opcao | Descricao | Vantagens | Riscos |
| --- | --- | --- | --- |
| A | Uma tabela de execucao e uma tabela filha de etapas. | Modelo claro, consultavel e idempotente. | Exige duas tabelas e DTOs de etapa. |
| B | Uma unica tabela com JSON de plano/resultado. | Implementacao inicial menor. | Pior para consulta, retry por etapa e auditoria fina. |
| C | Uma tabela por sistema participante. | Separacao forte por sistema. | Complexidade alta e dificil correlacao global. |

Decisao fechada:

`A`.

Recomendacao tecnica:

`A`. Usar tabela principal para correlacao, alvo, solicitante, motivo, status e
tabela filha para etapas por sistema/recurso. O JSON pode existir como snapshot
do plano, mas nao deve substituir etapas consultaveis.

### 22. Onde deve ficar a pendencia de remocao de avatar?

Contexto:

O avatar pertence ao dominio de identidade, mas o orquestrador da exclusao sera
o `eickrono-autenticacao-servidor`. A pendencia precisa guardar `bucket`,
`storageKey`, origem, produto e dono antes de qualquer limpeza logica.

Exemplo:

Se o registro de avatar for apagado antes de remover o arquivo fisico, o
`storageKey` pode se perder. Se a pendencia ficar no lugar errado, a Lambda pode
precisar de permissao e consultas demais.

Opcoes:

| Opcao | Descricao | Vantagens | Riscos |
| --- | --- | --- | --- |
| A | Pendencia fica no servidor de identidade, dono do avatar; orquestrador cria a solicitacao por endpoint interno. | Mantem o dado perto do dominio de avatar. | Orquestrador precisa consultar status na identidade. |
| B | Pendencia fica no servidor de autenticacao, dono da orquestracao. | Facilita visao central da execucao. | Autenticacao passa a armazenar detalhe de storage de identidade. |
| C | Pendencia duplicada: autenticacao guarda etapa; identidade guarda pendencia tecnica de storage. | Melhor separacao entre orquestracao e dominio de avatar. | Mais contrato e sincronizacao. |

Decisao fechada:

`A`.

Recomendacao tecnica:

`A`. E mais simples e mais correto manter a pendencia tecnica no servidor de
identidade, porque ele e o dono do avatar e dos metadados de storage. O
orquestrador registra a etapa global e chama a identidade por endpoint interno;
a identidade materializa a pendencia com `bucket + storageKey` e fica
responsavel pelo worker ou Lambda de remocao fisica.

### 23. A remocao fisica de avatar entra na primeira versao?

Contexto:

A regra alvo e apagar fisicamente objetos controlados pela Eickrono. Falta
definir se a primeira versao ja executa S3/Lambda ou se apenas registra a
pendencia e deixa a remocao fisica para pacote seguinte.

Exemplo:

O servico pode liberar e-mail/usuario/rede social corretamente mesmo que o
arquivo de avatar fique pendente por alguns minutos. Mas se a remocao fisica
ficar fora da primeira versao, o documento deve declarar essa limitacao.

Opcoes:

| Opcao | Descricao | Vantagens | Riscos |
| --- | --- | --- | --- |
| A | Primeira versao cria pendencia e ja aciona Lambda/worker de remocao fisica. | Entrega o fluxo completo. | Exige infraestrutura e IAM no mesmo pacote. |
| B | Primeira versao cria pendencia, mas nao remove fisicamente ainda. | Reduz risco inicial. | Deixa dado pessoal em storage por mais tempo e exige controle claro de pendencia. |
| C | Remocao fisica direta e sincrona pelo servidor de identidade. | Simples em codigo. | Acopla API a S3 e piora timeout/retry. |

Decisao fechada:

`A`.

Recomendacao tecnica:

`A`. A primeira versao deve criar a pendencia e acionar Lambda/worker de
remocao fisica com permissoes minimas.

### 24. Qual deve ser o escopo IAM do worker/Lambda de avatar?

Contexto:

O worker de avatar precisa apagar objetos, mas nao deve ter permissao ampla no
bucket inteiro se isso permitir apagar arquivos fora do escopo.

Exemplo:

Se o bucket tiver `avatares/thimisu/...` e outros prefixos futuros, a Lambda nao
deve poder apagar qualquer objeto fora dos prefixos de avatar controlados pela
Eickrono.

Opcoes:

| Opcao | Descricao | Vantagens | Riscos |
| --- | --- | --- | --- |
| A | Permissao limitada aos prefixos de avatar por bucket/produto. | Bom equilibrio entre seguranca e operacao. | Precisa padronizar prefixos. |
| B | Permissao por lista exata de `storageKey` em runtime. | Mais restritivo conceitualmente. | IAM nao opera bem com listas dinamicas por execucao. |
| C | Permissao para apagar qualquer objeto no bucket de avatares. | Simples. | Mais permissivo do que necessario. |

Decisao fechada:

`A`.

Recomendacao tecnica:

`A`. Padronizar prefixos de avatar por produto/origem e negar qualquer
`storageKey` fora do prefixo permitido no codigo e na permissao IAM.

### 25. Como validar a matriz contra o schema real antes de codificar?

Contexto:

A matriz deste documento foi escrita a partir do entendimento atual. Antes de
implementar queries destrutivas, ela precisa ser comparada com o schema real dos
bancos e com as migrations atuais.

Exemplo:

Se uma tabela de sessoes, dispositivos ou avatar existir no banco real e nao
estiver na matriz, o servico pode dizer que limpou o usuario mas deixar bloqueio
para novo cadastro ou login.

Opcoes:

| Opcao | Descricao | Vantagens | Riscos |
| --- | --- | --- | --- |
| A | Gerar snapshot SQL/MD dos schemas reais e atualizar a matriz antes do Pacote 1. | Reduz risco de tabela esquecida. | Adiciona etapa preparatoria. |
| B | Validar schema durante a implementacao de cada pacote. | Mais rapido para comecar. | Pode descobrir divergencia tarde. |
| C | Confiar somente na documentacao atual. | Menos trabalho. | Risco alto de erro operacional. |

Decisao fechada:

`A`.

Recomendacao tecnica:

`A`. Antes de codificar deletes ou anonimizações, gerar snapshot de tabelas,
colunas, FKs e indices dos bancos envolvidos e revisar a matriz. O snapshot
inicial desta etapa esta em
`documentacao/snapshot_schema_exclusao_usuario_cadastro_produto.md`.

### 26. Qual sera o valor inicial de retencao das pendencias de avatar?

Contexto:

A pendencia de avatar guarda dados tecnicos suficientes para remover o objeto
fisico e auditar a tentativa. Ela nao deve ficar para sempre sem necessidade,
mas precisa existir tempo suficiente para retry, auditoria e investigacao de
falha.

Exemplo:

Se a Lambda falha por permissao, a pendencia precisa continuar consultavel ate
o problema ser corrigido. Depois de sucesso e prazo de retencao, pode ser
minimizada ou apagada conforme politica.

Opcoes:

| Opcao | Descricao | Vantagens | Riscos |
| --- | --- | --- | --- |
| A | 7 dias. | Menor retencao de dado tecnico. | Pouco tempo para investigacao. |
| B | 30 dias. | Bom equilibrio entre retry, suporte e minimizacao. | Retem metadados por mais tempo. |
| C | 90 dias. | Mais margem para auditoria operacional. | Retencao maior que o necessario para maioria dos casos. |
| D | Outro valor configurado pela operacao. | Flexivel. | Precisa decisao externa antes de implementar. |

Decisao fechada:

`B`.

Recomendacao tecnica:

`B`. Valor inicial de 30 dias, configuravel por variavel de ambiente com
fallback em `application*.properties`, conforme ja definido na regra de
precedencia.

## Proximos passos operacionais

1. Congelar esta especificacao como referencia do escopo.
2. Usar o snapshot validado contra STG como base tecnica inicial. A validacao
   local preliminar mostrou schemas defasados e nao deve ser usada como fonte
   final sem migrar o banco local.
3. Criar o contrato administrativo no `eickrono-autenticacao-servidor`, ainda
   sem deletar dados.
4. Implementar somente o `dryRun`, com resolvedores por Keycloak, autenticacao,
   identidade, produto e storage.
5. Validar o `dryRun` contra os schemas reais e contra a matriz deste documento.
6. Implementar execucao compensavel/idempotente somente depois do `dryRun`
   cobrir todos os alvos e bloqueios.
7. Implementar remocao fisica de avatar por pendencia materializada e Lambda ou
   worker equivalente.
8. Rodar testes unitarios, integracao e verificacao manual controlada.
9. Revisar `git diff` contra esta especificacao antes de publicar a entrega.
10. Criar tag Git/release associando a implementacao a versao do contrato.

## Checklist de implementacao por pacote

Esta checklist existe para impedir que a exclusao de cadastro/produto, avatar
e a migracao `Long`/`UUID` sejam misturados no mesmo pacote. Cada pacote deve
revisar este documento antes de implementar e depois
validar se o comportamento final continua aderente ao escopo.

Regra de execucao dos pacotes:

1. Antes de iniciar o pacote, reler esta especificacao e confirmar que o escopo
   do pacote nao altera decisoes ja fechadas.
2. Durante o pacote, implementar somente os arquivos necessarios para cumprir
   o escopo declarado.
3. Ao terminar, rodar testes unitarios e de integracao do pacote.
4. Revisar `git diff` contra esta especificacao e listar qualquer diferenca
   antes de seguir para o pacote seguinte.
5. Se o pacote reabrir regra funcional, parar e atualizar a especificacao antes
   de codificar mais.

### Pacote 1 - contrato administrativo do orquestrador

Projeto principal: `eickrono-autenticacao-servidor`.

Status atual:

- contrato inicial implementado em
  `POST /api/interna/usuarios/exclusoes`;
- contrato aceita `dryRun=true` para simulacao e `dryRun=false` para execucao
  real controlada;
- `dryRun=false` exige `correlacaoId` retornado por um `dryRun=true`
  `PLANEJADA`, e o plano atual deve continuar consistente com o plano aprovado;
- permissao administrativa exigida por `SCOPE_admin:exclusoes` ou `ROLE_admin`;
- o orquestrador ja resolve `catalogo.clientes_ecossistema` e
  `autenticacao.usuarios_clientes_ecossistema`;
- o plano ja lista recursos da autenticacao, preservacoes canonicas basicas e
  bloqueios para partes ainda nao resolvidas;
- a execucao real materializa pendencia de avatar fisico na identidade antes da
  limpeza logica local;
- a execucao real chama o produto alvo para apagar/desassociar o perfil do
  produto;
- quando o usuario de autenticacao nao possui outros vinculos de produto, a
  execucao remove o usuario Keycloak por cliente interno e limpa o usuario
  central da autenticacao depois de anonimizar auditorias e soltar FKs
  operacionais;
- quando o usuario de autenticacao possui vinculos com outros produtos, a
  execucao preserva usuario central, formas de acesso, dispositivos e Keycloak.

Escopo:

- criar contrato interno administrativo para solicitar exclusao/reset de
  cadastro/produto;
- entrada minima deve resolver o alvo por `produto + usuarioPublicoProduto` ou
  `produto + perfilProdutoId`, sem exigir que o operador informe todas as
  chaves alternativas;
- manter `dryRun` obrigatorio;
- retornar plano de acao com categorias `APAGAR`, `ANONIMIZAR`, `PRESERVAR`,
  `NAO_TOCAR` e `BLOQUEAR`;
- bloquear admin do realm `master`, service accounts, clients, providers
  sociais, secrets, migrations e configuracoes.

Fora de escopo:

- apagar dados fisicamente;
- criar Lambda de S3;
- mudar `pessoaIdCentral Long`;
- remover compatibilidades historicas de pessoa/perfil.

Testes minimos:

- unitario do DTO de request/resposta;
- unitario de validacao de permissao/motivo;
- unitario de bloqueio de recursos proibidos;
- teste de serializacao do `dryRun`.

### Pacote 2 - resolvedores de alvo por sistema

Projeto principal: `eickrono-autenticacao-servidor`.

Status atual:

- resolvedor interno do produto Thimisu iniciado;
- `eickrono-thimisu-backend` expoe
  `POST /api/interna/perfis-sistema/exclusoes-cadastro-produto/dry-run`;
- o endpoint interno usa o mesmo padrao ja existente de chamada interna:
  JWT backchannel, `X-Eickrono-Internal-Secret` e validacao de cliente chamador;
- o dryRun do Thimisu lista apenas recursos do produto:
  `perfis_sistema`, `pessoas_produto_local`,
  `perfis_sistema_historico` e `pessoas_produto_local_historico`;
- o orquestrador da autenticacao chama esse dryRun por interface de resolvedor;
- o dryRun do Keycloak ja e materializado a partir de
  `autenticacao.usuarios.sub_remoto`, sem chamar Admin API e sem tocar em
  configuracoes globais;
- o plano do Keycloak lista `realm eickrono user_entity` como `APAGAR` apenas
  quando existe `sub_remoto` resolvido;
- o plano do Keycloak lista `realm master admin`, clients e identity providers
  globais como `NAO_TOCAR`;
- se algum usuario de autenticacao nao possuir `sub_remoto`, o dryRun bloqueia
  execucao futura com `keycloak_sub_nao_resolvido`;
- o dryRun de storage/avatar ja classifica `identidade.avatar_usuario`:
  registros com `storage_key` entram como `MATERIALIZAR_PENDENCIA`; registros
  sem `storage_key` entram como `NAO_APAGAR_URL_EXTERNA`;
- o dryRun de identidade fina ja resolve a `pessoa_id` do usuario de
  autenticacao e lista `identidade.pessoas` e `identidade.contatos_email` como
  `PRESERVAR`, com contagem real.

Escopo:

- resolver alvo no Keycloak sem tocar em clients/providers;
- na primeira implementacao, resolver Keycloak por `sub_remoto` ja persistido
  na autenticacao; a execucao real posterior deve usar Admin API/cliente
  interno para remover o usuario do realm correto;
- resolver alvo no banco de autenticacao usando primeiro o alvo de produto
  resolvido, e depois e-mail, usuario de autenticacao, sub e provedor social
  como chaves auxiliares;
- resolver alvo no banco de identidade por contato, pessoa canonica, avatar e
  compatibilidades historicas de pessoa/perfil, apenas para detectar bloqueios
  tecnicos quando necessario;
- resolver alvo no backend do produto pelo par produto + usuario publico ou
  perfil do produto;
- listar conflitos quando as chaves auxiliares apontarem para mais de um alvo
  de produto/acesso.

Fora de escopo:

- executar delete;
- consolidar dados divergentes automaticamente;
- migrar `Long` para `UUID`.

Testes minimos:

- unitarios por resolvedor;
- teste com alvo encontrado por `produto + usuarioPublicoProduto`;
- teste com alvo encontrado por `produto + perfilProdutoId`;
- teste com e-mail auxiliar resolvendo o mesmo alvo ja identificado pelo
  produto;
- teste com `sub` auxiliar resolvendo o mesmo alvo ja identificado pelo
  produto;
- teste com alvo social Google/Apple;
- teste com mesmo `usuarioPublicoProduto` em dois produtos diferentes sem
  apagar o outro produto;
- teste com conflito retornando `BLOQUEAR`.

### Pacote 3 - execucao compensavel e idempotente

Projeto principal: `eickrono-autenticacao-servidor`.

Status atual:

- migration `V33__criar_auditoria_exclusao_cadastro_produto.sql` criada no
  `eickrono-autenticacao-servidor`;
- a tabela principal `auditoria.exclusoes_cadastro_produto` registra
  correlacao, alvo, solicitante, motivo, status, plano e resultado;
- a tabela filha `auditoria.exclusoes_cadastro_produto_etapas` registra etapas
  por sistema/recurso, ordem, quantidade planejada, status, tentativas e erro;
- o endpoint administrativo com `dryRun=true` ja grava a simulacao na tabela
  principal e materializa as etapas planejadas, preservadas e bloqueadas;
- o `dryRun` preserva `autenticacao.usuarios`, formas de acesso, dispositivos,
  historico central e usuario Keycloak quando o usuario de autenticacao tem
  vinculo ativo com outro produto; isso nao bloqueia apagar/desassociar o perfil
  do produto alvo;
- o `dryRun` da autenticacao ja inclui a etapa de preservacao/minimizacao dos
  historicos `auditoria.usuarios_clientes_ecossistema_historico` e
  `auditoria.usuarios_historico`;
- a migration `V34__preparar_auditoria_exclusao_cadastro_produto.sql` prepara
  esses historicos para preservar auditoria sem manter FK obrigatoria para
  usuario/vinculo removido, adicionando `anonimizado_em` e
  `correlacao_exclusao_cadastro_produto`;
- o dryRun interno do Thimisu planeja `ANONIMIZAR` para
  `perfis_sistema_historico` e `pessoas_produto_local_historico` quando esses
  historicos ainda dependem do perfil/pessoa alvo por FK;
- o `eickrono-thimisu-backend` ja possui preparacao tecnica para historico
  anonimizado: migration `V14__preparar_historico_exclusao_cadastro_produto.sql`
  torna as FKs de historico nullable, adiciona `anonimizado_em` e
  `correlacao_exclusao_cadastro_produto`, e o servico
  `AnonimizacaoHistoricoExclusaoCadastroProdutoServico` remove a dependencia por
  FK e substitui PII direta por marcadores `anonimizado:<id>`;
- o `eickrono-thimisu-backend` ja possui endpoint interno isolado
  `POST /api/interna/perfis-sistema/exclusoes-cadastro-produto/execucoes`,
  que exige `correlacaoId`, anonimiza historico dependente, apaga
  `perfis_sistema` e apaga `pessoas_produto_local` somente quando a pessoa local
  nao possui outro perfil no produto;
- o endpoint destrutivo do produto ja e chamado pelo orquestrador do
  `eickrono-autenticacao-servidor` quando o plano nao possui bloqueios;
- planos com avatar controlado por `storage_key` materializam pendencia na
  identidade antes da limpeza logica local;
- quando o usuario central e exclusivo do produto alvo, a execucao remove o
  usuario no Keycloak por cliente interno, anonimiza historicos centrais,
  remove referencias operacionais de seguranca/auditoria, limpa dispositivos,
  recuperacoes de senha, cadastros, formas de acesso, vinculos de produto,
  avatares locais e por fim remove `autenticacao.usuarios`;
- quando o usuario central tem outro vinculo de produto ativo, a execucao apaga
  apenas o vinculo do produto alvo e preserva usuario central, formas de acesso,
  dispositivos e Keycloak;
- a execucao valida pos-condicoes locais na autenticacao: se ainda existir
  vinculo de produto, avatar local do vinculo, usuario central, forma de acesso
  ou cadastro que o plano exigia apagar, a operacao e marcada como `FALHOU`
  e retorna erro operacional em vez de registrar sucesso falso.

Escopo:

- executar apenas o plano previamente resolvido;
- registrar auditoria antes de qualquer operacao destrutiva;
- apagar/desassociar dados do produto alvo;
- apagar usuario/formas de acesso/vinculos do alvo em autenticacao;
- apagar usuario Keycloak do realm correto quando aplicavel;
- nao operar sobre estruturas sociais removidas; tratar compatibilidades
  historicas apenas como bloqueio tecnico quando impedirem a execucao segura;
- validar pos-condicoes automaticamente.

Fora de escopo:

- apagar pessoa canonica;
- apagar dados de outros produtos;
- apagar configuracoes globais;
- remover fisicamente objetos S3 sem pendencia materializada.

Testes minimos:

- integracao com cadastro completo;
- integracao liberando novo cadastro com mesmo e-mail;
- integracao liberando novo cadastro com mesmo usuario;
- integracao liberando novo uso da mesma rede social;
- teste de idempotencia executando duas vezes;
- teste de falha parcial com plano auditavel.

### Pacote 4 - avatar e remocao fisica de storage

Projetos envolvidos: `eickrono-autenticacao-servidor` e
`eickrono-identidade-servidor`.

Status atual:

- migration `V42__criar_pendencias_remocao_avatar_usuario.sql` criada no
  `eickrono-identidade-servidor`;
- a tabela `identidade.pendencias_remocao_avatar_usuario` materializa
  `correlacao_id`, `avatar_id`, `usuario_cliente_id`, produto, origem,
  `bucket`, `storage_key`, status, tentativas e prazo de retencao;
- endpoint interno `POST /identidade/avatares/interna/remocoes/pendencias`
  criado no `eickrono-identidade-servidor` para materializar pendencias por
  `correlacaoId + produto + usuarioClienteIds`;
- a materializacao usa `IDENTIDADE_AVATAR_STORAGE_BUCKET` para gravar o bucket
  e `IDENTIDADE_AVATAR_REMOCAO_PENDENCIA_RETENCAO_DIAS` com fallback de 30 dias
  para calcular a retencao da pendencia;
- o endpoint e idempotente por `avatar_id + storage_key`: uma segunda chamada
  nao duplica a mesma pendencia;
- worker equivalente criado no `eickrono-identidade-servidor` para processar
  pendencias `PENDENTE`/`FALHOU`, apagar S3 por `bucket + storage_key`, tratar
  objeto ausente como sucesso idempotente e marcar falha tecnica sem perder a
  pendencia;
- o worker fica desligado por padrao e e ativado por
  `IDENTIDADE_AVATAR_REMOCAO_WORKER_HABILITADO=true`; lote, tentativas e
  intervalo sao configuraveis por variavel de ambiente com fallback no
  `application*.yml`;
- Lambda separada continua opcional/futura se a empresa quiser isolar a
  permissao IAM fora do servidor de identidade.

Escopo:

- no `dryRun`, listar avatares controlados pela Eickrono por `storageKey`;
- no `dryRun`, tratar URL de avatar sem `storageKey` como URL externa
  preservada, nao como objeto fisico a apagar;
- materializar pendencia de remocao antes de alterar o registro logico;
- apagar objeto S3 por `bucket + storageKey`, nunca por URL publica;
- marcar/desassociar avatar apos remocao fisica ou apos registrar pendencia
  compensavel;
- respeitar prazo configuravel de retencao temporaria da pendencia.

Fora de escopo:

- apagar URL externa de Google/Apple;
- tentar apagar objeto sem `storageKey`;
- depender de consultar registro de avatar depois que ele ja foi apagado.

Testes minimos:

- unitario de resolucao de `storageKey`;
- unitario materializando pendencia com `bucket`, status `PENDENTE` e prazo de
  retencao;
- unitario rejeitando materializacao sem bucket configurado;
- unitario rejeitando paths fora do prefixo permitido;
- controller test garantindo validacao de chamada interna antes de materializar
  pendencia;
- unitario do worker apagando objeto S3 por `bucket + storageKey`;
- unitario do worker tratando `NoSuchKey` como sucesso idempotente;
- unitario do worker rejeitando `storageKey` fora de `avatares/`;
- integracao simulando S3;
- teste de idempotencia quando o objeto ja nao existe;
- teste garantindo que URL externa nao e apagada.

### Pacote 5 - migracao `pessoaCanonicaId UUID`

Projetos envolvidos: `eickrono-autenticacao-servidor`,
`eickrono-identidade-servidor` e backends de produto.

Escopo:

- adicionar `pessoaCanonicaId UUID` em paralelo a `pessoaIdCentral Long`;
- atualizar DTOs internos para transportar os dois campos durante a transicao;
- adicionar colunas canonicas em cadastro, dispositivo, atestacao, vinculo
  organizacional e pendencias de produto;
- novas leituras devem preferir UUID quando existir;
- manter fallback `Long` apenas enquanto houver registros antigos.

Fora de escopo:

- trocar `ContextoPessoaPerfilSistema.pessoaId` diretamente para `UUID` sem
  campo paralelo;
- dropar `pessoa_id_perfil` na mesma entrega;
- dropar `pessoas_identidade`, `perfis_identidade` ou `pessoas_formas_acesso`
  antes da migracao completa.

Testes minimos:

- cadastro novo grava `pessoaCanonicaId`;
- login por senha resolve por UUID;
- login social resolve por UUID;
- registro de dispositivo usa UUID;
- biometria valida a conta correta por identificador canonico;
- provisionamento de produto recebe UUID e ainda aceita `Long` em fallback;
- registro antigo sem UUID continua funcionando durante a transicao.

### Pacote 6 - remocao final de compatibilidades historicas

Pre-condicao:

todos os pacotes anteriores precisam estar validados no ambiente alvo, sem
consultas funcionais para as estruturas historicas de pessoa/perfil.

Escopo:

- remover fallbacks `ClienteContextoPessoaPerfilSistemaLegado`;
- remover construtores antigos que instanciam resolvedores por repositorios
  legados;
- remover uso de `pessoa_id_perfil`;
- dropar estruturas historicas por migration segura quando nao houver mais
  runtime dependente;
- garantir que o servico de exclusao nao reintroduza etapa funcional de legado
  social.

Testes minimos:

- busca global por nomes legados sem uso funcional;
- migration test;
- smoke test de cadastro, login, social, biometria, recuperacao e exclusao;
- validacao de que novo cadastro nao depende de estruturas historicas.

## Testes obrigatorios

### Unitarios

- resolve alvo principal por `produto + usuarioPublicoProduto`;
- resolve alvo principal por `produto + perfilProdutoId`;
- usa e-mail, usuario de autenticacao, `sub` e provedor social apenas como
  chaves auxiliares depois que o alvo de produto foi identificado;
- retorna `BLOQUEAR` quando chaves auxiliares apontam para mais de um alvo de
  produto/acesso;
- retorna `BLOQUEAR` quando o produto resolve o perfil por
  `usuarioPublicoProduto`, mas `autenticacao.usuarios_clientes_ecossistema` esta
  sem `identificador_publico_cliente` ou com valor diferente;
- permite o mesmo `usuarioPublicoProduto` em produtos diferentes sem tratar o
  identificador como global;
- bloqueia exclusao do admin do realm `master`;
- bloqueia exclusao de service account;
- preserva pessoa canonica no servidor de identidade;
- classifica registros por `APAGAR`, `ANONIMIZAR`, `PRESERVAR`, `NAO_TOCAR`,
  e `BLOQUEAR`;
- nao lista `vinculos_sociais` nem `contextos_sociais_pendentes` no `dryRun`;
- `dryRun=true` nao altera dados;
- `dryRun=false` exige motivo e permissao interna.

### Integracao

- cria usuario completo e executa o servico para exclusao de cadastro/produto;
- apos criar usuario completo pelo fluxo real do app, valida que
  `autenticacao.usuarios_clientes_ecossistema.identificador_publico_cliente`
  foi preenchido com o mesmo valor de
  `thimisu_stg.perfis_sistema.identificador_publico_sistema`;
- valida que o `dryRun=true` do usuario recem-cadastrado resolve
  `usuariosAutenticacaoIds` e `vinculosProdutoIds` antes da execucao real;
- valida que a execucao usa o perfil do produto alvo como fonte principal;
- valida novo cadastro com mesmo e-mail;
- valida novo cadastro com mesmo usuario;
- valida novo cadastro/login com mesma rede social;
- valida que outro produto com o mesmo `usuarioPublicoProduto` nao foi apagado
  nem alterado;
- valida que pessoa canonica nao foi apagada;
- valida que perfil do produto foi apagado/anonimizado;
- valida que `vinculos_sociais` e `contextos_sociais_pendentes` nao sao
  consultados;
- valida que historicos do produto sao anonimizados antes da remocao do perfil
  e da pessoa local, sem preservar PII direta;
- valida que configuracoes Keycloak de Google/Apple continuam intactas;
- valida que admin master continua autenticando.

### Verificacao manual controlada

- rodar `dryRun=true` antes de cada limpeza real;
- executar limpeza real;
- cadastrar novamente com os mesmos dados;
- revisar logs de autenticacao, identidade e produto;
- confirmar que nenhum client, provider social ou service account foi removido.

## Status das decisoes de desenho

| Item | Status | Decisao/observacao |
| --- | --- | --- |
| Mapear tabela por tabela nos bancos envolvidos e classificar cada uma na matriz final. | Decisao sanada. | Snapshot SQL/MD foi gerado e validado contra STG em `documentacao/snapshot_schema_exclusao_usuario_cadastro_produto.md`. O banco local esta defasado e nao deve guiar queries destrutivas. |
| Definir orquestrador. | Sanada. | O orquestrador sera o `eickrono-autenticacao-servidor`. Ele coordenara Keycloak, identidade, produto, storage e auditoria por contratos internos. |
| Definir contrato interno entre autenticacao, identidade e backend do produto para execucao atomica ou compensavel. | Decisao sanada. | Usar chamadas internas sincronas para `dryRun` e execucao, com tabela principal de execucao e tabela filha de etapas no orquestrador. |
| Definir autorizacao de execucao. | Sanada. | O servico tera contrato unico. A execucao sera controlada por permissao, aprovacao, auditoria e politica de retencao; qualquer bloqueio temporario deve ser explicito e nao deve criar outro contrato ou outra logica. |
| Definir politica de remocao fisica dos objetos de avatar no S3. | Decisao sanada. | Pendencia tecnica fica no servidor de identidade; a primeira versao deve acionar Lambda/worker; IAM limitado aos prefixos de avatar por bucket/produto. |
| Definir retencao das pendencias de remocao de avatar. | Decisao sanada. | Valor inicial de 30 dias, configuravel por variavel de ambiente com fallback para `application*.properties`. |
