# Fluxograma do Login Social no App Movel

Este documento isola apenas o fluxo de **login social** do app movel.

Objetivo:

- mostrar o que acontece quando o operador toca em `Apple`, `Google` ou outro
  provedor social publicado;
- listar os estados que o servidor pode devolver;
- mapear cada estado para a UX esperada no app;
- evitar que o app trate casos diferentes como se fossem a mesma coisa.

Este documento nao define cadastro por senha, biometria, recuperacao de senha
ou telas de perfil. Ele cobre apenas o recorte:

- `operador toca no botao de rede social`
- `app autentica com o provedor`
- `eickrono-autenticacao-servidor responde`
- `app decide o que mostrar`

## 1. Etapas do fluxo

### 1.1 Inicio

Quando o operador toca em um botao social:

- o app abre a autenticacao do provedor;
- se o provedor for nativo no aparelho, o app coleta:
  - `email`
  - `nomeCompleto`
  - `nomeUsuario`
  - `identificadorExterno`
  - `urlAvatarExterno`, quando existir;
- depois o app chama o endpoint publico de login social do
  `eickrono-autenticacao-servidor`.

### 1.2 Decisao principal

Depois que o `eickrono-autenticacao-servidor` responde, o app nao pode tratar
tudo como "mensagem de erro".

O app precisa olhar:

- `codigo`
- `statusCode`
- `detalhes`
- e, para `social_sem_conta_local`, obrigatoriamente:
  - `detalhes.acaoSugerida`

## 2. Tabela canonica de estados do servidor

| Estado do servidor | Sinal esperado | UX correta no app |
| --- | --- | --- |
| Sessao pronta | autenticacao concluida e sessao local finalizada | entrar no app |
| Dispositivo pendente | contexto de registro/confirmacao de dispositivo | abrir tela de confirmacao de dispositivo |
| Abrir cadastro | `codigo = social_sem_conta_local` + `detalhes.acaoSugerida = ABRIR_CADASTRO` | mostrar aviso inferior com `Sim, abrir cadastro` e `Agora nao`; se confirmar, abrir `/cadastro` com dados sociais temporarios no app e foto da rede quando existir |
| Entrar e vincular | `codigo = social_sem_conta_local` + `detalhes.acaoSugerida = ENTRAR_E_VINCULAR` | mostrar aviso inferior com `Entrar e vincular` e `Agora nao`; se confirmar, pedir login local e vincular a rede ao usuario ao concluir |
| Rede social ja pertence a outra conta | `codigo = vinculo_social_pertence_a_outra_conta` | mostrar conflito explicito; nao oferecer vinculacao automatica nem cadastro novo |
| Conta desabilitada | `codigo = conta_desabilitada` | abrir direto a tela de excecao de usuario bloqueado; voltar retorna ao login |
| Conta nao liberada | `codigo = conta_nao_liberada` ou `conta_em_preparacao` ou `conta_incompleta` | mostrar aviso inferior com texto explicativo e botoes `Sim` e `Nao`; `Sim` retoma a validacao aplicavel, `Nao` fecha o aviso e mantem o operador no login |
| Autenticacao social invalida | `codigo = autenticacao_social_invalida` | mostrar mensagem inferior temporaria que fecha sozinha |
| Falha de rede | `codigo = falha_rede` ou `statusCode = 0` | mostrar mensagem inferior temporaria que fecha sozinha |
| Erro inesperado | `statusCode >= 500` ou resposta sem codigo funcional tratavel | mostrar mensagem inferior generica, fechar sozinha e registrar observabilidade |

## 2.1 Topico: todas as opcoes possiveis enviadas pelo servidor neste recorte

Este topico lista as opcoes que o servidor de autenticacao pode devolver
quando o operador solicita autenticacao por rede social no app.

Para evitar confusao, elas foram separadas em dois grupos:

- respostas de **sucesso ou continuidade funcional**;
- respostas de **erro ou bloqueio funcional**.

### 2.1.1 Respostas de sucesso ou continuidade funcional

#### A. Sessao pronta

O que vem do servidor:

- `accessToken`
- `refreshToken`
- `expiresIn`
- `tokenDispositivo`, quando a sessao local tambem foi concluida

