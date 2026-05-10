# Fluxogramas dos Fluxos Publicos - Estado Atual Implementado

Este documento registra o comportamento **atual do codigo** do ecossistema de
autenticacao, para facilitar analise funcional, comparacao com a documentacao
alvo e identificacao de divergencias.

Para a revisao completa e detalhada dos fluxos atuais de cadastro, login por
senha, login social, `registro/silencioso`, recuperacao de senha e conflitos
entre runtime e alvo arquitetural, preferir:

- `guia_fluxos_login_autenticacao_app.md`

Importante:

- este documento **nao** descreve o comportamento desejado ideal;
- ele descreve o que hoje esta implementado principalmente em:
  - `eickrono-identidade-servidor`
  - `eickrono-thimisu-app`
- quando houver conflito entre este documento e um guia arquitetural de alvo,
  deve-se assumir que aqui esta o retrato do runtime/codigo vigente.

## Escopo observado

- cadastro publico
- validacao de contatos
- login publico com e-mail e senha
- desvio do app para retomada de validacao
- recuperacao de senha

## 1. Cadastro Publico Hoje

```mermaid
flowchart TD
    A[Usuario finaliza cadastro publico] --> B[POST /api/publica/cadastros]
    B --> C[Criar usuario pendente no Keycloak<br/>enabled=false<br/>emailVerified=false]
    C --> D[Persistir CadastroConta<br/>status=PENDENTE_EMAIL]
    D --> E{Telefone foi informado?}
    E -- Sim --> F[Gerar codigo de telefone]
    E -- Nao --> G[Sem codigo de telefone]
    F --> H[Enviar codigo de e-mail]
    G --> H
    H --> I{Falhou envio de e-mail?}
    I -- Sim --> J[Remover cadastro pendente<br/>Remover usuario pendente no auth]
    I -- Nao --> K[Retorna cadastro pendente]

    K --> L[Usuario confirma e-mail]
    L --> M[confirmarEmailPublico]

    M --> N{Codigo de e-mail valido?}
    N -- Nao --> O[Erro]
    N -- Sim --> P[marcarEmailConfirmado]

    P --> Q{possuiTelefoneParaValidacao?}
    Q -- Sim --> R[Retorna EMAIL_CONFIRMADO<br/>proximoPasso=VALIDAR_TELEFONE]
    Q -- Nao --> S[finalizarCadastroPublico]

    R --> T[Usuario confirma telefone]
    T --> U[confirmarTelefonePublico]
    U --> V{Codigo de telefone valido?}
    V -- Nao --> W[Erro]
    V -- Sim --> S

    S --> X[Provisionar perfil/contexto]
    X --> Y{Produto respondeu?}
    Y -- Sim --> Z[Vincular social pendente se existir]
    Y -- Nao --> ZA[Registrar pendencia do produto]
    Z --> AB[Keycloak.confirmarEmailEAtivarUsuario]
    ZA --> AB
    AB --> AC[Conta central liberada]
```

### Leitura objetiva

- hoje, no cadastro publico, `telefone informado` vira `telefone obrigatorio`;
- isso nao nasce de uma politica de produto separada;
- isso nasce do desenho atual do contrato e do dominio:
  - o request publico exige `telefone`;
  - a confirmacao considera `telefoneObrigatorio = possuiTelefoneParaValidacao()`.
- quando o produto falha nessa etapa final, a pendencia nao bloqueia o login
  central por padrao;
- o erro do produto aparece apenas quando o app realmente precisar do backend
  do produto e ele ainda nao estiver pronto.

## 2. Login Publico Hoje

```mermaid
flowchart TD
    A[Usuario tenta login com e-mail e senha] --> B[POST /api/publica/sessoes]
    B --> C[AutenticacaoSessaoInternaServico.autenticar no Keycloak]

    C --> D{Keycloak autenticou?}
    D -- Nao --> E[traduzir erro]
    D -- Sim --> F[Buscar ContextoPessoaPerfil]

    E --> G{motivo=Account disabled<br/>ou Account is not fully set up?}
    G -- Sim --> H{Existe cadastro pendente para esse e-mail?}
    H -- Sim --> I[Retorna conta_nao_liberada<br/>com cadastroId]
    H -- Nao --> J{motivo=incompleta?}
    J -- Sim --> K[Retorna conta_incompleta]
    J -- Nao --> L[Retorna conta_desabilitada]

    G -- Nao --> M{motivo=credenciais invalidas?}
    M -- Sim --> N[Retorna credenciais_invalidas]
    M -- Nao --> O[Retorna falha_autenticacao]

    F --> P{Contexto existe?}
    P -- Nao --> Q[Retorna conta_nao_liberada]
    P -- Sim --> R{statusUsuario == LIBERADO?}
    R -- Nao --> S[Retorna conta_nao_liberada]
    R -- Sim --> T[Registrar device token]
    T --> U[Login concluido]
```

