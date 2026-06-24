# Padrão Eickrono de Códigos de Erro, Correlação, Logs e Auditoria

Este documento consolida o padrão canônico para:

- códigos internos de erro da Eickrono;
- correlação entre app, autenticação, identidade e backend;
- logs técnicos mascarados;
- traces e observabilidade;
- auditoria de fatos de segurança e negócio;
- erros de frontend, UX e integração externa.

O objetivo não é mapear cada função do código.
O objetivo é tornar rastreável cada fluxo relevante nas bordas reais do sistema, inclusive quando as interações forem assíncronas.

## Decisões já consolidadas

Este padrão consolida decisões que já vinham aparecendo em documentos e código do ecossistema:

- contratos de erro do app e dos backends precisam convergir para `codigo`, `mensagem` e `detalhes`;
- o app não deve depender de strings soltas para decidir UX;
- logs precisam permanecer mascarados;
- auditoria de segurança e de processo é append-only;
- números de etapa dos fluxos ajudam a cruzar logs, traces OTEL e auditorias;
- erros de app e de UX também precisam ser mapeados, não só erros HTTP ou OAuth.

Referências já alinhadas com essa direção:

- [guia-tecnico-junior.md](/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/documentacao/guia-tecnico-junior.md)
- [guia-operacao-producao.md](/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/documentacao/guia-operacao-producao.md)
- [casos-uso-sequencia.md](/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/documentacao/diagramas/casos-uso-sequencia.md)
- [arquitetura_alvo_ecossistema_autenticacao.md](/Users/thiago/Desenvolvedor/flutter/eickrono-thimisu/eickrono-thimisu-app/docs/arquitetura_alvo_ecossistema_autenticacao.md)
- [uml_autenticacao_cadastro_social_ecossistema.md](/Users/thiago/Desenvolvedor/flutter/eickrono-thimisu/eickrono-thimisu-app/docs/uml_autenticacao_cadastro_social_ecossistema.md)
- [logs.md](/Users/thiago/Desenvolvedor/flutter/eickrono-thimisu/eickrono-thimisu-app/docs/logs.md)

## O que não fazer

Não adote este padrão:

- um código de erro por função interna;
- logs com senha, token bruto, authorization code ou segredo;
- correlação apenas por mensagem textual;
- parsing de UX por `contains("erro x")`;
- uso do mesmo identificador para tipo do erro e ocorrência concreta.

Isso degrada a manutenção, vaza dado sensível e não resolve a correlação assíncrona entre sistemas.

## O que um erro Eickrono precisa ter

Todo erro relevante do ecossistema deve ser modelado em três camadas:

1. `error_code`
2. `occurrence_id`
3. `flow_id`

### `error_code`

Identifica o tipo estável do erro.
Exemplos:

- `AP-UX-CRT`
- `AP-AU-CAN`
- `AU-GG-RDM`
- `AU-AA-ICL`
- `ID-DTK-INV`
- `TB-INT-404`

### `occurrence_id`

Identifica a ocorrência concreta.
É diferente do código.
Cada erro emitido ganha um identificador próprio.

Exemplo:

```text
err_01JV9P8YDFX2M1R4C7M5NQ8T2K
```

### `flow_id`

Identifica a jornada de negócio atravessando múltiplos requests, callbacks e integrações assíncronas.

Exemplos de fluxo que precisam de `flow_id`:

- login por senha;
- login social;
- cadastro público;
- recuperação de senha;
- bootstrap de dispositivo;
- App Attest;
- reautenticação sensível;
- vinculação social;
- provisionamento identidade -> thimisu.

Exemplo:

```text
flw_01JV9P8GX4R6R3Q2N7M8Q5M1XA
```

## Formato canônico do código de erro

Formato recomendado:

```text
<ORIGEM>-<DOMINIO>-<MOTIVO>
```

Regras:

- todos os blocos em maiúsculas;
- blocos curtos, estáveis e alfanuméricos;
- separação por `-`;
- o código não deve carregar usuário, dispositivo, data ou segredo;
- o código deve sobreviver a refactors internos.

