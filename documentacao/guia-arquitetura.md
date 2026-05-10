# Guia de Arquitetura

Este guia descreve a arquitetura canônica do ecossistema de autenticação da
Eickrono para o app móvel e para os serviços internos que participam do
cadastro, da identidade e do provisionamento do perfil de produto.

## Decisão estrutural do repositório de autenticação

Neste projeto, a decisão estrutural aprovada é esta:

- `eickrono-autenticacao-servidor` deve convergir para um **projeto simples**,
  não para um monorepo por domínio;
- ele deve funcionar como **servidor central/singleton** de autenticação do
  ecossistema;
- ele deve atender qualquer app, site ou projeto cadastrado por dados e
  configuração central, não por “um módulo para cada produto”;
- ele não deve incorporar `contas` como módulo interno de domínio;
- ele não deve pressupor comunicação interna de runtime entre autenticação e
  `contas`.

Estado atual do código:

- o layout físico atual agrupa os módulos em `modulos/`, com
  `modulos/modulo-eickrono-autenticacao`,
  `modulos/modulo-eickrono-keycloak` e a raiz
  agregadora;
- ainda existem referências históricas a `api-contas-eickrono` neste
  repositório;
- isso deve ser lido como **estado transitório do código e da operação local**,
  não como a arquitetura final desejada.

Leitura correta:

- quando este guia citar `modulos/modulo-eickrono-autenticacao`, ele está
  apontando para a localização física atual do código;
- isso não significa aprovação permanente de monorepo;
- `api-contas-eickrono` fica fora da fronteira arquitetural final da
  autenticação.

## Diretriz central

Para o app móvel:

- cadastro, confirmação de e-mail, login, refresh e recuperação de senha
  devem convergir para a borda pública final de `autenticacao`;
- o app não abre páginas do `realm`;
- a `identidade` deve ficar apenas em backchannel interno quando ainda for
  necessária durante a migração;
- a autenticação continua sendo a dona da conta central, das credenciais e da
  autorização para os outros sistemas seguirem;
- a identidade continua sendo a dona da `Pessoa` canônica;
- o backend do produto recebe apenas o provisionamento do perfil daquele
  sistema depois que as partes centrais forem concluídas.

## Componentes principais

- **Servidor de autorização (Keycloak/RH-SSO):** autoridade de credencial,
  token, required actions, derivação de senha e políticas de refresh.
- **Borda pública final de autenticação:** contrato público canônico do app
  para cadastro, confirmação de e-mail, login, recuperação de senha, emissão
  de `X-Device-Token` e integrações móveis.
- **Identidade:** serviço interno/backchannel responsável pela `Pessoa`
  canônica e por partes do contexto compartilhado durante a transição.
- **Backend do produto Thimisu:** downstream de domínio, provisionado por
  comunicação interna entre servidores depois que conta central e `Pessoa`
  canônica forem resolvidas.
- **API Contas Eickrono:** domínio financeiro separado, fora da fronteira
  interna da autenticação e sem receber senha ou código do app.
- **PostgreSQL + Flyway:** persistência dos estados de cadastro, dispositivos,
  códigos, tokens e auditoria.
- **Observabilidade:** Actuator, Micrometer, Prometheus e OpenTelemetry.

## Papéis por serviço

### Servidor de autorização e autenticação

- guarda e valida credenciais;
- sustenta sessão, refresh, brokers sociais e políticas de segurança;
- controla confiança do dispositivo;
- responde pela conta central do usuário;
- valida a disponibilidade de `usuario + sistema`;
- libera, por comunicação interna entre servidores, a continuidade do fluxo
  para identidade e para o backend do produto.

### Identidade

- recebe chamadas internas da autenticação quando o fluxo ainda precisar de
  `Pessoa` canônica, contexto compartilhado ou etapas transitórias do runtime;
- concentra a leitura e a escrita da `Pessoa` canônica;
- expõe o contexto de identidade compartilhado para os demais serviços.

### Backend do produto Thimisu

- recebe apenas os dados necessários para criar o perfil do produto;
- persiste o contexto do produto, não a `Pessoa` canônica;
- nunca recebe senha digitada, código de recuperação ou tentativa de login do
  app;
- deve tratar o provisionamento recebido da autenticação como operação
  idempotente.

### Contas

- continua sendo domínio separado;
- continua com sua própria borda pública para app/site/frontend;
- não deve ser tratada como módulo interno obrigatório do servidor de
  autenticação;
- não participa da malha interna canônica da autenticação.

## Fluxos públicos canônicos

Para o retrato do comportamento **efetivamente implementado hoje**, incluindo
divergências já observadas entre fluxo atual e fluxo alvo, ver também:

- `guia_fluxos_login_autenticacao_app.md`
- `fluxogramas_fluxos_publicos_estado_atual.md`

### Cadastro

1. o app envia o cadastro para a borda pública final de `autenticacao`;
2. a autenticação cria o cadastro pendente e envia o código de e-mail;
3. o app confirma o código pela borda pública final de `autenticacao`;
4. se ainda necessário durante a migração, a autenticação aciona a
   `identidade` por backchannel;
