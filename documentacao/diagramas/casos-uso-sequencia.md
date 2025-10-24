# Diagramas de sequência por caso de uso

Este documento apresenta os principais fluxos ponta a ponta da plataforma **Eickrono Autenticação**, cada um descrito com um diagrama de sequência (sintaxe Mermaid) e uma breve explicação operacional. Utilize-o como referência para entender quais serviços são acionados em cada cenário e como as respostas encadeiam novas chamadas.

---

## Caso 1 – Login inicial do app móvel (Authorization Code + PKCE)

```mermaid
sequenceDiagram
    autonumber
    participant U as Pessoa usuária
    participant App as App Flutter (público)
    participant CF as Cloudflare (WAF/mTLS)
    participant ALB as AWS ALB/NLB
    participant KC as Keycloak (Servidor de autorização)
    participant SM as AWS Secrets Manager
    participant KMS as AWS KMS
    participant DBK as PostgreSQL (schema keycloak)
    participant APII as API Identidade
    participant DBD as PostgreSQL (schema identidade)
    participant OTEL as OTEL Collector

    %% Solicitação de login
    U->>App: 1. Solicita autenticação
    App->>CF: 2. Authorization request (code_challenge, state, nonce)
    CF->>ALB: 3. Encaminha requisição (WAF + mTLS Origin Pull)
    ALB->>KC: 4. Redireciona para /auth do realm dev

    %% Bastidores Keycloak
    KC->>SM: 5. Carrega segredos (credenciais DB, certificados)
    KC->>KMS: 6. Obtém chaves para assinatura JARM/JWT
    KC->>DBK: 7. Valida cliente + políticas MFA
    KC-->>App: 8. Retorna autorização (JARM com authorization_code)

    %% Troca de código por token
    App->>CF: 9. Chamada /token (code + code_verifier)
    CF->>ALB: 10. Encaminha (origem protegida)
    ALB->>KC: 11. Keycloak valida PKCE + nonce
    KC->>DBK: 12. Registra grant + auditoria
    KC-->>App: 13. Retorna tokens (ID/Access/Refresh) com escopos FAPI
    KC->>OTEL: 14. Exporta métricas/traces

    %% Uso do token na API Identidade
    App->>CF: 15. GET /identidade/perfil (Bearer access token)
    CF->>ALB: 16. Reenvia após rate-limit/WAF
    ALB->>APII: 17. Roteia para API Identidade
    APII->>KC: 18. Valida assinatura token (JWKS cache > fallback JWKS endpoint)
    APII->>DBD: 19. Consulta dados de perfil
    APII->>OTEL: 20. Envia métricas/traces
    APII-->>App: 21. Resposta com dados do perfil
```

**Resumo:** o aplicativo inicia o fluxo PKCE, Keycloak autentica a pessoa usuária e devolve tokens. O app consome a API de Identidade usando o access token, que é validado pelos JWKS do Keycloak. Secrets Manager e KMS sustentam credenciais e chaves, enquanto OTEL registra observabilidade. Esse caso cobre o “primeiro acesso” típico.

---

## Caso 2 – Registro e confirmação de dispositivo móvel

```mermaid
sequenceDiagram
    autonumber
    participant U as Pessoa usuária
    participant App as App Flutter
    participant APII as API Identidade
    participant DB as PostgreSQL (schema identidade)
    participant SMS as Provedor SMS
    participant Email as Provedor E-mail
    participant Job as Scheduler (expiração)
    participant KC as Keycloak (SPI de provisionamento)

    U->>App: 1. Informa e-mail, telefone e fingerprint do aparelho
    App->>APII: 2. POST /identidade/dispositivos/registro
    APII->>DB: 3. Cria RegistroDispositivo (status PENDENTE, expira +9h)
    APII->>DB: 4. Gera dois CodigoVerificacao (SMS/EMAIL)
    APII->>SMS: 5. Dispara código por SMS (stub em dev/hml)
    APII->>Email: 6. Dispara código por e-mail
    APII-->>App: 7. Retorna registroId + expiraEm

    %% Confirmação de códigos
    U->>App: 8. Informa códigos recebidos
    App->>APII: 9. POST /identidade/dispositivos/registro/{id}/confirmacao
    APII->>DB: 10. Valida hash + tentativas dos códigos
    APII->>DB: 11. Marca canais como VALIDADO
    APII->>DB: 12. Atualiza registro (CONFIRMADO) + gera DispositivoToken
    APII->>KC: 13. Invoca SPI para vincular dispositivo ao usuário (quando autenticado)
    APII-->>App: 14. Retorna token opaco do dispositivo

    %% Pós-processamento
    Job->>DB: 15. Varre registros vencidos (status EXPIRADO)
    APII->>DB: 16. Revoga tokens antigos do mesmo usuário (quando necessário)
```

**Resumo:** este fluxo prepara o dispositivo móvel para operações sensíveis. A API cria registros pendentes, envia códigos por SMS/E-mail e somente após a confirmação dupla libera um `DispositivoToken`. O scheduler remove registros não confirmados. Em ambientes autenticados, o SPI do Keycloak sincroniza o dispositivo no realm.

---

## Caso 3 – Transação financeira via BFF web (cliente confidencial com mTLS, PAR/JAR/JARM)

