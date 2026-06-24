# TODO Local do Servidor de Autenticacao

Este arquivo e a agenda local do `eickrono-autenticacao-servidor`.

Ele nao substitui os documentos canonicos de migracao. Ele existe para:

- transformar a migracao em agenda executavel do repositorio;
- separar o que e prioridade de agora do que deve entrar depois;
- evitar que pendencias locais se percam no meio do backlog cross-service.

Documentos de referencia:

- [consolidado_migracao_autenticacao_identidade_thimisu.md](consolidado_migracao_autenticacao_identidade_thimisu.md)
- [matriz_migracao_autenticacao_identidade_thimisu_backend.md](matriz_migracao_autenticacao_identidade_thimisu_backend.md)
- [backlog_cross_service_autenticacao_oidc_dispositivo.md](backlog_cross_service_autenticacao_oidc_dispositivo.md)

## Como Ler Esta Agenda

- `Agora`: trabalho que faz sentido puxar imediatamente neste repositorio.
- `Proximo`: trabalho importante, mas que depende de estabilizar o que esta em andamento.
- `Depois da migracao`: pendencias relevantes, mas que nao devem roubar foco do corte arquitetural atual.
- `Observacao`: regra de leitura para nao misturar backlog local com backlog cross-service.

## Agora

### 1. Encerrar A Etapa 5 Da Migracao

Objetivo:

- sair de uma preparacao parcial da separacao de banco para uma operacao controlada por servico.

O que ja ficou pronto:

- `dev` e execucao do profile `stg` via Docker com variaveis de banco por servico;
- `rollout_stg_service.sh` preparado para host, porta, banco, usuario e segredo por servico;
- validacao automatica dos templates renderizados;
- resumo automatizado da configuracao de banco usada por cada servico em `stg`.

O que ainda falta fechar:

- decidir quando `stg` vai continuar no mesmo host RDS com bancos separados por nome e quando passara a usar separacao fisica por host ou instancia;
- aplicar esse mesmo padrao de configuracao no ambiente real quando a infraestrutura estiver pronta;
- registrar no historico operacional a primeira rodada que usar overrides reais por servico;
- confirmar se a conta `contas` tambem deve seguir a mesma estrategia de isolamento fisico no mesmo momento.

Definicao de pronto:

- o caminho operacional de `stg` deixa claro o estado atual;
- o rollout aceita separacao fisica por servico sem refactor novo;
- a equipe consegue inspecionar o layout efetivo de banco de `stg` sem leitura manual de JSON.

### 2. Fechar O Que Restar Da Limpeza Pos-Etapa 4

Objetivo:

- garantir que a mudanca de semantica de `identidade` para `perfil do sistema` nao deixou sobra local relevante no servidor de autenticacao.

Checklist local:

- revisar se ainda existe linguagem antiga em DTOs, logs e mensagens internas;
- revisar se o caminho novo de `usuario + sistema` ja e o unico caminho real de decisao, deixando o legado apenas onde for contrato externo temporario;
- revisar se os testes do modulo local cobrem os nomes novos de forma suficiente;
- revisar se a documentacao local do repositorio nao contradiz mais a semantica nova.

Definicao de pronto:

- nao sobra nome legado relevante em fluxo novo do servidor;
- qualquer resquicio restante fica documentado como compatibilidade temporaria.

## Proximo

### 3. Fila De Pendencias De Integracao Com Produto

Objetivo:

- preparar o servidor de autenticacao para lidar com falhas do backend do produto sem perder o cadastro central.

Escopo ja consolidado:

- sondagem operacional simples antes de criar, alterar ou apagar algo no produto;
- fila persistida de pendencias;
- nova tentativa automatica;
- parametrizacao de tempo entre tentativas e limite maximo no banco da `autenticacao`.

Modelo minimo ja decidido:

- `uriEndpoint`
- `metodoHttp`
- `payloadJson`
- `concluido`
- `statusPendencia`
- `produtoAlvo`
- `idempotencyKey`
- `versaoContrato`
- `ultimaTentativaEm`
- `proximaTentativaEm`
- `tentativasRealizadas`
- `codigoUltimoErro`
- `mensagemUltimoErro`

