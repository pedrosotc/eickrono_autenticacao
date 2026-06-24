# Guia técnico júnior – Plataforma Eickrono Autenticação

Este documento introduz a arquitetura e o funcionamento da plataforma de autenticação da Eickrono para quem está iniciando na equipe. O objetivo é explicar o que cada parte do sistema faz, como os componentes se comunicam e quais termos técnicos você verá com frequência. Sempre que precisar de mais detalhes, consulte os guias especializados na pasta `documentacao/`.

## Visão geral do repositório

- **Linguagem e build**: Java 21 com Maven.
- **Decisão estrutural aprovada**:
  - `eickrono-autenticacao-servidor` deve convergir para um projeto simples e central de autenticação;
  - ele não deve evoluir como monorepo por domínio;
  - `contas` permanece domínio separado, fora da fronteira interna da autenticação.
- **Componentes principais**:
  - `src/`: código Java do provider e das extensões do Keycloak.
  - `autorizacao/`: artefatos de runtime do Keycloak (realms, tema, políticas e scripts).
  - `../eickrono-identidade-servidor`: serviço de identidade e contexto canônico, ainda usado em partes do runtime atual e da migração.
  - `../eickrono-contas-servidor`: domínio separado de contas e transações, sem fazer parte da fronteira interna da autenticação.
  - `infraestrutura/`: scripts para execução via Docker de `dev` e `stg`, além de pastas guia para produção (AWS + Cloudflare).
- **Documentação**: guias de arquitetura, desenvolvimento, operação e checklist FAPI em `documentacao/`.

## Como o sistema funciona

1. **Clientes** (App Flutter, BFF web, APIs internas) iniciam fluxos OAuth 2.1 com suporte a FAPI (Financial-grade API).
2. **Cloudflare** atua como borda de segurança: faz WAF, rate limiting e mTLS Origin Pull antes de entregar as requisições ao balanceador da AWS.
3. **AWS ALB/NLB** distribui para:
   - **Keycloak** (servidor de autorização) responsável por autenticação, MFA, emissão de tokens e políticas PAR/JAR/JARM.
   - **APIs** de Identidade e Contas que expõem endpoints protegidos e validam tokens recebidos.
4. **PostgreSQL (RDS)** armazena dados dos realms, identidades, contas e auditorias em schemas separados.
5. **Secrets Manager / Parameter Store / KMS** guardam segredos, certificados e configurações sensíveis usados pelos serviços.
6. **Observabilidade**: todas as aplicações exportam métricas e traces via OpenTelemetry Collector, com visualização em Prometheus/Grafana e alertas via CloudWatch + PagerDuty.

## Fluxo de autenticação básico (Authorization Code + PKCE)

1. O App Flutter gera `code_verifier`/`code_challenge`, redireciona o usuário para o Keycloak e envia parâmetros como `state` e `nonce`.
2. Cloudflare valida a requisição, aplica proteções adicionais e encaminha para o ALB.
3. Keycloak autentica o usuário (senha, MFA, WebAuthn), grava auditorias e devolve um código de autorização assinado (JARM).
4. O App troca o código por tokens de acesso/ID/refresh; Keycloak registra o grant e assinaturas via KMS.
5. O App consome a borda pública final de autenticação usando o token. Durante a transição, parte do runtime ainda consulta a identidade por backchannel para contexto canônico e fechamento da sessão local.

## Dispositivo e `X-Device-Token`

O desenho canônico atual não usa uma tela dedicada de registro de dispositivo no app.

1. **Login do app**  
   - O App envia login, senha, atestação nativa e metadados do aparelho para `POST /api/publica/sessoes`.  
   - A API valida a credencial, valida a atestação e calcula a confiança do aparelho.  
   - Quando o contexto estiver aceitável, a própria autenticação cria ou atualiza o vínculo do dispositivo e já emite o `X-Device-Token` na resposta do login.

2. **Persistência do token**  
   - O App salva o `X-Device-Token` junto da sessão autenticada.  
   - Todas as requisições subsequentes às APIs protegidas usam `Authorization: Bearer ...` e `X-Device-Token: ...`.

