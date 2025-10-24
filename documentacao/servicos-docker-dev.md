# Serviços Docker do ambiente de desenvolvimento

Este guia rápido explica, de forma acessível para quem está começando, os serviços extra que sobem quando executamos `docker compose up` em `infraestrutura/dev`. O foco está nos containers `eickrono-keycloak-dev` e `eickrono-otel-collector-dev`.

---

## eickrono-keycloak-dev

- **O que é:** uma instância do Keycloak (servidor de autorização) configurada com o realm `dev`. Ele cuida do login, emissão de tokens e regras de segurança.
- **Configuração:** fica no container `servidor-autorizacao` definido em `infraestrutura/dev/docker-compose.yml`. Usa a imagem `quay.io/keycloak/keycloak:24.0.5`.

### Quando usar

1. **Gerar tokens para testes** — acessar `http://localhost:8080/`, entrar no realm `dev` e criar/usar usuários para chamar as APIs via Swagger.
2. **Conferir configurações de clientes e escopos** — útil para validar se o app ou BFF está com as permissões corretas.
3. **Verificar logs de autenticação** — ajuda a entender quando um token é negado ou expira.

### Comandos úteis

- **Ver logs em tempo real:**
  ```bash
  cd infraestrutura/dev
  docker compose logs -f servidor-autorizacao
  ```

- **Entrar no shell do container:**
  ```bash
  docker exec -it eickrono-keycloak-dev /bin/bash
  ```
  (apenas se estiver rodando uma imagem que tenha `bash`; caso contrário, use `/bin/sh`).

- **Reiniciar o serviço sem derrubar o restante:**
  ```bash
  cd infraestrutura/dev
  docker compose restart servidor-autorizacao
  ```

### Como obter informações pelo Keycloak

- **Console administrativo:** `http://localhost:8080/`. Entre com `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD` definidos no `.env`.
- **Endpoints OpenID Connect:**  
  - Descoberta: `http://localhost:8080/realms/dev/.well-known/openid-configuration`  
  - JWKs: `http://localhost:8080/realms/dev/protocol/openid-connect/certs`

---

## eickrono-otel-collector-dev

- **O que é:** um coletor do OpenTelemetry. Ele recebe métricas e traces das APIs e encaminha para ferramentas de observabilidade. Em dev, geralmente usamos apenas para garantir que os serviços estão exportando dados corretamente.
- **Configuração:** container `otel-collector` em `infraestrutura/dev/docker-compose.yml`, baseado na imagem `otel/opentelemetry-collector-contrib:0.100.0`.

### Quando usar

1. **Validar que as APIs estão emitindo métricas/traces** — mesmo sem Grafana/Prometheus locais, o coletor mostra nos logs se recebe algo.
2. **Testar integrações observabilidade** — quando precisamos checar headers OTLP, problemas de autenticação ou configurações SSL.

### Comandos úteis

- **Ver logs em tempo real:**
  ```bash
  cd infraestrutura/dev
  docker compose logs -f otel-collector
  ```

- **Verificar status do container:**
  ```bash
  docker ps | grep otel
  ```

- **Reiniciar o coletor:**
  ```bash
  cd infraestrutura/dev
  docker compose restart otel-collector
  ```

### Como conferir entradas

1. Realize uma requisição na API (ex.: `curl http://localhost:8081/actuator/health`).
2. Olhe os logs do coletor para ver se recebeu algum evento (`docker compose logs -f otel-collector`).
3. Se houver ajustes a fazer, edite o arquivo `infraestrutura/dev/otel-collector-config.yaml`.

---

## Passos práticos para um(a) júnior

1. Suba o ambiente: `cd infraestrutura/dev && docker compose up --build -d`.
2. Confirme que os containers necessários estão rodando: `docker compose ps`.
3. Abra o Keycloak em `http://localhost:8080/` e gere tokens para testar as APIs no Swagger.
4. Execute algumas rotas via Swagger ou `curl`.
5. Use `docker compose logs -f` nos containers para acompanhar a atividade:
   - `servidor-autorizacao` → autenticação / emissão de token.
   - `otel-collector` → métricas/traces enviados pelas APIs.

Seguindo esses passos, você ganha confiança no que cada serviço faz e sabe onde olhar quando precisar investigar problemas de autenticação ou observabilidade no ambiente local.
