# Eickrono Autenticação

Bem-vinda(o) ao monorepo **Eickrono Autenticação**. Este projeto reúne a plataforma de identidade, APIs de domínio e infraestrutura de suporte da organização Eickrono, com foco em requisitos FAPI (Financial-grade API) e integrações com Keycloak/RH-SSO.

## Visão geral

- **Linguagem:** Java 21  
- **Build:** Maven multi-módulo  
- **Segurança:** Spring Security, Keycloak, FAPI (PAR/JAR/JARM), mTLS  
- **Observabilidade:** Spring Boot Actuator, Micrometer, Prometheus, OpenTelemetry  
- **Qualidade:** Checkstyle, SpotBugs, JUnit 5, Testcontainers  
- **Banco de dados:** PostgreSQL com Flyway  
- **Cache:** Caffeine padronizado  
- **Ambientes:** desenvolvimento, homologação, produção

## Estrutura principal

- `/modulos`: código-fonte das aplicações (servidor de autorização e APIs)  
- `/infraestrutura`: provisionamento local (dev/hml) e diretrizes de produção (AWS + Cloudflare)  
- `/documentacao`: guias funcionais, operacionais e checklist de conformidade  
- `pom.xml`: agregador Maven com as configurações de build compartilhadas

## Diagramas e fluxos

Arquivos de diagramas serão adicionados gradualmente na pasta `documentacao/diagramas` (a ser criada) conforme avançarmos no detalhamento das integrações PAR/JAR/JARM e mTLS.

## Próximos passos sugeridos

1. Ler o `guia-desenvolvimento.md` para configurar o ambiente local.  
2. Executar `mvn verify` na raiz para validar o build.  
3. Subir o ambiente `docker-compose` de desenvolvimento para validar o fluxo Authorization Code + PKCE com o Keycloak local.  
4. Preencher e revisar o `checklist-seguranca-fapi.md` antes de cada entrega.
