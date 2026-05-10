# Classificacao da Documentacao do Ecossistema

Este documento classifica os arquivos `.md` atualmente encontrados em:

- `eickrono-autenticacao-servidor`
- `eickrono-thimisu-backend`
- `eickrono-identidade-servidor`

Objetivo:

- deixar explicito o que e documento canônico;
- deixar explicito o que e historico ou legado;
- deixar explicito o que ainda precisa alinhar para nao conflitar com o
  consolidado atual.

Data desta classificacao:

- `2026-05-06`

## Criterios

### Canonico

Documento atual, valido e confiavel dentro do seu escopo.

Pode ser:

- referencia principal de arquitetura;
- guia operacional vigente;
- especificacao vigente;
- agenda local vigente;
- documentacao setorial atual de modulo ou infraestrutura.

### Historico ou legado

Documento que continua util para contexto, rastreio de decisoes antigas ou
retrato de um estado anterior, mas que nao deve ser lido como fonte principal
de verdade.

### Precisa alinhar

Documento ainda util, mas que hoje pode conflitar com o conjunto canônico
porque:

- ainda traz termos antigos;
- ainda cita contratos ou rotas ja cortados;
- ainda fala de estados de transicao como se fossem alvo final;
- ou ainda nao foi revisado apos as ultimas mudancas de migracao.

## Resumo

Total de arquivos classificados nesta passada:

- `61` em `eickrono-autenticacao-servidor`
- `7` em `eickrono-thimisu-backend`
- `1` em `eickrono-identidade-servidor`
- `69` no total

## 1. eickrono-autenticacao-servidor

### 1.1 Canonico

- `README.md`
- `autorizacao/README.md`
- `autorizacao/providers/configuracoes-fapi/README.md`
- `autorizacao/providers/mapeamentos-atributos/README.md`
- `autorizacao/providers/scripts-spi/README.md`
- `autorizacao/temas/login-ptbr/README.md`
- `documentacao/README.md`
- `documentacao/TODO.md`
- `documentacao/TODO_seguranca_app_movel.md`
- `documentacao/arquitetura_mvc_clean.md`
- `documentacao/analise_fronteiras_funcionais_autenticacao_identidade_thimisu_backend.md`
- `documentacao/backlog_cross_service_autenticacao_oidc_dispositivo.md`
- `documentacao/checklist-seguranca-fapi.md`
- `documentacao/consolidado_migracao_autenticacao_identidade_thimisu.md`
- `documentacao/decisao_nomenclatura_repositorios_servicos.md`
- `documentacao/diagramas/casos-uso-sequencia.md`
- `documentacao/diagramas/fluxo-sequencia.md`
- `documentacao/diagramas/fluxo-sequencia-totp.md`
- `documentacao/especificacao_avatar_social_e_avatar_preferido_multiapp.md`
- `documentacao/especificacao_scheduler_pendencias_integracao_produto.md`
- `documentacao/especificacao_schema_db01_db02_db03_fluxos_publicos.md`
- `documentacao/especificacao_tecnica_contexto_email_fluxos_publicos.md`
- `documentacao/guia-arquitetura.md`
- `documentacao/guia-cloudflare-tunnel-google-keycloak-dev.md`
- `documentacao/guia-debug-eclipse.md`
- `documentacao/guia-desenvolvimento.md`
- `documentacao/guia_fluxos_login_autenticacao_app.md`
- `documentacao/guia-gerar-jwt.md`
- `documentacao/guia-mtls.md`
- `documentacao/guia-operacao-producao.md`
- `documentacao/guia-seguranca-app-movel.md`
- `documentacao/guia-tecnico-junior.md`
- `documentacao/guia-testes-swagger.md`
- `documentacao/guia-totp-keycloak.md`
- `documentacao/implementacao-provedores-sociais-keycloak.md`
- `documentacao/fluxogramas_fluxos_publicos_regra_funcional_em_fechamento.md`
- `documentacao/padrao-codigos-erro-correlacao-observabilidade.md`
- `documentacao/plano-padronizacao-realm-unico.md`
- `documentacao/plano-vinculos-sociais-keycloak.md`
- `documentacao/preenchimento-credenciais-provedores-sociais.md`
- `documentacao/matriz_migracao_autenticacao_identidade_thimisu_backend.md`
- `documentacao/mapeamento_tdd_componentes_migracoes_fluxos_publicos.md`
- `documentacao/runbook_migracao_multiapp_schemas.md`
- `documentacao/runbook_teste_integrado_dev_produto_indisponivel.md`
- `documentacao/servicos-docker-dev.md`
- `infraestrutura/prod/README.md`
- `infraestrutura/prod/cloudflare/README.md`
- `infraestrutura/prod/dns/README.md`
- `infraestrutura/prod/docker/README.md`
- `infraestrutura/prod/ecs/README.md`
- `infraestrutura/prod/pipeline/README.md`
- `infraestrutura/prod/runbook_hml_aws_operacional.md`
- `infraestrutura/prod/validacao_cabecalho_email_provedores.md`
- `modulos/modulo-eickrono-keycloak/README.md`
- `modulos/modulo-eickrono-autenticacao/README.md`
- `modulos/modulo-eickrono-keycloak/configuracoes-fapi/README.md`
- `modulos/modulo-eickrono-keycloak/mapeamentos-atributos/README.md`
- `modulos/modulo-eickrono-keycloak/scripts-spi/README.md`
- `modulos/modulo-eickrono-keycloak/temas-login-ptbr/README.md`

