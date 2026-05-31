# Plano histórico de Vínculos Sociais com Keycloak

> Status deste documento: **histórico para novas implementações**.
>
> Este documento registra o desenho técnico da primeira entrega de vínculos
> sociais com Keycloak. Ele nao deve ser usado como regra vigente para o fluxo
> de login social antes de cadastro ou antes de entrar-e-vincular.
>
> A regra vigente esta em:
>
> - `eickrono-identidade-servidor/documentacao/plano_migracao_avatar_usuario_autenticacao_identidade_thimisu.md`;
> - `eickrono-identidade-servidor/documentacao/avatar_perfil_schema_alvo.dbml`;
> - `eickrono-autenticacao-servidor/documentacao/fluxograma_login_social_app.md`;
> - `eickrono-autenticacao-servidor/documentacao/guia_fluxos_login_autenticacao_app.md`.
>
> Decisao vigente:
>
> - o app autentica Google/Apple nativamente;
> - o `eickrono-autenticacao-servidor` valida a credencial social;
> - antes de cadastro ou entrar-e-vincular final, dados sociais ficam apenas no
>   app como dados temporarios;
> - nao ha token social temporario entre app e servidor;
> - nao ha persistencia de contexto social pendente em banco;
> - Keycloak nao deve criar usuario/vinculo no pre-cadastro social;
> - vinculo social definitivo so nasce depois de usuario/pessoa definitivos.
>
> As secoes abaixo podem ser consultadas apenas como historico. Quando houver
> conflito, prevalece a documentacao vigente listada acima.

Este documento consolidava o desenho técnico decidido para a primeira entrega de vínculos sociais no ecossistema Eickrono.

## Fonte canonica complementar no app

Historicamente, este documento ficava focado em:

- desenho tecnico do vinculo social;
- contrato HTTP da API canonica;
- papel do Keycloak como fonte de verdade na primeira entrega.

Os cenarios detalhados do app, incluindo conectividade, experiencia de login
social, reacao da UI, cobertura automatizada atual e backlog de testes, agora
ficam concentrados em:

- [Matriz Canonica de Autenticacao, Conectividade e Testes](../../eickrono-thimisu/eickrono-thimisu-app/docs/matriz_autenticacao_social_conectividade_testes.md)

O desenho UML atual do codigo, incluindo app,
`modulo-eickrono-autenticacao`, Keycloak e `identidade-servidor`, fica em:

- [UML Atual de Autenticacao, Cadastro e Redes Sociais](../../eickrono-thimisu/eickrono-thimisu-app/docs/uml_autenticacao_cadastro_social_ecossistema.md)

O desenho futuro do ecossistema, com quick wins e arquitetura-alvo para fechar
os cenarios aprovados, fica em:

- [Arquitetura Alvo do Ecossistema de Autenticacao](../../eickrono-thimisu/eickrono-thimisu-app/docs/arquitetura_alvo_ecossistema_autenticacao.md)

## Decisões Fechadas

- o `eickrono-autenticacao-servidor` será a API canônica de vínculos sociais;
- o Keycloak será a fonte de verdade do vínculo;
- a arquitetura deve nascer genérica para `Google`, `Apple`, `Facebook`, `LinkedIn` e `Instagram`;
- a primeira entrega deve ter gestão completa:
  - listar vínculos;
  - vincular nova rede;
  - desvincular rede já conectada;
- o vínculo nasce no fluxo OIDC do Keycloak:
  - o app abre o fluxo;
  - o usuário autentica na rede social;
  - o Keycloak cria o vínculo real;
  - o backend apenas lê, sincroniza e expõe esse estado ao app.

## Princípio Central

O app não deve criar vínculo social por payload próprio.

Exemplo de abordagem incorreta:

```json
{
  "provedor": "google",
  "identificador": "123"
}
```

Essa abordagem é incorreta porque colocaria o app como origem do vínculo. No desenho aprovado, o vínculo real nasce no Keycloak e o backend apenas reconcilia o que o Keycloak já reconhece como verdadeiro.

## Fluxos

Os fluxos abaixo continuam validos do ponto de vista do backend e do Keycloak.
Para a matriz completa do comportamento no app e dos testes associados, use o
documento canonico do app indicado acima.

## Login Social

