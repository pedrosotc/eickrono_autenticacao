# Guia de rotas de testes para Swagger

Este roteiro organiza a ordem sugerida de chamadas e fornece exemplos de payloads para validar os serviços expostos por `eickrono-autenticacao` e `eickrono-contas-servidor` via Swagger ou cURL.

## Pré-requisitos rápidos
- Ambiente `dev` ativo (`cd infraestrutura/dev && docker compose up --build -d`).
- Containers saudáveis (`docker ps` confirmando `eickrono-keycloak-dev`, `eickrono-api-autenticacao-dev`, `eickrono-api-contas-dev`, `eickrono-postgres-dev`).
- Possibilidade de ler os logs das APIs (para capturar códigos e tokens gerados).
- Usuário de teste e cliente confidencial configurados no Keycloak conforme `documentacao/guia-gerar-jwt.md`.

## Tokens necessários

### 1. Token com usuário (`password grant`)
Use para fluxos que representam o app com um usuário autenticado (precisa de `ROLE_cliente` para acessar rotas protegidas). Siga esta sequência:

1. Entre no Keycloak (`http://localhost:8080`) com o usuário administrador.
2. Abra **Users > teste.dev** (ou o usuário que estiver usando):
   - Preencha os campos **First name**, **Last name** e **Email**.
   - Marque **Email verified**.
   - Confirme na aba **Role mapping** se o cliente `app-flutter-local` tem as roles `identidade:ler`, `vinculos:ler`, `vinculos:escrever`, `contas:ler`, `transacoes:ler`, `openid` e a realm role `cliente`.
   - Se não encontrar essas roles na lista (é comum na primeira configuração):
     1. Crie os **client scopes** em **Client scopes > Create client scope** (`identidade:ler`, `vinculos:ler`, `vinculos:escrever`, `contas:ler`, `transacoes:ler`, `openid`), deixando o tipo como *Default* ou *Optional* conforme sua política.
     2. Associe cada scope ao cliente **app-flutter-local** (aba **Client scopes** do cliente, botão **Add client scope**, marcando como *Optional*).
     3. Para disponibilizar as roles:
        - Em **Clients > eickrono-autenticacao > Roles**, adicione `identidade:ler`, `vinculos:ler`, `vinculos:escrever`.
        - Em **Clients > api-contas-eickrono > Roles**, adicione `contas:ler`, `transacoes:ler`.
        - Em **Realm roles**, crie (se necessário) a role `cliente`.
     4. Volte ao usuário e, em **Role mapping**, utilize **Client Roles** para selecionar `app-flutter-local` e adicionar as novas roles; também adicione a realm role `cliente`.
3. No mesmo cliente (`Clients > app-flutter-local`), valide que os escopos citados acima estão associados como optional/default conforme o guia de JWT.
4. Em um terminal local (com o ambiente `dev` rodando), execute:
```bash
curl -X POST http://localhost:8080/realms/eickrono/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'client_id=app-flutter-local' \
  -d "client_secret=${CLIENT_SECRET}" \
  -d 'grant_type=password' \
  -d 'username=teste.dev@exemplo.com' \
  -d 'password=SenhaForte123!' \
  -d 'scope=openid identidade:ler vinculos:ler vinculos:escrever contas:ler transacoes:ler'
```
   > Ajuste `client_id`, `CLIENT_SECRET`, `username`, `password` ou a lista de escopos se você tiver usado valores diferentes.
5. Copie o campo `access_token` do JSON retornado (é o JWT que será colado no Swagger em **Authorize** → `Bearer <access_token>`).

> **Observação:** estas ações manuais existem apenas para o ambiente de testes. Em produção, o app móvel ou web redireciona o usuário para o Keycloak, que emite o token automaticamente após o login. O cadastro de usuários, associação de roles e configuração de clientes acontecem via pipelines/automatizações administrativas; aqui reproduzimos os passos na mão para acelerar QA local antes de haver uma interface própria.

#### O que cada escopo/role representa

