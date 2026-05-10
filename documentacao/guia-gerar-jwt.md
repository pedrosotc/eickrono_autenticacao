# Guia: Gerar um JWT para testes

Este passo a passo foi escrito para quem está gerando tokens pela primeira vez. Ele mostra como usar o Keycloak local (ambiente `dev`) e, ao final, explica as diferenças para homologação (`hml`).

### Mapa rápido dos passos

| Etapa | Objetivo | Fase | Frequência |
|-------|----------|------|-----------|
| 1. Preparar o ambiente | Garantir que o stack `dev` (Keycloak + APIs) está em execução | Configuração | Sempre que iniciar um novo ciclo de testes |
| 2. Entrar no console administrativo | Acessar o Keycloak como administrador e selecionar o realm correto | Configuração | Toda vez que precisar criar/ajustar clientes ou usuários |
| 3. Conferir/criar o cliente confidencial | Registrar o aplicativo/integrador que vai receber tokens | Configuração | Uma vez por cliente (ajuste apenas quando mudar requisitos) |
| 4. Guardar o segredo do cliente | Obter o `client_secret` para fluxos sem usuário | Configuração | Sempre que criar o cliente ou regerar o segredo |
| 5. Garantir que os escopos estejam disponíveis | Associar os escopos/roles que as APIs exigem | Configuração | Uma vez por cliente; revise ao surgir novo escopo |
| 6. Criar usuário de testes | Provisionar credenciais humanas para fluxos `password`/Authorization Code | Configuração | Quando precisar simular usuários novos |
| 7. Emitir token (`password`) | Gerar token usando usuário/senha (QA manual) | Teste | Quando quiser testar endpoints sob contexto de usuário |
| 8. Emitir token (`client_credentials`) | Gerar token de serviço (sem usuário) | Teste | Quando validar integrações servidor-servidor |
| 9. Usar o token nas APIs | Aplicar o token via Swagger/cURL | Teste | Todas as chamadas que forem exercitadas |

> Observação: os comandos de linha de comando a seguir utilizam `jq` para manipular JSON. Em macOS, instale com `brew install jq`; em Debian/Ubuntu, `sudo apt-get install jq`.
> O realm padrão do Keycloak local agora chama-se `eickrono`.

## 1. Preparar o ambiente

1. Certifique-se de que o Docker Compose de desenvolvimento está rodando:
   ```bash
   cd infraestrutura/dev
   docker compose up --build -d
   ```
2. Abra `http://localhost:8080/` no navegador. Você deve ver a tela inicial do Keycloak com o botão **Administration Console**.

**Validação rápida (CLI)**
```bash
# Confere se o container do Keycloak subiu e está healthy
cd infraestrutura/dev
docker compose ps eickrono-keycloak-dev

# Alternativa: testa se o endpoint responde com HTTP 200
curl -I http://localhost:8080
```

## 2. Entrar no console administrativo

1. Clique em **Administration Console**.
2. Informe o usuário e a senha do administrador configurados nas variáveis `KEYCLOAK_ADMIN` e `KEYCLOAK_ADMIN_PASSWORD` do arquivo `.env`.
3. Após entrar, verifique no canto superior esquerdo se o *realm* selecionado é `eickrono`. Caso veja outro nome, abra o seletor (nome do realm atual) e escolha `eickrono`.

**Comandos equivalentes (kcadm)**
```bash
# Carrega as variaveis do arquivo .env (ajuste o caminho/ambiente conforme necessario)
set -a
source infraestrutura/dev/.env
set +a
REALM=eickrono

# Garante que o Keycloak recebeu as credenciais administrativas
docker exec -it eickrono-keycloak-dev /opt/keycloak/bin/kcadm.sh config credentials \
  --server http://localhost:8080 \
  --realm master \
  --user "$KEYCLOAK_ADMIN" \
  --password "$KEYCLOAK_ADMIN_PASSWORD"
```

**Validação**
```bash
# Lista o realm `eickrono` para confirmar acesso
docker exec -it eickrono-keycloak-dev /opt/keycloak/bin/kcadm.sh get realms/${REALM} --fields id,realm,enabled
```

