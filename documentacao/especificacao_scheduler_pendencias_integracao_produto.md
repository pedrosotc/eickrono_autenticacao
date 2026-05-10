# Especificacao Funcional e Tecnica do Scheduler de Pendencias de Integracao com Produto

Este documento detalha a proposta funcional e tecnica da trilha de
pendencias de integracao com backend de produto citada no consolidado de
migracao.

Leitura correta deste documento:

- ele detalha o tema especifico do `scheduler` (rotina agendada) de
  pendencias;
- ele complementa o
  `consolidado_migracao_autenticacao_identidade_thimisu.md`;
- ele nao substitui o consolidado para ownership geral dos projetos.

## 1. Objetivo

O objetivo desta trilha e garantir que a `autenticacao` consiga entregar, de
forma controlada e rastreavel, operacoes internas que precisem criar, alterar
ou apagar algo no backend do produto.

Na pratica, isso existe para resolver o seguinte problema:

- a conta central pode ter sido criada com sucesso;
- a `Pessoa` canonica pode ter sido criada com sucesso;
- mas a chamada para o backend do produto pode falhar por indisponibilidade,
  timeout (tempo maximo de espera) ou erro de rede;
- nesse caso, o ecossistema nao pode perder o que ja foi concluido nem pode
  ficar sem rastro do que ainda falta entregar ao produto.

O `scheduler` (rotina agendada) entra para retomar automaticamente essas
entregas pendentes.

## 2. Escopo desta especificacao

Esta especificacao cobre:

- onde a rotina deve morar;
- como ela e ativada;
- quais operacoes entram na fila de pendencia;
- como deve ser a comunicacao entre os projetos;
- se a comunicacao continua por `backchannel`;
- o que significa `backchannel`;
- qual a diferenca entre `backchannel` e `endpoint`;
- quais cenarios existem hoje;
- qual e o comportamento alvo;
- como persistir o que falta entregar;
- como o `scheduler` deve reprocessar essas pendencias.

Esta especificacao nao cobre:

- replicacao automatica entre bancos;
- troca do banco do produto por outro banco fisico;
- detalhe de tela do app para cada mensagem de erro;
- desenho final da operacao manual de suporte.

## 3. Glossario em linguagem simples

### Scheduler

`Scheduler` significa rotina agendada.

E uma rotina que roda sozinha em intervalos definidos, sem depender de um
usuario apertar botao nem de uma chamada externa chegar naquele momento.

### Pendencia

`Pendencia` significa que o ecossistema sabe que ainda falta concluir uma parte
do trabalho.

No contexto deste documento, significa:

- a parte central ja foi resolvida ou ja foi iniciada;
- mas a entrega ao produto ainda nao terminou com sucesso.

### Backchannel

Neste ecossistema, `backchannel` significa comunicacao interna entre
servidores.

Exemplo:

- `autenticacao -> identidade`
- `autenticacao -> thimisu`

O `backchannel` nao e trafego do app para a API publica. Ele e trafego entre
servidores do proprio ecossistema.

### Endpoint

`Endpoint` e a rota tecnica HTTP que recebe uma chamada.

Exemplo:

- `POST /api/interna/pessoas/confirmacoes-cadastro`
- `POST /api/interna/perfis-sistema/provisionamentos`

### Diferenca entre backchannel e endpoint

A diferenca e de nivel de conceito:

- `endpoint` e o endereco tecnico da chamada;
- `backchannel` e o modo arquitetural de comunicacao interna entre servicos.

Em outras palavras:

- o `backchannel` usa `endpoints`;
- mas nem todo `endpoint` e `backchannel`;
- um `endpoint` pode ser publico;
- outro `endpoint` pode ser interno e usado somente por `backchannel`.

### Sondagem operacional

`Sondagem operacional` significa uma checagem simples para saber se o backend
do produto esta de pe e apto a receber a operacao antes da tentativa real.

Ela nao substitui a chamada real. Ela so evita iniciar um processo quando o
produto ja esta claramente indisponivel.

### Idempotencia

`Idempotencia` significa poder repetir a mesma operacao sem duplicar o efeito.

Exemplo:

- o produto recebe duas vezes a mesma criacao de perfil com a mesma
  `idempotencyKey`;
- o resultado final deve continuar sendo um unico perfil do sistema, nao dois.

## 4. Decisao central de ownership

O `scheduler` (rotina agendada) desta trilha deve morar na
`autenticacao-servidor`.

Motivo:

- a `autenticacao` e a orquestradora do fluxo central;
- ela ja decide a conta central;
- ela ja decide a liberacao de `usuario + sistema`;
- ela ja conversa internamente com `identidade` e com o backend do produto;
- entao ela e o lugar correto para registrar o que ainda falta ser entregue ao
  produto.

Este `scheduler` nao deve morar:

- no app;
- no `eickrono-identidade-servidor`;
- no `eickrono-thimisu-backend`.

O backend do produto continua sendo o recebedor da operacao, mas nao o dono da
fila central de reentrega.

## 5. Onde isto fica no codigo

### Repositorio

- `eickrono-autenticacao-servidor`

### Modulo

- `modulo-eickrono-autenticacao`

### O que e este modulo

Apesar do nome, este modulo nao e apenas "identidade" no sentido puro de
ownership de `Pessoa`.

Hoje ele e o modulo Spring Boot que sobe a API publica do ecossistema para o
app e tambem concentra a orquestracao central de cadastro, sessao e integracoes
internas.

Em linguagem simples:

- ele e um modulo Maven dentro do repositorio
  `eickrono-autenticacao-servidor`;
- ele gera a aplicacao Spring Boot chamada `eickrono-autenticacao`;
- ele sobe como processo HTTP proprio;
- ele ja concentra a borda publica do app e varias integracoes internas.

Ainda existem nomes operacionais legados em alguns artefatos de infraestrutura,
mas a localizacao fisica e o `artifactId` atual deste componente ja
convergiram para `modulo-eickrono-autenticacao`.

### Motivo de ficar neste modulo

Hoje e neste modulo que ja vivem:

- a borda publica de cadastro e sessao;
- a orquestracao interna da conta central;
- a conversa interna com `identidade`;
- a conversa interna com backend do produto;
- os `schedulers` (rotinas agendadas) ja existentes.

### Estrutura tecnica recomendada

Sugestao de organizacao:

- `aplicacao/servico/IntegracaoProdutoPendenteScheduler`
- `aplicacao/servico/PendenciaIntegracaoProdutoService`
- `aplicacao/servico/ExecutorPendenciaIntegracaoProdutoService`
- `aplicacao/servico/SondagemOperacionalProdutoService`
- `dominio/modelo/PendenciaIntegracaoProduto`
- `dominio/modelo/StatusPendenciaIntegracaoProduto`
- `dominio/modelo/TipoOperacaoIntegracaoProduto`
- `dominio/repositorio/PendenciaIntegracaoProdutoRepositorio`

### Persistencia recomendada

Schema:

- `autenticacao`

Tabela sugerida:

- `autenticacao.pendencias_integracao_produto`

## 6. O que existe hoje no codigo real

Hoje o projeto ja tem suporte real a `scheduler` (rotina agendada).

### Ativacao global de scheduler

Em
`modulos/modulo-eickrono-autenticacao/src/main/java/com/eickrono/api/identidade/infraestrutura/configuracao/InfraestruturaBasicaConfiguracao.java`
ja existe:

