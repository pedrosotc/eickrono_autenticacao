# Snapshot de schema - servico de exclusao de usuario/cadastro/produto

## Objetivo

Registrar o snapshot tecnico inicial dos schemas envolvidos no servico de
exclusao de usuario/cadastro/produto, antes de implementar o Pacote 1.

Este snapshot compara as migrations versionadas dos projetos com a matriz da
especificacao:

- `documentacao/especificacao_servico_exclusao_usuario_cadastro_produto.md`

## Fonte do snapshot

Fonte usada nesta primeira versao:

- migrations versionadas do `eickrono-autenticacao-servidor`;
- migrations versionadas do `eickrono-identidade-servidor`;
- migrations versionadas do `eickrono-thimisu-backend`.

Ainda falta, antes de codificar queries destrutivas:

- validar este snapshot contra o banco vivo do ambiente alvo;
- conferir FKs, indices e colunas reais via `information_schema`;
- conferir Keycloak por Admin API ou consulta controlada de leitura.

## Validacao local preliminar

Em 2026-05-29 foi feita uma validacao local somente leitura no Postgres de
desenvolvimento (`eickrono-postgres-dev`), usando `information_schema`.

Resultado:

| Banco local | Resultado | Conclusao |
| --- | --- | --- |
| `eickrono_identidade_stg` | Schema `identidade_stg` esta em Flyway V13. Ainda possui `vinculos_sociais` e nao possui o modelo canonico novo de avatar. | Nao representa o estado alvo atual. Nao usar como fonte final do servico. |
| `eickrono_identidade` | Possui schemas antigos `autenticacao`, `identidade`, `seguranca`, `auditoria` e ainda possui `autenticacao.contextos_sociais_pendentes` e `identidade.vinculos_sociais`. | Banco local antigo/misto. Nao usar como fonte final do servico sem migrar. |
| `eickrono_thimisu_stg` | Sem tabelas de aplicacao no momento da consulta. | Nao serve para validar o schema de produto. |
| `eickrono_flashcard_stg` | Possui tabelas antigas `flashcard_stg.pessoas`, `flashcard_stg.usuarios` e historicos. | Parece representar modelo antigo do produto, nao o schema alvo `thimisu_stg`. |

Decisao tecnica:

- esta validacao local nao substitui a validacao contra o ambiente alvo;
- o snapshot de referencia continua sendo o das migrations versionadas;
- antes de queries destrutivas, o ambiente alvo precisa ser consultado por
  `information_schema` ou o banco local precisa ser migrado para o mesmo estado
  das migrations atuais.

## Validacao STG por `information_schema`

Em 2026-05-29 foi feita consulta somente leitura no RDS de STG usando task
Fargate temporaria com `psql`.

Resultado por banco:

| Banco STG | Resultado | Conclusao |
| --- | --- | --- |
| `keycloak_stg` | 90 tabelas no schema `public`, incluindo `user_entity`, `federated_identity`, `credential`, `user_attribute`, `realm`, `client` e `identity_provider`. | O resolvedor Keycloak deve usar Admin API ou leitura controlada dessas tabelas, preservando configuracoes globais. |
| `eickrono_identidade_stg` | Schemas novos `catalogo`, `autenticacao`, `identidade`, `dispositivos`, `seguranca`, `auditoria` existem. | Este e o banco alvo para resolver autenticacao/identidade em STG. |
| `eickrono_thimisu_stg` | Schema `thimisu_stg` possui `pessoas_produto_local`, `perfis_sistema` e historicos. | Este e o banco alvo do produto Thimisu em STG. |

Tabelas novas confirmadas em `eickrono_identidade_stg`:

| Tabela | Colunas chave confirmadas | Uso no servico |
| --- | --- | --- |
| `autenticacao.cadastros_conta` | `id`, `pessoa_id`, `usuario_id`, `cliente_ecossistema_id`, `email_id`, `telefone_id`, `status_processo`, `email_confirmado_em`, `concluido_em` | Resolver e apagar cadastro alvo. |
| `autenticacao.cadastros_conta_avatares` | `id`, `cadastro_id`, `origem_id`, `url_avatar`, `storage_key`, `hash_conteudo`, `versao`, `preferido` | Apagar avatares temporarios do cadastro; materializar remocao fisica se houver `storage_key`. |
| `autenticacao.cadastros_conta_vinculos_sociais_confirmados` | `id`, `cadastro_id`, `provedor`, `identificador_externo`, `email_social`, `url_avatar_externo`, `avatar_preferido`, `consumido_em` | Apagar vinculos sociais confirmados do cadastro alvo. |
| `autenticacao.usuarios` | `id`, `pessoa_id`, `sub_remoto`, `status_global`, `credencial_local_habilitada` | Apagar usuario de autenticacao do alvo. |
| `autenticacao.usuarios_formas_acesso` | `id`, `usuario_id`, `email_id`, `tipo`, `provedor`, `identificador_externo`, `url_avatar_externo` | Apagar formas de acesso locais e sociais do usuario alvo. |
| `autenticacao.usuarios_clientes_ecossistema` | `id`, `usuario_id`, `cliente_ecossistema_id`, `identificador_publico_cliente`, `avatar_preferido_*` | Desvincular usuario do produto alvo e liberar identificador publico. |
| `identidade.pessoas` | `id`, `nome_completo`, `tipo_pessoa`, `status_identidade` | Pessoa canonica preservada neste servico. |
| `identidade.contatos_email` | `id`, `pessoa_id`, `email_normalizado`, `principal`, `verificado_em` | Contato canonico preservado, salvo decisao futura de exclusao canonica. |
| `identidade.contatos_telefone` | `id`, `pessoa_id`, `telefone_normalizado`, `principal`, `verificado_em` | Contato canonico preservado neste servico. |
| `identidade.avatar_origens` | `id`, `codigo`, `tipo`, `cliente_ecossistema_id`, `permite_vinculo_social`, `permite_upload_usuario` | Catalogo; nao apagar. |
| `identidade.avatar_usuario` | `id`, `usuario_cliente_id`, `origem_id`, `forma_acesso_id`, `url_avatar`, `storage_key`, `hash_conteudo`, `versao`, `preferido`, `removido_em` | Resolver avatar preferido/controlado e gerar pendencia de remocao fisica. |

FKs relevantes confirmadas em `eickrono_identidade_stg`:

| FK | Impacto |
| --- | --- |
| `cadastros_conta_avatares.cadastro_id -> cadastros_conta.id` | Cadastro deve ser tratado antes/durante limpeza de avatares temporarios. |
| `cadastros_conta_vinculos_sociais_confirmados.cadastro_id -> cadastros_conta.id` | Vinculos de cadastro devem acompanhar a exclusao do cadastro. |
| `usuarios_formas_acesso.usuario_id -> usuarios.id` | Formas de acesso devem ser apagadas antes do usuario. |
| `usuarios_clientes_ecossistema.usuario_id -> usuarios.id` | Vinculo produto deve ser apagado/desassociado antes do usuario. |
| `usuarios_clientes_ecossistema.avatar_preferido_forma_acesso_id -> usuarios_formas_acesso.id` | Antes de apagar forma de acesso, limpar/desassociar referencia de avatar preferido. |
| `avatar_usuario.origem_id -> avatar_origens.id` | Origem e catalogo de avatar sao preservados. |
| `contatos_email.pessoa_id -> pessoas.id` e `contatos_telefone.pessoa_id -> pessoas.id` | Pessoa canonica e contatos ficam fora da exclusao de produto. |

Tabelas confirmadas em `eickrono_thimisu_stg`:

| Tabela | Colunas chave confirmadas | Uso no servico |
| --- | --- | --- |
| `thimisu_stg.pessoas_produto_local` | `id`, `sub`, `email`, `nome`, `tipo_pessoa`, `telefone_principal`, `cadastro_id_origem`, `pessoa_id_central` | Apagar/anonimizar dados pessoais do produto alvo. |
| `thimisu_stg.perfis_sistema` | `id`, `pessoa_produto_local_id`, `perfil_sistema_id`, `identificador_publico_sistema`, `email`, `ativo`, `status` | Remover/liberar perfil publico do produto alvo. |
| `thimisu_stg.pessoas_produto_local_historico` | Snapshot historico de pessoa do produto. | Preservar/anonimizar conforme regra de auditoria/produto. |
| `thimisu_stg.perfis_sistema_historico` | Snapshot historico de perfil. | Preservar/anonimizar conforme regra de auditoria/produto. |
| `thimisu_stg.documentos_historico` | Historico de documentos. | Preservar/anonimizar; nao apagar cegamente. |