## 3. Conferir (ou criar) o cliente confidencial

1. No menu lateral, clique em **Clients**.
2. Use o campo de busca para localizar o `client_id` desejado (ex.: `app-flutter-local`). Se o cliente já existir, clique nele e pule para a etapa 4.
3. Para criar um novo cliente:
   - Clique em **Create client**.
   - Em **Client ID**, use um nome que identifique claramente a origem e o ambiente (ex.: `app-flutter-local`). No projeto, adotamos a convenção `app-<canal>-<ambiente>` para facilitar auditoria.
   - Em **Client name** (opcional), preencha uma descrição amigável como “App Flutter (dev)”. Esse texto aparece em telas administrativas e ajuda a equipe a reconhecer o cliente.
   - Em **Description**, registre o propósito do cliente (ex.: “Aplicativo Flutter local consumindo APIs Identidade/Contas”). É útil para auditorias futuras.
   - Mantenha **Always display in UI** desmarcado. Habilite apenas se quiser que o cliente apareça na tela de escolha de aplicações do Keycloak; no monorepo isso não é necessário.
   - Mantenha **Client type = OpenID Connect**, o protocolo usado pelas APIs.
   - Clique em **Next**.
   - Na etapa **Capabilities**, configure da seguinte maneira:
     - **Client authentication**: marque. Isso transforma o cliente em confidencial e obriga o uso de `client_secret`. Todas as integrações que acessam as APIs Eickrono devem ser confidenciais.
     - **Standard flow**: marque apenas quando o login passa por navegador (Authorization Code + PKCE), como ocorre com o app Flutter abrindo a tela web. Para integrações servidor-servidor, deixe desmarcado.
     - **Direct access grants**: marque se quiser habilitar `grant_type=password` para testes manuais via `curl`. Em produção evitamos esse fluxo, mas no ambiente `dev` é o caminho mais rápido para QA e depuração.
     - **Service accounts roles**: marque se o cliente executa chamadas “sem usuário” (fluxo `client_credentials`) — padrão para rotinas internas e integrações backend.
     - **Authorization**: deixe desmarcado. Esse recurso habilita políticas de autorização fina dentro do Keycloak (ABAC/RBAC). As APIs Eickrono já implementam suas próprias regras, então ativar aqui só adicionaria complexidade.
     - **Implicit flow**, **Device authorization grant** e demais opções especiais: mantenha desmarcadas, pois não são utilizadas no monorepo.
     - **OAuth 2.0 Device Authorization Grant**: continue desmarcado. Esse fluxo é pensado para dispositivos sem navegador completo (TVs, consoles), cenário que não atendemos hoje.
   - Clique em **Next** para revisar as demais abas:
     - **Login settings**: você pode manter os valores padrão. Só altere se precisar forçar URLs específicas (ex.: `Valid redirect URIs` apontando para a aplicação). Para o app Flutter local, use `http://localhost/*` e `http://127.0.0.1/*` quando testar Authorization Code.
     - **General settings**: valide que o `Root URL` e `Home URL` estão vazios (não precisamos deles). Mantenha `Standard Flow Enabled`, `Direct Access Grants Enabled`, `Service Accounts Enabled` conforme o que marcou na etapa anterior.
     - **Capability config**: confirme se os itens habilitados na primeira tela permanecem marcados. Não é necessário ativar mais nada aqui.
   - Por fim, clique em **Save**.