1. o usuário toca em `Entrar com <rede>` na tela de login;
2. o app inicia OIDC com `kc_idp_hint=<alias>`;
3. o Keycloak redireciona para o provedor social;
4. o usuário autentica na rede social;
5. o Keycloak conclui a autenticação;
6. o app recebe a sessão autenticada normal.

## Vincular Rede Social

1. o usuário já está autenticado no app;
2. o usuário toca em `Conectar <rede>` na tela de contas vinculadas;
3. o app inicia OIDC com `kc_action=idp_link:<alias>`;
4. o Keycloak redireciona para o provedor social;
5. o usuário autentica na rede social;
6. o Keycloak cria o vínculo real entre o usuário atual e a conta externa;
7. o app retorna do fluxo OIDC;
8. o app chama a API de sincronização no `eickrono-autenticacao-servidor`;
9. o backend lê o estado real do usuário no Keycloak;
10. o backend reconcilia a projeção local;
11. o app atualiza a tela com o estado já sincronizado.

## Desvincular Rede Social

1. o usuário já está autenticado no app;
2. o usuário toca em `Desvincular <rede>`;
3. o app chama o endpoint de remoção no `eickrono-autenticacao-servidor`;
4. o backend remove o vínculo real no Keycloak;
5. o backend reconcilia a projeção local;
6. o app recarrega a lista de vínculos.

## Contrato de API Recomendado

## `GET /api/conta/redes-sociais`

Responsabilidade:

- listar o estado atual dos vínculos sociais;
- devolver todos os provedores suportados, inclusive os não vinculados;
- servir de base para a tela de gestão.

Resposta sugerida:

```json
{
  "provedores": [
    {
      "provedor": "google",
      "suportado": true,
      "vinculado": true,
      "vinculadoEm": "2026-04-09T12:00:00Z",
      "identificadorMascarado": "g***@gmail.com"
    },
    {
      "provedor": "apple",
      "suportado": true,
      "vinculado": false,
      "vinculadoEm": null,
      "identificadorMascarado": null
    }
  ]
}
```

## `POST /api/conta/redes-sociais/{provedor}/sincronizacao`

Responsabilidade:

- forçar reconciliação após retorno do fluxo OIDC de vínculo;
- não criar vínculo manualmente;
- apenas refletir localmente o estado já existente no Keycloak.

Resposta implementada:

- retorna o snapshot atualizado de todos os provedores suportados;
- isso permite ao app atualizar a tela sem precisar de um `GET` extra imediato.

## `DELETE /api/conta/redes-sociais/{provedor}`

Responsabilidade:

- remover o vínculo real no Keycloak;
- atualizar a projeção local do backend;
- devolver estado coerente ao app.

Resposta implementada:

- retorna o snapshot atualizado de todos os provedores suportados após a remoção.

## Por Que Não Usar `POST` Para Tudo

Tecnicamente seria possível usar `POST` para qualquer ação, mas isso piora o contrato.

### Motivo para `GET` na listagem

`GET /api/conta/redes-sociais` deve ser usado porque:

- a operação é leitura;
- o cliente está pedindo o estado atual do recurso;
- a semântica HTTP fica clara para quem lê, testa e mantém o sistema;
- retries, logs, observabilidade e documentação ficam mais previsíveis.

Usar `POST` para listar vínculos faria a API parecer mutável mesmo quando o objetivo fosse apenas leitura.

### Motivo para `POST` na sincronização

`POST /sincronizacao` deve ser usado porque:

- há efeito colateral no backend;
- o endpoint executa reconciliação;
- a chamada pode atualizar banco local, auditoria e timestamps.

Isso já não é mais uma leitura simples.

### Motivo para `DELETE` na desvinculação

`DELETE` deve ser usado porque:

- a intenção do cliente é remover um vínculo existente;
- a semântica do contrato fica explícita;
- evita transformar uma remoção em um `POST` genérico pouco expressivo.

## Resposta Curta Sobre `GET` vs `POST`

Se a operação é apenas consultar o estado atual, o método correto é `GET`.

Se a operação produz efeito colateral no servidor, como sincronizar ou desvincular, o método deve refletir isso:

- `POST` para sincronizar;
- `DELETE` para remover.

## Segurança Envolvida no Processo

Nem todos os mecanismos de segurança do servidor de autorização entram diretamente neste fluxo.

## Segurança Diretamente Usada

### 1. OIDC para login e vínculo

É a base do processo.

Será usado para:

- autenticação social com `kc_idp_hint`;
- vínculo social com `kc_action=idp_link:<alias>`.