```mermaid
sequenceDiagram
    autonumber
    participant BFF as BFF Web Eickrono (cliente confidencial)
    participant CF as Cloudflare (WAF/mTLS)
    participant ALB as AWS ALB/NLB
    participant KC as Keycloak
    participant SM as AWS Secrets Manager
    participant KMS as AWS KMS
    participant DBK as PostgreSQL (schema keycloak)
    participant APIC as API Contas
    participant Cache as Cache JWKS (Caffeine)
    participant DBC as PostgreSQL (schema contas)
    participant OTEL as OTEL Collector

    %% PAR (Pushed Authorization Request)
    BFF->>CF: 1. POST /par (mTLS + JWT assinado)
    CF->>ALB: 2. Encaminha requisição
    ALB->>KC: 3. Keycloak valida assinatura e certificado do cliente
    KC-->>BFF: 4. Retorna request_uri seguro

    %% Authorization Request com request_uri
    BFF->>CF: 5. Redireciona usuário para autorização (request_uri)
    CF->>ALB: 6. Encaminha
    ALB->>KC: 7. Keycloak aplica políticas MFA/WebAuthn <br/>e apresenta consentimento
    KC-->>BFF: 8. Resposta JARM (authorization_code protegido)

    %% Token Request com binding mTLS
    BFF->>CF: 9. POST /token (code + client cert)
    CF->>ALB: 10. Encaminha requisição
    ALB->>KC: 11. Keycloak valida certificado + JARM
    KC->>SM: 12. Carrega segredos (credenciais DB, chaves)
    KC->>KMS: 13. Usa chaves para assinar tokens bound certificate
    KC->>DBK: 14. Registra auditoria do grant
    KC-->>BFF: 15. Retorna access token + refresh token com binding mTLS

    %% Consumo da API Contas
    BFF->>CF: 16. POST /contas/transacoes (mTLS + Bearer)
    CF->>ALB: 17. Encaminha após rate limit
    ALB->>APIC: 18. Entrega requisição
    APIC->>Cache: 19. Verifica JWKS (cache hit?)
    alt JWKS expirado
        APIC->>KC: 19a. Download JWKS
        KC-->>APIC: 19b. JWKS atualizada
        APIC->>Cache: 19c. Atualiza cache local
    end
    APIC->>DBC: 20. Persiste transação e auditoria
    APIC->>OTEL: 21. Exporta métricas/traces
    APIC-->>BFF: 22. Confirmação da transação
```

**Resumo:** o BFF, como cliente confidencial, utiliza PAR/JAR/JARM com mTLS. Os tokens emitidos por Keycloak são vinculados ao certificado, garantindo proteção ao serem enviados para a API de Contas. O fluxo finaliza com operação persistida no banco e métricas exportadas.

---

## Caso 4 – Integração interna/batch utilizando client credentials

```mermaid
sequenceDiagram
    autonumber
    participant Job as Job interno / API parceira
    participant CF as Cloudflare (mTLS obrigatório)
    participant ALB as AWS ALB/NLB
    participant KC as Keycloak
    participant DBK as PostgreSQL (schema keycloak)
    participant APII as API Identidade
    participant APIC as API Contas
    participant Cache as Cache JWKS
    participant DBI as PostgreSQL (identidade/contas)
    participant OTEL as OTEL Collector

    %% Obtenção de token confidencial
    Job->>CF: 1. POST /protocol/openid-connect/token (client_credentials)
    CF->>ALB: 2. Encaminha (mTLS + IP whitelist)
    ALB->>KC: 3. Keycloak valida certificado + escopos permitidos
    KC->>DBK: 4. Registra grant + auditoria
    KC-->>Job: 5. Retorna access token com audiências específicas

    %% Consumo das APIs
    loop Para cada recurso necessário
        Job->>CF: 6. Chamada HTTP (Bearer + mTLS)
        CF->>ALB: 7. Encaminha requisição
        alt Operação de identidade
            ALB->>APII: 8. Entrega à API Identidade
            APII->>Cache: 9. Valida JWKS
            APII->>DBI: 10. Consulta/atualiza dados
            APII->>OTEL: 11. Exporta métricas/traces
            APII-->>Job: 12. Resposta
        else Operação de contas
            ALB->>APIC: 8a. Entrega à API Contas
            APIC->>Cache: 9a. Valida JWKS
            APIC->>DBI: 10a. Consulta/atualiza dados
            APIC->>OTEL: 11a. Exporta métricas/traces
            APIC-->>Job: 12a. Resposta
        end
    end
```

**Resumo:** jobs internos ou integrações parceiras autenticam-se com `client_credentials` e certificado mTLS. Dependendo do endpoint, a requisição alcança a API de Identidade ou de Contas, ambas validando o token via JWKS e registrando observabilidade. O ciclo se repete para cada recurso processado.

---

### Como usar estes diagramas

- Utilize ferramentas com suporte a Mermaid (GitHub, VS Code, Mermaid Live Editor) para visualizar os fluxos em formato gráfico.
- Os números de cada etapa facilitam o cruzamento com logs, traces OTEL e auditorias em banco.
- Para novos casos de uso, replique a estrutura: identifique atores, serviços, configurações sensíveis (segredos, certificados) e descreva o encadeamento ponta a ponta.