**Comandos (criação/atualização via CLI)**
```bash
REALM=eickrono
CLIENT_ID=app-flutter-local
CLIENT_NAME="App Flutter (dev)"
CLIENT_DESCRIPTION="Aplicativo Flutter local consumindo APIs Identidade/Contas"

# Verifica se o cliente já existe (usa jq para extrair o ID)
CLIENT_UUID=$(docker exec -i eickrono-keycloak-dev /opt/keycloak/bin/kcadm.sh get clients \
  -r ${REALM} \
  -q clientId="${CLIENT_ID}" \
  --fields id | jq -r '.[0].id // empty')

if [ -z "$CLIENT_UUID" ]; then
  # Cria o cliente confidencial com os fluxos necessários
  docker exec -i eickrono-keycloak-dev /opt/keycloak/bin/kcadm.sh create clients \
    -r ${REALM} \
    -f - <<EOF
{
  "clientId": "${CLIENT_ID}",
  "name": "${CLIENT_NAME}",
  "description": "${CLIENT_DESCRIPTION}",
  "protocol": "openid-connect",
  "publicClient": false,
  "standardFlowEnabled": true,
  "directAccessGrantsEnabled": true,
  "serviceAccountsEnabled": true,
  "redirectUris": [
    "http://localhost/*",
    "http://127.0.0.1/*"
  ]
}
EOF
else
  # Atualiza as configurações principais sem recriar o cliente
  docker exec -i eickrono-keycloak-dev /opt/keycloak/bin/kcadm.sh update "clients/${CLIENT_UUID}" \
    -r ${REALM} \
    -f - <<EOF
{
  "name": "${CLIENT_NAME}",
  "description": "${CLIENT_DESCRIPTION}",
  "standardFlowEnabled": true,
  "directAccessGrantsEnabled": true,
  "serviceAccountsEnabled": true,
  "redirectUris": [
    "http://localhost/*",
    "http://127.0.0.1/*"
  ]
}
EOF
fi
```

**Validação**
```bash
REALM=eickrono
# Lista o cliente e verifica se os fluxos desejados estão habilitados
docker exec -it eickrono-keycloak-dev /opt/keycloak/bin/kcadm.sh get clients \
  -r ${REALM} \
  -q clientId=app-flutter-local \
  --fields 'id,clientId,name,redirectUris,standardFlowEnabled,directAccessGrantsEnabled,serviceAccountsEnabled'
```

## 4. Guardar o segredo do cliente (para client_credentials)

1. Ainda na página do cliente, clique na aba **Credentials**.
2. Em **Client secret**, clique em **Copy** para copiar o valor existente ou em **Regenerate** para criar um novo.
3. Salve o segredo em algum local temporário. Ele será necessário para chamadas com `client_credentials`.

**Comandos**
```bash
REALM=eickrono
CLIENT_ID=app-flutter-local
CLIENT_UUID=$(docker exec -i eickrono-keycloak-dev /opt/keycloak/bin/kcadm.sh get clients \
  -r ${REALM} \
  -q clientId="${CLIENT_ID}" \
  --fields id | jq -r '.[0].id')

# Mostra o segredo atual (não exibe se o cliente for público)
docker exec -it eickrono-keycloak-dev /opt/keycloak/bin/kcadm.sh get "clients/${CLIENT_UUID}" \
  -r ${REALM} \
  --fields secret

# Para regerar e já armazenar em variável
NEW_SECRET=$(docker exec -i eickrono-keycloak-dev /opt/keycloak/bin/kcadm.sh update "clients/${CLIENT_UUID}/client-secret" \
  -r ${REALM} \
  | jq -r '.value')
echo "Novo segredo: ${NEW_SECRET}"
```

**Validação**
```bash
REALM=eickrono
# Confere se o segredo está cadastrado e ativo
docker exec -it eickrono-keycloak-dev /opt/keycloak/bin/kcadm.sh get "clients/${CLIENT_UUID}" \
  -r ${REALM} \
  --fields clientId,secret
```

## 5. Garantir que os escopos estejam disponíveis

1. Com o cliente aberto, use as abas horizontais logo abaixo do título (ex.: **Settings**, **Credentials**, **Client scopes**...) e clique em **Client scopes**.
2. Observe as tabelas **Assigned default client scopes** e **Assigned optional client scopes**. Confira se os escopos que as APIs esperam (`openid`, `contas:ler`, `identidade:ler` etc.) aparecem em uma dessas listas. No projeto deixamos `openid` como *default* e os demais como *optional*, pois chamamos explicitamente via `scope=...`.
3. Caso o escopo não exista ainda:
   - Volte ao menu lateral e clique em **Client scopes** (fora do cliente).
   - Pressione **Create client scope**, informe o nome exato (ex.: `contas:ler`), deixe o tipo como `Default` e clique em **Save**.
   - Retorne ao cliente (menu **Clients** > selecione o `client_id`), entre novamente na aba **Client scopes**, clique em **Add client scope**, escolha o escopo recém-criado e marque como **Optional**.

