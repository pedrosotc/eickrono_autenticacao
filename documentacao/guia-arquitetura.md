# Guia de Arquitetura

Este guia descreve a arquitetura do ecossistema de autenticação da Eickrono, destacando componentes, integrações e fluxos compatíveis com o padrão FAPI.

## Componentes principais

- **Servidor de autorização (Keycloak/RH-SSO):** responsável pelos realms `desenvolvimento`, `homologacao` e `producao`. Mantém configurações PAR/JAR/JARM, políticas de MFA/WebAuthn/Passkeys e rotação de chaves JWK.
- **API Identidade Eickrono:** serviço Spring Boot que expõe recursos de perfil e vínculos sociais, validando tokens JWT provenientes do servidor de autorização.
- **Registro de dispositivos móveis:** conjunto de serviços na API de Identidade (`RegistroDispositivoService`, `TokenDispositivoService`, `CodigoVerificacaoService`) que gerenciam onboarding de novos aparelhos, revogação de tokens antigos e dupla verificação via SMS e e-mail.
- **API Contas Eickrono:** serviço Spring Boot para operações de contas e transações, com escopos e papéis específicos (`SCOPE_transacoes:ler`, `ROLE_cliente`) e auditoria detalhada.
- **PostgreSQL:** banco multi-schema, com versionamento por Flyway e separação de usuários por ambiente.
- **Caffeine Cache:** camada de cache em memória utilizada de forma consistente pelos serviços.
- **Observabilidade:** Actuator, Micrometer (Prometheus) e OpenTelemetry (OTLP) compondo o stack de métricas e tracing.
- **Infraestrutura Cloud:** AWS (EKS/ECS, RDS, ACM, KMS/HSM, Secrets Manager, ALB/NLB) protegida por Cloudflare (WAF, Rate Limit, mTLS Origin Pull).

## Fluxos FAPI

1. **Authorization Code + PKCE:**  
   - Aplicativo público (`aplicativo-flutter-eickrono`) inicia fluxo com `code_verifier` e `code_challenge`.  
   - Servidor de autorização valida `state` e `nonce` durante o retorno do `redirect_uri`.
2. **PAR (Pushed Authorization Request):**  
   - Clientes confidenciais (`bff-web-eickrono`, `apis-internas-eickrono`) enviam parâmetros de autorização de forma autenticada via `request_uri` protegida.  
   - O servidor armazena a requisição temporariamente, mitigando exposição de dados sensíveis.
3. **JAR (JWT Authorization Request):**  
   - Os parâmetros de autorização são encapsulados em JWT assinado pelo cliente, garantindo integridade e autenticação.
4. **JARM (JWT Authorization Response Mode):**  
   - As respostas de autorização são assinadas/criptografadas pelo servidor, preservando confidencialidade e integridade dos códigos de autorização.
5. **mTLS:**  
   - Clientes confidenciais utilizam certificados gerenciados (ACM/KMS em produção) para autenticação mútua.  
   - Em desenvolvimento e homologação utilizamos certificados autoassinados gerados via scripts no repositório.
6. **Registro de dispositivo com MFA dupla (SMS + e-mail):**  
   - O App Flutter envia fingerprint e contatos para `POST /identidade/dispositivos/registro`.  
   - A API gera duas verificações (`CodigoVerificacao`) e envia códigos pelos canais configurados (`CanalEnvioCodigoSms`, `CanalEnvioCodigoEmail`).  
   - A confirmação via `POST /identidade/dispositivos/registro/{id}/confirmacao` compara hashes de código, garante tentativas limitadas e expiração em 9 horas.  
   - A finalização gera `DispositivoToken` opaco, revoga tokens anteriores e notifica o Keycloak (SPI `DeviceTokenConstraintProvider`) para impedir sessões de aparelhos não validados.

## Integrações e validações

- **Audience dedicada:** cada API valida o `aud` específico esperado para evitar reuso de tokens.  
- **Validação de escopos:** o gateway e os serviços reforçam escopos, inclusive combinações escopo+papel.  
- **Anti-replay:** armazenamento temporário de `jti` e uso de PKCE e nonce reduzem ataques de repetição.  
- **Clock skew mínimo:** tolerância configurável (padrão 1 minuto) e auditoria das discrepâncias.  
- **Logs mascarados:** dados sensíveis (tokens, CPFs, e-mails) são ofuscados antes da persistência ou envio a ferramentas externas.

## Estratégia de chaves e segredos

- **Rotação automática:** chaves JWK e certificados TLS com rotação programada.  
- **Segregação:** segredos distintos por ambiente e uso de Secrets Manager.  
- **Backups e DR:** dumps automáticos do RDS/PostgreSQL, exportação de realms Keycloak e testes de restauração periódicos.

## Modelo de dados do registro de dispositivos

- **Tabela `registro_dispositivo`:** armazena metadados da solicitação (UUID, e-mail, telefone, fingerprint, status, `criado_em`, `expira_em`, `confirmado_em`).  
- **Tabela `codigo_verificacao`:** relaciona registro + canal (`SMS`/`EMAIL`), hash do código, tentativas, limite e timestamps de envio/validação.  
- **Tabela `token_dispositivo`:** mantém tokens opacos vinculados ao usuário (`sub`), fingerprint, data de emissão, revogação e motivo.  
- Todas têm índices em `status`, `expira_em` e `usuario_sub` para facilitar queries do job de expiração e verificação rápida no filtro HTTP (`DeviceTokenFilter`).

## Políticas de expiração e revogação

- **Expiração automática:** job `RegistroDispositivoScheduler` roda a cada 15 minutos, usa `clockProvider` para comparar `expira_em` e marca registros/códigos como expirados.  
- **Revogação preventiva:** ao confirmar um novo dispositivo, os tokens anteriores do mesmo usuário são revogados antes da emissão do novo token.  
- **Bloqueio HTTP:** filtros Spring verificam header `X-Device-Token`, consultam `token_dispositivo` (com cache Caffeine de 5 minutos) e recusam requisições com tokens revogados (`423 Locked`).  
- **Auditoria:** todos os eventos relevantes são enviados para `AuditoriaEventoIdentidade` com detalhes do fingerprint e motivo.

## Diagramas recomendados

- Fluxo Authorization Code + PKCE com PAR/JAR/JARM.  
- Diagrama de implantação (AWS + Cloudflare).  
- Sequência de auditoria e registro de eventos.  
- Fluxo mTLS entre componentes internos e clientes confidenciais.