### 1.2 Historico ou legado

- `documentacao/fluxogramas_fluxos_publicos_estado_atual.md`
  - retrato intencional do comportamento implementado em estado anterior ou de
    transicao; foi atualizado na passada atual apenas para nao conflitar com a
    regra vigente de login central e pendencia de produto.
- `documentacao/plano_migrations_v30_v36_db01_db02_db03_local_primeiro.md`
  - plano de rollout e sequencing de uma fase especifica, nao fonte principal
    de ownership atual.

### 1.3 Precisa alinhar

- nenhum arquivo `.md` deste repositorio ficou marcado como desalinhado na
  passada atual.

## 2. eickrono-thimisu-backend

### 2.1 Canonico

- `README.md`
- `docs/arquitetura_mvc_clean.md`
- `docs/fluxo_cadastro_login_nativo.md`
- `docs/guia-mtls.md`
- `docs/proposta_cisao_identidade_thimisu.md`
- `infraestrutura/hml/README.md`
- `infraestrutura/prd/README.md`

Observacao importante:

- `docs/proposta_cisao_identidade_thimisu.md` e documento de apoio
  explicativo;
- ele continua classificado como canônico para o seu escopo;
- mas nao prevalece sobre o consolidado central em caso de divergencia.

### 2.2 Historico ou legado

- nenhum arquivo `.md` deste repositorio foi classificado como historico nesta
  passada.

### 2.3 Precisa alinhar

- nenhum arquivo `.md` deste repositorio ficou marcado como desalinhado na
  passada atual.

## 3. eickrono-identidade-servidor

### 3.1 Canonico

- `README.md`

### 3.2 Historico ou legado

- nenhum arquivo `.md` deste repositorio foi classificado como historico nesta
  passada.

### 3.3 Precisa alinhar

- nenhum arquivo `.md` deste repositorio ficou marcado como desalinhado na
  passada atual.

## 4. Ordem recomendada de alinhamento

Se a equipe quiser reduzir conflito documental com o menor esforco primeiro,
esta e a ordem recomendada:

1. nenhum item prioritario restante nesta passada

## 5. Regra de uso desta classificacao

Leitura pratica:

- para decidir arquitetura e ownership, usar primeiro os documentos canônicos;
- para entender de onde a migracao veio, consultar os historicos;
- para planejar a proxima passada documental, olhar primeiro os itens de
  `Precisa alinhar`.
