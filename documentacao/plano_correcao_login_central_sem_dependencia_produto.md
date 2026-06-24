# Plano de Correcao - Login de Autenticacao/Identidade sem Dependencia do Backend do Produto

## Objetivo

Este documento registra a decisao arquitetural e o passo a passo para corrigir
o acoplamento indevido entre o login resolvido por
`eickrono-autenticacao-servidor`/`eickrono-identidade-servidor` e
o `eickrono-thimisu-backend`.

A regra principal e:

- login, autenticacao social, registro silencioso de dispositivo e emissao de
  sessao de autenticacao devem depender apenas de
  `eickrono-autenticacao-servidor` e `eickrono-identidade-servidor`;
- `eickrono-thimisu-backend` nao pode participar do caminho obrigatorio de
  decisao do login;
- `eickrono-thimisu-backend` deve entrar depois, para provisionamento do perfil
  do produto ou para consumo de uma sessao de autenticacao ja pronta;
- indisponibilidade, `403`, timeout ou erro estrutural no produto nao pode virar
  falha do login resolvido por `eickrono-autenticacao-servidor` e
  `eickrono-identidade-servidor`.

## Problema atual

Foi identificado um caminho em que o login social, depois de autenticar a rede
social, tenta concluir sessao/dispositivo e acaba chamando o backend do produto.

Cadeia observada:

1. o app conclui autenticacao social;
2. o app chama o fluxo de sessao/registro de dispositivo;
3. o `eickrono-identidade-servidor` chega ao endpoint
   `/identidade/dispositivos/registro/silencioso`;
4. durante a resolucao do contexto da pessoa/perfil, o codigo chama
   `ClienteContextoPessoaPerfilSistemaHttp`;
5. esse cliente chama o produto em
   `GET /api/interna/perfis-sistema/contexto`;
6. se o produto recusa a chamada, por exemplo por cliente interno nao
   permitido, o produto retorna `403`;
7. esse erro interno sobe como falha do `eickrono-identidade-servidor` e aparece
   no app como `500` em `/identidade/dispositivos/registro/silencioso`.

O erro imediato visto em STG foi:

```text
GET /api/interna/perfis-sistema/contexto
thimisu-backend -> 403
/identidade/dispositivos/registro/silencioso -> 500
```

Isso esta errado porque a existencia, liberacao, vinculo social, usuario e
sessao de autenticacao pertencem a `eickrono-autenticacao-servidor` e
`eickrono-identidade-servidor`, nao ao produto.

## Evidencias nos documentos existentes

Os documentos atuais ja apontam para esse desenho:

- `eickrono-thimisu-backend/docs/fluxo_cadastro_login_nativo.md`
  - o `thimisu-backend` recebe apenas provisionamento interno;
  - o produto nao coordena dispositivo;
  - o produto nao decide onboarding ou autenticacao feita por
    `eickrono-autenticacao-servidor`/`eickrono-identidade-servidor`;
  - se o produto falhar apos a conta no `eickrono-autenticacao-servidor` e a
    pessoa no `eickrono-identidade-servidor` estarem prontas, a pendencia deve
    ficar operacional e o login de autenticacao/identidade pode continuar.
- `eickrono-autenticacao-servidor/documentacao/guia-arquitetura.md`
  - o backend do produto recebe apenas dados necessarios para criar o perfil do
    produto;
  - o produto nao e borda publica de login, senha ou codigo;
  - o provisionamento do produto ocorre depois da confirmacao feita por
    `eickrono-autenticacao-servidor`/`eickrono-identidade-servidor`.
- `eickrono-autenticacao-servidor/documentacao/consolidado_migracao_autenticacao_identidade_thimisu.md`
  - o alvo e `autenticacao -> identidade` para dados centrais;
  - `autenticacao -> backend do produto` e apenas provisionamento;
  - contexto canonico de pessoa exposto pelo produto e problema legado;
  - o alvo correto para contexto canonico e leitura interna no
    `eickrono-identidade-servidor`.

Conclusao:

- nao ha requisito aprovado para
  `identidade-servidor -> thimisu-backend` no momento de autenticar ou registrar
  dispositivo;
- esse acoplamento veio da implementacao, nao da regra funcional.

## Arquitetura correta

### Caminho permitido para login/autenticacao

