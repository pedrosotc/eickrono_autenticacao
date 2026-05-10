# Guia de depuração com Eclipse

Este guia mostra como preparar os ambientes Docker (desenvolvimento e homologação) e configurar o Eclipse para depurar os projetos Java do ecossistema **Eickrono Autenticação**. O passo a passo assume nível júnior e cobre desde o build até o uso dos endpoints Swagger para validação manual.

## Nota de nomenclatura operacional

Neste guia, nomes como `eickrono-autenticacao` e `api-contas-eickrono`
aparecem porque ainda são os **nomes operacionais atuais** de serviços,
containers e artefatos do `docker compose`.

Esses nomes:

- descrevem o runtime atual;
- não substituem os nomes físicos dos módulos Maven/IDE, que agora são
  `modulo-eickrono-autenticacao` e `modulo-eickrono-keycloak`;
- não definem a arquitetura final aprovada;
- não mudam a decisão de que `autenticacao` deve convergir para projeto simples
  e borda pública final do app.

## 1. Pré-requisitos locais

1. JDK Temurin 21 e Maven 3.9+ (`mvn -version` deve funcionar).
2. Docker Desktop ou Docker Engine + Docker Compose Plugin (`docker compose version`).
3. Eclipse IDE for Enterprise Java Developers 2023‑12 ou superior (com suporte a Maven e Remote Java Application).

> Dica: importe `eickrono-autenticacao-servidor` como projeto Maven principal.
> Dentro dele, o Eclipse deve enxergar os módulos
> `modulo-eickrono-autenticacao` e `modulo-eickrono-keycloak`. Importe
> `eickrono-identidade-servidor` e `eickrono-contas-servidor` apenas se você
> também precisar depurar esses serviços externos.

## 2. Build dos módulos antes de subir os containers

Os Dockerfiles das APIs esperam que os artefatos `.jar` já tenham sido construídos. Rode uma vez (após cada mudança de código) o comando abaixo na raiz do repositório:

```bash
cd /Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor
mvn -pl modulos/modulo-eickrono-autenticacao -am clean package -DskipTests
```

Os artefatos serão gerados em `target/`, prontos para serem copiados ou montados pelas imagens Docker.

> Se houver atualização de dependências Java (por exemplo, mudança de versão do JDK ou adição de bibliotecas nativas), repita o `clean package` para garantir que os JARs reflitam a nova configuração e atualize os Dockerfiles conforme descrito na seção 8.

## 3. Ambiente Docker de desenvolvimento

1. Vá para a pasta `infraestrutura/dev`.
2. Revise o arquivo `.env` e ajuste portas ou segredos, se necessario.
3. Suba os serviços com suporte a depuração:
   ```bash
   docker compose up --build -d
   ```
4. Aguarde até que `eickrono-keycloak` (Keycloak) e as APIs indiquem `healthy` em `docker compose ps`. Logs podem ser acompanhados via `docker compose logs -f eickrono-autenticacao`.
5. Parar o ambiente: `docker compose down -v` (remove volumes locais).

### Portas expostas em desenvolvimento

- `localhost:8080`: Keycloak (`realm` eickrono).
- `127.0.0.1:8081`: API pública de autenticação (HTTP).
- `localhost:8082`: API Contas (HTTP, opcional).
- `localhost:5005`: depuração remota da API pública de autenticação.
- `localhost:5006`: depuração remota da API Contas (opcional).

Se precisar alterar as portas de depuração, edite `API_AUTENTICACAO_DEBUG_PORT` ou `API_CONTAS_DEBUG_PORT` em `.env` antes de subir os containers. Os nomes `API_IDENTIDADE_*` ainda funcionam como fallback transitório. Para pausar a JVM logo no início (`suspend=y`), ajuste a linha `JAVA_OPTS` correspondente em `docker-compose.yml`.

## 4. Ambiente Docker de homologação local

O cenário `hml` replica a configuração com parâmetros de homologação (URLs, schemas, certificados).

1. Vá para `infraestrutura/hml`.
2. Revise o arquivo `.env` para ajustes locais do ambiente `hml`, se necessario.
3. Execute:
   ```bash
   docker compose up --build -d
   ```
4. Acompanhe a inicialização com `docker compose logs -f`.
5. Parar os serviços: `docker compose down -v`.

### Portas expostas em homologação local