- `openid` — escopo padrão do OpenID Connect que habilita emissão de tokens compatíveis com as bibliotecas OIDC. Sem ele, alguns clientes rejeitam o token.
- `vinculos:ler` — libera leituras autenticadas como `GET /api/conta/vinculos-organizacionais`.
- `vinculos:ler` — permite listar vínculos sociais (`GET /api/conta/redes-sociais`).
- `vinculos:escrever` — requerido para criar vínculos (`POST /api/conta/redes-sociais`).
- `contas:ler` — autoriza a leitura de contas (`GET /contas`, `GET /contas/{id}`).
- `transacoes:ler` — autoriza `GET /transacoes?contaId=...`.
- `cliente` — realm role que mapeia para `ROLE_cliente`, usada pelas APIs para impor o cabeçalho `X-Device-Token` e garantir que se trata de um cliente humano.

Todos esses nomes são customizados pelas nossas APIs e não vêm prontos no Keycloak; por isso é necessário criá-los e associá-los manualmente ou via script antes de testar. Nos ambientes oficiais (hml/prod), esses escopos e roles já devem ser provisionados pelas pipelines de infraestrutura ou pela importação automática do realm; o passo manual é exclusivo do ambiente local/dev quando o realm é iniciado do zero.

#### Como identificar escopos/roles diretamente no código

1. Verifique as classes `SegurancaConfiguracao` de cada serviço em `../eickrono-autenticacao-servidor/modulos/modulo-eickrono-autenticacao` e `../eickrono-contas-servidor`. Elas listam os `requestMatchers` e os `@PreAuthorize` globais; procure por strings começando com `SCOPE_` ou `ROLE_`.
2. Procure anotações `@PreAuthorize` nos controllers (`ContasController`, `TransacoesController` etc.). Os parâmetros usados ali (ex.: `hasAuthority('SCOPE_transacoes:ler')`) indicam escopos que precisam existir no Keycloak.
3. Analise filtros adicionais como `DeviceTokenFilter` em `../eickrono-autenticacao-servidor/modulos/modulo-eickrono-autenticacao`: ele exige que o token tenha `ROLE_cliente`, por isso a realm role `cliente` é obrigatória.
4. Reúna os nomes encontrados nessas verificações; o conjunto resultante é a lista de escopos/roles que você deve criar/atribuir no Keycloak.

Para o código atual, a análise rende exatamente os itens abaixo:
- Escopos `identidade:ler`, `vinculos:ler`, `vinculos:escrever` → usados nos endpoints de identidade.
- Escopos `contas:ler`, `transacoes:ler` → exigidos pelos endpoints de contas.
- Escopo `openid` → padrão do protocolo OIDC.
- Realm role `cliente` → transformada em `ROLE_cliente` para liberar rotas protegidas por `DeviceTokenFilter` e `@PreAuthorize`.

### 2. Token de serviço (`client_credentials`)
Use quando quiser exercitar chamadas sem usuário humano (não recebe `ROLE_cliente`, apenas escopos):

```bash
curl -X POST http://localhost:8080/realms/eickrono/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'client_id=app-flutter-local' \
  -d "client_secret=${CLIENT_SECRET}" \
  -d 'grant_type=client_credentials' \
  -d 'scope=openid identidade:ler vinculos:ler vinculos:escrever contas:ler transacoes:ler'
```

### 3. Descobrir o `sub` do token (útil para preencher bancos de teste)

Rode o comando abaixo (macOS/Linux; troque `python3` por `python` se necessário) e cole o token quando solicitado:

```bash
python3 - <<'PY'
import base64
import json

token = input("Cole o access_token e pressione Enter: ").strip()
if not token:
    raise SystemExit("Nenhum token informado.")

payload = token.split('.')[1]
payload += '=' * (-len(payload) % 4)
claims = json.loads(base64.urlsafe_b64decode(payload))
print(claims['sub'])
PY
```

> O valor impresso é o identificador do usuário no token (campo `sub`); use-o para pré-carregar dados nas tabelas de teste.

## Ordem sugerida de chamadas

> Diretriz atual do app móvel: o `X-Device-Token` canônico é emitido em `POST /api/publica/sessoes`. As rotas explícitas de `registro` abaixo permanecem para testes manuais, cenários excepcionais e compatibilidade transitória, não como fluxo principal do app.