3. **Refresh vinculado ao dispositivo**  
   - No refresh, o cliente envia o `device_token` ao Keycloak.  
   - A SPI do Keycloak consulta a API de identidade para validar o token.  
   - Se o token estiver revogado, expirado, inválido ou ausente, o refresh falha.

4. **Validação adicional**  
   - Se a autenticação decidir que o usuário precisa validar novamente um contato, o app deve reutilizar a mesma tela de verificação de e-mail/telefone já existente.  
   - Não existe mais uma etapa obrigatória e separada de “registrar dispositivo” no app.

5. **Fluxos excepcionais**  
   - As rotas explícitas de `POST /api/conta/dispositivos/registro` e `POST /api/conta/dispositivos/registro/{id}/confirmacao` continuam existindo para cenários excepcionais, manutenção e compatibilidade transitória.
   - Elas não representam mais o fluxo principal do app móvel.

## Fluxo para clientes confidenciais (BFF, integrações internas)

1. O cliente usa **PAR** para registrar a requisição de autorização de maneira autenticada (mTLS).
2. Opcionalmente, assina os parâmetros com **JAR**.
3. Keycloak responde com **JARM** (resposta de autorização em JWT).
4. Na troca de código por token, o cliente apresenta certificado mTLS; o token emitido fica “bound” ao certificado.
5. Ao chamar a API de Contas, o token e o certificado são verificados. O serviço aplica regras de escopo e chama `AuditoriaContasService` para registrar o acesso e o evento da transação.

## Observabilidade e auditoria

- **Auditoria**:
  - API de Contas: `AuditoriaContasService` grava eventos e acessos com timestamp.
  - Keycloak: registra interações em logs próprios e exporta para o stack de observabilidade.
- **Logs sensíveis**: tokens, CPFs e e-mails são mascarados antes de seguir para o SIEM.
- **Métricas/Traces**: cada serviço envia dados OTLP para o coletor; dashboards ficam no Grafana e alertas no PagerDuty.

## Primeiros passos para desenvolvimento

1. Leia `documentacao/guia-desenvolvimento.md` para configurar Java, Maven, Docker e outras dependências.
2. Execute `mvn verify` na raiz para baixar dependências e rodar testes.
3. Inicie o ambiente local com `docker compose up` em `infraestrutura/dev` (Keycloak + PostgreSQL + APIs).
4. Acesse `http://127.0.0.1:8081/actuator/health` e `http://127.0.0.1:8082/actuator/health` para validar as APIs.
5. Use os realms em `autorizacao/realms` para importar as configurações do Keycloak local.

## Boas práticas do time

- Commits pequenos, sempre em português e com contexto.
- Preencher o `documentacao/checklist-seguranca-fapi.md` antes de cada PR.
- Nunca subir segredos reais ou exports completos de produção.
- Utilizar certificados gerados pelos scripts da pasta `infraestrutura/dev/certificados` quando precisar de mTLS local.
- Monitorar alertas e métricas após deploys (consultar dashboards e logs mascarados).

## Glossário de termos e siglas