- `localhost:8080`: Keycloak (hostname configurado para `https://oidc-hml.eickrono.store`).
- `localhost:18081`: API pública de autenticação (HTTP).
- `localhost:18082`: API Contas (HTTP, opcional).
- `localhost:5005`: depuração remota da API pública de autenticação.
- `localhost:5006`: depuração remota da API Contas (opcional).

Os containers reutilizam o mesmo par de portas de depuração. Evite subir os ambientes `dev` e `hml` ao mesmo tempo ou personalize as variáveis em `.env`.

## 5. Configurando a depuração remota no Eclipse

### Checklist antes de anexar o depurador

1. Construa os artefatos atualizados nos repositórios standalone. O Dockerfile/compose usa os JARs em `target`, então eles precisam estar frescos.
2. Suba ou reinicie o serviço no Docker para aplicar o `JAVA_OPTS` com o agente de debug:
   ```bash
   cd infraestrutura/dev        # ou infraestrutura/hml
   docker compose up --build -d eickrono-autenticacao
   ```
   Sempre que mudar variáveis ou precisar “resetar” o JDWP, use `docker compose restart <serviço>`.
3. Confirme que a porta está ouvindo na máquina host:
   ```bash
   nc -vz localhost 5005  # API pública de autenticação
   nc -vz localhost 5006  # Contas (opcional, se o serviço estiver incluído)
   ```
   Se quiser ver o parâmetro na JVM, rode `docker exec eickrono-api-autenticacao-dev ps -o pid,args | grep jdwp`.

### Criando a configuração no Eclipse

1. Certifique-se de que o Docker Compose correspondente (`dev` ou `hml`) está rodando.
2. No Eclipse, abra `Run > Debug Configurations...`.
3. Em `Remote Java Application`, clique em `New`.
4. Preencha:
   - **Name:** escolha algo como `API Autenticação (Docker)` ou `API Contas (Docker)`.
   - **Project:** selecione `modulo-eickrono-autenticacao` ou `eickrono-contas-servidor`.
   - **Connection Type:** `Standard (Socket Attach)`.
   - **Host:** `localhost`.
   - **Port:** `5005` para Autenticação, `5006` para Contas (ou o valor configurado nos `.env`).
5. Salve e clique em `Debug`. O status “Connected to VM” aparecerá na aba **Debug**.
6. Adicione breakpoints nos arquivos Java desejados. A próxima chamada HTTP que atingir o código pausará a execução.

### Depurando múltiplos módulos

- Crie duas configurações diferentes (Autenticação e Contas, se Contas estiver ativo) e mantenha ambas salvas.
- Você pode conectar a mais de um módulo simultaneamente; o Eclipse abrirá cada sessão na árvore de debug.
- Para atualizar o código durante a sessão, recompile o módulo (`mvn package`) e reinicie apenas o serviço afetado (`docker compose restart eickrono-autenticacao`).

### Dicas de diagnóstico

- **Failed to attach / handshake timeout:** significa que a JVM não respondeu ao protocolo JDWP. Reinicie o serviço (`docker compose restart eickrono-autenticacao`), garanta que o `JAVA_OPTS` da compose contém `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005` e teste a porta com `nc -vz localhost 5005`.
- **Sessão travada por anexos anteriores:** verifique se já existe conexão ativa usando `docker exec eickrono-api-autenticacao-dev lsof -i :5005`. Se houver, reinicie o container.
- **Conexão recusada:** confirme se o container está up (`docker compose ps`) e se nenhuma outra aplicação está ocupando as portas 5005/5006.
- **Breakpoint ignorado:** verifique se o código foi compilado após sua alteração e se o módulo selecionado na configuração de debug corresponde ao serviço (Ex.: não usar o projeto de contas para depurar a API pública de autenticação).
- **Depuração no início da aplicação:** altere `suspend=n` para `suspend=y` em `infraestrutura/<ambiente>/docker-compose.yml`, na linha `JAVA_OPTS`, antes de subir o container.

### Verificações rápidas quando o attach insiste em falhar

