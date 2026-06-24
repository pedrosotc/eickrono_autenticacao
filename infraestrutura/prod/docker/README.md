# Docker STG

Esta pasta guarda os artefatos de imagem do runtime de `stg`.

Arquivos:

- `Dockerfile.keycloak-stg`
- `start-keycloak-stg.sh`

Objetivo:

- empacotar o `eickrono-autenticacao-servidor` como runtime Keycloak customizado
- embutir provider JAR, realms, tema e providers auxiliares
- eliminar a dependencia de bind mounts do `docker compose` local

A trilha operacional principal de build e uso desse Dockerfile esta em:

- `../runbook_stg_aws_operacional.md`

Para contexto cronologico mais amplo da implantacao:

- `../guia_subida_stg_aws.md`