### Bloco 1: origem

- `AP`: app
- `AU`: autenticação / Keycloak
- `ID`: identidade
- `TB`: thimisu-backend
- `CT`: contas

### Bloco 2: domínio

- `UX`: UX ou estado de tela
- `NET`: rede
- `OID`: protocolo OIDC/OAuth
- `PUB`: fluxo público
- `CAD`: cadastro
- `REC`: recuperação de senha e códigos
- `DTK`: device token
- `ATT`: atestação
- `SOC`: login social
- `LNK`: vínculo social
- `BIO`: biometria e presença local
- `CFG`: configuração e bootstrap
- `MTL`: malha mTLS
- `GG`: Google
- `AA`: Apple
- `FB`: Facebook
- `LI`: LinkedIn
- `INT`: integração interna
- `VAL`: validação funcional
- `SES`: sessão

### Bloco 3: motivo

- `RDM`: redirect mismatch
- `ICL`: invalid client
- `CAN`: cancelado pelo usuário
- `CRT`: falha crítica de UX/app
- `INV`: inválido
- `EXP`: expirado
- `TMO`: timeout
- `OFF`: offline
- `CFG`: configuração ausente ou inconsistente
- `DSB`: desabilitado
- `MIS`: ausente
- `DGD`: sessão degradada
- `PRV`: erro de provisionamento
- `UNL`: conta não liberada
- `INC`: conta incompleta
- `CRD`: credenciais inválidas
- `USR`: usuário indisponível
- `DSP`: cadastro indisponível
- `SEP`: conta separada obrigatória
- `RAT`: limite de tentativa ou rate limit
- `SNC`: sincronização falhou
- `CFL`: conflito
- `NEM`: sem e-mail utilizável
- `RFR`: refresh negado
- `ABS`: ausente no contexto esperado
- `HSK`: handshake falhou

## Envelope canônico de erro

O código sozinho não basta.
Toda emissão precisa carregar um envelope mínimo.

### Campos obrigatórios

| Campo | Finalidade |
| --- | --- |
| `timestamp` | Momento UTC da ocorrência |
| `environment` | `dev`, `stg`, `prd` |
| `service` | `app`, `autenticacao`, `identidade`, `thimisu-backend`, etc |
| `error_code` | Tipo estável do erro |
| `occurrence_id` | Identificador único da ocorrência |
| `flow_id` | Jornada de negócio correlacionada |
| `trace_id` | Correlação técnica do request/chamada |
| `severity` | `debug`, `info`, `warn`, `error`, `fatal` |
| `retryable` | Se o cliente pode tentar novamente |
| `message` | Mensagem técnica controlada |
| `root_cause` | Motivo técnico normalizado |

### Campos contextuais recomendados

| Campo | Finalidade |
| --- | --- |
| `user_id` | Usuário autenticado, quando existir |
| `anonymous_user_key` | Usuário ainda anônimo |
| `device_id` | Dispositivo lógico do ecossistema |
| `installation_id` | Instalação do app |
| `session_id` | Sessão lógica do app ou do OIDC |
| `provider` | `google`, `apple`, `facebook`, `linkedin`, etc |
| `endpoint` | Endpoint HTTP ou callback |
| `use_case` | Nome funcional do fluxo |
| `http_status` | Status HTTP final |
| `provider_error_code` | Código cru do provedor externo |
| `expected_result` | Estado esperado na borda |
| `observed_result` | Estado realmente observado |

### Campos opcionais especializados

| Campo | Quando usar |
| --- | --- |
| `app_version` | App móvel e desktop |
| `build_number` | App iOS/Android |
| `bundle_id` | iOS |
| `package_name` | Android |
| `issuer` | OIDC |
| `audience` | OIDC/resource server |
| `client_id` | OIDC/broker social |
| `redirect_uri` | OAuth/OIDC |
| `screen_name` | Erro de UX no app |
| `action_name` | Gatilho explícito do usuário |
| `details` | Estrutura segura e mascarada |

## Correlação em sistemas assíncronos