1. **Confirme se o agente está ouvindo:** `docker logs eickrono-api-autenticacao-dev | grep -m1 'Listening for transport'`. A saída deve conter `Listening for transport dt_socket at address: 5005`. Se não aparecer, o container foi iniciado sem `JAVA_OPTS` — refaça o `docker compose up --build -d`.
2. **Inspecione o comando efetivo:** `docker exec eickrono-api-autenticacao-dev cat /proc/1/cmdline | tr '\0' ' '`. Busque por `-agentlib:jdwp=...`. Ausência desse trecho significa que as variáveis não chegaram ao processo (verifique `.env` e compose).
3. **Teste o handshake JDWP diretamente (rode o comando abaixo em um terminal local):**  
   ```shell
   printf 'JDWP-Handshake' | nc -w 5 localhost 5005
   ```
   Resultado esperado: o texto `JDWP-Handshake` deve reaparecer no terminal.  
   - Se o comando retornar `Connection refused`, o agente ainda não está ouvindo (espere o container terminar de subir ou reinicie o serviço).  
   - Se não houver saída alguma e o terminal encerrar imediatamente, houve reset de conexão; reinicie o serviço para limpar sessões anteriores e tente novamente.  
   > Dica: se `nc` não estiver disponível na sua máquina, instale o pacote `netcat` ou utilize uma VM/container com a ferramenta. Caso continue preferindo uma verificação detalhada, é possível repetir o teste com um script simples em qualquer linguagem que envie a string `JDWP-Handshake` pela porta 5005 e leia a resposta.
4. **Cheque conexões pendentes:** `lsof -nP -iTCP:5005` (host) ou `docker exec eickrono-api-autenticacao-dev lsof -i :5005`. Se aparecer `ESTABLISHED`, encerre o processo listado ou reinicie o container para liberar a porta.

## 6. URLs de Swagger e credenciais

| Ambiente | Serviço | URL Swagger UI | Usuário | Senha |
|----------|---------|----------------|---------|-------|
| Dev      | API Autenticação | `http://127.0.0.1:8081/swagger-ui/index.html` | (Básico liberado) | (não requer) |
| Dev      | API Contas       | `http://localhost:8082/swagger-ui/index.html` | (Básico liberado) | (não requer) |
| HML      | API Autenticação | `http://localhost:18081/swagger-ui/index.html` | `swagger` | `swagger-hml` |
| HML      | API Contas       | `http://localhost:18082/swagger-ui/index.html` | `swagger` | `swagger-hml` |

Observações:

- Os endpoints OpenAPI JSON estão disponíveis em `/v3/api-docs`.
- Em homologação (`hml`), o Swagger exige autenticação Basic: clique em “Authorize” (ícone do cadeado) e informe usuário/senha acima.
- Para alterar usuário ou senha de homologação, edite as chaves `documentacao.swagger.usuario` e `documentacao.swagger.senha` em `../eickrono-autenticacao-servidor/modulos/modulo-eickrono-autenticacao/src/main/resources/application-hml.yml` e `../eickrono-contas-servidor/src/main/resources/application-hml.yml`, rode `mvn package` e reconstrua os containers (`docker compose build` / `up -d`).
- Tanto em dev quanto em hml, os endpoints protegidos requerem um JWT válido: após se autenticar, clique em “Authorize”, selecione `bearer-jwt` e informe `Bearer <token>`. Você pode obter tokens via Keycloak (ex.: fluxo Authorization Code pelo app/BFF ou `curl` no `token` endpoint com cliente confidencial configurado).
- Para facilitar testes locais, adicione tokens recentes na aba `Authorize`. Se mudar o token durante a sessão, clique em “Logout” no modal antes de colar o novo valor.

### Sequência rápida após mudanças no código

Para evitar erros de “módulo não encontrado” ou diretório incorreto, execute os comandos sempre a partir da raiz do repositório:

```bash
cd /Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/modulos/modulo-eickrono-autenticacao && mvn clean package
cd /Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/infraestrutura/dev
docker compose build eickrono-autenticacao
docker compose up -d eickrono-autenticacao
```

> Ajuste o caminho se o repositório estiver clonado em outra pasta.

### Como gerar um JWT para testes

Se estiver começando agora e precisa de um guia passo a passo com cada clique dentro do Keycloak, leia [[guia-gerar-jwt]]. O documento cobre criação de clientes, escopos, usuários e os fluxos `password`/`client_credentials`.

Para uma visão rápida:

1. Entre no Keycloak local (`http://localhost:8080/`) com o administrador configurado no `.env` e confirme que o cliente confidencial (ex.: `app-flutter-local`) tem os fluxos desejados habilitados.
2. Garanta que o usuário ou a service account recebeu as roles/escopos necessários (`contas:ler`, `identidade:ler`, `openid` etc.).
3. Emita o token com `curl` (exemplo abaixo) ou pelo app/BFF e informe `Bearer <token>` no modal `Authorize` do Swagger.

