# Diagrama de sequência – Plataforma Eickrono Autenticação

Abaixo está o fluxo completo que cobre desde o login via Authorization Code + PKCE até as chamadas das APIs protegidas (identidade e contas), incluindo os bastidores de segurança, armazenamento de segredos, auditoria e observabilidade. O diagrama utiliza sintaxe Mermaid para facilitar a visualização em ferramentas compatíveis.

```mermaid
sequenceDiagram
    autonumber
    participant U as Pessoa usuária
    participant App as App Flutter (público)
    participant CF as Cloudflare (WAF/mTLS)
    participant ALB as AWS ALB/NLB
    participant KC as Servidor de autorização (Keycloak)
    participant SM as AWS Secrets Manager
    participant KMS as AWS KMS / HSM
    participant PS as AWS Parameter Store
    participant Cache as Cache local (Caffeine)
    participant DB as RDS PostgreSQL (multi-schema)
    participant OTEL as OTEL Collector
    participant OBS as Observabilidade (Prometheus/Grafana/SIEM)
    participant BFF as BFF Web Eickrono (confidencial)
    participant APII as API Identidade Eickrono
    participant APIC as API Contas Eickrono
    participant APIInt as APIs internas / jobs
    participant AC as Autoridade certificadora interna
    participant TimeSec as Time de segurança/operações

    %% Abertura do fluxo de login público (PKCE)
    U->>App: 1. Solicita login
    App->>CF: 2. Authorization Request (code_challenge, state, nonce)
    Note right of CF: TLS 1.3 - WAF e validação nonce/JARM
    CF->>ALB: 3. Encaminha requisição (mTLS Origin Pull)
    ALB->>KC: 4. Redireciona para /auth
    Note right of ALB: Certificados emitidos pela AC interna via ACM

    KC->>SM: 5. Carrega segredos (credenciais DB, certificados mTLS)
    SM-->>KC: 6. Segredos versionados por ambiente
    KC->>KMS: 7. Solicita chave ativa para assinar JWK/JARM
    KMS-->>KC: 8. Material criptográfico e rotação automática
    KC->>DB: 9. Consulta dados do realm (schema keycloak)
    KC-->>ALB: 10. Redireciona usuário (JARM assinado)
    ALB-->>CF: 11. Resposta autorizada
    CF-->>App: 12. Devolve authorization code assinado

    %% Troca de código por tokens
    App->>CF: 13. Token Request (code + code_verifier)
    CF->>ALB: 14. Mútua autenticação (Origin Pull)
    ALB->>KC: 15. Encaminha /token (JAR validado)
    KC->>DB: 16. Registra grant + nonce (anti-replay)
    KC-->>App: 17. Emite tokens (ID, Access, Refresh) + claims FAPI
    KC->>OTEL: 18. Exporta métricas/traces (OTLP)
    OTEL->>OBS: 19. Armazena métricas, logs mascarados e traces

    %% Chamada de API Identidade a partir do App (cliente público)
    App->>CF: 20. Chamada GET /identidade/perfil (Bearer access token)
    CF->>ALB: 21. Encaminha req após WAF/Rate Limit
    ALB->>APII: 22. Roteia para API Identidade
    APII->>Cache: 23. Verifica JWKS cache (Caffeine, TTL 5 min)
    alt JWKS encontrado
        Cache-->>APII: 24. Chave válida
    else JWKS expirado
        APII->>KC: 24a. Busca JWKS atualizado (mTLS)
        KC-->>APII: 24b. JWKS + metadados
        APII->>Cache: 24c. Atualiza cache local
    end
    APII->>PS: 25. Lê feature flags / config de tolerância clock skew
    APII->>SM: 26. Obtém credenciais (schema identidade)
    APII->>DB: 27. Consulta dados de perfil (schema identidade)
    APII->>OTEL: 28. Exporta métricas/traces (OTLP)
    APII-->>App: 29. Retorna perfil com auditoria mínima

    %% Paralelo: clientes confidenciais utilizando PAR/JAR/JARM + mTLS
    par BFF confidencial
        BFF->>CF: 30. Pushed Authorization Request (mTLS + JWT assinado)
        CF->>ALB: 31. Encaminha para /par (mTLS)
        ALB->>KC: 32. Keycloak valida assinatura (DPoP opcional)
        KC-->>BFF: 33. Retorna request_uri protegido

        BFF->>CF: 34. Authorization Request com request_uri
        CF->>ALB: 35. Mútua autenticação
        ALB->>KC: 36. Autorização (políticas MFA/WebAuthn)
        KC-->>BFF: 37. Resposta JARM

        BFF->>CF: 38. Token Request (mTLS + client cert)
        CF->>ALB: 39. Encaminha /token (mutual TLS binding)
        ALB->>KC: 40. Keycloak valida certificado + scopes
        KC->>DB: 41. Registra auditoria
        KC-->>BFF: 42. Tokens confidenciais (bound certificate)

        BFF->>CF: 43. POST /contas/transacoes (mTLS + Bearer + signed payload)
        CF->>ALB: 44. Encaminha para API Contas
        ALB->>APIC: 45. Entrega requisição
        APIC->>Cache: 46. Valida JWKS no cache
        alt JWKS expirado
            APIC->>KC: 46a. Solicita JWKS
            KC-->>APIC: 46b. JWKS atualizado
            APIC->>Cache: 46c. Atualiza cache
        end
        APIC->>PS: 47. Lê config de escopos + limites de valor
        APIC->>SM: 48. Busca credenciais (schema contas)
        APIC->>DB: 49. Executa transação (schema contas)
        APIC->>DB: 50. Chama AuditoriaContasService
        APIC->>OTEL: 51. Exporta métricas/traces
        APIC-->>BFF: 52. Resposta com transação e registro auditor
    and APIs internas / jobs
        APIInt->>CF: 53. Autorização PAR/JAR (mTLS)
        CF->>ALB: 54. Encaminha
        ALB->>KC: 55. Keycloak aplica políticas de cliente confidencial
        KC-->>APIInt: 56. Tokens JARM/JWT com audiences dedicadas
        APIInt->>CF: 57. Chamada para APIs (mTLS obrigatório)
        CF->>ALB: 58. Encaminha para API alvo (identidade/contas)
        ALB->>APII: 59. Processa requisição (caso identidade)
        ALB->>APIC: 60. Processa requisição (caso contas)
        Note over APIInt: Fluxo idêntico ao descrito acima (JWKS / SM / DB / OTEL)
    end

    %% Distribuição e rotação de certificados
    AC->>CF: 61. Disponibiliza certificados Origin Pull
    AC->>ALB: 62. Atualiza certificados mTLS/TLS
    AC->>KC: 63. Certificados clientes confidenciais (dev/hml via scripts)
    AC->>BFF: 64. Certificados clientes (produção via ACM/KMS)

    %% Observabilidade e alarmes finais
    OTEL->>OBS: 65. Envia métricas, logs mascarados e traces correlacionados
    OBS-->>TimeSec: 66. Alarmes SNS/PagerDuty (quando necessário)
```

