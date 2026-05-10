# Guia TOTP no Keycloak

Status: proposta teórica inicial em 2026-04-04.

## Objetivo

Descrever como a Eickrono deve introduzir TOTP no ecossistema atual sem quebrar a arquitetura vigente:

- o `eickrono-identidade-servidor` continua sendo a borda do aplicativo móvel;
- o Keycloak continua sendo a autoridade de credencial;
- o aplicativo não passa a depender de telas web do domínio de autenticação como fluxo principal;
- a integração interna segue o padrão já adotado de `mTLS` + JWT de serviço + timeout curto.

## Resumo executivo

- O Keycloak já está parcialmente preparado para TOTP: a ação obrigatória `CONFIGURE_TOTP` já está habilitada nos domínios versionados.
- Isso ainda não basta para o aplicativo móvel atual, porque o login público passa pelo `eickrono-identidade-servidor`, que autentica no Keycloak via `grant_type=password` e hoje envia apenas `username` e `password`.
- Se um usuário com TOTP habilitado tentar logar hoje, a tendência é o fluxo falhar como se fossem credenciais inválidas, porque o backend atual não envia `otp`/`totp` nem distingue "senha errada" de "TOTP faltando".
- O TOTP deve entrar somente depois de a conta estar liberada, isto é, após verificação de e-mail e, quando a política do produto exigir, após verificação de telefone.
- A melhor direção para a Eickrono é manter o segredo TOTP e a validação final dentro do Keycloak, mas expor o provisionamento e a orquestração ao aplicativo via `eickrono-identidade-servidor`.
- Para interoperabilidade real com Google Authenticator, Microsoft Authenticator e o app Senhas da Apple, a política deve ficar em `TOTP + SHA1 + 6 dígitos + período de 30s + reusableCode=false`.
- O backend deve devolver ao aplicativo os dados de provisionamento em formato canônico (`otpauth://...` + segredo Base32), para que ele possa mostrar código QR, copiar a chave manual e, opcionalmente, tentar abrir um autenticador instalado no mesmo aparelho. No iOS, o aplicativo também pode derivar a variante `apple-otpauth://...` para integração com o app Senhas.
- Código QR e chave manual devem ser o fluxo canônico. "Abrir aplicativo autenticador" deve ser apenas conveniência, não contrato principal.
- Códigos de recuperação devem entrar junto do TOTP desde a primeira versão.

## Estado atual do monorepo

### Keycloak

O projeto [`eickrono-autenticacao-servidor`](/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/README.md) já customiza o Keycloak com SPI própria para:

- derivação de senha no fluxo de navegador;
- derivação de senha no registro;
- ação obrigatória de troca de senha;
- política de refresh por `device_token`.

Os domínios versionados já têm `CONFIGURE_TOTP` habilitado em:

- [`desenvolvimento-realm.json`](/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/autorizacao/realms/desenvolvimento-realm.json)
- [`homologacao-realm.json`](/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/autorizacao/realms/homologacao-realm.json)
- [`producao-realm.json`](/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/autorizacao/realms/producao-realm.json)

Ao mesmo tempo, o fluxo de navegador versionado da Eickrono hoje contém apenas o formulário customizado de usuário/senha, sem o subfluxo condicional de 2FA que existe no fluxo de navegador padrão do Keycloak.

### API de identidade

O login móvel atual passa por:

- [`FluxoPublicoController.java`](/Users/thiago/Desenvolvedor/flutter/eickrono-identidade-servidor/src/main/java/com/eickrono/api/identidade/apresentacao/api/FluxoPublicoController.java)
- [`AutenticacaoSessaoInternaServico.java`](/Users/thiago/Desenvolvedor/flutter/eickrono-identidade-servidor/src/main/java/com/eickrono/api/identidade/aplicacao/servico/AutenticacaoSessaoInternaServico.java)

Hoje esse serviço monta uma chamada de token com:

- `grant_type=password`
- `client_id`
- `client_secret` quando existir
- `username`
- `password`

Não existe envio de `otp` nem `totp`.

### Consequência prática