### 1. Utilidades iniciais
- **GET** `http://127.0.0.1:8081/.well-known/chaves-publicas`
  - Autenticação: não requer.
  - Verifica se a API pública de autenticação está respondendo e expõe as chaves do Keycloak.

### 2. Registro e confirmação manual de dispositivo (fluxo excepcional/legado)
1. **POST** `http://127.0.0.1:8081/api/conta/dispositivos/registro`
   - Autenticação: não requer.
   - Payload (JSON):
     ```json
     {
       "email": "maria.tester@exemplo.com",
       "telefone": "+5511988880000",
       "fingerprint": "device-ios-teste-001",
       "plataforma": "ios",
       "versaoAplicativo": "1.4.2"
     }
     ```
   - Observação:
     - `email` é sempre obrigatório.
     - `telefone` só é obrigatório quando a política `identidade.dispositivo.onboarding.sms-habilitado=true` estiver ativa.
   - Esperado: `202 Accepted` com `registroId`, `expiraEm`, `status=PENDENTE` e a lista `canaisConfirmacao`.
   - Pegue os códigos SMS/e-mail nos logs:
     `docker logs -f eickrono-api-autenticacao-dev | grep "Enviando código"`
     (o e-mail é enviado por `CanalEnvioCodigoEmailLog`; SMS passa por `CanalEnvioCodigoSms` e pelo `FornecedorEnvioSms` configurado, que em dev usa `FornecedorEnvioSmsLog`).

2. **POST** `http://127.0.0.1:8081/api/conta/dispositivos/registro/{registroId}/confirmacao`
   - Cabeçalhos:
     - `Authorization: Bearer <token_password>`
   - Payload:
     ```json
     {
       "codigoEmail": "654321"
     }
     ```
   - Se `canaisConfirmacao` contiver `SMS`, inclua também:
     ```json
     {
       "codigoSms": "123456",
       "codigoEmail": "654321"
     }
     ```
   - Esperado: `200 OK` com `tokenDispositivo`, `tokenExpiraEm`, `registroId`, `emitidoEm`.
   - Guarde `tokenDispositivo`; ele vai no cabeçalho `X-Device-Token` das demais rotas que exigem `ROLE_cliente`.

3. (Opcional) **POST** `http://127.0.0.1:8081/api/conta/dispositivos/registro/{registroId}/reenviar`
   - Autenticação: não requer.
   - Payload opcional:
     ```json
     {
       "reenviarSms": true,
       "reenviarEmail": false
     }
     ```
   - `reenviarSms` só terá efeito quando o registro tiver sido criado com o canal SMS ativo.
   - Esperado: `202 Accepted` e novos códigos nos logs.

4. **GET** `http://127.0.0.1:8081/api/conta/dispositivos/offline/politica`
   - Cabeçalhos:
     - `Authorization: Bearer <token_password>`
     - `X-Device-Token: <tokenDispositivo>`
   - Esperado: `200 OK` com a política central do backend:
     ```json
     {
       "permitido": true,
       "tempoMaximoMinutos": 720,
       "exigeReconciliacao": true,
       "condicoesBloqueio": [
         "TOKEN_REVOGADO",
         "TOKEN_EXPIRADO",
         "DISPOSITIVO_SEM_CONFIANCA"
       ]
     }
     ```

5. **POST** `http://127.0.0.1:8081/api/conta/dispositivos/offline/eventos`
   - Cabeçalhos:
     - `Authorization: Bearer <token_password>`
     - `X-Device-Token: <tokenDispositivo>`
   - Payload:
     ```json
     {
       "eventos": [
         {
           "tipoEvento": "MODO_OFFLINE_ATIVADO",
           "detalhes": "usuario entrou em modo offline"
         },
         {
           "tipoEvento": "RECONCILIACAO_REALIZADA",
           "detalhes": "sincronizacao concluida"
         }
       ]
     }
     ```
   - Esperado: `202 Accepted`.
   - Objetivo: registrar no backend os eventos relevantes do período offline do app sem criar ainda uma entidade de janela offline.