## Notas de leitura

- **Multiplicidade de clientes**: o mesmo fluxo se aplica ao aplicativo Flutter (PKCE), ao BFF web e às APIs internas, variando apenas a forma de autenticação (público vs. confidencial com mTLS). 
- **Cache JWKS local**: ambas as APIs utilizam Caffeine com TTL de 5 minutos e tamanho máximo de 1000 entradas para evitar chamadas repetidas ao Keycloak. 
- **Auditoria**: a API de Contas invoca `AuditoriaContasService` para registrar acessos e eventos no esquema `contas`, garantindo rastreabilidade. 
- **Segurança de segredos**: Secrets Manager armazena credenciais e certificados, enquanto o KMS/HSM fornece criptografia e assinatura das chaves JWK, com rotação automática. 
- **Observabilidade integrada**: cada serviço exporta métricas e traces via OTLP para o coletor OTEL, que os encaminha para Prometheus, Grafana, CloudWatch Logs e o SIEM corporativo. 
- **Compatibilidade FAPI**: Keycloak opera com PAR, JAR, JARM, mTLS, MFA/WebAuthn e tolerância mínima de clock skew (1 min) auditada pelas APIs.

## Fluxo de registro e verificação de dispositivo móvel

```mermaid
sequenceDiagram
    autonumber
    participant U as Pessoa usuária
    participant App as App Flutter
    participant APII as API Identidade
    participant SMS as Provedor SMS
    participant Email as Provedor E-mail
    participant DB as PostgreSQL (identidade)
    participant Job as Scheduler expiração
    participant KC as Keycloak (SPI device)

    U->>App: 1. Instala app e informa e-mail/telefone
    App->>APII: 2. POST /identidade/dispositivos/registro (fingerprint + metadados)
    APII->>DB: 3. Cria RegistroDispositivo (status PENDENTE, expira +9h)
    APII->>DB: 4. Cria CodigoVerificacao SMS/EMAIL (hash, tentativas=0)
    APII->>SMS: 5. Envia código aleatório 6 dígitos (via CanalEnvioCodigoSms)
    APII->>Email: 6. Envia código 6 dígitos (via CanalEnvioCodigoEmail)
    APII->>DB: 7. Auditoria DISPOSITIVO_REGISTRO_SOLICITADO
    APII-->>App: 8. Retorna registroId + expiraEm

    U->>App: 9. Informa códigos recebidos
    App->>APII: 10. POST /identidade/dispositivos/registro/{id}/confirmacao
    APII->>DB: 11. Verifica status PENDENTE e expiraEm >= agora
    APII->>DB: 12. Compara hash SMS e incrementa tentativas
    APII->>DB: 13. Compara hash EMAIL e incrementa tentativas
    APII->>DB: 14. Atualiza status registro -> CONFIRMADO
    APII->>DB: 15. Revoga tokens ativos do usuário (status REVOGADO)
    APII->>DB: 16. Cria novo TokenDispositivo (hash token, fingerprint)
    APII->>KC: 17. Notifica SPI DeviceTokenConstraint (permitir apenas novo token)
    APII->>DB: 18. Auditoria DISPOSITIVO_VERIFICACAO_SUCESSO
    APII-->>App: 19. Retorna deviceToken opaco + metadata

    App->>APII: 20. Requisições subsequentes com header X-Device-Token
    alt Token válido
        APII->>DB: 21. Consulta token (cache Caffeine + fallback DB)
        APII-->>App: 22. Resposta autorizada
    else Token revogado
        APII-->>App: 21a. Retorna 423 Locked (força novo registro)
    end

    par Expiração automática
        Job->>DB: 23. Busca registros PENDENTE com expiraEm < agora
        Job->>DB: 24. Marca como EXPIRADO e invalida códigos
        Job->>DB: 25. Auditoria DISPOSITIVO_REGISTRO_EXPIRADO
    and Reenvio de código
        App->>APII: 26. POST /identidade/dispositivos/registro/{id}/reenviar
        APII->>DB: 27. Valida limite (máx 3 reenvios) e expiração
        APII->>SMS: 28. Reenvia código SMS
        APII->>Email: 29. Reenvia código e atualiza timestamps
    end
```

### Notas específicas do fluxo de dispositivo

- **Fingerprint mínima**: inclui modelo, plataforma, versão do app e chave pública do aparelho (usada futuramente para DPoP).  
- **Armazenamento seguro**: os hashes de códigos e tokens utilizam HMAC-SHA256 com salt aleatório antes de persistir.  
- **Política de tentativas**: após 5 tentativas inválidas por canal, o registro é marcado como `BLOQUEADO` e passa a devolver 423 até que novo registro seja iniciado.  
- **Sincronização com Keycloak**: o SPI `DeviceTokenConstraintProvider` consulta a API antes de emitir refresh tokens, bloqueando sessões de tokens revogados ou expirados.  
- **Observabilidade**: métricas expostas incluem `device_registration_requested_total`, `device_registration_confirmed_total`, `device_token_revoked_total` e histogramas de tempo até confirmação.
