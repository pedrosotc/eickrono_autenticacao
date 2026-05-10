# Runbook de Teste Integrado Dev com Produto Indisponivel

## Objetivo

Validar, em `dev`, o comportamento conjunto de:

- `autenticacao`
- `identidade`
- `backend do produto`
- fila persistida de pendencias de integracao com produto
- `scheduler` (rotina agendada) de novas tentativas

Este runbook cobre tres cenarios:

1. cadastro confirmado com o produto fora do ar;
2. religamento do produto e drenagem automatica da fila;
3. login central com o produto fora do ar.

## Topologia usada nesta validacao

Topologia real usada na validacao de `2026-05-03`:

- `Keycloak` local em `http://localhost:8080`
- `Postgres` local em `localhost:5432`
- `MailHog` local em `http://localhost:8025`
- `autenticacao` rodando localmente em `http://localhost:8084`
- `identidade` rodando localmente em `http://localhost:8081`
- `thimisu-backend` rodando em container:
  - HTTP publico em `http://localhost:8083`
  - HTTPS interno em `https://localhost:18483`

Observacao:

- nesta validacao, `autenticacao` e `identidade` rodaram como `jar` local;
- `thimisu-backend` rodou em container Docker;
- isso foi suficiente para validar o comportamento funcional da fila e do login
  central.

## Pre-requisitos

Antes de executar o fluxo:

- `autenticacao` deve responder `200` em `http://localhost:8084/actuator/health`;
- `identidade` deve responder `200` em `http://localhost:8081/actuator/health`;
- `Keycloak`, `Postgres` e `MailHog` devem estar de pe;
- o client interno `eickrono-keycloak` deve existir no `Keycloak`
  local;
- o client interno `thimisu-backend` deve existir no `Keycloak` local;
- o segredo interno `X-Eickrono-Internal-Secret` deve estar alinhado entre os
  servidores.

## Parametros importantes do scheduler

O `scheduler` le os parametros efetivos do banco:

- tabela: `autenticacao.parametros_scheduler_integracao_produto`

Na validacao desta passada, o comportamento real so ficou rapido o suficiente
depois de garantir:

- `tempo_entre_tentativas_segundos = 10`
- `timeout_sondagem_millis = 2000`
- `timeout_entrega_millis = 5000`

Consulta util:

```sql
select *
from autenticacao.parametros_scheduler_integracao_produto
where id = 1;
```

Se o valor de `tempo_entre_tentativas_segundos` estiver alto, como `300`, a
fila vai funcionar corretamente, mas a nova tentativa so ocorrera depois de
`5` minutos.

## Regra de negocio validada

Regra vigente que este teste comprova:

- falha do backend do produto nao bloqueia o login central;
- depois que conta central e `Pessoa` foram resolvidas, o produto pode ficar
  pendente;
- a fila persistida assume a entrega ao produto;
- quando o produto volta, o `scheduler` deve concluir a entrega e apagar a
  pendencia.

## Passo 1. Desligar o backend do produto

Parar o container:

```bash
docker stop eickrono-thimisu-backend-compose-dev
```

Confirmar que ele realmente saiu do ar:

```bash
docker ps -a --format '{{.Names}}|{{.Status}}' | rg 'eickrono-thimisu-backend-compose-dev'
```

Resultado esperado:

- `Exited (...)`

## Passo 2. Gerar desafio de atestacao do cadastro

O fluxo publico de cadastro exige desafio valido de atestacao.

Endpoint:

- `POST /api/publica/atestacoes/desafios`

Exemplo:

```json
{
  "operacao": "CADASTRO",
  "plataforma": "IOS",
  "usuarioSub": null,
  "pessoaIdPerfil": null,
  "cadastroId": null,
  "registroDispositivoId": null
}
```

Resultado esperado:

- `201 Created`
- retorno com:
  - `identificadorDesafio`
  - `desafioBase64`

## Passo 3. Criar o cadastro publico

Usar o desafio gerado no passo anterior.

Campos que nao podem faltar:

- `confirmacaoSenha`
- `aceitouTermos = true`
- `aceitouPrivacidade = true`

Observacao:

- a senha deve obedecer a politica atual;
- nesta validacao foi usada `Senha@123456`.

Resultado esperado:

- `201 Created`
- `status = PENDENTE_EMAIL`

## Passo 4. Ler o codigo de confirmacao no MailHog

Consultar:

```bash
curl -s http://localhost:8025/api/v2/messages
```

Localizar a mensagem do email do cadastro e extrair:

- `Codigo de confirmacao`
- `Cadastro`

Exemplo real validado nesta passada:

- `cadastroId = 485b7a1c-8f87-4da6-a3ac-56c50b0a9e90`
- `email = it1777837171@eickrono.local`
- `codigo = 245303`

## Passo 5. Confirmar o email com o produto ainda fora do ar

Endpoint:

- `POST /api/publica/cadastros/{cadastroId}/confirmacoes/email`

Payload:

```json
{
  "codigo": "245303"
}
```

Resultado esperado:

- `200 OK`
- `statusUsuario = PENDENTE_LIBERACAO_PRODUTO`
- `liberadoParaLogin = true`
- `proximoPasso = LOGIN`

Exemplo real desta passada:

```json
{
  "usuarioId": "",
  "statusUsuario": "PENDENTE_LIBERACAO_PRODUTO",
  "cadastroId": "485b7a1c-8f87-4da6-a3ac-56c50b0a9e90",
  "emailPrincipal": "it1777837171@eickrono.local",
  "emailConfirmado": true,
  "liberadoParaLogin": true,
  "proximoPasso": "LOGIN"
}
```

