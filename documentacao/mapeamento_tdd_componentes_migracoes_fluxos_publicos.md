# Mapeamento dos TDDs Canonicos para Componentes e Migracoes

> Status deste documento: **canônico no seu escopo**.
>
> Este mapeamento traduz o bloco `TDD-45` a `TDD-64` para implementacao de
> fluxos publicos.
>
> Ele complementa o consolidado de migracao, mas nao substitui o ownership
> macro entre `autenticacao`, `identidade` e backend do produto.

Este documento traduz o bloco `TDD-45` a `TDD-64` em responsabilidade tecnica
de implementacao.

Objetivo:

- dizer o que e principalmente `backend`, `app/cliente` ou `cross-service`;
- separar o que exige `migration` de banco do que exige apenas regra de
  runtime;
- deixar claro quando um `seed`/registro de catalogo por projeto tambem e
  obrigatorio;
- orientar a ordem de preparacao de `local` e `hml` antes do rollout.

Relacionamento com os outros documentos:

- a regra funcional canonica continua em
  [fluxogramas_fluxos_publicos_regra_funcional_em_fechamento.md](fluxogramas_fluxos_publicos_regra_funcional_em_fechamento.md);
- a especificacao exata de schema dos pacotes `DB-01`, `DB-02` e `DB-03`
  continua em
  [especificacao_schema_db01_db02_db03_fluxos_publicos.md](especificacao_schema_db01_db02_db03_fluxos_publicos.md);
- a rastreabilidade `CASO-* -> TDD-*` continua em
  `../../../eickrono-thimisu/eickrono-thimisu-app/docs/matriz_autenticacao_social_conectividade_testes.md`.

## Pacotes de mudanca de dados

Para evitar repetir a mesma descricao em todos os `TDD-*`, este documento usa
os seguintes pacotes de banco/catalogo:

- `DB-01` Catalogo de projeto/cliente do ecossistema
  - reaproveitar o identificador ja existente de cliente/ecossistema;
  - garantir colunas de regra e exibicao por projeto:
    - `nomeProjeto`
    - `tipoProdutoExibicao`
    - `produtoExibicao`
    - `canalExibicao`
    - `exigeValidacaoTelefone`
    - `ativo`
- `DB-02` Snapshot do projeto/politica no fluxo publico
  - persistir no cadastro/recuperacao o projeto resolvido e a politica usada
    no inicio da jornada;
  - minimo recomendado:
    - identificador do projeto/cliente_ecossistema resolvido
    - `exigeValidacaoTelefone` aplicado no inicio do fluxo
  - objetivo: impedir que uma mudanca posterior de configuracao altere uma
    jornada ja iniciada
- `DB-03` Contexto social pendente para vinculacao assistida
  - persistir contexto do login social sem perfil do sistema vinculado;
  - minimo recomendado:
    - projeto atual
    - conta sugerida para vinculacao
    - contador de falhas
    - estado de cancelamento/consumo do contexto

## Legenda

- `Backend`: `sim`, `nao` ou `principal`
- `App/cliente`: `sim`, `nao` ou `principal`
- `Migration`: `sim`, `nao` ou `provavel`
- `Catalogo`: `sim`, `nao` ou `provavel`

`Migration=provavel` significa:

- a regra pode talvez ser implementada com estruturas ja existentes;
- mas, para o desenho fechado ate aqui, a implementacao correta muito
  provavelmente vai exigir ajuste persistente.

## Matriz tecnica por TDD