```text
app
  -> eickrono-autenticacao-servidor
  -> eickrono-identidade-servidor
  -> Keycloak, quando necessario para identidade/autenticacao
  -> resposta de autenticacao ao app
```

Esse caminho resolve:

- conta local;
- pessoa;
- usuario canonico;
- e-mail principal;
- vinculos sociais;
- status de liberacao da conta/perfil nos projetos de autenticacao e identidade;
- registro silencioso de dispositivo;
- token/sessao de autenticacao.

### Caminho do produto permitido

```text
eickrono-autenticacao-servidor
  -> eickrono-thimisu-backend
```

Somente depois de:

- conta criada ou encontrada no `eickrono-autenticacao-servidor`;
- pessoa criada ou encontrada no `eickrono-identidade-servidor`;
- usuario canonico resolvido;
- e-mail/contatos na situacao esperada para o fluxo;
- sessao de autenticacao ou operacao de provisionamento validada pelos projetos
  de autenticacao/identidade.

Uso permitido do produto:

- provisionar perfil do sistema;
- atualizar copia local minima do produto;
- registrar pendencia de integracao se o produto estiver indisponivel;
- responder chamadas do app ja autenticado que realmente dependem do produto.

Uso proibido do produto:

- decidir se login de autenticacao/identidade pode continuar;
- decidir se registro silencioso de dispositivo pode concluir;
- ser fonte primaria de `Pessoa`;
- ser fonte primaria de `usuario`;
- bloquear autenticacao social de `eickrono-autenticacao-servidor` ou
  `eickrono-identidade-servidor` por erro local do produto.

## Ponto tecnico de corte

### `eickrono-identidade-servidor`

Ponto atual a corrigir:

- `ClienteContextoPessoaPerfilSistemaHttp`
  - hoje chama `GET /api/interna/perfis-sistema/contexto`;
  - esse endpoint pertence ao `eickrono-thimisu-backend`;
  - esse cliente nao deve ser dependencia do fluxo de login/registro de
    dispositivo do `eickrono-identidade-servidor`.

Locais que precisam ser analisados antes da alteracao:

- `RegistroDispositivoController`;
- `RegistroDispositivoService`;
- `FluxoPublicoController`;
- `CadastroContaInternaServico`;
- `ConviteOrganizacionalService`;
- `VinculoOrganizacionalService`;
- implementacoes de `ClienteContextoPessoaPerfilSistema`;
- testes que mockam `clienteContextoPessoaPerfilSistema`.

Correção esperada:

- criar ou substituir a implementacao usada pelo fluxo de
  `eickrono-identidade-servidor` por um resolvedor interno do proprio
  `eickrono-identidade-servidor`;
- esse resolvedor deve consultar as tabelas/repositorios do
  `eickrono-identidade-servidor`;
- o contrato `ContextoPessoaPerfilSistema` pode continuar existindo, mas sua
  fonte nao deve ser o produto para login de autenticacao/identidade.

Dados minimos que o resolvedor interno do `eickrono-identidade-servidor` deve
devolver:

- `pessoaId`;
- `sub`;
- `emailPrincipal`;
- `nome`;
- `usuario`;
- `perfilSistemaId`, se existir como dado do perfil/sistema no
  `eickrono-identidade-servidor`;
- `statusPerfilSistema` ou status equivalente usado por
  `eickrono-identidade-servidor` para liberar ou bloquear o login.

### `eickrono-autenticacao-servidor`

Ponto atual a corrigir:

- `ResolvedorContextoAutenticacaoService.buscarPorEmailPublicoPreferindoProduto`
  ainda tenta buscar primeiro no produto;
- essa preferencia pelo produto contraria o desenho alvo;
- em login de autenticacao/identidade, a busca deve usar
  `eickrono-autenticacao-servidor` e `eickrono-identidade-servidor`.

Correção esperada:

- remover o produto da resolucao obrigatoria de contexto de login;
- fazer `buscarPorEmailPublicoPreferindoProduto` deixar de preferir produto, ou
  substituir seu uso por metodo explicito de autenticacao/identidade;
- manter chamadas ao produto apenas na trilha de provisionamento.

### `eickrono-thimisu-backend`

Ponto importante:

- nao corrigir o problema liberando permissao ampla no produto;
- adicionar `eickrono-identidade-servidor` na lista permitida do produto pode
  mascarar o erro, mas nao corrige a arquitetura;
