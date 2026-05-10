# Especificação de avatar social por vínculo e avatar preferido por projeto

> Status deste documento: **canônico no seu escopo**.
>
> Este documento trata do desenho de avatar social, avatar preferido por
> sistema e cache local no app.
>
> Ele nao substitui o consolidado de migracao para decisoes de ownership entre
> `autenticacao`, `identidade` e backend do produto.

## Objetivo

Definir o desenho canônico para:

- gravar uma URL de foto própria para cada rede social vinculada à pessoa;
- permitir que o usuário escolha um avatar principal para uso dentro de um projeto/app da Eickrono;
- manter um cache local mínimo e estável no app, sem precisar persistir todas as redes sociais localmente na primeira versão.

Este documento passou a refletir a implementação da primeira entrega:

- migration única `V37__adicionar_avatar_social_e_avatar_preferido.sql`;
- DTOs e endpoints já expostos no servidor de identidade;
- cliente compartilhado e app Thimisu já ajustados para consumir o contrato;
- TDD dedicado em backend, cliente e app.

## Estado atual

Hoje o ecossistema não possui um modelo canônico de foto por rede social vinculada.

No servidor, antes da implementação:

- `public.vinculos_sociais` persiste apenas `provedor`, `identificador` e `vinculado_em`;
- `public.pessoas_formas_acesso` persiste apenas dados de acesso social, sem foto;
- `autenticacao.usuarios_formas_acesso` também não tem campos de nome/foto externa;
- `autenticacao.contextos_sociais_pendentes` guarda e-mail e nome externo, mas não guarda URL de foto.

No app, antes da implementação:

- a tela de contas vinculadas consome uma copia remota transitoria em memoria;
- não existe tabela local persistindo todas as redes sociais vinculadas;
- existe apenas um campo `avatar` em `tb_contas_locais_dispositivo`, preenchido a partir das claims da sessão atual;
- esse `avatar` representa um unico avatar efetivo da conta autenticada no
  dispositivo, nao uma colecao de fotos por provedor.

Observacao importante:

- quando este documento fala em `cache local no app`, isso significa apenas
  persistencia local do dispositivo;
- isso nao muda o ownership da conta central nem o ownership do perfil do
  sistema no backend.

## Requisitos funcionais

1. Cada rede social vinculada deve poder guardar sua própria foto.
2. O usuário pode ter Google, Apple e outras redes vinculadas, cada uma com foto diferente.
3. O usuário pode escolher uma dessas fotos como avatar principal do projeto atual.
4. O usuário também pode, no futuro, escolher uma foto própria da aplicação, sem depender de rede social.
5. O app deve manter localmente apenas o avatar efetivo necessário para a experiência principal.
6. O fato de uma rede social ter foto não obriga essa foto a ser o avatar principal do projeto.

## Decisões de modelagem

### 1. Foto por rede social vinculada

A fonte canônica recomendada para a foto por rede social é `autenticacao.usuarios_formas_acesso`, porque ela já representa o vínculo multiapp de formas de acesso do usuário.

Campos propostos em `autenticacao.usuarios_formas_acesso`:

- `nome_exibicao_externo VARCHAR(255) NULL`
- `url_avatar_externo VARCHAR(2048) NULL`
- `avatar_externo_atualizado_em TIMESTAMP WITH TIME ZONE NULL`

Regras:

- esses campos só fazem sentido quando `tipo = 'SOCIAL'`;
- para `EMAIL`, `USUARIO` ou outros tipos locais, esses campos devem ficar nulos;
- a URL pode ser regravada quando o usuário reautenticar ou sincronizar o vínculo social;
- o `nome_exibicao_externo` é opcional, mas útil para UI e auditoria leve.

### 2. Avatar principal do usuário no projeto atual

O avatar principal não deve ser deduzido implicitamente da última rede usada. Ele deve ser uma preferência explícita do usuário no contexto do projeto/app.

A fonte recomendada para isso é `autenticacao.usuarios_clientes_ecossistema`, porque ela representa o vínculo do usuário com cada projeto do ecossistema.

Campos propostos em `autenticacao.usuarios_clientes_ecossistema`:

- `avatar_preferido_origem VARCHAR(32) NOT NULL`
- `avatar_preferido_forma_acesso_id UUID NULL REFERENCES autenticacao.usuarios_formas_acesso (id)`
- `avatar_preferido_url VARCHAR(2048) NULL`
- `avatar_preferido_atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL`
- `avatar_preferido_arquivo_id UUID NULL`