Para que serve:

- indicar que a autenticacao social deu certo;
- indicar que o app ja pode entrar sem etapa extra.

Uso no app:

- concluir a sessao;
- entrar na area autenticada.

#### B. Dispositivo pendente

O que vem do servidor:

- `registroDispositivoId`
- `registroDispositivoExpiraEm`
- `statusRegistroDispositivo`
- `canaisConfirmacao`

Para que serve:

- indicar que a autenticacao central deu certo;
- mas o aparelho ainda precisa confirmacao interativa antes de entrar.

Uso no app:

- abrir a tela de confirmacao de dispositivo.

#### C. `social_sem_conta_local` com `acaoSugerida = ABRIR_CADASTRO`

O que vem do servidor:

- `codigo = social_sem_conta_local`
- `detalhes.acaoSugerida = ABRIR_CADASTRO`
- normalmente tambem:
  - `email`
  - `provedor`
  - `identificadorExterno`
  - `nomeUsuarioExterno`
  - `nomeExibicaoExterno`
  - `urlAvatarExterno`

Para que serve:

- indicar que a rede social autenticou;
- mas ainda nao existe usuario local valido neste projeto para receber esse
  vinculo;
- e a proxima acao correta e abrir cadastro novo usando os dados dessa rede.
- neste ponto o `eickrono-autenticacao-servidor` nao deve criar usuario,
  forma social, avatar, pessoa, cadastro finalizado nem contexto pendente em
  banco.

Uso no app:

- aplicar a regra detalhada da secao `3.1`.

#### D. `social_sem_conta_local` com `acaoSugerida = ENTRAR_E_VINCULAR`

O que vem do servidor:

- `codigo = social_sem_conta_local`
- `detalhes.acaoSugerida = ENTRAR_E_VINCULAR`
- normalmente tambem:
  - `loginSugerido`
  - `emailContaExistente`
  - `provedor`
  - `identificadorExterno`
  - `nomeUsuarioExterno`

Para que serve:

- indicar que a rede social autenticou;
- indicar que ja existe usuario local neste projeto com o mesmo e-mail;
- mas essa rede ainda nao esta vinculada a esse usuario;
- e a proxima acao correta e entrar com a conta local para concluir a
  vinculacao.
- neste ponto o `eickrono-autenticacao-servidor` nao deve criar forma social,
  avatar ou contexto pendente em banco.

Uso no app:

- aplicar a regra detalhada da secao `3.2`.

### 2.1.2 Respostas de erro ou bloqueio funcional

#### E. `vinculo_social_pertence_a_outra_conta`

O que vem do servidor:

- `codigo = vinculo_social_pertence_a_outra_conta`
- normalmente com detalhe de acao sugerida de suporte/bloqueio

Para que serve:

- indicar que a rede social ja esta ligada a outro usuario local;
- impedir vinculacao automatica duplicada.

Uso no app:

- se a rede ja estiver ligada ao usuario correto e esse usuario estiver liberado neste projeto, esse nao e um caso de erro: o app deve entrar direto;
- este codigo so faz sentido quando a rede estiver ligada a outra conta local;
- mostrar conflito explicito;
- nao oferecer cadastro novo automatico;
- nao oferecer vinculacao automatica.

#### F. `conta_nao_liberada`

O que vem do servidor:

- `codigo = conta_nao_liberada`
- possivelmente com detalhes como:
  - `cadastroId`
  - `statusUsuario`
  - `email`

Para que serve:

- indicar que a conta central existe;
- mas o contexto local do projeto ainda nao esta liberado para uso.

Uso no app:

- mostrar aviso inferior com texto explicativo e botoes `Sim` e `Nao`;
- `Sim` abre `/validacao-contatos` para retomar a validacao aplicavel;
- `Nao` fecha o aviso e mantem o operador no login.

#### G. `conta_desabilitada`

O que vem do servidor:

- `codigo = conta_desabilitada`

Para que serve:

- indicar que a conta esta bloqueada ou desabilitada.

Uso no app:

- abrir direto a tela de excecao de usuario bloqueado;
- o botao de voltar dessa tela retorna ao login.

#### H. `autenticacao_social_invalida`

O que vem do servidor:

- `codigo = autenticacao_social_invalida`

Para que serve:

- indicar que a credencial social recebida nao pode ser aceita pelo
  `eickrono-autenticacao-servidor`.

Uso no app:

- mostrar mensagem na parte inferior da tela;
- a mensagem fecha sozinha depois de um tempo.

#### I. `falha_rede`

O que vem do servidor:

- `codigo = falha_rede`
- ou, do ponto de vista do app, `statusCode = 0`

Para que serve:

- representar indisponibilidade de rede ou impossibilidade de concluir a
  chamada.

Uso no app:

- mostrar mensagem na parte inferior da tela;
- a mensagem fecha sozinha depois de um tempo.

#### J. Erro inesperado sem codigo funcional estavel

O que vem do servidor:

- `statusCode >= 500`
- ou resposta sem `codigo` funcional tratavel

Para que serve:

- indicar falha interna, falha estrutural ou resposta fora do contrato esperado.

Uso no app:

- mostrar mensagem generica na parte inferior da tela;
- a mensagem fecha sozinha depois de um tempo;
- registrar observabilidade.

### 2.1.3 Observacao importante

No recorte de login social, o estado mais facil de confundir e:

- `social_sem_conta_local`

Ele nao e uma unica resposta funcional.

Hoje ele pode significar pelo menos dois caminhos diferentes:

- `ABRIR_CADASTRO`
- `ENTRAR_E_VINCULAR`

Por isso, o app nao pode decidir a UX olhando so:

- `codigo = social_sem_conta_local`

Ele precisa olhar tambem:

- `detalhes.acaoSugerida`

## 3. Regras especificas para `social_sem_conta_local`

Este codigo nao e suficiente sozinho.

O app obrigatoriamente precisa diferenciar dois subcasos:

### 3.1 `ABRIR_CADASTRO`

Sinal do servidor:

- `codigo = social_sem_conta_local`
- `detalhes.acaoSugerida = ABRIR_CADASTRO`

UX correta:

- a tela principal de login nao muda;
- a mensagem aparece na parte de baixo da tela;
- nessa mensagem aparecem:
  - `Sim, abrir cadastro`
  - `Agora nao`

Acao de `Sim, abrir cadastro`:

- fecha a mensagem inferior;
- abre `/cadastro`;
- leva os dados sociais temporarios do app para o cadastro;
- se existir foto da rede social, essa foto deve ir preenchida;
- se nao existir foto da rede, o cadastro inicia com o avatar padrao de usuario.

Acao de `Agora nao`:

- fecha a mensagem inferior;
- mantem o operador no login;
- limpa os dados sociais temporarios.

### 3.2 `ENTRAR_E_VINCULAR`

Sinal do servidor:

- `codigo = social_sem_conta_local`
- `detalhes.acaoSugerida = ENTRAR_E_VINCULAR`

UX correta:

- a tela principal de login nao muda;
- a mensagem aparece na parte de baixo da tela;
- nessa mensagem aparecem:
  - `Entrar e vincular`
  - `Agora nao`

Acao de `Entrar e vincular`:

- fecha a mensagem inferior;
- mantem o operador no login;
- preenche o campo de login com `loginSugerido`, quando existir;
- limpa o campo de senha;
- mantem os dados sociais temporarios ativos;
- espera o operador concluir o login local para finalizar a vinculacao;
- se o login local concluir, a rede social e vinculada automaticamente ao usuario;
- se a senha falhar 3 vezes, a vinculacao pendente e cancelada e os dados sociais temporarios sao apagados.

Acao de `Agora nao`:

- fecha a mensagem inferior;
- mantem o operador no login;
- limpa os dados sociais temporarios.

## 4. Regras para os dados sociais temporarios no app

Os dados sociais temporarios existem apenas para o app lembrar o que acabou de
voltar do login social. Eles nao representam usuario, cadastro, vinculo social
ou avatar persistido.

O app pode manter em memoria/estado local, quando disponiveis:

- `redeSocial`
- `email`
- `nomeCompleto`
- `nomeUsuario`
- `identificadorExterno`
- `nomeUsuarioExterno`
- `urlAvatarExterno`
- `loginSugerido`
- `acaoSugerida`

