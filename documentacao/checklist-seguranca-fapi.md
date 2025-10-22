# Checklist de Segurança FAPI

Utilize esta lista antes de cada release para garantir conformidade contínua com os requisitos FAPI.

## Preparação

- [ ] Todos os certificados mTLS válidos (dev/hml autoassinados, prod via ACM/KMS).  
- [ ] Chaves JWK rotacionadas conforme política interna e alinhadas com os clientes.  
- [ ] Relógios sincronizados (NTP) em todos os componentes críticos.

## Authorization Code + PKCE

- [ ] `code_verifier` e `code_challenge` aplicados em todos os clientes públicos.  
- [ ] Validação de `state` e `nonce` implementada nas aplicações.  
- [ ] Proteção anti-replay (`jti`, armazenamento temporário dos códigos).

## PAR (Pushed Authorization Request)

- [ ] Endpoint PAR habilitado e protegido para clientes confidenciais.  
- [ ] Tempo de expiração configurado e monitorado.  
- [ ] Auditoria das requisições PAR armazenada de forma segura.

## JAR (JWT Authorization Request)

- [ ] JWT de requisição assinado pelos clientes confidenciais.  
- [ ] Validação do `aud`, `iss`, `exp`, `nbf` e `jti`.  
- [ ] Falhas de assinatura geram alertas operacionais.

## JARM (JWT Authorization Response Mode)

- [ ] Respostas de autorização assinadas/criptografadas pelo servidor.  
- [ ] Clientes validam assinatura e criptografia do JWT de resposta.  
- [ ] Registro de eventos de sucesso e falha com mascaramento de dados.

## APIs Resource Server

- [ ] Validação de `issuer`, `audience` e escopos configurada.  
- [ ] Perfis `application-*.yml` atualizados com URLs corretas dos realms.  
- [ ] Auditoria ativa (tabelas `auditoria_eventos`, `auditoria_acessos`).  
- [ ] Logs sensíveis mascarados.  
- [ ] Swagger habilitado apenas em dev/hml (com Basic Auth + whitelist em hml).

## Observabilidade

- [ ] Actuator protegido com segurança adequada.  
- [ ] Métricas expostas em `/actuator/prometheus` com autenticação em ambientes sensíveis.  
- [ ] Exportação OpenTelemetry OTLP configurada para collector central.  
- [ ] Dashboards e alertas atualizados (tempo de resposta, erros, tentativas inválidas).

## Banco de dados e migrações

- [ ] Migrações Flyway executadas com sucesso em todos os ambientes.  
- [ ] Contas de banco com privilégios mínimos validadas.  
- [ ] Backups recentes e testados.

## Revisão final

- [ ] Checklist assinado pela equipe de segurança.  
- [ ] Evidências anexadas ao pipeline de release.  
- [ ] Comunicação com stakeholders realizada.