Valores sugeridos para `avatar_preferido_origem`:

- `SOCIAL`
- `UPLOAD_USUARIO`
- `URL_EXTERNA`
- `NENHUM`

Regras:

- quando `avatar_preferido_origem = 'SOCIAL'`, `avatar_preferido_forma_acesso_id` deve apontar para uma forma social do próprio usuário;
- `avatar_preferido_url` funciona como valor resolvido e pronto para consumo pelo app;
- `avatar_preferido_arquivo_id` fica reservado para a fase em que o usuário puder subir foto própria na aplicação;
- a preferência é por projeto, não global para todo o ecossistema.

### 2.1 Separação obrigatória entre vínculo social e avatar preferido

Esta separação é obrigatória:

- toda rede social autenticada e não removida deve continuar vinculada à pessoa;
- a escolha do avatar preferido não decide qual rede social fica persistida;
- o campo `principal` das tabelas de `formas_acesso` continua sendo metadado da
  forma de acesso, não a fonte canônica do avatar preferido;
- o estado canônico do avatar preferido por projeto continua em
  `autenticacao.usuarios_clientes_ecossistema.avatar_preferido_*`.

Em outras palavras:

- o usuário pode ter `Google` e `Apple` vinculados ao mesmo tempo;
- escolher `Google` como avatar preferido não apaga `Apple`;
- escolher `Apple` como avatar preferido não torna `Apple` a única rede
  persistida;
- escolher foto do dispositivo não apaga nenhum vínculo social.

### 3. Contexto social pendente

Quando o usuário entra por rede social sem vínculo final consumido, o contexto pendente já pode carregar a foto externa para melhorar a UX de:

- abrir cadastro com prefill (preenchimento inicial);
- entrar e vincular;
- mostrar confirmação visual da conta social em processo.

Campos propostos em `autenticacao.contextos_sociais_pendentes`:

- `nome_exibicao_externo VARCHAR(255) NULL`
- `url_avatar_externo VARCHAR(2048) NULL`
- `avatar_externo_atualizado_em TIMESTAMP WITH TIME ZONE NULL`

Regra:

- esse dado é transitório e não substitui o vínculo final em `autenticacao.usuarios_formas_acesso`;
- serve apenas para enriquecer a jornada antes do consumo ou cancelamento do contexto.

## Modelo de banco implementado

### Migration única `V37`

A entrega atual consolidou em uma única migration:

- `vinculos_sociais`
- `pessoas_formas_acesso`
- `autenticacao.usuarios_formas_acesso`
- `autenticacao.usuarios_clientes_ecossistema`
- `autenticacao.contextos_sociais_pendentes`

DDL consolidado principal:

```sql
ALTER TABLE vinculos_sociais
    ADD COLUMN nome_exibicao_externo VARCHAR(255),
    ADD COLUMN url_avatar_externo VARCHAR(2048),
    ADD COLUMN avatar_externo_atualizado_em TIMESTAMP WITH TIME ZONE;

ALTER TABLE pessoas_formas_acesso
    ADD COLUMN nome_exibicao_externo VARCHAR(255),
    ADD COLUMN url_avatar_externo VARCHAR(2048),
    ADD COLUMN avatar_externo_atualizado_em TIMESTAMP WITH TIME ZONE;

ALTER TABLE autenticacao.usuarios_formas_acesso
    ADD COLUMN nome_exibicao_externo VARCHAR(255),
    ADD COLUMN url_avatar_externo VARCHAR(2048),
    ADD COLUMN avatar_externo_atualizado_em TIMESTAMP WITH TIME ZONE;

ALTER TABLE autenticacao.usuarios_clientes_ecossistema
    ADD COLUMN avatar_preferido_origem VARCHAR(32) NOT NULL DEFAULT 'NENHUM',
    ADD COLUMN avatar_preferido_forma_acesso_id UUID
        REFERENCES autenticacao.usuarios_formas_acesso (id),
    ADD COLUMN avatar_preferido_url VARCHAR(2048),
    ADD COLUMN avatar_preferido_atualizado_em TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT NOW(),
    ADD COLUMN avatar_preferido_arquivo_id UUID;
```

Constraint sugerida:

```sql
ALTER TABLE autenticacao.usuarios_clientes_ecossistema
    ADD CONSTRAINT ck_usuarios_clientes_ecossistema_avatar_origem
    CHECK (avatar_preferido_origem IN ('SOCIAL', 'UPLOAD_USUARIO', 'URL_EXTERNA', 'NENHUM'));

ALTER TABLE autenticacao.contextos_sociais_pendentes
    ADD COLUMN nome_exibicao_externo VARCHAR(255),
    ADD COLUMN url_avatar_externo VARCHAR(2048),
    ADD COLUMN avatar_externo_atualizado_em TIMESTAMP WITH TIME ZONE;
```

## Compatibilidade com o legado

Enquanto o runtime ainda mantiver cópia temporária de parte do domínio entre
modelo legado e modelo multiapp, a sincronização deve seguir estas regras:

1. A gravação canônica da foto social nasce em `autenticacao.usuarios_formas_acesso`.
2. Se o legado continuar sendo usado por algum fluxo intermediário, os campos equivalentes podem ser replicados em:
   - `public.pessoas_formas_acesso`
   - ou `public.vinculos_sociais`
3. Essa cópia transitória não deve substituir a definição canônica do multiapp.

Recomendação:

- evitar criar duas fontes canônicas de avatar social;
- usar o legado apenas como cópia temporária, se ainda houver fluxo que dependa dele.

## Contratos de API recomendados

### 1. Leitura de vínculos sociais

A resposta de vínculos sociais deve incluir:

- `provedor`
- `suportado`
- `vinculado`
- `vinculadoEm`
- `identificadorMascarado`
- `nomeExibicaoExterno`
- `urlAvatarExterno`
- `avatarExternoAtualizadoEm`
- `avatarPrincipalNoProjeto`

Exemplo:

```json
{
  "provedor": "google",
  "suportado": true,
  "vinculado": true,
  "vinculadoEm": "2026-04-30T10:15:00Z",
  "identificadorMascarado": "jo***@gmail.com",
  "nomeExibicaoExterno": "Thiago C",
  "urlAvatarExterno": "https://lh3.googleusercontent.com/...",
  "avatarExternoAtualizadoEm": "2026-04-30T10:15:00Z",
  "avatarPrincipalNoProjeto": true
}
```

### 2. Seleção do avatar principal

Endpoint implementado:

- `PUT /api/conta/avatar-preferido`

Corpo para escolher avatar de rede social:

```json
{
  "origem": "SOCIAL",
  "provedor": "google"
}
```

Corpo para escolher URL externa já resolvida:

```json
{
  "origem": "URL_EXTERNA",
  "url": "https://cdn.eickrono.com/avatars/usuario-123.png"
}
```

Corpo para limpar escolha explícita:

```json
{
  "origem": "NENHUM"
}
```

Semântica:

- `SOCIAL` resolve a `forma_acesso` do provedor dentro do usuário autenticado;
- o servidor grava a preferência no vínculo do usuário com o projeto atual;
- a resposta pode devolver o `avatarUrlEfetivo` já resolvido.

### 3. Resposta do contexto autenticado

Nesta entrega, o app resolve o avatar efetivo a partir de `GET /api/conta/redes-sociais?aplicacaoId=...`.

Em evolução futura, quando houver contexto autenticado enriquecido, a resposta pode expor:

- `avatarUrlEfetivo`
- `avatarOrigemEfetiva`

Isso evita que o app precise remontar prioridade localmente.

## Cache local no app

### Objetivo da primeira versão

Na primeira versão, o app não precisa persistir localmente todas as fotos de todas as redes sociais.

O mínimo recomendado é:

- continuar usando `tb_contas_locais_dispositivo.avatar` como cache do avatar
  efetivo da conta autenticada no dispositivo;
- persistir apenas a URL efetiva já resolvida para aquele projeto;
- continuar consultando a lista de vínculos sociais do backend quando a tela específica de contas vinculadas for aberta.

### Evolução local opcional

Se o app precisar exibir a lista de redes sociais com foto em modo offline ou em reabertura rápida, pode ser criada uma tabela adicional:

- `tb_contas_locais_avatares_sociais`

Campos sugeridos:

- `conta_local_id TEXT NOT NULL`
- `provedor TEXT NOT NULL`
- `nome_exibicao_externo TEXT NULL`
- `url_avatar_externo TEXT NULL`
- `avatar_principal_no_projeto BOOLEAN NOT NULL DEFAULT FALSE`
- `atualizado_em DATETIME NOT NULL`