Lacunas reais confirmadas em STG:

| Lacuna | Onde deve nascer |
| --- | --- |
| Tabela principal da execucao administrativa. | `eickrono-autenticacao-servidor`. |
| Tabela filha de etapas por sistema/recurso. | `eickrono-autenticacao-servidor`. |
| Tabela de pendencia tecnica de remocao fisica de avatar. | `eickrono-identidade-servidor`. |
| Endpoint interno de `dryRun` e execucao do produto Thimisu. | `eickrono-thimisu-backend`. |

## Resumo executivo

| Item | Status no snapshot | Impacto no servico |
| --- | --- | --- |
| Tabelas de contexto social pendente | Removidas por migrations nos servidores. | Nao devem voltar ao contrato nem ao `dryRun`. |
| Tabela `vinculos_sociais` antiga | Removida por migrations nos servidores. | Nao deve ser fonte de limpeza nova. |
| Modelo de avatar canonico | Existe em `identidade.avatar_usuario` e `identidade.avatar_origens`. | Pode ser resolvido no `dryRun`; falta pendencia tecnica de remocao fisica. |
| Avatares de cadastro | Existem em `autenticacao.cadastros_conta_avatares`. | Devem ser apagados junto com cadastro alvo. |
| Vinculos sociais confirmados de cadastro | Existem em `autenticacao.cadastros_conta_vinculos_sociais_confirmados`. | Devem ser apagados junto com cadastro alvo. |
| Execucao administrativa da exclusao | Nao existe tabela ainda. | Precisa migration no Pacote 1/3. |
| Etapas da execucao | Nao existe tabela ainda. | Precisa migration no Pacote 1/3. |
| Pendencia tecnica de remocao de avatar | Nao existe tabela ainda. | Precisa migration no `eickrono-identidade-servidor`. |
| Thimisu produto | Usa `pessoas_produto_local`, `perfis_sistema` e historicos. | Limpeza precisa separar dados pessoais apagaveis de historicos preservados/anonimizados. |

## eickrono-autenticacao-servidor

### Tabelas ativas relevantes

| Tabela | Origem | Papel no servico |
| --- | --- | --- |
| `catalogo.clientes_ecossistema` | V15 | Resolver produto; nao apagar. |
| `catalogo.sistemas_origem` | V15 | Configuracao/catalogo; nao apagar. |
| `autenticacao.usuarios` | V15 | Apagar usuario de autenticacao/acesso do alvo. |
| `autenticacao.usuarios_formas_acesso` | V15 | Apagar formas de acesso do usuario alvo. |
| `autenticacao.usuarios_clientes_ecossistema` | V15 | Apagar/desvincular relacao do usuario com o produto alvo. |
| `autenticacao.cadastros_conta` | V15, V22 | Apagar cadastro pendente/finalizado do alvo. |
| `autenticacao.recuperacoes_senha` | V15 | Apagar recuperacoes do usuario alvo. |
| `dispositivos.registros_dispositivo` | V16 | Apagar registros de dispositivo do usuario alvo. |
| `dispositivos.codigos_verificacao_dispositivo` | V16 | Apagar codigos de dispositivo ligados ao alvo. |
| `dispositivos.dispositivos_confiaveis` | V16 | Apagar/desautorizar dispositivos confiaveis do alvo. |
| `dispositivos.tokens_dispositivo` | V16 | Apagar/invalidar tokens de dispositivo do alvo. |
| `seguranca.atestacoes_app_desafios` | V17 | Apagar desafios pendentes do alvo quando vinculados. |
| `seguranca.credenciais_atestacao_dispositivo` | V17 | Apagar credenciais tecnicas do alvo quando vinculadas. |
| `seguranca.apple_app_attest_chaves` | V17 | Configuracao/chaves; nao apagar por usuario salvo se houver chave por dispositivo alvo. |
| `auditoria.operacoes_atestadas` | V17 | Preservar/anonimizar conforme regra de auditoria. |
| `auditoria.google_play_integrity_veredictos` | V17 | Preservar/anonimizar conforme regra de seguranca. |
| `auditoria.usuarios_historico` | V17 | Preservar/anonimizar. |
| `auditoria.usuarios_clientes_ecossistema_historico` | V17 | Preservar/anonimizar. |
| `autenticacao.pendencias_integracao_produto` | V23 | Cancelar/apagar pendencias do alvo para nao recriar perfil. |
| `autenticacao.parametros_scheduler_integracao_produto` | V23 | Configuracao; nao apagar. |
| `autenticacao.controles_integracao_produto` | V23 | Configuracao por produto; nao apagar. |
| `autenticacao.cadastros_conta_vinculos_sociais_confirmados` | V29 | Apagar vinculos sociais confirmados no cadastro alvo. |
| `identidade.avatar_origens` | V30 | Catalogo de origem de avatar; nao apagar. |
| `identidade.avatar_usuario` | V30 | Resolver/desassociar avatares do usuario/produto alvo. |
| `autenticacao.cadastros_conta_avatares` | V30 | Apagar avatares capturados durante cadastro alvo. |