- o endpoint `/api/interna/perfis-sistema/contexto` pode continuar existindo
  para uso do produto ou compatibilidade temporaria, mas nao deve estar no
  caminho obrigatorio do login de autenticacao/identidade.

### Tratamento futuro do endpoint legado do produto

O endpoint do `eickrono-thimisu-backend`
`GET /api/interna/perfis-sistema/contexto` pode induzir erro de arquitetura se
ficar sem classificacao clara, porque seu nome sugere que ele pode resolver
contexto de pessoa/perfil para qualquer fluxo interno.

Regra enquanto o endpoint existir:

- ele nao e fonte canonica de login;
- ele nao e fonte canonica de autenticacao social;
- ele nao e fonte canonica de sessao;
- ele nao e fonte canonica de registro silencioso de dispositivo;
- ele nao deve ser chamado por `eickrono-identidade-servidor` para decidir se
  uma pessoa pode autenticar;
- ele nao deve ser chamado por `eickrono-autenticacao-servidor` para decidir se
  uma sessao pode ser emitida;
- ele so pode ser usado por fluxos de produto, compatibilidade temporaria ou
  consultas internas que ja partem de uma sessao/autorizacao resolvida pelos
  projetos de autenticacao/identidade.

Como corrigir definitivamente em uma etapa futura:

1. Inventariar consumidores reais.
   - buscar no codigo por `/api/interna/perfis-sistema/contexto`;
   - buscar por `ClienteContextoPessoaPerfilSistemaHttp`;
   - buscar em task definitions, variaveis, documentacao operacional e testes;
   - classificar cada consumidor como:
     - login/autenticacao/sessao/dispositivo;
     - provisionamento;
     - consulta autenticada do produto;
     - compatibilidade legada;
     - teste.
2. Remover consumidores proibidos.
   - qualquer consumidor classificado como login, autenticacao, sessao ou
     registro silencioso deve ser migrado para leitura em
     `eickrono-autenticacao-servidor`/`eickrono-identidade-servidor`;
   - nao corrigir esse caso liberando permissao no `thimisu-backend`.
3. Renomear ou duplicar o endpoint antes de remover o antigo.
   - criar um endpoint com nome explicito de produto, por exemplo:
     `/api/interna/produto/perfis-sistema/contexto`;
   - manter o endpoint antigo apenas como compatibilidade temporaria;
   - marcar o endpoint antigo como depreciado na documentacao e nos testes.
4. Restringir acesso do endpoint antigo.
   - bloquear chamadas vindas de clientes de autenticacao/identidade quando o
     uso for login, sessao ou dispositivo;
   - permitir apenas consumidores explicitamente classificados como produto ou
     compatibilidade temporaria;
   - registrar log de aviso quando o endpoint antigo for chamado.
5. Criar testes de protecao.
   - teste no `eickrono-thimisu-backend` garantindo que cliente de
     autenticacao/identidade nao usa esse endpoint como decisor de login;
   - teste nos projetos de autenticacao/identidade garantindo que os fluxos de
     login, login social, renovacao de sessao e registro silencioso passam sem
     `thimisu-backend`;
   - teste de contrato para o endpoint novo, limitado a uso de produto.
6. Remover o endpoint antigo.
   - so depois de nenhum consumidor real depender dele;
   - atualizar documentacao;
   - remover testes de compatibilidade antiga;
   - confirmar em STG que os logs nao registram chamadas ao endpoint antigo.

Critério para iniciar essa etapa futura:

- existir um escopo explicito para migrar ou remover o endpoint legado do
  `eickrono-thimisu-backend`;
- nao misturar essa migracao com correcao de login, cadastro, biometria ou app;
- antes de alterar codigo, listar os consumidores encontrados e decidir o
  destino de cada um.

## Plano de implementacao

## Status da implementacao em 2026-05-15

Alteracoes aplicadas:

- `eickrono-identidade-servidor`
  - criado resolvedor local `ClienteContextoPessoaPerfilSistemaLocal`;
  - o resolvedor local consulta `cadastros_conta` pelo proprio
    `eickrono-identidade-servidor`;
  - contexto valido exige cadastro local confirmado, `pessoaIdPerfil`,
    `usuario` e `emailPrincipal`;
  - `ClienteContextoPessoaPerfilSistemaHttp` deixou de ser bean Spring
    automatico, portanto nao participa mais do caminho obrigatorio de login,
    sessao ou registro silencioso;
  - foram adicionadas consultas por `pessoaIdPerfil` e por `usuario` no
    repositorio de cadastro.