**Comandos**
```bash
REALM=eickrono
CLIENT_ID=app-flutter-local
CLIENT_UUID=$(docker exec -i eickrono-keycloak-dev /opt/keycloak/bin/kcadm.sh get clients \
  -r ${REALM} \
  -q clientId="${CLIENT_ID}" \
  --fields id | jq -r '.[0].id')

# Lista escopos existentes
docker exec -it eickrono-keycloak-dev /opt/keycloak/bin/kcadm.sh get client-scopes -r ${REALM} --fields id,name

# Cria o escopo se ele ainda não existir
SCOPE_NAME="contas:ler"
docker exec -i eickrono-keycloak-dev /opt/keycloak/bin/kcadm.sh get client-scopes \
  -r ${REALM} \
  -q name="${SCOPE_NAME}" \
  --fields id | jq -e '.[0].id' >/dev/null 2>&1 || \
docker exec -it eickrono-keycloak-dev /opt/keycloak/bin/kcadm.sh create client-scopes \
  -r ${REALM} \
  -s name="${SCOPE_NAME}" \
  -s protocol=openid-connect

# Recupera o ID do escopo e associa como optional ao cliente
SCOPE_ID=$(docker exec -i eickrono-keycloak-dev /opt/keycloak/bin/kcadm.sh get client-scopes \
  -r ${REALM} \
  -q name="${SCOPE_NAME}" \
  --fields id | jq -r '.[0].id')

docker exec -it eickrono-keycloak-dev /opt/keycloak/bin/kcadm.sh create "clients/${CLIENT_UUID}/optional-client-scopes/${SCOPE_ID}" \
  -r ${REALM}

# Para escopos default (ex.: openid), basta trocar "optional-client-scopes" por "default-client-scopes"
```

> Observação: o `kcadm` pode retornar “Resource not found” ao tentar anexar client scopes opcionais em versões recentes do Keycloak. Quando isso ocorrer, conclua manualmente pelo console em **Clients > app-flutter-local > Client scopes > Add client scope**, marcando `contas:ler` e `identidade:ler` como *Optional*. Em seguida, execute o bloco de validação abaixo para confirmar que os escopos apareceram na lista.

**Validação**
```bash
REALM=eickrono
# Mostra os escopos opcionais associados ao cliente
docker exec -it eickrono-keycloak-dev /opt/keycloak/bin/kcadm.sh get "clients/${CLIENT_UUID}/optional-client-scopes" \
  -r ${REALM} \
  --fields name

# Verifica os escopos default
docker exec -it eickrono-keycloak-dev /opt/keycloak/bin/kcadm.sh get "clients/${CLIENT_UUID}/default-client-scopes" \
  -r ${REALM} \
  --fields name
```

## 6. Criar (ou ajustar) um usuário de testes

1. No menu lateral, clique em **Users** e depois em **Add user**.
2. Preencha **Username** (ex.: `teste.dev`). Os demais campos são opcionais neste momento.
3. Clique em **Create**.
4. Abra o usuário recém-criado e vá à aba **Credentials**.
5. Clique em **Set password**, informe a senha desejada, desmarque **Temporary** (para evitar troca obrigatória no primeiro login) e clique em **Save**.
6. Vá para a aba **Role mapping**:
   - Em **Client roles**, selecione o cliente que expõe as permissões (ex.: `api-identidade` ou `api-contas`).
   - Selecione e adicione as roles necessárias (`contas:ler`, `identidade:ler`, `openid` etc.).
   - Caso alguma role não exista, crie em **Clients** > `<cliente>` > **Roles** > **Add role** e repita a atribuição.
   - Preencha também **Email**, **First name** e **Last name** na aba **Details** e marque **Email verified** para evitar o erro “Account is not fully set up”.