Recomendação:

- não criar essa tabela na primeira versão se a UX principal só precisar do avatar efetivo.

## Estratégia de resolução de avatar

Ordem sugerida para o avatar exibido no app:

1. `avatar_preferido_url` do vínculo do usuário com o projeto atual
2. se a origem for `SOCIAL`, `url_avatar_externo` da forma de acesso social escolhida
3. se existir upload próprio da aplicação, URL estável do arquivo interno
4. avatar já cacheado em `tb_contas_locais_dispositivo.avatar`
5. fallback visual para iniciais

## Cenarios sem foto social

Para este documento, os casos abaixo devem ser tratados como equivalentes:

- a rede social nao envia nenhuma URL de foto;
- a rede social envia a claim/campo de foto como `null`;
- a rede social envia string vazia;
- a rede social informa o usuario, mas esse usuario nao possui foto publicada.

Regra canônica:

- nesses casos, o vinculo social continua valido;
- cadastro, login, sincronizacao e vinculacao social nao falham por ausencia
  de foto;
- a foto daquele provedor passa a ser considerada `indisponivel`.

### 1. Sincronizacao de vinculo social sem foto

Quando houver sincronizacao social e o provedor nao entregar uma URL de foto
utilizavel:

- `url_avatar_externo` deve ficar `NULL`;
- `nome_exibicao_externo` ainda pode ser gravado normalmente, se existir;
- a resposta da API deve trazer `urlAvatarExterno = null`;
- o provedor continua aparecendo como vinculado.

Exemplo esperado:

```json
{
  "provedor": "google",
  "vinculado": true,
  "nomeExibicaoExterno": "Pessoa Google",
  "urlAvatarExterno": null,
  "avatarPrincipalNoProjeto": false
}
```

### 2. Contexto social pendente sem foto

Quando o usuario chega por rede social e o contexto ainda esta pendente:

- o contexto pode carregar nome, e-mail e outros dados recebidos;
- a ausencia de `url_avatar_externo` nao impede abrir cadastro, vincular ou
  entrar;
- a UI nao deve assumir que toda rede social autenticada necessariamente
  possui foto.

### 3. Escolha de avatar social quando o provedor nao tem foto

Se o usuario tentar escolher `origem = SOCIAL` para um provedor que esta sem
foto utilizavel:

- a operacao deve ser rejeitada;
- o servidor nao deve gravar `avatar_preferido_origem = SOCIAL` com URL nula;
- a resposta deve informar de forma clara que aquele provedor nao possui foto
  disponivel no momento.

Regra recomendada de negocio:

- retornar erro funcional `422` ou equivalente de validacao;
- mensagem sugerida:
  `A rede social escolhida nao possui foto disponivel para uso como avatar.`

Motivo:

- evita gravar uma preferencia explicita que nao consegue produzir avatar
  efetivo;
- evita ambiguidade entre "avatar escolhido" e "avatar inexistente".

### 4. Provedor que tinha foto e depois deixou de ter

Se uma rede social antes possuia `url_avatar_externo`, mas em sincronizacao
posterior essa URL deixar de existir:

- `url_avatar_externo` deve ser limpo para `NULL`;
- se aquele provedor nao for o avatar principal do projeto, nada mais precisa
  ser alterado;
- se aquele provedor for o avatar principal atual do projeto, a preferencia
  social deve ser limpa.

Limpeza recomendada da preferencia do projeto:

- `avatar_preferido_origem = 'NENHUM'`
- `avatar_preferido_forma_acesso_id = NULL`
- `avatar_preferido_url = NULL`

Motivo:

- a preferencia social deixou de ser resolvivel;
- o app deve voltar para a ordem normal de fallback, sem manter um ponteiro
  quebrado para uma foto que nao existe mais.

### 5. Fallback no app quando nao existe foto social

Quando nao houver `avatarUrlEfetivo` resolvido:

1. usar `avatar_preferido_url`, se existir;
2. se nao existir, tentar outra fonte valida permitida pelo contrato;
3. se ainda assim nao houver avatar remoto valido, usar o cache local do
   dispositivo;
4. se nao houver cache local, usar fallback visual por iniciais.

Importante:

- a falta de foto social nao deve quebrar a tela;
- o app nao deve exibir avatar quebrado, icone de imagem corrompida ou URL
  vazia;