Como app, autenticação, identidade e backend interagem de forma assíncrona, a correlação precisa ser feita em dois níveis.

### 1. Correlação técnica

Usa:

- `trace_id`
- `span_id`
- `request_id`

Serve para amarrar:

- um request HTTP;
- uma chamada interna;
- um erro em um único salto de execução.

### 2. Correlação de negócio

Usa:

- `flow_id`
- `session_id`
- `device_id`
- `user_id` ou `anonymous_user_key`

Serve para amarrar:

- login iniciado no app;
- callback no Keycloak;
- emissão de token;
- chamada à identidade;
- bootstrap de dispositivo;
- falha renderizada ao usuário.

### Regra prática

- `trace_id` muda por request e por salto técnico;
- `flow_id` atravessa toda a jornada;
- `occurrence_id` identifica cada erro individual.

## Onde instrumentar

Não instrumente tudo.
Instrumente as bordas que mudam o estado do fluxo ou a decisão de UX.

### No app

- abertura do fluxo de login;
- callback OIDC;
- cancelamento de login social;
- falha crítica de inicialização;
- falha de App Attest;
- falha de bootstrap de dispositivo;
- erro que muda a navegação ou bloqueia a UX;
- tradução de erro remoto para mensagem de tela.

### Na autenticação

- broker social;
- emissão de token;
- refresh com `device_token`;
- TOTP/MFA;
- PAR/JAR/JARM;
- `client_credentials`;
- validação de sessão interna;
- erro de callback de provedor.

### Na identidade

- fluxo público;
- fluxo autenticado;
- `DeviceTokenFilter`;
- bootstrap de dispositivo;
- recuperação de senha;
- integração com `thimisu-backend`;
- `ControllerAdvice` e erros estruturados.

### No thimisu-backend

- integrações internas com identidade;
- endpoints protegidos;
- falha de provisionamento;
- inconsistência de contexto autenticado;
- política de sessão degradada.

## Erros de frontend e UX também entram

Este padrão cobre erros técnicos e funcionais do app.

Exemplos que precisam de código próprio:

- tela crítica de falha inesperada;
- cancelamento de login social;
- erro de conectividade sem navegação indevida;
- falha de discovery OIDC;
- falha de carregamento de configuração;
- conflito entre sessão local e sessão remota;
- tentativa de rota autenticada sem `device_token` utilizável;
- erro funcional tipado traduzido em modal/snackbar.

### Exemplos de códigos de app

| Código | Significado |
| --- | --- |
| `AP-UX-CRT` | falha crítica de UX ou inicialização |
| `AP-NET-DSC` | falha de discovery/config remota |
| `AP-AU-CAN` | login social cancelado pelo usuário |
| `AP-SES-DGD` | sessão degradada sem token de dispositivo utilizável |
| `AP-ATT-MIS` | atestação ausente quando obrigatória |
| `AP-INT-PRV` | erro de provisionamento refletido na UX |

## Logs, traces e auditoria não são a mesma coisa

## Modelo unificado mínimo entre logs, traces e auditoria

Os três canais não têm a mesma finalidade, mas devem compartilhar uma espinha dorsal comum para correlação.

### Campos comuns obrigatórios

| Campo | Log técnico | Trace | Auditoria |
| --- | --- | --- | --- |
| `timestamp` | obrigatório | obrigatório | obrigatório |
| `environment` | obrigatório | obrigatório | obrigatório |
| `service` | obrigatório | obrigatório | obrigatório |
| `error_code` | obrigatório quando houver erro | obrigatório quando houver erro | obrigatório quando houver erro relevante |
| `flow_id` | obrigatório em fluxos de negócio | obrigatório em fluxos de negócio | obrigatório em fluxos auditáveis |
| `trace_id` | obrigatório quando houver request/chamada | obrigatório | recomendado |
| `user_id` ou `anonymous_user_key` | recomendado | recomendado | obrigatório quando houver identidade conhecida ou inferível |
| `device_id` | recomendado | recomendado | obrigatório quando a decisão depender do dispositivo |
| `session_id` | recomendado | recomendado | recomendado |
| `provider` | obrigatório quando envolver social/OIDC/provedor externo | obrigatório quando envolver social/OIDC/provedor externo | obrigatório quando o fato auditável depender do provedor |
| `http_status` | recomendado | recomendado | opcional |
| `retryable` | recomendado | opcional | recomendado |

