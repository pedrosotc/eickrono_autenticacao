# Especificacao Tecnica De Contexto De E-mail E Protocolos Externos

## Objetivo

Padronizar os fluxos publicos de cadastro e recuperacao de senha para:

- separar identificadores internos de identificadores exibidos ao cliente;
- preparar os e-mails para localizacao por idioma e fuso horario;
- carregar contexto de produto, canal e empresa no conteudo enviado;
- manter base tecnica para futura migracao a um provedor transacional.

## Estado Atual

### Recuperacao de senha

- o backend expõe `fluxoId` ao cliente e usa esse valor como chave operacional do fluxo;
- o e-mail de recuperacao exibe o mesmo `fluxoId` no corpo;
- `locale` e `timeZone` nao sao recebidos nem persistidos;
- o conteudo do e-mail usa apenas `nomeAplicacao` e hora fixa em UTC.

### Cadastro de conta

- o backend expõe `cadastroId` ao cliente e usa esse valor como chave operacional do fluxo;
- o e-mail de cadastro exibe o mesmo `cadastroId` no corpo;
- `locale` e `timeZone` nao sao recebidos nem persistidos;
- o conteudo do e-mail usa apenas `nomeAplicacao` e hora fixa em UTC.

## Decisoes

### 1. Separar identificador interno de protocolo externo

- `fluxoId` continua interno e operacional nos endpoints de recuperacao;
- `cadastroId` continua interno e operacional nos endpoints de cadastro;
- os e-mails deixam de expor esses valores;
- cada fluxo passa a persistir um `protocoloSuporte` externo e estavel.

### 2. Persistir contexto de localizacao

Cada fluxo publico deve passar a aceitar e persistir:

- `locale`
- `timeZone`

Ordem de resolucao do fuso/idioma para renderizacao:

1. valor enviado explicitamente pelo app/web;
2. ultimo valor conhecido da conta/usuario;
3. valor costumeiro mais recorrente da conta/usuario, se a regra de negocio desejar esse refinamento;
4. fallback para UTC e locale padrao do sistema.

### 3. Separar contexto de exibicao

Os templates de e-mail devem deixar de depender de um nome generico unico e passar a trabalhar com:

- `tipoProdutoExibicao`
- `produtoExibicao`
- `canalExibicao`
- `empresaExibicao`
- `ambienteExibicao`

Exemplos:

- `app Thimisu da Eickrono`
- `portal Backoffice da Empresa X`
- `[STG] app Thimisu da Eickrono`

## Implementacao Aplicada Nesta Etapa

Nesta etapa fica implementado:

- `protocoloSuporte` persistido em `cadastros_conta`;
- `protocoloSuporte` persistido em `recuperacoes_senha`;
- substituicao do UUID interno por `protocoloSuporte` nos e-mails;
- rotulo humano `Protocolo de atendimento`.

Fica propositalmente adiado:

- receber `locale` e `timeZone` nos requests;
- renderizar data/hora local no e-mail;
- trocar `nomeAplicacao` por contexto completo de produto/canal/empresa;
- migrar para provedor transacional.

## Modelo De Dados

### Tabelas existentes

#### `cadastros_conta`

Novo campo:

- `protocolo_suporte VARCHAR(40) NOT NULL UNIQUE`

Regra:

- gerado no backend para novos registros;
- preenchido em migration para registros antigos;
- usado apenas para exibicao e suporte.

#### `recuperacoes_senha`

Novo campo:

- `protocolo_suporte VARCHAR(40) NOT NULL UNIQUE`

Regra:

- gerado no backend para novos registros;
- preenchido em migration para registros antigos;
- usado apenas para exibicao e suporte.

### Campos futuros

#### `cadastros_conta`

Adicionar em fase posterior:

- `locale_solicitante VARCHAR(16)`
- `time_zone_solicitante VARCHAR(64)`
- `tipo_produto_exibicao VARCHAR(32)`
- `produto_exibicao VARCHAR(128)`
- `canal_exibicao VARCHAR(64)`
- `empresa_exibicao VARCHAR(128)`
- `ambiente_exibicao VARCHAR(32)`