Esse estado deve:

- ser criado quando o `eickrono-autenticacao-servidor` devolver um caso
  funcional pendente;
- ser consumido pela UX da mensagem inferior;
- ser apagado quando o operador tocar `Agora nao`;
- ser apagado depois que o fluxo terminar com sucesso;
- nao vazar para outra tentativa futura depois de cancelado.

Esse estado nao deve:

- virar JWT ou token social temporario;
- virar registro em banco no `eickrono-autenticacao-servidor`;
- virar registro em banco no `eickrono-identidade-servidor`;
- virar usuario no Keycloak;
- virar conta local recente no app.

## 5. Regras de separacao entre casos

- `social_sem_conta_local` nunca deve ser tratado sem olhar `detalhes.acaoSugerida`.
- `ABRIR_CADASTRO` e `ENTRAR_E_VINCULAR` compartilham o mesmo `codigo`, mas nao compartilham a mesma UX.
- `vinculo_social_pertence_a_outra_conta` nao pode ser tratado como cadastro novo nem como vinculacao automatica.
- quando o operador precisa decidir, a mensagem inferior nao pode aparecer so com texto: ela precisa trazer os botoes da decisao.

## 6. Casos que precisam estar cobertos em teste

### 6.1 Login social com sucesso

- autentica
- fecha sessao local
- entra no app

### 6.2 `social_sem_conta_local` + `ABRIR_CADASTRO`

- a mensagem inferior aparece
- aparecem `Sim, abrir cadastro` e `Agora nao`
- `Sim` abre cadastro com os dados sociais temporarios e a foto da rede quando existir
- `Agora nao` limpa os dados sociais temporarios

### 6.3 `social_sem_conta_local` + `ENTRAR_E_VINCULAR`

- a mensagem inferior aparece
- aparecem `Entrar e vincular` e `Agora nao`
- `Entrar e vincular` mantem os dados sociais temporarios e inicia a vinculacao pelo login local
- 3 falhas de senha cancelam a vinculacao pendente e limpam os dados sociais temporarios
- `Agora nao` limpa os dados sociais temporarios

### 6.4 `vinculo_social_pertence_a_outra_conta`

- nao abre cadastro
- nao oferece vinculacao automatica
- mostra conflito explicito

### 6.5 `conta_desabilitada`

- abre a tela de excecao de usuario bloqueado
- voltar retorna ao login

### 6.6 `falha_rede`

- mostra mensagem inferior temporaria
- a mensagem fecha sozinha

### 6.7 `autenticacao_social_invalida`

- mostra mensagem inferior temporaria
- a mensagem fecha sozinha

### 6.8 `erro inesperado`

- mostra mensagem inferior generica
- a mensagem fecha sozinha
- registra observabilidade

## 7. Fonte de verdade deste fluxo

As decisoes deste documento se apoiam nestas referencias:

- [guia_fluxos_login_autenticacao_app.md](/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/documentacao/guia_fluxos_login_autenticacao_app.md)
- [checklist_tecnico_vinculos_sociais_plural_cadastro.md](/Users/thiago/Desenvolvedor/flutter/eickrono-identidade-servidor/checklist_tecnico_vinculos_sociais_plural_cadastro.md)
- [RegistroDispositivoControllerIT.java](/Users/thiago/Desenvolvedor/flutter/eickrono-identidade-servidor/src/test/java/com/eickrono/api/identidade/apresentacao/api/RegistroDispositivoControllerIT.java)
- [FluxoPublicoControllerIT.java](/Users/thiago/Desenvolvedor/flutter/eickrono-identidade-servidor/src/test/java/com/eickrono/api/identidade/apresentacao/api/FluxoPublicoControllerIT.java)

## 8. Regra final

Quando a autenticacao social for solicitada, o app deve sempre responder uma
destas saidas:

- entrar;
- confirmar dispositivo;
- abrir cadastro;
- entrar e vincular;
- bloquear com motivo explicito;
- ou falhar com erro tecnico claro.

Ele nao pode:

- tratar casos diferentes como se fossem iguais;
- mostrar so mensagem sem acao quando o operador precisa decidir;
- ignorar `detalhes.acaoSugerida` quando o servidor a enviar.