- `@EnableScheduling`

Entao o modulo ja sobe com suporte nativo a rotinas agendadas do Spring.

### Schedulers existentes hoje

Hoje ja existem pelo menos estas duas rotinas:

1. `CadastroContaPendenteScheduler`
   - expurga cadastro pendente expirado;
   - hoje roda com `@Scheduled(fixedDelayString = "PT1H")`.

2. `RegistroDispositivoScheduler`
   - expira registros pendentes de dispositivo;
   - hoje roda com `@Scheduled(fixedDelayString = "PT15M")`.

### O que significam `PT1H` e `PT15M`

Esses valores sao duracoes no padrao `ISO-8601`.

Leitura simples:

- `PT1H` = periodo de tempo de 1 hora;
- `PT15M` = periodo de tempo de 15 minutos.

No contexto do `@Scheduled(fixedDelayString = "...")`, eles significam:

- a rotina executa;
- quando ela termina, o Spring espera esse tempo;
- depois inicia a proxima execucao.

### Onde esses valores ficam hoje

Hoje eles estao escritos diretamente no codigo Java:

- `CadastroContaPendenteScheduler.java`
  - `@Scheduled(fixedDelayString = "PT1H")`
- `RegistroDispositivoScheduler.java`
  - `@Scheduled(fixedDelayString = "PT15M")`

Para o `scheduler` novo desta especificacao, a recomendacao e diferente:

- o intervalo nao deve nascer fixo e enterrado no codigo;
- ele deve poder ser parametrizado no banco da `autenticacao`;
- o `application.yml` pode servir apenas como valor padrao de seguranca.

### O que isso significa para a proposta nova

Isso prova tres coisas importantes:

1. o modulo ja aceita `scheduler` (rotina agendada) sem invencao nova;
2. o padrao tecnico ja esta presente no codigo;
3. a trilha nova nao nasce do zero, ela entra em uma infraestrutura de
   agendamento que ja existe.

### O que ainda nao existe hoje

Hoje ainda nao existe, de forma generica e persistida:

- uma fila de pendencias de integracao com produto;
- um registro formal do que precisa ser reenviado ao produto;
- uma nova tentativa automatica padronizada para create, update e delete no
  backend do produto.

## 7. Como a comunicacao entre projetos funciona hoje

### Cenario atual consolidado

Hoje a direcao canonica e esta:

1. app chama `identidade` pela API publica;
2. `identidade` integra com `autenticacao`;
3. `autenticacao` resolve conta central;
4. `autenticacao` chama `identidade` por `backchannel` para confirmar ou
   atualizar `Pessoa`;
5. `autenticacao` chama o backend do produto por `backchannel` para criar ou
   atualizar o perfil daquele sistema.

### O que ja e feito por backchannel

Hoje o `backchannel` ja e usado para:

- confirmar ou atualizar `Pessoa` na `identidade`;
- provisionar perfil do sistema no backend do produto;
- consultar disponibilidade de `usuario + sistema`.

### O que falta

O que falta e a camada de resiliencia para quando a parte central ja passou,
mas a entrega ao produto nao terminou.

## 8. O scheduler continua como backchannel?

Sim.

O `scheduler` (rotina agendada) nao substitui o `backchannel`.

Ele continua usando `backchannel`, porque o trabalho dele sera:

- decidir o que ainda precisa ser entregue;
- escolher o momento da nova tentativa automatica;
- chamar novamente os `endpoints` internos do backend do produto.

Entao a relacao correta e esta:

- o `scheduler` nao e um endpoint;
- o `scheduler` nao conversa com o app;
- o `scheduler` decide quando disparar uma nova chamada;
- a entrega real continua acontecendo por `backchannel`, usando `endpoints`
  internos do produto.

## 9. Como o scheduler deve ser ativado

## 9.1 Ativacao tecnica no bootstrap

O suporte basico ja e ativado por:

- `@EnableScheduling`

Logo, a trilha nova so precisa acrescentar uma nova classe com `@Scheduled`.

### Exemplo de ativacao esperada

Fluxo tecnico esperado:

1. a aplicacao Spring sobe;
2. o Spring encontra `@EnableScheduling`;
3. o Spring encontra o novo bean `IntegracaoProdutoPendenteScheduler`;
4. a rotina passa a executar no intervalo configurado;
5. a cada ciclo, ela le parametros operacionais e consulta as pendencias
   prontas para reenvio.

## 9.2 Ativacao funcional

O `scheduler` (rotina agendada) nao deve depender de chamada manual para
existir.

O comportamento padrao esperado e:

- subiu o servico;
- a rotina passou a existir;
- se estiver habilitada, ela trabalha sozinha.

## 9.3 Chave de habilitacao

Recomendacao:

- manter um parametro de banco que permita ligar ou desligar a rotina sem novo
  deploy;
- manter tambem valores de `fallback` (valor padrao) em `application.yml`.

### Parametros recomendados

No banco da `autenticacao`, a recomendacao e existir pelo menos:

- `schedulerIntegracaoProdutoHabilitado`
- `tempoEntreTentativasSegundos`
- `quantidadeMaximaTentativas`
- `quantidadeMaximaItensPorCiclo`
- `timeoutSondagemMillis`
- `timeoutEntregaMillis`

### Regra de leitura

Sugestao de comportamento:

1. se o parametro existir no banco, ele prevalece;
2. se nao existir, vale o padrao do `application.yml`;
3. se ambos faltarem, o servico nao deve subir com configuracao ambigua.

## 9.4 Execucao em mais de uma instancia

Se houver mais de uma replica da `autenticacao`, o `scheduler` pode rodar em
mais de uma instancia ao mesmo tempo.

Isso exige controle de concorrencia.

Recomendacao tecnica:

- usar selecao em lote com `FOR UPDATE SKIP LOCKED`;
- marcar a pendencia como `EM_PROCESSAMENTO` antes da tentativa;
- gravar qual instancia assumiu aquela pendencia;
- ter prazo de recuperacao para pendencia abandonada por crash.

Campos tecnicos recomendados para isso:

- `processandoPorInstancia`
- `processandoDesde`

## 10. Diferenca entre chamada sincrona e pendencia persistida

### Chamada sincrona simples

E o caso em que o servico chama o produto e recebe sucesso ou erro na mesma
hora, sem deixar rastro de fila persistida.

### Pendencia persistida

E o caso em que a operacao a ser entregue ao produto fica registrada em banco
antes ou durante a tentativa real.

Esse registro persiste mesmo se houver:

- timeout;
- erro de rede;
- queda do processo;
- queda temporaria do produto;
- perda da resposta depois que o produto ja processou a requisicao.

### Recomendacao deste documento

Para a trilha do produto, a recomendacao e usar pendencia persistida.

Ou seja:

- nao apenas tentar;
- registrar formalmente a entrega que precisa acontecer.

### Ajuste importante de desenho

Esta pendencia persistida deve ser entendida como fila de trabalho e nao como
historico eterno.

Leitura recomendada:

- enquanto a entrega nao concluiu, o registro fica vivo;
- se a entrega concluiu com sucesso, o registro pode ser apagado da fila;
- se a entrega falhou e ainda cabe nova tentativa, o mesmo registro continua
  vivo e o contador de tentativas e atualizado;
- se a entrega esgotou o limite automatico, o registro permanece para
  tratamento operacional.

