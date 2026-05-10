# Analise de Fronteiras Funcionais entre Autenticacao, Identidade e Thimisu-Backend

Este documento responde a pergunta funcional mais importante da refatoracao:

- ainda existem funcoes de autenticacao no projeto errado?
- ou o ecossistema ja esta coerente e o que resta e mais migracao tecnica do
  que refactor de responsabilidade?

## Escopo analisado

- `eickrono-identidade-servidor`
- `eickrono-thimisu-backend/modulos/thimisu-backend`
- contratos internos entre os dois servicos

Nao entram aqui:

- nomes de artefato, modulo Maven ou arquivo `.p12`
- convencoes de host e DNS
- detalhes de app Flutter

## Resultado resumido

No recorte atual, a fronteira funcional principal **esta coerente**.

Hoje:

- no runtime atual, a borda publica de cadastro, login, recuperacao de senha,
  confirmacao e dispositivo ainda passa por contratos fisicamente localizados
  em `modulos/modulo-eickrono-autenticacao`;
- o `thimisu-backend` nao recebe senha, codigo nem tentativa de login do app;
- o `thimisu-backend` ficou concentrado em contexto do produto, provisionamento
  do perfil do sistema e leitura local do proprio dominio.

Entao, neste momento, o que ainda resta e majoritariamente:

- migracao tecnica de nomes e contratos internos;
- limpeza de restos legados;
- hardening operacional.

Nao sobrou, hoje, um fluxo publico grande de autenticacao claramente no
projeto errado.

## Mapa de responsabilidades por servico

## Runtime publico atual em `modulo-eickrono-autenticacao`

### Deve continuar aqui

Rotas e fluxos publicos de autenticacao:

- `POST /api/publica/cadastros`
- `POST /api/publica/convites/{codigo}/cadastros`
- `GET /api/publica/cadastros/usuarios/disponibilidade`
- `POST /api/publica/cadastros/{cadastroId}/confirmacoes/email`
- `POST /api/publica/cadastros/{cadastroId}/confirmacoes/email/reenvio`
- `DELETE /api/publica/cadastros/{cadastroId}`
- `POST /api/publica/sessoes`
- `POST /api/publica/sessoes/refresh`
- `POST /api/publica/recuperacoes-senha`
- `POST /api/publica/recuperacoes-senha/{fluxoId}/confirmacoes/email`
- `POST /api/publica/recuperacoes-senha/{fluxoId}/confirmacoes/email/reenvio`
- `POST /api/publica/recuperacoes-senha/{fluxoId}/senha`

Rotas publicas de seguranca e onboarding de dispositivo:

- `POST /api/publica/atestacoes/desafios`
- `POST /api/publica/atestacoes/validacoes`
- `POST /api/conta/dispositivos/registro`
- `POST /api/conta/dispositivos/registro/silencioso`
- `POST /api/conta/dispositivos/registro/{id}/confirmacao`
- `POST /api/conta/dispositivos/registro/{id}/reenviar`
- `POST /api/conta/dispositivos/revogar`
- `GET /identidade/dispositivos/token/validacao`
- `GET /api/conta/dispositivos/token/validacao/interna`

Rotas de conta autenticada que tambem pertencem a autenticacao:

- `GET /api/conta/redes-sociais`
- `POST /api/conta/redes-sociais/{provedor}/sincronizacao`
- `DELETE /api/conta/redes-sociais/{provedor}`
- `GET /api/conta/vinculos-organizacionais`
- `GET /api/publica/convites/{codigo}`

Backchannel interno que tambem faz sentido aqui:

- `POST /identidade/cadastros/interna`
- `POST /identidade/cadastros/interna/{cadastroId}/confirmacoes/email`
- `POST /identidade/cadastros/interna/{cadastroId}/confirmacoes/email/reenvio`
- `POST /identidade/sessoes/interna`
- `POST /identidade/atestacoes/interna/desafios`
- `POST /identidade/atestacoes/interna/validacoes`

### Leitura funcional

No estado atual do codigo, esse servico e a **borda publica efetiva** de:

