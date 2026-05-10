# Modulo Eickrono Autenticacao

Este módulo concentra a API pública/autenticada de autenticação dentro do
repositório `eickrono-autenticacao-servidor`.

## Papel

- expor a borda HTTP usada pelo app e por clientes autenticados;
- orquestrar cadastro, login, sessão, recuperação de senha e registro de
  dispositivo;
- integrar por backchannel com `eickrono-identidade-servidor`, Keycloak e,
  quando necessário, backend de produto.

## Nomenclatura correta

- nome físico do módulo Maven: `modulo-eickrono-autenticacao`
- `artifactId` do módulo: `modulo-eickrono-autenticacao`
- nome operacional atual do serviço/container: `eickrono-autenticacao`

Regra prática:

- quando o assunto for diretório, `pom.xml`, `artifactId` ou projeto no IDE,
  use `modulo-eickrono-autenticacao`;
- quando o assunto for `docker compose`, logs, container ou endpoint do runtime
  atual, o nome ainda pode aparecer como `eickrono-autenticacao`.

## Build

Da raiz do repositório:

```bash
mvn -pl modulos/modulo-eickrono-autenticacao -am package -DskipTests
```

Ou direto neste diretório:

```bash
mvn package -DskipTests
```

## Runtime local

O `docker compose` local sobe este módulo como o serviço
`eickrono-autenticacao`.

Arquivos relevantes:

- `../../infraestrutura/dev/docker-compose.yml`
- `../../infraestrutura/hml/docker-compose.yml`
- `Dockerfile`