Se um dia o ecossistema quiser historico permanente disso, a recomendacao e
criar outra trilha de auditoria separada, e nao transformar a fila inteira em
arquivo morto eterno.

## 11. Modelo funcional recomendado para a entrega

Esta e a proposta funcional recomendada.

### Passo 1. Criar o registro da entrega

Antes da chamada real ao produto, a `autenticacao` cria um registro de
pendencia contendo:

- qual produto sera chamado;
- qual endpoint sera usado;
- qual metodo HTTP sera usado;
- qual JSON deve ser enviado;
- qual e a chave de idempotencia;
- qual operacao de negocio esta sendo entregue.

### Passo 2. Fazer a sondagem operacional

Antes da entrega real, a `autenticacao` faz uma sondagem operacional simples no
produto.

Se a sondagem falhar:

- o sistema registra que o produto esta indisponivel;
- a entrega fica como pendente;
- a regra principal nao e bloquear o login central por causa disso;
- se a conta central e a `Pessoa` ja estiverem prontas, o ecossistema pode
  seguir com login central normal;
- a falha passa a aparecer apenas quando algum app realmente precisar falar com
  o backend do produto e ele ainda estiver indisponivel;
- so faz sentido devolver erro claro de comunicacao ja nessa etapa quando a
  regra daquele fluxo exigir, obrigatoriamente, que o produto responda antes de
  continuar.

### Passo 3. Fazer a chamada real

Se a sondagem passar:

- a `autenticacao` faz a chamada real ao endpoint interno do produto;
- se der certo, apaga a pendencia da fila;
- se falhar, atualiza o mesmo registro com erro, contador de tentativas e
  proxima tentativa.

### Passo 4. Nova tentativa automatica

O `scheduler` (rotina agendada) consulta o banco e refaz apenas as pendencias
aptas a nova tentativa.

### Passo 5. Escalada operacional

Se atingir o limite maximo de tentativas:

- a pendencia nao e apagada;
- ela fica marcada para tratamento operacional;
- a equipe consegue localizar o que faltou entregar.

## 12. Quais operacoes entram nesta fila

A regra consolidada ate aqui e:

- todas as operacoes que precisem criar, alterar ou apagar algo no backend do
  produto entram nesta trilha.

Exemplos:

- criar perfil do sistema no produto;
- atualizar copia local de nome, email, telefone, avatar ou data de
  nascimento, quando esse fluxo existir;
- bloquear perfil do sistema no produto;
- reativar perfil do sistema no produto;
- revogar ou apagar perfil do sistema no produto, quando a regra de negocio
  exigir.

## 13. Quais operacoes nao entram nesta fila na versao 1

Na versao 1, a recomendacao e nao colocar nesta fila:

- operacoes puramente publicas do app;
- envio de e-mail;
- validacao de senha;
- criacao da conta central;
- confirmacao da `Pessoa` canonica na `identidade`.

Motivo:

- essa fila nasce para entrega ao backend do produto;
- o ownership da conta central e da `Pessoa` canonica continua sendo resolvido
  de forma sincronizada e direta.

## 14. Tabela recomendada para persistir a pendencia

Tabela sugerida:

- `autenticacao.pendencias_integracao_produto`

### Colunas recomendadas

| Coluna | Finalidade |
| --- | --- |
| `id` | identificador tecnico da pendencia |
| `clienteEcossistemaId` | qual cliente do ecossistema devera receber a entrega |
| `tipoOperacao` | tipo funcional da entrega, como CRIAR_PERFIL, ATUALIZAR_PERFIL ou APAGAR_PERFIL |
| `uriEndpoint` | endpoint interno do produto que deve ser chamado |
| `metodoHttp` | verbo HTTP da entrega |
| `payloadJson` | corpo JSON exato que precisa ser reenviado |
| `idempotencyKey` | chave para impedir duplicidade de efeito no produto |
| `versaoContrato` | versao do formato do payload |
| `cadastroId` | referencia de negocio quando a pendencia vier de cadastro |
| `pessoaIdCentral` | referencia da `Pessoa` canonica, quando existir |
| `perfilSistemaId` | referencia do perfil do sistema, quando existir |
| `identificadorPublicoSistema` | nome publico do perfil naquele sistema |
| `statusPendencia` | estado detalhado da pendencia |
| `tentativasRealizadas` | quantas tentativas ja aconteceram |
| `ultimaTentativaEm` | momento da ultima tentativa |
| `proximaTentativaEm` | momento minimo para a proxima tentativa |
| `codigoUltimoErro` | codigo tecnico do ultimo erro observado |
| `mensagemUltimoErro` | descricao resumida do ultimo erro observado |
| `processandoPorInstancia` | instancia que assumiu temporariamente a entrega |
| `processandoDesde` | quando a instancia assumiu o processamento |
| `criadoEm` | auditoria de criacao |
| `atualizadoEm` | auditoria de atualizacao |

### Observacao importante sobre seguranca

O `payloadJson` deve guardar o corpo funcional da chamada.

Ele nao deve guardar:

- `Bearer token`;
- segredo interno;
- senha;
- certificado;
- qualquer credencial efemera de transporte.

Essas credenciais devem ser montadas de novo a cada tentativa.

## 14.1 Desenho SQL recomendado da fila

Abaixo esta uma proposta concreta de DDL para a fila.

Ela segue a linha do projeto atual:

- PostgreSQL;
- `schema` explicito;
- `jsonb` para carga util variavel;
- `varchar` para estados, em vez de enum fisico do banco;
- `references` para catalogo e entidades centrais quando fizer sentido.

```sql
CREATE TABLE IF NOT EXISTS autenticacao.pendencias_integracao_produto (
    id uuid PRIMARY KEY,
    cliente_ecossistema_id bigint NOT NULL
        REFERENCES catalogo.clientes_ecossistema (id),
    tipo_operacao varchar(64) NOT NULL,
    uri_endpoint varchar(512) NOT NULL,
    metodo_http varchar(16) NOT NULL,
    payload_json jsonb NOT NULL,
    idempotency_key varchar(255) NOT NULL,
    versao_contrato varchar(32) NOT NULL,
    cadastro_id uuid,
    pessoa_id_central uuid,
    perfil_sistema_id uuid,
    identificador_publico_sistema varchar(255),
    status_pendencia varchar(32) NOT NULL,
    tentativas_realizadas integer NOT NULL DEFAULT 0,
    ultima_tentativa_em timestamptz,
    proxima_tentativa_em timestamptz NOT NULL,
    codigo_ultimo_erro varchar(128),
    mensagem_ultimo_erro varchar(2000),
    processando_por_instancia varchar(255),
    processando_desde timestamptz,
    criado_em timestamptz NOT NULL,
    atualizado_em timestamptz NOT NULL,
    CONSTRAINT uq_pendencia_integracao_produto_idempotency
        UNIQUE (cliente_ecossistema_id, idempotency_key),
    CONSTRAINT ck_pendencia_integracao_produto_metodo_http
        CHECK (metodo_http IN ('POST', 'PUT', 'PATCH', 'DELETE'))
);

CREATE INDEX IF NOT EXISTS idx_pendencias_integracao_produto_status_proxima
    ON autenticacao.pendencias_integracao_produto (status_pendencia, proxima_tentativa_em);

CREATE INDEX IF NOT EXISTS idx_pendencias_integracao_produto_cliente
    ON autenticacao.pendencias_integracao_produto (cliente_ecossistema_id);

CREATE INDEX IF NOT EXISTS idx_pendencias_integracao_produto_cadastro
    ON autenticacao.pendencias_integracao_produto (cadastro_id);
```

