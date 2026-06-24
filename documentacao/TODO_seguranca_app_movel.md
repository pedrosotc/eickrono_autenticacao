# TODO Segurança App Móvel

Checklist técnico do `servidor de autenticação` para correlação de atestação oficial com sinais locais do app.

## Contrato Público

- [x] Adicionar `aplicacaoId` explícito ao contrato público de cadastro.
- [x] Adicionar `aplicacaoId` explícito ao contrato público de login.
- [x] Adicionar payload de sinais locais de segurança ao contrato público.

## Avaliação de Risco

- [x] Criar serviço de avaliação de sinais locais do app.
- [x] Auditar `aplicacaoId`, plataforma, score e sinais recebidos.
- [x] Permitir modo observação em `dev/stg/test`.
- [x] Endurecer regras em `prod`.
- [x] Integrar a avaliação de risco ao cadastro público.
- [x] Integrar a avaliação de risco ao login público.

## Configuração

- [x] Criar propriedades de configuração para segurança de app móvel.
- [x] Permitir lista de `aplicacaoId` aceitos.
- [x] Documentar limites da correlação atual com `Play Integrity` / `App Attest`.

## Testes e Documentação

- [x] Adicionar testes da avaliação de risco.
- [x] Atualizar a arquitetura documentada.

## Implementado nesta etapa

- Contratos públicos de cadastro e login com `aplicacaoId` e `segurancaAplicativo`.
- `AvaliacaoSegurancaAplicativoService` com score, auditoria e bloqueio por política.
- `SegurancaAplicativoProperties` com modo observação, score máximo e allowlist de aplicações.
- Integração da avaliação de risco nos fluxos públicos canônicos.
- Testes unitários e integração ajustados para o novo contrato.

## Documentos correlatos

- biblioteca cliente compartilhada: [../../eickrono-autenticacao-cliente/docs/TODO_seguranca_app_movel.md](../../eickrono-autenticacao-cliente/docs/TODO_seguranca_app_movel.md)
- app Thimisu: [../../eickrono-thimisu/eickrono-thimisu-app/docs/TODO_seguranca_app_movel.md](../../eickrono-thimisu/eickrono-thimisu-app/docs/TODO_seguranca_app_movel.md)