5. a autenticação conclui a conta central;
6. a autenticação aciona a identidade para criar ou atualizar a `Pessoa`
   canônica;
7. a autenticação aciona o backend do produto para criar o perfil daquele
   sistema;
8. o fluxo libera a continuidade do uso do app.

### Login

1. o app envia login, senha, atestação e metadados do aparelho para a borda
   pública final de `autenticacao`;
2. a autenticação valida a credencial e a política de dispositivo;
3. se precisar de contexto canônico ainda não internalizado, a autenticação
   consulta a `identidade` por backchannel;
4. a autenticação registra ou atualiza silenciosamente o dispositivo quando o
   risco permitir;
5. a autenticação emite a sessão já com `X-Device-Token`;
6. o app não abre uma tela dedicada de registro de dispositivo.

### Recuperação de senha

1. o app envia o e-mail para a borda pública final de `autenticacao`;
2. a autenticação sempre devolve mensagem neutra;
3. se existir conta, a autenticação envia o código por e-mail;
4. o app confirma o código pela borda pública final de `autenticacao`;
5. o app envia a nova senha pela borda pública final de `autenticacao`;
6. a autenticação atualiza a credencial no Keycloak e registra auditoria.

## Contratos públicos canônicos

Para o app móvel, os contratos canônicos passam a ser:

- `POST /api/publica/cadastros`
- `POST /api/publica/cadastros/{cadastroId}/confirmacoes/email`
- `POST /api/publica/cadastros/{cadastroId}/confirmacoes/email/reenvio`
- `POST /api/publica/sessoes`
- `POST /api/publica/recuperacoes-senha`
- `POST /api/publica/recuperacoes-senha/{fluxoId}/confirmacoes/email`
- `POST /api/publica/recuperacoes-senha/{fluxoId}/confirmacoes/email/reenvio`
- `POST /api/publica/recuperacoes-senha/{fluxoId}/senha`

Qualquer contrato antigo em que servidor de produto receba senha ou código do
app deve ser tratado como legado.

## Comunicação interna entre servidores para provisionamento

O provisionamento interno entre serviços deve seguir estas regras:

- autenticação mútua por `mTLS`;
- JWT de serviço com allowlist explícita de `client_id`;
- timeout curto e retry controlado;
- idempotência por `cadastroId`;
- auditoria em ambos os lados.

Direção canônica:

- autenticação -> identidade para criar ou atualizar `Pessoa`;
- autenticação -> backend do produto para criar o perfil daquele sistema.

### O que significa idempotência por `cadastroId`

Se a autenticação repetir o mesmo provisionamento por timeout, retry ou falha
de rede:

- a identidade não pode criar uma segunda `Pessoa` para o mesmo fluxo;
- o backend do produto não pode criar um segundo perfil para o mesmo fluxo;
- os serviços devem reconhecer o mesmo `cadastroId`;
- os serviços devem devolver a mesma resposta lógica da primeira criação.

Isso evita duplicidade de pessoa ou perfil quando a primeira resposta se perde
no caminho.

## Momento correto do provisionamento

O backend do produto não deve ser provisionado no primeiro envio do cadastro.

O momento correto é:

1. identidade recebe o cadastro público;
2. autenticação cria o cadastro pendente;
3. autenticação envia o código por e-mail;
4. app confirma o código pela identidade;
5. autenticação conclui a conta central;
6. autenticação provisiona a identidade;
7. autenticação provisiona o backend do produto;
8. o fluxo libera a continuidade do uso.

Assim, o domínio do produto não fica poluído por cadastros incompletos ou nunca
confirmados.

## Segurança

- o app nunca recebe resposta que diga explicitamente se o e-mail existe na
  recuperação de senha;
- o backend do produto não recebe segredo principal do usuário;
- logs precisam mascarar tokens, e-mails e identificadores sensíveis;
- rate limit, lockout, expiração de código, limite de tentativas e antifraude
  ficam concentrados na autenticação;
- qualquer integração interna entre serviços modernos deve usar `mTLS` + JWT de
  serviço.

## Dispositivo e atestação

- a atestação nativa de app/dispositivo continua centralizada na autenticação;
- o `X-Device-Token` é emitido pelo backend já no login quando o dispositivo
  estiver liberado;
- se houver necessidade de nova validação de contato, o app deve reutilizar a
  tela de verificação já existente, não uma tela separada de registro de
  dispositivo;
- o refresh continua condicionado ao `device_token`, validado centralmente.

Para a camada adicional de sinais locais do app além de `Play Integrity` e
`App Attest`, ler também:

- `guia-seguranca-app-movel.md`

## Sobre FAPI e OIDC

O servidor de autorização continua suportando mecanismos FAPI, OIDC e client
policies para clientes confidenciais, web e integrações específicas. Para o app
móvel, porém, a diretriz vigente é UX nativa mediada pela API de identidade,
não telas web do `realm`.