### Leitura objetiva

- o login publico tem dois gates:
  - autenticacao no Keycloak;
  - estado `LIBERADO` no contexto local;
- por isso uma conta pode autenticar no servidor de autorizacao e ainda assim
  ser bloqueada depois por contexto/status local.

## 3. App no Login Hoje

```mermaid
flowchart TD
    A[Usuario toca Login] --> B[ControladorLogin.autenticar]
    B --> C{sucesso?}

    C -- Sim --> D[Prepara banco local]
    D --> E[Vai para Home]

    C -- Nao --> F{codigoErroPublico == conta_nao_liberada?}
    F -- Nao --> G[Mostra snackbar]
    F -- Sim --> H{Existe cadastroId recuperavel?}
    H -- Nao --> G
    H -- Sim --> I[Oferece retomar validacao pendente]
    I --> J{Usuario confirma?}
    J -- Sim --> K[Abre /validacao-contatos]
    J -- Nao --> G
```

### Leitura objetiva

- o app hoje nao entra mais automaticamente em `/validacao-contatos`;
- ele oferece confirmacao antes de abrir a retomada;
- esse comportamento e apenas de UX;
- ele nao resolve por si so a causa raiz do estado da conta.

## 4. Recuperacao de Senha Hoje

```mermaid
flowchart TD
    A[Usuario inicia recuperacao] --> B[POST /api/publica/recuperacoes-senha]
    B --> C[Criar fluxo de recuperacao]
    C --> D{Usuario real existe?}
    D -- Sim --> E[Enviar codigo por e-mail]
    D -- Nao --> F[Resposta neutra]

    E --> G[Usuario confirma codigo]
    F --> G
    G --> H[confirmarCodigo]

    H --> I{Codigo valido?}
    I -- Nao --> J[Erro]
    I -- Sim --> K[Marcar codigoJaConfirmado=true]

    K --> L[Usuario define nova senha]
    L --> M[redefinirSenha]
    M --> N[Keycloak.redefinirSenha]
    N --> O[Revogar device tokens / encerrar sessoes]
    O --> P[marcarSenhaRedefinida]
```

### Leitura objetiva

- a recuperacao de senha hoje:
  - valida codigo;
  - redefine senha;
  - revoga sessoes/tokens ativos;
- ela **nao** libera conta pendente de cadastro;
- ela **nao** ativa usuario desabilitado no servidor de autorizacao;
- ela **nao** promove `statusUsuario` para `LIBERADO`.

## 5. Divergencias Objetivas Encontradas

```mermaid
flowchart TD
    A[Intencao funcional esperada] --> B[Telefone so quando politica exigir]
    A --> C[Recuperacao pode reconciliar conta pendente]
    A --> D[LIBERADO deve refletir regras claras de transicao]

    B --> E[Codigo atual exige telefone no contrato]
    B --> F[Telefone informado vira obrigatorio]

    C --> G[Codigo atual nao faz recuperacao liberar ou reconciliar cadastro]
    C --> H[Recuperacao so confirma codigo e troca senha]

    D --> I[Login publico aceita apenas LIBERADO]
    D --> J[Fluxo social/device aceita LIBERADO ou ATIVO]
```

## 6. Achados de Analise

### Telefone

- o desenho alvo em outros documentos fala `telefone quando a politica exigir`;
- o codigo atual do cadastro publico nao implementa uma politica separada;
- ele implementa esta regra pratica:
  - se o cadastro tiver telefone, o telefone entra como canal obrigatorio.

### Recuperacao de senha

- o codigo atual trata recuperacao como fluxo separado de cadastro pendente;
- nao existe hoje reconciliacao explicita entre:
  - conta pendente/incompleta;
  - validacao de codigo de recuperacao;
  - liberacao final da conta.

### Estado do usuario

- o significado de `LIBERADO` e `ATIVO` ainda nao esta uniforme em todos os
  fluxos;
- no login publico por senha, a verificacao final exige `LIBERADO`;
- em parte do fluxo social/dispositivo, `ATIVO` tambem pode ser aceito.

## 7. Uso Recomendado Deste Documento

Este documento deve ser usado como base para:

- revisar o que o runtime faz hoje;
- comparar `estado atual` x `estado alvo`;
- desenhar a maquina de estados canonica antes de corrigir comportamento;
- evitar novas correcoes locais de UX sem antes fechar a regra de negocio.