**Comandos**
```bash
REALM=eickrono
USER_USERNAME=teste.dev
USER_PASSWORD='SenhaForte123!'
USER_EMAIL='teste.dev@exemplo.com'

# Cria o usuário se ainda não existir
USER_ID=$(docker exec -i eickrono-keycloak-dev /opt/keycloak/bin/kcadm.sh get users \
  -r ${REALM} \
  -q username="${USER_USERNAME}" \
  --fields id | jq -r '.[0].id // empty')

if [ -z "$USER_ID" ]; then
  USER_ID=$(docker exec -i eickrono-keycloak-dev /opt/keycloak/bin/kcadm.sh create users \
    -r ${REALM} \
    -s username="${USER_USERNAME}" \
    -s enabled=true \
    -i)
fi

# Define a senha definitiva
docker exec -it eickrono-keycloak-dev /opt/keycloak/bin/kcadm.sh set-password \
  -r ${REALM} \
  --userid "${USER_ID}" \
  --new-password "${USER_PASSWORD}" \
  --temporary false

# Preenche dados básicos e marca o e-mail como verificado
docker exec -it eickrono-keycloak-dev /opt/keycloak/bin/kcadm.sh update "users/${USER_ID}" \
  -r ${REALM} \
  -s email="${USER_EMAIL}" \
  -s emailVerified=true \
  -s firstName='Teste' \
  -s lastName='Dev'

# Atribui roles do cliente
CLIENT_ID=app-flutter-local
docker exec -it eickrono-keycloak-dev /opt/keycloak/bin/kcadm.sh add-roles \
  -r ${REALM} \
  --uusername "${USER_USERNAME}" \
  --cclientid "${CLIENT_ID}" \
  --rolename contas:ler \
  --rolename identidade:ler \
  --rolename openid
```

**Validação**
```bash
REALM=eickrono
# Confere dados básicos do usuário
docker exec -it eickrono-keycloak-dev /opt/keycloak/bin/kcadm.sh get "users/${USER_ID}" -r ${REALM} --fields username,enabled

# Lista as roles de cliente associadas
CLIENT_UUID=$(docker exec -i eickrono-keycloak-dev /opt/keycloak/bin/kcadm.sh get clients -r ${REALM} -q clientId="${CLIENT_ID}" --fields id | jq -r '.[0].id')
docker exec -it eickrono-keycloak-dev /opt/keycloak/bin/kcadm.sh get "users/${USER_ID}/role-mappings/clients/${CLIENT_UUID}" \
  -r ${REALM} \
  --fields roles
```

## 7. Emitir token com `grant_type=password`

Use quando quiser simular um usuário real com login e senha.

```bash
curl -X POST http://localhost:8080/realms/eickrono/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'client_id=app-flutter-local' \
  -d 'grant_type=password' \
  -d 'username=<usuario_ou_email>' \
  -d 'password=<senha>' \
  -d 'scope=openid contas:ler identidade:ler'
```

- Substitua `<usuario_ou_email>` e `<senha>` pelos valores definidos na etapa 6. Se o realm estiver configurado para login por e-mail, use `teste.dev@exemplo.com`.
- Se precisar solicitar escopos adicionais, inclua na lista separada por espaço.
- O retorno conterá `access_token`, `refresh_token`, `expires_in` etc. Copie o valor do `access_token`.
- Caso receba `invalid_grant` ou a mensagem “Account is not fully set up”, confirme no console se o usuário tem e-mail verificado, nome/sobrenome preenchidos e nenhuma *required action* pendente; ajuste e repita a requisição.

**Validação**
```bash
RESPONSE=$(curl -s http://localhost:8080/realms/eickrono/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'client_id=app-flutter-local' \
  -d 'grant_type=password' \
  -d "username=${USER_EMAIL:-${USER_USERNAME}}" \
  -d "password=${USER_PASSWORD}" \
  -d 'scope=openid contas:ler identidade:ler')

ACCESS_TOKEN=$(echo "$RESPONSE" | jq -r '.access_token')
echo "Token emitido? $( [ -n "$ACCESS_TOKEN" ] && echo SIM || echo NAO )"

# Testa um endpoint protegido usando o token
curl -s -o /dev/null -w '%{http_code}\n' \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  http://127.0.0.1:8081/actuator/health

# Se o endpoint acima exigir autenticacao diferente, teste uma rota funcional da API (ex.: /api/conta/vinculos-organizacionais) para validar os escopos.
```