- `eickrono-autenticacao-servidor`
  - `ResolvedorContextoAutenticacaoService` deixou de receber
    `ClienteContextoPessoaPerfilSistema`;
  - resolucao publica por e-mail passa a usar apenas
    `CadastroContaInternaServico.buscarContextoCentralPorEmailPublico`;
  - resolucao publica por `sub` passa a usar apenas
    `CadastroContaInternaServico.buscarContextoCentralPorSubPublico`;
  - removido o metodo com nome antigo
    `buscarPorEmailPublicoPreferindoProduto`, porque o nome passou a contradizer
    a regra corrigida;
  - `FluxoPublicoController` usa o resolvedor por e-mail sem referencia a
    produto;
  - teste novo garante que a busca por `sub` nao chama produto.

Comportamento esperado depois da implementacao:

- login por senha nao depende do `eickrono-thimisu-backend`;
- login social nao depende do `eickrono-thimisu-backend`;
- registro silencioso de dispositivo no `eickrono-identidade-servidor` nao
  depende do `eickrono-thimisu-backend`;
- erro, `403`, timeout ou indisponibilidade do produto nao deve causar `500` em
  `/identidade/dispositivos/registro/silencioso`;
- chamadas ao produto continuam permitidas apenas nos fluxos de provisionamento
  ou consumo de dados do produto apos sessao pronta.

Validacao ja executada:

- `eickrono-autenticacao-servidor`
  - comando:
    `mvn -pl modulos/modulo-eickrono-autenticacao clean test -Dtest=ResolvedorContextoAutenticacaoServiceTest,CadastroContaInternaServicoTest,FluxoPublicoControllerTest,RegistroDispositivoControllerTest`;
  - resultado: 37 testes executados, 0 falhas, 0 erros.
- `eickrono-identidade-servidor`
  - comando focado:
    `mvn test -Dtest=ClienteContextoPessoaPerfilSistemaLocalTest,EstadoApiControllerTest,VinculoOrganizacionalServiceTest,ConviteOrganizacionalServiceTest`;
  - resultado: 13 testes executados, 0 falhas, 0 erros.
- `git diff --check`
  - resultado: sem problemas nos dois projetos.

Validacao de integracao do `eickrono-identidade-servidor`:

- o Testcontainers local nao encontrou ambiente Docker valido para subir o
  PostgreSQL automaticamente;
- para validar a regra implementada, foi iniciado um PostgreSQL temporario em
  container Docker local;
- comando usado para os ITs:
  `EICKRONO_TEST_PREFER_LOCAL_POSTGRES=true EICKRONO_TEST_POSTGRES_PORT=56515 mvn test -Dtest=AplicacaoApiIdentidadeTest,FluxoPublicoControllerIT,RegistroDispositivoControllerIT`;
- resultado: 42 testes executados, 0 falhas, 0 erros;
- essa validacao cobre os fluxos de integracao do
  `eickrono-identidade-servidor` que dependiam de PostgreSQL.

Publicacao em STG:

- publicado `eickrono-identidade-servidor` na imagem
  `531708494702.dkr.ecr.sa-east-1.amazonaws.com/eickrono-identidade-servidor:stg-20260515-sem-produto-login-01`;
- atualizado o ECS service `identidade-stg`;
- task definition ativa apos o rollout: `identidade-stg:40`;
- estado do rollout no ECS: `COMPLETED`;
- validado `https://id-stg.eickrono.store/api/v1/estado`;
- validado `https://oidc-stg.eickrono.store/realms/eickrono/eickrono-runtime/estado`;
- validado `https://thimisu-backend-stg.eickrono.store/api/v1/estado`;
- logs recentes do `identidade-stg` mostram inicializacao concluida, Flyway sem
  migracao pendente e sem erro imediato apos a subida.

Validacao no iPhone fisico:

- gerada build iOS `profile` com `CONFIG_AMBIENTE=stg`;
- instalada no iPhone fisico `Pedroso-cel`;
- app aberto com bundle `com.eickrono.thimisu`;
- tentativa real de login social Apple em STG concluiu autenticacao social;
- o app chamou `/identidade/dispositivos/registro/silencioso`;
- o registro silencioso concluiu com emissao de token de dispositivo;
- o app navegou para `/`;
- logs do `identidade-stg` registraram:
  - `login_social_publico_sessao_emitida`;
  - token de dispositivo revogado por substituicao;
  - novo token de dispositivo emitido;
- logs do `thimisu-backend-stg` no mesmo intervalo nao registraram chamada para
  `/api/interna/perfis-sistema/contexto`;
- portanto, o caso que antes retornava `500` em
  `/identidade/dispositivos/registro/silencioso` foi validado em STG no iPhone
  fisico.

### Etapa 1 - Congelar o erro com testes

Objetivo:

- provar que login de autenticacao/identidade nao pode depender do produto;
- criar testes que falham no estado atual e passam depois da correcao.

Testes esperados:

- `eickrono-identidade-servidor`
  - teste de `RegistroDispositivoService` ou `RegistroDispositivoController`
    simulando produto indisponivel;
  - expectativa: registro silencioso conclui quando os dados existem no
    `eickrono-identidade-servidor`;
  - expectativa: nenhuma chamada ao cliente HTTP do produto e necessaria.
- `eickrono-autenticacao-servidor`
  - teste de `ResolvedorContextoAutenticacaoService`;
  - expectativa: resolucao por e-mail/sub usa dados de
    `eickrono-autenticacao-servidor`/`eickrono-identidade-servidor`;
  - expectativa: falha do produto nao muda o resultado do login de
    autenticacao/identidade.

Critério de aceite da etapa:

- existe teste reproduzindo que produto indisponivel nao pode derrubar login
  de autenticacao/identidade;
- o teste falha antes da correcao funcional.

### Etapa 2 - Criar resolvedor interno no identidade-servidor

Objetivo:

- tirar do caminho de `eickrono-identidade-servidor` a chamada HTTP para
  `thimisu-backend`.

Alteracao esperada:

- criar uma implementacao local de `ClienteContextoPessoaPerfilSistema` ou um
  servico equivalente;
- consultar repositorios centrais da identidade;
- montar `ContextoPessoaPerfilSistema` sem chamar o produto;
- manter o campo `usuario`, porque o app precisa dele para contas recentes,
  biometria e exibicao correta.

Testes esperados:

- busca por `sub` retorna contexto do `eickrono-identidade-servidor` com
  `usuario`;
- busca por e-mail retorna contexto do `eickrono-identidade-servidor` com
  `usuario`;
- busca por pessoaId retorna contexto do `eickrono-identidade-servidor` com
  `usuario`;
- dados incompletos retornam `Optional.empty()` ou erro funcional controlado,
  sem chamar produto;
- endpoint de registro silencioso usa esse contexto do
  `eickrono-identidade-servidor`.

Critério de aceite da etapa:

- `ClienteContextoPessoaPerfilSistemaHttp` deixa de ser usado no fluxo de
  login/registro silencioso do `eickrono-identidade-servidor`;
- `RegistroDispositivoService` conclui com contexto do
  `eickrono-identidade-servidor`;
- nenhum teste de login de autenticacao/identidade depende de
  `thimisu-backend`.

### Etapa 3 - Remover preferencia pelo produto no autenticacao-servidor

Objetivo:

- garantir que autenticacao publica/social tambem use apenas
  `eickrono-autenticacao-servidor` e `eickrono-identidade-servidor` para decidir
  login.

Alteracao esperada:

- alterar `ResolvedorContextoAutenticacaoService` para usar busca de
  autenticacao/identidade;
- remover ou neutralizar a ideia de "preferir produto" no caminho de login;
- manter produto apenas na camada de provisionamento.

Testes esperados:

- login por senha com contexto existente em autenticacao/identidade conclui sem
  produto;
- login social com rede ja vinculada a conta correta conclui sem produto;
- `social_sem_conta_local + ABRIR_CADASTRO` continua criando apenas contexto
  pendente;
- `social_sem_conta_local + ENTRAR_E_VINCULAR` continua pedindo login local
  para vincular;
- produto indisponivel nao muda esses estados funcionais;
- produto indisponivel nao vira `500` no login de autenticacao/identidade.