- autenticacao publica;
- ciclo de vida do cadastro;
- recuperacao de senha;
- confianca do dispositivo;
- vinculo social;
- politicas de sessao.

Isso esta coerente como estado de transicao. Na arquitetura alvo aprovada, a
borda publica final do app deve convergir para `autenticacao`, e `identidade`
deve ficar em backchannel interno quando ainda for necessaria.

## `thimisu-backend`

### Deve continuar aqui

Rotas efetivamente encontradas:

- `GET /api/v1/estado`
- `GET /api/interna/perfis-sistema/contexto`
- `GET /api/interna/perfis-sistema/disponibilidade`
- `POST /api/interna/perfis-sistema/provisionamentos`

### Leitura funcional

Esse servico esta concentrado em:

- resolucao de contexto local do produto;
- provisionamento idempotente de `PerfilSistema`;
- persistencia de copia local de `PessoaProdutoLocal`, sem ownership de
  `Pessoa` canonica;
- contratos internos do proprio produto, sem depender mais de namespace final
  `/api/interna/identidade`.

Ou seja:

- a borda publica principal continua coerente;
- o backend do produto ja fala majoritariamente a linguagem de `PerfilSistema`;
- o que ainda resta e mais limpeza residual e coerencia documental do que corte
  funcional forte.

## Achados objetivos

### 1. Nao encontrei login publico no `thimisu-backend`

No modulo `thimisu-backend`, nao existe rota publica de:

- login
- senha
- recuperacao de senha
- confirmacao de codigo
- refresh de sessao

Isso significa que o principal desvio historico ja foi saneado.

### 2. A disponibilidade de `usuario + sistema` ja foi centralizada

A verificacao de disponibilidade de `usuario + sistema` deixou de ser decidida
localmente no `thimisu-backend`.

Hoje a leitura correta e:

- a `autenticacao` controla essa regra do ecossistema;
- o backend do produto consome essa resposta por `backchannel`;
- o contrato interno novo ja fala em `/identidade/perfis-sistema/interna`.

### 3. O provisionamento final ja foi quebrado no desenho correto

O provisionamento tambem ja nao deve mais ser lido como uma operacao unica de
"identidade dentro do thimisu".

Hoje a direcao correta e:

- `autenticacao -> identidade` para confirmar ou atualizar `Pessoa`;
- `autenticacao -> backend do produto` para criar ou atualizar o
  `PerfilSistema`.

No backend do produto, o contrato novo ja esta separado em
`/api/interna/perfis-sistema/provisionamentos`.

### 4. O resquicio funcional legado ja foi removido

`GET /identidade/perfil` foi removido da API de autenticacao e do pacote
compartilhado. A leitura autenticada agora deve usar contratos vivos, como
`/api/conta/redes-sociais`, `/api/conta/vinculos-organizacionais` ou o
backend de dominio propriamente dito.

### 5. O que resta hoje e mais tecnico do que funcional

Os pontos mais pendentes nao sao "funcao no projeto errado", e sim:

- fechamento da separacao fisica do banco do produto;
- limpeza de documentos secundarios e diagramas ainda em linguagem antiga;
- hardening operacional da fila de pendencias de integracao com produto;
- revisao final de aliases residuais onde ainda houver compatibilidade externa
  temporaria.

## Conclusao

A refatoracao funcional principal ja esta em boa forma.

No recorte analisado:

- a autenticacao publica ainda passa por `eickrono-autenticacao` no runtime
  atual;
- dominio e provisionamento interno estao no `thimisu-backend`;
- nao encontrei um fluxo grande de login/cadastro/senha claramente perdido no
  backend de dominio.

Entao a resposta objetiva e:

- **sim**, a fronteira de funcoes entre os projetos hoje esta majoritariamente
  correta;
- o que ainda falta e principalmente migracao tecnica, operacao e fechamento
  documental;
- o que sobra agora nao e mais um desvio grande de ownership funcional.

## Proximos passos recomendados

1. Fechar a separacao fisica do banco do produto na Etapa 5.
2. Concluir a especificacao e futura implementacao da fila de pendencias de
   integracao com produto.
3. Continuar a limpeza documental dos artefatos secundarios que ainda falam a
   linguagem do legado.
