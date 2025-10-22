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