#### `recuperacoes_senha`

Adicionar em fase posterior:

- `locale_solicitante VARCHAR(16)`
- `time_zone_solicitante VARCHAR(64)`
- `tipo_produto_exibicao VARCHAR(32)`
- `produto_exibicao VARCHAR(128)`
- `canal_exibicao VARCHAR(64)`
- `empresa_exibicao VARCHAR(128)`
- `ambiente_exibicao VARCHAR(32)`

## Payloads Futuros

### Cadastro

Expandir `CadastroApiRequest` para aceitar:

```json
{
  "locale": "pt-BR",
  "timeZone": "America/Sao_Paulo",
  "tipoProdutoExibicao": "app",
  "produtoExibicao": "Thimisu",
  "canalExibicao": "ios",
  "empresaExibicao": "Eickrono",
  "ambienteExibicao": "STG"
}
```

### Recuperacao de senha

Expandir `IniciarRecuperacaoSenhaApiRequest` para aceitar:

```json
{
  "emailPrincipal": "usuario@dominio.com",
  "locale": "pt-BR",
  "timeZone": "America/Sao_Paulo",
  "tipoProdutoExibicao": "app",
  "produtoExibicao": "Thimisu",
  "canalExibicao": "ios",
  "empresaExibicao": "Eickrono",
  "ambienteExibicao": "STG"
}
```

## Regras De Template

### Recuperacao de senha

Formato alvo:

- `Voce solicitou a recuperacao de senha do app Thimisu da Eickrono.`
- `Codigo de recuperacao: 123456`
- `Protocolo de atendimento: REC-...`
- `Validade ate: 29/04/2026 17:45 (horario de Brasilia)`
- `Referencia tecnica: 20:45 UTC`

### Cadastro

Formato alvo:

- `Voce solicitou a confirmacao de cadastro do app Thimisu da Eickrono.`
- `Codigo de confirmacao: 123456`
- `Protocolo de atendimento: CAD-...`
- `Validade ate: 29/04/2026 17:45 (horario de Brasilia)`
- `Referencia tecnica: 20:45 UTC`

### Protocolo

Regras:

- nao deve reutilizar `fluxoId` ou `cadastroId`;
- nao deve autorizar nenhuma operacao sozinho;
- serve para correlacao com suporte, auditoria e notificacoes;
- preferir formato curto e legivel ao cliente.

## Ordem De Implementacao

### Fase 1

- adicionar `protocoloSuporte` nas entidades e tabelas;
- trocar e-mails para exibir o protocolo externo;
- manter contratos publicos atuais.

### Fase 2

- receber `locale` e `timeZone` nos requests publicos;
- persistir esses campos nos fluxos;
- usar ultimo contexto conhecido antes do fallback para UTC.

### Fase 3

- receber contexto de produto/canal/empresa;
- remover dependencia de um nome estatico unico no template;
- suportar ambiente de staging sem perder o nome do produto.

### Fase 4

- versionar templates por evento, idioma e ambiente;
- incluir HTML + texto puro;
- manter protocolo externo e horario local + UTC.

### Fase 5

- migrar para provedor de e-mail transacional;
- autenticar dominio com SPF, DKIM e DMARC no provedor;
- usar API transacional em vez de SMTP de conta pessoal;
- receber webhooks de `delivered`, `bounced`, `complaint` e opcionalmente `opened`;
- integrar esses eventos com auditoria e observabilidade do fluxo.

## Observacoes De Seguranca

- `fluxoId` e `cadastroId` continuam operacionais apenas no backend e nas APIs controladas;
- `protocoloSuporte` e estritamente externo;
- o protocolo nao substitui codigo de verificacao, senha nem autenticacao;
- o protocolo pode ser mostrado ao cliente sem expor diretamente a chave interna do fluxo.