### Regra de especialização por canal

- log técnico:
  - leva contexto operacional curto;
  - pode conter stack trace mascarado;
  - privilegia troubleshooting rápido.
- trace:
  - privilegia latência, spans e causalidade;
  - usa atributos curtos e estáveis;
  - não substitui auditoria.
- auditoria:
  - registra o fato de segurança, negócio ou conformidade;
  - deve ser append-only;
  - não depende de stack trace nem de texto livre para reconstrução.

### Exemplo de mesmo erro nos três canais

Para `AU-GG-RDM`:

- log técnico:
  - `error_code=AU-GG-RDM`
  - `flow_id=...`
  - `trace_id=...`
  - `provider=google`
  - `redirect_uri=https://oidc-stg.eickrono.store/realms/eickrono/broker/google/endpoint`
- trace:
  - span `broker_google_callback`
  - atributo `error_code=AU-GG-RDM`
  - atributo `provider=google`
  - atributo `root_cause=redirect_uri_mismatch`
- auditoria:
  - fato `login_social_google_negado`
  - `error_code=AU-GG-RDM`
  - `flow_id=...`
  - `anonymous_user_key=...`
  - `device_id=...`

### Log técnico

Serve para diagnóstico operacional.

Características:

- estruturado;
- rápido;
- mascarado;
- pode ter alto volume;
- útil para debugging e observabilidade.

### Trace

Serve para reconstruir o caminho de execução.

Características:

- usa `trace_id` e `span_id`;
- mostra latência entre serviços;
- amarra app, auth, identidade e backend;
- é o lugar natural para tempo de chamada e gargalo.

### Auditoria

Serve para registrar fato de segurança, negócio ou conformidade.

Características:

- append-only;
- sem sobrescrita;
- mais estável;
- não deve depender de texto livre;
- precisa de contexto suficiente para suporte, fraude e reconciliação.

### Regra prática

- nem todo log vira auditoria;
- nem toda auditoria precisa de stack trace;
- todo erro relevante de segurança precisa de auditoria ou de decisão explícita de não auditar.

## Regras de mascaramento

Nunca registrar em log ou auditoria:

- senha;
- token completo;
- authorization code;
- refresh token;
- client secret;
- JWT bruto;
- código TOTP;
- segredo criptográfico;
- payload sensível completo de provedor externo.

Registrar apenas:

- `sub`;
- `aud`;
- `iss`;
- `azp`;
- `client_id`;
- `redirect_uri`;
- hash, prefixo ou fingerprint quando necessário;
- e-mail mascarado;
- identificador externo mascarado.

## Estrutura mínima de resposta HTTP de erro

Quando o erro cruzar uma borda HTTP controlada pela Eickrono, a resposta deve convergir para:

```json
{
  "codigo": "ID-DTK-INV",
  "mensagem": "Token de dispositivo invalido.",
  "detalhes": {
    "retryable": false
  },
  "correlationId": "flw_01JV9P8GX4R6R3Q2N7M8Q5M1XA"
}
```

Regras:

- `codigo` é obrigatório;
- `mensagem` é controlada;
- `detalhes` é estruturado e seguro;
- `correlationId` deve apontar ao menos para o `flow_id`.

## Eventos canônicos recomendados

### App

- `login_iniciado`
- `login_social_cancelado`
- `erro_ux_critico`
- `app_attest_falhou`
- `sessao_degradada_detectada`

### Autenticação

- `oidc_callback_recebido`
- `token_emitido`
- `refresh_negado`
- `broker_google_redirecionado`
- `broker_apple_callback_falhou`

### Identidade

- `cadastro_liberado`
- `token_dispositivo_emitido`
- `token_dispositivo_invalidado`
- `integracao_thimisu_falhou`