No Keycloak padrão, a concessão direta nativa contém um subfluxo condicional de OTP e o autenticador `direct-grant-validate-otp`. Esse autenticador aceita o parâmetro `otp` e, por retrocompatibilidade, também `totp`.

Portanto, do ponto de vista técnico:

- o Keycloak já sabe validar TOTP em concessão direta;
- a Eickrono ainda não orquestra isso no login do aplicativo;
- habilitar TOTP apenas no console ou no domínio não resolve o produto móvel por si só.

## Como Google Authenticator, Microsoft Authenticator e Senhas da Apple entram nessa história

### Formato canônico de provisionamento

O ecossistema de autenticadores TOTP converge para o URI `otpauth://`.

Exemplo de carga:

```text
otpauth://totp/Eickrono:usuario@exemplo.com?secret=BASE32SECRET&issuer=Eickrono&algorithm=SHA1&digits=6&period=30
```

Essa mesma carga serve para:

- gerar um código QR;
- permitir cópia manual da chave;
- tentar abrir um aplicativo autenticador que registre o esquema `otpauth://`.

Para o ecossistema Apple, existe ainda uma variante oficial do mesmo padrão:

```text
apple-otpauth://totp/Eickrono:usuario@exemplo.com?secret=BASE32SECRET&issuer=exemplo.com&digits=6&period=30
```

Essa variante é específica do gerenciador de senhas da Apple e pode ser aberta pelo aplicativo em resposta a um toque de botão no iOS. Nela, o `issuer` deve seguir o domínio do serviço.

### Google Authenticator

Pontos relevantes para o desenho:

- o formato `otpauth://` com `secret`, `issuer`, `digits`, `algorithm` e `period` é o formato de provisionamento associado historicamente ao Google Authenticator;
- o Google passou a oferecer sincronização/backup dos códigos na conta Google;
- historicamente, implementações do Google Authenticator ignoraram alguns parâmetros não padrão em certas plataformas, especialmente `algorithm`, `digits` e `period`.

Implicação para a Eickrono:

- se quisermos compatibilidade forte, não devemos inventar política fora do trio clássico `SHA1 + 6 dígitos + 30 segundos`.

### Microsoft Authenticator

Pontos relevantes para o desenho:

- a documentação pública da Microsoft mostra suporte a contas não Microsoft por código QR;
- a documentação pública também cita caminho manual quando a câmera não pode escanear;
- a Microsoft suporta backup/restore de contas OTP de terceiros, mas com uma restrição importante: o restore é feito no mesmo tipo de dispositivo;
- o próprio código do Keycloak trata Microsoft Authenticator como compatível apenas com TOTP de `6 dígitos`, `HmacSHA1` e `30 segundos`.

Implicação para a Eickrono:

- se quisermos interoperabilidade real com Microsoft Authenticator, a política também precisa ficar no padrão clássico `SHA1 + 6 dígitos + 30 segundos`.

### Senhas da Apple

Pontos relevantes para o desenho:

- a Apple suporta códigos TOTP no app Senhas e no Preenchimento Automático do sistema;
- a Apple documenta uma variante própria baseada no padrão `otpauth://`, trocando apenas o esquema para `apple-otpauth://`;
- essa URL pode ser aberta por um app iOS em resposta a um toque de botão para oferecer a gravação direta do TOTP no gerenciador de senhas da Apple;
- a Apple também continua suportando configuração por código QR e por chave de configuração manual.

Implicação para a Eickrono:

- o contrato do backend pode continuar canônico em `otpauth://...` + segredo Base32;
- no iOS, o aplicativo pode derivar `apple-otpauth://...` como conveniência específica da plataforma;
- isso não substitui `código QR + chave manual` como contrato principal, porque é uma integração específica do ecossistema Apple.

### Tabela resumida de autenticadores

| Autenticadores | QR | Link (`otpauth://` e ou `apple-otpauth://`) | Observação |
| --- | --- | --- | --- |
| Google Authenticator | Sim | `otpauth://...` | Link genérico amplamente compatível com o ecossistema TOTP. |
| Senhas da Apple | Sim | `apple-otpauth://...` | Conveniência oficial da Apple para iOS e macOS, além de QR e chave manual. |
| Microsoft Authenticator | Sim | `otpauth://...` | QR é o caminho oficialmente documentado; a abertura por link deve ser tratada como tentativa genérica, não como contrato específico do app. |