```bash
curl -X POST http://localhost:8080/realms/eickrono/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'client_id=app-flutter-local' \
  -d 'grant_type=password' \
  -d 'username=<usuario>' \
  -d 'password=<senha>' \
  -d 'scope=openid contas:ler identidade:ler'
```

> Em `hml`, ajuste a URL para `https://oidc-hml.eickrono.store/realms/eickrono/...`, use as credenciais do ambiente e lembre-se de atualizar `/etc/hosts` ou o proxy corporativo caso precise resolver o hostname externo.

## 7. Fluxo resumido para o dia a dia

1. Empacote `modulo-eickrono-autenticacao` com `mvn -pl modulos/modulo-eickrono-autenticacao -am package -DskipTests`
2. `cd /Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/infraestrutura/dev` (ou `hml`) e `docker compose up --build -d`
3. Criar/abrir a configuração de depuração remota no Eclipse e conectar.
4. Usar o Swagger correspondente para exercitar as rotas e validar os breakpoints.
5. Finalizado o debug, `docker compose down -v` e feche a sessão no Eclipse.

Seguindo estes passos, você terá o ambiente completo pronto para depurar, validar integrações e testar endpoints, sem depender de configurações externas.

## 8. Atualizando Java ou configurações Docker

### Alteração de versão do Java

1. Ajuste a versão no `pom.xml` raíz (propriedade `java.version`) e nos módulos se houver override.
2. Atualize os Dockerfiles que usam uma imagem base Temurin:
   - Em `./modulos/modulo-eickrono-autenticacao/Dockerfile` e `../eickrono-contas-servidor/Dockerfile` (se contas estiver em uso), substitua `eclipse-temurin:21-jre-alpine` pela tag correspondente (ex.: `22-jre-alpine`).
   - Se o Keycloak exigir uma versão específica, valide nas notas oficiais antes de alterar o Docker Compose.
3. Refaça o build local:
   ```bash
   cd /Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/modulos/modulo-eickrono-autenticacao && mvn clean package
   ```
4. Recrie as imagens Docker com cache limpo (dentro da pasta `infraestrutura/<ambiente>`):
   ```bash
   docker compose build --no-cache eickrono-autenticacao
   docker compose up -d eickrono-autenticacao
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
cd /Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/modulos/modulo-eickrono-autenticacao && mvn package -DskipTests

# Reconstrói as imagens (após mudanças no Dockerfile ou em dependências)
docker compose build eickrono-autenticacao

# Reinicia a API pública de autenticação, mantendo banco e Keycloak
docker compose up -d eickrono-autenticacao

# Força reinício rápido de um container específico
docker compose restart eickrono-autenticacao
```
> Importante: rode os comandos um por vez; não inclua a palavra “e” no terminal (ex.: `docker compose build ... && docker compose up -d ...`).

Em casos de comportamento estranho, faça um ciclo completo: `docker compose down -v` seguido de `docker compose up --build -d` para descartar volumes e evitar resíduos.

## 10. Testes recomendados

Após qualquer alteração relevante:

1. **Testes automatizados Maven**  
   - `mvn verify` para garantir que unitários, integrações, Checkstyle e SpotBugs continuam passando.
2. **Health checks**  
  - `curl http://127.0.0.1:8081/actuator/health` (dev) ou `curl http://127.0.0.1:18081/actuator/health` (hml).
  - Se `api-contas-eickrono` estiver ativo, repita para `8082` / `18082`.
3. **Fluxos via Swagger**  
   - Acesse as URLs da tabela da seção 6, faça login com as credenciais informadas e execute endpoints básicos (ex.: listar contas, registrar dispositivo).
4. **Logs**  
  - Use `docker compose logs -f eickrono-autenticacao` para confirmar que o aplicativo carregou sem erros e que o agente JDWP iniciou (procure “Listening for transport dt_socket”).
5. **Depuração**  
   - Anexe o Eclipse e valide que um breakpoint simples (ex.: método `controller`) é atingido ao chamar o endpoint correspondente.

Esses passos asseguram que as mudanças foram propagadas corretamente para os containers e que o ambiente continua consistente para outros membros da equipe.