### Leitura pratica dessa DDL

- `cliente_ecossistema_id` e melhor do que um texto solto de produto porque o
  ecossistema ja possui catalogo normalizado;
- `payload_json` fica flexivel para cada endpoint interno;
- `idempotency_key` fica unica por cliente para impedir duplicidade;
- `status_pendencia + proxima_tentativa_em` ajuda a fila a achar o que esta
  pronto para processar;
- `processando_por_instancia` e `processando_desde` ajudam no controle quando
  houver mais de uma replica da `autenticacao`.

## 14.2 Tabela recomendada para parametros globais do scheduler

Como o comportamento da fila precisa ser ajustavel sem novo deploy, a
recomendacao e ter uma tabela pequena de parametros globais.

Tabela sugerida:

- `autenticacao.parametros_scheduler_integracao_produto`

```sql
CREATE TABLE IF NOT EXISTS autenticacao.parametros_scheduler_integracao_produto (
    id smallint PRIMARY KEY DEFAULT 1,
    habilitado boolean NOT NULL DEFAULT true,
    tempo_entre_tentativas_segundos integer NOT NULL,
    quantidade_maxima_tentativas integer NOT NULL,
    quantidade_maxima_itens_por_ciclo integer NOT NULL,
    timeout_sondagem_millis integer NOT NULL,
    timeout_entrega_millis integer NOT NULL,
    criado_em timestamptz NOT NULL,
    atualizado_em timestamptz NOT NULL,
    CONSTRAINT ck_parametros_scheduler_integracao_produto_singleton
        CHECK (id = 1)
);
```

### Por que uma tabela singleton aqui

Porque estes valores sao globais da rotina:

- intervalo base;
- limite de tentativas;
- tamanho de lote;
- timeouts.

Como sao poucos e a leitura e simples, uma linha unica evita modelagem
desnecessaria.

## 14.2.1 Seed inicial recomendado para os parametros globais

Exemplo de carga inicial:

```sql
INSERT INTO autenticacao.parametros_scheduler_integracao_produto (
    id,
    habilitado,
    tempo_entre_tentativas_segundos,
    quantidade_maxima_tentativas,
    quantidade_maxima_itens_por_ciclo,
    timeout_sondagem_millis,
    timeout_entrega_millis,
    criado_em,
    atualizado_em
) VALUES (
    1,
    true,
    300,
    10,
    50,
    3000,
    10000,
    NOW(),
    NOW()
)
ON CONFLICT (id) DO UPDATE
SET habilitado = EXCLUDED.habilitado,
    tempo_entre_tentativas_segundos = EXCLUDED.tempo_entre_tentativas_segundos,
    quantidade_maxima_tentativas = EXCLUDED.quantidade_maxima_tentativas,
    quantidade_maxima_itens_por_ciclo = EXCLUDED.quantidade_maxima_itens_por_ciclo,
    timeout_sondagem_millis = EXCLUDED.timeout_sondagem_millis,
    timeout_entrega_millis = EXCLUDED.timeout_entrega_millis,
    atualizado_em = NOW();
```

Leitura recomendada destes valores iniciais:

- `300` segundos = 5 minutos entre novas tentativas automaticas;
- `10` tentativas = limite automatico antes de escalar para operacao;
- `50` itens por ciclo = lote moderado, sem gerar pico artificial;
- `3000` ms = 3 segundos para a sondagem operacional;
- `10000` ms = 10 segundos para a entrega real.

## 14.3 Tabela recomendada para controle operacional por produto

Para manutencao programada, pausa de escritas internas e override por cliente,
faz mais sentido uma tabela por produto.

Tabela sugerida:

- `autenticacao.controles_integracao_produto`

```sql
CREATE TABLE IF NOT EXISTS autenticacao.controles_integracao_produto (
    id bigserial PRIMARY KEY,
    cliente_ecossistema_id bigint NOT NULL
        REFERENCES catalogo.clientes_ecossistema (id),
    escritas_internas_habilitadas boolean NOT NULL DEFAULT true,
    produto_em_manutencao boolean NOT NULL DEFAULT false,
    inicio_manutencao timestamptz,
    fim_manutencao timestamptz,
    motivo_manutencao varchar(512),
    tempo_entre_tentativas_segundos_override integer,
    quantidade_maxima_tentativas_override integer,
    timeout_sondagem_millis_override integer,
    timeout_entrega_millis_override integer,
    criado_em timestamptz NOT NULL,
    atualizado_em timestamptz NOT NULL,
    CONSTRAINT uq_controles_integracao_produto_cliente
        UNIQUE (cliente_ecossistema_id)
);
```

### Leitura pratica dessa segunda tabela

Ela existe para responder perguntas como:

- este produto pode receber escritas internas agora?
- este produto esta em manutencao conhecida?
- este produto precisa de timeout diferente do padrao?
- este produto precisa de menos ou mais tentativas que o padrao global?

## 14.3.1 Seed inicial recomendado para um produto sem manutencao

Exemplo conceitual:

```sql
INSERT INTO autenticacao.controles_integracao_produto (
    cliente_ecossistema_id,
    escritas_internas_habilitadas,
    produto_em_manutencao,
    inicio_manutencao,
    fim_manutencao,
    motivo_manutencao,
    tempo_entre_tentativas_segundos_override,
    quantidade_maxima_tentativas_override,
    timeout_sondagem_millis_override,
    timeout_entrega_millis_override,
    criado_em,
    atualizado_em
) VALUES (
    1,
    true,
    false,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NOW(),
    NOW()
)
ON CONFLICT (cliente_ecossistema_id) DO NOTHING;
```

Exemplo conceitual de manutencao programada:

```sql
UPDATE autenticacao.controles_integracao_produto
SET produto_em_manutencao = true,
    inicio_manutencao = TIMESTAMPTZ '2026-05-03 22:00:00+00',
    fim_manutencao = TIMESTAMPTZ '2026-05-04 01:00:00+00',
    motivo_manutencao = 'Atualizacao assistida do backend do produto',
    atualizado_em = NOW()
WHERE cliente_ecossistema_id = 1;
```

## 14.4 Ordem de leitura recomendada dos parametros

Sugestao de prioridade:

1. ler o controle por produto;
2. se houver override no produto, usar override;
3. se nao houver override, usar parametro global do scheduler;
4. se nao houver linha no banco, usar `application.yml` como fallback tecnico.

## 14.4.1 Chaves recomendadas no `application.yml`

O banco deve prevalecer.

Mesmo assim, a recomendacao e manter um fallback tecnico explicito no
`application.yml`, por exemplo:

```yaml
eickrono:
  integracao-produto:
    scheduler:
      habilitado: true
      tempo-entre-tentativas-segundos: 300
      quantidade-maxima-tentativas: 10
      quantidade-maxima-itens-por-ciclo: 50
      timeout-sondagem-millis: 3000
      timeout-entrega-millis: 10000
      timeout-recuperacao-processamento-segundos: 900
```

Leitura recomendada:

- `timeout-recuperacao-processamento-segundos` serve para destravar item que
  ficou `EM_PROCESSAMENTO` depois de crash da instancia;
- o valor sugerido `900` significa 15 minutos.