Observação:

- além desses três, outros autenticadores compatíveis com TOTP podem aceitar `QR` e `otpauth://...`, mas este guia só assume oficialmente os autenticadores verificados nesta análise.

## O que recomendar para a Eickrono

### Diretriz central

A recomendação é:

1. manter o TOTP dentro do Keycloak;
2. manter a experiência nativa dentro da borda pública final de
   `autenticacao`, ainda que parte do runtime atual permaneça fisicamente
   localizada em `modulo-eickrono-autenticacao` durante a migração;
3. não usar tela web do domínio de autenticação como fluxo principal do aplicativo;
4. devolver ao aplicativo o `otpauth://...` como carga canônica de provisionamento;
5. oferecer código QR, chave manual e tentativa opcional de abertura do autenticador;
6. tratar códigos de recuperação como parte obrigatória do desenho.

### Momento da ativação

O TOTP não deve aparecer misturado ao onboarding básico de cadastro.

Ordem recomendada:

1. cadastro;
2. verificação de e-mail;
3. verificação de telefone, quando a política do sistema exigir;
4. conta liberada;
5. tela separada de `Segurança da conta` para ativação opcional de TOTP.

Se algum sistema da Eickrono quiser tornar TOTP obrigatório, a mesma tela continua existindo, mas com comportamento bloqueante antes da liberação completa das operações protegidas.

Importante:

- isso deve ser tratado como uma única tela autenticada de `Segurança da conta`;
- dentro dela, a seção de TOTP pode trocar de estado entre provisionamento, configuração manual, validação e exibição dos recovery codes;
- não é necessário modelar várias telas independentes para a ativação do TOTP na área autenticada.

### Política do sistema versus fator do usuário

É importante separar duas coisas:

- política do sistema ou cliente;
- fatores efetivamente habilitados para um usuário.

Esses dois conceitos não devem ser modelados no mesmo campo.

Exemplo:

- o aplicativo Flutter pode ter política `TOTP opcional`;
- outro cliente pode ter política `TOTP obrigatório`;
- ainda assim, cada usuário continua tendo seu próprio estado de habilitação do fator.

Por isso, um campo do tipo `mfa_habilitado` no usuário não resolve a necessidade completa.

### Por que não usar AIA/webview como solução principal

Existe um caminho mais simples no papel: abrir um fluxo OIDC com `kc_action=CONFIGURE_TOTP` e deixar o Keycloak renderizar a tela de configuração.

Isso não é o mais alinhado ao monorepo porque:

- a documentação da arquitetura atual diz explicitamente que o aplicativo não deve depender de telas do domínio de autenticação como fluxo principal;
- a borda pública do móvel hoje é a API de identidade, não o fluxo de navegador do Keycloak;
- abrir AIA/webview para TOTP introduz um desvio de experiência e de arquitetura só para esse caso;
- o produto ficaria com dois estilos de autenticação convivendo ao mesmo tempo: nativo para login/cadastro e web para MFA.

Esse caminho ainda pode existir como contingência ou para clientes web no futuro, mas não deveria ser a solução principal do aplicativo móvel.

### Proposta recomendada

#### 1. Provisionamento TOTP nativo, mas persistido no Keycloak

Fluxo sugerido:

1. o usuário já autenticado no aplicativo pede para ativar TOTP;
2. o aplicativo chama um endpoint autenticado da API de identidade;
3. a API de identidade exige reautenticação recente ou confirmação da senha atual;
4. a API de identidade chama um endpoint interno do servidor de autorização;
5. o servidor de autorização gera um segredo temporário de provisionamento e devolve:
   - `issuer`
   - `accountName`
   - `secretBase32`
   - `otpauthUri`
   - opcionalmente um `codigoQrBase64`