- ausencia de foto e um estado funcional esperado, nao uma excecao.

## Risco de usar URL remota de rede social

Uma URL social externa pode:

- expirar;
- ser rotacionada pelo provedor;
- mudar após o usuário trocar foto na rede social;
- ser removida sem aviso.

Por isso, o plano canônico é:

- para vínculo social, salvar a URL remota por vínculo social quando ela
  existir;
- quando o usuário escolher uma foto social como avatar principal, o backend
  pode reaproveitar essa URL remota enquanto ela continuar válida;
- quando o usuário escolher uma foto processada localmente no dispositivo como
  foto pública do perfil, essa imagem deve subir para uma mídia própria da
  Eickrono e virar uma URL pública estável;
- essa URL estável deve alimentar `avatar_preferido_url` ou, quando o pipeline
  estiver completo, `avatar_preferido_arquivo_id`.

## Cobertura de testes

## Fronteira funcional atual

Hoje este tema cobre apenas estas origens de avatar por projeto:

- `SOCIAL`
- `URL_EXTERNA`
- `NENHUM`

Nao existe, neste momento, uma origem funcional separada de `avatar
organizacional`.

Os vinculos organizacionais existem para contexto de acesso e escopo do usuario,
mas nao publicam hoje uma foto da organizacao para disputar o papel de avatar
preferido do projeto atual.

Entao, quando falamos em ampliar cobertura "alem de Google", o ganho real desta
trilha e ampliar a matriz de provedores sociais suportados, e nao criar um
comportamento novo de avatar organizacional que ainda nao existe no produto.

## Status funcional da foto social

Quando uma conta social ja esta vinculada, o backend deve conseguir informar ao
app em qual estado funcional a foto daquele provedor esta.

Campos recomendados no contrato do provedor:

- `statusAvatarSocial`
- `mensagemAvatarSocial`

Estados publicos recomendados:

- `FOTO_DISPONIVEL`
  - a rede social possui uma foto utilizavel neste momento
- `PROVEDOR_SEM_SUPORTE_DE_FOTO`
  - a conta esta vinculada, mas este tipo de integracao nao entrega foto para o
    projeto atual
  - exemplo atual conhecido: `apple`
- `FOTO_NAO_DISPONIVEL`
  - a rede social suporta foto, mas nao entregou uma URL utilizavel agora
- `FOTO_REMOVIDA_APOS_SINCRONIZACAO`
  - a rede ja teve foto disponivel antes, mas deixou de entregar essa foto em
    uma sincronizacao posterior

Mensagens publicas recomendadas:

- `PROVEDOR_SEM_SUPORTE_DE_FOTO`
  - "Esta conta esta vinculada, mas este provedor nao disponibiliza foto para uso no perfil neste aplicativo."
- `FOTO_NAO_DISPONIVEL`
  - "Esta conta esta vinculada, mas nao ha foto disponivel para usar no perfil neste momento."
- `FOTO_REMOVIDA_APOS_SINCRONIZACAO`
  - "A foto desta rede social nao esta mais disponivel. Por isso ela deixou de poder ser usada como foto de perfil."

Observacao importante:

- o app nao deve inventar um motivo mais especifico do que o backend consegue
  provar
- por isso, diferencas tecnicas como `campo ausente`, `valor null` e
  `valor vazio` podem existir internamente, mas para UX elas normalmente caem
  no mesmo estado publico `FOTO_NAO_DISPONIVEL`

Cobertura principal ja existente:

Backend:

- [VinculoSocialServiceTest.java](/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/modulos/modulo-eickrono-autenticacao/src/test/java/com/eickrono/api/identidade/aplicacao/servico/VinculoSocialServiceTest.java:1)
- [VinculosSociaisControllerIT.java](/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/modulos/modulo-eickrono-autenticacao/src/test/java/com/eickrono/api/identidade/apresentacao/api/VinculosSociaisControllerIT.java:1)
- [RegistroDispositivoControllerIT.java](/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/modulos/modulo-eickrono-autenticacao/src/test/java/com/eickrono/api/identidade/apresentacao/api/RegistroDispositivoControllerIT.java:1)

Cliente compartilhado:

- [cliente_api_conta_eickrono_test.dart](/Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-cliente/test/cliente_api_conta_eickrono_test.dart:1)

App:

- [controlador_contas_vinculadas_test.dart](/Users/thiago/Desenvolvedor/flutter/eickrono-thimisu/eickrono-thimisu-app/test/funcionalidades/autenticacao/aplicacao/controlador_contas_vinculadas_test.dart:1)
- [pagina_contas_vinculadas_widget_test.dart](/Users/thiago/Desenvolvedor/flutter/eickrono-thimisu/eickrono-thimisu-app/test/funcionalidades/autenticacao/apresentacao/pagina_contas_vinculadas_widget_test.dart:1)
- [catalogo_local_contas_drift_test.dart](/Users/thiago/Desenvolvedor/flutter/eickrono-thimisu/eickrono-thimisu-app/test/infraestrutura/autenticacao/catalogo_local_contas_drift_test.dart:1)

## Cenarios de teste obrigatorios

Os cenarios abaixo devem existir de forma explicita na trilha de testes deste
tema.

### Backend

1. sincronizar vinculo social com URL de avatar presente;
2. sincronizar vinculo social sem URL de avatar;
3. sincronizar vinculo social com URL vazia e normalizar para `NULL`;
4. escolher avatar social quando o provedor possui foto;
5. rejeitar escolha de avatar social quando o provedor nao possui foto;
6. resincronizar um provedor que antes tinha foto e agora nao tem mais;
7. limpar `avatar_preferido_*` quando o avatar principal social deixar de
   existir;
8. manter vinculacao social valida mesmo sem foto.

### API

1. `GET /api/conta/redes-sociais` com `urlAvatarExterno` preenchido;
2. `GET /api/conta/redes-sociais` com `urlAvatarExterno = null`;
3. `PUT /api/conta/avatar-preferido` para `SOCIAL` com
   sucesso;
4. `PUT /api/conta/avatar-preferido` para `SOCIAL` sem foto
   disponivel, com erro funcional;
5. resposta final do projeto depois da limpeza de preferencia social invalida.

### Cliente e app

1. lista de vinculos sociais exibindo provedor sem foto sem quebrar layout;
2. escolha de avatar social bem-sucedida com foto presente;
3. tentativa de escolher avatar social sem foto, exibindo erro funcional;
4. fallback para cache local quando nao existir avatar remoto efetivo;
5. fallback para iniciais quando nao existir avatar remoto nem cache local.

## Estado da cobertura atual

Hoje a cobertura principal deste documento ja cobre o caminho feliz de avatar
social com foto, mas ainda precisa deixar explicitos os casos de ausencia de
foto.

Em especial, este documento passa a exigir cenarios dedicados para:

- provedor social sem URL de foto;
- provedor social com URL vazia;
- tentativa de escolher avatar principal social sem foto disponivel;
- perda posterior da foto de um provedor que era o avatar principal do
  projeto.

## Matriz minima por provedor

Para nao deixar a cobertura presa apenas ao caminho do Google, a trilha minima
de testes deve cobrir pelo menos:

- um provedor nativo com suporte de foto social:
  - `google`
  - `apple`
- um provedor nao nativo, sincronizado pelo fluxo OIDC web:
  - `facebook`
  - ou outro equivalente habilitado no ambiente

O objetivo nao e repetir todos os cenarios para todos os provedores, mas
garantir pelo menos estas tres leituras:

1. a regra de foto ausente funciona para um provedor nativo diferente de
   `google`, como `apple`;
2. a regra de foto ausente funciona para um provedor nao nativo, como
   `facebook`;
3. a ausencia de foto em um provedor secundario nao apaga indevidamente o
   avatar preferido de outro provedor que continua valido.

## Decisões ainda abertas

1. O nome da origem deve ser `SOCIAL` ou mais granular, como `SOCIAL_GOOGLE`, `SOCIAL_APPLE`, etc.?
2. O avatar principal deve ser sempre por projeto ou pode existir também uma preferência global futura?
3. O upload de foto própria será suportado já na primeira versão ou só em fase posterior?
4. Quando o usuário escolher uma foto social como principal, a plataforma vai apenas usar a URL remota ou copiar a imagem para uma mídia própria da Eickrono?

## Conclusão

O desenho recomendado separa corretamente três coisas diferentes:

- a foto de cada rede social vinculada;
- a escolha de qual foto é a principal no projeto atual;
- o cache local mínimo necessário no dispositivo.

Isso evita confundir:

- identidade federada do provedor;
- preferência visual do usuário no app;
- e persistência local do dispositivo.