## Passo 6. Confirmar que a fila foi criada

Consulta:

```sql
select id,
       status_pendencia,
       tentativas_realizadas,
       codigo_ultimo_erro,
       mensagem_ultimo_erro
from autenticacao.pendencias_integracao_produto
where cadastro_id = '485b7a1c-8f87-4da6-a3ac-56c50b0a9e90';
```

Resultado esperado com o produto fora do ar:

- existe `1` linha;
- `status_pendencia = AGUARDANDO_NOVA_TENTATIVA`;
- `tentativas_realizadas >= 1`;
- `codigo_ultimo_erro = SONDAGEM_FALHOU`

Exemplo real desta passada:

- `89cdec4c-4dc9-4746-83a8-2e4c5723b19c|AGUARDANDO_NOVA_TENTATIVA|1|SONDAGEM_FALHOU`

## Passo 7. Ligar o backend do produto

Subir novamente o container:

```bash
docker start eickrono-thimisu-backend-compose-dev
```

Confirmar saude publica:

```bash
curl -sf http://localhost:8083/actuator/health
```

Resultado esperado:

- `{"status":"UP",...}`

## Passo 8. Esperar o scheduler drenar a pendencia

Com o produto de pe e `tempo_entre_tentativas_segundos = 10`, aguardar um ciclo.

Sinais esperados no log da `autenticacao`:

- `HTTP GET https://localhost:18483/api/v1/estado`
- `HTTP POST https://localhost:18483/api/interna/perfis-sistema/provisionamentos`
- `DELETE FROM autenticacao.pendencias_integracao_produto`
- log final:
  - `concluidas=1`

Trecho real observado:

- `Scheduler de integracao com produto executado. recuperadas=0, reservadas=1, concluidas=1, reagendadas=0, escaladas=0, pausadas=0.`

## Passo 9. Confirmar que a fila foi drenada

Consulta:

```sql
select count(*)
from autenticacao.pendencias_integracao_produto
where cadastro_id = '485b7a1c-8f87-4da6-a3ac-56c50b0a9e90';
```

Resultado esperado:

- `0`

## Passo 10. Confirmar o provisionamento no banco do produto

Consultas:

```sql
select p.id,
       p.identificador_publico_sistema,
       p.status,
       pl.pessoa_id_central
from thimisu.perfis_sistema p
join thimisu.pessoas_produto_local pl
  on pl.id = p.pessoa_produto_local_id
where lower(p.identificador_publico_sistema) = lower('it1777837171.user');
```

```sql
select id,
       pessoa_id_central,
       sub,
       email
from thimisu.pessoas_produto_local
where email = 'it1777837171@eickrono.local';
```

Resultado esperado:

- perfil criado em `thimisu.perfis_sistema`;
- `status = LIBERADO`;
- copia local da pessoa criada em `thimisu.pessoas_produto_local`.

Exemplo real desta passada:

- `2|it1777837171.user|LIBERADO|2`
- `2|2|912c7d67-c96e-45be-8e71-6407d57fa077|it1777837171@eickrono.local`

## Passo 11. Validar login central com o produto fora do ar

Desligar o produto de novo:

```bash
docker stop eickrono-thimisu-backend-compose-dev
```

Gerar desafio de login:

- `POST /api/publica/atestacoes/desafios`

Payload:

```json
{
  "operacao": "LOGIN",
  "plataforma": "IOS",
  "usuarioSub": null,
  "pessoaIdPerfil": null,
  "cadastroId": null,
  "registroDispositivoId": null
}
```

Depois chamar:

- `POST /api/publica/sessoes`

Resultado esperado:

- `200 OK`
- `autenticado = true`
- `statusUsuario = PENDENTE_LIBERACAO_PRODUTO`

Exemplo real desta passada:

```json
{
  "statusUsuario": "PENDENTE_LIBERACAO_PRODUTO",
  "autenticado": true,
  "tipoToken": "Bearer",
  "...": "..."
}
```

Isso comprova a regra de negocio:

- o login central nao depende da disponibilidade imediata do backend do produto.

## Observacoes importantes

### 1. O fluxo publico exige desafio de atestacao real

Nao funciona usar payload fixo sem antes criar:

- `POST /api/publica/atestacoes/desafios`

Sem isso, o backend devolve:

- `desafio_nao_encontrado`

### 2. O estado do cadastro legado pode nao refletir toda a orquestracao nova

Em alguns pontos desta validacao, a tabela legado `identidade.cadastros_conta`
nao foi o melhor indicador do estado final da fila.

Para este teste, os indicadores mais confiaveis foram:

- resposta HTTP da confirmacao;
- tabela `autenticacao.pendencias_integracao_produto`;
- log do `scheduler`;
- tabelas do banco do produto.

### 3. O `scheduler` pode estar correto e ainda parecer lento

Se o banco estiver com:

- `tempo_entre_tentativas_segundos = 300`

o comportamento sera correto, mas a nova tentativa so acontecera em `5`
minutos.

## Definicao de pronto deste runbook

Considerar o teste integrado aprovado quando:

1. com o produto desligado, a confirmacao de email devolve:
   - `PENDENTE_LIBERACAO_PRODUTO`
   - `liberadoParaLogin = true`
2. a fila recebe a pendencia e registra `SONDAGEM_FALHOU`;
3. com o produto religado, o `scheduler` remove a pendencia;
4. o perfil aparece no banco do produto como `LIBERADO`;
5. com o produto desligado novamente, o `LOGIN` publico continua funcionando.