6. o aplicativo mostra a experiência de ativação;
7. o usuário informa o código de 6 dígitos gerado pelo autenticador;
8. a API de identidade envia a confirmação ao servidor de autorização;
9. o Keycloak valida o código e persiste a credencial OTP do usuário;
10. o backend devolve códigos de recuperação para o aplicativo exibir uma única vez.

Esse fluxo pressupõe que a conta já passou pelas verificações anteriores exigidas pelo produto.

#### 2. O aplicativo deve receber dados, não HTML

O contrato ideal entre backend e aplicativo não é "uma página pronta", e sim dados estruturados:

- `otpauthUri` para o aplicativo gerar o código QR localmente;
- `secretBase32` para cópia manual;
- `issuer` e `accountName` para texto de ajuda;
- opcionalmente, dados suficientes para o aplicativo derivar `apple-otpauth://...` no iOS sem depender de novo contrato;
- metadados de política para fins de diagnóstico e telemetria, se desejado.

Vantagens:

- a mesma resposta atende fluxo com dois dispositivos e fluxo no mesmo dispositivo;
- o aplicativo pode renderizar código QR, copiar segredo e abrir um autenticador sem depender de tema do Keycloak;
- evitamos acoplar o produto móvel ao HTML da ação obrigatória.

#### 3. Código QR deve ser canônico

Para dois dispositivos, o fluxo mais simples e mais interoperável continua sendo:

- o aplicativo mostra o código QR;
- o usuário escaneia com Google Authenticator, Microsoft Authenticator ou com o app Senhas da Apple.

#### 4. Chave manual deve existir obrigatoriamente

Para o caso em que o autenticador está no mesmo aparelho do aplicativo, ou quando a câmera não pode ser usada:

- o aplicativo deve permitir copiar a chave Base32;
- o aplicativo deve mostrar claramente `issuer` e `accountName`;
- o aplicativo deve permitir colar ou usar a configuração manual.

#### 5. Botão "abrir autenticador" é opcional, não o fluxo principal

É aceitável oferecer um botão do tipo:

- `Abrir aplicativo autenticador`
- `Adicionar ao app Senhas` no iOS

Mas com estas regras:

- o backend não deve depender de link profundo proprietário de Google ou Microsoft;
- o aplicativo pode tentar abrir o `otpauthUri` no sistema e cair em contingência se não houver aplicativo registrado;
- no iOS, o aplicativo pode oferecer um botão específico que abra `apple-otpauth://...` para integração com o app Senhas;
- a experiência sempre precisa continuar funcional com código QR e chave manual.

Observação importante:

- não encontrei documentação oficial pública suficiente para tratar um link profundo proprietário da Microsoft como contrato estável de integração para contas TOTP arbitrárias;
- para a Apple, existe documentação oficial suficiente para tratar `apple-otpauth://...` como conveniência suportada no iOS e no macOS;
- por isso, código QR e chave manual precisam continuar sendo o caminho garantido;
- a tentativa de abertura automática via `otpauth://` ou `apple-otpauth://` deve ser tratada apenas como melhoria de experiência quando o sistema operacional tiver um aplicativo associado compatível.

Conclusão prática:

- `código QR + segredo manual` é contrato;
- `abrir aplicativo autenticador` e `Adicionar ao app Senhas` são conveniências.

## Como o login deve evoluir

### Fase recomendada

A forma mais simples e alinhada à concessão direta atual é manter o endpoint de login e acrescentar suporte opcional a TOTP.

Exemplo de evolução de contrato:

- `POST /api/publica/sessoes`
  - continua recebendo `login`, `senha`, metadados do dispositivo, atestação e segurança local;
  - passa a aceitar opcionalmente `totp`.

### Comportamento esperado

Caso 1:

- usuário sem TOTP habilitado;
- fluxo segue igual ao atual.

Caso 2:

- usuário com TOTP habilitado;
- aplicativo envia login e senha sem `totp`;
- o backend devolve resposta estruturada informando que falta TOTP;
- o aplicativo mostra o campo do código e repete a mesma operação com `totp`.

Caso 3:

- usuário com TOTP habilitado;
- aplicativo envia `totp` inválido;
- o backend devolve erro específico de TOTP inválido.