### 2. Sessão autenticada do usuário

A área de contas vinculadas é protegida.

Logo, o usuário precisa:

- estar autenticado;
- portar token válido;
- ter autorização para chamar os endpoints do backend.

### 3. Escopos e autorização da API

Os endpoints de vínculos devem exigir:

- `vinculos:ler` para leitura;
- `vinculos:escrever` para sincronização e remoção.

Hoje o código ainda libera esses endpoints em `permitAll`, o que deve ser corrigido.

### 4. Keycloak como fonte de verdade

O backend precisa consultar e alterar o vínculo real no Keycloak por integração administrativa segura.

Isso inclui:

- localizar o usuário do realm;
- listar identidades federadas;
- remover identidade federada por provedor.

### 5. Logout, refresh e sessão já existentes

Continuam valendo normalmente:

- `access_token`;
- `refresh_token`;
- `id_token`;
- renovação da sessão;
- regras de expiração e restauração.

## Segurança Indireta ou Situacional

### 1. `X-Device-Token`

Continua relevante para chamadas protegidas do app e para refresh, mas não é o mecanismo que cria o vínculo social.

Ele protege o contexto da sessão do app, não o broker social em si.

### 2. Atestação do app

Continua relevante para o ecossistema móvel, mas não é a etapa central do vínculo no Keycloak.

Ela ajuda a validar o cliente antes ou durante fluxos protegidos do app, não substitui o vínculo do broker.

### 3. FAPI, client policies avançadas, PAR, JAR, JARM, mTLS

Esses mecanismos não precisam necessariamente participar todos do fluxo de vínculo social do app móvel.

Em especial:

- o fluxo social do app usa UX nativa e broker OIDC;
- `mTLS` interno continua importante para integrações servidor-servidor quando aplicável;
- políticas adicionais do Keycloak podem continuar existindo no ecossistema, mas não são todas obrigatórias para o vínculo social em si.

## Resposta Curta Sobre “Todos os Métodos de Segurança Serão Usados?”

Não.

Serão usados os mecanismos necessários para este fluxo, principalmente:

- sessão autenticada;
- OIDC;
- escopos/autorização;
- integração segura com o Keycloak;
- regras normais de tokens e refresh.

Nem todo mecanismo avançado do servidor de autorização entra obrigatoriamente no processo de vínculo social.

## Componentes de Backend a Implementar

No `eickrono-autenticacao-servidor`, a implementação recomendada é:

- substituir o stub de `VinculosSociaisController`;
- criar DTOs de leitura, sincronização e remoção;
- criar serviço de aplicação para vínculos sociais;
- criar cliente para Admin API do Keycloak;
- mapear provedores suportados de forma canônica;
- reconciliar formas de acesso e avatar canônico com o estado real do Keycloak,
  sem recriar ou consultar a tabela legada `vinculos_sociais`;
- registrar auditoria de sincronização e remoção.

## Integrações Necessárias Fora do Backend

## Pacote `eickrono-autenticacao-cliente`

Precisa ganhar:

- `autenticarComRedeSocial(rede)`;
- `vincularRedeSocial(rede)`;
- cliente HTTP para:
  - listar vínculos;
  - sincronizar vínculo;
  - desvincular vínculo.

## App Flutter

Precisa:

- ligar os botões sociais da tela de login ao fluxo OIDC real;
- trocar a tela placeholder de contas vinculadas pela versão conectada à API;
- permitir listagem, vínculo e desvinculação.

## Observação Sobre os Provedores

O backend e o app devem nascer genéricos para:

- `google`
- `apple`
- `facebook`
- `linkedin`
- `instagram`

O tratamento por alias deve ser configurável.

No desenho operacional:

- `Google`, `Facebook`, `LinkedIn` e `Instagram` podem ser tratados como brokers sociais suportados pelo Keycloak;
- `Apple` deve ser tratada via broker OIDC compatível com o mesmo catálogo canônico do sistema.

## Ordem Recomendada de Implementação

1. implementar o backend real de vínculos sociais no `eickrono-autenticacao-servidor`;
2. implementar o suporte social no `eickrono-autenticacao-cliente`;
3. integrar o app Flutter à API e ao fluxo OIDC real;
4. configurar e validar os cinco provedores nos realms `dev`, `hml` e `prod`;
5. cobrir com testes unitários, integração e e2e.
