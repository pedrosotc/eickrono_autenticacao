# Guia de Desenvolvimento

Este guia orienta a preparação do ambiente local e o fluxo de trabalho diário para contribuir com o monorepo **Eickrono Autenticação**.

## Requisitos locais

- **Sistema operacional:** macOS, Linux ou Windows 11 com WSL2.  
- **JDK:** Temurin/Adoptium Java 21.  
- **Maven:** 3.9 ou superior.  
- **Docker + Docker Compose:** para execução dos ambientes dev/hml.  
- **Node.js (opcional):** apenas se for necessário personalizar o tema do Keycloak com toolchain front-end.  
- **Ferramentas auxiliares:** `make`, `openssl` para geração de certificados, `psql` para interações com PostgreSQL.

## Primeiros passos

1. Execute `mvn -version` para validar a instalação do Maven e do JDK 21.  
2. Rode `mvn verify` na raiz para baixar dependências e validar qualidade.  
3. Copie `infraestrutura/dev/.env.dev` para personalização local (use valores seguros).  
4. Execute `docker compose up` em `infraestrutura/dev` para subir Keycloak, PostgreSQL e as APIs.  
5. Acesse `http://localhost:8081/actuator/health` e `http://localhost:8082/actuator/health` para verificar se as APIs estão saudáveis.

## Fluxo Git recomendado

- Branch principal: `main`.  
- Branches de feature: `feature/<descricao-curta>`.  
- Commits pequenos e em português.  
- Pull request acompanhado do `checklist-seguranca-fapi.md` preenchido.

## Testes e qualidade

- `mvn verify`: executa testes, Checkstyle, SpotBugs e validações do Spring Boot.  
- `mvn -pl modulos/api-identidade-eickrono spring-boot:run`: inicia apenas a API de identidade.  
- `mvn -pl modulos/api-contas-eickrono spring-boot:run`: inicia a API de contas.  
- Testcontainers é utilizado para testes de integração; não é necessário subir um PostgreSQL manualmente para a suíte de testes.

## Dicas adicionais

- Utilize perfis `application-dev.yml`, `application-hml.yml` e `application-prod.yml` para configurações específicas por ambiente.  
- O Swagger (springdoc) fica acessível apenas em dev/hml, protegido por Basic Auth e whitelist em homologação.  
- Certificados mTLS autoassinados podem ser regenerados com o script `infraestrutura/dev/certificados/gerar_certificados.sh`.  
- Para Keycloak, utilize os realms exportados em `modulos/servidor-autorizacao-eickrono/realms`.