### Thimisu-backend

- `provisionamento_contexto_falhou`
- `perfil_remoto_inexistente`
- `resolucao_social_pendente`

## Compatibilidade com códigos públicos já ativos

Hoje o app já traduz códigos públicos legados em `snake_case`.
Eles não devem ser quebrados de imediato.

A recomendação é:

- manter o código público atual na borda enquanto ele já estiver em uso;
- registrar em log, trace e auditoria também o código canônico Eickrono;
- migrar a UI para o código canônico apenas quando a borda e os testes estiverem alinhados.

### Mapeamento inicial de compatibilidade

| Código público atual | Código canônico sugerido | Observação |
| --- | --- | --- |
| `falha_rede` | `AP-NET-TMO` | o app já traduz isso como falha técnica de conectividade |
| `conta_nao_liberada` | `ID-PUB-UNL` | já aparece em fluxo público e em testes |
| `conta_incompleta` | `ID-PUB-INC` | já traduzido no app |
| `conta_desabilitada` | `ID-PUB-DSB` | já traduzido no app |
| `credenciais_invalidas` | `ID-PUB-CRD` | já traduzido no app |
| `usuario_indisponivel` | `ID-CAD-USR` | usado no cadastro |
| `cadastro_nao_disponivel` | `ID-CAD-DSP` | usado no cadastro |
| `cadastro_email_indisponivel` | `ID-CAD-EML` | falha ao enviar código de confirmação por e-mail |
| `conta_separada_obrigatoria` | `ID-CAD-SEP` | usado no cadastro |
| `recuperacao_email_indisponivel` | `ID-REC-EML` | falha ao enviar código de recuperação por e-mail |

## Catálogo operacional inicial (v1)

Este catálogo inicial já é suficientemente grande para cobrir os fluxos públicos, autenticados, sociais, de dispositivo, App Attest e provisionamento interno.

### App e UX

| Código | Camada | Uso principal | Retryável |
| --- | --- | --- | --- |
| `AP-UX-CRT` | app | tela crítica de falha inesperada | não |
| `AP-UX-FLW` | app | fluxo da tela entrou em estado inválido | não |
| `AP-CFG-DSC` | app | falha de discovery OIDC ou config remota | sim |
| `AP-NET-TMO` | app | timeout ou falha de rede | sim |
| `AP-NET-OFF` | app | operação iniciada sem conectividade suficiente | sim |
| `AP-AU-CAN` | app | login social cancelado pelo usuário | sim |
| `AP-AU-CBK` | app | callback OIDC não conseguiu ser concluído | sim |
| `AP-SES-DGD` | app | sessão degradada sem `device_token` utilizável | não |
| `AP-SES-ABS` | app | sessão esperada não foi encontrada localmente | sim |
| `AP-DTK-MIS` | app | rota exigia `device_token` e ele não estava presente | não |
| `AP-DTK-REV` | app | backend sinalizou revogação de `device_token` | não |
| `AP-BIO-CAN` | app | biometria ou prova local foi cancelada | sim |
| `AP-BIO-INV` | app | biometria disponível, mas sem autorização para destravar o fluxo | não |
| `AP-ATT-MIS` | app | atestação obrigatória ausente no contexto | não |
| `AP-INT-PRV` | app | erro de provisionamento refletido na UX | depende |

### Autenticação e OIDC