Observacao:

- esta frente deve entrar depois que a migracao principal deixar de consumir energia diaria;
- ela e importante, mas nao deve competir com o fechamento do ownership entre `autenticacao`, `identidade` e `thimisu`.

### 4. Governanca De Sessao E Dispositivo

Objetivo:

- amadurecer o controle de sessao e confianca de dispositivo no ecossistema.

Backlog local:

- implementar revogacao remota de sessoes e de `X-Device-Token`;
- permitir encerramento de todas as sessoes ativas de um usuario;
- manter lista de dispositivos confiaveis com historico de uso e ultimo acesso;
- introduzir bloqueio por risco, expiracao por inatividade e reautenticacao para operacoes sensiveis.

Observacao:

- esta trilha conversa com `app`, `identidade` e Keycloak;
- a parte local deste repositorio deve focar no contrato e na governanca central.

## Depois Da Migracao

### 5. Fluxo De Cadastro Pendente

Objetivo:

- revisar o modelo de estados de `cadastros_conta` quando a validacao canonica de telefone entrar no fluxo.

Pontos ja conhecidos:

- hoje apenas `PENDENTE_EMAIL` pode ser apagado automaticamente;
- `EMAIL_CONFIRMADO` nao deve ser apagado no modelo atual;
- quando a validacao de telefone entrar, um e-mail confirmado sem telefone confirmado nao devera mais liberar o usuario imediatamente.

Decisao futura esperada:

- reavaliar a politica de limpeza e o desenho de estados antes de colocar telefone como etapa canonica obrigatoria.

### 6. Entregabilidade E Contexto De E-mail

Objetivo:

- amadurecer o canal de e-mail transacional do ecossistema.

Pendencias:

- publicar `DMARC` para o dominio de envio;
- validar cabecalho real dos provedores-alvo;
- separar `fluxoId` interno de `protocoloSuporte` externo;
- evoluir o contexto exibido no e-mail por canal, produto, marca e ambiente;
- persistir `locale` e `timeZone` nos fluxos publicos;
- definir fallback de renderizacao de horario;
- exibir horario local e `UTC` quando fizer sentido.

#### Subagenda De Negocio: Migracao Para E-mail Transacional

Passo 1:

- autenticar o dominio no provedor escolhido.

Passo 2:

- trocar SMTP pessoal por API transacional.

Passo 3:

- receber webhooks do provedor.

Passo 4:

- ligar webhooks a auditoria e suporte.

Passo 5:

- evoluir templates versionados por evento e idioma.

### 7. Monitoramento E Antifraude

Objetivo:

- ampliar seguranca operacional alem do rate limit.

Pendencias:

- ampliar mascaramento de dados sensiveis em logs e eventos;
- integrar eventos criticos de autenticacao com alerta e monitoramento;
- implementar estrategia de antifraude operacional com deteccao, bloqueio progressivo, quarentena e resposta.

### 8. Seguranca Do App Movel

Objetivo:

- endurecer a seguranca do lado cliente para complementar a autenticacao central.

Pendencias:

- deteccao de root e jailbreak alem da atestacao nativa;
- sinais de Frida, hooking e adulteracao do app;
- revisao de armazenamento local, screenshot, clipboard e cache.

### 9. Padronizacao OIDC Entre Ambientes

Objetivo:

- manter o contrato OIDC uniforme entre `dev`, `stg` e `prod`.

Pendencias:

- padronizar realm `eickrono` em todos os ambientes;
- separar claramente hosts `id-*` e `oidc-*`;
- alinhar `issuer`, callbacks, exports de realm, configs Spring e configs do app.

Observacao:

- ver plano detalhado em `plano-padronizacao-realm-unico.md`.

## Observacao Final

Quando uma frente atravessar app, Keycloak, `identidade`, `contas` e infraestrutura
ao mesmo tempo:

- usar o documento cross-service ou o consolidado como fonte principal;
- deixar aqui apenas o desdobramento local que realmente pertence ao
  `servidor de autenticacao`.