### 3. Consultas e ações da API pública de autenticação
> As chamadas abaixo exigem **dois cabeçalhos**:
> - `Authorization: Bearer <token_password>`
> - `X-Device-Token: <tokenDispositivo>`
> (o `DeviceTokenFilter` libera apenas usuários com `ROLE_cliente` e token ativo).

1. **GET** `http://127.0.0.1:8081/api/conta/vinculos-organizacionais`
   - Esperado: `200 OK` com `PerfilDto` (nome, email, perfis, papeis).

2. **GET** `http://127.0.0.1:8081/api/conta/redes-sociais`
   - Escopos: `identidade:ler` ou role `cliente`.
   - Esperado: lista de vínculos; vazia na primeira execução.

3. **POST** `http://127.0.0.1:8081/api/conta/redes-sociais/google`
   - Requer escopo `vinculos:escrever`.
   - Payload:
     ```json
     {
       "provedor": "google",
       "identificador": "google-oauth2|1234567890"
     }
     ```
   - Esperado: `200 OK` com `VinculoSocialDto` recém-criado.

4. **POST** `http://127.0.0.1:8081/api/conta/dispositivos/revogar`
   - Cabeçalhos extras: `X-Device-Token: <tokenDispositivo>` (o mesmo a ser revogado).
   - Payload opcional:
     ```json
     {
       "motivo": "novo login manual"
     }
     ```
   - Esperado: `204 No Content`. Depois da revogação, chamadas que exigem `ROLE_cliente` voltarão a recusar (`423 Locked`) até confirmar novo dispositivo.

### 4. Preparar dados da API Contas
As APIs de contas leem dados reais do Postgres. Insira registros manualmente usando o mesmo `sub` do token de usuário:

```bash
docker exec -it eickrono-postgres-dev psql -U eickrono -d eickrono_contas
```

```sql
INSERT INTO contas.contas (numero, cliente_id, saldo, criada_em, atualizada_em)
VALUES ('000123-0', '<SUB_DO_TOKEN>', 1250.75, now(), now());

INSERT INTO contas.transacoes (conta_id, tipo, valor, efetivada_em, descricao)
VALUES (currval('contas.contas_id_seq'), 'CREDITO', 1500.00, now() - interval '2 days', 'Depósito inicial'),
       (currval('contas.contas_id_seq'), 'DEBITO', 249.25,  now() - interval '1 day',  'Pagamento cartão');
```

### 5. Chamadas da API Contas
> Para rotas que exigem `ROLE_cliente`, reutilize o `X-Device-Token` obtido na etapa 2.

1. **GET** `http://localhost:8082/contas`
   - Autenticação: `Bearer <token_password>` ou token de serviço com `contas:ler`.
   - Retorna lista de `ContaResumoDto` vinculadas ao `sub`.

2. **GET** `http://localhost:8082/contas/{id}`
   - Escopos: `contas:ler` **e** `ROLE_cliente`.
   - Retorna `ContaResumoDto` quando a conta pertence ao `sub`; senão, `404`.

3. **GET** `http://localhost:8082/transacoes?contaId={id}`
   - Escopos: `transacoes:ler` **e** `ROLE_cliente`.
   - Esperado: lista ordenada por `efetivadaEm` (DTO com `tipo`, `valor`, `descricao`).

### 6. Dicas úteis
- Decodifique o `sub` e reaproveite-o para popular bancos ou construir payloads.
- Erros comuns:
  - `401/403`: valide escopos e o tipo de token usado.
  - `428 Precondition Required`: faltou `X-Device-Token`.
  - `423 Locked`: token de dispositivo revogado; refaça o registro/confirmação.
- Para repetir testes sem Swagger, salve os exemplos acima em um arquivo `.http` ou script `curl`.

### 7. Finalização
- Após validar os fluxos, rode `mvn verify` para garantir que a suíte continua verde antes de versionar alterações.
- Limpe tokens sensíveis do clipboard e interrompa o `docker compose` se não for mais utilizar (`docker compose down`).