| Código | Camada | Uso principal | Retryável |
| --- | --- | --- | --- |
| `AU-OID-PKC` | autenticação | PKCE ausente ou inválido | não |
| `AU-OID-STA` | autenticação | `state` ausente ou inconsistente | não |
| `AU-OID-NON` | autenticação | `nonce` inválido ou ausente | não |
| `AU-OID-RFR` | autenticação | refresh negado pelo servidor de autorização | depende |
| `AU-OID-DTK` | autenticação | refresh negado por `device_token` inválido | não |
| `AU-GG-ICL` | autenticação | Google `invalid_client` | não |
| `AU-GG-RDM` | autenticação | Google `redirect_uri_mismatch` | não |
| `AU-GG-CFG` | autenticação | Google configurado de forma inconsistente | não |
| `AU-AA-ICL` | autenticação | Apple `invalid_client` | não |
| `AU-AA-CFG` | autenticação | Apple configurada de forma inconsistente | não |
| `AU-AA-CBK` | autenticação | callback Apple falhou depois do retorno do provedor | depende |
| `AU-FB-DSB` | autenticação | Facebook desabilitado no ambiente | não |
| `AU-FB-CFG` | autenticação | Facebook sem `App Domain` ou callback correto | não |
| `AU-LI-CFG` | autenticação | LinkedIn com configuração incompleta | não |
| `AU-SOC-CAN` | autenticação | provedor social retornou cancelamento | sim |
| `AU-SOC-NEM` | autenticação | token social voltou sem e-mail utilizável | não |
| `AU-MFA-MIS` | autenticação | MFA obrigatório e fator não apresentado | não |
| `AU-MFA-INV` | autenticação | código TOTP ou fator equivalente inválido | sim |

### Identidade e borda pública/autenticada

| Código | Camada | Uso principal | Retryável |
| --- | --- | --- | --- |
| `ID-PUB-UNL` | identidade | conta não liberada | não |
| `ID-PUB-INC` | identidade | conta incompleta | não |
| `ID-PUB-DSB` | identidade | conta desabilitada | não |
| `ID-PUB-CRD` | identidade | credenciais inválidas | sim |
| `ID-CAD-USR` | identidade | usuário indisponível no cadastro | não |
| `ID-CAD-DSP` | identidade | cadastro indisponível | depende |
| `ID-CAD-SEP` | identidade | conta separada obrigatória | não |
| `ID-COD-INV` | identidade | código de confirmação inválido | sim |
| `ID-COD-EXP` | identidade | código de confirmação expirado | sim |
| `ID-COD-RAT` | identidade | limite de tentativas ou reenvio | sim |
| `ID-REC-ABS` | identidade | fluxo de recuperação não encontrado | depende |
| `ID-REC-EXP` | identidade | código de recuperação expirado | sim |
| `ID-REC-RAT` | identidade | limite de tentativas na recuperação | sim |
| `ID-DTK-REQ` | identidade | `X-Device-Token` obrigatório e ausente | não |
| `ID-DTK-INV` | identidade | `device_token` inválido | não |
| `ID-DTK-EXP` | identidade | `device_token` expirado | não |
| `ID-DTK-REV` | identidade | `device_token` revogado | não |
| `ID-ATT-REQ` | identidade | atestação obrigatória e ausente | não |
| `ID-ATT-INV` | identidade | atestação inválida ou incompatível | não |
| `ID-SOC-NEM` | identidade | JWT social sem e-mail utilizável | não |
| `ID-SOC-PND` | identidade | resolução social pendente | depende |
| `ID-SOC-SNC` | identidade | sincronização de vínculo social falhou | depende |
| `ID-INT-THI` | identidade | falha ao integrar com `thimisu-backend` | depende |
| `ID-AU-PRV` | identidade | erro de provisionamento autenticado | depende |

### Thimisu-backend e integrações internas

| Código | Camada | Uso principal | Retryável |
| --- | --- | --- | --- |
| `TB-INT-404` | thimisu-backend | perfil ou recurso remoto inexistente | depende |
| `TB-INT-CFL` | thimisu-backend | conflito durante provisionamento | depende |
| `TB-INT-PRV` | thimisu-backend | falha de provisionamento interno | depende |
| `TB-INT-CTX` | thimisu-backend | contexto autenticado inconsistente | não |
| `TB-MTL-HSK` | thimisu-backend | falha de handshake `mTLS` | depende |
| `TB-MTL-TRS` | thimisu-backend | truststore ou identidade remota inconsistente | não |
| `TB-SES-DGD` | thimisu-backend | backend recebeu sessão degradada para rota sensível | não |
| `TB-SOC-PND` | thimisu-backend | contexto social ainda pendente de resolução | depende |