- **ACM (AWS Certificate Manager)**: serviço que gerencia certificados TLS usados em ALB/NLB e mTLS.
- **ALB/NLB (Application/Network Load Balancer)**: balanceadores da AWS; terminam TLS e distribuem tráfego para Keycloak e APIs.
- **Authorization Code + PKCE**: fluxo OAuth 2.1 onde o cliente gera `code_verifier`/`code_challenge` para evitar interceptação do código de autorização.
- **Auditoria**: registro de eventos de segurança (quem acessou, o que fez, quando). Implementada nas APIs e no Keycloak.
- **BFF (Backend for Frontend)**: backend dedicado ao front web que age como cliente confidencial da plataforma.
- **CFSSL/Certificados**: scripts locais geram certificados autoassinados para desenvolvimento/staging.
- **Cloudflare**: serviço de borda que aplica WAF, rate limit, proteção DDoS e mTLS Origin Pull antes do tráfego chegar à AWS.
- **code_challenge / code_verifier**: par de valores usado no PKCE. O `code_challenge` é enviado na autorização e o `code_verifier` é usado depois para provar a posse do código.
- **DPoP (Demonstration of Proof of Possession)**: mecanismo opcional para vincular o token a uma chave pública do cliente.
- **FAPI (Financial-grade API)**: conjunto de requisitos de segurança para APIs financeiras (PAR, JAR, JARM, mTLS, auditoria reforçada).
- **JWKS (JSON Web Key Set)**: endpoint do Keycloak que expõe as chaves públicas usadas para verificar tokens JWT.
- **JAR (JWT Authorization Request)**: encapsula parâmetros de autorização em um JWT assinado pelo cliente.
- **JARM (JWT Authorization Response Mode)**: faz com que a resposta de autorização seja um JWT assinado/criptografado pelo servidor.
- **KMS (Key Management Service)**: serviço AWS que gera e rotaciona chaves usadas para assinar JWKs e certificados.
- **mTLS (mutual TLS)**: autenticação mútua via certificados, garantindo a identidade do cliente e do servidor.
- **nonce**: valor aleatório usado para prevenir replay; enviado na autorização e validado pelo servidor.
- **Observabilidade**: conjunto de ferramentas (Actuator, Micrometer, Prometheus, OpenTelemetry) para métricas, logs e traces.
- **OTLP (OpenTelemetry Protocol)**: protocolo usado para enviar métricas e traces para o coletor OTEL.
- **OpenTelemetry Collector (OTEL)**: serviço que recebe métricas/traces dos aplicativos e encaminha para destinos como Prometheus, Grafana e CloudWatch.
- **PagerDuty**: ferramenta de gerenciamento de incidentes utilizada para acionar o time de suporte em caso de alertas críticos.
- **PAR (Pushed Authorization Request)**: fluxo onde o cliente envia a requisição de autorização diretamente ao servidor, protegido por mTLS e autenticação.
- **PKCE (Proof Key for Code Exchange)**: extensão do Authorization Code que evita ataques com interceptação.
- **Secrets Manager**: armazena segredos (senhas, certificados) com rotação e controle de acesso.
- **SPI (Service Provider Interface)**: ponto de extensão do Keycloak usado para adicionar comportamentos personalizados.
- **state**: valor gerado pelo cliente para garantir que a resposta de autorização corresponde à requisição original.
- **Temas**: customizações de layout/aparência do Keycloak (login, páginas de erro) armazenadas em `autorizacao/temas/login-ptbr`.
- **CloudWatch**: serviço de monitoramento da AWS que recebe logs e métricas do ambiente.
- **MFA (Multi-Factor Authentication)**: autenticação com múltiplos fatores (senha + token, biometria ou WebAuthn).
- **Realm**: contexto lógico do Keycloak que agrupa usuários, clientes, escopos e configurações. Neste repositório o nome padronizado é `eickrono`, com separação de ambiente feita pelo host e pela implantação.
- **TLS (Transport Layer Security)**: protocolo de criptografia usado em todas as conexões HTTPS.
- **Validação de audiência (`aud`)**: checagem se o token recebido é destinado à API que está processando.
- **WAF (Web Application Firewall)**: firewall que inspeciona HTTP/HTTPS, bloqueando requisições maliciosas.

## Próximos passos sugeridos

- Desenhar o diagrama de sequência em `documentacao/diagramas/fluxo-sequencia.md` para entender o passo a passo das interações.
- Consultar `documentacao/guia-arquitetura.md` para detalhes avançados sobre PAR/JAR/JARM, mTLS e fluxos FAPI.
- Antes de abrir uma PR, verificar o checklist FAPI, rodar `mvn verify` e garantir que o Docker local está saudável.

Bem-vindo(a) ao time! Em caso de dúvidas, fale com seu par ou com quem mantém os módulos principais antes de alterar configurações críticas (Keycloak, segurança das APIs ou scripts de certificados).