## 8. Emitir token com `grant_type=client_credentials`

Use quando o serviço não tem usuário humano (ex.: integração servidor-servidor).

```bash
CLIENT_SECRET='<copie_da_aba_Credentials>'
curl -X POST http://localhost:8080/realms/eickrono/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'client_id=app-flutter-local' \
  -d "client_secret=${CLIENT_SECRET}" \
  -d 'grant_type=client_credentials' \
  -d 'scope=openid contas:ler identidade:ler'
```

- Substitua `CLIENT_SECRET` pelo valor copiado na etapa 4.
- O Keycloak retornará um `access_token` sem `refresh_token` (como esperado para client credentials).

**Validação**
```bash
SERVICE_RESPONSE=$(curl -s http://localhost:8080/realms/eickrono/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'client_id=app-flutter-local' \
  -d "client_secret=${NEW_SECRET:-$CLIENT_SECRET}" \
  -d 'grant_type=client_credentials' \
  -d 'scope=openid contas:ler identidade:ler')

SERVICE_TOKEN=$(echo "$SERVICE_RESPONSE" | jq -r '.access_token')
echo "Token de serviço emitido? $( [ -n "$SERVICE_TOKEN" ] && echo SIM || echo NAO )"

# Verifica acesso a um endpoint que aceita escopo de serviço
curl -s -o /dev/null -w '%{http_code}\n' \
  -H "Authorization: Bearer ${SERVICE_TOKEN}" \
  http://localhost:8082/actuator/health

# Se o health ainda responder 401, valide se o serviço está em execução ou teste um endpoint que realmente exija os escopos `contas:ler`/`identidade:ler`.
```

## 9. Usar o token para testar as APIs

1. Abra o Swagger desejado (`http://127.0.0.1:8081/swagger-ui/index.html` para Autenticacao, `http://127.0.0.1:8082/swagger-ui/index.html` para Contas).
2. Clique no botão **Authorize** (cadeado).
3. Selecione `bearer-jwt` e preencha com `Bearer <access_token>`.
4. Clique em **Authorize**. A partir daí, todas as chamadas feitas pelo Swagger usarão o token informado.
5. Para trocar o token, clique em **Authorize** novamente e use **Logout** antes de colar o novo valor.

**Comandos**
```bash
# Reutiliza o ACCESS_TOKEN ou SERVICE_TOKEN obtido acima
curl -H "Authorization: Bearer ${ACCESS_TOKEN}" http://127.0.0.1:8081/api/conta/vinculos-organizacionais

# Para a API de contas:
curl -H "Authorization: Bearer ${SERVICE_TOKEN}" http://localhost:8082/contas
```

**Validação**
```bash
# Verifica se a chamada retornou HTTP 200
curl -s -o /dev/null -w '%{http_code}\n' \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  http://127.0.0.1:8081/api/conta/vinculos-organizacionais
```

## 10. Adaptando para homologação (`hml`)

1. **Suba o stack de homologação local**  
   ```bash
   cd infraestrutura/hml
   docker compose up --build -d
   ```
   Esse ambiente replica os certificados e variáveis usados em homologação real.

2. **Ajuste DNS/hosts para o hostname externo**  
   - Adicione a linha `127.0.0.1  oidc-hml.eickrono.store` em `/etc/hosts` (ou configure seu proxy corporativo) para que o navegador resolva o domínio quando estiver testando localmente.

3. **Acesse o admin do Keycloak de homologação**  
   - URL: `https://oidc-hml.eickrono.store/admin/`  
   - Realm: `eickrono`  
   - Credenciais: utilize o usuário administrador exclusivo de `hml` (definido no cofre corporativo). Não reaproveite o admin de `dev`.