### Contas e APIs financeiras internas

| Código | Camada | Uso principal | Retryável |
| --- | --- | --- | --- |
| `CT-AUD-SCP` | contas | escopo insuficiente para operação financeira | não |
| `CT-DTK-REQ` | contas | `X-Device-Token` ausente em rota protegida | não |
| `CT-DTK-INV` | contas | `device_token` inválido em consulta financeira | não |
| `CT-INT-LED` | contas | falha ao reconciliar extrato ou trilha interna | depende |

## Regra para adoção incremental

Use esta sequência:

1. manter o código público legado se ele já for traduzido pela UI;
2. registrar internamente o código canônico novo no envelope;
3. expor `codigo`, `mensagem` e `detalhes` estruturados nas novas bordas;
4. migrar a UI do app para o código canônico quando os contratos antigos deixarem de ser necessários.

## Exemplo completo de envelope

```json
{
  "timestamp": "2026-04-27T15:22:31Z",
  "environment": "stg",
  "service": "autenticacao",
  "error_code": "AU-GG-RDM",
  "occurrence_id": "err_01JV9T7BME5KH1AFV0M4A9K8JQ",
  "flow_id": "flw_01JV9T6B36N0Q5M9J9R5T0ZY7A",
  "trace_id": "9fd2f6e94fbbf302bfbb9f4f53d5f2b0",
  "severity": "error",
  "retryable": false,
  "message": "Falha no callback do Google por redirect URI divergente.",
  "root_cause": "redirect_uri_mismatch",
  "user_id": null,
  "anonymous_user_key": "anon_8f44b2",
  "device_id": "dev_7f31a1",
  "installation_id": "ios_install_182d",
  "session_id": "ses_1Q2W3E",
  "provider": "google",
  "endpoint": "/realms/eickrono/broker/google/endpoint",
  "use_case": "login_social",
  "http_status": 400,
  "expected_result": "redirecionar de volta ao app com sucesso",
  "observed_result": "provedor recusou o callback",
  "details": {
    "client_id": "<google-web-client-id>.apps.googleusercontent.com",
    "redirect_uri": "https://oidc-stg.eickrono.store/realms/eickrono/broker/google/endpoint"
  }
}
```

## Persistência recomendada

Use três camadas complementares:

1. logs estruturados;
2. traces OTEL;
3. trilhas de auditoria append-only.

Quando o fluxo for sensível, cada camada deve compartilhar pelo menos:

- `flow_id`;
- `trace_id`;
- `error_code`;
- `user_id` ou `anonymous_user_key`;
- `device_id`, quando aplicável.

## Regra operacional para novos fluxos

Nenhum fluxo novo deve entrar no ecossistema sem responder estas perguntas:

1. qual é o `error_code` estável para cada falha relevante;
2. qual `flow_id` cruza app, auth, identidade e backend;
3. qual resposta HTTP ou UX será exibida;
4. quais campos vão para log;
5. quais fatos precisam de auditoria;
6. quais dados precisam ser mascarados;
7. como o suporte vai localizar a ocorrência a partir do código e do `flow_id`.

## Relação com o app móvel

No app, isso implica:

- traduzir erros remotos por `codigo`, não por texto;
- persistir log estruturado local em `logs/aplicativo.log`;
- incluir erros de UX e de conectividade no mesmo catálogo canônico;
- propagar `flow_id`, `device_id`, `installation_id` e versão do app nos requests e nos eventos locais.

O guia operacional de extração do log do app continua em:

- [logs.md](/Users/thiago/Desenvolvedor/flutter/eickrono-thimisu/eickrono-thimisu-app/docs/logs.md)

## Status desta decisão

Este documento passa a ser a referência canônica para:

- modelagem de erro público e autenticado;
- correlação assíncrona entre sistemas;
- observabilidade;
- auditoria;
- mapeamento de erro de app e UX.

Quando um serviço ainda não estiver devolvendo `codigo` e `detalhes` estruturados, isso deve ser tratado como dívida explícita de contrato, não como liberdade para voltar a texto solto.