Critério de aceite da etapa:

- nenhum teste de fluxo publico precisa simular retorno positivo do produto para
  aprovar login de autenticacao/identidade;
- falha de produto vira pendencia operacional somente quando a etapa for de
  provisionamento.

### Etapa 4 - Preservar provisionamento do produto fora do login

Objetivo:

- manter o `thimisu-backend` funcionando como destino de provisionamento, sem
  voltar a acoplar login.

Alteracao esperada:

- revisar `RegistradorPendenciaIntegracaoProdutoService`;
- revisar chamadas para `/api/interna/perfis-sistema/provisionamentos`;
- garantir que erros do produto sejam registrados como pendencia quando o
  fluxo de autenticacao/identidade ja puder seguir;
- nao mover regras de autenticacao para o produto.

Testes esperados:

- provisionamento com produto online conclui;
- provisionamento com produto fora registra pendencia;
- repetir a mesma pendencia nao cria duplicidade;
- login de autenticacao/identidade continua permitido quando a pendencia de
  produto existe;
- operacoes que realmente dependem do produto podem bloquear no momento de uso
  do produto, nao no login de autenticacao/identidade.

Critério de aceite da etapa:

- produto continua recebendo provisionamento;
- produto indisponivel nao quebra login;
- pendencia de produto fica observavel e reprocessavel.

### Etapa 5 - Limpar contratos legados ou deixar compatibilidade explicita

Objetivo:

- evitar que alguem volte a usar contexto do produto como fonte primaria de
  identidade.

Alteracao esperada:

- se `GET /api/interna/perfis-sistema/contexto` continuar existindo, documentar
  que ele nao e fonte de login de autenticacao/identidade;
- se algum cliente de `eickrono-autenticacao-servidor` ou
  `eickrono-identidade-servidor` ainda apontar para esse endpoint, remover ou
  isolar;
- renomear metodos que contenham `PreferindoProduto` se eles deixarem de fazer
  sentido;
- revisar propriedades de configuracao que apontam `identidade` para
  `thimisu-backend`.

Testes esperados:

- busca textual no codigo nao encontra uso do endpoint do produto no caminho de
  login;
- testes de integracao de `eickrono-autenticacao-servidor` e
  `eickrono-identidade-servidor` passam sem subir `thimisu-backend`;
- testes do produto continuam cobrindo apenas contratos de produto.

Critério de aceite da etapa:

- a fronteira fica clara no codigo e nos nomes;
- nao sobra caminho silencioso `identidade -> thimisu-backend` para login.

## Matriz de cenarios obrigatorios

| Cenario | Produto online | Produto offline/403 | Resultado esperado |
| --- | --- | --- | --- |
| Login por senha com conta liberada | entra | entra | produto nao decide login de autenticacao/identidade |
| Login social com rede vinculada a conta correta | entra | entra | produto nao decide vinculo social |
| Login social sem conta local e sem e-mail existente | pergunta cadastro | pergunta cadastro | produto nao cria nem bloqueia contexto pendente |
| Login social com e-mail de conta existente | pergunta entrar e vincular | pergunta entrar e vincular | decisao vem de `eickrono-autenticacao-servidor`/`eickrono-identidade-servidor` |
| Registro silencioso apos sessao de autenticacao | conclui | conclui | contexto vem de `eickrono-identidade-servidor` |
| Cadastro confirmado com produto online | provisiona | nao se aplica | perfil do produto pronto |
| Cadastro confirmado com produto offline | registra pendencia | registra pendencia | login de autenticacao/identidade pode seguir conforme regra definida em `eickrono-autenticacao-servidor`/`eickrono-identidade-servidor` |
| Operacao do app que depende do produto | funciona | falha de produto | bloqueio ocorre no uso do produto, nao no login |

## Testes por projeto

### `eickrono-identidade-servidor`

Unitarios:

- resolvedor interno do `eickrono-identidade-servidor` por `sub`;
- resolvedor interno do `eickrono-identidade-servidor` por e-mail;
- resolvedor interno do `eickrono-identidade-servidor` por pessoaId;
- montagem de `ContextoPessoaPerfilSistema` com `usuario`;
- comportamento quando dados no `eickrono-identidade-servidor` nao existem.

Integracao:

- `/identidade/dispositivos/registro/silencioso` com contexto valido vindo do
  `eickrono-identidade-servidor`;