## 14.5 Exemplo de linha de pendencia

Exemplo conceitual:

```json
{
  "id": "3d4c0e71-5d8d-4f6f-b17a-f7c39a8d1b5e",
  "clienteEcossistemaId": 1,
  "tipoOperacao": "CRIAR_PERFIL_SISTEMA",
  "uriEndpoint": "/api/interna/perfis-sistema/provisionamentos",
  "metodoHttp": "POST",
  "payloadJson": {
    "cadastroId": "cb7f0d2d-0d8a-4188-a5ea-2f38d12f933d",
    "pessoaIdCentral": "c782b36a-6840-4b66-b7d8-1fd0d8669d4d",
    "identificadorPublicoSistema": "joao123",
    "nomePessoaAtual": "Joao Silva",
    "emailPessoaAtual": "joao@exemplo.com"
  },
  "idempotencyKey": "cadastro-cb7f0d2d-0d8a-4188-a5ea-2f38d12f933d-thimisu-criar-perfil",
  "versaoContrato": "v1",
  "statusPendencia": "AGUARDANDO_NOVA_TENTATIVA",
  "tentativasRealizadas": 2
}
```

## 14.6 Exemplo de leitura operacional da manutencao

Exemplo:

- `cliente_ecossistema_id = 1`
- `produto_em_manutencao = true`
- `inicio_manutencao = 2026-05-03T22:00:00Z`
- `fim_manutencao = 2026-05-04T01:00:00Z`
- `motivo_manutencao = Atualizacao assistida do backend do produto`

Enquanto esse intervalo estiver vigente:

- a fila nao tenta novas escritas;
- as pendencias daquele produto ficam pausadas;
- o scheduler segue trabalhando para outros produtos.

### Leitura correta da fila

Esta tabela deve funcionar como fila de trabalho ativa.

Entao:

- item concluido com sucesso sai da fila;
- item ainda pendente continua na fila;
- item esgotado continua na fila para operacao;
- a fila nao precisa manter linha de "concluido com sucesso" para sempre.

## 14.7 Consultas SQL recomendadas para o ciclo da fila

Esta secao traduz o comportamento funcional em consultas concretas de banco.

### 14.7.1 Buscar parametros globais

```sql
SELECT
    habilitado,
    tempo_entre_tentativas_segundos,
    quantidade_maxima_tentativas,
    quantidade_maxima_itens_por_ciclo,
    timeout_sondagem_millis,
    timeout_entrega_millis
FROM autenticacao.parametros_scheduler_integracao_produto
WHERE id = 1;
```

### 14.7.2 Buscar controle de um produto

```sql
SELECT
    cliente_ecossistema_id,
    escritas_internas_habilitadas,
    produto_em_manutencao,
    inicio_manutencao,
    fim_manutencao,
    motivo_manutencao,
    tempo_entre_tentativas_segundos_override,
    quantidade_maxima_tentativas_override,
    timeout_sondagem_millis_override,
    timeout_entrega_millis_override
FROM autenticacao.controles_integracao_produto
WHERE cliente_ecossistema_id = :clienteEcossistemaId;
```

### 14.7.3 Reservar lote para processamento com concorrencia segura

Exemplo usando `FOR UPDATE SKIP LOCKED`:

```sql
WITH lote AS (
    SELECT p.id
    FROM autenticacao.pendencias_integracao_produto p
    WHERE p.status_pendencia IN ('PENDENTE_ENVIO', 'AGUARDANDO_NOVA_TENTATIVA')
      AND p.proxima_tentativa_em <= NOW()
    ORDER BY p.proxima_tentativa_em ASC, p.criado_em ASC
    FOR UPDATE SKIP LOCKED
    LIMIT :quantidadeMaximaItensPorCiclo
)
UPDATE autenticacao.pendencias_integracao_produto p
SET status_pendencia = 'EM_PROCESSAMENTO',
    processando_por_instancia = :nomeInstancia,
    processando_desde = NOW(),
    atualizado_em = NOW()
FROM lote
WHERE p.id = lote.id
RETURNING
    p.id,
    p.cliente_ecossistema_id,
    p.tipo_operacao,
    p.uri_endpoint,
    p.metodo_http,
    p.payload_json,
    p.idempotency_key,
    p.versao_contrato,
    p.cadastro_id,
    p.pessoa_id_central,
    p.perfil_sistema_id,
    p.identificador_publico_sistema,
    p.tentativas_realizadas;
```

### 14.7.4 Reagendar item apos falha

```sql
UPDATE autenticacao.pendencias_integracao_produto
SET status_pendencia = 'AGUARDANDO_NOVA_TENTATIVA',
    tentativas_realizadas = tentativas_realizadas + 1,
    ultima_tentativa_em = NOW(),
    proxima_tentativa_em = NOW() + make_interval(secs => :tempoEntreTentativasSegundos),
    codigo_ultimo_erro = :codigoUltimoErro,
    mensagem_ultimo_erro = :mensagemUltimoErro,
    processando_por_instancia = NULL,
    processando_desde = NULL,
    atualizado_em = NOW()
WHERE id = :pendenciaId;
```

### 14.7.5 Marcar manutencao conhecida

```sql
UPDATE autenticacao.pendencias_integracao_produto
SET status_pendencia = 'PAUSADO_MANUTENCAO',
    codigo_ultimo_erro = 'PRODUTO_EM_MANUTENCAO',
    mensagem_ultimo_erro = :motivoManutencao,
    processando_por_instancia = NULL,
    processando_desde = NULL,
    atualizado_em = NOW()
WHERE id = :pendenciaId;
```

### 14.7.6 Escalar para operacao

```sql
UPDATE autenticacao.pendencias_integracao_produto
SET status_pendencia = 'FALHA_ESCALADA',
    tentativas_realizadas = tentativas_realizadas + 1,
    ultima_tentativa_em = NOW(),
    codigo_ultimo_erro = :codigoUltimoErro,
    mensagem_ultimo_erro = :mensagemUltimoErro,
    processando_por_instancia = NULL,
    processando_desde = NULL,
    atualizado_em = NOW()
WHERE id = :pendenciaId;
```

### 14.7.7 Remover item concluido com sucesso

```sql
DELETE FROM autenticacao.pendencias_integracao_produto
WHERE id = :pendenciaId;
```

### 14.7.8 Recuperar item preso em processamento por crash

```sql
UPDATE autenticacao.pendencias_integracao_produto
SET status_pendencia = 'AGUARDANDO_NOVA_TENTATIVA',
    processando_por_instancia = NULL,
    processando_desde = NULL,
    codigo_ultimo_erro = 'PROCESSAMENTO_ABANDONADO',
    mensagem_ultimo_erro = 'Item recuperado apos timeout de processamento',
    proxima_tentativa_em = NOW(),
    atualizado_em = NOW()
WHERE status_pendencia = 'EM_PROCESSAMENTO'
  AND processando_desde < NOW() - make_interval(secs => :timeoutRecuperacaoProcessamentoSegundos);
```

## 14.8 Regras de preenchimento das colunas da fila

### `tipo_operacao`

Valores iniciais recomendados:

- `CRIAR_PERFIL_SISTEMA`
- `ATUALIZAR_PERFIL_SISTEMA`
- `BLOQUEAR_PERFIL_SISTEMA`
- `REATIVAR_PERFIL_SISTEMA`
- `REVOGAR_PERFIL_SISTEMA`
- `APAGAR_PERFIL_SISTEMA`