### Estruturas antigas/removidas

| Estrutura | Origem | Estado |
| --- | --- | --- |
| `autenticacao.contextos_sociais_pendentes` | Criada em V26, removida em V31 | Nao deve ser consultada nem recriada. |
| `vinculos_sociais` | Criada em V1, removida em V32 | Nao deve ser consultada nem recriada. |

### Lacunas para implementar

| Necessidade | Status | Acao esperada |
| --- | --- | --- |
| Execucao administrativa da exclusao | Ausente | Criar tabela principal no orquestrador. |
| Etapas da execucao | Ausente | Criar tabela filha de etapas no orquestrador. |
| Snapshot do plano `dryRun` | Ausente | Campo JSONB na tabela principal ou tabela propria, mantendo etapas consultaveis. |

## eickrono-identidade-servidor

### Tabelas ativas relevantes

| Tabela | Origem | Papel no servico |
| --- | --- | --- |
| `catalogo.clientes_ecossistema` | V15 | Resolver produto; nao apagar. |
| `autenticacao.usuarios` | V15 | Apagar/desassociar usuario de acesso se a identidade mantiver copia do modelo. |
| `autenticacao.usuarios_formas_acesso` | V15 | Apagar formas de acesso do usuario alvo. |
| `autenticacao.usuarios_clientes_ecossistema` | V15 | Apagar/desvincular relacao usuario/produto alvo. |
| `autenticacao.cadastros_conta` | V15 | Apagar cadastro alvo quando existir neste servidor. |
| `autenticacao.recuperacoes_senha` | V15 | Apagar recuperacoes do usuario alvo. |
| `identidade.pessoas` | V38 | Pessoa canonica; preservar. |
| `identidade.contatos_email` | V38 | Preservar contato canonico; desassociar apenas se for forma de acesso e a regra permitir. |
| `identidade.contatos_telefone` | V38 | Preservar contato canonico; nao apagar neste servico. |
| `identidade.avatar_origens` | V38 | Catalogo de origem de avatar; nao apagar. |
| `identidade.avatar_usuario` | V38 | Resolver/desassociar avatar do usuario/produto alvo. |
| `autenticacao.cadastros_conta_avatares` | V38 | Apagar avatares de cadastro alvo quando existir neste servidor. |
| `autenticacao.cadastros_conta_vinculos_sociais_confirmados` | V40 | Apagar vinculos sociais confirmados no cadastro alvo. |

### Estruturas antigas/removidas

| Estrutura | Origem | Estado |
| --- | --- | --- |
| `autenticacao.contextos_sociais_pendentes` | Criada em V34, removida em V39 | Nao deve ser consultada nem recriada. |
| `vinculos_sociais` | Criada em V1, removida em V41 | Nao deve ser consultada nem recriada. |

### Compatibilidades historicas ainda existentes

| Estrutura | Estado | Regra para este servico |
| --- | --- | --- |
| `perfis_identidade` | Criada em V1 | Nao usar como fonte funcional nova; pode indicar bloqueio tecnico se houver divergencia. |
| `pessoas_identidade` | Criada em V3 | Nao usar como fonte funcional nova; manter ate migracao `Long`/`UUID`. |
| `pessoas_formas_acesso` | Criada em V3 | Nao usar como fonte de vinculo social novo; validar apenas se ainda houver dependencia runtime. |