| TDD | CASO | Ownership principal | Backend | App/cliente | Migration | Catalogo | Pacotes de dados | Observacao tecnica |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `TDD-45` | `CASO-01` | `backend` | `principal` | `sim` | `sim` | `sim` | `DB-01`, `DB-02` | Cadastro depende da politica do projeto, mesmo quando a validacao de telefone for dispensada. |
| `TDD-46` | `CASO-02` | `cross-service` | `principal` | `principal` | `sim` | `sim` | `DB-01`, `DB-02` | Backend precisa bloquear ate telefone; app precisa reutilizar a mesma tela na etapa de telefone. |
| `TDD-47` | `CASO-03` | `backend` | `principal` | `nao` | `sim` | `sim` | `DB-01` | Projeto desconhecido/inativo so fica correto se a resolucao do catalogo existir de forma confiavel. |
| `TDD-48` | `CASO-04` | `backend` | `principal` | `sim` | `nao` | `nao` | `-` | Fluxo basico de login com conta ja `LIBERADA`; tende a ser majoritariamente runtime. |
| `TDD-49` | `CASO-05` | `cross-service` | `principal` | `principal` | `sim` | `sim` | `DB-01`, `DB-02` | Login precisa distinguir pendencia so de e-mail e decidir pelo projeto se segue ou nao para telefone. |
| `TDD-50` | `CASO-06` | `cross-service` | `principal` | `principal` | `sim` | `sim` | `DB-01`, `DB-02` | Abertura direta na etapa de telefone depende do estado correto do cadastro pendente e da politica do projeto. |
| `TDD-51` | `CASO-07` | `backend` | `principal` | `sim` | `nao` | `nao` | `-` | Exige classificacao de estado funcional, mas nao necessariamente nova estrutura de banco. |
| `TDD-52` | `CASO-08` | `cross-service` | `principal` | `principal` | `sim` | `sim` | `DB-01`, `DB-02` | Regularizacao com senha incorreta cruza login, validacao de contato e redefinicao de senha. |
| `TDD-53` | `CASO-09` | `backend` | `principal` | `nao` | `nao` | `nao` | `-` | Caso defensivo de contrato e seguranca; resposta controlada sem regularizacao. |
| `TDD-54` | `CASO-10` | `backend` | `principal` | `sim` | `nao` | `nao` | `-` | Recuperacao normal de conta ja liberada; mudanca principal e de runtime. |
| `TDD-55` | `CASO-11` | `backend` | `principal` | `sim` | `sim` | `sim` | `DB-01`, `DB-02` | Recuperacao passa a servir como prova de e-mail em conta pendente sem telefone obrigatorio. |
| `TDD-56` | `CASO-12` | `cross-service` | `principal` | `principal` | `sim` | `sim` | `DB-01`, `DB-02` | Recuperacao com telefone obrigatorio precisa reabrir a mesma jornada na etapa de telefone. |
| `TDD-57` | `CASO-13` | `backend` | `principal` | `sim` | `nao` | `nao` | `-` | Mantem bloqueio administrativo mesmo na recuperacao; tende a ser regra de runtime. |
| `TDD-58` | `CASO-14` | `backend` | `principal` | `sim` | `nao` | `nao` | `-` | Login social ja vinculado e liberado deve continuar direto, sem nova estrutura. |
| `TDD-59` | `CASO-15` | `cross-service` | `principal` | `principal` | `provavel` | `sim` | `DB-01`, `DB-03` | Precisa respeitar o projeto atual ao procurar e-mail e manter contexto social pendente com prefill (preenchimento inicial) editavel. |
| `TDD-60` | `CASO-16` | `cross-service` | `principal` | `principal` | `provavel` | `sim` | `DB-01`, `DB-03` | Vinculacao assistida depende da checagem por projeto atual e do contexto pendente da conta sugerida. |
| `TDD-61` | `CASO-17` | `cross-service` | `sim` | `principal` | `provavel` | `nao` | `DB-03` | Recusa da vinculacao exige descarte consistente do contexto social pendente. |
| `TDD-62` | `CASO-18` | `cross-service` | `principal` | `principal` | `provavel` | `sim` | `DB-01`, `DB-03` | Sucesso da vinculacao exige garantir que a autenticacao local corresponde a conta sugerida do projeto atual. |
| `TDD-63` | `CASO-19` | `cross-service` | `principal` | `principal` | `provavel` | `sim` | `DB-01`, `DB-03` | Outra conta informada deve cancelar o contexto pendente e impedir vinculacao cruzada. |
| `TDD-64` | `CASO-20` | `cross-service` | `principal` | `principal` | `provavel` | `nao` | `DB-03` | Limite de 3 falhas depende de persistir ou controlar com seguranca o contador do contexto pendente. |

## Resumo por camada

### Backend predominante

- `TDD-45`
- `TDD-47`
- `TDD-48`
- `TDD-51`
- `TDD-53`
- `TDD-54`
- `TDD-55`
- `TDD-57`
- `TDD-58`

### Cross-service predominante

- `TDD-46`
- `TDD-49`
- `TDD-50`
- `TDD-52`
- `TDD-56`
- `TDD-59`
- `TDD-60`
- `TDD-61`
- `TDD-62`
- `TDD-63`
- `TDD-64`

### Pacotes de banco/catalogo mais provaveis

- `DB-01` e obrigatorio para a politica por projeto:
  - `TDD-45`
  - `TDD-46`
  - `TDD-47`
  - `TDD-49`
  - `TDD-50`
  - `TDD-52`
  - `TDD-55`
  - `TDD-56`
  - `TDD-59`
  - `TDD-60`
  - `TDD-62`
  - `TDD-63`
- `DB-02` e o pacote recomendado para estabilidade das jornadas longas:
  - `TDD-45`
  - `TDD-46`
  - `TDD-49`
  - `TDD-50`
  - `TDD-52`
  - `TDD-55`
  - `TDD-56`
- `DB-03` concentra a vinculacao social assistida:
  - `TDD-59`
  - `TDD-60`
  - `TDD-61`
  - `TDD-62`
  - `TDD-63`
  - `TDD-64`

## Preparacao de banco antes de implementar

Ordem recomendada:

1. fechar o desenho de `DB-01`, `DB-02` e `DB-03`;
2. escrever as migrations locais;
3. aplicar e validar em `local`;
4. popular seeds minimos de catalogo por projeto;
5. so depois preparar rollout de migration para `hml`.

Conclusao pratica:

- `nao` e hora de subir migration direto em `hml`;
- `sim`, local e `hml` muito provavelmente vao exigir atualizacao de banco para
  fechar o alvo funcional;
- o minimo seguro antes do rollout e sair deste documento com `DB-01..03`
  fechados em especificacao de schema/migration.
