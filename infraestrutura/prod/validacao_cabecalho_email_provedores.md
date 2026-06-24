# Validacao de Cabecalho de E-mail por Provedor

## Objetivo

Confirmar, com mensagem real recebida, se o ecossistema de envio do dominio
`eickrono.com` esta autenticando corretamente nas caixas de destino mais
relevantes:

- `Hotmail/Outlook`
- `Gmail`
- `Yahoo`

Este guia complementa a validacao de DNS. Mesmo com `SPF`, `DKIM` e `DMARC`
publicados no DNS, a confirmacao final depende do cabecalho efetivo da mensagem
recebida.

## Pre-condicoes

Antes desta etapa, o dominio precisa passar no validador DNS:

```bash
bash ./infraestrutura/prod/dns/validate_email_auth_dns.sh --domain eickrono.com
```

Estado esperado:

- `SPF_PRESENT=true`
- `DMARC_PRESENT=true`
- `DKIM_SELECTOR_sig1_PRESENT=true`
- `OVERALL_STATUS=ok`

## Fluxo recomendado de teste

1. enviar um e-mail real do fluxo que se quer validar:
   - recuperacao de senha
   - confirmacao de cadastro
2. mandar para pelo menos uma caixa de cada provedor:
   - `Hotmail/Outlook`
   - `Gmail`
   - `Yahoo`
3. abrir a mensagem recebida;
4. abrir o cabecalho completo;
5. localizar a linha `Authentication-Results`;
6. registrar o resultado no historico operacional.

## Como abrir o cabecalho completo

### Gmail

Fonte oficial:

- Google Help: `Trace an email with its full header`

Passos:

1. abrir a mensagem no navegador;
2. ao lado de `Reply`, clicar em `More`;
3. clicar em `Show original`.

## Hotmail / Outlook.com

Fonte operacional:

- o proprio Google Help lista o caminho classico de `Hotmail` para inspecao de
  header completo

Passos:

1. entrar na caixa `Hotmail/Outlook`;
2. abrir a `Inbox`;
3. clicar com o botao direito na mensagem;
4. abrir `View Message Source`.

Observacao:

- a interface do Outlook.com pode variar ao longo do tempo;
- se o menu estiver diferente, o objetivo continua sendo abrir a origem ou o
  cabecalho bruto da mensagem.

### Yahoo Mail

Fonte oficial:

- Yahoo Help: `Use full headers to find delivery delays or an email’s true sender`

Passos:

1. abrir a mensagem;
2. clicar em `More`;
3. clicar em `View Raw Message`.

## O que procurar no cabecalho

### Minimo obrigatorio

Na linha `Authentication-Results`, o estado saudavel alvo e:

```text
spf=pass
dkim=pass
dmarc=pass
```

### Sinais adicionais muito bons

- `header.from=eickrono.com`
- `signed-by` ou `d=` apontando para o dominio correto
- ausencia de alertas visuais de autenticacao quebrada no provedor

### Gmail

No Gmail, alem do header bruto, o proprio provedor mostra sinais visuais uteis.

Estado desejado:

- aparece `Mailed by` com o dominio correto
- aparece `Signed by` com o dominio correto

Se houver `?` ao lado do remetente, o Gmail esta tratando a mensagem como nao
autenticada.

## Criterio de aceite

### Aprovado

Considerar o provedor aprovado quando:

- `dmarc=pass`
- `dkim=pass`
- `spf=pass`

e a mensagem nao mostrar alerta de autenticacao suspeita.

### Aprovado com ressalva

Pode acontecer de o fluxo ficar tecnicamente aceitavel mesmo que apenas um dos
metodos esteja alinhado para DMARC. Exemplo:

- `spf=pass`
- `dkim=pass`
- `dmarc=pass`

Esse ainda e o melhor alvo.

Mas se aparecer:

- `spf=fail`
- `dkim=pass`
- `dmarc=pass`

ou

- `spf=pass`
- `dkim=fail`
- `dmarc=pass`

isso pode continuar entregando bem, desde que o `DMARC` esteja em `pass`. Ainda
assim, vale revisar o alinhamento que falhou.

### Reprovado

Tratar como reprovado quando qualquer um destes ocorrer:

- `dmarc=fail`
- a mensagem entra em lixo eletronico e o cabecalho mostra falha de
  autenticacao
- o Gmail mostra alerta visual de remetente nao autenticado

## Como interpretar problemas comuns

### `spf=pass`, `dkim=pass`, `dmarc=pass`, mas ainda caiu no lixo

Hipotese principal:

- problema de reputacao, conteudo, warming do dominio ou historico do remetente

Proximo passo:

- revisar assunto, corpo, taxa de reclamacao, volume e reputacao do dominio

### `spf=pass`, `dkim=pass`, `dmarc=fail`

Hipotese principal:

- falta de alinhamento entre o dominio do `From` e o dominio validado por SPF ou
  DKIM

Proximo passo:

- revisar `header.from`, `return-path`, `smtp.mailfrom` e assinatura `DKIM d=`

### `spf=fail`, `dkim=pass`, `dmarc=pass`

Hipotese principal:

- SPF do caminho de envio nao esta alinhado, mas DKIM esta salvando o DMARC

Proximo passo:

- manter como funcional, mas revisar se o `mailfrom` pode ser melhorado

### `spf=pass`, `dkim=fail`, `dmarc=pass`

Hipotese principal:

- DKIM quebrou em algum salto, mas SPF alinhado sustentou o DMARC

Proximo passo:

- revisar assinatura DKIM e possiveis alteracoes no corpo/cabecalho

## Registro minimo no historico

Para cada provedor, registrar:

- `provedor`
- `assunto`
- `data/hora local`
- `Authentication-Results`
- pasta de destino:
  - `Inbox`
  - `Junk/Spam`
- observacoes visuais:
  - `Mailed by`
  - `Signed by`
  - alerta de autenticacao

Modelo:

```text
Provedor: Gmail
Assunto: [STG] Codigo de recuperacao - Thimisu da Eickrono
Authentication-Results: spf=pass dkim=pass dmarc=pass
Pasta: Inbox
Observacoes: Mailed by eickrono.com; Signed by eickrono.com
```

## Fontes

- Google Help: `Trace an email with its full header`
- Google Help: `Check if your Gmail message is authenticated`
- Yahoo Help: `Use full headers to find delivery delays or an email’s true sender`