### Lacunas para implementar

| Necessidade | Status | Acao esperada |
| --- | --- | --- |
| Pendencia tecnica de remocao de avatar | Ausente | Criar tabela no servidor de identidade com `bucket`, `storageKey`, origem, produto, dono, status, tentativas e retencao. |
| Endpoint interno de `dryRun` de avatar | Ausente | Expor para o orquestrador listar avatares controlados pela Eickrono. |
| Endpoint interno de execucao de avatar | Ausente | Expor para materializar pendencia e acionar Lambda/worker. |

## eickrono-thimisu-backend

### Tabelas ativas relevantes

| Tabela | Origem | Papel no servico |
| --- | --- | --- |
| `pessoas_produto_local` | V2, renomeada em V11 | Dado pessoal do produto; apagar/anonimizar conforme politica. |
| `perfis_sistema` | V2, renomeada em V11/V12 | Perfil do produto; remover/liberar `identificador_publico_sistema` do alvo. |
| `pessoas_produto_local_historico` | V7, renomeada em V11/V12 | Preservar/anonimizar conforme auditoria e regra de produto. |
| `perfis_sistema_historico` | V7, renomeada em V11/V12 | Preservar/anonimizar conforme auditoria e regra de produto. |
| `documentos_historico` | V7 | Preservar/anonimizar; pode referenciar dados de terceiros/regra legal. |

### Estruturas removidas

| Estrutura | Origem | Estado |
| --- | --- | --- |
| `pessoas_formas_acesso` | Criada em V2, removida em V3 | Nao deve ser consultada. |
| `atestacoes_app_desafios` | Criada em V4, removida em V13 | Nao deve ser consultada no produto. |
| `operacoes_atestadas` | Criada em V6, removida em V13 | Nao deve ser consultada no produto. |

### Lacunas para implementar

| Necessidade | Status | Acao esperada |
| --- | --- | --- |
| Endpoint interno de `dryRun` do produto | Ausente no snapshot | Listar perfil/dados do produto que seriam apagados, anonimizados ou preservados. |
| Endpoint interno de execucao do produto | Ausente no snapshot | Apagar/anonimizar somente dados do produto alvo. |
| Pos-condicao de liberacao do usuario publico | Ausente no snapshot | Confirmar que `identificador_publico_sistema` pode ser reutilizado apos execucao. |

## Keycloak

As tabelas do Keycloak nao estao em migrations destes projetos. O snapshot
operacional deve ser feito por Admin API ou leitura controlada do banco
Keycloak antes da implementacao do resolvedor.

Regras ja fechadas:

- apagar usuario real do realm da aplicacao quando pertencer ao alvo;
- apagar credenciais e identidades federadas desse usuario;
- invalidar sessoes/tokens;
- nunca apagar realm `master`, usuario `admin`, clients, service accounts,
  identity providers, secrets ou configuracoes globais.

## Comparacao com a matriz da especificacao

| Item da matriz | Resultado do snapshot |
| --- | --- |
| `contextos_sociais_pendentes` nao deve entrar no fluxo novo | Confirmado por migrations de drop nos servidores. |
| `vinculos_sociais` antigo nao deve entrar no fluxo novo | Confirmado por migrations de drop nos servidores. |
| Avatar deve ter pendencia materializada antes da limpeza logica | Ainda nao existe tabela; precisa implementacao. |
| Execucao compensavel/idempotente com etapas | Ainda nao existe tabela; precisa implementacao. |
| Produto deve limpar apenas o perfil do produto alvo | Thimisu tem tabelas proprias de produto; precisa endpoint interno para `dryRun` e execucao. |
| Configuracoes globais devem ser preservadas | Catalogos e parametros aparecem separados; devem ser `NAO_TOCAR`. |
| Pessoa canonica deve ser preservada | `identidade.pessoas`, `contatos_email` e `contatos_telefone` existem; regra de preservacao confirmada. |

## Proximo passo

Antes de criar qualquer endpoint destrutivo:

1. Confirmar este snapshot contra o banco vivo via `information_schema`.
2. Atualizar este arquivo se houver tabela/coluna real que nao aparece nas
   migrations versionadas.
3. Iniciar Pacote 1 com contrato administrativo e `dryRun`, sem delete fisico.
