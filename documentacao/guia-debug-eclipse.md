# Guia de depuração com Eclipse

Este guia mostra como preparar os ambientes Docker (desenvolvimento e homologação) e configurar o Eclipse para depurar os módulos Java do monorepo **Eickrono Autenticação**. O passo a passo assume nível júnior e cobre desde o build até o uso dos endpoints Swagger para validação manual.

## 1. Pré-requisitos locais

1. JDK Temurin 21 e Maven 3.9+ (`mvn -version` deve funcionar).
2. Docker Desktop ou Docker Engine + Docker Compose Plugin (`docker compose version`).
3. Eclipse IDE for Enterprise Java Developers 2023‑12 ou superior (com suporte a Maven e Remote Java Application).

> Dica: ao importar o monorepo no Eclipse, use `File > Import... > Existing Maven Projects`, apontando para a raiz `eickrono_autenticacao`. Isso cria projetos separados para cada módulo, facilitando o vínculo nas configurações de depuração.

## 2. Build dos módulos antes de subir os containers

Os Dockerfiles das APIs esperam que os artefatos `.jar` já tenham sido construídos. Rode uma vez (após cada mudança de código) o comando abaixo na raiz do repositório:

```bash
mvn -pl modulos/api-identidade-eickrono,modulos/api-contas-eickrono -am clean package -DskipTests
```

Os artefatos serão gerados em `modulos/<nome>/target/`, prontos para serem copiados pelas imagens Docker.

> Se houver atualização de dependências Java (por exemplo, mudança de versão do JDK ou adição de bibliotecas nativas), repita o `clean package` para garantir que os JARs reflitam a nova configuração e atualize os Dockerfiles conforme descrito na seção 8.

## 3. Ambiente Docker de desenvolvimento

1. Vá para a pasta `infraestrutura/dev`.
2. Personalize variáveis copiando `.env.dev` para `.env` se precisar alterar portas ou segredos: `cp .env.dev .env`.
3. Suba os serviços com suporte a depuração:
   ```bash
   docker compose up --build -d
   ```
4. Aguarde até que `servidor-autorizacao` (Keycloak) e as APIs indiquem `healthy` em `docker compose ps`. Logs podem ser acompanhados via `docker compose logs -f api-identidade-eickrono`.
5. Parar o ambiente: `docker compose down -v` (remove volumes locais).

### Portas expostas em desenvolvimento

- `localhost:8080`: Keycloak (`realm` dev).
- `localhost:8081`: API Identidade (HTTP).
- `localhost:8082`: API Contas (HTTP).
- `localhost:5005`: depuração remota da API Identidade.
- `localhost:5006`: depuração remota da API Contas.

Se precisar alterar as portas de depuração, edite `API_IDENTIDADE_DEBUG_PORT` ou `API_CONTAS_DEBUG_PORT` em `.env` antes de subir os containers. Para pausar a JVM logo no início (`suspend=y`), ajuste a linha `JAVA_OPTS` correspondente em `docker-compose.yml`.

## 4. Ambiente Docker de homologação local

O cenário `hml` replica a configuração com parâmetros de homologação (URLs, schemas, certificados).

1. Vá para `infraestrutura/hml`.
2. Revise `.env.hml` e, se necessário, copie para `.env` para ajustes locais (`cp .env.hml .env`).
3. Execute:
   ```bash
   docker compose up --build -d
   ```
4. Acompanhe a inicialização com `docker compose logs -f`.
5. Parar os serviços: `docker compose down -v`.

### Portas expostas em homologação local

- `localhost:8080`: Keycloak (hostname configurado para `https://hml-identidade.eickrono.com.br`).
- `localhost:18081`: API Identidade (HTTP).
- `localhost:18082`: API Contas (HTTP).
- `localhost:5005`: depuração remota da API Identidade.
- `localhost:5006`: depuração remota da API Contas.

Os containers reutilizam o mesmo par de portas de depuração. Evite subir os ambientes `dev` e `hml` ao mesmo tempo ou personalize as variáveis em `.env`.

## 5. Configurando a depuração remota no Eclipse

Repita os passos abaixo para cada módulo que desejar depurar.

1. Certifique-se de que o Docker Compose correspondente (`dev` ou `hml`) está rodando.
2. No Eclipse, abra `Run > Debug Configurations...`.
3. Em `Remote Java Application`, clique em `New`.
4. Preencha:
   - **Name:** escolha algo como `API Identidade (Docker)` ou `API Contas (Docker)`.
   - **Project:** selecione `api-identidade-eickrono` ou `api-contas-eickrono`.
   - **Connection Type:** `Standard (Socket Attach)`.
   - **Host:** `localhost`.
   - **Port:** `5005` para Identidade, `5006` para Contas (ou o valor configurado nos `.env`).