- `/identidade/dispositivos/registro/silencioso` sem produto disponivel;
- login social resolvendo pessoa/vinculo sem produto;
- conta nao liberada continua retornando erro funcional correto;
- conta desabilitada continua retornando erro funcional correto.

Verificacao negativa:

- teste deve falhar se o fluxo de login/registro silencioso do
  `eickrono-identidade-servidor` tentar chamar
  `/api/interna/perfis-sistema/contexto` no `eickrono-thimisu-backend`.

### `eickrono-autenticacao-servidor`

Unitarios:

- `ResolvedorContextoAutenticacaoService` busca por e-mail usando
  `eickrono-autenticacao-servidor`/`eickrono-identidade-servidor`;
- `ResolvedorContextoAutenticacaoService` busca por sub usando
  `eickrono-autenticacao-servidor`/`eickrono-identidade-servidor`;
- produto indisponivel nao altera resultado de autenticacao/identidade;
- fluxo `ENTRAR_E_VINCULAR` continua dependente de login local, nao do produto.

Integracao:

- login por senha sem `thimisu-backend`;
- login social sem `thimisu-backend`;
- renovacao de sessao sem `thimisu-backend`;
- registro de pendencia de produto quando a etapa for provisionamento.

### `eickrono-thimisu-backend`

Unitarios/integracao:

- provisionamento interno continua aceitando chamadas validas da autenticacao;
- chamada duplicada de provisionamento e idempotente;
- contratos de produto nao assumem ownership de login de
  autenticacao/identidade.

Nao testar como solucao:

- nao usar liberacao de `GET /api/interna/perfis-sistema/contexto` para fazer o
  login de autenticacao/identidade passar.

### App Flutter

Testes esperados depois dos servidores corrigidos:

- login social com produto fora nao mostra erro `500`;
- login social com rede vinculada entra;
- login social sem conta local mostra o estado funcional correto;
- sessao recebida continua trazendo `usuario`;
- conta recente local usa `usuario` como login reutilizavel;
- biometria continua validando conta local e sessao compativel.

## Validacao manual em STG

Antes do deploy:

1. rodar testes unitarios de `eickrono-autenticacao-servidor` e
   `eickrono-identidade-servidor`;
2. rodar testes de integracao de `registro/silencioso`;
3. garantir que o app cliente ainda compila com o contrato de sessao.

Deploy recomendado:

1. `eickrono-identidade-servidor`;
2. `eickrono-autenticacao-servidor`;
3. `eickrono-thimisu-backend` somente se houver ajuste de provisionamento, nao
   como dependencia para login.

Smoke test em STG:

1. autenticar com Google/Apple de conta ja vinculada;
2. autenticar com Google/Apple sem conta local;
3. autenticar com Google/Apple cujo e-mail pertence a conta local existente;
4. fazer login por senha;
5. chamar registro silencioso;
6. desligar ou simular indisponibilidade do produto e repetir os passos de
   `eickrono-autenticacao-servidor`/`eickrono-identidade-servidor`;
7. confirmar nos logs que o produto nao recebeu
   `/api/interna/perfis-sistema/contexto` durante login de
   autenticacao/identidade.

## Criterios finais de aceite

- `/identidade/dispositivos/registro/silencioso` nao retorna `500` por `403` do
  produto;
- login social de `eickrono-autenticacao-servidor`/`eickrono-identidade-servidor`
  nao chama `thimisu-backend` para decidir usuario, pessoa ou vinculo;
- `usuario` continua sendo retornado na sessao publica;
- produto indisponivel gera pendencia operacional apenas quando a etapa for de
  provisionamento;
- testes de `eickrono-autenticacao-servidor` e `eickrono-identidade-servidor`
  passam sem `thimisu-backend`;
- logs de STG confirmam ausencia de chamada ao produto no login de
  autenticacao/identidade.

## Fora do escopo desta correcao

- mudar UX do app;
- alterar textos de mensagens;
- mudar politica de biometria;
- mudar regra de contas recentes;
- liberar permissao ampla no `thimisu-backend` para mascarar o `403`;
- reescrever o provisionamento do produto alem do necessario para manter a
  fronteira correta;
- alterar fluxo de cadastro social alem do ponto em que ele depende
  indevidamente do produto.