### `versao_contrato`

Recomendacao:

- guardar valor curto e estavel, como `v1`, `v2`;
- mudar esse valor quando o formato do `payload_json` mudar de forma
  incompativel.

### `idempotency_key`

Recomendacao:

- gerar chave deterministica por operacao de negocio;
- incluir pelo menos:
  - o cliente do ecossistema;
  - o tipo de operacao;
  - a entidade principal;
  - o identificador do fluxo, quando existir.

Exemplo:

- `cliente-1-criar-perfil-cadastro-cb7f0d2d`
- `cliente-1-atualizar-perfil-pessoa-c782b36a`

### `payload_json`

Recomendacao:

- guardar exatamente o corpo da chamada interna ao produto;
- nao guardar cabecalhos de autenticacao;
- nao guardar token efemero;
- nao guardar segredo.

### `codigo_ultimo_erro`

Valores iniciais recomendados:

- `PRODUTO_EM_MANUTENCAO`
- `SONDAGEM_FALHOU`
- `TIMEOUT_ENTREGA`
- `HTTP_4XX`
- `HTTP_5XX`
- `PROCESSAMENTO_ABANDONADO`
- `ERRO_DESCONHECIDO`

## 14.9 Sequencia recomendada de implementacao da migration

Ordem tecnica sugerida:

1. criar `autenticacao.pendencias_integracao_produto`;
2. criar indices da fila;
3. criar `autenticacao.parametros_scheduler_integracao_produto`;
4. inserir seed inicial global;
5. criar `autenticacao.controles_integracao_produto`;
6. inserir seed inicial dos produtos ja ativos no catalogo;
7. so depois ligar o novo `scheduler` em ambiente local.

## 14.10 Definicao de pronto desta parte de banco

Esta parte do trabalho pode ser considerada pronta quando:

- as tres tabelas existirem por migration versionada;
- o banco local subir limpo com essas migrations;
- existir seed inicial de parametro global;
- existir ao menos um seed de controle por produto real do catalogo;
- a aplicacao conseguir ler os parametros do banco com fallback tecnico no
  `application.yml`;
- e a reserva de lote com `FOR UPDATE SKIP LOCKED` estiver coberta por teste
  de integracao.

## 14.11 Plano TDD curto da primeira entrega

Primeira rodada recomendada:

1. escrever teste da leitura de parametros com prioridade:
   - banco em primeiro lugar;
   - fallback de `application.yml` em segundo lugar;
2. escrever teste do `scheduler` garantindo:
   - que ele respeita o flag `habilitado`;
   - que ele delega o ciclo ao servico central;
3. escrever teste do servico da fila garantindo:
   - recuperacao de item abandonado por crash;
   - contagem de pendencias prontas para processamento;
4. so depois escrever a migration `V23` e o esqueleto de codigo.

Definicao de pronto da primeira entrega:

- migration criada;
- properties de fallback criadas;
- repositorio JDBC inicial criado;
- servico inicial criado;
- `scheduler` criado;
- testes unitarios dessa primeira rodada passando.

## 15. Status recomendados para a pendencia

Sugestao de estados:

- `PENDENTE_ENVIO`
- `EM_PROCESSAMENTO`
- `AGUARDANDO_NOVA_TENTATIVA`
- `PAUSADO_MANUTENCAO`
- `FALHA_ESCALADA`
- `CANCELADO`

### Leitura em linguagem simples

- `PENDENTE_ENVIO`
  a entrega foi criada, mas ainda nao concluiu;
- `EM_PROCESSAMENTO`
  alguma instancia esta tentando entregar agora;
- `AGUARDANDO_NOVA_TENTATIVA`
  houve falha e ja existe proxima tentativa agendada;
- `PAUSADO_MANUTENCAO`
  a fila sabe que aquele produto esta em manutencao e nao deve insistir agora;
- `FALHA_ESCALADA`
  o limite automatico acabou e operacao precisa olhar;
- `CANCELADO`
  a pendencia deixou de fazer sentido por decisao funcional.

## 16. Como o scheduler executa a fila

Fluxo recomendado por ciclo:

1. ler parametros operacionais;
2. buscar pendencias ativas;
3. filtrar apenas as que estiverem aptas naquele momento;
4. assumir um lote pequeno com controle de concorrencia;
5. para cada item:
   - montar credenciais internas;
   - verificar se o produto esta em manutencao conhecida;
   - se estiver em manutencao, remarcar sem tentar entrega;
   - fazer sondagem operacional;
   - se a sondagem falhar, reagendar;
   - se passar, fazer a chamada real;
   - se der certo, remover da fila;
   - se falhar, reagendar ou escalar;
6. liberar o lote e seguir para o proximo ciclo.

### Tamanho do lote

Recomendacao:

- nao tentar tudo de uma vez;
- usar lote pequeno e configuravel.

Motivo:

- evita pico artificial no backend do produto;
- facilita retomada depois de indisponibilidade;
- diminui risco de um unico ciclo travar o sistema.

## 17. Forma de comunicacao com os projetos

### 17.1 Comunicacao com identidade

Para esta especificacao, a comunicacao com `identidade` continua sincronizada e
fora da fila de produto.

Direcao:

- `autenticacao -> identidade`

Uso:

- criar ou atualizar `Pessoa` canonica;
- consultar contexto canonico quando o fluxo exigir.

### 17.2 Comunicacao com o backend do produto

Direcao:

- `autenticacao -> backend do produto`

Uso:

- criar perfil do sistema;
- atualizar perfil do sistema;
- apagar, bloquear ou revogar algo no produto.

Esta e a comunicacao que entra na fila de pendencias desta especificacao.

### 17.3 Comunicacao do produto com autenticacao

Direcao:

- `backend do produto -> autenticacao`

Uso:

- consultar disponibilidade de `usuario + sistema`;
- eventualmente buscar contexto ou validar alguma operacao interna.

Essa chamada existe por `backchannel`, mas nao faz parte do `scheduler` de
entrega ao produto.

## 18. Seguranca da comunicacao

Para a entrega ao produto, a recomendacao continua sendo a mesma do restante do
ecossistema:

- `mTLS` no transporte;
- `JWT` interno na aplicacao;
- `X-Eickrono-Internal-Secret` como barreira adicional;
- `X-Eickrono-Calling-System` quando o contrato interno pedir identificacao do
  chamador;
- `idempotencyKey` no contexto da operacao.

### O que o scheduler nao deve fazer

Ele nao deve:

- inventar canal novo fora do `backchannel`;
- escrever direto no banco do produto;
- reaproveitar token velho persistido em banco;
- burlar as mesmas camadas de seguranca usadas pelas chamadas internas
  normais.

## 19. Catalogo de cenarios funcionais e tecnicos

Leitura correta desta secao:

- ela lista os cenarios relevantes do desenho atual;
- ela nao tenta enumerar "todo erro imaginavel de rede do universo";
- ela cobre os casos que a arquitetura precisa tratar de forma consciente;
- ela tambem registra o que ja foi testado e o que ainda precisa de teste
  integrado em `dev`.

Legenda de cobertura usada abaixo:

- `Automatizado`: ja existe teste automatizado no codigo;
- `Integrado dev`: ja foi validado em ambiente local com servicos de pe;
- `Pendente`: ainda nao foi exercitado fim a fim em `dev`.