4. **Replique o cliente com o nome do ambiente**  
   - Siga as etapas da seção 3 escolhendo um `client_id` coerente (ex.: `app-flutter-hml`).  
   - Garanta que os redirecionamentos (`Valid redirect URIs`) apontem para os domínios de homologação (`https://auth-hml.eickrono.store/*`, `http://localhost/*` se precisar testar localmente via tunnel).
   - Segredo, roles e escopos são próprios desse ambiente: gere novos valores em **Credentials** e atribua as roles conforme a política de homologação.

5. **Atualize as requisições `curl`**  
   ```bash
   CLIENT_SECRET='<segredo_de_hml>'
   curl -X POST https://oidc-hml.eickrono.store/realms/eickrono/protocol/openid-connect/token \
     -H 'Content-Type: application/x-www-form-urlencoded' \
     -d 'client_id=app-flutter-hml' \
     -d "client_secret=${CLIENT_SECRET}" \
     -d 'grant_type=client_credentials' \
     -d 'scope=openid contas:ler identidade:ler'
   ```
   - Substitua `grant_type` e escopos conforme o fluxo que estiver exercitando.

6. **Use credenciais e usuários específicos de homologação**  
   - Crie usuários de teste próprios para `hml` (ex.: `teste.hml`).  
   - Configure senhas e escopos respeitando as mesmas regras de produção (sem habilitar `Temporary = On`).

## 11. Adaptando para produção (`prd`)

1. **Acesso restrito**  
   - O Keycloak de produção (realm `eickrono`) não fica exposto para uso local. Apenas administradores autorizados podem acessar via VPN corporativa e console oficial. Solicite acesso ao time de Segurança antes de qualquer alteração.

2. **Criação e ajustes de clientes**  
   - Siga o mesmo checklist do ambiente `hml`, porém utilize `client_id` com o sufixo `-prd` (ex.: `app-flutter-prd`).  
   - `Valid redirect URIs` e `Web origins` devem apontar apenas para domínios oficiais (`https://id.eickrono.com/*`, `https://oidc.eickrono.com/*`).  
   - Preserve o mínimo de capabilities: evite habilitar `Direct access grants` em produção; use principalmente `Standard flow` (para apps interativos) e `Service accounts roles` (para integrações sem usuário).

3. **Segredos e guarda segura**  
   - Gere os segredos em **Credentials** e armazene no cofre corporativo (ex.: HashiCorp Vault / AWS Secrets Manager).  
   - Nunca comite segredos de produção em repositórios.

4. **Usuários e roles**  
   - Usuários finais não são gerenciados manualmente em produção; utilize somente usuários de serviço estritamente necessários.  
   - As roles (ex.: `contas:ler`, `identidade:ler`) devem estar alinhadas às políticas aprovadas pelo time jurídico/segurança.

5. **Requisições de token**  
   - Os endpoints seguem o mesmo padrão, trocando o host e o realm:  
     `https://oidc.eickrono.com/realms/eickrono/protocol/openid-connect/token`.  
   - Todos os chamados devem ocorrer a partir de infra autorizada (BFF, backoffice, serviços internos). Evite rodar `curl` diretamente na máquina local sem VPN/aprovação.

6. **Auditoria e monitoração**  
   - Registre cada cliente criado com justificativa e responsável. O monitoramento de produção dispara alertas se surgirem clientes novos sem ticket associado.  
   - Se um segredo for vazado, revogue o cliente imediatamente e acione o processo de resposta a incidentes.

7. **Boas práticas finais**  
   - Repita em produção apenas o que já foi validado em `hml`.  
   - Mantenha documentação atualizada com os `client_id` ativos, responsáveis e escopos concedidos.

## 11. Dicas de validação

- Se o token não tiver o escopo esperado, as APIs vão retornar 403. Use um decodificador JWT (ex.: `jwt.io`) para conferir o conteúdo.
- A cada mudança nos escopos ou roles, gere um novo token; tokens antigos não recebem permissões retroativas.
- Caso receba erro `invalid_client`, confira se o cliente está com **Client authentication** ligado e se o segredo está correto.