### O que precisa mudar no Keycloak para isso ficar bom

O `ValidateOTP` padrão do Keycloak responde como `invalid user credentials`, o que para a Eickrono é ruim porque:

- mistura senha errada com OTP faltando;
- o runtime público atual ainda localizado em `modulo-eickrono-autenticacao`
  hoje
  traduz isso como credencial inválida genérica.

Então a recomendação é criar um fluxo customizado de concessão direta da Eickrono com um autenticador OTP customizado que:

- continue validando o TOTP no Keycloak;
- diferencie pelo menos:
  - `totp_required`
  - `totp_invalid`
  - `invalid_credentials`

Assim a API de identidade pode mapear corretamente para respostas do aplicativo sem adivinhar.

## Onde isso deve morar

### No servidor de autorização

Criar no projeto [`eickrono-autenticacao-servidor`](/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/README.md):

- um endpoint interno de provisionamento TOTP;
- um endpoint interno de confirmação TOTP;
- um endpoint interno de revogação TOTP;
- um autenticador customizado de concessão direta para diferenciar ausência ou erro de TOTP;
- ajustes nos domínios versionados para vincular o fluxo correto;
- opcionalmente, suporte oficial a códigos de recuperação já na mesma entrega.

### Política por sistema ou cliente

Se a necessidade é dizer que determinado sistema usa TOTP como opcional ou obrigatório, essa política deve morar no nível do cliente ou sistema, não no cadastro e não no usuário.

Há duas formas aceitáveis:

#### Opção mínima

Guardar a política no próprio Keycloak, como atributo do cliente, no mesmo espírito do atributo já usado para `device token`.

Exemplo recomendado:

- `eickrono.modo.totp=DESABILITADO`
- `eickrono.modo.totp=OPCIONAL`
- `eickrono.modo.totp=OBRIGATORIO`

Recomendação final deste guia:

- usar enum, não boolean, para a política de TOTP por cliente.

Exemplo imediato para o produto citado nesta discussão:

- cliente do aplicativo principal: `eickrono.modo.totp=OPCIONAL`

#### Opção mais estruturada

Criar uma tabela de política por sistema ou cliente.

Exemplo conceitual:

```text
politicas_autenticacao_cliente
- client_id
- modo_totp
- exige_email_verificado
- exige_telefone_verificado
- criado_em
- atualizado_em
```

Se a demanda imediata é só obrigatoriedade de TOTP por sistema, a opção mínima no Keycloak tende a ser suficiente e bem mais barata.

### Na API de identidade

Evoluir [`eickrono-identidade-servidor`](/Users/thiago/Desenvolvedor/flutter/eickrono-identidade-servidor/README.md) para:

- expor endpoints autenticados de ativação/desativação de TOTP;
- orquestrar o provisionamento com o servidor de autorização;
- aceitar `totp` no login público;
- traduzir erros do servidor de autorização para códigos funcionais estáveis do aplicativo;
- registrar auditoria de ativação, confirmação, falha e revogação.

### No banco da identidade

Eu não recomendo colocar um boolean simples em `cadastros_conta`, `pessoas` ou estrutura equivalente para dizer se o usuário "tem MFA".

Motivos:

- `cadastros_conta` representa processo de onboarding, não estado permanente de autenticação;
- a pessoa pode ter mais de um fator ao longo do tempo;
- a política do sistema e o estado do usuário são conceitos diferentes;
- a credencial real de TOTP continua pertencendo ao Keycloak.

Se o produto já sabe que quer evoluir para vários fatores, vale considerar desde cedo uma tabela própria de catálogo de fatores do usuário.

O modelo conceitual complementar desta ideia foi registrado em:

- [`documentacao/diagramas/modelo_fatores_autenticacao.dbml`](/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/documentacao/diagramas/modelo_fatores_autenticacao.dbml)

Exemplo conceitual:

```text
fatores_autenticacao_usuario
- id
- usuario_sub
- tipo_fator
- provedor
- identificador_referencia
- status
- criado_em
- habilitado_em
- desabilitado_em
- metadados_json
```