5. Salve e clique em `Debug`. O status “Connected to VM” aparecerá na aba **Debug**.
6. Adicione breakpoints nos arquivos Java desejados. A próxima chamada HTTP que atingir o código pausará a execução.

### Depurando múltiplos módulos

- Crie duas configurações diferentes (Identidade e Contas) e mantenha ambas salvas.
- Você pode conectar a mais de um módulo simultaneamente; o Eclipse abrirá cada sessão na árvore de debug.
- Para atualizar o código durante a sessão, recompile o módulo (`mvn package`) e reinicie apenas o serviço afetado (`docker compose restart api-identidade-eickrono`).

### Dicas de diagnóstico

- **Conexão recusada:** confirme se o container está up (`docker compose ps`) e se nenhuma outra aplicação está ocupando as portas 5005/5006.
- **Breakpoint ignorado:** verifique se o código foi compilado após sua alteração e se o módulo selecionado na configuração de debug corresponde ao serviço (Ex.: não usar o projeto de contas para depurar a API de identidade).
- **Depuração no início da aplicação:** altere `suspend=n` para `suspend=y` em `infraestrutura/<ambiente>/docker-compose.yml`, na linha `JAVA_OPTS`, antes de subir o container.

## 6. URLs de Swagger e credenciais

| Ambiente | Serviço | URL Swagger UI | Usuário | Senha |
|----------|---------|----------------|---------|-------|
| Dev      | API Identidade | `http://localhost:8081/swagger-ui/index.html` | (Básico liberado) | (não requer) |
| Dev      | API Contas     | `http://localhost:8082/swagger-ui/index.html` | (Básico liberado) | (não requer) |
| HML      | API Identidade | `http://localhost:18081/swagger-ui/index.html` | `swagger` | `swagger-hml` |
| HML      | API Contas     | `http://localhost:18082/swagger-ui/index.html` | `swagger` | `swagger-hml` |

Observações:

- Os endpoints OpenAPI JSON estão disponíveis em `/v3/api-docs`.
- Em homologação (`hml`), o Swagger exige autenticação Basic: clique em “Authorize” (ícone do cadeado) e informe usuário/senha acima.
- Para alterar usuário ou senha de homologação, edite as chaves `documentacao.swagger.usuario` e `documentacao.swagger.senha` em `modulos/api-identidade-eickrono/src/main/resources/application-hml.yml` e `modulos/api-contas-eickrono/src/main/resources/application-hml.yml`, rode `mvn package` e reconstrua os containers (`docker compose build` / `up -d`).
- Tanto em dev quanto em hml, os endpoints protegidos requerem um JWT válido: após se autenticar, clique em “Authorize”, selecione `bearer-jwt` e informe `Bearer <token>`. Você pode obter tokens via Keycloak (ex.: fluxo Authorization Code pelo app/BFF ou `curl` no `token` endpoint com cliente confidencial configurado).
- Para facilitar testes locais, adicione tokens recentes na aba `Authorize`. Se mudar o token durante a sessão, clique em “Logout” no modal antes de colar o novo valor.

### Sequência rápida após mudanças no código

Para evitar erros de “módulo não encontrado” ou diretório incorreto, execute os comandos sempre a partir da raiz do repositório:

```bash
cd /Users/thiago/Desenvolvedor/eickrono_autenticacao
mvn -pl modulos/api-identidade-eickrono,modulos/api-contas-eickrono -am clean package
cd infraestrutura/dev
docker compose build api-identidade-eickrono api-contas-eickrono
docker compose up -d api-identidade-eickrono api-contas-eickrono
```

> Ajuste o caminho se o repositório estiver clonado em outra pasta.

### Como gerar um JWT para testes

1. Autentique-se no Keycloak local (`http://localhost:8080/`) com usuário administrador definido no `.env`.
2. Garanta que exista um cliente confidencial com `client_id` (ex.: `app-flutter-local`) e habilite o fluxo `Authorization Code` ou `Client Credentials`, conforme o cenário.
3. Para testes rápidos via linha de comando, utilize `curl`:

```bash
curl -X POST http://localhost:8080/realms/dev/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'client_id=app-flutter-local' \
  -d 'grant_type=password' \
  -d 'username=<usuario>' \
  -d 'password=<senha>' \
  -d 'scope=openid contas:ler identidade:ler'
```

4. Copie o valor de `access_token` da resposta e cole no modal `Authorize` como `Bearer <token>`.