### 19.1 Matriz resumida dos casos

| ID | Situacao | Resultado esperado | Efeito na fila | Login central | Cobertura atual |
| --- | --- | --- | --- | --- | --- |
| 1 | Cadastro central conclui e produto responde normalmente | perfil do sistema criado no ato | nao cria pendencia ou remove logo em seguida | liberado | Automatizado |
| 2 | Produto esta fora do ar antes da chamada real | cria pendencia e registra indisponibilidade | fica em `AGUARDANDO_NOVA_TENTATIVA` | liberado | Automatizado + Integrado dev |
| 3 | Produto responde na sondagem, mas falha na chamada real com erro toleravel `5xx` | mantem parte central valida e agenda nova tentativa | reaproveita o mesmo registro e incrementa tentativas | liberado | Automatizado |
| 4 | Produto processa, mas a resposta se perde | nova tentativa nao pode duplicar efeito | registro pode permanecer ate sucesso confirmado | liberado | Automatizado por regra de `idempotencia`, sem teste integrado especifico |
| 5 | Falha de rede, `timeout` (tempo maximo de espera) ou erro TLS na entrega | trata como falha toleravel e tenta depois | reaproveita o mesmo registro | liberado | Automatizado parcial |
| 6 | Erro funcional do produto `4xx` que nao deve ser tolerado | nao trata como fila automatica eterna | pode falhar de forma sincrona ou escalar conforme regra do fluxo | depende do caso | Pendente |
| 7 | Alteracao futura no produto com produto fora do ar | registra alteracao pendente | fica na fila ate nova tentativa ou escalada | nao se aplica diretamente ao login | Pendente |
| 8 | Operacao de apagar ou revogar algo no produto | tambem entra na fila com `idempotencia` | fila trata como qualquer outra operacao | nao se aplica diretamente ao login | Pendente |
| 9 | Produto em manutencao programada | fila nao insiste inutilmente | marca pausa ou posterga tentativa | liberado quando o caso nao depender do produto na hora | Automatizado |
| 10 | Muitas falhas seguidas antes do limite | mesmo registro continua vivo | incrementa `tentativas_realizadas` | liberado | Pendente em `dev` |
| 11 | Limite maximo de tentativas atingido | sai do automatico e vira caso operacional | fica marcado para escalada operacional | liberado, salvo regra especifica do produto | Automatizado |
| 12 | Instancia cai com item em `EM_PROCESSAMENTO` | outra execucao futura precisa recuperar o item | volta para fila processavel | liberado | Estrutura implementada, teste integrado pendente |
| 13 | Mais de uma instancia da `autenticacao` roda ao mesmo tempo | evitar pegar a mesma linha duas vezes | reserva com `FOR UPDATE SKIP LOCKED` | liberado | Estrutura implementada, teste integrado pendente |
| 14 | Fila desligada por parametro operacional | nao processa pendencias | itens permanecem aguardando | liberado | Automatizado |
| 15 | Login central com produto ainda pendente | autenticacao central segue funcionando | fila continua em paralelo | liberado | Automatizado + Integrado dev |

### 19.2 Descricao detalhada dos casos

#### Cenario 1. Cadastro concluido na parte central e produto disponivel

Resultado esperado:

- conta central conclui;
- `Pessoa` canonica conclui;
- produto responde;
- perfil do sistema e criado;
- sem pendencia residual na fila.

#### Cenario 2. Produto cai antes da chamada real

Resultado esperado:

- a `autenticacao` detecta isso pela sondagem operacional;
- a entrega fica registrada;
- o cadastro central continua valido;
- o login central pode seguir normal quando a conta central e a `Pessoa`
  estiverem prontas;
- o erro aparece apenas quando algum app realmente precisar usar o backend do
  produto e ele ainda estiver indisponivel.

Este foi testado fim a fim em `dev`.

#### Cenario 3. Produto responde na sondagem, mas falha na chamada real

Resultado esperado:

- o sistema registra a falha;
- a pendencia continua viva;
- o `scheduler` faz nova tentativa automatica;
- o login central continua podendo acontecer;
- aviso especial ao usuario so faz sentido se a fila atrasar demais.

#### Cenario 4. Produto processa a requisicao, mas a resposta se perde

Resultado esperado:

- a nova tentativa pode acontecer;
- o produto precisa tratar isso com `idempotencia`;
- a mesma `idempotencyKey` nao pode gerar duplicidade;
- o item so sai da fila quando houver confirmacao suficiente de sucesso.

#### Cenario 5. Falha de rede, `timeout` ou erro TLS na entrega

Resultado esperado:

- tratar como indisponibilidade toleravel;
- manter o mesmo registro;
- incrementar `tentativas_realizadas`;
- reagendar a proxima tentativa.

#### Cenario 6. Erro funcional `4xx` no produto

Exemplos:

- payload invalido;
- conflito de negocio irrecuperavel;
- contrato interno quebrado;
- regra do produto recusando a operacao por motivo funcional.

Resultado esperado:

- nao ficar tentando infinitamente algo que o produto sempre vai recusar;
- registrar erro claro;
- decidir por falha sincrona imediata ou escalada operacional, conforme a
  natureza do fluxo.

#### Cenario 7. Produto fora do ar para alteracao futura

Exemplos:

- atualizar nome local do produto;
- atualizar email local do produto;
- atualizar telefone local do produto;
- atualizar avatar local do produto.

Resultado esperado:

- registrar a alteracao pendente;
- manter trilha de erro;
- tentar de novo depois;
- decidir na regra do caso se o usuario ve dado antigo localmente ate
  concluir.

#### Cenario 8. Operacao de apagar ou revogar algo no produto

Resultado esperado:

- tambem entra na fila;
- tambem precisa de `idempotencia`;
- tambem precisa de trilha persistida, porque apagar em duplicidade precisa
  ser seguro.

#### Cenario 9. Produto em manutencao programada ou atualizacao assistida

Exemplos:

- a equipe sabe com antecedencia que o backend do produto ficara em manutencao;
- uma atualizacao assistida do produto foi agendada;
- o proprio ecossistema decide pausar temporariamente integracoes de escrita
  naquele produto.

Resultado esperado:

- a `autenticacao` marca aquele produto como temporariamente indisponivel para
  escritas internas;
- enquanto essa marcacao estiver ativa, a fila nao insiste em chamadas
  desnecessarias;
- as pendencias daquele produto ficam em `PAUSADO_MANUTENCAO` ou aguardam a
  janela certa;
- ao terminar a manutencao, a fila volta a processar normalmente.

#### Cenario 10. Muitas falhas seguidas antes do limite

Resultado esperado:

- o item continua sendo o mesmo;
- `tentativas_realizadas` sobe a cada nova tentativa;
- `codigo_ultimo_erro` e `mensagem_ultimo_erro` sao atualizados;
- a fila nao cria duplicatas da mesma pendencia.

#### Cenario 11. Limite maximo de tentativas atingido

Resultado esperado:

- a pendencia nao e apagada;
- ela sai do fluxo automatico;
- ela fica marcada para tratamento operacional;
- a equipe consegue localizar o que faltou entregar.

#### Cenario 12. Instancia cai com item em `EM_PROCESSAMENTO`

Resultado esperado:

- a fila detecta que a linha ficou presa em processamento por tempo demais;
- a linha volta a estado processavel;
- outra execucao futura pode retomar a entrega.