Valores típicos de `tipo_fator`:

- `SENHA`
- `TOTP`
- `CODIGO_EMAIL`
- `CODIGO_SMS`
- `REDE_SOCIAL`

Nada impede manter as tabelas atuais de contato e vínculo social e acrescentar as tabelas de autenticação por cima delas.

Na prática, isso tende a ser o melhor desenho:

- contatos e acessos continuam como fonte de verdade do identificador;
- fatores de autenticação passam a apontar para esses registros;
- o catálogo de fatores descreve o papel do mecanismo na autenticação;
- o segredo TOTP continua fora desse catálogo, no Keycloak.

Em outras palavras:

- `email` e `telefone` continuam existindo como contato;
- `CODIGO_EMAIL` e `CODIGO_SMS` passam a existir como fatores;
- `REDE_SOCIAL` pode continuar existindo como acesso/login;
- `TOTP` entra como segundo fator forte.

### Redes sociais

Redes sociais podem continuar existindo no modelo como fator ou forma de entrada, mas eu recomendo separar semanticamente:

- `REDE_SOCIAL` como mecanismo de login federado;
- `TOTP` como mecanismo principal de segundo fator;
- `CODIGO_EMAIL` e `CODIGO_SMS` como fatores de verificação ou recuperação, conforme a política.

Então a resposta objetiva é:

- eu não tiraria redes sociais do modelo de autenticação;
- mas eu não as trataria como equivalentes ao TOTP para satisfazer uma política de MFA forte.

Exemplo prático:

- usuário pode entrar com Google ou Apple;
- ainda assim, se a política do cliente exigir TOTP, o login social sozinho não satisfaz essa exigência;
- o fator `REDE_SOCIAL` continua útil para login federado, vinculação e catálogo de acessos, mas não substitui o papel do TOTP como segundo fator.

Observação importante:

- essa tabela não deve guardar segredo TOTP;
- ela serve como catálogo de fatores do usuário;
- a fonte de verdade da credencial TOTP e da senha continua sendo o Keycloak.

Se a primeira entrega quiser ser a menor possível, essa tabela ainda pode ser adiada e o status do TOTP pode ser derivado do Keycloak. Mas, se a intenção já é suportar e-mail, telefone, TOTP, senha e redes sociais como conjunto de fatores, a tabela própria passa a fazer bastante sentido como fundação do domínio.

### Endpoints sugeridos

Endpoints externos, já pensando no padrão do monorepo:

- `POST /identidade/fatores/totp/provisionamento`
- `POST /identidade/fatores/totp/confirmacao`
- `DELETE /identidade/fatores/totp`
- `GET /identidade/fatores`
- `GET /identidade/seguranca/politica`

Endpoint público existente que evolui:

- `POST /api/publica/sessoes`

Endpoints internos entre API e servidor de autorização:

- `POST /interno/totp/provisionamentos`
- `POST /interno/totp/provisionamentos/{id}/confirmacao`
- `DELETE /interno/totp/usuarios/{userId}`
- `GET /interno/totp/usuarios/{userId}/status`
- `GET /interno/clientes/{clientId}/politica-autenticacao`

Os nomes exatos ainda podem mudar. O importante é preservar a separação:

- API identidade para o aplicativo;
- Keycloak para a autoridade de credencial.

### Política OTP recomendada

Para reduzir risco de incompatibilidade:

- tipo: `totp`
- algoritmo: `SHA1`
- dígitos: `6`
- período: `30`
- look around window: `1`
- reusable code: `false`

Essa política é a mais segura do ponto de vista de interoperabilidade com Google Authenticator e Microsoft Authenticator.

### Recovery codes

Códigos de recuperação devem entrar na primeira versão por três motivos:

- Google e Microsoft tratam backup de formas diferentes;
- troca ou perda de aparelho é inevitável;
- reset de TOTP sem backup gera muito atrito operacional.

Recomendação:

1. habilitar códigos de recuperação no Keycloak;
2. incluí-los no fluxo de ativação do TOTP;
3. exibi-los uma única vez para o usuário;
4. exigir confirmação explícita no aplicativo de que o usuário armazenou os códigos;
5. manter uma operação clara de reset/revogação via conta autenticada e via fluxo de recuperação controlado.