> Em `hml`, troque a URL para `https://hml-identidade.eickrono.com.br/realms/homologacao/...` e utilize credenciais próprias do ambiente.
- Caso esteja usando o ambiente `hml`, lembre-se de atualizar seu `/etc/hosts` ou proxy corporativo se precisar testar com o hostname externo (`hml-identidade.eickrono.com.br`).

## 7. Fluxo resumido para o dia a dia

1. `mvn -pl modulos/api-identidade-eickrono,modulos/api-contas-eickrono -am package -DskipTests`
2. `cd infraestrutura/dev` (ou `hml`) e `docker compose up --build -d`
3. Criar/abrir a configuração de depuração remota no Eclipse e conectar.
4. Usar o Swagger correspondente para exercitar as rotas e validar os breakpoints.
5. Finalizado o debug, `docker compose down -v` e feche a sessão no Eclipse.

Seguindo estes passos, você terá o ambiente completo pronto para depurar, validar integrações e testar endpoints, sem depender de configurações externas.

## 8. Atualizando Java ou configurações Docker

### Alteração de versão do Java

1. Ajuste a versão no `pom.xml` raíz (propriedade `java.version`) e nos módulos se houver override.
2. Atualize os Dockerfiles que usam uma imagem base Temurin:
   - Em `modulos/api-identidade-eickrono/Dockerfile` e `modulos/api-contas-eickrono/Dockerfile`, substitua `eclipse-temurin:21-jre-alpine` pela tag correspondente (ex.: `22-jre-alpine`).
   - Se o Keycloak exigir uma versão específica, valide nas notas oficiais antes de alterar o Docker Compose.
3. Refaça o build local:
   ```bash
   mvn -pl modulos/api-identidade-eickrono,modulos/api-contas-eickrono -am clean package
   ```
4. Recrie as imagens Docker com cache limpo (dentro da pasta `infraestrutura/<ambiente>`):
   ```bash
   docker compose build --no-cache api-identidade-eickrono api-contas-eickrono
   docker compose up -d api-identidade-eickrono api-contas-eickrono
   ```
   > Execute cada linha separadamente (ou encadeie com `&&`); combinar tudo em uma única linha com texto intermediário causa erros nos parâmetros.

### Alteração em configurações do Docker

- **Variáveis `.env`**: após editar valores, execute `docker compose down` e depois `docker compose up -d` para aplicar.
- **Dockerfile**: qualquer modificação exige novo build (`docker compose build`) antes do `up`.
- **Compose override**: se criar arquivos `docker-compose.override.yml`, mantenha as portas de depuração consistentes e documente as alterações para não confundir o time.

## 9. Recarregando containers pelo terminal

Use estes comandos para renovar o ambiente sem depender da GUI do Docker Desktop:

```bash
# (executar dentro de infraestrutura/dev ou infraestrutura/hml)

# Atualiza os JARs
mvn -pl modulos/api-identidade-eickrono,modulos/api-contas-eickrono -am package -DskipTests

# Reconstrói as imagens (após mudanças no Dockerfile ou em dependências)
docker compose build api-identidade-eickrono api-contas-eickrono

# Reinicia apenas as APIs, mantendo banco e Keycloak
docker compose up -d api-identidade-eickrono api-contas-eickrono

# Força reinício rápido de um container específico
docker compose restart api-identidade-eickrono
```
> Importante: rode os comandos um por vez; não inclua a palavra “e” no terminal (ex.: `docker compose build ... && docker compose up -d ...`).

Em casos de comportamento estranho, faça um ciclo completo: `docker compose down -v` seguido de `docker compose up --build -d` para descartar volumes e evitar resíduos.

## 10. Testes recomendados

Após qualquer alteração relevante:

1. **Testes automatizados Maven**  
   - `mvn verify` para garantir que unitários, integrações, Checkstyle e SpotBugs continuam passando.
2. **Health checks**  
   - `curl http://localhost:8081/actuator/health` (dev) ou `curl http://localhost:18081/actuator/health` (hml).  
   - Repita para a API de contas (`8082` / `18082`).
3. **Fluxos via Swagger**  
   - Acesse as URLs da tabela da seção 6, faça login com as credenciais informadas e execute endpoints básicos (ex.: listar contas, registrar dispositivo).
4. **Logs**  
   - Use `docker compose logs -f api-identidade-eickrono` para confirmar que o aplicativo carregou sem erros e que o agente JDWP iniciou (procure “Listening for transport dt_socket”).
5. **Depuração**  
   - Anexe o Eclipse e valide que um breakpoint simples (ex.: método `controller`) é atingido ao chamar o endpoint correspondente.

Esses passos asseguram que as mudanças foram propagadas corretamente para os containers e que o ambiente continua consistente para outros membros da equipe.