#### Cenario 13. Mais de uma instancia da `autenticacao` executando a fila

Resultado esperado:

- duas instancias nao devem entregar o mesmo item ao mesmo tempo;
- a reserva do lote precisa ser concorrente e segura;
- cada linha deve ser assumida por apenas uma instancia de cada vez.

#### Cenario 14. Fila desligada por parametro operacional

Resultado esperado:

- a rotina agendada pode estar de pe, mas sem processar itens;
- as pendencias permanecem na fila;
- a equipe pode religar depois sem novo deploy.

#### Cenario 15. Login central com produto ainda pendente

Resultado esperado:

- a falta do produto nao bloqueia o login central por padrao;
- a pessoa autentica normalmente;
- o erro aparece so quando o app realmente tentar usar o backend do produto;
- o `statusPerfilSistema` pode continuar pendente enquanto a fila trabalha.

Este foi testado fim a fim em `dev`.

### 19.3 Casos ja validados ate aqui

Ja validados por teste automatizado:

- fila desabilitada por parametro;
- leitura de parametros do banco com `fallback` do `application.yml`;
- remocao do item em caso de sucesso;
- reagendamento em falha de sondagem;
- pausa por manutencao;
- escalada por limite maximo;
- criacao da pendencia no fluxo de cadastro;
- login central com perfil do sistema pendente.

Ja validados por teste integrado em `dev`:

- produto fora do ar durante a confirmacao do cadastro;
- criacao real da linha em `autenticacao.pendencias_integracao_produto`;
- linha ficando em `AGUARDANDO_NOVA_TENTATIVA` com `SONDAGEM_FALHOU`;
- religamento do produto e remocao real da linha da fila;
- criacao real do perfil no banco do produto;
- login central funcionando com o produto desligado.

Ainda pendentes de validacao integrada explicita em `dev`:

- varias falhas seguidas no mesmo item com contador subindo acima de `1`;
- escalada operacional apos atingir o limite;
- pausa de fila por manutencao programada;
- recuperacao de item preso em `EM_PROCESSAMENTO`;
- cenarios de erro funcional `4xx` do produto;
- concorrencia real com mais de uma instancia do `scheduler`.

## 20. O que muda em relacao ao estado atual

### Hoje

Hoje a arquitetura ja tem:

- `backchannel`;
- `mTLS`;
- JWT interno;
- duas rotinas agendadas para outros assuntos;
- provisionamento interno de `Pessoa`;
- provisionamento interno de perfil do sistema.

### O que falta hoje

Hoje ainda falta:

- registrar a entrega ao produto como entidade persistida;
- ter uma fila controlada do que ainda precisa ser reenviado;
- ter uma rotina automatica para reentregar o que falhou;
- saber pausar reentregas quando o produto estiver em manutencao conhecida;
- ter parametros operacionais de tentativas no banco da `autenticacao`.

### Estado alvo desta especificacao

Estado alvo:

- toda entrega relevante ao produto fica rastreada;
- falha operacional nao apaga o que ja foi resolvido no centro;
- a `autenticacao` sabe exatamente o que ainda falta entregar;
- existe nova tentativa automatica;
- existe escalada operacional quando o automatico nao resolver.

## 21. Diferenca entre fila de pendencia e replicacao entre bancos

Esta especificacao nao propoe replicacao automatica entre bases de dados.

A logica correta e:

- o centro decide;
- a `autenticacao` entrega por `backchannel`;
- se falhar, a `autenticacao` tenta de novo depois;
- o produto continua recebendo a operacao pela sua API interna;
- nenhum servico escreve direto no banco do outro.

## 22. Recomendacao de observabilidade

Cada tentativa deve produzir rastros suficientes para operacao.

Campos minimos de log e correlacao:

- `pendenciaId`
- `clienteEcossistemaId`
- `tipoOperacao`
- `cadastroId`, quando existir
- `pessoaIdCentral`, quando existir
- `perfilSistemaId`, quando existir
- `idempotencyKey`
- `tentativasRealizadas`
- `statusPendencia`

Metricas recomendadas:

- quantidade de pendencias abertas;
- quantidade de entregas concluidas com sucesso por janela;
- quantidade de falhas por produto;
- quantidade de pendencias escaladas;
- tempo medio entre criacao da pendencia e conclusao.

## 23. Recomendacao de contrato para a sondagem operacional

O produto precisa oferecer uma forma interna de dizer se esta apto a receber
operacoes de escrita.

Recomendacao:

- nao depender apenas do endpoint publico de health;
- usar uma verificacao interna apropriada para escrita;
- essa verificacao continuar protegida por `backchannel`.

Este documento nao fecha o nome exato do endpoint de sondagem, mas fecha a
regra:

- a `autenticacao` precisa ter uma checagem simples e especifica antes das
  operacoes de create, update e delete no produto.

## 24. Recomendacao de implementacao por etapas

### Etapa A

- criar a tabela de pendencias;
- criar enums de status e tipo de operacao;
- criar repositorio e servico de persistencia.

### Etapa B

- criar o servico de sondagem operacional;
- criar o servico executor da entrega real;
- integrar `idempotencyKey`.

### Etapa C

- criar o `scheduler` (rotina agendada);
- ligar leitura de parametros do banco;
- implementar lote, lock e nova tentativa automatica.

### Etapa D

- plugar a criacao de pendencia no fluxo de provisionamento do produto;
- garantir que o registro nasca antes da entrega real;
- ajustar logs, metricas e rastros operacionais.

### Etapa E

- adicionar comando interno operacional para reprocessamento manual, se a
  equipe quiser esse recurso;
- manter esse comando fora da API publica do app.

## 25. Decisoes que este documento ja considera fechadas

- o `scheduler` deve morar na `autenticacao`;
- ele continua usando `backchannel`;
- `backchannel` continua usando `endpoints` internos;
- a fila cobre operacoes de criar, alterar e apagar no produto;
- o registro persistido deve guardar `uriEndpoint`, `metodoHttp` e
  `payloadJson`;
- a fila funciona como fila ativa de trabalho, nao como historico eterno;
- item entregue com sucesso sai da fila;
- item com falha reutiliza o mesmo registro e incrementa o contador de
  tentativas;
- os intervalos e limites devem ser parametrizaveis no banco da
  `autenticacao`;
- manutencao conhecida do produto deve conseguir pausar tentativas desnecessarias;
- a fila nao significa escrita direta no banco do produto;
- o `scheduler` nao substitui a chamada sincronizada, ele complementa a
  entrega quando houver falha.

## 26. Pontos que ainda podem ser detalhados depois

Este documento deixa alguns pontos para refinamento posterior:

- nome exato da tabela no banco;
- nome exato do endpoint de sondagem operacional;
- politica de mensagens de erro por tipo de jornada;
- regras especificas de escalada operacional por produto;
- necessidade ou nao de comando manual de reprocessamento.

## 27. Relacao com a documentacao existente

Este documento deve ser lido junto de:

1. `consolidado_migracao_autenticacao_identidade_thimisu.md`
2. `guia-arquitetura.md`
3. `guia-mtls.md`
4. `TODO.md`

Leitura correta:

- o consolidado diz por que a fila existe no desenho geral;
- este documento detalha como essa fila deve funcionar;
- o `TODO` so deve voltar a listar essa trilha como agenda depois desta
  especificacao estar aceita.