### O que não fazer

- não guardar o segredo TOTP definitivo no banco do
  `modulo-eickrono-autenticacao`;
- não escrever direto nas tabelas do Keycloak;
- não tornar código QR o único caminho;
- não depender exclusivamente de link profundo proprietário para Google ou Microsoft;
- não ativar TOTP no domínio sem antes tratar o login do aplicativo;
- não devolver "credenciais inválidas" quando na verdade o fator que falta é o TOTP.

## Escopo da implementação única

Esta documentação passa a assumir uma única implementação, sem fase 1 e fase 2 separadas.

Escopo consolidado:

- configurar política TOTP por cliente no servidor de autenticação;
- usar enum de política `DESABILITADO | OPCIONAL | OBRIGATORIO`;
- manter o aplicativo principal com `TOTP = OPCIONAL`;
- criar o fluxo customizado de concessão direta com erro diferenciado;
- criar endpoints internos de provisionamento, confirmação, status e revogação;
- criar endpoints autenticados na API de identidade para segurança da conta;
- evoluir `/api/publica/sessoes` para aceitar `totp`;
- expor status do segundo fator no perfil/segurança da conta;
- habilitar códigos de recuperação;
- manter código QR e chave manual como contrato principal;
- permitir abertura via `otpauth://` e, no iOS, via `apple-otpauth://` apenas como conveniência;
- introduzir as tabelas de catálogo de fatores e seus relacionamentos com acesso social/contato, sem replicar segredo TOTP.

O recorte de banco sugerido para essa implementação única foi documentado em:

- [`documentacao/diagramas/modelo_teste_mfa_impl_unica.dbml`](/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/documentacao/diagramas/modelo_teste_mfa_impl_unica.dbml)

O recorte de fluxo, telas e integrações sugerido para essa implementação única foi documentado em:

- [`documentacao/diagramas/fluxo-sequencia-totp.md`](/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/documentacao/diagramas/fluxo-sequencia-totp.md)

## Checklist futuro de implementação

- testar ativação com Google Authenticator em Android;
- testar ativação com Google Authenticator em iOS;
- testar ativação com Microsoft Authenticator em Android;
- testar ativação com Microsoft Authenticator em iOS;
- testar ativação com app Senhas da Apple em iPhone;
- testar adição direta via `apple-otpauth://` no iOS;
- validar contingência manual sem código QR;
- validar login sem TOTP para usuário não enrolado;
- validar login com TOTP obrigatório;
- validar erro específico para TOTP inválido;
- validar códigos de recuperação;
- validar revogação e reativação;
- validar auditoria e mascaramento de logs.

## Referências

- Keycloak Server Administration Guide: https://www.keycloak.org/docs/latest/server_admin/
- Key Uri Format do Google Authenticator: https://github.com/google/google-authenticator/wiki/Key-Uri-Format
- Firebase `TotpSecret.generateQrCodeUrl` / `openInOtpApp`: https://firebase.google.com/docs/reference/kotlin/com/google/firebase/auth/TotpSecret
- Google Security Blog sobre sincronização do Google Authenticator: https://security.googleblog.com/2023/04/google-authenticator-now-supports.html
- Microsoft Support, adicionar contas no Microsoft Authenticator: https://support.microsoft.com/en-US/authenticator/how-to-add-your-accounts-to-microsoft-authenticator
- Microsoft Support, backup de contas no Microsoft Authenticator: https://support.microsoft.com/en-US/authenticator/back-up-your-accounts-in-microsoft-authenticator
- Apple Developer, Securing Logins with iCloud Keychain Verification Codes: https://developer.apple.com/documentation/AuthenticationServices/securing-logins-with-icloud-keychain-verification-codes
- Apple Support, preencha automaticamente códigos de verificação no iPhone: https://support.apple.com/pt-br/guide/iphone/ipha6173c19f/ios
- Apple Support, usar o app Senhas para criar e gerenciar senhas e códigos: https://support.apple.com/pt-br/120758
